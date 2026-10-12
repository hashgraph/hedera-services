// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.metrics.extensions.internal;

import com.swirlds.base.time.Time;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;

/**
 * Slides a {@link LatencyWindow} over the values of a {@link ConcurrentLatencyRecorder}, so that it holds the values
 * recorded during the last window length.
 *
 * <p>It keeps a ring of snapshots of the recorder, one per metrics snapshot interval, and sets the window to the
 * difference between the newest and the one a window length ago. Until the ring is full, the oldest snapshot is still
 * empty, so the window holds everything since the start.
 *
 * <p>The first read of a metrics snapshot round advances the window: if at least half an interval has passed since the
 * last advance, it takes a snapshot and sets the window, and later reads of the round get the same window. A round can
 * read a gauge more than once, and all gauges of a recorder must report on the same window. Half an interval tolerates
 * rounds that come early or late. If no value was recorded during the last window length, the window keeps the values
 * of the last window that had some, so it is empty only before the first value.
 *
 * <p>This class is not thread-safe.
 */
public final class SlidingLatencyWindow {

    /** The recorder to take snapshots of. */
    private final ConcurrentLatencyRecorder recorder;

    /** The source of the current time. */
    private final Time time;

    /** The time that must pass since the last advance before a read advances the window, half an interval. */
    private final long advanceThresholdNanos;

    /** The ring of snapshots of the recorder: one per interval of the window, plus the one it starts at. */
    private final RingBuffer<LatencySnapshot> snapshots;

    /** The window over the values. */
    private final LatencyWindow window;

    /** The time of the last advance, from {@link Time#nanoTime()}. */
    private long lastAdvance;

    /**
     * Creates an empty sliding window.
     *
     * @param recorder the recorder to take snapshots of
     * @param time     the source of the current time
     * @param interval the metrics snapshot interval
     * @param length   the time the window covers, at least one interval, rounded up to whole intervals
     * @throws IllegalArgumentException if the interval is not positive or the length is shorter than the interval
     */
    public SlidingLatencyWindow(
            @NonNull final ConcurrentLatencyRecorder recorder,
            @NonNull final Time time,
            @NonNull final Duration interval,
            @NonNull final Duration length) {
        if (!interval.isPositive()) {
            throw new IllegalArgumentException("The metrics snapshot interval must be positive, but is " + interval);
        }
        if (length.compareTo(interval) < 0) {
            throw new IllegalArgumentException("The window length (" + length
                    + ") must not be shorter than the metrics snapshot interval (" + interval + ")");
        }
        final HistogramLayout layout = recorder.layout();
        final int intervals = (int) Math.ceilDiv(length.toNanos(), interval.toNanos());
        this.recorder = recorder;
        this.time = time;
        this.advanceThresholdNanos = interval.toNanos() / 2;
        this.snapshots = new RingBuffer<>(intervals + 1, () -> new LatencySnapshot(layout));
        this.window = new LatencyWindow(layout);
        this.lastAdvance = time.nanoTime();
    }

    /**
     * Returns the window, advancing it first if at least half an interval has passed since the last advance.
     *
     * @return the window over the values recorded during the last window length
     */
    @NonNull
    public LatencyWindow current() {
        final long now = time.nanoTime();
        if (now - lastAdvance >= advanceThresholdNanos) {
            lastAdvance = now;
            advance();
        }
        return window;
    }

    /**
     * Takes a snapshot into the slot of the oldest one and sets the window to the values recorded since the new oldest.
     */
    private void advance() {
        final LatencySnapshot newest = snapshots.advance();
        newest.copyFrom(recorder);
        final LatencySnapshot oldest = snapshots.oldest();
        if (newest.total() != oldest.total()) {
            window.set(oldest, newest);
        }
    }
}
