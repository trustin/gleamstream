package com.limelight.nvstream.input;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.ConnectionContext;

public class MouseButtonPacket extends InputPacket {

    byte buttonEventType;
    byte mouseButton;

    private static final int PACKET_TYPE = 0x5;
    private static final int PAYLOAD_LENGTH = 5;
    private static final int PACKET_LENGTH = PAYLOAD_LENGTH +
                                             HEADER_LENGTH;

    private static final byte PRESS_EVENT = 0x07;
    private static final byte RELEASE_EVENT = 0x08;

    public static final byte BUTTON_LEFT = 0x01;
    public static final byte BUTTON_MIDDLE = 0x02;
    public static final byte BUTTON_RIGHT = 0x03;

    public MouseButtonPacket(ConnectionContext context, boolean buttonDown, byte mouseButton) {
        super(PACKET_TYPE);

        this.mouseButton = mouseButton;

        buttonEventType = buttonDown ?
                          PRESS_EVENT : RELEASE_EVENT;

        // On Gen 5 servers, the button event codes are incremented by one
        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
            buttonEventType++;
        }
    }

    @Override
    public void toWirePayload(ByteBuffer bb) {
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put(buttonEventType);
        bb.putInt(mouseButton);
    }

    @Override
    public int getPacketLength() {
        return PACKET_LENGTH;
    }
}
