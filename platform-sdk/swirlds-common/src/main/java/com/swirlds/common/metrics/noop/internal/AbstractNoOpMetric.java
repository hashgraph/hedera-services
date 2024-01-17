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

import com.swirlds.common.metrics.PlatformMetric;
import com.swirlds.common.metrics.statistics.StatsBuffered;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.MetricConfig;
import java.util.EnumSet;

/**
 * Boilerplate for a no-op metric.
 */
public abstract class AbstractNoOpMetric implements Metric, PlatformMetric {

    private final MetricConfig<?, ?> config;

    protected AbstractNoOpMetric(final MetricConfig<?, ?> config) {
        this.config = config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCategory() {
        return config.getCategory();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return config.getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return config.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUnit() {
        return config.getUnit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFormat() {
        return config.getFormat();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EnumSet<ValueType> getValueTypes() {
        return EnumSet.noneOf(ValueType.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        // intentional no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StatsBuffered getStatsBuffered() {
        return new NoOpStatsBuffered();
    }
}
