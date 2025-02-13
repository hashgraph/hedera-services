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

package com.swirlds.metrics.impl;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.metrics.api.Label;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.MetricConfig;
import com.swirlds.metrics.api.snapshot.SnapshotableMetric;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Basic implementation of all platform-implementations of {@link Metric}
 */
public abstract class AbstractMetric implements SnapshotableMetric {

    private final String category;
    private final String name;
    private final String description;
    private final String unit;
    private final String format;

    private final Set<Label> labels;

    private final Set<String> supportedLabelKeys;

    protected AbstractMetric(@NonNull final MetricConfig<?, ?> config) {
        Objects.requireNonNull(config, "config must not be null");
        this.category = config.getCategory();
        this.name = config.getName();
        this.description = config.getDescription();
        this.unit = config.getUnit();
        this.format = config.getFormat();
        this.labels = Collections.unmodifiableSet(config.getPredefinedLabels());
        this.supportedLabelKeys = Collections.unmodifiableSet(config.getLabelKeys());
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
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        // default implementation is empty
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
        final AbstractMetric metric = (AbstractMetric) other;
        return category.equals(metric.category) && name.equals(metric.name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(category, name);
    }

    @NonNull
    @Override
    public Set<Label> getLabels() {
        return labels;
    }

    @NonNull
    @Override
    public Set<String> getSupportedLabelKeys() {
        return supportedLabelKeys;
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
