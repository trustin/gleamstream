package com.limelight.nvstream.input;

import static com.limelight.nvstream.ConnectionContext.SERVER_GENERATION_5;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.ConnectionContext;

class MultiControllerPacket extends InputPacket {
    private static final byte[] TAIL = { (byte) 0x9C, 0x00, 0x00, 0x00, 0x55, 0x00 };

    private static final int HEADER_CODE = 0x0d;
    private static final int PACKET_TYPE = 0x1e;

    private static final short PAYLOAD_LENGTH = 30;
    private static final short PACKET_LENGTH = PAYLOAD_LENGTH + HEADER_LENGTH;

    short controllerNumber;
    short buttonFlags;
    byte leftTrigger;
    byte rightTrigger;
    short leftStickX;
    short leftStickY;
    short rightStickX;
    short rightStickY;

    MultiControllerPacket(short controllerNumber, short buttonFlags, byte leftTrigger, byte rightTrigger,
                          short leftStickX, short leftStickY, short rightStickX, short rightStickY) {

        this.controllerNumber = controllerNumber;

        this.buttonFlags = buttonFlags;
        this.leftTrigger = leftTrigger;
        this.rightTrigger = rightTrigger;

        this.leftStickX = leftStickX;
        this.leftStickY = leftStickY;

        this.rightStickX = rightStickX;
        this.rightStickY = rightStickY;
    }

    @Override
    void toWirePayload(ConnectionContext ctx, ByteBuffer bb) {
        bb.order(ByteOrder.LITTLE_ENDIAN);
        // On Gen 5 servers, the header code is decremented by one
        bb.putInt(ctx.serverGeneration >= SERVER_GENERATION_5 ? HEADER_CODE - 1 : HEADER_CODE);
        bb.putShort((short) 0x1a);
        bb.putShort(controllerNumber);
        bb.putShort((short) 0x0f); // Active controller flags
        bb.putShort((short) 0x14);
        bb.putShort(buttonFlags);
        bb.put(leftTrigger);
        bb.put(rightTrigger);
        bb.putShort(leftStickX);
        bb.putShort(leftStickY);
        bb.putShort(rightStickX);
        bb.putShort(rightStickY);
        bb.put(TAIL);
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
