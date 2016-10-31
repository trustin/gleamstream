package com.limelight.nvstream.mdns;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.jmdns.JmmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MdnsDiscoveryAgent implements ServiceListener {

    private static final Logger logger = LoggerFactory.getLogger(MdnsDiscoveryAgent.class);

    public static final String SERVICE_TYPE = "_nvstream._tcp.local.";

    private final MdnsDiscoveryListener listener;
    private Thread discoveryThread;
    private final HashMap<InetAddress, MdnsComputer> computers = new HashMap<>();
    private final HashSet<String> pendingResolution = new HashSet<>();

    // The resolver factory's instance member has a static lifetime which
    // means our ref count and listener must be static also.
    private static int resolverRefCount;
    private static final HashSet<ServiceListener> listeners = new HashSet<>();
    private static final ServiceListener nvstreamListener = new ServiceListener() {
        @Override
        public void serviceAdded(ServiceEvent event) {
            HashSet<ServiceListener> localListeners;

            // Copy the listener set into a new set so we can invoke
            // the callbacks without holding the listeners monitor the
            // whole time.
            synchronized (listeners) {
                localListeners = new HashSet<>(listeners);
            }

            for (ServiceListener listener : localListeners) {
                listener.serviceAdded(event);
            }
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            HashSet<ServiceListener> localListeners;

            // Copy the listener set into a new set so we can invoke
            // the callbacks without holding the listeners monitor the
            // whole time.
            synchronized (listeners) {
                localListeners = new HashSet<>(listeners);
            }

            for (ServiceListener listener : localListeners) {
                listener.serviceRemoved(event);
            }
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            HashSet<ServiceListener> localListeners;

            // Copy the listener set into a new set so we can invoke
            // the callbacks without holding the listeners monitor the
            // whole time.
            synchronized (listeners) {
                localListeners = new HashSet<>(listeners);
            }

            for (ServiceListener listener : localListeners) {
                listener.serviceResolved(event);
            }
        }
    };

    private static synchronized JmmDNS referenceResolver() {
        JmmDNS instance = JmmDNS.Factory.getInstance();
        if (++resolverRefCount == 1) {
            // This will cause the listener to be invoked for known hosts immediately.
            // JmDNS only supports one listener per service, so we have to do this here
            // with a static listener.
            instance.addServiceListener(SERVICE_TYPE, nvstreamListener);
        }
        return instance;
    }

    private static synchronized void dereferenceResolver() {
        if (--resolverRefCount == 0) {
            try {
                JmmDNS.Factory.close();
            } catch (IOException ignored) {}
        }
    }

    public MdnsDiscoveryAgent(MdnsDiscoveryListener listener) {
        this.listener = listener;
    }

    private void handleResolvedServiceInfo(ServiceInfo info) {
        pendingResolution.remove(info.getName());
        handleServiceInfo(info);
    }

    private void handleServiceInfo(ServiceInfo info) {
        Inet4Address[] addrs = info.getInet4Addresses();

        logger.info("mDNS: " + info.getName() + " has " + addrs.length + " addresses");

        // Add a computer object for each IPv4 address reported by the PC
        for (Inet4Address addr : addrs) {
            synchronized (computers) {
                MdnsComputer computer = new MdnsComputer(info.getName(), addr);
                if (computers.put(computer.getAddress(), computer) == null) {
                    // This was a new entry
                    listener.notifyComputerAdded(computer);
                }
            }
        }
    }

    public void startDiscovery(final int discoveryIntervalMs) {
        // Kill any existing discovery before starting a new one
        stopDiscovery();

        // Add our listener to the set
        synchronized (listeners) {
            listeners.add(this);
        }

        discoveryThread = new Thread(() -> {
            // This may result in listener callbacks so we must register
            // our listener first.
            JmmDNS resolver = referenceResolver();

            try {
                while (!Thread.interrupted()) {
                    // Start an mDNS request
                    resolver.requestServiceInfo(SERVICE_TYPE, null, discoveryIntervalMs);

                    // Run service resolution again for pending machines
                    ArrayList<String> pendingNames = new ArrayList<>(pendingResolution);
                    for (String name : pendingNames) {
                        logger.info("mDNS: Retrying service resolution for machine: " + name);
                        ServiceInfo[] infos = resolver.getServiceInfos(SERVICE_TYPE, name, 500);
                        if (infos != null && infos.length != 0) {
                            logger.info(
                                    "mDNS: Resolved (retry) with " + infos.length + " service entries");
                            for (ServiceInfo svcinfo : infos) {
                                handleResolvedServiceInfo(svcinfo);
                            }
                        }
                    }

                    // Wait for the next polling interval
                    try {
                        Thread.sleep(discoveryIntervalMs);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            } finally {
                // Dereference the resolver
                dereferenceResolver();
            }
        });
        discoveryThread.setName("mDNS Discovery Thread");
        discoveryThread.start();
    }

    public void stopDiscovery() {
        // Remove our listener from the set
        synchronized (listeners) {
            listeners.remove(this);
        }

        // If there's already a running thread, interrupt it
        if (discoveryThread != null) {
            discoveryThread.interrupt();
            discoveryThread = null;
        }
    }

    public List<MdnsComputer> getComputerSet() {
        synchronized (computers) {
            return new ArrayList<MdnsComputer>(computers.values());
        }
    }

    @Override
    public void serviceAdded(ServiceEvent event) {
        logger.info("mDNS: Machine appeared: " + event.getInfo().getName());

        ServiceInfo info = event.getDNS().getServiceInfo(SERVICE_TYPE, event.getInfo().getName(), 500);
        if (info == null) {
            // This machine is pending resolution
            pendingResolution.add(event.getInfo().getName());
            return;
        }

        logger.info("mDNS: Resolved (blocking)");
        handleResolvedServiceInfo(info);
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
        logger.info("mDNS: Machine disappeared: " + event.getInfo().getName());

        Inet4Address addrs[] = event.getInfo().getInet4Addresses();
        for (Inet4Address addr : addrs) {
            synchronized (computers) {
                MdnsComputer computer = computers.remove(addr);
                if (computer != null) {
                    listener.notifyComputerRemoved(computer);
                    break;
                }
            }
        }
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
        logger.info("mDNS: Machine resolved (callback): " + event.getInfo().getName());
        handleResolvedServiceInfo(event.getInfo());
    }
}
