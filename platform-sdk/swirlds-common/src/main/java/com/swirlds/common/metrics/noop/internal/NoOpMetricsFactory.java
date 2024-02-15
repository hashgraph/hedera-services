/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.noop.internal;

import com.swirlds.common.metrics.DurationGauge;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.PlatformMetricsFactory;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.StatEntry;
import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.DoubleAccumulator;
import com.swirlds.metrics.api.DoubleGauge;
import com.swirlds.metrics.api.IntegerAccumulator;
import com.swirlds.metrics.api.IntegerGauge;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.metrics.api.LongGauge;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Builds no-op metrics.
 */
public class NoOpMetricsFactory implements PlatformMetricsFactory {

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Counter createCounter(final @NonNull Counter.Config config) {
        return new NoOpCounter(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull DoubleAccumulator createDoubleAccumulator(final @NonNull DoubleAccumulator.Config config) {
        return new NoOpDoubleAccumulator(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull DoubleGauge createDoubleGauge(final @NonNull DoubleGauge.Config config) {
        return new NoOpDoubleGauge(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull DurationGauge createDurationGauge(final @NonNull DurationGauge.Config config) {
        return new NoOpDurationGauge(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> @NonNull FunctionGauge<T> createFunctionGauge(final @NonNull FunctionGauge.Config<T> config) {
        return new NoOpFunctionGauge<>(config, config.getSupplier().get());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull IntegerAccumulator createIntegerAccumulator(final @NonNull IntegerAccumulator.Config config) {
        return new NoOpIntegerAccumulator(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull IntegerGauge createIntegerGauge(final @NonNull IntegerGauge.Config config) {
        return new NoOpIntegerGauge(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> @NonNull IntegerPairAccumulator<T> createIntegerPairAccumulator(
            final @NonNull IntegerPairAccumulator.Config<T> config) {
        return new NoOpIntegerPairAccumulator<>(
                config, config.getResultFunction().apply(0, 0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull LongAccumulator createLongAccumulator(final @NonNull LongAccumulator.Config config) {
        return new NoOpLongAccumulator(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull LongGauge createLongGauge(final @NonNull LongGauge.Config config) {
        return new NoOpLongGauge(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull RunningAverageMetric createRunningAverageMetric(final @NonNull RunningAverageMetric.Config config) {
        return new NoOpRunningAverageMetric(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull SpeedometerMetric createSpeedometerMetric(final @NonNull SpeedometerMetric.Config config) {
        return new NoOpSpeedometerMetric(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull StatEntry createStatEntry(final @NonNull StatEntry.Config<?> config) {
        return new NoOpStatEntry(config);
    }
}
