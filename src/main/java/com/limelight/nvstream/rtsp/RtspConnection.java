package com.limelight.nvstream.rtsp;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;

import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.av.video.VideoDecoderRenderer.VideoFormat;
import com.limelight.nvstream.enet.EnetConnection;
import com.tinyrtsp.rtsp.message.RtspMessage;
import com.tinyrtsp.rtsp.message.RtspRequest;
import com.tinyrtsp.rtsp.message.RtspResponse;
import com.tinyrtsp.rtsp.parser.RtspParser;
import com.tinyrtsp.rtsp.parser.RtspStream;

public class RtspConnection {
    public static final int PORT = 48010;
    public static final int RTSP_TIMEOUT = 10000;

    private int sequenceNumber = 1;
    private int sessionId;
    private EnetConnection enetConnection;

    private final ConnectionContext context;
    private final String hostStr;

    public RtspConnection(ConnectionContext context) {
        this.context = context;
        if (context.serverAddress instanceof Inet6Address) {
            // RFC2732-formatted IPv6 address for use in URL
            hostStr = '[' + context.serverAddress.getHostAddress() + ']';
        } else {
            hostStr = context.serverAddress.getHostAddress();
        }
    }

    private String getRtspVideoStreamName() {
        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
            return "video/0/0";
        } else {
            return "video";
        }
    }

    private String getRtspAudioStreamName() {
        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
            return "audio/0/0";
        } else {
            return "audio";
        }
    }

    public static int getRtspVersionFromContext(ConnectionContext context) {
        switch (context.serverGeneration) {
            case ConnectionContext.SERVER_GENERATION_3:
                return 10;
            case ConnectionContext.SERVER_GENERATION_4:
                return 11;
            case ConnectionContext.SERVER_GENERATION_5:
                return 12;
            case ConnectionContext.SERVER_GENERATION_6:
                // Gen 6 has never been seen in the wild
                return 13;
            case ConnectionContext.SERVER_GENERATION_7:
            default:
                return 14;
        }
    }

    private RtspRequest createRtspRequest(String command, String target) {
        RtspRequest m = new RtspRequest(command, target, "RTSP/1.0",
                                        sequenceNumber++, new HashMap<>(), null);
        m.setOption("X-GS-ClientVersion", String.valueOf(getRtspVersionFromContext(context)));
        return m;
    }

    private static String byteBufferToString(byte[] bytes, int length) {
        StringBuilder message = new StringBuilder();

        for (int i = 0; i < length; i++) {
            message.append((char) bytes[i]);
        }

        return message.toString();
    }

    private RtspResponse transactRtspMessageEnet(RtspMessage m) throws IOException {
        byte[] header, payload;

        header = m.toWireNoPayload();
        payload = m.toWirePayloadOnly();

        // Send the RTSP header
        enetConnection.writePacket(ByteBuffer.wrap(header));

        // Send payload in a separate packet if there's payload on this
        if (payload != null) {
            enetConnection.writePacket(ByteBuffer.wrap(payload));
        }

        // Wait for a response
        ByteBuffer responseHeader = enetConnection.readPacket(2048, RTSP_TIMEOUT);

        // Parse the response and determine whether it has a payload
        RtspResponse message = (RtspResponse) RtspParser.parseMessageNoPayload(
                byteBufferToString(responseHeader.array(), responseHeader.limit()));
        if (message.getOption("Content-Length") != null) {
            // The payload comes in a second packet
            ByteBuffer responsePayload = enetConnection.readPacket(65536, RTSP_TIMEOUT);
            message.setPayload(byteBufferToString(responsePayload.array(), responsePayload.limit()));
        }

        return message;
    }

    private RtspResponse transactRtspMessageTcp(RtspMessage m) throws IOException {
        try (Socket s = new Socket()) {
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(context.serverAddress, PORT), RTSP_TIMEOUT);
            s.setSoTimeout(RTSP_TIMEOUT);

            try (RtspStream rtspStream = new RtspStream(s.getInputStream(), s.getOutputStream())) {
                rtspStream.write(m);
                return (RtspResponse) rtspStream.read();
            }
        }
    }

    private RtspResponse transactRtspMessage(RtspMessage m) throws IOException {
        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
            return transactRtspMessageEnet(m);
        } else {
            return transactRtspMessageTcp(m);
        }
    }

    private RtspResponse requestOptions() throws IOException {
        RtspRequest m = createRtspRequest("OPTIONS", "rtsp://" + hostStr);
        return transactRtspMessage(m);
    }

    private RtspResponse requestDescribe() throws IOException {
        RtspRequest m = createRtspRequest("DESCRIBE", "rtsp://" + hostStr);
        m.setOption("Accept", "application/sdp");
        m.setOption("If-Modified-Since", "Thu, 01 Jan 1970 00:00:00 GMT");
        return transactRtspMessage(m);
    }

    private RtspResponse setupStream(String streamName) throws IOException {
        RtspRequest m = createRtspRequest("SETUP", "streamid=" + streamName);
        if (sessionId != 0) {
            m.setOption("Session", String.valueOf(sessionId));
        }
        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_6) {
            // It looks like GFE doesn't care what we say our port is but
            // we need to give it some port to successfully complete the
            // handshake process.
            m.setOption("Transport", "unicast;X-GS-ClientPort=50000-50001");
        } else {
            m.setOption("Transport", " ");
        }
        m.setOption("If-Modified-Since", "Thu, 01 Jan 1970 00:00:00 GMT");
        return transactRtspMessage(m);
    }

    private RtspResponse playStream(String streamName) throws IOException {
        RtspRequest m = createRtspRequest("PLAY", "streamid=" + streamName);
        m.setOption("Session", String.valueOf(sessionId));
        return transactRtspMessage(m);
    }

    private RtspResponse sendVideoAnnounce() throws IOException {
        RtspRequest m = createRtspRequest("ANNOUNCE", "streamid=video");
        m.setOption("Session", String.valueOf(sessionId));
        m.setOption("Content-type", "application/sdp");
        m.setPayload(SdpGenerator.generateSdpFromContext(context));
        m.setOption("Content-length", String.valueOf(m.getPayload().length()));
        return transactRtspMessage(m);
    }

    private void processDescribeResponse(RtspResponse r) {
        // The RTSP DESCRIBE reply will contain a collection of SDP media attributes that
        // describe the various supported video stream formats and include the SPS, PPS,
        // and VPS (if applicable). We will use this information to determine whether the
        // server can support HEVC. For some reason, they still set the MIME type of the HEVC
        // format to H264, so we can't just look for the HEVC MIME type. What we'll do instead is
        // look for the base 64 encoded VPS NALU prefix that is unique to the HEVC bitstream.
        String describeSdpContent = r.getPayload();
        if (context.streamConfig.getHevcSupported() &&
            describeSdpContent.contains("sprop-parameter-sets=AAAAAU")) {
            context.negotiatedVideoFormat = VideoFormat.H265;
        } else {
            context.negotiatedVideoFormat = VideoFormat.H264;
        }
    }

    private void processRtspSetupAudio(RtspResponse r) throws IOException {
        try {
            sessionId = Integer.parseInt(r.getOption("Session"));
        } catch (NumberFormatException e) {
            throw new IOException("RTSP SETUP response was malformed", e);
        }
    }

    public void doRtspHandshake() throws IOException {
        RtspResponse r;

        // Gen 5+ servers do RTSP over ENet instead of TCP
        if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
            enetConnection = EnetConnection.connect(context.serverAddress.getHostAddress(), PORT, RTSP_TIMEOUT);
        }

        try {
            r = requestOptions();
            if (r.getStatusCode() != 200) {
                throw new IOException("RTSP OPTIONS request failed: " + r.getStatusCode());
            }

            r = requestDescribe();
            if (r.getStatusCode() != 200) {
                throw new IOException("RTSP DESCRIBE request failed: " + r.getStatusCode());
            }

            // Process the RTSP DESCRIBE response
            processDescribeResponse(r);

            r = setupStream(getRtspAudioStreamName());
            if (r.getStatusCode() != 200) {
                throw new IOException("RTSP SETUP request failed: " + r.getStatusCode());
            }

            // Process the RTSP SETUP streamid=audio response
            processRtspSetupAudio(r);

            r = setupStream(getRtspVideoStreamName());
            if (r.getStatusCode() != 200) {
                throw new IOException("RTSP SETUP request failed: " + r.getStatusCode());
            }

            if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
                r = setupStream("control/1/0");
                if (r.getStatusCode() != 200) {
                    throw new IOException("RTSP SETUP request failed: " + r.getStatusCode());
                }
            }

            r = sendVideoAnnounce();
            if (r.getStatusCode() != 200) {
                throw new IOException("RTSP ANNOUNCE request failed: " + r.getStatusCode());
            }

            r = playStream("video");
            if (r.getStatusCode() != 200) {
                throw new IOException("RTSP PLAY request failed: " + r.getStatusCode());
            }
            r = playStream("audio");
            if (r.getStatusCode() != 200) {
                throw new IOException("RTSP PLAY request failed: " + r.getStatusCode());
            }
        } finally {
            if (enetConnection != null) {
                enetConnection.close();
                enetConnection = null;
            }
        }
    }
}
