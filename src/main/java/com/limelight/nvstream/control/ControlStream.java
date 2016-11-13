package com.limelight.nvstream.control;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Queue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.jctools.queues.SpscLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;
import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.Util;
import com.limelight.nvstream.av.ConnectionStatusListener;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.enet.EnetConnection;

public class ControlStream implements ConnectionStatusListener, InputPacketSender {

    private static final Logger logger = LoggerFactory.getLogger(ControlStream.class);

    private static final int TCP_PORT = 47995;
    private static final int UDP_PORT = 47999;

    private static final int CONTROL_TIMEOUT = 10000;

    private static final int IDX_START_A = 0;
    private static final int IDX_REQUEST_IDR_FRAME = 0;
    private static final int IDX_START_B = 1;
    private static final int IDX_INVALIDATE_REF_FRAMES = 2;
    private static final int IDX_LOSS_STATS = 3;
    private static final int IDX_INPUT_DATA = 5;

    private static final short[] packetTypesGen3 = {
            0x1407, // Request IDR frame
            0x1410, // Start B
            0x1404, // Invalidate reference frames
            0x140c, // Loss Stats
            0x1417, // Frame Stats (unused)
            -1,     // Input data (unused)
    };
    private static final short[] packetTypesGen4 = {
            0x0606, // Request IDR frame
            0x0609, // Start B
            0x0604, // Invalidate reference frames
            0x060a, // Loss Stats
            0x0611, // Frame Stats (unused)
            -1,     // Input data (unused)
    };
    private static final short[] packetTypesGen5 = {
            0x0305, // Start A
            0x0307, // Start B
            0x0301, // Invalidate reference frames
            0x0201, // Loss Stats
            0x0204, // Frame Stats (unused)
            0x0207, // Input data
    };
    private static final short[] packetTypesGen7 = {
            0x0305, // Start A
            0x0307, // Start B
            0x0301, // Invalidate reference frames
            0x0201, // Loss Stats
            0x0204, // Frame Stats (unused)
            0x0206, // Input data
    };

    private static final short[] payloadLengthsGen3 = {
            -1, // Request IDR frame
            16, // Start B
            24, // Invalidate reference frames
            32, // Loss Stats
            64, // Frame Stats
            -1, // Input Data
    };
    private static final short[] payloadLengthsGen4 = {
            -1, // Request IDR frame
            -1, // Start B
            24, // Invalidate reference frames
            32, // Loss Stats
            64, // Frame Stats
            -1, // Input Data
    };
    private static final short[] payloadLengthsGen5 = {
            -1, // Start A
            16, // Start B
            24, // Invalidate reference frames
            32, // Loss Stats
            80, // Frame Stats
            -1, // Input Data
    };
    private static final short[] payloadLengthsGen7 = {
            -1, // Start A
            16, // Start B
            24, // Invalidate reference frames
            32, // Loss Stats
            80, // Frame Stats
            -1, // Input Data
    };

    private static final byte[][] precontructedPayloadsGen3 = {
            new byte[] { 0, 0 }, // Request IDR frame
            null, // Start B
            null, // Invalidate reference frames
            null, // Loss Stats
            null, // Frame Stats
            null, // Input Data
    };
    private static final byte[][] precontructedPayloadsGen4 = {
            new byte[] { 0, 0 }, // Request IDR frame
            new byte[] { 0 },  // Start B
            null, // Invalidate reference frames
            null, // Loss Stats
            null, // Frame Stats
            null, // Input Data
    };
    private static final byte[][] precontructedPayloadsGen5 = {
            new byte[] { 0, 0 }, // Start A
            null, // Start B
            null, // Invalidate reference frames
            null, // Loss Stats
            null, // Frame Stats
            null, // Input Data
    };
    private static final byte[][] precontructedPayloadsGen7 = {
            new byte[] { 0, 0 }, // Start A
            null, // Start B
            null, // Invalidate reference frames
            null, // Loss Stats
            null, // Frame Stats
            null, // Input Data
    };

    private static final int LOSS_REPORT_INTERVAL_MS = 50;

    private int lastGoodFrame;
    private int lastSeenFrame;
    private int lossCountSinceLastReport;

    private final ConnectionContext context;

    // If we drop at least 10 frames in 15 second (or less) window
    // more than 5 times in 60 seconds, we'll display a warning
    public static final int LOSS_PERIOD_MS = 15000;
    public static final int LOSS_EVENT_TIME_THRESHOLD_MS = 60000;
    public static final int MAX_LOSS_COUNT_IN_PERIOD = 10;
    public static final int LOSS_EVENTS_TO_WARN = 5;
    public static final int MAX_SLOW_SINK_COUNT = 2;
    public static final int MESSAGE_DELAY_FACTOR = 3;

