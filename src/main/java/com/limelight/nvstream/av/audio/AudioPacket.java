package com.limelight.nvstream.av.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.RtpPacket;

final class AudioPacket implements RtpPacket {

    private byte packetType;
    private short seqNum;
    private int headerSize;

    private final ByteBufferDescriptor buffer;
    private final ByteBuffer bb;

    AudioPacket(byte[] buffer) {
        this.buffer = new ByteBufferDescriptor(buffer, 0, buffer.length);
        bb = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN);
    }

    void initializeWithLength(int length) {
        // Rewind to start
        bb.rewind();

        // Read the RTP header byte
        byte header = bb.get();

        // Get the packet type
        packetType = bb.get();

        // Get the sequence number
        seqNum = bb.getShort();

        // If an extension is present, read the fields
        headerSize = FIXED_HEADER_SIZE;
        if ((header & FLAG_EXTENSION) != 0) {
            headerSize += 4; // 2 additional fields
        }

        // Update descriptor length
        buffer.length = length;
    }

    @Override
    public byte getPacketType() {
        return packetType;
    }

    @Override
    public short getRtpSequenceNumber() {
        return seqNum;
    }

    byte[] getBuffer() {
        return buffer.data;
    }

    void initializePayloadDescriptor(ByteBufferDescriptor bb) {
        bb.reinitialize(buffer.data, buffer.offset + headerSize, buffer.length - headerSize);
    }

    @Override
    public int referencePacket() {
        // There's no circular buffer for audio packets so this is a no-op
        return 0;
    }

    @Override
    public int dereferencePacket() {
        // There's no circular buffer for audio packets so this is a no-op
        return 0;
    }

    @Override
    public int getRefCount() {
        // There's no circular buffer for audio packets so this is a no-op
        return 0;
    }
}
