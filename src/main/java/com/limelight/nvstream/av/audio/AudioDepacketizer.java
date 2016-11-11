package com.limelight.nvstream.av.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.DecodedUnitPool;
import com.limelight.nvstream.av.SequenceHelper;

final class AudioDepacketizer {

    private static final Logger logger = LoggerFactory.getLogger(AudioDepacketizer.class);

    private static final int DU_LIMIT = 30;
    private DecodedUnitPool<ByteBufferDescriptor> decodedUnits;

    // Direct submit state
    private final AudioRenderer directSubmitRenderer;
    private byte[] directSubmitData;

    // Cached objects
    private final ByteBufferDescriptor cachedDesc = new ByteBufferDescriptor(null, 0, 0);

    // Sequencing state
    private short lastSequenceNumber;

    AudioDepacketizer(AudioRenderer directSubmitRenderer, final int bufferSizeShorts) {
        this.directSubmitRenderer = directSubmitRenderer;
        if (directSubmitRenderer != null) {
            directSubmitData = new byte[bufferSizeShorts * 2];
        } else {
            decodedUnits = new DecodedUnitPool<>(
                    DU_LIMIT, true,
                    () -> new ByteBufferDescriptor(
                            new byte[bufferSizeShorts * 2], 0, bufferSizeShorts * 2));
        }
    }

    private void decodeData(byte[] data, int off, int len) {
        // Submit this data to the decoder
        int decodeLen;
        ByteBufferDescriptor bb;
        if (directSubmitData != null) {
            bb = null;
            decodeLen = OpusDecoder.decode(data, off, len, directSubmitData);
        } else {
            bb = decodedUnits.pollFree();
            if (bb == null) {
                logger.warn("Audio player too slow! Forced to drop decoded samples");
                decodedUnits.clearAllDecoded();
                bb = decodedUnits.pollFree();
                if (bb == null) {
                    logger.error("Audio player is leaking buffers!");
                    return;
                }
            }
            decodeLen = OpusDecoder.decode(data, off, len, bb.data);
        }

        if (decodeLen > 0) {
            if (directSubmitRenderer != null) {
                directSubmitRenderer.playDecodedAudio(directSubmitData, 0, decodeLen);
            } else {
                bb.length = decodeLen;
                decodedUnits.addDecoded(bb);
            }
        } else if (directSubmitRenderer == null) {
            decodedUnits.freeDecoded(bb);
        }
    }

    void decodeInputData(AudioPacket packet) {
        short seq = packet.getRtpSequenceNumber();

        // Toss out the current NAL if we receive a packet that is
        // out of sequence
        if (lastSequenceNumber != 0 &&
            (short) (lastSequenceNumber + 1) != seq) {
            logger.warn(
                    "Received OOS audio data (expected " + (lastSequenceNumber + 1) + ", got " + seq + ')');

            // Only tell the decoder if we got packets ahead of what we expected
            // If the packet is behind the current sequence number, drop it
            if (!SequenceHelper.isBeforeSigned(seq, (short) (lastSequenceNumber + 1), false)) {
                decodeData(null, 0, 0);
            } else {
                return;
            }
        }

        lastSequenceNumber = seq;

        // This is all the depacketizing we need to do
        packet.initializePayloadDescriptor(cachedDesc);
        decodeData(cachedDesc.data, cachedDesc.offset, cachedDesc.length);
    }

    ByteBufferDescriptor getNextDecodedData() throws InterruptedException {
        return decodedUnits.takeDecoded();
    }

    void freeDecodedData(ByteBufferDescriptor data) {
        decodedUnits.freeDecoded(data);
    }
}
