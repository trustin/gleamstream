package com.limelight.nvstream.av.audio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.RtpPacket;
import com.limelight.nvstream.av.RtpReorderQueue;
import com.limelight.nvstream.av.RtpReorderQueue.RtpQueueStatus;

public class AudioStream {
    private static final Logger logger = LoggerFactory.getLogger(AudioStream.class);

    private static final int RTP_PORT = 48000;

    private static final int SAMPLE_RATE = 48000;
    private static final int SHORTS_PER_CHANNEL = 240;

    private static final int RTP_RECV_BUFFER = 64 * 1024;
    private static final int MAX_PACKET_SIZE = 250;

    private DatagramChannel rtp;

    private AudioDepacketizer depacketizer;

    private final LinkedList<Thread> threads = new LinkedList<>();

    private volatile boolean aborting;

    private final NvConnection parent;
    private final ConnectionContext context;
    private final AudioRenderer streamListener;

    public AudioStream(NvConnection parent, ConnectionContext context, AudioRenderer streamListener) {
        this.parent = parent;
        this.context = context;
        this.streamListener = streamListener;
    }

    public void abort() {
        if (aborting) {
            return;
        }

        aborting = true;

        // Close the socket to interrupt the receive thread
        if (rtp != null) {
            try {
                rtp.close();
            } catch (IOException e) {
                logger.warn("Failed to close a RTP connection", e);
            }
        }

        // Wait for threads to terminate
        for (Thread t : threads) {
            while (t.isAlive()) {
                t.interrupt();
                try {
                    t.join();
                } catch (InterruptedException ignored) {}
            }
        }

        streamListener.streamClosing();

        threads.clear();
    }

    public boolean startAudioStream() throws IOException {
        setupRtpSession();

        if (!setupAudio()) {
            abort();
            return false;
        }

        startReceiveThread();

        if ((streamListener.getCapabilities() & AudioRenderer.CAPABILITY_DIRECT_SUBMIT) == 0) {
            startDecoderThread();
        }

        startUdpPingThread();

        return true;
    }

    private void setupRtpSession() throws IOException {
        rtp = DatagramChannel.open();
        rtp.setOption(StandardSocketOptions.SO_RCVBUF, RTP_RECV_BUFFER);
        try {
            rtp.setOption(StandardSocketOptions.IP_TOS, 0x10); // IPTOS_LOWDELAY
        } catch (Exception ignored) {
            // May not be supported on some platforms.
        }
        rtp.connect(new InetSocketAddress(context.serverAddress, RTP_PORT));
    }

    private static final int[] STREAMS_2 = { 1, 1 };
    private static final int[] STREAMS_5_1 = { 4, 2 };

    private static final byte[] MAPPING_2 = { 0, 1 };
    private static final byte[] MAPPING_5_1 = { 0, 4, 1, 5, 2, 3 };

    private boolean setupAudio() {
        int err;

        int channels = context.streamConfig.getAudioChannelCount();
        byte[] mapping;
        int[] streams;

        if (channels == 2) {
            mapping = MAPPING_2;
            streams = STREAMS_2;
        } else if (channels == 6) {
            mapping = MAPPING_5_1;
            streams = STREAMS_5_1;
        } else {
            throw new IllegalStateException("Unsupported surround configuration");
        }

        err = OpusDecoder.init(SAMPLE_RATE, SHORTS_PER_CHANNEL, channels,
                               streams[0], streams[1], mapping);
        if (err != 0) {
            throw new IllegalStateException("Opus decoder failed to initialize: " + err);
        }

        if (!streamListener.streamInitialized(context.streamConfig.getAudioChannelCount(),
                                              context.streamConfig.getAudioChannelMask(),
                                              context.streamConfig.getAudioChannelCount() * SHORTS_PER_CHANNEL,
                                              SAMPLE_RATE)) {
            return false;
        }

        if ((streamListener.getCapabilities() & AudioRenderer.CAPABILITY_DIRECT_SUBMIT) != 0) {
            depacketizer = new AudioDepacketizer(streamListener, context.streamConfig.getAudioChannelCount()
                                                                 * SHORTS_PER_CHANNEL);
        } else {
            depacketizer = new AudioDepacketizer(null, context.streamConfig.getAudioChannelCount()
                                                       * SHORTS_PER_CHANNEL);
        }

        return true;
    }

