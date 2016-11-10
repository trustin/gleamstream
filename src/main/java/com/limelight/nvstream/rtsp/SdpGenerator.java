package com.limelight.nvstream.rtsp;

import java.net.Inet6Address;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.av.video.VideoDecoderRenderer.VideoFormat;

public final class SdpGenerator {

    private static void addSessionAttribute(StringBuilder config, String attribute, String value) {
        config.append("a=" + attribute + ':' + value + " \r\n");
    }

    private static void addSessionAttributeBytes(StringBuilder config, String attribute, byte[] value) {
        char[] str = new char[value.length];

        for (int i = 0; i < value.length; i++) {
            str[i] = (char) value[i];
        }

        addSessionAttribute(config, attribute, new String(str));
    }

    private static void addSessionAttributeInt(StringBuilder config, String attribute, int value) {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        b.putInt(value);
        addSessionAttributeBytes(config, attribute, b.array());
    }

    private static void addGen3Attributes(StringBuilder config, ConnectionContext context) {
        addSessionAttribute(config, "x-nv-general.serverAddress", context.serverAddress.getHostAddress());

        addSessionAttributeInt(config, "x-nv-general.featureFlags", 0x42774141);

        addSessionAttributeInt(config, "x-nv-video[0].transferProtocol", 0x41514141);
        addSessionAttributeInt(config, "x-nv-video[1].transferProtocol", 0x41514141);
        addSessionAttributeInt(config, "x-nv-video[2].transferProtocol", 0x41514141);
        addSessionAttributeInt(config, "x-nv-video[3].transferProtocol", 0x41514141);

        addSessionAttributeInt(config, "x-nv-video[0].rateControlMode", 0x42414141);
        addSessionAttributeInt(config, "x-nv-video[1].rateControlMode", 0x42514141);
        addSessionAttributeInt(config, "x-nv-video[2].rateControlMode", 0x42514141);
        addSessionAttributeInt(config, "x-nv-video[3].rateControlMode", 0x42514141);

        addSessionAttribute(config, "x-nv-vqos[0].bw.flags", "14083");

        addSessionAttribute(config, "x-nv-vqos[0].videoQosMaxConsecutiveDrops", "0");
        addSessionAttribute(config, "x-nv-vqos[1].videoQosMaxConsecutiveDrops", "0");
        addSessionAttribute(config, "x-nv-vqos[2].videoQosMaxConsecutiveDrops", "0");
        addSessionAttribute(config, "x-nv-vqos[3].videoQosMaxConsecutiveDrops", "0");
    }

    private static void addGen4Attributes(StringBuilder config, ConnectionContext context) {
        addSessionAttribute(config, "x-nv-general.serverAddress",
                            "rtsp://" + context.serverAddress.getHostAddress() + ":48010");

        addSessionAttribute(config, "x-nv-video[0].rateControlMode", "4");
    }

    private static void addGen5Attributes(StringBuilder config) {
        // We want to use the new ENet connections for control and input
        addSessionAttribute(config, "x-nv-general.useReliableUdp", "1");
        addSessionAttribute(config, "x-nv-ri.useControlChannel", "1");

        // Disable dynamic resolution switching
        addSessionAttribute(config, "x-nv-vqos[0].drc.enable", "0");
    }

