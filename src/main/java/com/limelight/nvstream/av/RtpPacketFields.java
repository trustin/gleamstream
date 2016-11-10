package com.limelight.nvstream.av;

public interface RtpPacketFields {
    byte getPacketType();
    short getRtpSequenceNumber();
    int referencePacket();
    int dereferencePacket();
    int getRefCount();
}
