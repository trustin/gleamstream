package kr.motd.gleamstream;

import static java.lang.System.exit;
import static kr.motd.gleamstream.Panic.panic;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.lwjgl.system.Library;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.Util;
import com.limelight.nvstream.av.audio.AudioStream;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.enet.EnetConnection;
import com.limelight.nvstream.http.CryptoProvider;
import com.limelight.nvstream.http.DefaultCryptoProvider;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;

public final class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @Parameter(
            names = "-connect",
            description = "Connects to the specified IP address or hostname (e.g. -c 192.168.0.100)")
    private String connectHost;

    @Parameter(
            names = "-pair",
            description = "Pairs with the specified IP address or hostname (e.g. -p 192.168.0.100)")
    private String pairHost;

    @Parameter(
            names = "-list",
            description = "Lists the applications available in the specified IP address or hostname")
    private String listHost;

    @Parameter(
            names = "-quit",
            description = "Quits the running application in the specified IP address or hostname")
    private String quitHost;

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

    @Parameter(names = "-appname", description = "The name of the application to launch")
    private String appName = "Steam";

    @Parameter(names = "-appid", description = "The ID of the application to launch")
    private Integer appId;

    @Parameter(names = { "-help", "-h" }, description = "Prints the usage", help = true)
    private Boolean help;

    private Main() {}

    private void run(String[] args) throws Exception {
        final JCommander commander = new JCommander(this);
        commander.setProgramName("gleamstream");

        try {
            commander.parse(args);
        } catch (ParameterException ignored) {
            System.err.println("Invalid arguments: " + String.join(", ", args));
            help = true;
        }

        if (args.length == 0) {
            help = true;
        } else if (Util.countNonNull(connectHost, pairHost, listHost, quitHost) == 0) {
            System.err.println("-connect, -pair or -quit must be specified.");
            help = true;
        } else if (Util.countNonNull(connectHost, pairHost, listHost, quitHost) != 1) {
            System.err.println("-connect, -pair, -list and -quit cannot be specified with each other.");
            help = true;
        } else if (resolution != 1080 && resolution != 720) {
            System.err.println("The value of -res option must be 1080 or 720.");
            help = true;
        } else if (fps != 60 && fps != 30) {
            System.err.println("The value of -fps option must be 60 or 30.");
            help = true;
        }

        if (Boolean.TRUE.equals(help)) {
            final StringBuilder buf = new StringBuilder();
            commander.usage(buf);
            System.err.print(buf);
            System.err.flush();
            exit(1);
            return;
        }

        initialize();

        final Preferences prefs = new Preferences();
        if (connectHost != null) {
            connect(prefs, resolution != 720, Boolean.TRUE.equals(useLocalAudio));
        } else if (pairHost != null) {
            pair(prefs);
        } else if (listHost != null) {
            list(prefs);
        } else {
            quit(prefs);
        }
    }

    private static void initialize() {
        Thread.setDefaultUncaughtExceptionHandler((thread, cause) -> {
            throw panic(cause);
        });

        // Load all native libraries.
        logger.info("Loading native libraries");
        Library.initialize();
        FFmpegVideoDecoderRenderer.initNativeLibraries();
        AudioStream.initNativeLibraries();
        EnetConnection.initNativeLibraries();
    }

    private void connect(Preferences prefs, boolean use1080p, boolean useLocalAudio) throws Exception {
        final MainWindow window = new MainWindow(prefs.gamepadMappings());

        final Thread thread = new Thread(() -> {
            final int width;
            final int height;
            if (use1080p) {
                width = 1920;
                height = 1080;
            } else {
                width = 1280;
                height = 720;
            }

            StreamConfiguration streamConfig = createConfiguration(
                    width, height, useLocalAudio, Boolean.TRUE.equals(useHevc));

            final NvConnection conn = new NvConnection(connectHost, prefs.uniqueId(),
                                                       new DefaultNvConnectionListener(window),
                                                       streamConfig, new DefaultCryptoProvider());
            addShutdownHook(conn);

            try {
                conn.start(VideoDecoderRenderer.FLAG_PREFER_QUALITY,
                           new OpenAlAudioRenderer(),
                           new FFmpegVideoDecoderRenderer(window, width, height));
            } catch (UnknownHostException e) {
                throw panic("Failed to connect to the server", e);
            }

            window.setNvConnection(conn);
        });
        thread.setName("NvConnection Starter");
        thread.start();

        // NB: GLFW event loop must be run on the main thread.
        window.osd().setProgress("Initializing");
        window.run();
        Panic.enableGui();
    }

    private void pair(Preferences prefs) throws Exception {
        final CryptoProvider crypto = new DefaultCryptoProvider();
        final NvHTTP nvHttp = new NvHTTP(InetAddress.getByName(pairHost), prefs.uniqueId(), crypto);
        final String serverInfo = nvHttp.getServerInfo();
        if (nvHttp.getPairState(serverInfo) == PairState.PAIRED) {
            logger.info("Paired already with {}", pairHost);
            return;
        }

        if (nvHttp.getCurrentGame(serverInfo) != 0) {
            logger.warn("Server is currently in a game. Close it before pairing.");
            return;
        }

        final String pin = PairingManager.generatePinString();

        logger.info("Pairing with {}; enter the PIN '{}' on the server ..", pairHost, pin);
        final PairState state = nvHttp.pair(serverInfo, pin);

        switch (state) {
            case NOT_PAIRED:
                logger.warn("Not paired with {}", pairHost);
                break;
            case PAIRED:
                logger.info("Paired successfully with {}", pairHost);
                break;
            case PIN_WRONG:
                logger.warn("Wrong PIN");
                break;
            case FAILED:
                logger.warn("Failed to pair with {}", pairHost);
                break;
            case ALREADY_IN_PROGRESS:
                logger.warn("Other device is pairing with {} already.", pairHost);
                break;
        }
    }

    private void list(Preferences prefs) throws Exception {
        final CryptoProvider crypto = new DefaultCryptoProvider();
        final NvHTTP nvHttp = new NvHTTP(InetAddress.getByName(quitHost), prefs.uniqueId(), crypto);
        nvHttp.getAppList().forEach(app -> System.out.println(app.getAppId() + "=" + app.getAppName()));
    }

    private void quit(Preferences prefs) throws Exception {
        final CryptoProvider crypto = new DefaultCryptoProvider();
        final NvHTTP nvHttp = new NvHTTP(InetAddress.getByName(quitHost), prefs.uniqueId(), crypto);
        logger.info("Quitting the current session at {} ..", quitHost);
        nvHttp.quitApp();
        logger.info("Done");
    }

    /*
     * Creates a StreamConfiguration given a Resolution.
     * Used to specify what kind of stream will be used.
     */
    private StreamConfiguration createConfiguration(
            int width, int height, boolean useLocalAudio, boolean useHevc) {

        final NvApp app;
        if (appId != null) {
            app = new NvApp("", appId);
        } else {
            app = new NvApp(appName);
        }

        final StreamConfiguration.Builder builder = new StreamConfiguration.Builder();
        builder.setApp(app)
               .setResolution(width, height)
               .setRefreshRate(fps)
               .setBitrate(bitrateMbps * 1000)
               .enableLocalAudioPlayback(useLocalAudio);

        if (useHevc) {
            builder.setHevcSupported(true);
        }
        return builder.build();
    }

    private static void addShutdownHook(NvConnection conn) {
        final Thread connStopper = new Thread(() -> {
            try {
                conn.stop().get();
            } catch (Exception e) {
                logger.warn("Failed to stop an NvConnection", e);
            }
        });
        connStopper.setName("NvConnection stopper");
        Runtime.getRuntime().addShutdownHook(connStopper);
    }

    public static void main(String[] args) throws Exception {
        new Main().run(args);
    }

    private static class DefaultNvConnectionListener implements NvConnectionListener {

        private final MainWindow window;

        DefaultNvConnectionListener(MainWindow window) {
            this.window = window;
        }

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

            window.osd().setProgress(message);
            logger.info(message);
        }

        @Override
        public void stageComplete(Stage stage) {
            window.osd().clear();
        }

        @Override
        public void stageFailed(Stage stage) {
            logger.error("Stage failed: {}", stage);
            window.destroy();
        }
    }
}
