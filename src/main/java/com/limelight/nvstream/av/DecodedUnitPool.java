package com.limelight.nvstream.av;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.jctools.queues.MpscArrayQueue;

public final class DecodedUnitPool<T> {

    private final Queue<T> free;
    private final Queue<T> decoded;
    private final Consumer<T> cleaner;

    public DecodedUnitPool(int size, boolean threadSafe, Supplier<T> factory) {
        this(size, threadSafe, factory, null);
    }

    public DecodedUnitPool(int size, boolean threadSafe, Supplier<T> factory, Consumer<T> cleaner) {
        this(size,
             threadSafe ? MpscArrayQueue::new : ArrayDeque::new,
             threadSafe ? ArrayBlockingQueue::new : ArrayDeque::new,
             factory, cleaner);
    }

    private DecodedUnitPool(int size,
                            IntFunction<Queue<T>> freeQueueFactory,
                            IntFunction<Queue<T>> decodedQueueFactory,
                            Supplier<T> factory, Consumer<T> cleaner) {

        free = freeQueueFactory.apply(size);
        decoded = decodedQueueFactory.apply(size);

        for (int i = 0; i < size; i++) {
            free.add(factory.get());
        }
        this.cleaner = cleaner;
    }

    public T pollFree() {
        return free.poll();
    }

    public void addDecoded(T object) {
        decoded.add(object);
    }

    public void freeDecoded(T object) {
        if (cleaner != null) {
            cleaner.accept(object);
        }
        free.add(object);
    }

    public T pollDecoded() {
        return decoded.poll();
    }

    public T takeDecoded() throws InterruptedException {
        if (!(decoded instanceof BlockingQueue)) {
            throw new UnsupportedOperationException("Blocking is unsupported on this buffer list");
        }

        return ((BlockingQueue<T>) decoded).take();
    }

    public void clearAllDecoded() {
        T object;
        while ((object = pollDecoded()) != null) {
            freeDecoded(object);
        }
    }
}
