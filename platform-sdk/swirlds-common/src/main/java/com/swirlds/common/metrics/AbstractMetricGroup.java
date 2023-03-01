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

import static com.swirlds.common.utility.CommonUtils.throwArgBlank;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class AbstractMetricGroup implements Metric {

    private final MetricsFactory factory;
    private final MetricConfig<?> config;

    private final Map<String, Metric> metrics = new ConcurrentSkipListMap<>();

    public AbstractMetricGroup(final MetricsFactory factory, final MetricConfig<?> config) {
        this.factory = throwArgNull(factory, "factory");
        this.config = throwArgNull(config, "config");
    }

    protected <T extends Metric> T createMetric(String type, MetricConfig<T> subMetricConfig) {
        throwArgBlank(type, "type");
        throwArgNull(subMetricConfig, "config");
        if (metrics.containsKey(type)) {
            throw new IllegalArgumentException("Metric already exists for type: " + type);
        }
        final T metric = factory.createSubMetric(config, subMetricConfig);
        metrics.put(type, metric);
        return metric;
    }

    @Override
    public String getCategory() {
        return config.category();
    }

    @Override
    public String getName() {
        return config.name();
    }

    @Override
    public String getDescription() {
        return config.description();
    }

    @Override
    public String getUnit() {
        return config.unit();
    }

    @Override
    public String getFormat() {
        return config.format();
    }

    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    @Override
    public MetricType getMetricType() {
        return null;
    }

    @Override
    public DataType getDataType() {
        final var dataTypes = getBaseMetrics().values().stream().map(Metric::getDataType).collect(Collectors.toSet());
        return dataTypes.size() > 1 ? DataType.MIXED : dataTypes.iterator().next();
    }

    @Override
    public boolean isMetricGroup() {
        return true;
    }

    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    @Override
    public EnumSet<ValueType> getValueTypes() {
        return EnumSet.noneOf(ValueType.class);
    }

    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    @Override
    public Object get(ValueType valueType) {
        throw new IllegalArgumentException("Unsupported ValueType: " + valueType);
    }

    @Override
    public Map<String, BaseMetric> getBaseMetrics() {
        return metrics.entrySet().stream()
                .flatMap(metricEntry -> metricEntry.getValue().getBaseMetrics().entrySet().stream()
                        .map(baseMetricEntry -> createMetricEntry(metricEntry.getKey(), baseMetricEntry)))
                .collect(Collectors.toUnmodifiableMap(MetricEntry::type, MetricEntry::metric));
    }

    private record MetricEntry(String type, BaseMetric metric) {}

    private static MetricEntry createMetricEntry(final String metricKey, final Map.Entry<String, BaseMetric> subMetricEntry) {
        final String subMetricKey = subMetricEntry.getKey();
        final String key;
        if (isBlank(metricKey)) {
            key = subMetricKey;
        } else if (StringUtils.isBlank(subMetricKey)) {
            key = metricKey;
        } else {
            key = metricKey + "." + subMetricKey;
        }
        return new MetricEntry(key, subMetricEntry.getValue());
    }

    @Override
    public void reset() {
        metrics.values().forEach(Metric::reset);
    }
}
