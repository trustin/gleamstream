package com.limelight.nvstream.av;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.limelight.nvstream.Util;

public final class RtpPingSender {

    // Ping in ASCII
    private static final ByteBuffer pingPacketData = ByteBuffer.wrap(new byte[] { 0x50, 0x49, 0x4E, 0x47 });

    public static ScheduledFuture<?> start(DatagramChannel rtpChannel) {
        // Send PING every 500 ms
        return Util.scheduleAtFixedDelay(() -> {
            pingPacketData.clear();
            try {
                rtpChannel.write(pingPacketData);
            } catch (IOException ignored) {}
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private RtpPingSender() {}
}
