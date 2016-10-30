package com.limelight.input.gamepad;

import com.limelight.LimeLog;

public final class NativeGamepad {
    public static final int DEFAULT_DEVICE_POLLING_ITERATIONS = 400;
    public static final int DEFAULT_EVENT_POLLING_INTERVAL = 5;

    private static boolean running;
    private static boolean initialized;
    private static Thread pollingThread;
    private static int devicePollingIterations = DEFAULT_DEVICE_POLLING_ITERATIONS;
    private static int pollingIntervalMs = DEFAULT_EVENT_POLLING_INTERVAL;

    static {
        System.loadLibrary("gamepad_jni");
    }

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

    public static void start() {
        if (!running) {
            running = true;
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
        pollingThread = new Thread() {
            @Override
            public void run() {
                int iterations = 0;

                if (!initialized) {
                    NativeGamepad.init();
                    initialized = true;
                }

                while (!isInterrupted()) {
                    // If we have no devices, we don't bother with the event
                    // polling interval. We just run the device polling interval.
                    if (getDeviceCount() == 0) {
                        detectDevices();
                        processEvents();
                        if (getDeviceCount() == 0) {
                            try {
                                Thread.sleep(pollingIntervalMs * devicePollingIterations);
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                    } else {
                        if (iterations++ % devicePollingIterations == 0) {
                            detectDevices();
                        }

                        processEvents();

                        try {
                            Thread.sleep(pollingIntervalMs);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
            }
        };
        pollingThread.setName("Native Gamepad - Polling Thread");
        pollingThread.start();
    }

    private static void stopPolling() {
        if (pollingThread != null) {
            pollingThread.interrupt();

            try {
                pollingThread.join();
            } catch (InterruptedException e) {}
        }
    }

    public static void deviceAttachCallback(int deviceId, int numButtons, int numAxes) {
        LimeLog.info(deviceId + " has attached.");
        GamepadListener.deviceAttached(deviceId, numButtons, numAxes);
    }

    public static void deviceRemoveCallback(int deviceId) {
        LimeLog.info(deviceId + " has detached.");
        GamepadListener.deviceRemoved(deviceId);
    }

    public static void buttonUpCallback(int deviceId, int buttonId) {
        GamepadListener.buttonUp(deviceId, buttonId);
    }

    public static void buttonDownCallback(int deviceId, int buttonId) {
        GamepadListener.buttonDown(deviceId, buttonId);
    }

    public static void axisMovedCallback(int deviceId, int axisId, float value, float lastValue) {
        GamepadListener.axisMoved(deviceId, axisId, value, lastValue);
    }

    private NativeGamepad() {}
}
