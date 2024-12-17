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

import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerBuilder.UNLIMITED_CAPACITY;

import com.swirlds.common.metrics.extensions.FractionalTimer;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.model.TraceableWiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A scheduler that performs work immediately on the caller's thread.
 *
 * @param <OUT> the output type of the scheduler (use {@link Void} for a task scheduler with no output type)
 */
public class DirectTaskScheduler<OUT> extends TaskScheduler<OUT> {

    private final UncaughtExceptionHandler uncaughtExceptionHandler;
    private final ObjectCounter onRamp;
    private final ObjectCounter offRamp;
    private final FractionalTimer busyTimer;

    /**
     * Constructor.
     *
     * @param model                    the wiring model containing this task scheduler
     * @param name                     the name of the task scheduler
     * @param uncaughtExceptionHandler the uncaught exception handler
     * @param onRamp                   an object counter that is incremented when data is added to the task scheduler
     * @param offRamp                  an object counter that is decremented when data is removed from the task
     * @param squelchingEnabled        if true, then squelching will be enabled, otherwise trying to squelch will throw
     * @param busyTimer                a timer that tracks the amount of time the task scheduler is busy
     * @param threadsafe               true if the work scheduled by this object is threadsafe
     */
    public DirectTaskScheduler(
            @NonNull final TraceableWiringModel model,
            @NonNull final String name,
            @NonNull final UncaughtExceptionHandler uncaughtExceptionHandler,
            @NonNull final ObjectCounter onRamp,
            @NonNull final ObjectCounter offRamp,
            final boolean squelchingEnabled,
            @NonNull final FractionalTimer busyTimer,
            final boolean threadsafe) {
        super(
                model,
                name,
                threadsafe ? TaskSchedulerType.DIRECT_THREADSAFE : TaskSchedulerType.DIRECT,
                false,
                squelchingEnabled,
                true);

        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler);
        this.onRamp = Objects.requireNonNull(onRamp);
        this.offRamp = Objects.requireNonNull(offRamp);
        this.busyTimer = Objects.requireNonNull(busyTimer);
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
        // Direct schedulers have no concept of capacity.
        return UNLIMITED_CAPACITY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() {
        throw new UnsupportedOperationException("Direct task schedulers do not support flushing");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void put(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        onRamp.onRamp();
        handleAndOffRamp(handler, data);
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
        handleAndOffRamp(handler, data);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void inject(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        onRamp.forceOnRamp();
        handleAndOffRamp(handler, data);
    }

    /**
     * Helper method. Handles the data and then off ramps.
     *
     * @param handler the handler
     * @param data    the data
     */
    private void handleAndOffRamp(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        busyTimer.activate();
        try {
            handler.accept(data);
        } catch (final Throwable t) {
            uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), t);
        }
        busyTimer.deactivate();
        offRamp.offRamp();
    }
}