    private long lossTimestamp;
    private long lossEventTimestamp;
    private int lossCount;
    private int lossEventCount;

    private int slowSinkCount;

    // Used on Gen 5 servers and above
    private EnetConnection enetConnection;

    // Used on Gen 4 servers and below
    private Socket s;
    private InputStream in;
    private OutputStream out;
    private final byte[] packetSendBuf = new byte[256];
    private final byte[] packetRecvBuf = new byte[4];

    private ScheduledFuture<?> lossStatsFuture;
    private final Runnable resyncTask;
    private final Queue<int[]> invalidReferenceFrameTuples = new SpscLinkedQueue<>();
    private volatile boolean aborting;
    private boolean forceIdrRequest;

    private final short[] packetTypes;
    private final short[] payloadLengths;
    private final byte[][] preconstructedPayloads;

    public ControlStream(ConnectionContext context) {
        this.context = context;

        switch (context.serverGeneration) {
            case ConnectionContext.SERVER_GENERATION_3:
                packetTypes = packetTypesGen3;
                payloadLengths = payloadLengthsGen3;
                preconstructedPayloads = precontructedPayloadsGen3;
                break;
            case ConnectionContext.SERVER_GENERATION_4:
                packetTypes = packetTypesGen4;
                payloadLengths = payloadLengthsGen4;
                preconstructedPayloads = precontructedPayloadsGen4;
                break;
            case ConnectionContext.SERVER_GENERATION_5:
                packetTypes = packetTypesGen5;
                payloadLengths = payloadLengthsGen5;
                preconstructedPayloads = precontructedPayloadsGen5;
                break;
            case ConnectionContext.SERVER_GENERATION_7:
            default:
                packetTypes = packetTypesGen7;
                payloadLengths = payloadLengthsGen7;
                preconstructedPayloads = precontructedPayloadsGen7;
                break;
        }

        if (context.videoDecoderRenderer != null) {
            forceIdrRequest = (context.videoDecoderRenderer.getCapabilities() &
                               VideoDecoderRenderer.CAPABILITY_REFERENCE_FRAME_INVALIDATION) == 0;
        }

        resyncTask = () -> {
            try {
                boolean idrFrameRequired = false;
                int[] tuple = invalidReferenceFrameTuples.poll();
                if (tuple == null) {
                    // Aggregated by previous resync task
                    return;
                }

                // Check for the magic IDR frame tuple
                int[] lastTuple = null;
                if (tuple[0] != 0 || tuple[1] != 0) {
                    // Aggregate all lost frames into one range
                    for (;;) {
                        int[] nextTuple = lastTuple = invalidReferenceFrameTuples.poll();
                        if (nextTuple == null) {
                            break;
                        }

                        // Check if this tuple has IDR frame magic values
                        if (nextTuple[0] == 0 && nextTuple[1] == 0) {
                            // We will need an IDR frame now, but we won't break out
                            // of the loop because we want to dequeue all pending requests
                            idrFrameRequired = true;
                        }
                    }
                } else {
                    // We must require an IDR frame
                    idrFrameRequired = true;
                }

                if (forceIdrRequest || idrFrameRequired) {
                    requestIdrFrame();
                } else {
                    // Update the end of the range to the latest tuple
                    if (lastTuple != null) {
                        tuple[1] = lastTuple[1];
                    }

                    invalidateReferenceFrames(tuple[0], tuple[1]);
                }
            } catch (IOException e) {
                logger.warn("Failed to request an IDR frame", e);
            }
        };
    }

