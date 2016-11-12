package kr.motd.gleamstream.gamepad;

public final class GamepadState {
    private short buttonFlags = -1;
    private byte leftTrigger;
    private byte rightTrigger;
    private short leftStickX;
    private short leftStickY;
    private short rightStickX;
    private short rightStickY;

    public boolean update(short buttonFlags, byte leftTrigger, byte rightTrigger,
                          short leftStickX, short leftStickY, short rightStickX, short rightStickY) {

        final boolean different = this.leftStickX   != leftStickX   ||
                                  this.leftStickY   != leftStickY   ||
                                  this.rightStickX  != rightStickX  ||
                                  this.rightStickY  != rightStickY  ||
                                  this.leftTrigger  != leftTrigger  ||
                                  this.rightTrigger != rightTrigger ||
                                  this.buttonFlags  != buttonFlags;

        if (different) {
            this.buttonFlags = buttonFlags;
            this.leftTrigger = leftTrigger;
            this.rightTrigger = rightTrigger;
            this.leftStickX = leftStickX;
            this.leftStickY = leftStickY;
            this.rightStickX = rightStickX;
            this.rightStickY = rightStickY;
        }

        return different;
    }

    public short buttonFlags() {
        return buttonFlags;
    }
}
