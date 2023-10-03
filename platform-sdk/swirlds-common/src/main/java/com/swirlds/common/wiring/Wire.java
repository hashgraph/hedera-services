package com.swirlds.common.wiring;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Wires two components together.
 *
 * @param <T> the type of object that is passed through the wire
 */
public interface Wire<T> extends Consumer<T> {

    /**
     * Get a new wire builder.
     *
     * @param executor the executor that the wire will use to run tasks
     * @param consumer tasks are passed to this consumer
     * @param <T>      the type of object that is passed through the wire
     * @return a new wire builder
     */
    static <T> WireBuilder<T> builder(
            @NonNull final Executor executor,
            @NonNull final Consumer<T> consumer) {
        return new WireBuilder<>(executor, consumer);
    }

    /**
     * Add a task to the wire.
     *
     * @param t the task
     */
    @Override
    void accept(@NonNull T t);
}