    private void startDecoderThread() {
        // Decoder thread
        Thread t = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    ByteBufferDescriptor samples = depacketizer.getNextDecodedData();
                    streamListener.playDecodedAudio(samples.data, samples.offset, samples.length);
                    depacketizer.freeDecodedData(samples);
                }
            } catch (InterruptedException e) {
                // Interrupted
            } finally {
                parent.stop();
            }
        });
        threads.add(t);
        t.setName("Audio - Player");
        t.setPriority(Thread.NORM_PRIORITY + 2);
        t.start();
    }

    private void startReceiveThread() {
        // Receive thread
        Thread t = new Thread(() -> {
            byte[] buffer = new byte[MAX_PACKET_SIZE];
            ByteBuffer packet = ByteBuffer.wrap(buffer);
            RtpPacket queuedPacket, rtpPacket = new RtpPacket(buffer);
            RtpReorderQueue rtpQueue = new RtpReorderQueue();
            RtpQueueStatus queueStatus;

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    packet.clear();
                    rtp.read(packet);
                    packet.flip();

                    // DecodeInputData() doesn't hold onto the buffer so we are free to reuse it
                    rtpPacket.initializeWithLength(packet.remaining());

                    // Throw away non-audio packets before queuing
                    if (rtpPacket.getPacketType() != 97) {
                        // Only type 97 is audio
                        continue;
                    }

                    queueStatus = rtpQueue.addPacket(rtpPacket);
                    if (queueStatus == RtpQueueStatus.HANDLE_IMMEDIATELY) {
                        // Send directly to the depacketizer
                        depacketizer.decodeInputData(rtpPacket);
                    } else {
                        if (queueStatus != RtpQueueStatus.REJECTED) {
                            // The queue consumed our packet, so we must allocate a new one
                            buffer = new byte[MAX_PACKET_SIZE];
                            packet = ByteBuffer.wrap(buffer);
                            rtpPacket = new RtpPacket(buffer);
                        }

                        // If packets are ready, pull them and send them to the depacketizer
                        if (queueStatus == RtpQueueStatus.QUEUED_PACKETS_READY) {
                            while ((queuedPacket = (RtpPacket) rtpQueue.getQueuedPacket()) != null) {
                                depacketizer.decodeInputData(queuedPacket);
                                queuedPacket.dereferencePacket();
                            }
                        }
                    }
                }
            } catch (ClosedChannelException ignored) {
            } catch (IOException e) {
                logger.warn("Failed to receive an audio packet", e);
            } finally {
                parent.stop();
            }
        });
        threads.add(t);
        t.setName("Audio - Receive");
        t.setPriority(Thread.NORM_PRIORITY + 1);
        t.start();
    }

    private void startUdpPingThread() {
        // Ping thread
        Thread t = new Thread(() -> {
            // PING in ASCII
            final ByteBuffer pingPacketData = ByteBuffer.wrap(new byte[] { 0x50, 0x49, 0x4E, 0x47 });

            // Send PING every 500 ms
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    pingPacketData.clear();
                    rtp.write(pingPacketData);
                    Thread.sleep(500);
                }
            } catch (IOException e) {
                logger.warn("Failed to send an audio ping", e);
            } catch (InterruptedException e) {
                // Interrupted
            } finally {
                parent.stop();
            }
        });
        threads.add(t);
        t.setPriority(Thread.MIN_PRIORITY);
        t.setName("Audio - Ping");
        t.start();
    }
}
