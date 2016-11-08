package com.limelight.nvstream.input;

import static com.limelight.nvstream.ConnectionContext.SERVER_GENERATION_5;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.ConnectionContext;

public final class MouseButtonPacket extends InputPacket {

    private static final int PACKET_TYPE = 0x5;
    private static final int PAYLOAD_LENGTH = 5;
    private static final int PACKET_LENGTH = PAYLOAD_LENGTH + HEADER_LENGTH;

    private static final byte PRESS_EVENT = 0x07;
    private static final byte RELEASE_EVENT = 0x08;

    public static final byte BUTTON_LEFT = 0x01;
    public static final byte BUTTON_MIDDLE = 0x02;
    public static final byte BUTTON_RIGHT = 0x03;

    final byte buttonEventType;
    byte mouseButton;

    MouseButtonPacket(ConnectionContext context, boolean buttonDown, byte mouseButton) {
        super(PACKET_TYPE);
        this.mouseButton = mouseButton;
        buttonEventType = buttonDown ? PRESS_EVENT : RELEASE_EVENT;
    }

    @Override
    void toWirePayload(ConnectionContext ctx, ByteBuffer bb) {
        bb.order(ByteOrder.BIG_ENDIAN);
        // On Gen 5 servers, the button event codes are incremented by one
        bb.put(ctx.serverGeneration >= SERVER_GENERATION_5 ? (byte) (buttonEventType + 1) : buttonEventType);
        bb.putInt(mouseButton);
    }

    @Override
    int packetLength() {
        return PACKET_LENGTH;
    }
}
