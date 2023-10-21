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

package com.swirlds.common.wiring.wires;

import com.swirlds.common.metrics.extensions.FractionalTimer;
import com.swirlds.common.wiring.Wire;
import com.swirlds.common.wiring.WiringModel;
import com.swirlds.common.wiring.counters.ObjectCounter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A {@link Wire} that guarantees that tasks are executed sequentially in the order they are received.
 *
 * @param <O> the output time of the wire (use {@link Void}) for a wire with no output type)
 */
public class SequentialWire<O> extends Wire<O> {

    private final AtomicReference<SequentialTask> lastTask;
    private final ObjectCounter onRamp;
    private final ObjectCounter offRamp;
    private final FractionalTimer busyTimer;
    private final UncaughtExceptionHandler uncaughtExceptionHandler;
    private final ForkJoinPool pool;

    /**
     * Constructor.
     *
     * @param model                    the wiring model containing this wire
     * @param name                     the name of the wire
     * @param pool                     the fork join pool that will execute tasks on this wire
     * @param uncaughtExceptionHandler the uncaught exception handler
     * @param onRamp                   an object counter that is incremented when data is added to the wire, ignored if
     *                                 null
     * @param offRamp                  an object counter that is decremented when data is removed from the wire, ignored
     *                                 if null
     * @param busyTimer                a timer that tracks the amount of time the wire is busy, ignored if null
     * @param flushEnabled             if true, then {@link #flush()} and {@link #interruptableFlush()} will be enabled,
     *                                 otherwise they will throw.
     */
    public SequentialWire(
            @NonNull final WiringModel model,
            @NonNull final String name,
            @NonNull ForkJoinPool pool,
            @NonNull final UncaughtExceptionHandler uncaughtExceptionHandler,
            @NonNull final ObjectCounter onRamp,
            @NonNull final ObjectCounter offRamp,
            @NonNull final FractionalTimer busyTimer,
            final boolean flushEnabled) {

        super(model, name, flushEnabled);

        this.pool = Objects.requireNonNull(pool);
        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler);
        this.onRamp = Objects.requireNonNull(onRamp);
        this.offRamp = Objects.requireNonNull(offRamp);
        this.busyTimer = Objects.requireNonNull(busyTimer);

        this.lastTask =
                new AtomicReference<>(new SequentialTask(pool, offRamp, busyTimer, uncaughtExceptionHandler, 1));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void put(@NonNull final Consumer<Object> handler, @Nullable final Object data) {
        onRamp.onRamp();
        scheduleTask(handler, data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void interruptablePut(@NonNull final Consumer<Object> handler, @Nullable final Object data)
            throws InterruptedException {
        onRamp.interruptableOnRamp();
        scheduleTask(handler, data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean offer(@NonNull final Consumer<Object> handler, @Nullable final Object data) {
        final boolean accepted = onRamp.attemptOnRamp();
        if (accepted) {
            scheduleTask(handler, data);
        }
        return accepted;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void inject(@NonNull final Consumer<Object> handler, @Nullable final Object data) {
        onRamp.forceOnRamp();
        scheduleTask(handler, data);
    }

    /**
     * Schedule a task to be handled. This should only be called after successfully on-ramping (one way or another).
     *
     * @param handler the method that will be called when this task is executed
     * @param data    the data to be passed to the consumer for this task
     */
    private void scheduleTask(@NonNull final Consumer<Object> handler, @Nullable final Object data) {
        // This wire may be called by may threads, but it must serialize the results a sequence of tasks that are
        // guaranteed to be executed one at a time on the target processor. We do this by forming a dependency graph
        // from task to task, such that each task depends on the previous task.

        final SequentialTask nextTask = new SequentialTask(pool, offRamp, busyTimer, uncaughtExceptionHandler, 2);
        SequentialTask currentTask;
        do {
            currentTask = lastTask.get();
        } while (!lastTask.compareAndSet(currentTask, nextTask));
        currentTask.send(nextTask, handler, data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getUnprocessedTaskCount() {
        return onRamp.getCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() {
        throwIfFlushDisabled();
        flushWithSemaphore().acquireUninterruptibly();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void interruptableFlush() throws InterruptedException {
        throwIfFlushDisabled();
        flushWithSemaphore().acquire();
    }

    /**
     * Start a flush operation with a semaphore. The flush is completed when the semaphore can be acquired.
     *
     * @return the semaphore that will be released when the flush is complete
     */
    @NonNull
    private Semaphore flushWithSemaphore() {
        onRamp.forceOnRamp();
        final Semaphore semaphore = new Semaphore(1);
        semaphore.acquireUninterruptibly();

        final SequentialTask nextTask = new SequentialTask(pool, offRamp, busyTimer, uncaughtExceptionHandler, 2);
        SequentialTask currentTask;
        do {
            currentTask = lastTask.get();
        } while (!lastTask.compareAndSet(currentTask, nextTask));
        currentTask.send(nextTask, x -> semaphore.release(), null);

        return semaphore;
    }
}
