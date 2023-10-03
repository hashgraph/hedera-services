package com.swirlds.common.wiring.internal;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A unit of work that is handled by a wire.
 */
final class Task implements Runnable {

    // True if this task has already been scheduled
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private final AtomicBoolean done = new AtomicBoolean(false);
    // If set, then this task will notify it when it is complete so this one can be scheduled
    private final AtomicReference<Task> dependent = new AtomicReference<>(null);
    // The thing to run when the task is ready
    private final Runnable runnable;
    private final Executor executor;

    /**
     * Constructor.
     *
     * @param executor   where the task will be executed
     * @param dependency a task that must be completed before this task is started
     * @param runnable   the operation to perform
     */
    public Task(
            @NonNull final Executor executor,
            @Nullable final Task dependency,
            @NonNull final Runnable runnable) {

        this.executor = Objects.requireNonNull(executor);
        this.runnable = Objects.requireNonNull(runnable);
        if (dependency != null) {
            dependency.setDependent(this);
        } else {
            markReady();
        }
    }

    /**
     * Set the dependant task.
     *
     * @param dependent a dependant task
     */
    private void setDependent(@NonNull final Task dependent) {
        this.dependent.set(dependent);
        if (this.done.get()) {
            dependent.markReady();
        }
    }

    /**
     * Called by a task I depend on, when it is ready to go.
     */
    private void markReady() {
        // Just fire it off once.
        if (scheduled.compareAndSet(false, true)) {
            executor.execute(this);
        }
    }

    /**
     * Execute the task.
     */
    @Override
    public void run() {
        // Delegate
        this.runnable.run();
        this.done.set(true);
        // Notify any tasks that depend on this one that they can now run
        final Task dep = dependent.get();
        if (dep != null) {
            dep.markReady();
        }
    }
}
