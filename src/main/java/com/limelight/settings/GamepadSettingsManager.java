package com.limelight.settings;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.limelight.input.gamepad.GamepadMapping;

/**
 * Manages the gamepad settings
 * @author Diego Waxemberg
 */
public abstract class GamepadSettingsManager {

    private static final Logger logger = LoggerFactory.getLogger(GamepadSettingsManager.class);

    private static GamepadMapping cachedSettings;

    /**
     * Reads the gamepad settings from the gamepad file and caches the configuration
     * @return the gamepad settings
     */
    public static GamepadMapping getSettings() {
        if (cachedSettings == null) {
            logger.info("Reading Gamepad Settings");
            File gamepadFile = SettingsManager.getInstance().getGamepadFile();
            GamepadMapping savedMapping = (GamepadMapping) SettingsManager.readSettings(gamepadFile,
                                                                                        GamepadMapping.class);
            cachedSettings = savedMapping;
        }
        if (cachedSettings == null) {
            logger.warn("Unable to get gamepad settings. Using default mapping instead.");
            if (System.getProperty("os.name").contains("Windows")) {
                cachedSettings = GamepadMapping.getWindowsDefaultMapping();
            } else {
                cachedSettings = new GamepadMapping();
            }
            writeSettings(cachedSettings);
        }
        return cachedSettings;
    }

    /**
     * Writes the specified mapping to the gamepad file and updates the cached settings
     * @param settings the new gamepad mapping to be written out
     */
    public static void writeSettings(GamepadMapping settings) {
        cachedSettings = settings;
        logger.info("Writing Gamepad Settings");

        File gamepadFile = SettingsManager.getInstance().getGamepadFile();
        SettingsManager.writeSettings(gamepadFile, settings);
    }

}
