package kr.motd.gleamstream;

import static com.limelight.nvstream.input.ControllerPacket.BACK_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.SPECIAL_BUTTON_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.Y_FLAG;
import static kr.motd.gleamstream.Panic.panic;
import static org.lwjgl.glfw.GLFW.GLFW_BLUE_BITS;
import static org.lwjgl.glfw.GLFW.GLFW_CONNECTED;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;
import static org.lwjgl.glfw.GLFW.GLFW_DECORATED;
import static org.lwjgl.glfw.GLFW.GLFW_DISCONNECTED;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_GREEN_BITS;
import static org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1;
import static org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_LAST;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_GRAVE_ACCENT;
import static org.lwjgl.glfw.GLFW.GLFW_MAXIMIZED;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RED_BITS;
import static org.lwjgl.glfw.GLFW.GLFW_REFRESH_RATE;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetJoystickAxes;
import static org.lwjgl.glfw.GLFW.glfwGetJoystickButtons;
import static org.lwjgl.glfw.GLFW.glfwGetJoystickName;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwJoystickPresent;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetCharCallback;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
import static org.lwjgl.glfw.GLFW.glfwSetInputMode;
import static org.lwjgl.glfw.GLFW.glfwSetJoystickCallback;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;
import static org.lwjgl.glfw.GLFW.glfwSetScrollCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowPos;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.glfw.GLFWErrorCallback.getDescription;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_LIGHTING;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTexSubImage2D;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBlitFramebuffer;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.SpscArrayQueue;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.input.MouseButtonPacket;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ShortMap;
import it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import kr.motd.gleamstream.FFmpegFramePool.FFmpegFrame;
import kr.motd.gleamstream.gamepad.GamepadInput;
import kr.motd.gleamstream.gamepad.GamepadMapping;
import kr.motd.gleamstream.gamepad.GamepadMappings;
import kr.motd.gleamstream.gamepad.GamepadOutput;
import kr.motd.gleamstream.gamepad.GamepadState;

final class MainWindow {

    private static final int NV_MAX_NUM_GAMEPADS = 4;
    private static final int NV_STICK_MAX = 0x7FFE;
    private static final int NV_TRIGGER_MAX = 0xFF;

    private static volatile MainWindow currentWindow;

    static MainWindow current() {
        return currentWindow;
    }

    private static final Logger logger = LoggerFactory.getLogger(MainWindow.class);

    private final Queue<Runnable> pendingTasks = new MpscArrayQueue<>(64);
    private long window;

    // Fields for gamepad input
    private final GamepadMappings availableGamepadMappings;
    private final Int2ObjectMap<GamepadMapping> attachedGamepads;
    private final Int2ShortMap gamepadAssignments;
    private final GamepadState[] lastGamepadStates;
    private final IntSet knownMissingGamepadMappings;

    // Fields for mouse cursor position
    private double lastCursorXpos;
    private double lastCursorYpos;

    // Fields for video stream
    private final Queue<FFmpegFrame> pendingFrames = new SpscArrayQueue<>(64);
    private int frameTexture;
    private int frameBuffer;
    private FFmpegFrame lastFrame;

    // Fields for OSD
    private NuklearHelper nk;
    private final Osd osd = new Osd();
    private long lastGravePressTime = System.nanoTime();
    private boolean showOsd = true;

    // Fields for stats
    private long lastStatUpdateTime = System.nanoTime();
    private int osdFrameCounter;
    private int streamFrameCounter;
    private int droppedStreamFrameCounter;
    private long osdRenderTime;
    private long streamRenderTime;

    private volatile NvConnection nvConn;

    MainWindow(GamepadMappings availableGamepadMappings) {
        this.availableGamepadMappings = availableGamepadMappings;
        attachedGamepads = new Int2ObjectOpenHashMap<>();
        gamepadAssignments = new Int2ShortOpenHashMap();
        gamepadAssignments.defaultReturnValue((short) -1);
        lastGamepadStates = new GamepadState[NV_MAX_NUM_GAMEPADS];
        for (int i = 0; i < lastGamepadStates.length; i++) {
            lastGamepadStates[i] = new GamepadState();
        }
        knownMissingGamepadMappings = new IntOpenHashSet();
        currentWindow = this;
    }

    public Osd osd() {
        return osd;
    }

    public void addFrame(FFmpegFrame frame) {
        pendingFrames.add(frame);
    }

