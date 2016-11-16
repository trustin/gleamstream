package com.limelight.nvstream.input;

import static kr.motd.gleamstream.Panic.panic;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.Util;
import com.limelight.nvstream.control.InputPacketSender;
import com.limelight.nvstream.input.cipher.AesCbcInputCipher;
import com.limelight.nvstream.input.cipher.AesGcmInputCipher;
import com.limelight.nvstream.input.cipher.FastAesGcmInputCipher;
import com.limelight.nvstream.input.cipher.InputCipher;

public class ControllerStream {

    private static final Logger logger = LoggerFactory.getLogger(ControllerStream.class);

    private static final int PORT = 35043;

    private static final int CONTROLLER_TIMEOUT = 10000;

    private final NvConnection parent;
    private final ConnectionContext context;

    // Only used on Gen 4 or below servers
    private Socket s;
    private OutputStream out;

    // Used on Gen 5+ servers
    private InputPacketSender controlSender;

    private final InputCipher cipher;

    private Thread inputThread;
    private final BlockingQueue<InputPacket> inputQueue = new ArrayBlockingQueue<>(128);
    private final byte[] gamepadAxisDirs = new byte[6];
    private final AtomicInteger droppedPackets = new AtomicInteger();
    private ScheduledFuture<?> droppedPacketWarningFuture;

    private final ByteBuffer stagingBuffer = ByteBuffer.allocate(128);
    private final ByteBuffer sendBuffer = ByteBuffer.allocate(128);

