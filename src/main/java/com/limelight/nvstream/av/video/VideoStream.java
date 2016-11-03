package com.limelight.nvstream.av.video;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.av.ConnectionStatusListener;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.RtpPacket;
import com.limelight.nvstream.av.RtpReorderQueue;
import com.limelight.nvstream.av.RtpReorderQueue.RtpQueueStatus;

public class VideoStream {

    private static final Logger logger = LoggerFactory.getLogger(VideoStream.class);

    private static final int RTP_PORT = 47998;
    private static final int FIRST_FRAME_PORT = 47996;

    private static final int FIRST_FRAME_TIMEOUT = 5000;
    private static final int RTP_RECV_BUFFER = 256 * 1024;

    // We can't request an IDR frame until the depacketizer knows
    // that a packet was lost. This timeout bounds the time that
    // the RTP queue will wait for missing/reordered packets.
    private static final int MAX_RTP_QUEUE_DELAY_MS = 10;

    // The ring size MUST be greater than or equal to
    // the maximum number of packets in a fully
    // presentable frame
    private static final int VIDEO_RING_SIZE = 384 * 8;

    private DatagramChannel rtp;
    private Socket firstFrameSocket;

    private final List<Thread> threads = new ArrayList<>();

    private final NvConnection parent;
    private final ConnectionContext context;
    private final ConnectionStatusListener avConnListener;
    private VideoDepacketizer depacketizer;

    private VideoDecoderRenderer decRend;
    private boolean startedRendering;

    private volatile boolean aborting;

    public VideoStream(NvConnection parent, ConnectionContext context, ConnectionStatusListener avConnListener) {
        this.parent = parent;
        this.context = context;
        this.avConnListener = avConnListener;
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

        if (firstFrameSocket != null) {
            try {
                firstFrameSocket.close();
            } catch (IOException e) {
                logger.warn("Failed to close the first-frame socket", e);
            }
        }

        if (decRend != null) {
            if (startedRendering) {
                decRend.stop();
            }
            decRend.release();
        }

        threads.clear();
    }

    private void connectFirstFrame() throws IOException {
        firstFrameSocket = new Socket();
        firstFrameSocket.setSoTimeout(FIRST_FRAME_TIMEOUT);
        firstFrameSocket.connect(new InetSocketAddress(context.serverAddress, FIRST_FRAME_PORT),
                                 FIRST_FRAME_TIMEOUT);
    }

    private void readFirstFrame() throws IOException {
        // We can actually ignore this data. It's the act of gracefully closing the socket
        // that matters.

        firstFrameSocket.close();
        firstFrameSocket = null;
    }

    public void setupRtpSession() throws IOException {
        rtp = DatagramChannel.open();
        rtp.setOption(StandardSocketOptions.SO_RCVBUF, RTP_RECV_BUFFER);
        try {
            rtp.setOption(StandardSocketOptions.IP_TOS, 0x10); // IPTOS_LOWDELAY
        } catch (Exception ignored) {
            // May not be supported on some platforms.
        }
        rtp.connect(new InetSocketAddress(context.serverAddress, RTP_PORT));
    }

    public boolean setupDecoderRenderer(VideoDecoderRenderer decRend, int drFlags) {
        this.decRend = decRend;

        depacketizer = new VideoDepacketizer(context, avConnListener, context.streamConfig.getMaxPacketSize());

        if (decRend != null) {
            try {
                if (!decRend.setup(context.negotiatedVideoFormat, context.negotiatedWidth,
                                   context.negotiatedHeight, drFlags)) {
                    return false;
                }

                if (!decRend.start(depacketizer)) {
                    abort();
                    return false;
                }

                startedRendering = true;
            } catch (Exception e) {
                logger.warn("Failed to initialize the VideoDecoderRender", e);
                return false;
            }
        }

        return true;
    }

    public boolean startVideoStream(int drFlags) throws IOException {
        // Setup the decoder and renderer
        if (!setupDecoderRenderer(context.videoDecoderRenderer, drFlags)) {
            // Nothing to cleanup here
            throw new IOException(
                    "Video decoder failed to initialize. Your device may not support the selected resolution.");
        }

        // Open RTP sockets and start session
        setupRtpSession();

        if (decRend != null) {
            // Start the receive thread early to avoid missing
            // early packets that are part of the IDR frame
            startReceiveThread();
        }

        // Open the first frame port connection on Gen 3 servers
        if (context.serverGeneration == ConnectionContext.SERVER_GENERATION_3) {
            connectFirstFrame();
        }

        // Start pinging before reading the first frame
        // so GFE knows where to send UDP data
        startUdpPingThread();

        // Read the first frame on Gen 3 servers
        if (context.serverGeneration == ConnectionContext.SERVER_GENERATION_3) {
            readFirstFrame();
        }

        return true;
    }

