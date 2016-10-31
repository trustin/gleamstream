package com.limelight.input.gamepad;

import java.util.ArrayList;
import java.util.List;

import com.limelight.input.Device;
import com.limelight.input.DeviceListener;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * Listens to {@code Controller}s connected to this computer and gives any gamepad to the gamepad handler
 * @author Diego Waxemberg
 */
public final class GamepadListener {

    private static final Int2ObjectMap<Device> devices = new Int2ObjectArrayMap<>(4);
    private static final List<DeviceListener> listeners = new ArrayList<>();

    public static int deviceCount() {
        return devices.size();
    }

    public static void addDeviceListener(DeviceListener listener) {
        listeners.add(listener);
    }

    public static void removeListener(DeviceListener listener) {
        listeners.remove(listener);
    }

    public static void deviceAttached(int deviceId, int numButtons, int numAxes) {
        Device dev = new Device(deviceId, numButtons, numAxes);

        devices.put(deviceId, dev);

        for (DeviceListener listener : listeners) {
            listener.handleDeviceAdded(dev);
        }
    }

    public static void deviceRemoved(int deviceId) {
        Device dev = devices.get(deviceId);
        if (dev == null) {
            return;
        }

        for (DeviceListener listener : listeners) {
            listener.handleDeviceRemoved(dev);
        }

        devices.remove(deviceId);
    }

    public static void buttonDown(int deviceId, int buttonId) {
        Device dev = devices.get(deviceId);
        for (DeviceListener listener : listeners) {
            listener.handleButton(dev, buttonId, true);
        }
    }

    public static void buttonUp(int deviceId, int buttonId) {
        Device dev = devices.get(deviceId);
        for (DeviceListener listener : listeners) {
            listener.handleButton(dev, buttonId, false);
        }
    }

    public static void axisMoved(int deviceId, int axisId, float value, float lastValue) {
        Device dev = devices.get(deviceId);
        for (DeviceListener listener : listeners) {
            listener.handleAxis(dev, axisId, value, lastValue);
        }
    }

    private GamepadListener() {}
}
