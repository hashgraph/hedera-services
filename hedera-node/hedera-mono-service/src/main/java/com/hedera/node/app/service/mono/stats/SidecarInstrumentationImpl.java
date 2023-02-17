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

package com.hedera.node.app.service.mono.stats;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.service.mono.exceptions.Requires;
import com.hedera.services.stream.proto.SidecarType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds the accumulators needed for the metrics/logging of a sidecar record (corresponding, most
 * likely, to a record block. There's a separate accumulator for each kind of sidecar record
 * (bytecode, state change, actions).
 */
public class SidecarInstrumentationImpl implements SidecarInstrumentation {

    private final @NonNull EnumMap<SidecarType, SidecarAccumulators> accumulators;
    private final @NonNull Clock clock;
    private final @NonNull AtomicInteger copies;

    private SidecarInstrumentationImpl(
            final @NonNull EnumMap<SidecarType, SidecarAccumulators> accumulators,
            final @NonNull Clock clock,
            final @NonNull AtomicInteger copies) {
        Requires.nonNull(accumulators, "accumulators:EnumMap<>", clock, "clock:Clock", copies, "copies:AtomicInteger");

        this.accumulators = accumulators;
        this.clock = clock;
        this.copies = copies;
    }

    /** Constructs the sidecar instrumentation */
    public SidecarInstrumentationImpl() {
        this(new EnumMap<>(SidecarType.class), Clock.systemUTC(), new AtomicInteger());
        createAccumulators();
    }

    /** Constructs the sidecar instrumentation, allowing the injection of a `Clock` (for testing) */
    public SidecarInstrumentationImpl(@NonNull Clock clock) {
        this(new EnumMap<>(SidecarType.class), clock, new AtomicInteger());
        createAccumulators();
    }

    /** Create an accumulator for each kind of sidecar record */
    private void createAccumulators() {
        for (final var e : SidecarType.values()) accumulators.put(e, new SidecarAccumulators(e.name()));
    }

    /**
     * Add a sample of the serialized size of a sidecar transaction, for a particular kind of
     * sidecar
     */
    @Override
    public void addSample(@NonNull final SidecarType type, long size) {
        Objects.requireNonNull(type, "type:SidecarType");
        final var acc = accumulators.get(type);
        acc.getSerializedSize().addSample(size);
    }

    /**
     * Add a sample of the compute duration of a sidecar transaction, for a particular kind of
     * sidecar
     */
    @Override
    public void addSample(@NonNull final SidecarType type, @NonNull final Duration duration) {
        Requires.nonNull(type, "type:SidecarType", duration, "duration:Duration");

        final var acc = accumulators.get(type);
        acc.getComputeDuration().addSample(duration.toNanos());
    }

    /**
     * Add a sample of both serialized size and compute time for a sidecar transaction, for a
     * particular kind of sidecar
     */
    @Override
    public void addSample(@NonNull final SidecarType type, long size, @NonNull final Duration duration) {
        Requires.nonNull(type, "type:SidecarType", duration, "duration:Duration");

        final var acc = accumulators.get(type);
        acc.getSerializedSize().addSample(size);
        acc.getComputeDuration().addSample(duration.toNanos());
    }

    /**
     * Add the entire accumulated values of a SidecarInstrumentation to this one (which is a summary
     * of multiple records
     */
    @SuppressWarnings("java:S2445")
    // sync on method parameter _OK_ because adding samples goes only in _one_ direction ever (from
    // more specific accumulator to more general accumulator)
    @Override
    public void addSamples(@NonNull final SidecarInstrumentation samples) {
        Objects.requireNonNull(samples, "samples:SidecarInstrumentation");

        if (samples instanceof SidecarInstrumentationImpl impl) {
            final var syncObjs = orderByHashcode(this, samples);
            synchronized (syncObjs[0]) {
                synchronized (syncObjs[1]) {
                    for (final var e : SidecarType.values()) {
                        final var thisAcc = accumulators.get(e);
                        final var thatAcc = impl.accumulators.get(e);
                        thisAcc.getComputeDuration().addSamples(thatAcc.getComputeDuration());
                        thisAcc.getSerializedSize().addSamples(thatAcc.getSerializedSize());
                    }
                }
            }
        }
    }

    /**
     * Start timing a split. Use the returned value in a try-with-resources that encloses the
     * computation to be timed.
     */
    @Override
    public Closeable startTimer(@NonNull final SidecarType type) {
        Objects.requireNonNull(type, "type:SidecarType");
        final var start = clock.instant();
        return () -> {
            final var now = clock.instant();
            final var interval = Duration.between(start, now);
            accumulators.get(type).getDurationSplit().addSample(interval.toNanos());
        };
    }

    /**
     * Measure a duration split for a computation given by a `Runnable`, add that duration split as
     * a sample.
     */
    @Override
    public void captureDurationSplit(@NonNull final SidecarType type, @NonNull final Runnable fn) {
        Requires.nonNull(type, "type:SidecarType", fn, "fn:Runnable");

        try (final var timer = startTimer(type)) {
            fn.run();
        } catch (IOException ex) { // because Closeable
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Measure a duration split for a computation given by a `Runnable`, add that duration split as
     * a sample.
     */
    @Override
    public <V> V captureDurationSplit(@NonNull final SidecarType type, @NonNull final ExceptionFreeCallable<V> fn) {
        Requires.nonNull(type, "type:SidecarType", fn, "fn:ExceptionFreeCallable");

        try (final var timer = startTimer(type)) {
            return fn.call();
        } catch (IOException ex) { // because Closeable
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Finalize a duration measurement by adding all the splits, for one sidecar type, together as
     * one sample to the compute duration.
     */
    @Override
    public void addDurationSplitsAsDurationSample(@NonNull final SidecarType type) {
        Objects.requireNonNull(type, "type:SidecarType");
        accumulators.get(type).finalizeDurationSplits();
    }

    /* Reset all accumulators (all sidecar types) to zero samples */
    @Override
    public void reset() {
        for (final var a : accumulators.values()) a.reset();
    }

    // Copy management: the same measurements are used for multiple sidecar transactions.  And
    // they need to be passed across thread boundaries via a queue.  There's no way to know which
    // is the last one for a given sidecar record, and no way to signal that across the queue.
    // So a counter is incremented every time this measurement set is passed through the queue,
    // and decremented when it is removed.  When the counter reaches zero it's time to log and
    // report
    // metrics.

    /** Increment the copy counter when passing this across the record stream queue. */
    @Override
    public void addCopy() {
        copies.incrementAndGet();
    }

    /**
     * Decrement the copy counter when processing this after fetching it from the records stream
     * queue. Returns `true` when this is the _last_ copy coming across the queue (it's time to log
     * and issue metrics).
     */
    @Override
    public boolean removeCopy() {
        return 0 == copies.decrementAndGet();
    }

    @VisibleForTesting
    int getCopies() {
        return copies.get();
    }

    @Override
    public String toString(@NonNull final SidecarType type) {
        Objects.requireNonNull(type, "type:SidecarType");
        return accumulators.get(type).toString();
    }

    /** Order two objects based on their hashCode (why? so you can synchronize _in order_ to avoid deadlock) */
    private static Object[] orderByHashcode(final @NonNull Object a, final @NonNull Object b) {
        Requires.nonNull(a, "a:Object", b, "b:Object");

        if (a == b) throw new IllegalArgumentException("arguments are not distinct objects");

        final int hca = System.identityHashCode(a);
        final int hcb = System.identityHashCode(b);
        if (hca < hcb) return new Object[] {a, b};
        else return new Object[] {b, a};

        // Depends on JVM trying really very hard to not return colliding hashcodes for distinct objects
    }

    @VisibleForTesting
    static Object[] testOrderByHashcode(final @NonNull Object a, final @NonNull Object b) {
        return orderByHashcode(a, b);
    }

    @VisibleForTesting
    Clock getClock() {
        return clock;
    }
}
