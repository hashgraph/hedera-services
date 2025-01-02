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

package com.swirlds.common.wiring.model.internal.deterministic;

import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.DIRECT;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.DIRECT_THREADSAFE;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.NO_OP;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.extensions.FractionalTimer;
import com.swirlds.common.metrics.extensions.NoOpFractionalTimer;
import com.swirlds.common.wiring.model.DeterministicWiringModel;
import com.swirlds.common.wiring.model.TraceableWiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.schedulers.builders.internal.AbstractTaskSchedulerBuilder;
import com.swirlds.common.wiring.schedulers.internal.DirectTaskScheduler;
import com.swirlds.common.wiring.schedulers.internal.NoOpTaskScheduler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

/**
 * Builds schedulers for a {@link DeterministicWiringModel}.
 *
 * @param <OUT>
 */
public class DeterministicTaskSchedulerBuilder<OUT> extends AbstractTaskSchedulerBuilder<OUT> {

    private final Consumer<Runnable> submitWork;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param model           the wiring model
     * @param name            the name of the task scheduler. Used for metrics and debugging. Must be unique. Must only
     *                        contain alphanumeric characters and underscores.
     * @param submitWork      a method where all work should be submitted
     */
    public DeterministicTaskSchedulerBuilder(
            @NonNull final PlatformContext platformContext,
            @NonNull final TraceableWiringModel model,
            @NonNull final String name,
            @NonNull final Consumer<Runnable> submitWork) {
        super(platformContext, model, name, ForkJoinPool.commonPool());
        this.submitWork = Objects.requireNonNull(submitWork);
    }

    /**
     * Ensures that direct schedulers do not have an unhandled task capacity set.
     *
     * <p>If the scheduler type is {@link TaskSchedulerType#DIRECT} or {@link TaskSchedulerType#DIRECT_THREADSAFE}
     * and the unhandled task capacity is not 1, an {@link IllegalArgumentException} is thrown.
     *
     * @throws IllegalArgumentException if the type is direct or direct threadsafe and the unhandled task capacity is
     * not 1
     */
    private void validateConfiguration() {
        if ((type == DIRECT || type == DIRECT_THREADSAFE) && unhandledTaskCapacity != 1) {
            throw new IllegalArgumentException("Direct schedulers cannot have an unhandled task capacity.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public TaskScheduler<OUT> build() {
        // Check to ensure unhandled task capacity is not set for direct schedulers
        validateConfiguration();
        final boolean insertionIsBlocking = unhandledTaskCapacity != UNLIMITED_CAPACITY || externalBackPressure;

        final Counters counters = buildCounters();
        final FractionalTimer busyFractionTimer = NoOpFractionalTimer.getInstance();

        final TaskScheduler<OUT> scheduler =
                switch (type) {
                    case CONCURRENT, SEQUENTIAL, SEQUENTIAL_THREAD -> new DeterministicTaskScheduler<>(
                            model,
                            name,
                            type,
                            counters.onRamp(),
                            counters.offRamp(),
                            unhandledTaskCapacity,
                            flushingEnabled,
                            squelchingEnabled,
                            insertionIsBlocking,
                            submitWork);
                    case DIRECT, DIRECT_THREADSAFE -> new DirectTaskScheduler<>(
                            model,
                            name,
                            buildUncaughtExceptionHandler(),
                            counters.onRamp(),
                            counters.offRamp(),
                            squelchingEnabled,
                            busyFractionTimer,
                            type == DIRECT_THREADSAFE);
                    case NO_OP -> new NoOpTaskScheduler<>(model, name, type, flushingEnabled, squelchingEnabled);
                };

        if (type != NO_OP) {
            model.registerScheduler(scheduler, hyperlink);
        }

        return scheduler;
    }
}
