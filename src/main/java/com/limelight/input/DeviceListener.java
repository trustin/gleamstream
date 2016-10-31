package com.limelight.input;

public interface DeviceListener {
    void handleButton(Device device, int buttonId, boolean pressed);

    void handleAxis(Device device, int axisId, float newValue, float lastValue);

    void handleDeviceAdded(Device device);

    void handleDeviceRemoved(Device device);
}
