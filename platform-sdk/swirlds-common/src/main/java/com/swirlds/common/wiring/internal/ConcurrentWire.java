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

import com.swirlds.common.wiring.Wire;
import com.swirlds.common.wiring.counters.ObjectCounter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

/**
 * A {@link Wire} that permits parallel execution of tasks. Similar to {@link ConcurrentWire} but with extra metering.
 */
public class ConcurrentWire extends Wire {

    private final ObjectCounter onRamp;
    private final ObjectCounter offRamp;
    private final String name;
    private final UncaughtExceptionHandler uncaughtExceptionHandler;
    private final ForkJoinPool pool;
    private final boolean flushEnabled;

    /**
     * Constructor.
     *
     * @param name                     the name of the wire
     * @param pool                     the fork join pool that will execute tasks on this wire
     * @param uncaughtExceptionHandler the handler for uncaught exceptions
     * @param onRamp                   an object counter that is incremented when data is added to the wire, ignored if
     *                                 null
     * @param offRamp                  an object counter that is decremented when data is removed from the wire, ignored
     *                                 if null
     * @param flushEnabled             if true, then {@link #flush()} and {@link #interruptableFlush()} will be enabled,
     *                                 otherwise they will throw.
     */
    public ConcurrentWire(
            @NonNull final String name,
            @NonNull ForkJoinPool pool,
            @NonNull UncaughtExceptionHandler uncaughtExceptionHandler,
            @NonNull final ObjectCounter onRamp,
            @NonNull final ObjectCounter offRamp,
            final boolean flushEnabled) {
        this.name = Objects.requireNonNull(name);
        this.pool = Objects.requireNonNull(pool);
        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler);
        this.onRamp = Objects.requireNonNull(onRamp);
        this.offRamp = Objects.requireNonNull(offRamp);
        this.flushEnabled = flushEnabled;
    }

    private class ConcurrentTask extends AbstractTask {

        private final Consumer<Object> handler;
        private final Object data;

        /**
         * Constructor.
         *
         * @param handler the method that will be called when this task is executed
         * @param data    the data to be passed to the consumer for this task
         */
        protected ConcurrentTask(@NonNull final Consumer<Object> handler, @Nullable final Object data) {
            super(pool, 0);
            this.handler = handler;
            this.data = data;
        }

        @Override
        protected boolean exec() {
            offRamp.offRamp();
            try {
                handler.accept(data);
            } catch (final Throwable t) {
                uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), t);
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
        new ConcurrentTask(handler, data).send();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void interruptablePut(@NonNull final Consumer<Object> handler, @NonNull final Object data)
            throws InterruptedException {
        onRamp.interruptableOnRamp();
        new ConcurrentTask(handler, data).send();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean offer(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        boolean accepted = onRamp.attemptOnRamp();
        if (!accepted) {
            return false;
        }
        new ConcurrentTask(handler, data).send();
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void inject(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        onRamp.forceOnRamp();
        new ConcurrentTask(handler, data).send();
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
            throw new UnsupportedOperationException("Flushing is not enabled for this wire");
        }

        onRamp.waitUntilEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void interruptableFlush() throws InterruptedException {
        if (!flushEnabled) {
            throw new UnsupportedOperationException("Flushing is not enabled for this wire");
        }

        onRamp.interruptableWaitUntilEmpty();
    }
}
