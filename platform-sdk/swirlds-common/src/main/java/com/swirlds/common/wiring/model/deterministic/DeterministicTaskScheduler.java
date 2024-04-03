/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring.model.deterministic;

import com.swirlds.common.wiring.model.internal.TraceableWiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A deterministic implementation of a task scheduler.
 *
 * @param <OUT> the output type of the scheduler (use {@link Void} for a task scheduler with no output type)
 */
public class DeterministicTaskScheduler<OUT> extends TaskScheduler<OUT> {

    private final Consumer<Runnable> submitWork;

    // TODO off/on ramps

    /**
     * Constructor.
     *
     * @param model               the wiring model containing this task scheduler
     * @param name                the name of the task scheduler
     * @param type                the type of task scheduler
     * @param flushEnabled        if true, then {@link #flush()} will be enabled, otherwise it will throw.
     * @param squelchingEnabled   if true, then squelching will be enabled, otherwise trying to squelch will throw.
     * @param insertionIsBlocking when data is inserted into this task scheduler, will it block until capacity is
     *                            available?
     * @param submitWork          a method where all work should be submitted
     */
    protected DeterministicTaskScheduler(
            @NonNull final TraceableWiringModel model,
            @NonNull final String name,
            @NonNull final TaskSchedulerType type,
            final boolean flushEnabled,
            final boolean squelchingEnabled,
            final boolean insertionIsBlocking,
            @NonNull final Consumer<Runnable> submitWork) {
        super(model, name, type, flushEnabled, squelchingEnabled, insertionIsBlocking);

        this.submitWork = Objects.requireNonNull(submitWork);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getUnprocessedTaskCount() {
        return 0; // TODO
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() {
        // TODO is this meaningful on a single thread?
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void put(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        submitWork.accept(() -> handler.accept(data));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean offer(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        submitWork.accept(() -> handler.accept(data));
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void inject(@NonNull final Consumer<Object> handler, @NonNull final Object data) {
        submitWork.accept(() -> handler.accept(data));
    }
}
