package com.swirlds.common.wiring.internal;

import com.swirlds.common.wiring.Wire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

/**
 * A {@link Wire} that permits parallel execution of tasks.
 *
 * @param <T> the type of object that is passed through the wire
 */
public class ConcurrentWire<T> implements Wire<T> {
    private final Executor executor;
    private final Consumer<T> consumer;

    public ConcurrentWire(@NonNull final Executor executor, @NonNull final Consumer<T> consumer) {
        this.executor = Objects.requireNonNull(executor);
        this.consumer = Objects.requireNonNull(consumer);
    }

    @Override
    public void accept(@NonNull final T t) {
        executor.execute(() -> consumer.accept(t));
    }
}
