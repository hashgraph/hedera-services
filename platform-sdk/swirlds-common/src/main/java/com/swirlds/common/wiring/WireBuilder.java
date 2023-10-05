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
     * @param consumer tasks are passed to this consumer
     */
    WireBuilder(@NonNull final Consumer<T> consumer) {
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
            return new ConcurrentWire<>(consumer);
        }
        return new SequentialWire<>(consumer);
    }
}
