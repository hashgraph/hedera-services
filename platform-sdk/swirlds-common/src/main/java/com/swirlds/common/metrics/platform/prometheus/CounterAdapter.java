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

package com.swirlds.common.metrics.platform.prometheus;

import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType.GLOBAL;
import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType.PLATFORM;
import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.NODE_LABEL;

import com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType;
import com.swirlds.common.platform.NodeId;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.snapshot.Snapshot;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import java.util.Objects;

/**
 * Adapter that synchronizes a {@link Counter}
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
     * 		The {@link Counter} which value should be reported to Prometheus
     * @param adapterType
     * 		Scope of the {@link Counter},
     * 		either {@link AdapterType#GLOBAL} or {@link AdapterType#PLATFORM}
     * @throws NullPointerException if any of the following parameters are {@code null}.
     *     <ul>
     *       <li>{@code registry}</li>
     *       <li>{@code metric}</li>
     *     </ul>
     */
    public CounterAdapter(final CollectorRegistry registry, final Metric metric, final AdapterType adapterType) {
        super(adapterType, metric);
        Objects.requireNonNull(registry, "registry must not be null");
        Objects.requireNonNull(metric, "metric must not be null");

        final Counter.Builder builder =
                super.assignCommonValues(new Counter.Builder()).withoutExemplars();
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
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        final double newValue = ((Number) snapshot.getValue()).doubleValue();
        if (adapterType == GLOBAL) {
            final double oldValue = counter.get();
            counter.inc(newValue - oldValue);
        } else {
            Objects.requireNonNull(nodeId, "nodeId must not be null");
            final Counter.Child child = counter.labels(nodeId.toString());
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
