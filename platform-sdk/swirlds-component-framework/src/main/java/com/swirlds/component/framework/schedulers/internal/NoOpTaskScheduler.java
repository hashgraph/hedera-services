/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.schedulers.internal;

import static com.swirlds.component.framework.schedulers.builders.TaskSchedulerBuilder.UNLIMITED_CAPACITY;

import com.swirlds.component.framework.model.TraceableWiringModel;
import com.swirlds.component.framework.schedulers.TaskScheduler;
import com.swirlds.component.framework.schedulers.builders.TaskSchedulerType;
import com.swirlds.component.framework.wires.input.BindableInputWire;
import com.swirlds.component.framework.wires.input.NoOpInputWire;
import com.swirlds.component.framework.wires.output.NoOpOutputWire;
import com.swirlds.component.framework.wires.output.StandardOutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A no-op task scheduler that does nothing.
 *
 * @param <OUT> the output type of the scheduler (use {@link Void} for a task scheduler with no output type). This is
 *              just to appease the compiler, as this scheduler never produces output.
 */
public class NoOpTaskScheduler<OUT> extends TaskScheduler<OUT> {

    private final TraceableWiringModel model;

    /**
     * Constructor.
     *
     * @param model             the wiring model containing this task scheduler
     * @param name              the name of the task scheduler
     * @param type              the type of task scheduler
     * @param flushEnabled      if true, then {@link #flush()} will be enabled, otherwise it will throw.
     * @param squelchingEnabled if true, then squelching will be enabled, otherwise trying to squelch will throw.
     */
    public NoOpTaskScheduler(
            @NonNull final TraceableWiringModel model,
            @NonNull final String name,
            @NonNull final TaskSchedulerType type,
            final boolean flushEnabled,
            final boolean squelchingEnabled) {
        super(model, name, type, flushEnabled, squelchingEnabled, false);

        this.model = Objects.requireNonNull(model);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getUnprocessedTaskCount() {
        return 0;
    }

    @Override
    public long getCapacity() {
        // No op schedulers have no concept of capacity
        return UNLIMITED_CAPACITY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() {
        throwIfFlushDisabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void put(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        throw new UnsupportedOperationException(
                "Data should have been discarded before being sent to this no-op scheduler");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean offer(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        throw new UnsupportedOperationException(
                "Data should have been discarded before being sent to this no-op scheduler");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void inject(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        throw new UnsupportedOperationException(
                "Data should have been discarded before being sent to this no-op scheduler");
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected StandardOutputWire<OUT> buildPrimaryOutputWire(
            @NonNull final TraceableWiringModel model, @NonNull final String name) {
        return new NoOpOutputWire<>(model, getName());
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <T> StandardOutputWire<T> buildSecondaryOutputWire() {
        return new NoOpOutputWire<>(model, getName());
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <I> BindableInputWire<I, OUT> buildInputWire(@NonNull final String name) {
        return new NoOpInputWire<>(model, this, name);
    }
}
