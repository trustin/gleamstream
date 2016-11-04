package com.limelight.nvstream;

import static kr.motd.gleamstream.Panic.panic;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;

import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.audio.AudioStream;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer.VideoFormat;
import com.limelight.nvstream.av.video.VideoStream;
import com.limelight.nvstream.control.ControlStream;
import com.limelight.nvstream.http.CryptoProvider;
import com.limelight.nvstream.http.GfeHttpResponseException;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.input.ControllerStream;
import com.limelight.nvstream.rtsp.RtspConnection;

public class NvConnection {

    private static final Logger logger = LoggerFactory.getLogger(NvConnection.class);

    // Context parameters
    private final String host;
    private final CryptoProvider cryptoProvider;
    private final String uniqueId;
    private final ConnectionContext context;

    // Stream objects
    private ControlStream controlStream;
    private ControllerStream inputStream;
    private VideoStream videoStream;
    private AudioStream audioStream;

    // Start parameters
    private int drFlags;
    private AudioRenderer audioRenderer;

    private volatile boolean stopped;

    public NvConnection(String host, String uniqueId, NvConnectionListener listener, StreamConfiguration config,
                        CryptoProvider cryptoProvider) {
        this.host = host;
        this.cryptoProvider = cryptoProvider;
        this.uniqueId = uniqueId;

        context = new ConnectionContext();
        context.connListener = listener;
        context.streamConfig = config;
        try {
            // This is unique per connection
            context.riKey = generateRiAesKey();
        } catch (NoSuchAlgorithmException e) {
            // Should never happen
            throw panic(e);
        }

        context.riKeyId = generateRiKeyId();

        context.negotiatedVideoFormat = VideoFormat.Unknown;
    }

    private static SecretKey generateRiAesKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");

        // RI keys are 128 bits
        keyGen.init(128);

