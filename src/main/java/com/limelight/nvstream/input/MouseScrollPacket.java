package com.limelight.nvstream.input;

import static com.limelight.nvstream.ConnectionContext.SERVER_GENERATION_5;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.ConnectionContext;

final class MouseScrollPacket extends InputPacket {
    private static final int HEADER_CODE = 0x09;
    private static final int PACKET_TYPE = 0xa;
    private static final int PAYLOAD_LENGTH = 10;
    private static final int PACKET_LENGTH = PAYLOAD_LENGTH + HEADER_LENGTH;

    private final short scroll;

    MouseScrollPacket(byte scrollClicks) {
        scroll = (short) (scrollClicks * 120);
    }

    @Override
    void toWirePayload(ConnectionContext ctx, ByteBuffer bb) {
        bb.order(ByteOrder.LITTLE_ENDIAN);
        // On Gen 5 servers, the header code is incremented by one
        bb.putInt(ctx.serverGeneration >= SERVER_GENERATION_5 ? HEADER_CODE + 1 : HEADER_CODE);

        bb.order(ByteOrder.BIG_ENDIAN);
        bb.putShort(scroll);
        bb.putShort(scroll);

        bb.putShort((short) 0);
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
