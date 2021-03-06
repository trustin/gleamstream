package kr.motd.gleamstream;

import static org.bytedeco.javacpp.avcodec.AVCodecContext.FF_THREAD_SLICE;
import static org.bytedeco.javacpp.avcodec.AV_CODEC_FLAG2_FAST;
import static org.bytedeco.javacpp.avcodec.AV_CODEC_FLAG_LOW_DELAY;
import static org.bytedeco.javacpp.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.javacpp.avcodec.AV_CODEC_ID_HEVC;
import static org.bytedeco.javacpp.avcodec.AV_INPUT_BUFFER_PADDING_SIZE;
import static org.bytedeco.javacpp.avcodec.av_packet_alloc;
import static org.bytedeco.javacpp.avcodec.avcodec_alloc_context3;
import static org.bytedeco.javacpp.avcodec.avcodec_find_decoder;
import static org.bytedeco.javacpp.avcodec.avcodec_open2;
import static org.bytedeco.javacpp.avcodec.avcodec_receive_frame;
import static org.bytedeco.javacpp.avcodec.avcodec_register_all;
import static org.bytedeco.javacpp.avcodec.avcodec_send_packet;
import static org.bytedeco.javacpp.avutil.AV_PIX_FMT_BGR0;
import static org.bytedeco.javacpp.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.javacpp.avutil.AV_SAMPLE_FMT_U8;
import static org.bytedeco.javacpp.avutil.av_frame_alloc;
import static org.bytedeco.javacpp.swscale.SWS_FAST_BILINEAR;
import static org.bytedeco.javacpp.swscale.sws_getContext;
import static org.bytedeco.javacpp.swscale.sws_scale;

import java.nio.ByteBuffer;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.avcodec.AVCodec;
import org.bytedeco.javacpp.avcodec.AVCodecContext;
import org.bytedeco.javacpp.avcodec.AVPacket;
import org.bytedeco.javacpp.avutil.AVDictionary;
import org.bytedeco.javacpp.avutil.AVFrame;
import org.bytedeco.javacpp.swscale.SwsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.video.VideoDecodeUnit;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.av.video.VideoDepacketizer;

import kr.motd.gleamstream.FFmpegFramePool.FFmpegFrame;

final class FFmpegVideoDecoderRenderer extends VideoDecoderRenderer {

    private static final Logger logger = LoggerFactory.getLogger(FFmpegVideoDecoderRenderer.class);

    static {
        avcodec_register_all();
    }

    static void initNativeLibraries() {}

    private static final int DECODER_BUFFER_SIZE = 256 * 1024;

    private final MainWindow mainWindow;
    private final FFmpegFramePool framePool;
    private AVCodecContext ctx;
    private AVFrame decFrame;
    private SwsContext scalerCtx;
    private AVPacket packet;

    private Thread decoderThread;
    private volatile boolean dying;

    private ByteBuffer decoderBuffer;

    private int totalFrames;
    private long totalDecoderTimeMs;

    FFmpegVideoDecoderRenderer(MainWindow mainWindow, int width, int height) {
        this.mainWindow = mainWindow;
        framePool = new FFmpegFramePool(width, height);
    }

    /**
     * Sets up the decoder and renderer to render video at the specified dimensions
     * @param width the width of the video to render
     * @param height the height of the video to render
     * @param drFlags flags for the decoder and renderer
     */
    @Override
    public boolean setup(VideoFormat format, int width, int height, int drFlags) {
        assert framePool.width() == width;
        assert framePool.height() == height;

        final AVCodec codec;
        switch (format) {
            case H264:
                codec = avcodec_find_decoder(AV_CODEC_ID_H264);
                break;
            case H265:
                codec = avcodec_find_decoder(AV_CODEC_ID_HEVC);
                break;
            default:
                return false;
        }

        logger.info("Video codec: " + codec.name().getString());

        ctx = avcodec_alloc_context3(codec);
        ctx.pix_fmt(AV_PIX_FMT_YUV420P);
        ctx.sample_fmt(AV_SAMPLE_FMT_U8);
        ctx.width(width);
        ctx.height(height);

        if (format == VideoFormat.H265) {
            ctx.thread_count(2);
            ctx.thread_type(FF_THREAD_SLICE);
        } else {
            ctx.flags(ctx.flags() | AV_CODEC_FLAG_LOW_DELAY);
        }
        ctx.flags2(ctx.flags2() | AV_CODEC_FLAG2_FAST);

        int result = avcodec_open2(ctx, codec, (AVDictionary) null);
        decFrame = av_frame_alloc();
        scalerCtx = sws_getContext(
                ctx.width(), ctx.height(), ctx.pix_fmt(), ctx.width(), ctx.height(), AV_PIX_FMT_BGR0,
                SWS_FAST_BILINEAR, null, null, (DoublePointer) null);

        packet = av_packet_alloc();

        decoderBuffer = ByteBuffer.allocateDirect(DECODER_BUFFER_SIZE + AV_INPUT_BUFFER_PADDING_SIZE);

        return true;
    }

