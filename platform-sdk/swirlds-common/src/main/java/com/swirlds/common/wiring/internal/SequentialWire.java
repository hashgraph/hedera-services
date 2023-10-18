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

package com.swirlds.common.wiring.internal;

import com.swirlds.common.metrics.extensions.FractionalTimer;
import com.swirlds.common.wiring.Wire;
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
 */
public class SequentialWire extends Wire {
    private final AtomicReference<SequentialTask> lastTask;

    private final ObjectCounter onRamp;
    private final ObjectCounter offRamp;
    private final boolean flushEnabled;
    private final FractionalTimer busyTimer;
    private final String name;
    private final UncaughtExceptionHandler uncaughtExceptionHandler;
    private final ForkJoinPool pool;

    /**
     * Constructor.
     *
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
            @NonNull final String name,
            @NonNull ForkJoinPool pool,
            @NonNull final UncaughtExceptionHandler uncaughtExceptionHandler,
            @NonNull final ObjectCounter onRamp,
            @NonNull final ObjectCounter offRamp,
            @NonNull final FractionalTimer busyTimer,
            final boolean flushEnabled) {

        this.pool = Objects.requireNonNull(pool);
        this.name = Objects.requireNonNull(name);
        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler);
        this.onRamp = Objects.requireNonNull(onRamp);
        this.offRamp = Objects.requireNonNull(offRamp);
        this.busyTimer = Objects.requireNonNull(busyTimer);
        this.flushEnabled = flushEnabled;

        this.lastTask = new AtomicReference<>(new SequentialTask(1));
    }

    /**
     * A task in a sequential wire.
     */
    private class SequentialTask extends AbstractTask {
        private Consumer<Object> handler;
        private Object data;
        private SequentialTask nextTask;

        /**
         * Constructor.
         *
         * @param dependencyCount the number of dependencies that must be satisfied before this task is eligible for
         *                        execution. The first task in a sequence has a dependency count of 1 (data must be
         *                        provided), and subsequent tasks have a dependency count of 2 (the previous task must
         *                        be executed and data must be provided).
         */
        SequentialTask(final int dependencyCount) {
            super(pool, dependencyCount);
        }

        /**
         * Provide a reference to the next task and the data that will be processed during the handling of this task.
         *
         * @param nextTask the task that will execute after this task
         * @param handler  the method that will be called when this task is executed
         * @param data     the data to be passed to the consumer for this task
         */
        void send(
                @NonNull final SequentialTask nextTask,
                @NonNull final Consumer<Object> handler,
                @Nullable final Object data) {
            this.nextTask = nextTask;
            this.handler = handler;
            this.data = data;

            // This method provides us with the data we intend to send to the consumer
            // when this task is executed, thus resolving one of the two dependencies
            // required for the task to be executed. This call will decrement the
            // dependency count. If this causes the dependency count to reach 0
            // (i.e. if the previous task has already been executed), then this call
            // will cause this task to be immediately eligible for execution.
            send();
        }

        /**
         * Execute this task.
         */
        @Override
        public boolean exec() {
            busyTimer.activate();
            try {
                handler.accept(data);
            } catch (final Throwable t) {
                uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), t);
            } finally {
                offRamp.offRamp();
                busyTimer.deactivate();

                // Reduce the dependency count of the next task. If the next task already has its data, then this
                // method will cause the next task to be immediately eligible for execution.
                nextTask.send();
            }
            return true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void put(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        onRamp.onRamp();

        // This wire may be called by may threads, but it must serialize the results a sequence of tasks that are
        // guaranteed to be executed one at a time on the target processor. We do this by forming a dependency graph
        // from task to task, such that each task depends on the previous task.

        final SequentialTask nextTask = new SequentialTask(2);
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
    protected void interruptablePut(@NonNull final Consumer<Object> handler, @NonNull final Object data)
            throws InterruptedException {
        onRamp.interruptableOnRamp();

        // This wire may be called by may threads, but it must serialize the results a sequence of tasks that are
        // guaranteed to be executed one at a time on the target processor. We do this by forming a dependency graph
        // from task to task, such that each task depends on the previous task.

        final SequentialTask nextTask = new SequentialTask(2);
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
    protected boolean offer(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        final boolean accepted = onRamp.attemptOnRamp();
        if (!accepted) {
            return false;
        }

        // This wire may be called by may threads, but it must serialize the results a sequence of tasks that are
        // guaranteed to be executed one at a time on the target processor. We do this by forming a dependency graph
        // from task to task, such that each task depends on the previous task.

        final SequentialTask nextTask = new SequentialTask(2);
        SequentialTask currentTask;
        do {
            currentTask = lastTask.get();
        } while (!lastTask.compareAndSet(currentTask, nextTask));
        currentTask.send(nextTask, handler, data);

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void inject(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        onRamp.forceOnRamp();

        // This wire may be called by may threads, but it must serialize the results a sequence of tasks that are
        // guaranteed to be executed one at a time on the target processor. We do this by forming a dependency graph
        // from task to task, such that each task depends on the previous task.

        final SequentialTask nextTask = new SequentialTask(2);
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
        if (!flushEnabled) {
            // We intentionally throw this to make wire implementations more predictable. Even though there is no
            // overhead if we enable flushing on a sequential wire but do not use it, some wire implementations
            // may have overhead if flushing is enabled.
            throw new UnsupportedOperationException("Flushing is not enabled for this wire");
        }

        onRamp.forceOnRamp();

        final Semaphore semaphore = new Semaphore(1);
        semaphore.acquireUninterruptibly();

        final SequentialTask nextTask = new SequentialTask(2);
        SequentialTask currentTask;
        do {
            currentTask = lastTask.get();
        } while (!lastTask.compareAndSet(currentTask, nextTask));
        currentTask.send(nextTask, x -> semaphore.release(), null);

        semaphore.acquireUninterruptibly();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void interruptableFlush() throws InterruptedException {
        if (!flushEnabled) {
            // We intentionally throw this to make wire implementations more predictable. Even though there is no
            // overhead if we enable flushing on a sequential wire but do not use it, some wire implementations
            // may have overhead if flushing is enabled.
            throw new UnsupportedOperationException("Flushing is not enabled for this wire");
        }

        onRamp.forceOnRamp();

        final Semaphore semaphore = new Semaphore(1);
        semaphore.acquire();

        final SequentialTask nextTask = new SequentialTask(2);
        SequentialTask currentTask;
        do {
            currentTask = lastTask.get();
        } while (!lastTask.compareAndSet(currentTask, nextTask));
        currentTask.send(nextTask, x -> semaphore.release(), null);

        semaphore.acquire();
    }
}
