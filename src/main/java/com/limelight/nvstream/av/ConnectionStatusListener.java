package com.limelight.nvstream.av;

public interface ConnectionStatusListener {
    public void connectionDetectedFrameLoss(int firstLostFrame, int nextSuccessfulFrame);

    public void connectionSinkTooSlow(int firstLostFrame, int nextSuccessfulFrame);

    public void connectionReceivedCompleteFrame(int frameIndex);

    public void connectionSawFrame(int frameIndex);

    public void connectionLostPackets(int lastReceivedPacket, int nextReceivedPacket);
}