    public ControllerStream(NvConnection parent, ConnectionContext context) {
        this.parent = parent;
        this.context = context;

        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_7) {
            // Newer GFE versions use AES GCM
            InputCipher cipher;
            try {
                cipher = new FastAesGcmInputCipher();
                logger.info("Using fast-path AES/GCM cipher");
            } catch (Throwable t) {
                logger.warn("Using slow-path AES/GCM cipher", t);
                cipher = new AesGcmInputCipher();
            }
            this.cipher = cipher;
        } else {
            // Older versions used AES CBC
            cipher = new AesCbcInputCipher();
        }

        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putInt(context.riKeyId);
        cipher.initialize(context.riKey, bb.array(), 0, bb.capacity());
    }

    public void initialize(InputPacketSender controlSender) throws IOException {
        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
            // Gen 5 sends input over the control stream
            this.controlSender = controlSender;
        } else {
            // Gen 4 and below uses a separate TCP connection for input
            s = new Socket();
            s.connect(new InetSocketAddress(context.serverAddress, PORT), CONTROLLER_TIMEOUT);
            s.setTcpNoDelay(true);
            out = s.getOutputStream();
        }
    }

    public void start() {
        inputThread = new Thread(() -> {
            try {
                // Move the mouse cursor very slightly to wake the screen up for gamepad-only scenarios.
                sendPacket(new MouseMovePacket((short) 1, (short) 1));
                sendPacket(new MouseMovePacket((short) -1, (short) -1));

                while (!Thread.currentThread().isInterrupted()) {
                    final InputPacket packet = inputQueue.take();
                    final int remainingPackets = inputQueue.size();

                    if (remainingPackets != 0) {
                        // Try to batch mouse move packets
                        if (packet instanceof MouseMovePacket) {
                            batchAndSendMouseMovePackets((MouseMovePacket) packet, remainingPackets);
                            continue;
                        }

                        // Try to batch axis changes on controller packets too
                        if (packet instanceof MultiControllerPacket) {
                            batchAndSendControllerPackets((MultiControllerPacket) packet, remainingPackets);
                            continue;
                        }
                    }

                    // Send any other packet as-is
                    sendPacket(packet);
                }
            } catch (InterruptedException e) {
                // Interrupted
            } catch (IOException e) {
                logger.warn("Failed to send a packet:", e);
            } finally {
                parent.stop();
            }
        });
        inputThread.setName("Input - Queue");
        inputThread.setPriority(Thread.NORM_PRIORITY + 1);
        inputThread.start();

        droppedPacketWarningFuture = Util.scheduleAtFixedDelay(() -> {
            final int droppedPackets = this.droppedPackets.getAndSet(0);
            if (droppedPackets != 0) {
                logger.warn("Dropped {} input packets", this.droppedPackets);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void batchAndSendMouseMovePackets(
            MouseMovePacket packet, int maxNumPackets) throws IOException {

        int totalDeltaX = packet.deltaX;
        int totalDeltaY = packet.deltaY;

        // Combine the deltas with other mouse move packets in the queue
        final Iterator<InputPacket> it = inputQueue.iterator();
        for (int i = 0; i < maxNumPackets; i++) {
            final InputPacket queuedPacket = it.next();
            if (queuedPacket instanceof MouseMovePacket) {
                final MouseMovePacket queuedMouseMove = (MouseMovePacket) queuedPacket;

                // Add this packet's deltas to the running total
                totalDeltaX += queuedMouseMove.deltaX;
                totalDeltaY += queuedMouseMove.deltaY;

                // Remove this packet from the queue
                it.remove();
            }
        }

        // Total deltas could overflow the short so we must split them if required
        do {
            short partialDeltaX = (short) (totalDeltaX < 0 ?
                                           Math.max(Short.MIN_VALUE, totalDeltaX) :
                                           Math.min(Short.MAX_VALUE, totalDeltaX));
            short partialDeltaY = (short) (totalDeltaY < 0 ?
                                           Math.max(Short.MIN_VALUE, totalDeltaY) :
                                           Math.min(Short.MAX_VALUE, totalDeltaY));

            packet.deltaX = partialDeltaX;
            packet.deltaY = partialDeltaY;

            sendPacket(packet);

            totalDeltaX -= partialDeltaX;
            totalDeltaY -= partialDeltaY;
        } while (totalDeltaX != 0 && totalDeltaY != 0);
    }

    private void batchAndSendControllerPackets(
            MultiControllerPacket packet, int maxNumPackets) throws IOException {

        Arrays.fill(gamepadAxisDirs, (byte) 0);
        final Iterator<InputPacket> it = inputQueue.iterator();
        for (int i = 0; i < maxNumPackets; i++) {
            final InputPacket queuedPacket = it.next();

            if (queuedPacket instanceof MultiControllerPacket) {
                if (packet.merge((MultiControllerPacket) queuedPacket, gamepadAxisDirs)) {
                    // Batching was successful, so remove this packet
                    it.remove();
                } else {
                    // Unable to batch so we must stop
                    break;
                }
            }
        }

        sendPacket(packet);
    }

    public void abort() {
        if (droppedPacketWarningFuture != null) {
            droppedPacketWarningFuture.cancel(false);
            droppedPacketWarningFuture = null;
        }

        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                logger.warn("Failed to close a Socket", e);
            }
        }

        Util.stop(inputThread);
    }

    private void sendPacket(InputPacket packet) throws IOException {
        // Store the packet in wire form in the byte buffer
        packet.toWire(context, stagingBuffer);
        int packetLen = packet.packetLength();

        // Get final encrypted size of this block
        int paddedLength = cipher.getEncryptedSize(packetLen);

        // Allocate a byte buffer to represent the final packet
        sendBuffer.rewind();
        sendBuffer.putInt(paddedLength);
        try {
            cipher.encrypt(stagingBuffer.array(), stagingBuffer.arrayOffset(), packetLen,
                           sendBuffer.array(), sendBuffer.arrayOffset() + 4);
        } catch (Exception e) {
            // Should never happen
            throw panic(e);
        }

        // Send the packet over the control stream on Gen 5+
        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
            controlSender.sendInputPacket(
                    sendBuffer.array(), sendBuffer.arrayOffset(), paddedLength + 4);

            // For reasons that I can't understand, NVIDIA decides to use the last 16
            // bytes of ciphertext in the most recent game controller packet as the IV for
            // future encryption. I think it may be a buffer overrun on their end but we'll have
            // to mimic it to work correctly.
            if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_7 && paddedLength >= 32) {
                cipher.initialize(context.riKey, sendBuffer.array(), 4 + paddedLength - 16, 16);
            }
        } else {
            // Send the packet over the TCP connection on Gen 4 and below
            out.write(sendBuffer.array(), 0, paddedLength + 4);
            out.flush();
        }
    }

    private void queuePacket(InputPacket packet) {
        // NB: Use offer() rather than add() so we do not block.
        if (!inputQueue.offer(packet)) {
            droppedPackets.incrementAndGet();
        }
    }

    public void sendControllerInput(short controllerNumber,
                                    short buttonFlags, byte leftTrigger, byte rightTrigger,
                                    short leftStickX, short leftStickY, short rightStickX, short rightStickY) {
        if (context.serverGeneration == ConnectionContext.SERVER_GENERATION_3) {
            // Use legacy controller packets for generation 3
            queuePacket(new ControllerPacket(buttonFlags, leftTrigger, rightTrigger,
                                             leftStickX, leftStickY, rightStickX, rightStickY));
        } else {
            // Use multi-controller packets for generation 4 and above
            queuePacket(new MultiControllerPacket(controllerNumber, buttonFlags, leftTrigger, rightTrigger,
                                                  leftStickX, leftStickY, rightStickX, rightStickY));
        }
    }

    public void sendMouseButtonDown(byte mouseButton) {
        queuePacket(new MouseButtonPacket(context, true, mouseButton));
    }

    public void sendMouseButtonUp(byte mouseButton) {
        queuePacket(new MouseButtonPacket(context, false, mouseButton));
    }

    public void sendMouseMove(short deltaX, short deltaY) {
        queuePacket(new MouseMovePacket(deltaX, deltaY));
    }

    public void sendKeyboardInput(short keyMap, byte keyDirection, byte modifier) {
        queuePacket(new KeyboardPacket(keyMap, keyDirection, modifier));
    }

    public void sendMouseScroll(byte scrollClicks) {
        queuePacket(new MouseScrollPacket(scrollClicks));
    }

}
