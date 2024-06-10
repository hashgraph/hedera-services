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

package com.swirlds.metrics.impl.snapshot;

import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.Metric.ValueType;
import com.swirlds.metrics.api.snapshot.Label;
import com.swirlds.metrics.api.snapshot.SnapshotEntry;
import com.swirlds.metrics.api.snapshot.SnapshotableMetric;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An instance of {@code Snapshot} contains the data of a single snapshot of a {@link Metric}.
 */
public record Snapshot(
        @NonNull Metric metric, @NonNull List<SnapshotEntry> entries, @NonNull Collection<Label> labels) {

    public Snapshot(@NonNull Metric metric, @NonNull List<SnapshotEntry> entries, @NonNull Collection<Label> labels) {
        this.metric = Objects.requireNonNull(metric, "metric must not be null");
        this.entries = Objects.requireNonNull(entries, "entries must not be null");
        Objects.requireNonNull(labels, "labels must not be null");
        this.labels = Collections.unmodifiableCollection(labels);
    }

    /**
     * Create a {@code Snapshot} of a {@link SnapshotableMetric}
     *
     * @param metric The source metric
     * @return the {@code Snapshot}
     * @throws NullPointerException in case {@code metric} parameter is {@code null}
     */
    @NonNull
    public static Snapshot of(final SnapshotableMetric metric) {
        Objects.requireNonNull(metric, "metric must not be null");
        return new Snapshot(metric, metric.takeSnapshot(), metric.getLabels());
    }

    /**
     * Get the main value of this {@code Snapshot}
     *
     * @return the main value
     */
    public Object getValue() {
        for (final SnapshotEntry entry : entries) {
            if (entry.valueType() == ValueType.VALUE) {
                return entry.value();
            }
        }
        throw new IllegalStateException("Snapshot does not contain a value: " + this);
    }
}
