package com.swirlds.common.wiring.internal;

import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A unit of work that is handled by a wire.
 */
public abstract class AbstractTask extends ForkJoinTask<Void> {

    /**
     *  Counts outstanding dependencies. When it reaches 0, the task is ready to run.
     */
    private final AtomicInteger dependencyCount;

    /**
     * Constructors.
     */
    protected AbstractTask() {
        this(0);
    }

    protected AbstractTask(int count) {
        this.dependencyCount = count > 0 ? new AtomicInteger(count) : null;
    }

    @Override
    public final Void getRawResult() {
        return null;
    }

    @Override
    protected final void setRawResult(Void value) {
    }

    /**
     * Send data
     */
    protected void send() {
        if (dependencyCount == null || dependencyCount.decrementAndGet() == 0) {
            fork();
        }
    }
}
