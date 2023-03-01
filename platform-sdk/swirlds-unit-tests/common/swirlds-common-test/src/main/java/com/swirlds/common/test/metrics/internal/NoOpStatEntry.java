/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.metrics.Metric.DataType.INT;

import com.swirlds.common.metrics.MetricConfig;
import com.swirlds.common.metrics.StatEntry;
import com.swirlds.common.statistics.StatsBuffered;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A no-op implementation of a stat entry.
 */
public class NoOpStatEntry extends AbstractNoOpMetric implements StatEntry {

    public NoOpStatEntry(final MetricConfig<?> config) {
        super(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return INT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StatsBuffered getBuffered() {
        return new NoOpStatsBuffered();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Consumer<Double> getReset() {
        return x -> {};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Supplier<Object> getStatsStringSupplier() {
        return () -> "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Supplier<Object> getResetStatsStringSupplier() {
        return () -> "";
    }
}
