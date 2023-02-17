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

import com.hedera.services.stream.proto.SidecarType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Closeable;
import java.time.Duration;

/**
 * Holds the accumulators needed for the metrics/logging of a sidecar record (corresponding, most
 * likely, to a record block. There's a separate accumulator for each kind of sidecar record
 * (bytecode, state change, actions).
 */
public interface SidecarInstrumentation {

    /**
     * Add a sample of the serialized size of a sidecar transaction, for a particular kind of
     * sidecar
     */
    void addSample(@NonNull final SidecarType type, long size);

    /**
     * Add a sample of the compute duration of a sidecar transaction, for a particular kind of
     * sidecar
     */
    void addSample(@NonNull final SidecarType type, @NonNull final Duration duration);

    /**
     * Add a sample of both serialized size and compute time for a sidecar transaction, for a
     * particular kind of sidecar
     */
    void addSample(@NonNull final SidecarType type, long size, @NonNull final Duration duration);

    /**
     * Add the entire accumulated values of a SidecarInstrumentation to this one (which is a summary
     * of multiple records).  In use ensure that these roll-ups go only in one direction - from more
     * specific accumulator to more general accumulator - and that will in turn ensure that there are
     * no deadlock/livelock problems (when this method takes a lock so it can add all samples
     * atomically.)
     */
    void addSamples(@NonNull final SidecarInstrumentation samples);

    /**
     * Start timing a split. Use the returned record in a try-with-resources that encloses the
     * computation to be timed.
     */
    Closeable startTimer(@NonNull final SidecarType type);

    /**
     * Measure a duration split for a computation given by a `Runnable`, add that duration split as
     * a sample.
     */
    void captureDurationSplit(@NonNull final SidecarType type, @NonNull final Runnable fn);

    /** Just like a callable except _doesn't_ declare it throws a checked exception */
    interface ExceptionFreeCallable<V> {
        V call();
    }

    /**
     * Measure a duration split for a computation given by a `Runnable`, add that duration split as
     * a sample.
     */
    <V> V captureDurationSplit(@NonNull final SidecarType type, @NonNull final ExceptionFreeCallable<V> fn);

    /**
     * Finalize a duration measurement by adding all the splits, for one sidecar type, together as
     * one sample to the compute duration.
     */
    void addDurationSplitsAsDurationSample(@NonNull final SidecarType type);

    /* Reset all accumulators (all sidecar types) to zero samples */
    void reset();

    // Copy management: the same measurements are used for multiple sidecar transactions.  And
    // they need to be passed across thread boundaries via a queue.  There's no way to know which
    // is the last one for a given sidecar record, and no way to signal that across the queue.
    // So a counter is incremented every time this measurement set is passed through the queue,
    // and decremented when it is removed.  When the counter reaches zero it's time to log and
    // report
    // metrics.

    /** Increment the copy counter when passing this across the record stream queue. */
    void addCopy();

    /**
     * Decrement the copy counter when processing this after fetching it from the records stream
     * queue. Returns `true` when this is the _last_ copy coming across the queue (it's time to log
     * and issue metrics).
     */
    boolean removeCopy();

    String toString(@NonNull final SidecarType type);

    /**
     * Create a _cheap_ (no heap!) do-nothing implementation for: 1. some uses where a
     * instrumentation is required by a function signature but we don't need the metrics 2. some
     * uses where you need to create a placeholder before a "real" one is created 3. all the tests
     * where we aren't tracking metrics at all
     */
    @SuppressWarnings("java:S1186") // suppress warning about lots of empty method bodies: it's intentional
    static SidecarInstrumentation createNoop() {
        return new NoopSidecarInstrumentation();
    }
}
