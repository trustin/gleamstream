package com.limelight.nvstream.av;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.limelight.utils.TimeHelper;

public class RtpReorderQueue {

    private static final Logger logger = LoggerFactory.getLogger(RtpReorderQueue.class);

    private final int maxSize;
    private final int maxQueueTime;
    private final Deque<RtpQueueEntry> queue;

    private short nextRtpSequenceNumber;

    private long oldestQueuedTime;

    public enum RtpQueueStatus {
        HANDLE_IMMEDIATELY,
        QUEUED_NOTHING_READY,
        QUEUED_PACKETS_READY,
        REJECTED
    }

    public RtpReorderQueue() {
        maxSize = 16;
        maxQueueTime = 40;
        queue = new ArrayDeque<>();

        oldestQueuedTime = Long.MAX_VALUE;
        nextRtpSequenceNumber = Short.MAX_VALUE;
    }

    public RtpReorderQueue(int maxSize, int maxQueueTime) {
        this.maxSize = maxSize;
        this.maxQueueTime = maxQueueTime;
        queue = new ArrayDeque<>();

        oldestQueuedTime = Long.MAX_VALUE;
        nextRtpSequenceNumber = Short.MAX_VALUE;
    }

    private boolean queuePacket(boolean head, RtpPacketFields packet) {
        short seq = packet.getRtpSequenceNumber();

        if (nextRtpSequenceNumber != Short.MAX_VALUE) {
            // Don't queue packets we're already ahead of
            if (SequenceHelper.isBeforeSigned(seq, nextRtpSequenceNumber, false)) {
                return false;
            }

            // Don't queue duplicates either
            for (RtpQueueEntry existingEntry : queue) {
                if (existingEntry.sequenceNumber == seq) {
                    return false;
                }
            }
        }

        RtpQueueEntry entry = new RtpQueueEntry();
        entry.packet = packet;
        entry.queueTime = TimeHelper.getMonotonicMillis();
        entry.sequenceNumber = seq;

        if (oldestQueuedTime == Long.MAX_VALUE) {
            oldestQueuedTime = entry.queueTime;
        }

        // Add a reference to the packet while it's in the queue
        packet.referencePacket();

        if (head) {
            queue.addFirst(entry);
        } else {
            queue.addLast(entry);
        }

        return true;
    }

    private void updateOldestQueued() {
        oldestQueuedTime = Long.MAX_VALUE;
        for (RtpQueueEntry entry : queue) {
            if (entry.queueTime < oldestQueuedTime) {
                oldestQueuedTime = entry.queueTime;
            }
        }
    }

    private RtpQueueEntry getEntryByLowestSeq() {
        if (queue.isEmpty()) {
            return null;
        }

        RtpQueueEntry lowestSeqEntry = queue.getFirst();
        short nextSeq = lowestSeqEntry.sequenceNumber;

        for (RtpQueueEntry entry : queue) {
            if (SequenceHelper.isBeforeSigned(entry.sequenceNumber, nextSeq, true)) {
                lowestSeqEntry = entry;
                nextSeq = entry.sequenceNumber;
            }
        }

        if (nextSeq != Short.MAX_VALUE) {
            nextRtpSequenceNumber = nextSeq;
        }

        return lowestSeqEntry;
    }

    private RtpQueueEntry validateQueueConstraints() {
        if (queue.isEmpty()) {
            return null;
        }

        boolean dequeuePacket = false;

        // Check that the queue's time constraint is satisfied
        if (TimeHelper.getMonotonicMillis() - oldestQueuedTime > maxQueueTime) {
            logger.info("Returning RTP packet queued for too long: " +
                        (TimeHelper.getMonotonicMillis() - oldestQueuedTime));
            dequeuePacket = true;
        }

        // Check that the queue's size constraint is satisfied. We subtract one
        // because this is validating that the queue will meet constraints _after_
        // the current packet is enqueued.
        if (!dequeuePacket && queue.size() == maxSize - 1) {
            logger.info("Returning RTP packet after queue overgrowth");
            dequeuePacket = true;
        }

        if (dequeuePacket) {
            // Return the lowest seq queued
            return getEntryByLowestSeq();
        } else {
            return null;
        }
    }

    public RtpQueueStatus addPacket(RtpPacketFields packet) {
        if (nextRtpSequenceNumber != Short.MAX_VALUE &&
            SequenceHelper.isBeforeSigned(packet.getRtpSequenceNumber(), nextRtpSequenceNumber, false)) {
            // Reject packets behind our current sequence number
            return RtpQueueStatus.REJECTED;
        }

        if (queue.isEmpty()) {
            // Return immediately for an exact match with an empty queue
            if (nextRtpSequenceNumber == Short.MAX_VALUE ||
                packet.getRtpSequenceNumber() == nextRtpSequenceNumber) {
                nextRtpSequenceNumber = (short) (packet.getRtpSequenceNumber() + 1);
                return RtpQueueStatus.HANDLE_IMMEDIATELY;
            } else {
                // Queue is empty currently so we'll put this packet on there
                if (queuePacket(false, packet)) {
                    return RtpQueueStatus.QUEUED_NOTHING_READY;
                } else {
                    return RtpQueueStatus.REJECTED;
                }
            }
        } else {
            // Validate that the queue remains within our contraints
            RtpQueueEntry lowestEntry = validateQueueConstraints();

            // If the queue is now empty after validating queue constraints,
            // this packet can be returned immediately
            if (lowestEntry == null && queue.isEmpty()) {
                nextRtpSequenceNumber = (short) (packet.getRtpSequenceNumber() + 1);
                return RtpQueueStatus.HANDLE_IMMEDIATELY;
            }

            // Queue has data inside, so we need to see where this packet fits
            if (packet.getRtpSequenceNumber() == nextRtpSequenceNumber) {
                // It fits in a hole where we need a packet, now we have some ready
                if (queuePacket(true, packet)) {
                    return RtpQueueStatus.QUEUED_PACKETS_READY;
                } else {
                    return RtpQueueStatus.REJECTED;
                }
            } else {
                if (queuePacket(false, packet)) {
                    // Constraint validation may have changed the oldest packet to one that
                    // matches the next sequence number
                    return lowestEntry != null ? RtpQueueStatus.QUEUED_PACKETS_READY :
                           RtpQueueStatus.QUEUED_NOTHING_READY;
                } else {
                    return RtpQueueStatus.REJECTED;
                }
            }
        }
    }

    // This function returns a referenced packet. The caller must dereference
    // the packet when it is finished.
    public RtpPacketFields getQueuedPacket() {
        RtpQueueEntry queuedEntry = null;

        // Find the matching entry
        Iterator<RtpQueueEntry> i = queue.iterator();
        while (i.hasNext()) {
            RtpQueueEntry entry = i.next();
            if (entry.sequenceNumber == nextRtpSequenceNumber) {
                nextRtpSequenceNumber++;
                queuedEntry = entry;
                i.remove();
                break;
            }
        }

        // Bail if we found nothing
        if (queuedEntry == null) {
            // Update the oldest queued packet time
            updateOldestQueued();

            return null;
        }

        // We don't update the oldest queued entry here, because we know
        // the caller will call again until it receives null

        return queuedEntry.packet;
    }

    private static class RtpQueueEntry {
        public RtpPacketFields packet;

        public short sequenceNumber;
        public long queueTime;
    }
}
