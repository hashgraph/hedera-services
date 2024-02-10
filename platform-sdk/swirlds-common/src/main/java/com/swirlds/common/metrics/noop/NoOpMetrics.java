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

package com.swirlds.common.metrics.noop;

import com.swirlds.common.metrics.PlatformMetrics;
import com.swirlds.common.metrics.noop.internal.NoOpMetricsFactory;
import com.swirlds.common.platform.NodeId;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.MetricConfig;
import com.swirlds.metrics.api.Metrics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A no-op {@link Metrics} implementation.
 *
 * @deprecated This class serves as a temporary workaround and may be removed at a future time without notice. External
 * parties are warned not to rely on this class.
 */
@Deprecated(forRemoval = true)
public class NoOpMetrics implements PlatformMetrics {

    private final Map<String /* category */, Map<String /* name */, Metric>> metrics = new HashMap<>();

    private static final NoOpMetricsFactory factory = new NoOpMetricsFactory();

    @Override
    public NodeId getNodeId() {
        return new NodeId(42L);
    }

    @Override
    public boolean isGlobalMetrics() {
        return false;
    }

    @Override
    public boolean isPlatformMetrics() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Metric getMetric(final String category, final String name) {
        final Map<String, Metric> metricsInCategory = metrics.get(category);
        if (metricsInCategory == null) {
            return null;
        }
        return metricsInCategory.get(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Collection<Metric> findMetricsByCategory(final String category) {
        final Map<String, Metric> metricsInCategory = metrics.get(category);
        if (metricsInCategory == null) {
            return List.of();
        }
        return metricsInCategory.values();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Collection<Metric> getAll() {
        // Not very efficient, but the no-op metrics doesn't do snapshotting, so this should rarely (if ever) be called.
        final List<Metric> allMetrics = new ArrayList<>();
        for (final Map<String, Metric> metricsInCategory : metrics.values()) {
            allMetrics.addAll(metricsInCategory.values());
        }
        return allMetrics;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public synchronized <T extends Metric> T getOrCreate(final MetricConfig<T, ?> config) {

        final String category = config.getCategory();
        final String name = config.getName();

        final Map<String, Metric> metricsInCategory = metrics.computeIfAbsent(category, k -> new HashMap<>());
        return (T) metricsInCategory.computeIfAbsent(name, k -> factory.createMetric(config));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void remove(final String category, final String name) {
        final Map<String, Metric> metricsInCategory = metrics.get(category);

        if (metricsInCategory == null) {
            return;
        }

        metricsInCategory.remove(name);

        if (metricsInCategory.isEmpty()) {
            metrics.remove(category);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(final Metric metric) {
        remove(metric.getCategory(), metric.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(final MetricConfig<?, ?> config) {
        remove(config.getCategory(), config.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addUpdater(final Runnable updater) {
        // Intentional no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeUpdater(final Runnable updater) {
        // Intentional no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        // Intentional no-op
    }
}