    public void setNvConnection(NvConnection nvConn) {
        this.nvConn = nvConn;
        pendingTasks.add(() -> setOsdVisibility(window, false));
    }

    private void setOsdVisibility(long window, boolean visible) {
        showOsd = visible;
        if (visible) {
            osd.follow();
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        } else {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        }
    }

    public void destroy() {
        pendingTasks.add(() -> glfwSetWindowShouldClose(window, true));
    }

    public void run() {
        try {
            init();
            loop();
        } finally {
            final Future<?> stopFuture;
            if (nvConn != null) {
                stopFuture = nvConn.stop();
            } else {
                stopFuture = CompletableFuture.completedFuture(null);
            }

            releaseLastFrame();

            if (nk != null) {
                nk.destroy();
            }

            if (frameBuffer != 0) {
                glDeleteFramebuffers(frameBuffer);
            }
            if (frameTexture != 0) {
                glDeleteTextures(frameTexture);
            }

            glfwTerminate();
            glfwSetErrorCallback(null).free();

            try {
                stopFuture.get();
            } catch (Exception e) {
                logger.warn("Failed to stop an NvConnection", e);
            }
        }
    }

    private void init() {
        GLFWErrorCallback.create(MainWindow::onError).set();

        if (!glfwInit()) {
            throw panic("Unable to initialize GLFW");
        }

        final long primaryMonitor = glfwGetPrimaryMonitor();
        if (primaryMonitor == NULL) {
            throw panic("Failed to get the primary monitor");
        }

        final GLFWVidMode videoMode = glfwGetVideoMode(primaryMonitor);
        if (videoMode == null) {
            throw panic("Failed to get the current video mode");
        }

        // Create a borderless full screen window.
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE);
        glfwWindowHint(GLFW_RED_BITS, videoMode.redBits());
        glfwWindowHint(GLFW_GREEN_BITS, videoMode.greenBits());
        glfwWindowHint(GLFW_BLUE_BITS, videoMode.blueBits());
        glfwWindowHint(GLFW_REFRESH_RATE, videoMode.refreshRate());
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        if (Platform.get() == Platform.MACOSX) {
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        }

        final int width = videoMode.width();
        final int height = videoMode.height();
        if (Platform.get() != Platform.MACOSX) {
            window = glfwCreateWindow(width, height, "GleamStream", NULL, NULL);
        }  else {
            // GLFW doesn't seem to support borderless fullscreen on OSX.
            window = glfwCreateWindow(width, height, "GleamStream", primaryMonitor, NULL);
        }
        if (window == NULL) {
            throw panic("Failed to create the main window");
        }

        glfwMakeContextCurrent(window);
        glfwSetWindowPos(window, 0, 0);
        glfwSwapInterval(1);
        glfwSetKeyCallback(window, this::onKey);
        glfwSetCharCallback(window, this::onChar);
        glfwSetJoystickCallback(this::onJoystick);
        glfwSetScrollCallback(window, this::onScroll);
        glfwSetCursorPosCallback(window, this::onCursorPos);
        glfwSetMouseButtonCallback(window, this::onMouseButton);

