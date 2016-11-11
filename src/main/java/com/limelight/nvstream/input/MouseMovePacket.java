package com.limelight.nvstream.input;

import static com.limelight.nvstream.ConnectionContext.SERVER_GENERATION_5;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.ConnectionContext;

final class MouseMovePacket extends InputPacket {
    private static final int HEADER_CODE = 0x06;
    private static final int PACKET_TYPE = 0x8;
    private static final int PAYLOAD_LENGTH = 8;
    private static final int PACKET_LENGTH = PAYLOAD_LENGTH + HEADER_LENGTH;

    // Accessed in ControllerStream for batching
    short deltaX;
    short deltaY;

    MouseMovePacket(short deltaX, short deltaY) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    @Override
    void toWirePayload(ConnectionContext ctx, ByteBuffer bb) {
        bb.order(ByteOrder.LITTLE_ENDIAN);
        // On Gen 5 servers, the header code is incremented by one
        bb.putInt(ctx.serverGeneration >= SERVER_GENERATION_5 ? HEADER_CODE + 1 : HEADER_CODE);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.putShort(deltaX);
        bb.putShort(deltaY);
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
