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

import static com.swirlds.common.metrics.Metric.ValueType.MAX;
import static com.swirlds.common.metrics.Metric.ValueType.MIN;
import static com.swirlds.common.metrics.Metric.ValueType.STD_DEV;
import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.MetricConfig;
import com.swirlds.common.metrics.StatEntry;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotEntry;
import com.swirlds.common.statistics.StatsBuffered;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Platform-implementation of {@link StatEntry}
 */
@SuppressWarnings("removal")
public class DefaultStatEntry extends DefaultMetric implements StatEntry {

    private final DataType dataType;
    /**
     * the statistics object (if it implements StatsBuffered), else null
     */
    private final StatsBuffered buffered;
    /**
     * a lambda that resets the statistic, using the given half life
     */
    private final Consumer<Double> reset;
    /**
     * a lambda that returns the statistic string
     */
    private final Supplier<Object> statsStringSupplier;
    /**
     * a lambda that returns the statistic string and resets it at the same time
     */
    private final Supplier<Object> resetStatsStringSupplier;

    @SuppressWarnings("unchecked")
    public DefaultStatEntry(final StatEntry.Config<?> config) {
        super(config);
        this.dataType = MetricConfig.mapDataType(config.getType());
        this.buffered = config.getBuffered();
        this.reset = config.getReset();
        this.statsStringSupplier = (Supplier<Object>) config.getStatsStringSupplier();
        this.resetStatsStringSupplier = (Supplier<Object>) config.getResetStatsStringSupplier();
        if (config.getInit() != null) {
            config.getInit().apply(SettingsCommon.halfLife);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return dataType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StatsBuffered getBuffered() {
        return buffered;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Consumer<Double> getReset() {
        return reset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Supplier<Object> getStatsStringSupplier() {
        return statsStringSupplier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Supplier<Object> getResetStatsStringSupplier() {
        return resetStatsStringSupplier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SnapshotEntry> takeSnapshot() {
        if (buffered == null) {
            return List.of(new SnapshotEntry(VALUE, resetStatsStringSupplier.get()));
        }

        final double max = buffered.getMax();
        final double min = buffered.getMin();
        final double stdDev = buffered.getStdDev();
        final Object value = resetStatsStringSupplier.get();
        return List.of(
                new SnapshotEntry(VALUE, value),
                new SnapshotEntry(MAX, max),
                new SnapshotEntry(MIN, min),
                new SnapshotEntry(STD_DEV, stdDev));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        if (reset != null) {
            reset.accept(SettingsCommon.halfLife);
        } else if (buffered != null) {
            buffered.reset(SettingsCommon.halfLife);
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("removal")
    @Override
    public StatsBuffered getStatsBuffered() {
        return getBuffered();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("value", statsStringSupplier.get())
                .toString();
    }
}
