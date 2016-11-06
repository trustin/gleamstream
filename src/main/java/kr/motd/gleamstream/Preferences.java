package kr.motd.gleamstream;

import static com.google.common.base.MoreObjects.firstNonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import org.lwjgl.system.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import kr.motd.gleamstream.gamepad.GamepadMappings;

public final class Preferences {

    private static final Logger logger = LoggerFactory.getLogger(Preferences.class);

    /**
     * Directory to which settings will be saved
     */
    public static final String SETTINGS_DIR;

    static {
        final String configHome;
        switch (Platform.get()) {
            case LINUX:
                configHome = firstNonNull(System.getenv("XDG_CONFIG_HOME"),
                                          System.getProperty("user.home") + File.separator + ".config");
                break;
            case MACOSX:
                configHome = System.getProperty("user.home") + File.separator +
                             "Library" + File.separator +
                             "Application Support";
                break;
            case WINDOWS:
                configHome = firstNonNull(System.getenv("APPDATA"),
                                          System.getProperty("user.home") + File.separator +
                                          "AppData" + File.separator +
                                          "Roaming");
                break;
            default:
                throw new IllegalStateException("Unsupported platform: " + Platform.get());
        }

        if (configHome.endsWith(File.separator)) {
            SETTINGS_DIR = configHome + "gleamstream";
        } else {
            SETTINGS_DIR = configHome + File.separator + "gleamstream";
        }
    }

    private static final File uniqueIdFile = new File(SETTINGS_DIR + File.separator + "unique_id");
    private static final File customGamepadMappingsFile =
            new File(SETTINGS_DIR + File.separator + "gamepads.json");
    private static final String defaultGamepadMappingsPath = "/gamepads.json";

    private final String uniqueId;
    private final GamepadMappings gamepadMappings;

    public Preferences() throws IOException {
        logger.info("Loading preferences");

        // Load, generate and save the unique ID.
        if (uniqueIdFile.exists() && uniqueIdFile.canRead()) {
            logger.info("Loading the unique ID of the machine");
            uniqueId = Files.readFirstLine(uniqueIdFile, StandardCharsets.US_ASCII);
        } else {
            logger.info("Generating a unique ID of the machine");
            uniqueId = String.format("%016x", new SecureRandom().nextLong());
            Files.write(uniqueId, uniqueIdFile, StandardCharsets.US_ASCII);
        }

        // Load the gamepad mappings.
        if (customGamepadMappingsFile.isFile() && customGamepadMappingsFile.canRead()) {
            logger.info("Loading gamepad mappings from: {}", customGamepadMappingsFile);
            gamepadMappings = GamepadMappings.load(new FileInputStream(customGamepadMappingsFile));
        } else {
            logger.info("Loading the default gamepad mappings");
            gamepadMappings = GamepadMappings.load(
                    Main.class.getResourceAsStream(defaultGamepadMappingsPath));
        }

        // Generate the example mappings, which is actually the default.
        final File exampleGamepadMappingFile =
                new File(SETTINGS_DIR + File.separator + "gamepads.example.json");
        try (OutputStream out = new FileOutputStream(exampleGamepadMappingFile)) {
            ByteStreams.copy(Main.class.getResourceAsStream(defaultGamepadMappingsPath), out);
        } catch (Exception ignored) {}
    }

    public String uniqueId() {
        return uniqueId;
    }

    public GamepadMappings gamepadMappings() {
        return gamepadMappings;
    }
}
