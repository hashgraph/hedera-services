/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.mono.utils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A statistics "counter" that collects (reduces) `Long` samples, keeping track of the number of
 * samples, the total accumulated, and the maximum seen.
 */
public final class LongSampleAccumulator {

    /** (Base) name for this accumulator */
    private final String name;

    /**
     * Scaling factor for this accumulator (used for changing units, e.g., accumulate nanosec but
     * return millisec
     */
    private final long denominator;

    // For the current use case concurrent updates to counters are not needed.  But provided anyway
    // via synchronized blocks - which should be very cheap in the case of no contention, which is
    // what is expected.  (Not using atomics because there are multiple values to be updated
    // together.)

    private int nSamples;
    private long total;
    private long maximum;

    /** Construct an accumulator, providing its name and its scaling factor. */
    public LongSampleAccumulator(@NonNull final String name, long denominator) {
        Objects.requireNonNull(name, "name:String");
        if (0 == denominator) throw new IllegalArgumentException("denominator (scale factor) must be > 0");

        this.name = name;
        this.denominator = denominator;
        reset();
    }

    /**
     * Construct an accumulator, providing its name (direct accumulator: scaling factor is unity)
     */
    public LongSampleAccumulator(@NonNull final String name) {
        this(name, 1L);
    }

    /** Reset accumulator to zero (no samples). */
    public void reset() {
        synchronized (this) {
            nSamples = 0;
            total = 0L;
            maximum = Long.MIN_VALUE;
        }
    }

    /** Add a single sample to the accumulator */
    public void addSample(long sample) {
        synchronized (this) {
            nSamples++;
            total += sample;
            maximum = Long.max(maximum, sample);
        }
    }

    /** Add _all_ the samples of another accumulator into this one */
    public void addSamples(@NonNull final LongSampleAccumulator samples) {
        Objects.requireNonNull(samples, "samples:LongSampleAccumulator");
        final var values = samples.getAccumulatedValues();
        if (denominator != values.denominator)
            throw new IllegalArgumentException("must have same denominator when adding accumulators");
        synchronized (this) {
            nSamples += values.nSamples();
            total += values.total();
            maximum = Long.max(maximum, values.maximum());
        }
    }

    /**
     * Coalesce all samples taken in this accumulator as if they were all just one measurement.
     *
     * <p>Useful when measuring a duration over several periods. Sum of _all_ durations should be
     * treated as one measurement.
     */
    public void coalesceSamples() {
        synchronized (this) {
            nSamples = 1;
            maximum = total;
        }
    }

    /** Unscaled values of an accumulator */
    public record LongSampleAccumulatedValues(
            @NonNull String name, int nSamples, long total, long maximum, long denominator) {}

    /** Return _unscaled_ values of this accumulator (count, total, and maximum) */
    public @NonNull LongSampleAccumulatedValues getAccumulatedValues() {
        synchronized (this) {
            return new LongSampleAccumulatedValues(name, nSamples, total, maximum, denominator);
        }
    }

    /* Scaled values of an accumulator */
    public record DoubleSampleAccumulatedValues(@NonNull String name, int nSamples, double total, double maximum) {}

    /**
     * Return _scaled_ values of this accumulator (count, total, and maximum, the latter two divided
     * by the denominator (scale factor))
     */
    public @NonNull DoubleSampleAccumulatedValues getScaledAccumulatedValues() {
        final var values = getAccumulatedValues();
        return new DoubleSampleAccumulatedValues(
                values.name(),
                values.nSamples(),
                (double) values.total() / (double) values.denominator(),
                values.maximum() != Long.MIN_VALUE
                        ? (double) values.maximum / (double) values.denominator()
                        : -Double.MAX_VALUE);
    }

    public @NonNull String toString() {
        final var values = getAccumulatedValues();
        final String maxIt = "\uD835\uDC5A\uD835\uDC4E\uD835\uDC65"; // "𝑚𝑎𝑥"
        return "%s[#%d, ∑%d, %s %s, ÷%d]"
                .formatted(
                        values.name(),
                        values.nSamples(),
                        values.total(),
                        maxIt,
                        values.maximum() != Long.MIN_VALUE ? Long.toString(values.maximum()) : "MINIMUM",
                        values.denominator());
    }
}
