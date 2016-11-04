package com.limelight.input.gamepad;

import com.limelight.utils.NativeLibraries;

public final class NativeGamepad {

    public static final int DEFAULT_DEVICE_POLLING_ITERATIONS = 400;
    public static final int DEFAULT_EVENT_POLLING_INTERVAL = 5;

    private static boolean loadedNativeLibrary;
    private static boolean running;
    private static boolean initialized;
    private static Thread pollingThread;
    private static int devicePollingIterations = DEFAULT_DEVICE_POLLING_ITERATIONS;
    private static int pollingIntervalMs = DEFAULT_EVENT_POLLING_INTERVAL;
    private static volatile GamepadListener listener;

    static {
        NativeLibraries.load("gamepad_jni");
    }

    public static void initNativeLibraries() {}

    private static native void init();

    private static native void shutdown();

    private static native int numDevices();

    private static native void detectDevices();

    private static native void processEvents();

    public static boolean isRunning() {
        return running;
    }

    public static void setDevicePollingIterations(int iterations) {
        devicePollingIterations = iterations;
    }

    public static int getDevicePollingIterations() {
        return devicePollingIterations;
    }

    public static void setPollingInterval(int interval) {
        pollingIntervalMs = interval;
    }

    public static int getPollingInterval() {
        return pollingIntervalMs;
    }

    public static void start(GamepadListener listener) {
        if (!running) {
            running = true;
            NativeGamepad.listener = listener;
            startPolling();
        }
    }

    public static void stop() {
        if (running) {
            stopPolling();
            running = false;
        }
    }

    public static void release() {
        if (running) {
            throw new IllegalStateException("Cannot release running NativeGamepad");
        }

        if (initialized) {
            shutdown();
            initialized = false;
        }
    }

    public static int getDeviceCount() {
        if (!running) {
            throw new IllegalStateException("NativeGamepad not running");
        }

        return numDevices();
    }

    private static void startPolling() {
        pollingThread = new Thread(() -> {
            int iterations = 0;

            if (!initialized) {
                init();
                initialized = true;
            }

            while (!Thread.currentThread().isInterrupted()) {
                // If we have no devices, we don't bother with the event
                // polling interval. We just run the device polling interval.
                if (getDeviceCount() == 0) {
                    detectDevices();
                    processEvents();
                    if (getDeviceCount() == 0) {
                        try {
                            Thread.sleep(pollingIntervalMs * devicePollingIterations);
                        } catch (InterruptedException ignored) {
                            break;
                        }
                    }
                } else {
                    if (iterations++ % devicePollingIterations == 0) {
                        detectDevices();
                    }

                    processEvents();

                    try {
                        Thread.sleep(pollingIntervalMs);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }
        });
        pollingThread.setName("Native Gamepad - Polling Thread");
        pollingThread.start();
    }

    private static void stopPolling() {
        if (pollingThread != null) {
            while (pollingThread.isAlive()) {
                pollingThread.interrupt();

                try {
                    pollingThread.join();
                } catch (InterruptedException ignored) {}
            }
        }
    }

    public static void deviceAttachCallback(int deviceId, int numButtons, int numAxes) {
        final GamepadListener l = listener;
        if (l == null) {
            return;
        }
        l.handleDeviceAdded(deviceId);
    }

    public static void deviceRemoveCallback(int deviceId) {
        final GamepadListener l = listener;
        if (l == null) {
            return;
        }
        l.handleDeviceRemoved(deviceId);
    }

    public static void buttonUpCallback(int deviceId, int buttonId) {
        final GamepadListener l = listener;
        if (l == null) {
            return;
        }
        l.handleButton(deviceId, buttonId, false);
    }

    public static void buttonDownCallback(int deviceId, int buttonId) {
        final GamepadListener l = listener;
        if (l == null) {
            return;
        }
        l.handleButton(deviceId, buttonId, true);
    }

    public static void axisMovedCallback(int deviceId, int axisId, float value, float lastValue) {
        final GamepadListener l = listener;
        if (l == null) {
            return;
        }
        l.handleAxis(deviceId, axisId, value, lastValue);
    }

    private NativeGamepad() {}
}
