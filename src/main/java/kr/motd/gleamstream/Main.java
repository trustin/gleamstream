package kr.motd.gleamstream;

import static java.lang.System.exit;
import static kr.motd.gleamstream.Panic.panic;

import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.internal.Console;
import com.limelight.binding.audio.JavaxAudioRenderer;
import com.limelight.binding.crypto.PcCryptoProvider;
import com.limelight.input.gamepad.GamepadHandler;
import com.limelight.input.gamepad.GamepadListener;
import com.limelight.input.gamepad.NativeGamepad;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.settings.PreferencesManager;
import com.limelight.settings.PreferencesManager.Preferences;
import com.limelight.settings.PreferencesManager.Preferences.Resolution;

public final class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    static {
        Thread.setDefaultUncaughtExceptionHandler((thread, cause) -> {
            throw panic("Unexpected exception from " + thread.getName() + ':', cause);
        });
    }

    private final Console console = JCommander.getConsole();

    @Parameter(
            names = "-connect",
            description = "Connects to the specified IP address or host (e.g. -c 192.168.0.100)")
    private String connectHost;

    @Parameter(
            names = "-pair",
            description = "Pairs with the specified IP address or host (e.g. -p 192.168.0.100)")
    private String pairHost;

    @Parameter(
            names = "-res",
            description = "The resolution of the video stream (must be 1080 or 720)")
    private int resolution = 1080;

    @Parameter(
            names = "-fps",
            description = "The frame rate of the video stream (must be 60 or 30)")
    private int fps = 60;

    @Parameter(names = "-bitrate", description = "The desired bitrate in Mbps")
    private int bitrateMbps = 30;

    @Parameter(names = "-hevc", description = "Use HEVC video codec")
    private Boolean useHevc;

    @Parameter(names = "-localaudio", description = "Makes the audio stay in the server")
    private Boolean useLocalAudio;

    @Parameter(names = "-app", description = "The name of the application to launch")
    private String app = "Steam";

    @Parameter(names = { "-help", "-h" }, description = "Prints the usage", help = true)
    private Boolean help;

    private Main() {}

    private void run(String[] args) throws Exception {
        final JCommander commander = new JCommander(this);
        commander.setProgramName("gleamstream");

        try {
            commander.parse(args);
        } catch (ParameterException ignored) {
            console.println("Invalid arguments: " + String.join(", ", args));
            help = true;
        }

        if (args.length == 0) {
            help = true;
        } else if (connectHost == null && pairHost == null) {
            console.println("-connect or -pair must be specified.");
            help = true;
        } else if (connectHost != null && pairHost != null) {
            console.println("-connect and -pair cannot be specified with each other.");
            help = true;
        } else if (resolution != 1080 && resolution != 720) {
            console.println("The value of -res option must be 1080 or 720.");
            help = true;
        } else if (fps != 60 && fps != 30) {
            console.println("The value of -fps option must be 60 or 30.");
            help = true;
        }

        if (Boolean.TRUE.equals(help)) {
            commander.usage();
            exit(1);
            return;
        }

        // TODO: Device name pattern matching + built-in gamepad mapping presets
        Preferences prefs = PreferencesManager.getPreferences();
        // Save preferences to preserve possibly new unique ID
        PreferencesManager.writePreferences(prefs);

        if (connectHost != null) {
            connect(prefs, resolution != 720, Boolean.TRUE.equals(useLocalAudio));
        } else {
            pair(prefs);
        }
    }

    private void connect(Preferences prefs, boolean use1080p, boolean useLocalAudio) throws Exception {
        final int width;
        final int height;
        if (use1080p) {
            width = 1920;
            height = 1080;
        } else {
            width = 1280;
            height = 720;
        }

        Osd.INSTANCE.setProgress("Initializing");
        MainWindow.INSTANCE.start().join();

        NativeGamepad.start();
        StreamConfiguration streamConfig = createConfiguration(
                width, height, useLocalAudio, Boolean.TRUE.equals(useHevc));

        prefs.setResolution(Resolution.findRes(height, fps));
        prefs.setBitrate(bitrateMbps);
        prefs.setLocalAudio(useLocalAudio);

        NvConnectionListener connListener = new NvConnectionListener() {
            @Override
            public void stageStarting(Stage stage) {
                final String message;
                switch (stage) {
                    case LAUNCH_APP:
                        message = "Launching application";
                        break;
                    case RTSP_HANDSHAKE:
                        message = "Handshaking RTSP session";
                        break;
                    case CONTROL_START:
                        message = "Establishing control stream";
                        break;
                    case VIDEO_START:
                        message = "Establishing video stream";
                        break;
                    case AUDIO_START:
                        message = "Establishing audio stream";
                        break;
                    case INPUT_START:
                        message = "Establishing input stream";
                        break;
                    default:
                        return;
                }

                Osd.INSTANCE.setProgress(message);
            }

            @Override
            public void stageComplete(Stage stage) {
                Osd.INSTANCE.clear();
            }

            @Override
            public void stageFailed(Stage stage) {
                logger.error("Stage failed: {}", stage);
                MainWindow.INSTANCE.destroy();
            }
        };

        final NvConnection conn = new NvConnection(
                connectHost, prefs.getUniqueId(), connListener, streamConfig,
                new PcCryptoProvider());

        // TODO: Replace with GLFW's Joystick API.
        final GamepadHandler gamepad = new GamepadHandler(conn);
        GamepadListener.addDeviceListener(gamepad);

        conn.start(
                VideoDecoderRenderer.FLAG_PREFER_QUALITY,
                new JavaxAudioRenderer(), // TODO: Replace with OpenAL.
                new FFmpegVideoDecoderRenderer(MainWindow.INSTANCE,
                                               new FFmpegFramePool(width, height)));

        Runtime.getRuntime().addShutdownHook(new Thread(conn::stop));
        MainWindow.INSTANCE.setListener(new DefaultMainWindowListener(conn));

        // TODO: Remove the MainWindow.destroy() calls in networking code.
    }

    private void pair(Preferences prefs) throws Exception {
        final LimelightCryptoProvider crypto = new PcCryptoProvider();
        final NvHTTP nvHttp = new NvHTTP(InetAddress.getByName(pairHost), prefs.getUniqueId(), crypto);
        final String serverInfo = nvHttp.getServerInfo();
        if (nvHttp.getPairState(serverInfo) == PairState.PAIRED) {
            console.println("Paired already with " + pairHost);
            return;
        }

        if (nvHttp.getCurrentGame(serverInfo) != 0) {
            console.println("Server is currently in a game. Close it before pairing.");
            return;
        }

        final String pin = PairingManager.generatePinString();

        console.println("Pairing with " + pairHost + "; enter the PIN '" + pin + "' on the server ..");
        final PairState state = nvHttp.pair(serverInfo, pin);

        switch (state) {
            case NOT_PAIRED:
                console.println("Not paired with " + pairHost);
                break;
            case PAIRED:
                console.println("Paired successfully with " + pairHost);
                break;
            case PIN_WRONG:
                console.println("Wrong PIN");
                break;
            case FAILED:
                console.println("Failed to pair with " + pairHost);
                break;
            case ALREADY_IN_PROGRESS:
                console.println("Other device is pairing with " + pairHost + " already.");
                break;
        }
    }

    /*
     * Creates a StreamConfiguration given a Resolution.
     * Used to specify what kind of stream will be used.
     */
    private StreamConfiguration createConfiguration(
            int width, int height, boolean useLocalAudio, boolean useHevc) {

        final StreamConfiguration.Builder builder = new StreamConfiguration.Builder();
        builder.setApp(new NvApp(app))
               .setResolution(width, height)
               .setRefreshRate(fps)
               .setBitrate(bitrateMbps * 1000)
               .enableLocalAudioPlayback(useLocalAudio);

        if (useHevc) {
            builder.setHevcSupported(true);
        }
        return builder.build();
    }

    public static void main(String[] args) throws Exception {
        new Main().run(args);
    }
}
