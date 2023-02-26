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

package com.swirlds.common.metrics.platform.prometheus;

import static com.swirlds.common.metrics.platform.prometheus.NameConverter.fix;
import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType.GLOBAL;
import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType.PLATFORM;
import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.NODE_LABEL;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.platform.Snapshot;
import com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType;
import com.swirlds.common.system.NodeId;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;

/**
 * Adapter that synchronizes a {@link com.swirlds.common.metrics.Counter}
 * with the corresponding Prometheus {@link Collector}.
 */
public class CounterAdapter extends AbstractMetricAdapter {

    private final Counter counter;

    /**
     * Constructor of {@code CounterAdapter}.
     *
     * @param registry
     * 		The {@link CollectorRegistry} with which the Prometheus {@link Collector} should be registered
     * @param metric
     * 		The {@link com.swirlds.common.metrics.Counter} which value should be reported to Prometheus
     * @param adapterType
     * 		Scope of the {@link com.swirlds.common.metrics.Counter},
     * 		either {@link AdapterType#GLOBAL} or {@link AdapterType#PLATFORM}
     * @throws IllegalArgumentException if one of the parameters is {@code null}
     */
    public CounterAdapter(final CollectorRegistry registry, final Metric metric, final AdapterType adapterType) {
        super(adapterType);
        throwArgNull(registry, "registry");
        throwArgNull(metric, "metric");
        final Counter.Builder builder = new Counter.Builder()
                .subsystem(fix(metric.getCategory()))
                .name(fix(metric.getName()))
                .help(metric.getDescription())
                .unit(metric.getUnit())
                .withoutExemplars();
        if (adapterType == PLATFORM) {
            builder.labelNames(NODE_LABEL);
        }
        this.counter = builder.register(registry);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(final Snapshot snapshot, final NodeId nodeId) {
        throwArgNull(snapshot, "snapshot");
        final double newValue = ((Number) snapshot.getValue()).doubleValue();
        if (adapterType == GLOBAL) {
            final double oldValue = counter.get();
            counter.inc(newValue - oldValue);
        } else {
            throwArgNull(nodeId, "nodeId");
            final Counter.Child child = counter.labels(Long.toString(nodeId.getId()));
            final double oldValue = child.get();
            child.inc(newValue - oldValue);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unregister(final CollectorRegistry registry) {
        registry.unregister(counter);
    }
}
