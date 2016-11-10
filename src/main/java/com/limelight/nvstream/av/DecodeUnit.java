package com.limelight.nvstream.av;

import com.limelight.nvstream.av.video.VideoPacket;

public class DecodeUnit {
    public static final int DU_FLAG_CODEC_CONFIG = 0x1;
    public static final int DU_FLAG_SYNC_FRAME = 0x2;

    private ByteBufferDescriptor bufferHead;
    private int dataLength;
    private int frameNumber;
    private long receiveTimestamp;
    private int flags;
    private VideoPacket backingPacketHead;

    public void initialize(ByteBufferDescriptor bufferHead, int dataLength,
                           int frameNumber, long receiveTimestamp, int flags, VideoPacket backingPacketHead) {
        this.bufferHead = bufferHead;
        this.dataLength = dataLength;
        this.frameNumber = frameNumber;
        this.receiveTimestamp = receiveTimestamp;
        this.flags = flags;
        this.backingPacketHead = backingPacketHead;
    }

    public long getReceiveTimestamp() {
        return receiveTimestamp;
    }

    public ByteBufferDescriptor getBufferHead() {
        return bufferHead;
    }

    public int getDataLength() {
        return dataLength;
    }

    public int getFrameNumber() {
        return frameNumber;
    }

    public int getFlags() {
        return flags;
    }

    // Internal use only
    public VideoPacket removeBackingPacketHead() {
        VideoPacket pkt = backingPacketHead;
        if (pkt != null) {
            backingPacketHead = pkt.nextPacket;
        }
        return pkt;
    }
}
