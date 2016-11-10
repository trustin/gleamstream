package com.limelight.nvstream.av.video;

import com.limelight.nvstream.av.ByteBufferDescriptor;

final class NAL {

    // This assumes that the buffer passed in is already a special sequence
    static boolean isAnnexBStartSequence(ByteBufferDescriptor specialSeq) {
        // The start sequence is 00 00 01 or 00 00 00 01
        return specialSeq.data[specialSeq.offset + specialSeq.length - 1] == 0x01;
    }

    // This assumes that the buffer passed in is already a special sequence
    static boolean isAnnexBFrameStart(ByteBufferDescriptor specialSeq) {
        if (specialSeq.length != 4) { return false; }

        // The frame start sequence is 00 00 00 01
        return specialSeq.data[specialSeq.offset + specialSeq.length - 1] == 0x01;
    }

    // This assumes that the buffer passed in is already a special sequence
    static boolean isPadding(ByteBufferDescriptor specialSeq) {
        // The padding sequence is 00 00 00
        return specialSeq.data[specialSeq.offset + specialSeq.length - 1] == 0x00;
    }

    // Returns a buffer descriptor describing the start sequence
    static boolean getSpecialSequenceDescriptor(ByteBufferDescriptor buffer,
                                                ByteBufferDescriptor outputDesc) {
        // NAL start sequence is 00 00 00 01 or 00 00 01
        if (buffer.length < 3) { return false; }

        // 00 00 is magic
        if (buffer.data[buffer.offset] == 0x00 &&
            buffer.data[buffer.offset + 1] == 0x00) {
            // Another 00 could be the end of the special sequence
            // 00 00 00 or the middle of 00 00 00 01
            if (buffer.data[buffer.offset + 2] == 0x00) {
                if (buffer.length >= 4 &&
                    buffer.data[buffer.offset + 3] == 0x01) {
                    // It's the Annex B start sequence 00 00 00 01
                    outputDesc.reinitialize(buffer.data, buffer.offset, 4);
                } else {
                    // It's 00 00 00
                    outputDesc.reinitialize(buffer.data, buffer.offset, 3);
                }
                return true;
            } else if (buffer.data[buffer.offset + 2] == 0x01 ||
                       buffer.data[buffer.offset + 2] == 0x02) {
                // These are easy: 00 00 01 or 00 00 02
                outputDesc.reinitialize(buffer.data, buffer.offset, 3);
                return true;
            } else if (buffer.data[buffer.offset + 2] == 0x03) {
                // 00 00 03 is special because it's a subsequence of the
                // NAL wrapping substitute for 00 00 00, 00 00 01, 00 00 02,
                // or 00 00 03 in the RBSP sequence. We need to check the next
                // byte to see whether it's 00, 01, 02, or 03 (a valid RBSP substitution)
                // or whether it's something else

                if (buffer.length < 4) { return false; }

                if (buffer.data[buffer.offset + 3] >= 0x00 &&
                    buffer.data[buffer.offset + 3] <= 0x03) {
                    // It's not really a special sequence after all
                    return false;
                } else {
                    // It's not a standard replacement so it's a special sequence
                    outputDesc.reinitialize(buffer.data, buffer.offset, 3);
                    return true;
                }
            }
        }

        return false;
    }

    private NAL() {}
}
