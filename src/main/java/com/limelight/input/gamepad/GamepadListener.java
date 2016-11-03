package com.limelight.input.gamepad;

public interface GamepadListener {
    void handleButton(int deviceId, int buttonId, boolean pressed);
    void handleAxis(int deviceId, int axisId, float newValue, float lastValue);
    void handleDeviceAdded(int deviceId);
    void handleDeviceRemoved(int deviceId);
}
