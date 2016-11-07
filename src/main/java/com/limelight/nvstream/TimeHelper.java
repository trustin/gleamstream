package com.limelight.nvstream;

public final class TimeHelper {
    public static long getMonotonicMillis() {
        return System.nanoTime() / 1000000L;
    }

    private TimeHelper() {}
}
