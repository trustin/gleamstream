package kr.motd.gleamstream;

import static org.bytedeco.javacpp.avutil.AVFrame;
import static org.bytedeco.javacpp.avutil.AV_PIX_FMT_BGR0;
import static org.bytedeco.javacpp.avutil.av_frame_alloc;
import static org.bytedeco.javacpp.avutil.av_image_fill_arrays;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.bytedeco.javacpp.BytePointer;

final class FFmpegFramePool {

    private static final int POOL_SIZE = 8;
    private final BlockingQueue<FFmpegFrame> pool = new ArrayBlockingQueue<>(POOL_SIZE);

    private final int width;
    private final int height;

    FFmpegFramePool(int width, int height) {
        this.width = width;
        this.height = height;

        for (int i = 0; i < POOL_SIZE; i++) {
            AVFrame frame = av_frame_alloc();
            BytePointer buf = new BytePointer(width * height * 4L);
            av_image_fill_arrays(frame.data(), frame.linesize(), buf, AV_PIX_FMT_BGR0,
                                 width, height, 1);
            pool.add(new FFmpegFrame(frame, buf));
        }
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    FFmpegFrame acquire() throws InterruptedException {
        return pool.take();
    }

    final class FFmpegFrame {
        private final AVFrame avFrame;
        private final BytePointer data;
        private final long dataAddress;

        FFmpegFrame(AVFrame avFrame, BytePointer data) {
            this.avFrame = avFrame;
            this.data = data;
            dataAddress = data.address();
        }

        AVFrame avFrame() {
            return avFrame;
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }

        long dataAddress() {
            return dataAddress;
        }

        void release() {
            pool.add(this);
        }
    }
}
