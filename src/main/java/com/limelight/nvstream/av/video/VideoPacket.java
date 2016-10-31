package com.limelight.nvstream.av.video;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.RtpPacket;
import com.limelight.nvstream.av.RtpPacketFields;

public class VideoPacket implements RtpPacketFields {
    private final ByteBufferDescriptor buffer;
    private final ByteBuffer byteBuffer;
    private final boolean useAtomicRefCount;

    private int dataOffset;

    private int frameIndex;
    private int flags;
    private int streamPacketIndex;

    private short rtpSequenceNumber;

    private final AtomicInteger duAtomicRefCount = new AtomicInteger();
    private int duRefCount;

    // Only for use in DecodeUnit for packet queuing
    public VideoPacket nextPacket;

    public static final int FLAG_CONTAINS_PIC_DATA = 0x1;
    public static final int FLAG_EOF = 0x2;
    public static final int FLAG_SOF = 0x4;

    public static final int HEADER_SIZE = 16;

    public VideoPacket(byte[] buffer, boolean useAtomicRefCount) {
        this.buffer = new ByteBufferDescriptor(buffer, 0, buffer.length);
        byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        this.useAtomicRefCount = useAtomicRefCount;
    }

    public void initializeWithLengthNoRtpHeader(int length) {
        // Back to beginning
        byteBuffer.rewind();

        // No sequence number field is present in these packets

        // Read the video header fields
        streamPacketIndex = byteBuffer.getInt() >> 8 & 0xFFFFFF;
        frameIndex = byteBuffer.getInt();
        flags = byteBuffer.getInt() & 0xFF;

        // Data offset without the RTP header
        dataOffset = HEADER_SIZE;

        // Update descriptor length
        buffer.length = length;
    }

    public void initializeWithLength(int length) {
        // Read the RTP sequence number field (big endian)
        byteBuffer.position(2);
        rtpSequenceNumber = byteBuffer.getShort();
        rtpSequenceNumber =
                (short) (rtpSequenceNumber << 8 & 0xFF00 | rtpSequenceNumber >> 8 & 0x00FF);

        // Skip the rest of the RTP header
        byteBuffer.position(RtpPacket.MAX_HEADER_SIZE);

        // Read the video header fields
        streamPacketIndex = byteBuffer.getInt() >> 8 & 0xFFFFFF;
        frameIndex = byteBuffer.getInt();
        flags = byteBuffer.getInt() & 0xFF;

        // Data offset includes the RTP header
        dataOffset = RtpPacket.MAX_HEADER_SIZE + HEADER_SIZE;

        // Update descriptor length
        buffer.length = length;
    }

    public int getFlags() {
        return flags;
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    public int getStreamPacketIndex() {
        return streamPacketIndex;
    }

    public byte[] getBuffer() {
        return buffer.data;
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public void initializePayloadDescriptor(ByteBufferDescriptor bb) {
        bb.reinitialize(buffer.data, buffer.offset + dataOffset, buffer.length - dataOffset);
    }

    @Override
    public byte getPacketType() {
        // No consumers use this field so we don't look it up
        return -1;
    }

    @Override
    public short getRtpSequenceNumber() {
        return rtpSequenceNumber;
    }

    @Override
    public int referencePacket() {
        if (useAtomicRefCount) {
            return duAtomicRefCount.incrementAndGet();
        } else {
            return ++duRefCount;
        }
    }

    @Override
    public int dereferencePacket() {
        if (useAtomicRefCount) {
            return duAtomicRefCount.decrementAndGet();
        } else {
            return --duRefCount;
        }
    }

    @Override
    public int getRefCount() {
        if (useAtomicRefCount) {
            return duAtomicRefCount.get();
        } else {
            return duRefCount;
        }
    }
}
