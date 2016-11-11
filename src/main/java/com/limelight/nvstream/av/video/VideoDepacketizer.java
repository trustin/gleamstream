package com.limelight.nvstream.av.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.TimeHelper;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.ConnectionStatusListener;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.SequenceHelper;
import com.limelight.nvstream.av.buffer.AbstractPopulatedBufferList;
import com.limelight.nvstream.av.buffer.AtomicPopulatedBufferList;
import com.limelight.nvstream.av.buffer.UnsynchronizedPopulatedBufferList;

public class VideoDepacketizer {

    private static final Logger logger = LoggerFactory.getLogger(VideoDepacketizer.class);

    // Current frame state
    private int frameDataLength;
    private ByteBufferDescriptor frameDataChainHead;
    private ByteBufferDescriptor frameDataChainTail;
    private VideoPacket backingPacketHead;
    private VideoPacket backingPacketTail;

    // Sequencing state
    private int lastPacketInStream = -1;
    private int nextFrameNumber = 1;
    private int startFrameNumber;
    private boolean waitingForNextSuccessfulFrame;
    private boolean waitingForIdrFrame = true;
    private long lastWaitForIdrFrameLogTime = System.nanoTime();
    private long frameStartTime;
    private boolean decodingFrame;
    private final boolean strictIdrFrameWait;

    // Cached objects
    private final ByteBufferDescriptor cachedReassemblyDesc = new ByteBufferDescriptor(null, 0, 0);
    private final ByteBufferDescriptor cachedSpecialDesc = new ByteBufferDescriptor(null, 0, 0);

    private final ConnectionStatusListener controlListener;
    private final int nominalPacketDataLength;

    private static final int CONSECUTIVE_DROP_LIMIT = 120;
    private int consecutiveFrameDrops;

    private static final int DU_LIMIT = 15;
    private final AbstractPopulatedBufferList<DecodeUnit> decodedUnits;

    private final int frameHeaderOffset;

    public VideoDepacketizer(ConnectionContext context, ConnectionStatusListener controlListener,
                             int nominalPacketSize) {
        this.controlListener = controlListener;
        nominalPacketDataLength = nominalPacketSize - VideoPacket.HEADER_SIZE;

        if (context.serverAppVersion[0] > 7 ||
            context.serverAppVersion[0] == 7 && context.serverAppVersion[1] > 1 ||
            context.serverAppVersion[0] == 7 && context.serverAppVersion[1] == 1
             && context.serverAppVersion[2] >= 320) {
            // Anything over 7.1.320 should use the 12 byte frame header
            frameHeaderOffset = 12;
        } else if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
            // Gen 5 servers have an 8 byte header in the data portion of the first
            // packet of each frame
            frameHeaderOffset = 8;
        } else {
            frameHeaderOffset = 0;
        }

        boolean unsynchronized;
        if (context.videoDecoderRenderer != null) {
            int videoCaps = context.videoDecoderRenderer.getCapabilities();
            strictIdrFrameWait =
                    (videoCaps & VideoDecoderRenderer.CAPABILITY_REFERENCE_FRAME_INVALIDATION) == 0;
            unsynchronized = (videoCaps & VideoDecoderRenderer.CAPABILITY_DIRECT_SUBMIT) != 0;
        } else {
            // If there's no renderer, it doesn't matter if we synchronize or wait for IDRs
            strictIdrFrameWait = false;
            unsynchronized = true;
        }

        AbstractPopulatedBufferList.BufferFactory<DecodeUnit> factory =
                new AbstractPopulatedBufferList.BufferFactory<DecodeUnit>() {
                    @Override
                    public DecodeUnit createFreeBuffer() {
                        return new DecodeUnit();
                    }

                    @Override
                    public void cleanupObject(DecodeUnit o) {

                        // Disassociate video packets from this DU
                        VideoPacket pkt;
                        while ((pkt = o.removeBackingPacketHead()) != null) {
                            pkt.dereferencePacket();
                        }
                    }
                };

