package com.limelight.nvstream;

import com.google.common.collect.Iterables;

public final class ThreadUtil {

    public static void stop(Iterable<Thread> threads) {
        stop(Iterables.toArray(threads, Thread.class));
    }

    public static void stop(Thread... threads) {
        for (;;) {
            int deadThreads = 0;
            for (Thread t : threads) {
                if (t != null && t.isAlive()) {
                    t.interrupt();
                } else {
                    deadThreads++;
                }
            }

            if (deadThreads == threads.length) {
                break;
            }

            for (Thread t : threads) {
                if (t == null) {
                    continue;
                }
                try {
                    t.join(1000);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private ThreadUtil() {}
}
