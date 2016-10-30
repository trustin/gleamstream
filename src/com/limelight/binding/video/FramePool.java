package com.limelight.binding.video;

import static org.bytedeco.javacpp.avutil.AVFrame;
import static org.bytedeco.javacpp.avutil.AV_PIX_FMT_BGR0;
import static org.bytedeco.javacpp.avutil.av_frame_alloc;
import static org.bytedeco.javacpp.avutil.av_image_fill_arrays;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.bytedeco.javacpp.BytePointer;

public class FramePool {
    private static final int POOL_SIZE = 10;
    private final BlockingQueue<Entry<AVFrame, BytePointer>> pool = new ArrayBlockingQueue<>(POOL_SIZE);

    public FramePool(int width, int height) {
        for (int i = 0; i < POOL_SIZE; i++) {
            AVFrame frame = av_frame_alloc();
            BytePointer buf = new BytePointer(width * height * 4L);
            av_image_fill_arrays(frame.data(), frame.linesize(), buf, AV_PIX_FMT_BGR0,
                                 width, height, 1);
            pool.add(new SimpleEntry<>(frame, buf));
        }
    }

    public Entry<AVFrame, BytePointer> acquire() throws InterruptedException {
        return pool.take();
    }

    public void release(Entry<AVFrame, BytePointer> entry) {
        pool.add(entry);
    }
}
