package com.limelight.nvstream.av;

public interface RtpPacket {
    int FLAG_EXTENSION = 0x10;
    int FIXED_HEADER_SIZE = 12;
    int MAX_HEADER_SIZE = 16;

    byte getPacketType();
    short getRtpSequenceNumber();
    int referencePacket();
    int dereferencePacket();
    int getRefCount();
}
