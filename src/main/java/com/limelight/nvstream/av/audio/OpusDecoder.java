package com.limelight.nvstream.av.audio;

import com.limelight.utils.NativeLibraries;

public class OpusDecoder {
    static {
        NativeLibraries.load("nv_opus_dec");
    }

    public static native int init(int sampleRate, int samplesPerChannel, int channelCount, int streams,
                                  int coupledStreams, byte[] mapping);

    public static native void destroy();

    public static native int decode(byte[] indata, int inoff, int inlen, byte[] outpcmdata);
}
