/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics;

import com.swirlds.metrics.BasicMetricsFactory;
import com.swirlds.metrics.Metric;

/**
 * Factory for all {@link Metric}-implementations
 */
public interface MetricsFactory extends BasicMetricsFactory {

    /**
     * Creates a {@link DurationGauge}
     *
     * @param config the configuration
     * @return the new {@link DurationGauge}
     * @throws IllegalArgumentException if {@code config} is {@code null}
     */
    DurationGauge createDurationGauge(final DurationGauge.Config config);

    /**
     * Creates a {@link FunctionGauge}
     *
     * @param config
     * 		the configuration
     * @param <T>
     * 		the type of the value that will be contained in the {@code FunctionGauge}
     * @return the new {@code FunctionGauge}
     * @throws IllegalArgumentException
     * 		if {@code config} is {@code null}
     */
    <T> FunctionGauge<T> createFunctionGauge(final FunctionGauge.Config<T> config);

    /**
     * Creates a {@link IntegerPairAccumulator}
     *
     * @param config
     * 		the configuration
     * @return the new {@code IntegerPairAccumulator}
     * @throws IllegalArgumentException
     * 		if {@code config} is {@code null}
     */
    <T> IntegerPairAccumulator<T> createIntegerPairAccumulator(IntegerPairAccumulator.Config<T> config);

    /**
     * Creates a {@link RunningAverageMetric}
     *
     * @param config
     * 		the configuration
     * @return the new {@code RunningAverageMetric}
     * @throws IllegalArgumentException
     * 		if {@code config} is {@code null}
     */
    RunningAverageMetric createRunningAverageMetric(final RunningAverageMetric.Config config);

    /**
     * Creates a {@link SpeedometerMetric}
     *
     * @param config
     * 		the configuration
     * @return the new {@code SpeedometerMetric}
     * @throws IllegalArgumentException
     * 		if {@code config} is {@code null}
     */
    SpeedometerMetric createSpeedometerMetric(final SpeedometerMetric.Config config);

    /**
     * Creates a {@link StatEntry}
     *
     * @param config
     * 		the configuration
     * @return the new {@code StatEntry}
     * @throws IllegalArgumentException
     * 		if {@code config} is {@code null}
     */
    @SuppressWarnings("removal")
    StatEntry createStatEntry(final StatEntry.Config<?> config);
}
