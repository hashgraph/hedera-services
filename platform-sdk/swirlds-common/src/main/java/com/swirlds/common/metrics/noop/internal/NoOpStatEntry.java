/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.metrics.api.Metric.DataType.INT;

import com.swirlds.common.metrics.StatEntry;
import com.swirlds.common.metrics.statistics.StatsBuffered;
import com.swirlds.metrics.api.MetricConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A no-op implementation of a stat entry.
 */
public class NoOpStatEntry extends AbstractNoOpMetric implements StatEntry {

    public NoOpStatEntry(final @NonNull MetricConfig<?, ?> config) {
        super(config);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public DataType getDataType() {
        return INT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public StatsBuffered getBuffered() {
        return new NoOpStatsBuffered();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Consumer<Double> getReset() {
        return x -> {};
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Supplier<Object> getStatsStringSupplier() {
        return () -> "";
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Supplier<Object> getResetStatsStringSupplier() {
        return () -> "";
    }
}
