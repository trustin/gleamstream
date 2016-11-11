package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.ConnectionContext;

public final class KeyboardPacket extends InputPacket {
    public static final byte KEY_DOWN = 0x03;
    public static final byte KEY_UP = 0x04;

    public static final byte MODIFIER_SHIFT = 0x01;
    public static final byte MODIFIER_CTRL = 0x02;
    public static final byte MODIFIER_ALT = 0x04;

    private static final int PACKET_TYPE = 0x0A;
    private static final int PACKET_LENGTH = 14;

    private final short keyCode;
    private final byte keyDirection;
    private final byte modifier;

    KeyboardPacket(short keyCode, byte keyDirection, byte modifier) {
        this.keyCode = keyCode;
        this.keyDirection = keyDirection;
        this.modifier = modifier;
    }

    @Override
    void toWirePayload(ConnectionContext ctx, ByteBuffer bb) {
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put(keyDirection);
        bb.putShort((short) 0);
        bb.putShort((short) 0);
        bb.putShort(keyCode);
        bb.put(modifier);
        bb.put((byte) 0);
        bb.put((byte) 0);
    }

    @Override
    int packetType() {
        return PACKET_TYPE;
    }

    @Override
    int packetLength() {
        return PACKET_LENGTH;
    }
}
