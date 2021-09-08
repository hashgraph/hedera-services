package com.hedera.services.state.jasperdb;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple timer to measure multiple time periods and get the average, min and max time taken. It is designed to be
 * thread safe.
 */
public class Timer {
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong accumulatedTime = new AtomicLong();
    private final AtomicLong minTime = new AtomicLong();
    private final AtomicLong maxTime = new AtomicLong();
    private final ThreadLocal<Long> starts = new ThreadLocal<>();

    /**
     * Start measuring a time period
     */
    public void start() {
        starts.set(System.nanoTime());
    }

    /**
     * End measuring time period
     */
    public void stop() {
        final long end = System.nanoTime();
        final long timeTaken = end - starts.get();
        count.incrementAndGet();
        accumulatedTime.updateAndGet(currentAccumulatedTime -> currentAccumulatedTime + timeTaken);
        minTime.updateAndGet(minTime -> Math.min(minTime, timeTaken));
        maxTime.updateAndGet(maxTime -> Math.max(maxTime, timeTaken));
    }

    /**
     * Reset timer back to zero
     */
    public void reset() {
        count.set(0);
        accumulatedTime.set(0);
        minTime.set(Long.MAX_VALUE);
        maxTime.set(Long.MIN_VALUE);
    }

    /**
     * Get a nicely formatted set of results
     */
    public String toString() {
        return toString(TimeUnit.NANOSECONDS);
    }

    /**
     * Get a nicely formatted set of results
     */
    public String toString(TimeUnit timeUnit) {
        final long average = accumulatedTime.get() / count.get();
        return String.format("{min= %10s, average= %10s, max= %10s, count= %,d}",
                formatDuration(minTime.get(),timeUnit),
                formatDuration(average,timeUnit),
                formatDuration(maxTime.get(),timeUnit),
                count.get());
    }

    private static String formatDuration(long durationNanos, TimeUnit unit) {
        return String.format("%,d %s",unit.convert(durationNanos,TimeUnit.NANOSECONDS),abbreviate(unit));
    }

    private static String abbreviate(TimeUnit unit) {
        switch(unit) {
            case NANOSECONDS:
                return "ns";
            case MICROSECONDS:
                return "μs";
            case MILLISECONDS:
                return "ms";
            case SECONDS:
                return "s";
            case MINUTES:
                return "min";
            case HOURS:
                return "h";
            case DAYS:
                return "d";
            default:
                throw new AssertionError();
        }
    }
}
