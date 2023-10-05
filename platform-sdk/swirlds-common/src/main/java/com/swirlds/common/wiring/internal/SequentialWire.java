package com.swirlds.common.wiring.internal;

import com.swirlds.common.wiring.Wire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A {@link Wire} that guarantees that tasks are executed sequentially in the order they are received.
 *
 * @param <T> the type of object that is passed through the wire
 */
public class SequentialWire<T> implements Wire<T> {
    private final Consumer<T> consumer;
    private final AtomicReference<Task> lastTask = new AtomicReference<>(new Task(true));

    public SequentialWire(
            @NonNull final Executor executor,
            @NonNull final Consumer<T> consumer) {
        this.consumer = consumer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(@NonNull final T t) {

        // This wire may be called by may threads, but it must serialize the results a sequence of tasks that are
        // guaranteed to be executed one at a time on the target processor. We do this by forming a dependency graph
        // from task to task, such that each task depends on the previous task.

        final Task nextTask = new Task();
        Task curTask;
        do {
            curTask = lastTask.get();
        } while (!lastTask.compareAndSet(curTask, nextTask));
        curTask.send(nextTask, () -> consumer.accept(t));
    }
}
