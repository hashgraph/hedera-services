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
    private final Consumer<T> consumer;

    public ConcurrentWire(@NonNull final Consumer<T> consumer) {
        this.consumer = Objects.requireNonNull(consumer);
    }

    @Override
    public void accept(@NonNull final T t) {
        new AbstractTask() {
            @Override
            protected boolean exec() {
                consumer.accept(t);
                return true;
            }
        }.send();
    }
}