    /**
     * Starts the decoding and rendering of the video stream on a new thread
     */
    @Override
    public boolean start(final VideoDepacketizer depacketizer) {
        decoderThread = new Thread(() -> {
            VideoDecodeUnit du;
            while (!dying) {
                try {
                    du = depacketizer.takeNextDecodeUnit();
                } catch (InterruptedException e) {
                    // Interrupted
                    break;
                }

                if (du != null) {
                    submitDecodeUnit(du);
                    depacketizer.freeDecodeUnit(du);
                }

            }
        });
        decoderThread.setPriority(Thread.MAX_PRIORITY - 1);
        decoderThread.setName("Video - Decoder (CPU)");
        decoderThread.start();
        return true;
    }

    /**
     * Stops the decoding and rendering of the video stream.
     */
    @Override
    public void stop() {
        dying = true;
        if (decoderThread != null) {
            while (decoderThread.isAlive()) {
                decoderThread.interrupt();
                try {
                    decoderThread.join();
                } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Releases resources held by the decoder.
     */
    @Override
    public void release() {
    }
    // End of VideoDecoderRenderer @Overrides

    /**
     * Give a unit to be decoded to the decoder.
     *
     * @param decodeUnit the unit to be decoded
     */
    public void submitDecodeUnit(VideoDecodeUnit decodeUnit) {

        if (decoderBuffer.capacity() < decodeUnit.getDataLength() + AV_INPUT_BUFFER_PADDING_SIZE) {
            int newCapacity = (int) (1.15f * decodeUnit.getDataLength()) + AV_INPUT_BUFFER_PADDING_SIZE;
            logger.info(
                    "Reallocating decoder buffer from " + decoderBuffer.capacity() + " to " + newCapacity
                    + " bytes");

            decoderBuffer = ByteBuffer.allocateDirect(newCapacity);
        }

        decoderBuffer.clear();

        for (ByteBufferDescriptor bbd = decodeUnit.getBufferHead();
             bbd != null; bbd = bbd.nextDescriptor) {
            decoderBuffer.put(bbd.data, bbd.offset, bbd.length);
        }
        decoderBuffer.flip();

        if (!decoderBuffer.hasRemaining()) {
            return;
        }

        BytePointer ptr = new BytePointer(decoderBuffer);
        packet.data(ptr);
        packet.size(decoderBuffer.limit());

        avcodec_send_packet(ctx, packet);

        int result = avcodec_receive_frame(ctx, decFrame);
        if (result != 0) {
            return;
        }

        // Convert the YUV image to RGB
        FFmpegFrame e;
        try {
            e = framePool.acquire();
        } catch (InterruptedException e1) {
            return;
        }

        // http://stackoverflow.com/questions/22456884/how-to-render-androids-yuv-nv21-camera-image-on-the-background-in-libgdx-with-o
        AVFrame rgbFrame = e.avFrame();
        sws_scale(scalerCtx, decFrame.data(), decFrame.linesize(), 0, ctx.height(), rgbFrame.data(),
                  rgbFrame.linesize());

        mainWindow.addFrame(e);

        long timeAfterDecode = System.nanoTime() / 1000000L;

        // Add delta time to the totals (excluding probable outliers)
        long delta = timeAfterDecode - decodeUnit.getReceiveTimestamp();
        if (delta >= 0 && delta < 300) {
            totalDecoderTimeMs += delta;
            totalFrames++;
        }
    }

    @Override
    public int getAverageDecoderLatency() {
        if (totalFrames == 0) {
            return 0;
        }
        return (int) (totalDecoderTimeMs / totalFrames);
    }
}
