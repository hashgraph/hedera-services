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

package com.swirlds.common.metrics.platform;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.metrics.PlatformMetric;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotEntry;
import com.swirlds.common.metrics.statistics.StatsBuffered;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.MetricConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Basic implementation of all platform-implementations of {@link Metric}
 */
public abstract class DefaultMetric implements Metric, PlatformMetric {

    private final String category;
    private final String name;
    private final String description;
    private final String unit;
    private final String format;

    DefaultMetric(MetricConfig<?, ?> config) {
        Objects.requireNonNull(config, "config must not be null");
        this.category = config.getCategory();
        this.name = config.getName();
        this.description = config.getDescription();
        this.unit = config.getUnit();
        this.format = config.getFormat();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getCategory() {
        return category;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public String getDescription() {
        return description;
    }

    @Override
    @NonNull
    public String getUnit() {
        return unit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public String getFormat() {
        return format;
    }

    /**
     * Take entries of the current values and return them. If the functionality of this {@code PlatformMetric}
     * requires it to be reset in regular intervals, it is done automatically after the snapshot was generated.
     * The list of {@code ValueTypes} will always be in the same order.
     *
     * @return the list of {@code ValueTypes} with their current values
     */
    public abstract @NonNull List<SnapshotEntry> takeSnapshot();

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        // default implementation is empty
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @SuppressWarnings("removal")
    @Override
    public StatsBuffered getStatsBuffered() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final DefaultMetric metric = (DefaultMetric) other;
        return category.equals(metric.category) && name.equals(metric.name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(category, name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("category", category)
                .append("name", name)
                .append("description", description)
                .append("unit", unit)
                .append("format", format)
                .append("dataType", getDataType())
                .toString();
    }
}
