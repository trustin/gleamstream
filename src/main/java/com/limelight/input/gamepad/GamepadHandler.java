package com.limelight.input.gamepad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.limelight.input.gamepad.GamepadMapping.Mapping;
import com.limelight.input.gamepad.SourceComponent.Direction;
import com.limelight.input.gamepad.SourceComponent.Type;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.settings.GamepadSettingsManager;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Represents a gamepad connected to the system
 * @author Diego Waxemberg
 */
public class GamepadHandler implements GamepadListener {

    private static final Logger logger = LoggerFactory.getLogger(GamepadHandler.class);

    private class Gamepad {
        public short controllerNumber;

        public GamepadMapping mapping;

        public short buttonFlags;
        public byte leftTrigger;
        public byte rightTrigger;
        public short rightStickX;
        public short rightStickY;
        public short leftStickX;
        public short leftStickY;

        public void assignControllerNumber() {
            for (short i = 0; i < 4; i++) {
                if ((currentControllers & 1 << i) == 0) {
                    currentControllers |= 1 << i;
                    logger.info("Assigned controller " + i);
                    controllerNumber = i;
                    return;
                }
            }

            controllerNumber = 0;
            logger.info("No controller numbers left; using 0");
        }

        public void releaseControllerNumber() {
            logger.info("Controller " + controllerNumber + " is now available");
            currentControllers &= ~(1 << controllerNumber);
        }
    }

    private final NvConnection conn;
    private final Int2ObjectMap<Gamepad> gamepads = new Int2ObjectOpenHashMap<>();
    private int currentControllers;

    public GamepadHandler(NvConnection conn) {
        this.conn = conn;
    }

    private Gamepad getGamepad(int deviceId, boolean create) {
        Gamepad gamepad = gamepads.get(deviceId);
        if (gamepad != null) {
            return gamepad;
        }

        if (!create) {
            return null;
        }

        gamepad = new Gamepad();
        gamepad.mapping = GamepadSettingsManager.getSettings();
        gamepad.assignControllerNumber();
        gamepads.put(deviceId, gamepad);
        return gamepad;
    }

    @Override
    public void handleButton(int deviceId, int buttonId, boolean pressed) {
        final Gamepad gamepad = getGamepad(deviceId, true);
        final Mapping mapped = gamepad.mapping.get(new SourceComponent(Type.BUTTON, buttonId, null));
        if (mapped == null) {
            //LimeLog.info("Unmapped button pressed: " + buttonId);
            return;
        }

        if (!mapped.padComp.isAnalog()) {
            handleDigitalComponent(gamepad, mapped, pressed);
        } else {
            handleAnalogComponent(gamepad, mapped.padComp, sanitizeValue(mapped, pressed));
        }

        //used for debugging
        //printInfo(device, new SourceComponent(Type.BUTTON, buttonId), mapped.padComp, pressed ? 1F : 0F);
    }

    @Override
    public void handleAxis(int deviceId, int axisId, float newValue, float lastValue) {
        final Gamepad gamepad = getGamepad(deviceId, false);
        if (gamepad == null) {
            return;
        }

        final Direction mappedDir;
        if (newValue == 0) {
            if (lastValue > 0) {
                mappedDir = Direction.POSITIVE;
            } else {
                mappedDir = Direction.NEGATIVE;
            }
        } else {
            mappedDir = newValue > 0 ? Direction.POSITIVE : Direction.NEGATIVE;
        }

        final Mapping mapped = gamepad.mapping.get(new SourceComponent(Type.AXIS, axisId, mappedDir));
        if (mapped == null) {
            //LimeLog.info("Unmapped axis moved: " + axisId);
            return;
        }

        final float value = sanitizeValue(mapped, newValue);
        if (mapped.padComp.isAnalog()) {
            handleAnalogComponent(gamepad, mapped.padComp, value);
        } else {
            handleDigitalComponent(gamepad, mapped, Math.abs(value) > 0.5);
        }

        //used for debugging
        //printInfo(device, new SourceComponent(Type.AXIS, axisId, mappedDir), mapped.padComp, newValue);
    }

    private static float sanitizeValue(Mapping mapped, boolean value) {
        if (mapped.invert) {
            return value ? 0F : 1F;
        } else {
            return value ? 1F : 0F;
        }
    }

    private static float sanitizeValue(Mapping mapped, float value) {
        float retVal = value;
        if (mapped.invert) {
            retVal = -retVal;
        }
        if (mapped.trigger) {
            retVal = (retVal + 1) / 2;
        }
        return retVal;
    }

