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

import com.swirlds.base.internal.BaseExecutorFactory;
import com.swirlds.base.internal.BaseExecutorObserver;
import com.swirlds.base.internal.BaseTaskDefinition;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;

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

        final LongAccumulator.Config taskCountAccumulatorConfig = new LongAccumulator.Config(
                        "base_executor", "base_executor_task_count")
                .withAccumulator((a, b) -> a + b)
                .withInitialValue(0L);
        final LongAccumulator taskCountAccumulator = metrics.getOrCreate(taskCountAccumulatorConfig);

        final LongAccumulator.Config taskExecutionCountAccumulatorConfig = new LongAccumulator.Config(
                        "base_executor", "base_executor_task_execution_count")
                .withAccumulator((a, b) -> a + b)
                .withInitialValue(0L);
        final LongAccumulator taskExecutionCountAccumulator = metrics.getOrCreate(taskExecutionCountAccumulatorConfig);

        final LongAccumulator.Config tasksDoneCountAccumulatorConfig = new LongAccumulator.Config(
                        "base_executor", "base_executor_tasks_done_count")
                .withAccumulator((a, b) -> a + b)
                .withInitialValue(0L);
        final LongAccumulator tasksDoneCountAccumulator = metrics.getOrCreate(tasksDoneCountAccumulatorConfig);

        final LongAccumulator.Config tasksFailedCountAccumulatorConfig = new LongAccumulator.Config(
                        "base_executor", "base_executor_tasks_failed_count")
                .withAccumulator((a, b) -> a + b)
                .withInitialValue(0L);
        final LongAccumulator tasksFailedCountAccumulator = metrics.getOrCreate(tasksFailedCountAccumulatorConfig);

        final BaseExecutorObserver observer = new BaseExecutorObserver() {

            @Override
            public void onTaskSubmitted(@NonNull BaseTaskDefinition taskDefinition) {
                taskCountAccumulator.update(1);
            }

            @Override
            public void onTaskStarted(@NonNull BaseTaskDefinition taskDefinition) {
                taskExecutionCountAccumulator.update(1);
            }

            @Override
            public void onTaskDone(@NonNull BaseTaskDefinition taskDefinition, @NonNull Duration duration) {
                tasksDoneCountAccumulator.update(1);
            }

            @Override
            public void onTaskFailed(@NonNull BaseTaskDefinition taskDefinition, @NonNull Duration duration) {
                tasksFailedCountAccumulator.update(1);
            }
        };
        BaseExecutorFactory.addObserver(observer);
    }
}
