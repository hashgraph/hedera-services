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

package com.swirlds.common.test.metrics.internal;

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.DoubleAccumulator;
import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.metrics.DurationGauge;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.IntegerAccumulator;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.metrics.LongGauge;
import com.swirlds.common.metrics.MetricsFactory;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.StatEntry;

/**
 * Builds no-op metrics.
 */
public class NoOpMetricsFactory implements MetricsFactory {

    /**
     * {@inheritDoc}
     */
    @Override
    public Counter createCounter(final Counter.Config config) {
        return new NoOpCounter(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DoubleAccumulator createDoubleAccumulator(final DoubleAccumulator.Config config) {
        return new NoOpDoubleAccumulator(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DoubleGauge createDoubleGauge(final DoubleGauge.Config config) {
        return new NoOpDoubleGauge(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DurationGauge createDurationGauge(final DurationGauge.Config config) {
        return new NoOpDurationGauge(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> FunctionGauge<T> createFunctionGauge(final FunctionGauge.Config<T> config) {
        return new NoOpFunctionGauge<>(config, config.getSupplier().get());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntegerAccumulator createIntegerAccumulator(final IntegerAccumulator.Config config) {
        return new NoOpIntegerAccumulator(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntegerGauge createIntegerGauge(final IntegerGauge.Config config) {
        return new NoOpIntegerGauge(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> IntegerPairAccumulator<T> createIntegerPairAccumulator(final IntegerPairAccumulator.Config<T> config) {
        return new NoOpIntegerPairAccumulator<>(
                config, config.getResultFunction().apply(0, 0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LongAccumulator createLongAccumulator(final LongAccumulator.Config config) {
        return new NoOpLongAccumulator(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LongGauge createLongGauge(final LongGauge.Config config) {
        return new NoOpLongGauge(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RunningAverageMetric createRunningAverageMetric(final RunningAverageMetric.Config config) {
        return new NoOpRunningAverageMetric(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SpeedometerMetric createSpeedometerMetric(final SpeedometerMetric.Config config) {
        return new NoOpSpeedometerMetric(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StatEntry createStatEntry(final StatEntry.Config<?> config) {
        return new NoOpStatEntry(config);
    }
}
