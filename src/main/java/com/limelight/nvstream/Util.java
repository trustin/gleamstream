package com.limelight.nvstream;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class Util {

    private static final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                final Thread thread = new Thread(r, "Task Scheduler");
                thread.setDaemon(true);
                return thread;
            });

    public static long monotonicMillis() {
        return System.nanoTime() / 1000000L;
    }

    public static void execute(Runnable task) {
        executor.execute(task);
    }

    public static Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    public static ScheduledFuture<?> scheduleAtFixedDelay(
            Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return executor.scheduleWithFixedDelay(task, initialDelay, delay, unit);
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

    private Util() {}
}
