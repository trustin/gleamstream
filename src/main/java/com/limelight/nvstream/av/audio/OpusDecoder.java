package com.limelight.nvstream.av.audio;

import com.limelight.nvstream.NativeLibraries;

final class OpusDecoder {
    static {
        NativeLibraries.load("nv_opus_dec");
    }

    static void initNativeLibraries() {}

    static native int init(int sampleRate, int samplesPerChannel, int channelCount,
                           int streams, int coupledStreams, byte[] mapping);

    static native void destroy();

    static native int decode(byte[] inData, int inOffset, int inLength, byte[] outPcmData);

    private OpusDecoder() {}
}
