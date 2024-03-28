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

package com.hedera.node.app.state.merkle.disk;

import static java.util.Objects.requireNonNull;

import com.swirlds.common.metrics.FunctionGauge.Config;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Helper class that maintains utilization metrics for a store.
 */
public class OnDiskWritableKvStateMetrics {

    private final AtomicLong count;

    /**
     * Create a new {@link OnDiskWritableKvStateMetrics} instance.
     *
     * @param metrics The metrics-API used to report utilization.
     * @param name The name of the entities that are being tracked.
     * @param label The label of the entities used in the description.
     * @param currentValue The current size of the store.
     * @param maxCapacity The maximum capacity of the store.
     */
    public OnDiskWritableKvStateMetrics(
            @NonNull final Metrics metrics,
            @NonNull final String name,
            @NonNull final String label,
            final long currentValue,
            final long maxCapacity) {
        requireNonNull(name, "name must not be null");
        requireNonNull(label, "label must not be null");
        if (currentValue < 0) {
            throw new IllegalArgumentException("currentValue must be non-negative");
        }
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("maxCapacity must be positive");
        }
        this.count = new AtomicLong(currentValue);

        final var totalUtilizationConfig = new Config<>("app", name + "Used", Long.class, count::get)
                .withDescription(String.format("instantaneous %% used of %s system limit", label))
                .withFormat("%,d");
        metrics.getOrCreate(totalUtilizationConfig);

        final double relativeFactor = 100.0 / maxCapacity;
        final var relativeUtilizationConfig = new Config<>(
                        "app", name + "PercentUsed", Double.class, () -> count.get() * relativeFactor)
                .withDescription(String.format("instantaneous %% used of %s system limit", label))
                .withFormat("%,13.2f");
        metrics.getOrCreate(relativeUtilizationConfig);
    }

    /**
     * Update the metrics with the current count.
     *
     * @param newValue The current count.
     */
    public void updateMetrics(final long newValue) {
        this.count.set(newValue);
    }
}