        return keyGen.generateKey();
    }

    private static int generateRiKeyId() {
        return new SecureRandom().nextInt();
    }

    public boolean isStopped() {
        return stopped;
    }

    public Future<?> stop() {
        return ForkJoinPool.commonPool().submit(() -> {
            synchronized (this) {
                try {
                    if (inputStream != null) {
                        inputStream.abort();
                        inputStream = null;
                    }

                    if (audioStream != null) {
                        audioStream.abort();
                        audioStream = null;
                    }

                    if (videoStream != null) {
                        videoStream.abort();
                        videoStream = null;
                    }

                    if (controlStream != null) {
                        controlStream.abort();
                        controlStream = null;
                    }
                } finally {
                    stopped = true;
                }
            }
        });
    }

    private boolean startApp() throws XmlPullParserException, IOException {
        NvHTTP h = new NvHTTP(context.serverAddress, uniqueId, cryptoProvider);

        String serverInfo = h.getServerInfo();

        context.serverAppVersion = h.getServerAppVersionQuad(serverInfo);
        if (context.serverAppVersion == null) {
            throw panic("Server version malformed");
        }

        int majorVersion = context.serverAppVersion[0];
        logger.info("Server major version: " + majorVersion);

        if (majorVersion == 0) {
            throw panic("Server version malformed");
        }
        if (majorVersion < 3) {
            // Even though we support major version 3 (2.1.x), GFE 2.2.2 is preferred.
            throw panic(
                    "This app requires GeForce Experience 2.2.2 or later. Please upgrade GFE on your PC and try again.");
        }
        if (majorVersion > 7) {
            // Warn the user but allow them to continue
            logger.warn("This version of GFE is not currently supported. You may experience issues until this app is updated.");
        }

        switch (majorVersion) {
            case 3:
                context.serverGeneration = ConnectionContext.SERVER_GENERATION_3;
                break;
            case 4:
                context.serverGeneration = ConnectionContext.SERVER_GENERATION_4;
                break;
            case 5:
                context.serverGeneration = ConnectionContext.SERVER_GENERATION_5;
                break;
            case 6:
                context.serverGeneration = ConnectionContext.SERVER_GENERATION_6;
                break;
            case 7:
            default:
                context.serverGeneration = ConnectionContext.SERVER_GENERATION_7;
                break;
        }

        if (h.getPairState(serverInfo) != PairingManager.PairState.PAIRED) {
            throw panic("Device not paired with computer");
        }

        //
        // Decide on negotiated stream parameters now
        //

        // Check for a supported stream resolution
        if (context.streamConfig.getHeight() >= 2160 && !h.supports4K(serverInfo)) {
            // Client wants 4K but the server can't do it
            logger.warn(
                    "Your PC does not have a supported GPU or GFE version for 4K streaming. The stream will be 1080p.");

            // Lower resolution to 1080p
            context.negotiatedWidth = 1920;
            context.negotiatedHeight = 1080;
            context.negotiatedFps = context.streamConfig.getRefreshRate();
        } else if (context.streamConfig.getHeight() >= 2160 && context.streamConfig.getRefreshRate() >= 60 && !h
                .supports4K60(serverInfo)) {
            // Client wants 4K 60 FPS but the server can't do it
            logger.warn(
                    "Your GPU does not support 4K 60 FPS streaming. The stream will be 4K 30 FPS.");

            context.negotiatedWidth = context.streamConfig.getWidth();
            context.negotiatedHeight = context.streamConfig.getHeight();
            context.negotiatedFps = 30;
        } else {
            // Take what the client wanted
            context.negotiatedWidth = context.streamConfig.getWidth();
            context.negotiatedHeight = context.streamConfig.getHeight();
            context.negotiatedFps = context.streamConfig.getRefreshRate();
        }

        //
        // Video stream format will be decided during the RTSP handshake
        //

        NvApp app = context.streamConfig.getApp();

        // If the client did not provide an exact app ID, do a lookup with the app list
        if (!context.streamConfig.getApp().isInitialized()) {
            app = h.getAppByName(context.streamConfig.getApp().getAppName());
            if (app == null) {
                throw panic(
                        "The app " + context.streamConfig.getApp().getAppName() + " is not in GFE app list");
            }
            logger.info("Please use '-appid {}' option instead of '-appname' for faster startup.", app.getAppId());
        }

        // If there's a game running, resume it
        if (h.getCurrentGame(serverInfo) != 0) {
            try {
                if (h.getCurrentGame(serverInfo) == app.getAppId()) {
                    if (!h.resumeApp(context)) {
                        throw panic("Failed to resume existing session");
                    }
                } else {
                    return quitAndLaunch(h, app);
                }
            } catch (GfeHttpResponseException e) {
                if (e.getErrorCode() == 470) {
                    // This is the error you get when you try to resume a session that's not yours.
                    // Because this is fairly common, we'll display a more detailed message.
                    throw panic("This session wasn't started by this device," +
                                " so it cannot be resumed. End streaming on the original " +
                                "device or the PC itself and try again. (Error code: " +
                                e.getErrorCode() + ')');
                } else if (e.getErrorCode() == 525) {
                    throw panic(
                            "The application is minimized. Resume it on the PC manually or " +
                            "quit the session and start streaming again.");
                } else {
                    throw e;
                }
            }

            logger.info("Resumed an existing session");
            return true;
        } else {
            return launchNotRunningApp(h, app);
        }
    }

    protected boolean quitAndLaunch(NvHTTP h, NvApp app) throws IOException, XmlPullParserException {
        try {
            logger.info("Quitting the previous session ..");
            if (!h.quitApp()) {
                throw panic(
                        "Failed to quit previous session! You must quit it manually");
            }
        } catch (GfeHttpResponseException e) {
            if (e.getErrorCode() == 599) {
                throw panic("This session wasn't started by this device," +
                            " so it cannot be quit. End streaming on the original " +
                            "device or the PC itself. (Error code: " + e.getErrorCode() + ')');
            } else {
                throw e;
            }
        }

        return launchNotRunningApp(h, app);
    }

    private boolean launchNotRunningApp(NvHTTP h, NvApp app)
            throws IOException, XmlPullParserException {
        logger.info("Launching a new session ..");
        // Launch the app since it's not running
        if (!h.launchApp(context, app.getAppId())) {
            throw panic("Failed to launch application");
        }

        logger.info("Launched a new session");

        return true;
    }

    private boolean doRtspHandshake() throws IOException {
        RtspConnection r = new RtspConnection(context);
        r.doRtspHandshake();
        return true;
    }

    private boolean startControlStream() throws IOException {
        controlStream = new ControlStream(this, context);
        controlStream.initialize();
        controlStream.start();
        return true;
    }

    private boolean startVideoStream() throws IOException {
        videoStream = new VideoStream(this, context, controlStream);
        return videoStream.startVideoStream(drFlags);
    }

    private boolean startAudioStream() throws IOException {
        audioStream = new AudioStream(this, context, audioRenderer);
        return audioStream.startAudioStream();
    }

    private boolean startInputConnection() throws IOException {
        // Because input events can be delivered at any time, we must only assign
        // it to the instance variable once the object is properly initialized.
        // This avoids the race where inputStream != null but inputStream.initialize()
        // has not returned yet.
        ControllerStream tempController = new ControllerStream(this, context);
        tempController.initialize(controlStream);
        tempController.start();
        inputStream = tempController;
        return true;
    }

    private void establishConnection() {
        for (NvConnectionListener.Stage currentStage : NvConnectionListener.Stage.values()) {
            boolean success = false;
            context.connListener.stageStarting(currentStage);
            try {
                switch (currentStage) {
                    case LAUNCH_APP:
                        success = startApp();
                        break;

                    case RTSP_HANDSHAKE:
                        success = doRtspHandshake();
                        break;

                    case CONTROL_START:
                        success = startControlStream();
                        break;

                    case VIDEO_START:
                        success = startVideoStream();
                        break;

                    case AUDIO_START:
                        success = startAudioStream();
                        break;

                    case INPUT_START:
                        success = startInputConnection();
                        break;
                }
            } catch (Exception e) {
                throw panic(e);
            }

            if (success) {
                context.connListener.stageComplete(currentStage);
            } else {
                context.connListener.stageFailed(currentStage);
                return;
            }
        }

        logger.info("Connection has been started");
    }

    public void start(int drFlags, AudioRenderer audioRenderer, VideoDecoderRenderer videoDecoderRenderer)
            throws UnknownHostException {

        this.drFlags = drFlags;
        this.audioRenderer = audioRenderer;
        context.videoDecoderRenderer = videoDecoderRenderer;

        boolean success = false;
        try {
            context.serverAddress = InetAddress.getByName(host);
            establishConnection();
            success = true;
        } finally {
            if (!success) {
                try {
                    stop().get();
                } catch (Exception e) {
                    logger.warn("Failed to stop an NvConnection", e);
                }
            }
        }
    }

    public void sendMouseMove(final short deltaX, final short deltaY) {
        if (inputStream == null) { return; }

        inputStream.sendMouseMove(deltaX, deltaY);
    }

    public void sendMouseButtonDown(final byte mouseButton) {
        if (inputStream == null) { return; }

        inputStream.sendMouseButtonDown(mouseButton);
    }

    public void sendMouseButtonUp(final byte mouseButton) {
        if (inputStream == null) { return; }

        inputStream.sendMouseButtonUp(mouseButton);
    }

    public void sendControllerInput(final short controllerNumber,
                                    final short buttonFlags,
                                    final byte leftTrigger, final byte rightTrigger,
                                    final short leftStickX, final short leftStickY,
                                    final short rightStickX, final short rightStickY) {
        if (inputStream == null) { return; }

        inputStream.sendControllerInput(controllerNumber, buttonFlags, leftTrigger,
                                        rightTrigger, leftStickX, leftStickY,
                                        rightStickX, rightStickY);
    }

    public void sendControllerInput(final short buttonFlags,
                                    final byte leftTrigger, final byte rightTrigger,
                                    final short leftStickX, final short leftStickY,
                                    final short rightStickX, final short rightStickY) {
        if (inputStream == null) { return; }

        inputStream.sendControllerInput(buttonFlags, leftTrigger,
                                        rightTrigger, leftStickX, leftStickY,
                                        rightStickX, rightStickY);
    }

    public void sendKeyboardInput(final short keyMap, final byte keyDirection, final byte modifier) {
        if (inputStream == null) { return; }

        inputStream.sendKeyboardInput(keyMap, keyDirection, modifier);
    }

    public void sendMouseScroll(final byte scrollClicks) {
        if (inputStream == null) { return; }

        inputStream.sendMouseScroll(scrollClicks);
    }

    public VideoFormat getActiveVideoFormat() {
        return context.negotiatedVideoFormat;
    }
}
