package com.limelight.nvstream.av.buffer;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.jctools.queues.MpscArrayQueue;

public class AtomicPopulatedBufferList<T> extends AbstractPopulatedBufferList<T> {
    private final BlockingQueue<T> populatedList;
    private final Queue<T> freeList;

    @SuppressWarnings("unchecked")
    public AtomicPopulatedBufferList(int maxQueueSize, BufferFactory factory) {
        super(maxQueueSize, factory);

        populatedList = new ArrayBlockingQueue<>(maxQueueSize);
        freeList = new MpscArrayQueue<>(maxQueueSize);

        for (int i = 0; i < maxQueueSize; i++) {
            freeList.add((T) factory.createFreeBuffer());
        }
    }

    @Override
    public int getPopulatedCount() {
        return populatedList.size();
    }

    @Override
    public int getFreeCount() {
        return freeList.size();
    }

    @Override
    public T pollFreeObject() {
        return freeList.poll();
    }

    @Override
    public void addPopulatedObject(T object) {
        populatedList.add(object);
    }

    @Override
    public void freePopulatedObject(T object) {
        factory.cleanupObject(object);
        freeList.add(object);
    }

    @Override
    public T pollPopulatedObject() {
        return populatedList.poll();
    }

    @Override
    public T peekPopulatedObject() {
        return populatedList.peek();
    }

    @Override
    public T takePopulatedObject() throws InterruptedException {
        return populatedList.take();
    }
}
