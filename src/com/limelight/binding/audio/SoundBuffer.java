package com.limelight.binding.audio;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import com.limelight.nvstream.av.ByteBufferDescriptor;

public class SoundBuffer {

    private final Deque<ByteBufferDescriptor> bufferList = new ArrayDeque<>();
    private final int maxBuffers;

    public SoundBuffer(int maxBuffers) {
        this.maxBuffers = maxBuffers;
    }

    public void queue(ByteBufferDescriptor buff) {
        if (bufferList.size() > maxBuffers) {
            bufferList.removeFirst();
        }

        bufferList.addLast(buff);
    }

    public int size() {
        int size = 0;
        for (ByteBufferDescriptor desc : bufferList) {
            size += desc.length;
        }
        return size;
    }

    public int fill(byte[] data, int offset, int length) {
        int filled = 0;

        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.position(offset);
        while (length > 0 && !bufferList.isEmpty()) {
            ByteBufferDescriptor buff = bufferList.getFirst();

            if (buff.length > length) {
                break;
            }

            bb.put(buff.data, buff.offset, buff.length);
            length -= buff.length;
            filled += buff.length;

            bufferList.removeFirst();
        }

        return filled;
    }
}