        GL.createCapabilities();
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_LIGHTING);
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        // Initialize Nuklear.
        nk = new NuklearHelper(window);
        nk.init();

        // Initialize the texture and the frame buffer for displaying the video stream.
        frameTexture = glGenTextures();
        frameBuffer = glGenFramebuffers();
        glBindTexture(GL_TEXTURE_2D, frameTexture);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, frameBuffer);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 2048, 2048, 0, GL_BGRA, GL_UNSIGNED_BYTE, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, frameTexture, 0);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);

        // Initialize the Joystick state
        for (int i = GLFW_JOYSTICK_1; i <= GLFW_JOYSTICK_LAST; i++) {
            if (attachedGamepads.containsKey(i)) {
                if (!glfwJoystickPresent(i)) {
                    onJoystick(i, GLFW_DISCONNECTED);
                }
            } else {
                if (glfwJoystickPresent(i)) {
                    onJoystick(i, GLFW_CONNECTED);
                }
            }
        }

        // Show the main window.
        glfwShowWindow(window);
    }

    private void loop() {
        final IntBuffer widthBuf = MemoryUtil.memAllocInt(1);
        final IntBuffer heightBuf = MemoryUtil.memAllocInt(1);
        try {
            while (!glfwWindowShouldClose(window)) {
                final NvConnection nvConn = this.nvConn;
                if (nvConn != null && nvConn.isStopped()) {
                    break;
                }

                handleGamepadInput(nvConn);

                // Get the width and height of the frame buffer.
                glfwGetFramebufferSize(window, widthBuf, heightBuf);
                final int fbWidth = widthBuf.get(0);
                final int fbHeight = heightBuf.get(0);

                // Set the viewport and clear.
                glViewport(0, 0, fbWidth, fbHeight);
                glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                handlePendingFrames(fbWidth, fbHeight);
                handlePendingTasks();

                if (showOsd) {
                    handleOsd(fbWidth, fbHeight, widthBuf, heightBuf);
                } else {
                    glfwSetKeyCallback(window, this::onKey);
                    glfwPollEvents();
                }

                updateStats();
                glfwSwapBuffers(window); // swap the color buffers
            }
        } finally {
            MemoryUtil.memFree(widthBuf);
            MemoryUtil.memFree(heightBuf);
        }
    }

    private void handleGamepadInput(NvConnection nvConn) {
        for (Int2ObjectMap.Entry<GamepadMapping> e : attachedGamepads.int2ObjectEntrySet()) {
            final int jid = e.getIntKey();
            final GamepadMapping mapping = e.getValue();
            final ByteBuffer buttons = glfwGetJoystickButtons(jid);
            if (buttons == null) {
                continue;
            }

            short nvJid = gamepadAssignments.get(jid);
            short buttonFlags = 0;
            short leftStickX  = 0;
            short leftStickY  = 0;
            short rightStickX = 0;
            short rightStickY = 0;
            byte leftTrigger  = 0;
            byte rightTrigger = 0;

            final int numButtons = buttons.remaining();
            for (int i = 0; i < numButtons; i++) {
                if (buttons.get(i) == GLFW_PRESS) {
                    final GamepadMapping.Entry mapped = mapping.mapButton(i);
                    if (mapped == GamepadMapping.MISSING) {
                        final int key = jid << 16 | i;
                        if (!knownMissingGamepadMappings.contains(key)) {
                            knownMissingGamepadMappings.add(key);
                            logger.warn("Missing gamepad button mapping for {}: {}",
                                        glfwGetJoystickName(jid), i);
                        }
                        continue;
                    }

                    if (mapped == GamepadMapping.IGNORED) {
                        continue;
                    }

                    switch (mapped.out()) {
                        case LS_LEFT:
                            leftStickX = -NV_STICK_MAX;
                            break;
                        case LS_RIGHT:
                            leftStickX = NV_STICK_MAX;
                            break;
                        case LS_DOWN:
                            leftStickY = -NV_STICK_MAX;
                            break;
                        case LS_UP:
                            leftStickY = NV_STICK_MAX;
                            break;
                        case RS_LEFT:
                            rightStickX = -NV_STICK_MAX;
                            break;
                        case RS_RIGHT:
                            rightStickX = NV_STICK_MAX;
                            break;
                        case RS_DOWN:
                            rightStickY = -NV_STICK_MAX;
                            break;
                        case RS_UP:
                            rightStickY = NV_STICK_MAX;
                            break;
                        case LT:
                            leftTrigger = (byte) NV_TRIGGER_MAX;
                            break;
                        case RT:
                            rightTrigger = (byte) NV_TRIGGER_MAX;
                            break;
                        default:
                            buttonFlags |= mapped.out().buttonFlag();
                            break;
                    }
                }
            }

            if (nvJid < 0) {
                // Assign the controller number on the first button press
                // so that unused gamepads are not assigned.
                if (buttonFlags != 0) {
                    for (short i = 0; i < NV_MAX_NUM_GAMEPADS; i++) {
                        if (!gamepadAssignments.containsValue(i)) {
                            nvJid = i;
                            gamepadAssignments.put(jid, i);
                            logger.info("Gamepad {} ({}) assigned to {}",
                                        jid, glfwGetJoystickName(jid), i);
                            break;
                        }
                    }

                    if (nvJid < 0) {
                        logger.warn("Failed to assign the controller {} ({})", jid, glfwGetJoystickName(jid));
                    }
                }
            }

            if (nvJid < 0) {
                continue;
            }

            final FloatBuffer axes = glfwGetJoystickAxes(jid);
            if (axes == null) {
                continue;
            }

            final int numAxes = axes.remaining();
            for (int i = 0; i < numAxes; i++) {
                final float value = axes.get(i);
                final GamepadMapping.Entry mapped = mapping.mapAxis(i, value);
                if (mapped == GamepadMapping.MISSING) {
                    final float absVal = Math.abs(value);
                    if (absVal >= 0.5) {
                        final int key = jid << 16 | (value >= 0 ? 0x0100 : 0x0200) | i;
                        if (!knownMissingGamepadMappings.contains(key)) {
                            knownMissingGamepadMappings.add(key);
                            logger.warn("Missing gamepad axis mapping for {} ({}): {}{}{}",
                                        jid, glfwGetJoystickName(jid), i, value > 0 ? "+" : "-", absVal);
                        }
                    }
                    continue;
                }

                if (mapped == GamepadMapping.IGNORED) {
                    continue;
                }

                final GamepadInput in = mapped.in();
                final GamepadOutput out = mapped.out();
                switch (out) {
                    case LS_LEFT:
                    case LS_RIGHT:
                        leftStickX = getGamepadStickValue(in, value);
                        break;
                    case LS_DOWN:
                    case LS_UP:
                        leftStickY = getGamepadStickValue(in, value);
                        break;
                    case RS_LEFT:
                    case RS_RIGHT:
                        rightStickX = getGamepadStickValue(in, value);
                        break;
                    case RS_DOWN:
                    case RS_UP:
                        rightStickY = getGamepadStickValue(in, value);
                        break;
                    case LT:
                        leftTrigger = getGamepadTriggerValue(in, value);
                        break;
                    case RT:
                        rightTrigger = getGamepadTriggerValue(in, value);
                        break;
                    default:
                        if (isGamepadButtonPressed(value)) {
                            buttonFlags |= out.buttonFlag();
                        }
                        break;
                }
            }

            // Apply dead zone.
            final float leftStickScalar = (float) Math.sqrt((double) leftStickX * leftStickX +
                                                            (double) leftStickY * leftStickY) / NV_STICK_MAX;
            final float rightStickScalar = (float) Math.sqrt((double) rightStickX * rightStickX +
                                                             (double) rightStickY * rightStickY) / NV_STICK_MAX;
            if (leftStickScalar < mapping.leftStickDeadZone()) {
                leftStickX = 0;
                leftStickY = 0;
            }
            if (rightStickScalar < mapping.rightStickDeadZone()) {
                rightStickX = 0;
                rightStickY = 0;
            }

            final GamepadState lastGamepadState = lastGamepadStates[nvJid];
            final short lastGamepadButtonFlags = lastGamepadState.buttonFlags();

            // Process the input only when there's a change.
            if (lastGamepadState.update(
                    buttonFlags, leftTrigger, rightTrigger,
                    leftStickX, leftStickY, rightStickX, rightStickY)) {

                if (nvConn != null) {
                    nvConn.sendControllerInput(
                            nvJid, buttonFlags, leftTrigger, rightTrigger,
                            leftStickX, leftStickY, rightStickX, rightStickY);
                }

                // Quit when OSD is visible and BACK+START is pressed.
                if (showOsd) {
                    if (buttonFlags == (BACK_FLAG | PLAY_FLAG) &&
                        (lastGamepadButtonFlags == 0 ||
                         lastGamepadButtonFlags == BACK_FLAG ||
                         lastGamepadButtonFlags == PLAY_FLAG)) {

                        glfwSetWindowShouldClose(window, true);
                    }
                }

                // Toggle OSD when SPECIAL+Y or BACK+Y is pressed.
                if (buttonFlags == (SPECIAL_BUTTON_FLAG | Y_FLAG) &&
                    (lastGamepadButtonFlags == 0 ||
                     lastGamepadButtonFlags == SPECIAL_BUTTON_FLAG) ||
                    buttonFlags == (BACK_FLAG | Y_FLAG) &&
                    (lastGamepadButtonFlags == 0 ||
                     lastGamepadButtonFlags == BACK_FLAG)) {

                    setOsdVisibility(window, !showOsd);
                }
            }
        }
    }

    private static boolean isGamepadButtonPressed(float value) {
        return Math.abs(value) > 0.5f;
    }

    private static short getGamepadStickValue(GamepadInput in, float value) {
        return (short) (Math.min(Math.max(getGamepadAxisValue(in, value), -1.0), 1.0) * NV_STICK_MAX);
    }

    private static byte getGamepadTriggerValue(GamepadInput in, float value) {
        return (byte) (Math.min(Math.max(getGamepadAxisValue(in, value), 0), 1.0) * NV_TRIGGER_MAX);
    }

    private static float getGamepadAxisValue(GamepadInput in, float value) {
        final float start = in.start();
        final float end = in.end();
        final float outputStart = in.outputStart();
        final float outputEnd = in.outputEnd();
        final float scalar;
        if (start < end) {
            scalar = (value - start) / (end - start);
        } else {
            scalar = (start - value) / (start - end);
        }

        float outValue;
        if (outputStart < outputEnd) {
            outValue = outputStart + (outputEnd - outputStart) * scalar;
        } else {
            outValue = outputStart - (outputStart - outputEnd) * scalar;
        }

        return outValue;
    }

    private void handlePendingFrames(int fbWidth, int fbHeight) {
        final int numFrames = pendingFrames.size();
        if (numFrames == 0) {
            if (lastFrame != null) {
                drawFrame(fbWidth, fbHeight, lastFrame);
            }
            return;
        }

        if (numFrames > 2) {
            for (int i = 1; i < numFrames; i++) {
                pendingFrames.poll().release();
                droppedStreamFrameCounter++;
            }
        }

        final FFmpegFrame e = pendingFrames.poll();
        releaseLastFrame();

        final long renderStartTime = System.nanoTime();

        drawFrame(fbWidth, fbHeight, e);
        lastFrame = e;
        streamFrameCounter++;

        streamRenderTime += System.nanoTime() - renderStartTime;
    }

    private void releaseLastFrame() {
        if (lastFrame != null) {
            lastFrame.release();
            lastFrame = null;
        }
    }
    private void drawFrame(int fbWidth, int fbHeight, FFmpegFrame e) {
        final int streamWidth = e.width();
        final int streamHeight = e.height();
        final int zoomedX;
        final int zoomedY;
        final int zoomedWidth;
        final int zoomedHeight;
        if (streamHeight * fbWidth > fbHeight * streamWidth) {
            zoomedWidth = streamWidth * fbHeight / streamHeight;
            zoomedHeight = fbHeight;
        } else {
            zoomedWidth = fbWidth;
            zoomedHeight = streamHeight * fbWidth / streamWidth;
        }
        zoomedX = fbWidth - zoomedWidth >>> 1;
        zoomedY = fbHeight - zoomedHeight >>> 1;

        glBindTexture(GL_TEXTURE_2D, frameTexture);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, frameBuffer);

        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, streamWidth, streamHeight,
                        GL_BGRA, GL_UNSIGNED_BYTE, e.dataAddress());
        glBlitFramebuffer(0, streamHeight, streamWidth, 0,
                          zoomedX, zoomedY,
                          zoomedWidth + zoomedX, zoomedHeight + zoomedY, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void handlePendingTasks() {
        for (;;) {
            final Runnable task = pendingTasks.poll();
            if (task == null) {
                break;
            }
            task.run();
        }
    }

    private void handleOsd(int fbWidth, int fbHeight, IntBuffer widthBuf, IntBuffer heightBuf) {
        final long renderStartTime = System.nanoTime();

        glfwGetWindowSize(window, widthBuf, heightBuf);
        final int width = widthBuf.get(0);
        final int height = heightBuf.get(0);
        nk.prepare();
        osd.layout(nk, width, height);
        nk.render(width, height, fbWidth, fbHeight);

        osdFrameCounter++;
        osdRenderTime += System.nanoTime() - renderStartTime;
    }

    private void updateStats() {
        final long currentTime = System.nanoTime();
        final long elapsedTime = currentTime - lastStatUpdateTime;
        if (elapsedTime > 2000000000) { // Update at every other second
            if (nvConn != null) {
                osd.setStatus(String.format(
                        "Stream[fps: %2.2f, drops: %2.2f, ms/f: %2.2f] OSD[fps: %2.2f, ms/f: %2.2f]",
                        streamFrameCounter * 1000000000.0 / elapsedTime,
                        droppedStreamFrameCounter * 1000000000.0 / elapsedTime,
                        streamFrameCounter != 0 ? streamRenderTime / 1000000.0 / streamFrameCounter : 0,
                        osdFrameCounter * 1000000000.0 / elapsedTime,
                        osdFrameCounter != 0 ? osdRenderTime / 1000000.0 / osdFrameCounter : 0));
            }
            streamFrameCounter = 0;
            droppedStreamFrameCounter = 0;
            streamRenderTime = 0;
            osdFrameCounter = 0;
            osdRenderTime = 0;
            lastStatUpdateTime = currentTime;
        }
    }

    private void onKey(long window, int key, int scancode, int action, int mods) {
        if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE && showOsd) {
            glfwSetWindowShouldClose(window, true);
            return;
        }

        if (key == GLFW_KEY_GRAVE_ACCENT && action == GLFW_RELEASE) {
            // Toggle the OSD if the grave (backquote) key was pressed twice within one second.
            final long currentTime = System.nanoTime();
            if (currentTime - lastGravePressTime < 1000000000L) {
                setOsdVisibility(window, !showOsd);
                return;
            }
            lastGravePressTime = currentTime;
        }

        if (showOsd) {
            nk.onKey(window, key, scancode, action, mods);
        } else if (nvConn != null) {
            final short nvKey = KeyTranslator.translate(key);
            if (nvKey == 0) {
                return;
            }

            byte nvMods = 0x0;
            if ((mods & GLFW_MOD_SHIFT) != 0) {
                nvMods |= KeyboardPacket.MODIFIER_SHIFT;
            }
            if ((mods & GLFW_MOD_CONTROL) != 0) {
                nvMods |= KeyboardPacket.MODIFIER_CTRL;
            }
            if ((mods & GLFW_MOD_ALT) != 0) {
                nvMods |= KeyboardPacket.MODIFIER_ALT;
            }

            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                nvConn.sendKeyboardInput(nvKey, KeyboardPacket.KEY_DOWN, nvMods);
            } else {
                nvConn.sendKeyboardInput(nvKey, KeyboardPacket.KEY_UP, nvMods);
            }
        }
    }

    private void onChar(long window, int codepoint) {
        if (showOsd) {
            nk.onChar(window, codepoint);
        }
    }

    private void onCursorPos(long window, double xpos, double ypos) {
        if (showOsd) {
            nk.onCursorPos(window, xpos, ypos);
        } else if (nvConn != null) {
            nvConn.sendMouseMove((short) (xpos - lastCursorXpos), (short) (ypos - lastCursorYpos));
            lastCursorXpos = xpos;
            lastCursorYpos = ypos;
        }
    }

    private void onMouseButton(long window, int button, int action, int mods) {
        if (showOsd) {
            nk.onMouseButton(window, button, action, mods);
        } else if (nvConn != null) {
            final byte nvButton;
            switch (button) {
                case GLFW_MOUSE_BUTTON_RIGHT:
                    nvButton = MouseButtonPacket.BUTTON_RIGHT;
                    break;
                case GLFW_MOUSE_BUTTON_MIDDLE:
                    nvButton = MouseButtonPacket.BUTTON_MIDDLE;
                    break;
                default:
                    nvButton = MouseButtonPacket.BUTTON_LEFT;
            }
            if (action == GLFW_PRESS) {
                nvConn.sendMouseButtonDown(nvButton);
            } else {
                nvConn.sendMouseButtonUp(nvButton);
            }
        }
    }

    private void onScroll(long window, double xoffset, double yoffset) {
        if (showOsd) {
            nk.onScroll(window, xoffset, yoffset);
        } else if (nvConn != null) {
            nvConn.sendMouseScroll((byte) -yoffset);
        }
    }

    private void onJoystick(int jid, int event) {
        switch (event) {
            case GLFW_CONNECTED:
                final String name = glfwGetJoystickName(jid);
                final GamepadMapping mapping = availableGamepadMappings.find(name);
                attachedGamepads.put(jid, mapping);
                logger.info("Gamepad {} ({}) attached; using mapping '{}'", jid, name, mapping.id());
                break;
            case GLFW_DISCONNECTED:
                attachedGamepads.remove(jid);
                gamepadAssignments.remove(jid);
                logger.info("Gamepad {} detached", jid);
                break;
        }
    }

    private static void onError(int error, long description) {
        throw panic(String.format("[0x%X]: %s", error, getDescription(description)));
    }
}