    private void handleAnalogComponent(Gamepad gamepad, GamepadComponent padComp, float value) {
        switch (padComp) {
            case LS_RIGHT:
                gamepad.leftStickX = (short) (Math.abs(value) * 0x7FFE);
                break;
            case LS_LEFT:
                gamepad.leftStickX = (short) (-Math.abs(value) * 0x7FFE);
                break;
            case LS_UP:
                gamepad.leftStickY = (short) (Math.abs(value) * 0x7FFE);
                break;
            case LS_DOWN:
                gamepad.leftStickY = (short) (-Math.abs(value) * 0x7FFE);
                break;
            case RS_UP:
                gamepad.rightStickY = (short) (Math.abs(value) * 0x7FFE);
                break;
            case RS_DOWN:
                gamepad.rightStickY = (short) (-Math.abs(value) * 0x7FFE);
                break;
            case RS_RIGHT:
                gamepad.rightStickX = (short) (Math.abs(value) * 0x7FFE);
                break;
            case RS_LEFT:
                gamepad.rightStickX = (short) (-Math.abs(value) * 0x7FFE);
                break;
            case LT:
                // HACK: Fix polling so we don't have to do this
                if (Math.abs(value) < 0.9) {
                    value = 0;
                }
                gamepad.leftTrigger = (byte) (Math.abs(value) * 0xFF);
                break;
            case RT:
                // HACK: Fix polling so we don't have to do this
                if (Math.abs(value) < 0.9) {
                    value = 0;
                }
                gamepad.rightTrigger = (byte) (Math.abs(value) * 0xFF);
                break;
            default:
                logger.warn("A mapping error has occurred. Ignoring: " + padComp.name());
                break;
        }

        sendControllerPacket(gamepad);
    }

    private void handleDigitalComponent(Gamepad gamepad, Mapping mapped, boolean pressed) {
        switch (mapped.padComp) {
            case BTN_A:
                toggleButton(gamepad, ControllerPacket.A_FLAG, pressed);
                break;
            case BTN_X:
                toggleButton(gamepad, ControllerPacket.X_FLAG, pressed);
                break;
            case BTN_Y:
                toggleButton(gamepad, ControllerPacket.Y_FLAG, pressed);
                break;
            case BTN_B:
                toggleButton(gamepad, ControllerPacket.B_FLAG, pressed);
                break;
            case DPAD_UP:
                toggleButton(gamepad, ControllerPacket.UP_FLAG, pressed);
                break;
            case DPAD_DOWN:
                toggleButton(gamepad, ControllerPacket.DOWN_FLAG, pressed);
                break;
            case DPAD_LEFT:
                toggleButton(gamepad, ControllerPacket.LEFT_FLAG, pressed);
                break;
            case DPAD_RIGHT:
                toggleButton(gamepad, ControllerPacket.RIGHT_FLAG, pressed);
                break;
            case LS_THUMB:
                toggleButton(gamepad, ControllerPacket.LS_CLK_FLAG, pressed);
                break;
            case RS_THUMB:
                toggleButton(gamepad, ControllerPacket.RS_CLK_FLAG, pressed);
                break;
            case LB:
                toggleButton(gamepad, ControllerPacket.LB_FLAG, pressed);
                break;
            case RB:
                toggleButton(gamepad, ControllerPacket.RB_FLAG, pressed);
                break;
            case BTN_START:
                toggleButton(gamepad, ControllerPacket.PLAY_FLAG, pressed);
                break;
            case BTN_BACK:
                toggleButton(gamepad, ControllerPacket.BACK_FLAG, pressed);
                break;
            case BTN_SPECIAL:
                toggleButton(gamepad, ControllerPacket.SPECIAL_BUTTON_FLAG, pressed);
                break;
            default:
                logger.warn("A mapping error has occurred. Ignoring: " + mapped.padComp.name());
                return;
        }

        sendControllerPacket(gamepad);
    }

    /*
     * Sends a controller packet to the specified connection containing the current gamepad values
     */
    private void sendControllerPacket(Gamepad gamepad) {
        if (conn != null) {
            conn.sendControllerInput(gamepad.controllerNumber, gamepad.buttonFlags, gamepad.leftTrigger,
                                     gamepad.rightTrigger,
                                     gamepad.leftStickX, gamepad.leftStickY, gamepad.rightStickX,
                                     gamepad.rightStickY);
        }
    }

    /*
     * Prints out the specified event information for the given gamepad
     * used for debugging, normally unused.
     */
    @SuppressWarnings("unused")
    private static void printInfo(int deviceId, SourceComponent sourceComp, GamepadComponent padComp,
                                  float value) {

        StringBuilder builder = new StringBuilder();

        builder.append(sourceComp.getType().name() + ": ");
        builder.append(sourceComp.getId() + " ");
        builder.append("mapped to: " + padComp + ' ');
        builder.append("changed to " + value);

        logger.info(builder.toString());
    }

    /*
     * Toggles a flag that indicates the specified button was pressed or released
     */
    private static void toggleButton(Gamepad gamepad, short button, boolean pressed) {
        if (pressed) {
            gamepad.buttonFlags |= button;
        } else {
            gamepad.buttonFlags &= ~button;
        }
    }

    @Override
    public void handleDeviceAdded(int deviceId) {
        // Do not create the gamepad object until the first button press.
    }

    @Override
    public void handleDeviceRemoved(int deviceId) {
        final Gamepad gamepad = getGamepad(deviceId, false);
        if (gamepad != null) {
            gamepad.releaseControllerNumber();
            gamepads.remove(deviceId);
        }
    }
}
