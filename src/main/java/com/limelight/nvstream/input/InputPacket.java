package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.ConnectionContext;

abstract class InputPacket {

    static final int HEADER_LENGTH = 0x4;

    abstract int packetType();
    abstract int packetLength();

    final void toWire(ConnectionContext ctx, ByteBuffer bb) {
        bb.rewind();
        toWireHeader(bb);
        toWirePayload(ctx, bb);
    }

    private void toWireHeader(ByteBuffer bb) {
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.putInt(packetType());
    }

    abstract void toWirePayload(ConnectionContext ctx, ByteBuffer bb);
}
