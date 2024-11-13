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

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.common.metrics.extensions.FractionalTimer;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.model.TraceableWiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

/**
 * A scheduler that performs work sequentially on a dedicated thread. This class has very similar semantics to
 * {@link DirectTaskScheduler}, except that work is done on a thread instead of on a fork join pool.
 *
 * @param <OUT> the type of the primary output wire
 */
public class SequentialThreadTaskScheduler<OUT> extends TaskScheduler<OUT> implements Startable, Stoppable {

    private final UncaughtExceptionHandler uncaughtExceptionHandler;
    private final ObjectCounter onRamp;
    private final ObjectCounter offRamp;
    private final ToLongFunction<Object> dataCounter;
    private final FractionalTimer busyTimer;
    private final Duration sleepDuration;
    private final long capacity;

    private final BlockingQueue<SequentialThreadTask> tasks = new LinkedBlockingQueue<>();

    private static final int BUFFER_SIZE = 1024;

    private final AtomicBoolean alive = new AtomicBoolean(true);

    private final Thread thread;

    /**
     * Constructor.
     *
     * @param model                    the wiring model containing this task scheduler
     * @param name                     the name of the task scheduler
     * @param uncaughtExceptionHandler the handler to call when an exception is thrown by a task
     * @param onRamp                   the counter to increment when a task is added to the queue
     * @param offRamp                  the counter to decrement when a task is removed from the queue
     * @param dataCounter              the function to weight input data objects for health monitoring
     * @param busyTimer                the timer to activate when a task is being handled
     * @param sleepDuration            the duration to sleep when the queue is empty
     * @param capacity                 the maximum desired capacity for this task scheduler
     * @param flushEnabled             if true, then {@link #flush()} will be enabled, otherwise it will throw.
     * @param squelchingEnabled        if true, then squelching will be enabled, otherwise trying to squelch will throw
     * @param insertionIsBlocking      when data is inserted into this task scheduler, will it block until capacity is
     *                                 available?
     */
    public SequentialThreadTaskScheduler(
            @NonNull final TraceableWiringModel model,
            @NonNull final String name,
            @NonNull final UncaughtExceptionHandler uncaughtExceptionHandler,
            @NonNull final ObjectCounter onRamp,
            @NonNull final ObjectCounter offRamp,
            @NonNull final ToLongFunction<Object> dataCounter,
            @NonNull final FractionalTimer busyTimer,
            @NonNull final Duration sleepDuration,
            final long capacity,
            final boolean flushEnabled,
            final boolean squelchingEnabled,
            final boolean insertionIsBlocking) {
        super(model, name, TaskSchedulerType.SEQUENTIAL_THREAD, flushEnabled, squelchingEnabled, insertionIsBlocking);

        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler);
        this.onRamp = Objects.requireNonNull(onRamp);
        this.offRamp = Objects.requireNonNull(offRamp);
        this.dataCounter = dataCounter;
        this.busyTimer = Objects.requireNonNull(busyTimer);
        this.sleepDuration = Objects.requireNonNull(sleepDuration);
        this.capacity = capacity;

        thread = new Thread(this::run, "<scheduler " + name + ">");
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

    /**
     * {@inheritDoc}
     */
    @Override
    protected void put(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        onRamp.onRamp(dataCounter.applyAsLong(data));
        tasks.add(new SequentialThreadTask(handler, data));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean offer(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        final boolean accepted = onRamp.attemptOnRamp(dataCounter.applyAsLong(data));
        if (!accepted) {
            return false;
        }
        tasks.add(new SequentialThreadTask(handler, data));
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void inject(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        onRamp.forceOnRamp(dataCounter.applyAsLong(data));
        tasks.add(new SequentialThreadTask(handler, data));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        thread.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        alive.set(false);
    }

    /**
     * Take work off of the queue and handle it.
     */
    private void run() {
        final List<SequentialThreadTask> buffer = new ArrayList<>(BUFFER_SIZE);

        while (alive.get()) {
            tasks.drainTo(buffer, BUFFER_SIZE);
            if (buffer.isEmpty()) {
                if (sleepDuration.toNanos() <= 0) {
                    continue;
                }

                try {
                    final SequentialThreadTask task = tasks.poll(sleepDuration.toNanos(), NANOSECONDS);
                    if (task == null) {
                        continue;
                    }
                    buffer.add(task);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            busyTimer.activate();
            for (final SequentialThreadTask task : buffer) {
                try {
                    task.handle();
                } catch (final Throwable t) {
                    uncaughtExceptionHandler.uncaughtException(thread, t);
                } finally {
                    offRamp.offRamp(dataCounter.applyAsLong(task.data()));
                }
            }
            busyTimer.deactivate();

            buffer.clear();
        }
    }
}
