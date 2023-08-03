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

package com.swirlds.common.metrics.platform;

import com.swirlds.common.metrics.DurationGauge;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.MetricsFactory;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.StatEntry;
import com.swirlds.metrics.Counter;
import com.swirlds.metrics.DoubleAccumulator;
import com.swirlds.metrics.DoubleGauge;
import com.swirlds.metrics.IntegerAccumulator;
import com.swirlds.metrics.IntegerGauge;
import com.swirlds.metrics.LongAccumulator;
import com.swirlds.metrics.LongGauge;
import com.swirlds.metrics.Metric;
import com.swirlds.metrics.MetricConfig;

/**
 * An implementation of {@link MetricsFactory} that creates platform-internal {@link Metric}-instances
 */
public class DefaultMetricsFactory implements MetricsFactory {

    /**
     * {@inheritDoc}
     */
    @Override
    public Counter createCounter(final Counter.Config config) {
        return new DefaultCounter(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DoubleAccumulator createDoubleAccumulator(final DoubleAccumulator.Config config) {
        return new DefaultDoubleAccumulator(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DoubleGauge createDoubleGauge(final DoubleGauge.Config config) {
        return new DefaultDoubleGauge(config);
    }

    @Override
    public DurationGauge createDurationGauge(final DurationGauge.Config config) {
        return new DefaultDurationGauge(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> FunctionGauge<T> createFunctionGauge(final FunctionGauge.Config<T> config) {
        return new DefaultFunctionGauge<>(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntegerAccumulator createIntegerAccumulator(final IntegerAccumulator.Config config) {
        return new DefaultIntegerAccumulator(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntegerGauge createIntegerGauge(final IntegerGauge.Config config) {
        return new DefaultIntegerGauge(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> IntegerPairAccumulator<T> createIntegerPairAccumulator(final IntegerPairAccumulator.Config<T> config) {
        return new DefaultIntegerPairAccumulator<>(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LongAccumulator createLongAccumulator(final LongAccumulator.Config config) {
        return new DefaultLongAccumulator(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LongGauge createLongGauge(final LongGauge.Config config) {
        return new DefaultLongGauge(config);
    }

    @Override
    public <M extends Metric, C extends MetricConfig<M, ?>> M createMetric(C config) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RunningAverageMetric createRunningAverageMetric(final RunningAverageMetric.Config config) {
        return new DefaultRunningAverageMetric(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SpeedometerMetric createSpeedometerMetric(final SpeedometerMetric.Config config) {
        return new DefaultSpeedometerMetric(config);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("removal")
    @Override
    public StatEntry createStatEntry(final StatEntry.Config<?> config) {
        return new DefaultStatEntry(config);
    }
}
