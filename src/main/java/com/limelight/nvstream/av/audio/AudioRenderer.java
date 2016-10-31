package com.limelight.nvstream.av.audio;

public interface AudioRenderer {
    // playDecodedAudio() is lightweight, so don't use an extra thread for playback
    int CAPABILITY_DIRECT_SUBMIT = 0x1;

    int getCapabilities();

    boolean streamInitialized(int channelCount, int channelMask, int samplesPerFrame, int sampleRate);

    void playDecodedAudio(byte[] audioData, int offset, int length);

    void streamClosing();
}