    public static String generateSdpFromContext(ConnectionContext context) {
        // By now, we must have decided on a format
        if (context.negotiatedVideoFormat == VideoFormat.Unknown) {
            throw new IllegalStateException(
                    "Video format negotiation must be completed before generating SDP response");
        }

        // Also, resolution and frame rate must be set
        if (context.negotiatedWidth == 0 || context.negotiatedHeight == 0 || context.negotiatedFps == 0) {
            throw new IllegalStateException(
                    "Video resolution/FPS negotiation must be completed before generating SDP response");
        }

        StringBuilder config = new StringBuilder();
        config.append("v=0").append("\r\n"); // SDP Version 0
        config.append("o=android 0 " + RtspConnection.getRtspVersionFromContext(context) + " IN ");
        if (context.serverAddress instanceof Inet6Address) {
            config.append("IPv6 ");
        } else {
            config.append("IPv4 ");
        }
        config.append(context.serverAddress.getHostAddress());
        config.append("\r\n");
        config.append("s=NVIDIA Streaming Client").append("\r\n");

        addSessionAttribute(config, "x-nv-video[0].clientViewportWd", String.valueOf(context.negotiatedWidth));
        addSessionAttribute(config, "x-nv-video[0].clientViewportHt", String.valueOf(context.negotiatedHeight));
        addSessionAttribute(config, "x-nv-video[0].maxFPS", String.valueOf(context.negotiatedFps));

        addSessionAttribute(config, "x-nv-video[0].packetSize",
                            String.valueOf(context.streamConfig.getMaxPacketSize()));

        addSessionAttribute(config, "x-nv-video[0].timeoutLengthMs", "7000");
        addSessionAttribute(config, "x-nv-video[0].framesWithInvalidRefThreshold", "0");

        // H.265 can encode much more efficiently, but we have a problem since not all
        // users will be using H.265 and we don't have an independent bitrate setting
        // for H.265. We'll use use the selected bitrate * .75 when H.265 is in use.
        int bitrate;
        if (context.negotiatedVideoFormat == VideoFormat.H265) {
            bitrate = (int) (context.streamConfig.getBitrate() * 0.75);
        } else {
            bitrate = context.streamConfig.getBitrate();
        }

        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
            addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrateKbps", String.valueOf(bitrate));
            addSessionAttribute(config, "x-nv-vqos[0].bw.maximumBitrateKbps", String.valueOf(bitrate));
        } else {
            if (context.streamConfig.getRemote()) {
                addSessionAttribute(config, "x-nv-video[0].averageBitrate", "4");
                addSessionAttribute(config, "x-nv-video[0].peakBitrate", "4");
            }

            // We don't support dynamic bitrate scaling properly (it tends to bounce between min and max and never
            // settle on the optimal bitrate if it's somewhere in the middle), so we'll just latch the bitrate
            // to the requested value.
            addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", String.valueOf(bitrate));
            addSessionAttribute(config, "x-nv-vqos[0].bw.maximumBitrate", String.valueOf(bitrate));
        }

        // Using FEC turns padding on which makes us have to take the slow path
        // in the depacketizer, not to mention exposing some ambiguous cases with
        // distinguishing padding from valid sequences. Since we can only perform
        // execute an FEC recovery on a 1 packet frame, we'll just turn it off completely.
        addSessionAttribute(config, "x-nv-vqos[0].fec.enable", "0");

        addSessionAttribute(config, "x-nv-vqos[0].videoQualityScoreUpdateTime", "5000");

        if (context.streamConfig.getRemote()) {
            addSessionAttribute(config, "x-nv-vqos[0].qosTrafficType", "0");
        } else {
            addSessionAttribute(config, "x-nv-vqos[0].qosTrafficType", "5");
        }

        if (context.streamConfig.getRemote()) {
            addSessionAttribute(config, "x-nv-aqos.qosTrafficType", "0");
        } else {
            addSessionAttribute(config, "x-nv-aqos.qosTrafficType", "4");
        }

        // Add generation-specific attributes
        switch (context.serverGeneration) {
            case ConnectionContext.SERVER_GENERATION_3:
                addGen3Attributes(config, context);
                break;

            case ConnectionContext.SERVER_GENERATION_4:
                addGen4Attributes(config, context);
                break;
            case ConnectionContext.SERVER_GENERATION_5:
            default:
                addGen5Attributes(config);
                break;
        }

        // Gen 4+ supports H.265 and surround sound
        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_4) {
            // If client and server are able, request HEVC
            if (context.negotiatedVideoFormat == VideoFormat.H265) {
                addSessionAttribute(config, "x-nv-clientSupportHevc", "1");
                addSessionAttribute(config, "x-nv-vqos[0].bitStreamFormat", "1");

                // Disable slicing on HEVC
                addSessionAttribute(config, "x-nv-video[0].videoEncoderSlicesPerFrame", "1");
            } else {
                // Otherwise, use AVC
                addSessionAttribute(config, "x-nv-clientSupportHevc", "0");
                addSessionAttribute(config, "x-nv-vqos[0].bitStreamFormat", "0");

                // Use slicing for increased performance on some decoders
                addSessionAttribute(config, "x-nv-video[0].videoEncoderSlicesPerFrame", "4");
            }

            // Enable surround sound if configured for it
            addSessionAttribute(config, "x-nv-audio.surround.numChannels",
                                String.valueOf(context.streamConfig.getAudioChannelCount()));
            addSessionAttribute(config, "x-nv-audio.surround.channelMask",
                                String.valueOf(context.streamConfig.getAudioChannelMask()));
            if (context.streamConfig.getAudioChannelCount() > 2) {
                addSessionAttribute(config, "x-nv-audio.surround.enable", "1");
            } else {
                addSessionAttribute(config, "x-nv-audio.surround.enable", "0");
            }
        }

        config.append("t=0 0").append("\r\n");

        if (context.serverGeneration == ConnectionContext.SERVER_GENERATION_3) {
            config.append("m=video 47996  ").append("\r\n");
        } else {
            config.append("m=video 47998  ").append("\r\n");
        }

        return config.toString();
    }

    private SdpGenerator() {}
}