        if (unsynchronized) {
            decodedUnits = new UnsynchronizedPopulatedBufferList<>(DU_LIMIT, factory);
        } else {
            decodedUnits = new AtomicPopulatedBufferList<>(DU_LIMIT, factory);
        }
    }

    private void dropFrameState() {
        // We'll need an IDR frame now if we're in strict mode
        if (strictIdrFrameWait) {
            waitingForIdrFrame = true;
        }

        // Count the number of consecutive frames dropped
        consecutiveFrameDrops++;

        // If we reach our limit, immediately request an IDR frame
        // and reset
        if (consecutiveFrameDrops == CONSECUTIVE_DROP_LIMIT) {
            logger.warn("Reached consecutive drop limit");

            // Restart the count
            consecutiveFrameDrops = 0;

            // Request an IDR frame (0 tuple always generates an IDR frame)
            controlListener.connectionDetectedFrameLoss(0, 0);
        }

        cleanupFrameState();
    }

    private void cleanupFrameState() {
        backingPacketTail = null;
        while (backingPacketHead != null) {
            backingPacketHead.dereferencePacket();
            backingPacketHead = backingPacketHead.nextPacket;
        }

        frameDataChainHead = frameDataChainTail = null;
        frameDataLength = 0;
    }

    private static boolean isReferencePictureNalu(byte nalType) {
        switch (nalType) {
            case 0x20:
            case 0x22:
            case 0x24:
            case 0x26:
            case 0x28:
            case 0x2A:
                // H265
                return true;

            case 0x65:
                // H264
                return true;

            default:
                return false;
        }
    }

    private void reassembleFrame(int frameNumber) {
        // This is the start of a new frame
        if (frameDataChainHead != null) {
            ByteBufferDescriptor firstBuffer = frameDataChainHead;

            int flags = 0;
            if (NAL.getSpecialSequenceDescriptor(firstBuffer, cachedSpecialDesc) &&
                NAL.isAnnexBFrameStart(cachedSpecialDesc)) {
                switch (cachedSpecialDesc.data[cachedSpecialDesc.offset + cachedSpecialDesc.length]) {

                    // H265
                    case 0x40: // VPS
                    case 0x42: // SPS
                    case 0x44: // PPS
                        flags |= DecodeUnit.DU_FLAG_CODEC_CONFIG;
                        break;

                    // H264
                    case 0x67: // SPS
                    case 0x68: // PPS
                        flags |= DecodeUnit.DU_FLAG_CODEC_CONFIG;
                        break;
                }

                if (isReferencePictureNalu(
                        cachedSpecialDesc.data[cachedSpecialDesc.offset + cachedSpecialDesc.length])) {
                    flags |= DecodeUnit.DU_FLAG_SYNC_FRAME;
                }
            }

            // Construct the video decode unit
            DecodeUnit du = decodedUnits.pollFreeObject();
            if (du == null) {
                logger.warn("Video decoder is too slow! Forced to drop decode units");

                // Invalidate all frames from the start of the DU queue
                // (0 tuple always generates an IDR frame)
                controlListener.connectionSinkTooSlow(0, 0);
                waitingForIdrFrame = true;

                // Remove existing frames
                decodedUnits.clearPopulatedObjects();

                // Clear frame state and wait for an IDR
                dropFrameState();
                return;
            }

            // Initialize the free DU
            du.initialize(frameDataChainHead, frameDataLength, frameNumber,
                          frameStartTime, flags, backingPacketHead);

            // Packets now owned by the DU
            backingPacketTail = backingPacketHead = null;

            controlListener.connectionReceivedCompleteFrame(frameNumber);

            // Submit the DU to the consumer
            decodedUnits.addPopulatedObject(du);

            // Clear old state
            cleanupFrameState();

            // Clear frame drops
            consecutiveFrameDrops = 0;
        }
    }

    private void chainBufferToCurrentFrame(ByteBufferDescriptor desc) {
        desc.nextDescriptor = null;

        // Chain the packet
        if (frameDataChainTail != null) {
            frameDataChainTail.nextDescriptor = desc;
            frameDataChainTail = desc;
        } else {
            frameDataChainHead = frameDataChainTail = desc;
        }

        frameDataLength += desc.length;
    }

    private void chainPacketToCurrentFrame(VideoPacket packet) {
        packet.referencePacket();
        packet.nextPacket = null;

        // Chain the packet
        if (backingPacketTail != null) {
            backingPacketTail.nextPacket = packet;
            backingPacketTail = packet;
        } else {
            backingPacketHead = backingPacketTail = packet;
        }
    }

    private void addInputDataSlow(VideoPacket packet, ByteBufferDescriptor location) {
        boolean isDecodingVideoData = false;

        while (location.length != 0) {
            // Remember the start of the NAL data in this packet
            int start = location.offset;

            // Check for a special sequence
            if (NAL.getSpecialSequenceDescriptor(location, cachedSpecialDesc)) {
                if (NAL.isAnnexBStartSequence(cachedSpecialDesc)) {
                    // We're decoding video data now
                    isDecodingVideoData = true;

                    // Check if it's the end of the last frame
                    if (NAL.isAnnexBFrameStart(cachedSpecialDesc)) {
                        // Update the global state that we're decoding a new frame
                        decodingFrame = true;

                        // Reassemble any pending NAL
                        reassembleFrame(packet.getFrameIndex());

                        // Reload cachedSpecialDesc after reassembleFrame overwrote it
                        NAL.getSpecialSequenceDescriptor(location, cachedSpecialDesc);

                        if (isReferencePictureNalu(
                                cachedSpecialDesc.data[cachedSpecialDesc.offset + cachedSpecialDesc.length])) {
                            // This is the NALU code for I-frame data
                            waitingForIdrFrame = false;

                            // Cancel any pending IDR frame request
                            waitingForNextSuccessfulFrame = false;
                        }
                    }

                    // Skip the start sequence
                    location.length -= cachedSpecialDesc.length;
                    location.offset += cachedSpecialDesc.length;
                } else {
                    // Check if this is padding after a full video frame
                    if (isDecodingVideoData && NAL.isPadding(cachedSpecialDesc)) {
                        // The decode unit is complete
                        reassembleFrame(packet.getFrameIndex());
                    }

                    // Not decoding video
                    isDecodingVideoData = false;

                    // Just skip this byte
                    location.length--;
                    location.offset++;
                }
            }

            // Move to the next special sequence
            while (location.length != 0) {
                // Catch the easy case first where byte 0 != 0x00
                if (location.data[location.offset] == 0x00) {
                    // Check if this should end the current NAL
                    if (NAL.getSpecialSequenceDescriptor(location, cachedSpecialDesc)) {
                        // Only stop if we're decoding something or this
                        // isn't padding
                        if (isDecodingVideoData || !NAL.isPadding(cachedSpecialDesc)) {
                            break;
                        }
                    }
                }

                // This byte is part of the NAL data
                location.offset++;
                location.length--;
            }

            if (isDecodingVideoData && decodingFrame) {
                // The slow path may result in multiple decode units per packet.
                // The VideoPacket objects only support being in 1 DU list, so we'll
                // copy this data into a new array rather than reference the packet, if
                // this NALU ends before the end of the frame. Only copying if this doesn't
                // go to the end of the frame means we'll be only copying the SPS and PPS which
                // are quite small, while the actual I-frame data is referenced via the packet.
                if (location.length != 0) {
                    // Copy the packet data into a new array
                    byte[] dataCopy = new byte[location.offset - start];
                    System.arraycopy(location.data, start, dataCopy, 0, dataCopy.length);

                    // Chain a descriptor referencing the copied data
                    chainBufferToCurrentFrame(new ByteBufferDescriptor(dataCopy, 0, dataCopy.length));
                } else {
                    // Chain this packet to the current frame
                    chainPacketToCurrentFrame(packet);

                    // Add a buffer descriptor describing the NAL data in this packet
                    chainBufferToCurrentFrame(
                            new ByteBufferDescriptor(location.data, start, location.offset - start));
                }
            }
        }
    }

    private void addInputDataFast(VideoPacket packet, ByteBufferDescriptor location, boolean firstPacket) {
        if (firstPacket) {
            // Setup state for the new frame
            frameStartTime = TimeHelper.getMonotonicMillis();
        }

        // Add the payload data to the chain
        chainBufferToCurrentFrame(new ByteBufferDescriptor(location));

        // The receive thread can't use this until we're done with it
        chainPacketToCurrentFrame(packet);
    }

    private static boolean isFirstPacket(int flags) {
        // Clear the picture data flag
        flags &= ~VideoPacket.FLAG_CONTAINS_PIC_DATA;

        // Check if it's just the start or both start and end of a frame
        return flags == (VideoPacket.FLAG_SOF | VideoPacket.FLAG_EOF) ||
                flags == VideoPacket.FLAG_SOF;
    }

    public void addInputData(VideoPacket packet) {
        // Load our reassembly descriptor
        packet.initializePayloadDescriptor(cachedReassemblyDesc);

        int flags = packet.getFlags();

        int frameIndex = packet.getFrameIndex();
        boolean firstPacket = isFirstPacket(flags);

        // Drop duplicates or re-ordered packets
        int streamPacketIndex = packet.getStreamPacketIndex();
        if (SequenceHelper.isBeforeSigned((short) streamPacketIndex, (short) (lastPacketInStream + 1), false)) {
            return;
        }

        // Drop packets from a previously completed frame
        if (SequenceHelper.isBeforeSigned(frameIndex, nextFrameNumber, false)) {
            return;
        }

        // Notify the listener of the latest frame we've seen from the PC
        controlListener.connectionSawFrame(frameIndex);

        // Look for a frame start before receiving a frame end
        if (firstPacket && decodingFrame) {
            logger.warn("Network dropped end of a frame");
            nextFrameNumber = frameIndex;

            // Unexpected start of next frame before terminating the last
            waitingForNextSuccessfulFrame = true;

            // Clear the old state and wait for an IDR
            dropFrameState();
        }
        // Look for a non-frame start before a frame start
        else if (!firstPacket && !decodingFrame) {
            // Check if this looks like a real frame
            if (flags == VideoPacket.FLAG_CONTAINS_PIC_DATA ||
                flags == VideoPacket.FLAG_EOF ||
                cachedReassemblyDesc.length < nominalPacketDataLength) {
                logger.warn("Network dropped beginning of a frame");
                nextFrameNumber = frameIndex + 1;

                waitingForNextSuccessfulFrame = true;

                dropFrameState();
                decodingFrame = false;
                return;
            } else {
                // FEC data
                return;
            }
        }
        // Check sequencing of this frame to ensure we didn't
        // miss one in between
        else if (firstPacket) {
            // Make sure this is the next consecutive frame
            if (SequenceHelper.isBeforeSigned(nextFrameNumber, frameIndex, true)) {
                logger.warn("Network dropped an entire frame");
                nextFrameNumber = frameIndex;

                // Wait until an IDR frame comes
                waitingForNextSuccessfulFrame = true;
                dropFrameState();
            } else if (nextFrameNumber != frameIndex) {
                // Duplicate packet or FEC dup
                decodingFrame = false;
                return;
            }

            // We're now decoding a frame
            decodingFrame = true;
        }

        // If it's not the first packet of a frame
        // we need to drop it if the stream packet index
        // doesn't match
        if (!firstPacket && decodingFrame) {
            if (streamPacketIndex != lastPacketInStream + 1) {
                logger.warn("Network dropped middle of a frame");
                nextFrameNumber = frameIndex + 1;

                waitingForNextSuccessfulFrame = true;

                dropFrameState();
                decodingFrame = false;

                return;
            }
        }

        // Notify the server of any packet losses
        if (streamPacketIndex != lastPacketInStream + 1) {
            // Packets were lost so report this to the server
            controlListener.connectionLostPackets(lastPacketInStream, streamPacketIndex);
        }
        lastPacketInStream = streamPacketIndex;

        // If this is the first packet, skip the frame header (if one exists)
        if (firstPacket) {
            cachedReassemblyDesc.offset += frameHeaderOffset;
            cachedReassemblyDesc.length -= frameHeaderOffset;
        }

        if (firstPacket && isIdrFrameStart(cachedReassemblyDesc)) {
            // The slow path doesn't update the frame start time by itself
            frameStartTime = TimeHelper.getMonotonicMillis();

            // SPS and PPS prefix is padded between NALs, so we must decode it with the slow path
            addInputDataSlow(packet, cachedReassemblyDesc);
        } else {
            // Everything else can take the fast path
            addInputDataFast(packet, cachedReassemblyDesc, firstPacket);
        }

        if ((flags & VideoPacket.FLAG_EOF) != 0) {
            // Move on to the next frame
            decodingFrame = false;
            nextFrameNumber = frameIndex + 1;

            // If waiting for next successful frame and we got here
            // with an end flag, we can send a message to the server
            if (waitingForNextSuccessfulFrame) {
                // This is the next successful frame after a loss event
                controlListener.connectionDetectedFrameLoss(startFrameNumber, nextFrameNumber - 1);
                waitingForNextSuccessfulFrame = false;
            }

            // If we need an IDR frame first, then drop this frame
            if (waitingForIdrFrame) {
                long currentTime = System.nanoTime();
                if (currentTime - lastWaitForIdrFrameLogTime > 1000000000) {
                    logger.warn("Waiting for IDR frame");
                    lastWaitForIdrFrameLogTime = currentTime;
                }
                dropFrameState();
                return;
            }

            reassembleFrame(frameIndex);

            startFrameNumber = nextFrameNumber;
        }
    }

    private boolean isIdrFrameStart(ByteBufferDescriptor desc) {
        return NAL.getSpecialSequenceDescriptor(desc, cachedSpecialDesc) &&
               NAL.isAnnexBFrameStart(cachedSpecialDesc) &&
               (cachedSpecialDesc.data[cachedSpecialDesc.offset + cachedSpecialDesc.length] == 0x67 || // H264 SPS
                cachedSpecialDesc.data[cachedSpecialDesc.offset + cachedSpecialDesc.length] == 0x40);  // H265 VPS
    }

    public DecodeUnit takeNextDecodeUnit() throws InterruptedException {
        return decodedUnits.takePopulatedObject();
    }

    public DecodeUnit pollNextDecodeUnit() {
        return decodedUnits.pollPopulatedObject();
    }

    public void freeDecodeUnit(DecodeUnit du) {
        decodedUnits.freePopulatedObject(du);
    }
}