    private void startReceiveThread() {
        // Receive thread
        Thread t = new Thread(() -> {
            VideoPacket[] ring = new VideoPacket[VIDEO_RING_SIZE];
            VideoPacket queuedPacket;
            int ringIndex = 0;
            RtpReorderQueue rtpQueue = new RtpReorderQueue(16, MAX_RTP_QUEUE_DELAY_MS);
            RtpQueueStatus queueStatus;

            boolean directSubmit = decRend != null && (decRend.getCapabilities() &
                                                       VideoDecoderRenderer.CAPABILITY_DIRECT_SUBMIT) != 0;

            // Preinitialize the ring buffer
            int requiredBufferSize = context.streamConfig.getMaxPacketSize() + RtpPacket.MAX_HEADER_SIZE;
            for (int i = 0; i < VIDEO_RING_SIZE; i++) {
                ring[i] = new VideoPacket(new byte[requiredBufferSize], !directSubmit);
            }

            ByteBuffer buffer;
            int iterationStart;

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // Pull the next buffer in the ring and reset it
                    buffer = ring[ringIndex].getByteBuffer();
                    buffer.clear();

                    // Read the video data off the network
                    rtp.read(buffer);
                    buffer.flip();

                    // Initialize the video packet
                    ring[ringIndex].initializeWithLength(buffer.remaining());

                    queueStatus = rtpQueue.addPacket(ring[ringIndex]);
                    if (queueStatus == RtpQueueStatus.HANDLE_IMMEDIATELY) {
                        // Submit immediately because the packet is in order
                        depacketizer.addInputData(ring[ringIndex]);
                    } else if (queueStatus == RtpQueueStatus.QUEUED_PACKETS_READY) {
                        // The packet queue now has packets ready
                        while ((queuedPacket = (VideoPacket) rtpQueue.getQueuedPacket()) != null) {
                            depacketizer.addInputData(queuedPacket);
                            queuedPacket.dereferencePacket();
                        }
                    }

                    // If the DR supports direct submission, call the direct submit callback
                    if (directSubmit) {
                        DecodeUnit du;

                        while ((du = depacketizer.pollNextDecodeUnit()) != null) {
                            decRend.directSubmitDecodeUnit(du);
                        }
                    }

                    // Go to the next free element in the ring
                    iterationStart = ringIndex;
                    do {
                        ringIndex = (ringIndex + 1) % VIDEO_RING_SIZE;
                        if (ringIndex == iterationStart) {
                            // Reinitialize the video ring since they're all being used
                            logger.warn("Packet ring wrapped around!");
                            for (int i = 0; i < VIDEO_RING_SIZE; i++) {
                                ring[i] = new VideoPacket(new byte[requiredBufferSize], !directSubmit);
                            }
                            break;
                        }
                    } while (ring[ringIndex].getRefCount() != 0);
                }
            } catch (ClosedChannelException ignored) {
            } catch (IOException e) {
                logger.warn("Failed to receive a video packet", e);
            } finally {
                parent.stop();
            }
        });
        threads.add(t);
        t.setName("Video - Receive");
        t.setPriority(Thread.MAX_PRIORITY - 1);
        t.start();
    }

    private void startUdpPingThread() {
        // Ping thread
        Thread t = new Thread(() -> {
            // PING in ASCII
            final ByteBuffer pingPacketData = ByteBuffer.wrap(new byte[] { 0x50, 0x49, 0x4E, 0x47 });

            try {
                // Send PING every 500 ms
                while (!Thread.currentThread().isInterrupted()) {
                    pingPacketData.clear();
                    rtp.write(pingPacketData);
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                // Interrupted
            } catch (IOException e) {
                logger.warn("Failed to send a video ping", e);
            } finally {
                parent.stop();
            }
        });
        threads.add(t);
        t.setName("Video - Ping");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }
}
