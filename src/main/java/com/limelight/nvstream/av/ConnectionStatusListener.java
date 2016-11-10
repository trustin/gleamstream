package com.limelight.nvstream.av;

public interface ConnectionStatusListener {
    void connectionDetectedFrameLoss(int firstLostFrame, int nextSuccessfulFrame);
    void connectionSinkTooSlow(int firstLostFrame, int nextSuccessfulFrame);
    void connectionReceivedCompleteFrame(int frameIndex);
    void connectionSawFrame(int frameIndex);
    void connectionLostPackets(int lastReceivedPacket, int nextReceivedPacket);
}
