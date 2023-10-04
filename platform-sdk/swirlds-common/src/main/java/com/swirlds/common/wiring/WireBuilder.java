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

package com.swirlds.common.wiring;

import com.swirlds.common.wiring.internal.ConcurrentWire;
import com.swirlds.common.wiring.internal.SequentialWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * A builder for wires.
 *
 * @param <T> the type of object that is passed through the wire
 */
public class WireBuilder<T> {

    public static final int UNLIMITED_CAPACITY = -1;

    private final Executor executor;
    private boolean concurrent = false;
    private final Consumer<T> consumer;
    private int capacity = UNLIMITED_CAPACITY; // TODO this is a place holder, not currently implemented

    // Future parameters:
    //  - if we should automatically create metrics for the wire
    //  - name of the wire
    //  - uncaught exception handler
    //  - max concurrency (or would this be implemented by limiting the thread pool size?)

    /**
     * Constructor.
     *
     * @param executor the executor that the wire will use to run tasks
     * @param consumer tasks are passed to this consumer
     */
    WireBuilder(@NonNull final Executor executor, @NonNull final Consumer<T> consumer) {
        this.executor = Objects.requireNonNull(executor);
        this.consumer = Objects.requireNonNull(consumer);
    }

    /**
     * Set whether the wire should be concurrent or not. Default false.
     *
     * @param concurrent true if the wire should be concurrent, false otherwise
     * @return this
     */
    @NonNull
    public WireBuilder<T> withConcurrency(boolean concurrent) {
        this.concurrent = concurrent;
        return this;
    }

    /**
     * Set the capacity of the wire. Wires that are "full" will apply back pressure. Default is
     * {@link #UNLIMITED_CAPACITY}.
     *
     * @param capacity the capacity of the wire
     * @return this
     */
    @NonNull
    public WireBuilder<T> withCapacity(final int capacity) {
        this.capacity = capacity;
        return this;
    }

    /**
     * Build the wire.
     *
     * @return the wire
     */
    @NonNull
    public Wire<T> build() {
        if (concurrent) {
            return new ConcurrentWire<>(executor, consumer);
        }
        return new SequentialWire<>(consumer);
    }
}
