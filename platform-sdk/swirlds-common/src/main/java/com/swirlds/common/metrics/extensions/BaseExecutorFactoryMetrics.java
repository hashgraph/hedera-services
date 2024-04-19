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

package com.swirlds.common.metrics.extensions;

import static com.swirlds.base.units.UnitConstants.MICROSECOND_UNIT;

import com.swirlds.base.internal.BaseExecutorFactory;
import com.swirlds.base.internal.observe.BaseExecutorObserver;
import com.swirlds.base.internal.observe.BaseTaskDefinition;
import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.DoubleAccumulator;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class installs metrics for the {@link BaseExecutorFactory}.
 */
public class BaseExecutorFactoryMetrics {

    /**
     * Installs metrics for the {@link BaseExecutorFactory}.
     *
     * @param metrics the metrics system
     */
    public static void installForBaseExecutor(@NonNull Metrics metrics) {
        Objects.requireNonNull(metrics, "metrics must not be null");

        final Counter.Config taskCountAccumulatorConfig = new Counter.Config(
                        "base_executor", "base_executor_task_count")
                .withUnit("tasks")
                .withDescription("The number of tasks submitted to the base executor");
        final Counter taskCountAccumulator = metrics.getOrCreate(taskCountAccumulatorConfig);

        final Counter.Config taskExecutionCountAccumulatorConfig = new Counter.Config(
                        "base_executor", "base_executor_task_execution_count")
                .withUnit("tasks")
                .withDescription("The number of tasks executed by the base executor");
        final Counter taskExecutionCountAccumulator = metrics.getOrCreate(taskExecutionCountAccumulatorConfig);

        final Counter.Config tasksDoneCountAccumulatorConfig = new Counter.Config(
                        "base_executor", "base_executor_tasks_done_count")
                .withUnit("tasks")
                .withDescription("The number of tasks executed successfully by the base executor");
        final Counter tasksDoneCountAccumulator = metrics.getOrCreate(tasksDoneCountAccumulatorConfig);

        final Counter.Config tasksFailedCountAccumulatorConfig = new Counter.Config(
                        "base_executor", "base_executor_tasks_failed_count")
                .withUnit("tasks")
                .withDescription("The number of tasks failed to execute by the base executor");
        final Counter tasksFailedCountAccumulator = metrics.getOrCreate(tasksFailedCountAccumulatorConfig);

        final TaskExecutionTimeMetric taskExecutionTimeMetric =
                new TaskExecutionTimeMetric("base_executor_task_execution_time", metrics);

        final TaskExecutionTimeMetric taskDoneExecutionTimeMetric =
                new TaskExecutionTimeMetric("base_executor_task_done_execution_time", metrics);

        final TaskExecutionTimeMetric taskFailExecutionTimeMetric =
                new TaskExecutionTimeMetric("base_executor_task_Fail_execution_time", metrics);

        final BaseExecutorObserver observer = new BaseExecutorObserver() {

            @Override
            public void onTaskSubmitted(@NonNull BaseTaskDefinition taskDefinition) {
                taskCountAccumulator.increment();
            }

            @Override
            public void onTaskStarted(@NonNull BaseTaskDefinition taskDefinition) {
                taskExecutionCountAccumulator.increment();
            }

            @Override
            public void onTaskDone(@NonNull BaseTaskDefinition taskDefinition, @NonNull Duration duration) {
                tasksDoneCountAccumulator.increment();
                taskExecutionTimeMetric.update(duration);
                taskDoneExecutionTimeMetric.update(duration);
            }

            @Override
            public void onTaskFailed(@NonNull BaseTaskDefinition taskDefinition, @NonNull Duration duration) {
                tasksFailedCountAccumulator.increment();
                taskExecutionTimeMetric.update(duration);
                taskFailExecutionTimeMetric.update(duration);
            }
        };
        BaseExecutorFactory.addObserver(observer);
    }

    /**
     * A metric that tracks the execution time of tasks.
     */
    private static class TaskExecutionTimeMetric {

        private final Counter countMetric;

        private final LongAccumulator maxMillisMetric;

        private final LongAccumulator minMillisMetric;

        private final DoubleAccumulator averageMillisMetric;

        public TaskExecutionTimeMetric(final String name, @NonNull Metrics metrics) {
            final Counter.Config countConfig = new Counter.Config("base_executor", name + "_count")
                    .withDescription("The number of update calls to the time metric")
                    .withUnit("calls");
            countMetric = metrics.getOrCreate(countConfig);

            final LongAccumulator.Config maxMillisConfig = new LongAccumulator.Config(
                            "base_executor", name + "_max_millis")
                    .withDescription("The maximum time in milliseconds that a task took to execute")
                    .withUnit(MICROSECOND_UNIT)
                    .withAccumulator((a, b) -> Math.max(a, b))
                    .withInitialValue(0L);
            maxMillisMetric = metrics.getOrCreate(maxMillisConfig);

            final LongAccumulator.Config minMillisConfig = new LongAccumulator.Config(
                            "base_executor", name + "_min_millis")
                    .withDescription("The minimum time in milliseconds that a task took to execute")
                    .withUnit(MICROSECOND_UNIT)
                    .withAccumulator((a, b) -> Math.min(a, b))
                    .withInitialValue(0L);
            minMillisMetric = metrics.getOrCreate(minMillisConfig);

            final AtomicLong count = new AtomicLong(0);
            final DoubleAccumulator.Config averageMillisConfig = new DoubleAccumulator.Config(
                            "base_executor", name + "_avg_millis")
                    .withDescription("The average time in milliseconds that a task took to execute")
                    .withUnit(MICROSECOND_UNIT)
                    .withAccumulator((a, b) -> {
                        final long prefCount = count.getAndIncrement();
                        if (a == Double.NaN) {
                            return b;
                        }
                        if (b == Double.NaN) {
                            return a;
                        }
                        try {
                            return ((a * prefCount) + b) / (prefCount + 1);
                        } catch (Exception e) {
                            return Double.NaN;
                        }
                    })
                    .withInitialValue(Double.NaN);
            averageMillisMetric = metrics.getOrCreate(averageMillisConfig);
        }

        public void update(@NonNull Duration duration) {
            Objects.requireNonNull(duration, "duration must not be null");
            countMetric.increment();

            final long millis = duration.toMillis();
            maxMillisMetric.update(millis);
            minMillisMetric.update(millis);
            averageMillisMetric.update(millis);
        }
    }
}
