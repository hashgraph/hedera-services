/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.metric;

import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.MetricConfig;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collection;

/**
 * A {@link Metrics} implementation that throws {@link UnsupportedOperationException} for all methods,
 * needed as a non-null placeholder for {@link com.hedera.node.app.services.ServiceMigrator} calls made to
 * initialize platform state before the metrics are available.
 */
public enum UnavailableMetrics implements Metrics {
    UNAVAILABLE_METRICS;

    @Nullable
    @Override
    public Metric getMetric(@NonNull String category, @NonNull String name) {
        throw new UnsupportedOperationException("Metrics are not available");
    }

    @NonNull
    @Override
    public Collection<Metric> findMetricsByCategory(@NonNull String category) {
        throw new UnsupportedOperationException("Metrics are not available");
    }

    @NonNull
    @Override
    public Collection<Metric> getAll() {
        throw new UnsupportedOperationException("Metrics are not available");
    }

    @Override
    public <T extends Metric> @NonNull T getOrCreate(@NonNull MetricConfig<T, ?> config) {
        throw new UnsupportedOperationException("Metrics are not available");
    }

    @Override
    public void remove(@NonNull String category, @NonNull String name) {
        throw new UnsupportedOperationException("Metrics are not available");
    }

    @Override
    public void remove(@NonNull Metric metric) {
        throw new UnsupportedOperationException("Metrics are not available");
    }

    @Override
    public void remove(@NonNull MetricConfig<?, ?> config) {
        throw new UnsupportedOperationException("Metrics are not available");
    }

    @Override
    public void addUpdater(@NonNull Runnable updater) {
        throw new UnsupportedOperationException("Metrics are not available");
    }

    @Override
    public void removeUpdater(@NonNull Runnable updater) {
        throw new UnsupportedOperationException("Metrics are not available");
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException("Metrics are not available");
    }
}
