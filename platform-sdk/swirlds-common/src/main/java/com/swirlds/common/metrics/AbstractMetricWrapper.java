/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.common.metrics;

import com.swirlds.common.statistics.StatsBuffered;
import java.util.EnumSet;
import java.util.Map;

public class AbstractMetricWrapper<T extends Metric> implements Metric {

    private final T wrapped;

    public AbstractMetricWrapper(final MetricsFactory factory, MetricConfig<T> config) {
        wrapped = factory.createWrappedMetric(config);
    }

    protected T getWrapped() {
        return wrapped;
    }

    @Override
    public String getCategory() {
        return wrapped.getCategory();
    }

    @Override
    public String getName() {
        return wrapped.getName();
    }

    @Override
    public String getDescription() {
        return wrapped.getDescription();
    }

    @Override
    public String getUnit() {
        return wrapped.getUnit();
    }

    @Override
    public String getFormat() {
        return wrapped.getFormat();
    }

    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    @Override
    public MetricType getMetricType() {
        return wrapped.getMetricType();
    }

    @Override
    public DataType getDataType() {
        return wrapped.getDataType();
    }

    @Override
    public boolean isMetricGroup() {
        return wrapped.isMetricGroup();
    }

    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    @Override
    public EnumSet<ValueType> getValueTypes() {
        return wrapped.getValueTypes();
    }

    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    @Override
    public Object get(ValueType valueType) {
        return wrapped.get(valueType);
    }

    @Override
    public Map<String, BaseMetric> getBaseMetrics() {
        return wrapped.getBaseMetrics();
    }

    @Override
    public void reset() {
        wrapped.reset();
    }

    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    @Override
    public StatsBuffered getStatsBuffered() {
        return wrapped.getStatsBuffered();
    }
}
