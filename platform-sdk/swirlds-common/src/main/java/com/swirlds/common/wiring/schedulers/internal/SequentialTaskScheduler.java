/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring.schedulers.internal;

import com.swirlds.common.metrics.extensions.FractionalTimer;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.model.TraceableWiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A {@link TaskScheduler} that guarantees that tasks are executed sequentially in the order they are received.
 *
 * @param <OUT> the output type of the scheduler (use {@link Void} for a task scheduler with no output type)
 */
public class SequentialTaskScheduler<OUT> extends TaskScheduler<OUT> {
    /**
     * The next task to be scheduled will be inserted into this placeholder task. When that happens, a new task will be
     * created and inserted into this placeholder.
     */
    private final AtomicReference<SequentialTask> nextTaskPlaceholder;

    private final ObjectCounter onRamp;
    private final ObjectCounter offRamp;
    private final FractionalTimer busyTimer;
    private final UncaughtExceptionHandler uncaughtExceptionHandler;
    private final ForkJoinPool pool;
    private final long capacity;

    /**
     * Constructor.
     *
     * @param model                    the wiring model containing this scheduler
     * @param name                     the name of the task scheduler
     * @param pool                     the fork join pool that will execute tasks on this scheduler
     * @param uncaughtExceptionHandler the uncaught exception handler
     * @param onRamp                   an object counter that is incremented when data is added to the task scheduler
     * @param offRamp                  an object counter that is decremented when data is removed from the task
     *                                 scheduler
     * @param busyTimer                a timer that tracks the amount of time the scheduler is busy
     * @param capacity                 the maximum desired capacity for this task scheduler
     * @param flushEnabled             if true, then {@link #flush()} will be enabled, otherwise it will throw.
     * @param squelchingEnabled        if true, then squelching will be enabled, otherwise trying to squelch will throw
     * @param insertionIsBlocking      when data is inserted into this task scheduler, will it block until capacity is
     *                                 available?
     */
    public SequentialTaskScheduler(
            @NonNull final TraceableWiringModel model,
            @NonNull final String name,
            @NonNull ForkJoinPool pool,
            @NonNull final UncaughtExceptionHandler uncaughtExceptionHandler,
            @NonNull final ObjectCounter onRamp,
            @NonNull final ObjectCounter offRamp,
            @NonNull final FractionalTimer busyTimer,
            final long capacity,
            final boolean flushEnabled,
            final boolean squelchingEnabled,
            final boolean insertionIsBlocking) {

        super(model, name, TaskSchedulerType.SEQUENTIAL, flushEnabled, squelchingEnabled, insertionIsBlocking);

        this.pool = Objects.requireNonNull(pool);
        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler);
        this.onRamp = Objects.requireNonNull(onRamp);
        this.offRamp = Objects.requireNonNull(offRamp);
        this.busyTimer = Objects.requireNonNull(busyTimer);
        this.capacity = capacity;

        this.nextTaskPlaceholder =
                new AtomicReference<>(new SequentialTask(pool, offRamp, busyTimer, uncaughtExceptionHandler, true));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void put(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        onRamp.onRamp();
        scheduleTask(handler, data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean offer(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
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
    protected void inject(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        onRamp.forceOnRamp();
        scheduleTask(handler, data);
    }

    /**
     * Schedule a task to be handled. This should only be called after successfully on-ramping (one way or another).
     *
     * @param handler the method that will be called when this task is executed
     * @param data    the data to be passed to the consumer for this task
     */
    private void scheduleTask(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        // This method may be called by many threads, but actual execution is required to happen serially. This method
        // organizes tasks into a linked list. Tasks in this linked list are executed one at a time in order.
        // When execution of one task is completed, execution of the next task is scheduled on the pool.

        final SequentialTask nextTask = new SequentialTask(pool, offRamp, busyTimer, uncaughtExceptionHandler, false);
        SequentialTask currentTask;
        do {
            currentTask = nextTaskPlaceholder.get();
        } while (!nextTaskPlaceholder.compareAndSet(currentTask, nextTask));
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
    public long getCapacity() {
        return capacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() {
        throwIfFlushDisabled();
        onRamp.waitUntilEmpty();
    }
}
