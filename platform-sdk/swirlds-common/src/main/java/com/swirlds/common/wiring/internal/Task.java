package com.swirlds.common.wiring.internal;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A unit of work that is handled by a wire.
 */
final class Task extends ForkJoinTask<Void> {

    // Output task
    private Task out;
    // Input count
    private final AtomicInteger count;
    // The thing to run when the task is ready
    private Runnable runnable;

    /**
     * Constructors.
     */
    public Task() {
        this(false);
    }

    public Task(boolean initial) {
        count = new AtomicInteger(initial ? 1 : 2);
    }

    @Override
    public Void getRawResult() {
        return null;
    }

    @Override
    protected void setRawResult(Void value) {
    }

    /**
     * Send data
     */
    public void send(@NonNull Task out, @NonNull Runnable runnable) {
        this.out = Objects.requireNonNull(out);
        this.runnable = Objects.requireNonNull(runnable);
        send();
    }

    private void send() {
        if (count.decrementAndGet() == 0) {
            fork();
        }
    }

    /**
     * Execute the task.
     */
    @Override
    public boolean exec() {
        // Delegate
        runnable.run();
        out.send();
        return true;
    }
}
