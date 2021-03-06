package kr.motd.gleamstream.gamepad;

import static com.limelight.nvstream.input.ControllerPacket.A_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.BACK_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.B_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.DOWN_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.LB_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.LEFT_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.LS_CLK_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.RB_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.RIGHT_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.RS_CLK_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.SPECIAL_BUTTON_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.UP_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.X_FLAG;
import static com.limelight.nvstream.input.ControllerPacket.Y_FLAG;

/**
 * Enumerator for every gamepad component GFE recognizes
 * @author Diego Waxemberg
 */
public enum GamepadOutput {
    A(A_FLAG), B(B_FLAG), X(X_FLAG), Y(Y_FLAG),
    DPAD_UP(UP_FLAG), DPAD_DOWN(DOWN_FLAG), DPAD_LEFT(LEFT_FLAG), DPAD_RIGHT(RIGHT_FLAG),
    LS_LEFT, LS_RIGHT, LS_DOWN, LS_UP,
    RS_LEFT, RS_RIGHT, RS_DOWN, RS_UP,
    LS_THUMB(LS_CLK_FLAG), RS_THUMB(RS_CLK_FLAG),
    LT, RT, LB(LB_FLAG), RB(RB_FLAG),
    START(PLAY_FLAG), BACK(BACK_FLAG), SPECIAL(SPECIAL_BUTTON_FLAG);

    private final short buttonFlag;

    GamepadOutput() {
        this((short) 0);
    }

    GamepadOutput(short buttonFlag) {
        this.buttonFlag = buttonFlag;
    }

    public short buttonFlag() {
        assert buttonFlag != 0;
        return buttonFlag;
    }
}