    public void initialize() throws IOException {
        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
            enetConnection = EnetConnection.connect(context.serverAddress.getHostAddress(), UDP_PORT,
                                                    CONTROL_TIMEOUT);
        } else {
            s = new Socket();
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(context.serverAddress, TCP_PORT), CONTROL_TIMEOUT);
            in = s.getInputStream();
            out = s.getOutputStream();
        }
    }

    private synchronized void sendPacket(
            short type, byte[] payload, int offset, int length) throws IOException {
        // Prevent multiple clients from writing to the stream at the same time
        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
            enetConnection.pumpSocket();

            packetSendBuf[0] = (byte) (type & 0xFF);
            packetSendBuf[1] = (byte) (type >>> 8 & 0xFF);
            System.arraycopy(payload, offset, packetSendBuf, 2, length);
            enetConnection.writePacket(packetSendBuf, length + 2);
        } else {
            packetSendBuf[0] = (byte) (type & 0xFF);
            packetSendBuf[1] = (byte) (type >>> 8 & 0xFF);
            packetSendBuf[2] = (byte) (length & 0xFF);
            packetSendBuf[3] = (byte) (length >>> 8 & 0xFF);
            System.arraycopy(payload, offset, packetSendBuf, 4, length);
            out.write(packetSendBuf, 0, length + 4);
            out.flush();
        }
    }

    private synchronized void sendAndDiscardReply(
            short type, byte[] payload, int offset, int length) throws IOException {
        sendPacket(type, payload, offset, length);
        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
            enetConnection.readPacket(0, CONTROL_TIMEOUT);
        } else {
            ByteStreams.readFully(in, packetRecvBuf, 0, 4);
            final int recvLength = packetRecvBuf[2] & 0xFF | (packetRecvBuf[3] & 0xFF) << 8;
            ByteStreams.skipFully(in, recvLength);
        }
    }

    private void sendLossStats(ByteBuffer bb) throws IOException {
        bb.rewind();
        bb.putInt(lossCountSinceLastReport); // Packet loss count
        bb.putInt(LOSS_REPORT_INTERVAL_MS); // Time since last report in milliseconds
        bb.putInt(1000);
        bb.putLong(lastGoodFrame); // Last successfully received frame
        bb.putInt(0);
        bb.putInt(0);
        bb.putInt(0x14);

        sendPacket(packetTypes[IDX_LOSS_STATS],
                   bb.array(), bb.arrayOffset(), payloadLengths[IDX_LOSS_STATS]);
    }

    @Override
    public void sendInputPacket(byte[] data, int offset, int length) throws IOException {
        sendPacket(packetTypes[IDX_INPUT_DATA], data, offset, length);
    }

    public void abort() {
        if (aborting) {
            return;
        }

        aborting = true;

        if (lossStatsFuture != null) {
            lossStatsFuture.cancel(false);
            lossStatsFuture = null;
        }

        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                logger.warn("Failed to close the control stream", e);
            }
        }

        if (enetConnection != null) {
            enetConnection.close();
        }
    }

    public void start() throws IOException {
        // Use a finite timeout during the handshake process
        if (s != null) {
            s.setSoTimeout(CONTROL_TIMEOUT);
        }

        doStartA();
        doStartB();

        // Return to an infinite read timeout after the initial control handshake
        if (s != null) {
            s.setSoTimeout(0);
        }

        final ByteBuffer lossStatsBuf = ByteBuffer.allocate(payloadLengths[IDX_LOSS_STATS])
                                                  .order(ByteOrder.LITTLE_ENDIAN);
        lossStatsFuture = Util.scheduleAtFixedDelay(() -> {
            try {
                sendLossStats(lossStatsBuf);
                lossCountSinceLastReport = 0;
            } catch (IOException ignored) {}
        }, LOSS_REPORT_INTERVAL_MS, LOSS_REPORT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void doStartA() throws IOException {
        sendAndDiscardReply(packetTypes[IDX_START_A],
                            preconstructedPayloads[IDX_START_A], 0,
                            preconstructedPayloads[IDX_START_A].length);
    }

    private void doStartB() throws IOException {
        // Gen 3 and 5 both use a packet of this form
        if (context.serverGeneration != ConnectionContext.SERVER_GENERATION_4) {
            ByteBuffer payload = ByteBuffer.wrap(new byte[payloadLengths[IDX_START_B]]).order(
                    ByteOrder.LITTLE_ENDIAN);

            payload.putInt(0);
            payload.putInt(0);
            payload.putInt(0);
            payload.putInt(0xa);

            sendAndDiscardReply(packetTypes[IDX_START_B],
                                payload.array(), 0, payloadLengths[IDX_START_B]);
        } else {
            sendAndDiscardReply(packetTypes[IDX_START_B],
                                preconstructedPayloads[IDX_START_B], 0,
                                preconstructedPayloads[IDX_START_B].length);
        }
    }

    private void requestIdrFrame() throws IOException {
        // On Gen 3, we use the invalidate reference frames trick.
        // On Gen 4+, we use the known IDR frame request packet
        // On Gen 5, we're currently using the invalidate reference frames trick again.

        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
            ByteBuffer conf = ByteBuffer.wrap(new byte[payloadLengths[IDX_INVALIDATE_REF_FRAMES]]).order(
                    ByteOrder.LITTLE_ENDIAN);

            //conf.putLong(firstLostFrame);
            //conf.putLong(nextSuccessfulFrame);

            // Early on, we'll use a special IDR sequence. Otherwise,
            // we'll just say we lost the last 32 frames. This is larger
            // than the number of buffered frames in the encoder (16) so
            // it should trigger an IDR frame.
            if (lastSeenFrame < 0x20) {
                conf.putLong(0);
                conf.putLong(0x20);
            } else {
                conf.putLong(lastSeenFrame - 0x20);
                conf.putLong(lastSeenFrame);
            }
            conf.putLong(0);

            sendAndDiscardReply(packetTypes[IDX_INVALIDATE_REF_FRAMES],
                                conf.array(), conf.arrayOffset(),
                                payloadLengths[IDX_INVALIDATE_REF_FRAMES]);
        } else {
            sendAndDiscardReply(packetTypes[IDX_REQUEST_IDR_FRAME],
                                preconstructedPayloads[IDX_REQUEST_IDR_FRAME], 0,
                                preconstructedPayloads[IDX_REQUEST_IDR_FRAME].length);
        }

        logger.warn("IDR frame request sent");
    }

    private void invalidateReferenceFrames(int firstLostFrame, int nextSuccessfulFrame) throws IOException {
        logger.warn("Invalidating reference frames from " + firstLostFrame + " to " + nextSuccessfulFrame);

        ByteBuffer conf = ByteBuffer.wrap(new byte[payloadLengths[IDX_INVALIDATE_REF_FRAMES]]).order(
                ByteOrder.LITTLE_ENDIAN);

        conf.putLong(firstLostFrame);
        conf.putLong(nextSuccessfulFrame);
        conf.putLong(0);

        sendAndDiscardReply(packetTypes[IDX_INVALIDATE_REF_FRAMES],
                            conf.array(), conf.arrayOffset(),
                            payloadLengths[IDX_INVALIDATE_REF_FRAMES]);

        logger.warn("Reference frame invalidation sent");
    }

    private void resyncConnection(int firstLostFrame, int nextSuccessfulFrame) {
        invalidReferenceFrameTuples.add(new int[] { firstLostFrame, nextSuccessfulFrame });
        Util.execute(resyncTask);
    }

    @Override
    public void connectionDetectedFrameLoss(int firstLostFrame, int nextSuccessfulFrame) {
        resyncConnection(firstLostFrame, nextSuccessfulFrame);

        // Suppress connection warnings for the first 150 frames to allow the connection
        // to stabilize
        if (lastGoodFrame < 150) {
            return;
        }

        // Reset the loss count if it's been too long
        if (Util.monotonicMillis() > LOSS_PERIOD_MS + lossTimestamp) {
            lossCount = 0;
            lossTimestamp = Util.monotonicMillis();
        }

        // Count this loss event
        if (++lossCount == MAX_LOSS_COUNT_IN_PERIOD) {
            // Reset the loss event count if it's been too long
            if (Util.monotonicMillis() > LOSS_EVENT_TIME_THRESHOLD_MS + lossEventTimestamp) {
                lossEventCount = 0;
                lossEventTimestamp = Util.monotonicMillis();
            }

            if (++lossEventCount == LOSS_EVENTS_TO_WARN) {
                logger.warn("Poor network connection");

                lossEventCount = 0;
                lossEventTimestamp = 0;
            }

            lossCount = 0;
            lossTimestamp = 0;
        }
    }

    @Override
    public void connectionSinkTooSlow(int firstLostFrame, int nextSuccessfulFrame) {
        resyncConnection(firstLostFrame, nextSuccessfulFrame);

        // Suppress connection warnings for the first 150 frames to allow the connection
        // to stabilize
        if (lastGoodFrame < 150) {
            return;
        }

        if (++slowSinkCount == MAX_SLOW_SINK_COUNT) {
            logger.warn(
                    "Your device is processing the A/V data too slowly. Try lowering stream resolution and/or frame rate.");
            slowSinkCount = -MAX_SLOW_SINK_COUNT * MESSAGE_DELAY_FACTOR;
        }
    }

    @Override
    public void connectionReceivedCompleteFrame(int frameIndex) {
        lastGoodFrame = frameIndex;
    }

    @Override
    public void connectionSawFrame(int frameIndex) {
        lastSeenFrame = frameIndex;
    }

    @Override
    public void connectionLostPackets(int lastReceivedPacket, int nextReceivedPacket) {
        // Update the loss count for the next loss report
        lossCountSinceLastReport += nextReceivedPacket - lastReceivedPacket - 1;
    }
}
