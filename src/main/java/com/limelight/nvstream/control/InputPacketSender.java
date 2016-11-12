package com.limelight.nvstream.control;

import java.io.IOException;

@FunctionalInterface
public interface InputPacketSender {
    void sendInputPacket(byte[] data, int offset, int length) throws IOException;
}
