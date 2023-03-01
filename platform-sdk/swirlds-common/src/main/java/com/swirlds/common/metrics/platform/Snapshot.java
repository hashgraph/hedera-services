/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.platform;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.platform.DefaultMetric.LegacySnapshotEntry;
import com.swirlds.common.utility.CommonUtils;
import java.util.List;
import java.util.Optional;

/**
 * An instance of {@code Snapshot} contains the data of a single snapshot of a {@link Metric}.
 */
public record Snapshot(Metric metric, List<SnapshotEntry> entries) {

    /**
     * Create a {@code Snapshot} of a {@link DefaultMetric}
     *
     * @param metric The source metric
     * @return the {@code Snapshot}
     */
    public static Snapshot of(final Metric metric) {
        CommonUtils.throwArgNull(metric, "metric");
        final List<SnapshotEntry> snapshotEntries = metric.getBaseMetrics().entrySet().stream()
                .filter(entry -> entry.getValue() instanceof DefaultMetric)
                .flatMap(entry -> ((DefaultMetric)entry.getValue()).takeSnapshot().stream()
                        .map(legacySnapshotEntry -> SnapshotEntry.of(entry.getKey(), legacySnapshotEntry)))
                .toList();
        return new Snapshot(metric, snapshotEntries);
    }

    /**
     * Get the main value of this {@code Snapshot}, if available
     *
     * @return the main value
     */
    public Optional<SnapshotEntry> getMainEntry() {
        final List<Snapshot.SnapshotEntry> entries = entries();
        if (entries.size() == 1) {
            return Optional.of(entries.get(0));
        }
        return entries.stream()
                .filter(entry -> "VALUE".equalsIgnoreCase(entry.type()))
                .findAny();
    }

    /**
     * As single entry within a {@code Snapshot}
     *
     * @param type the type of this entry (e.g. "avg", "min", "max")
     * @param value the actual value
     */
    public record SnapshotEntry(String type, Object value) {
        private static SnapshotEntry of(final String type, final LegacySnapshotEntry legacySnapshotEntry) {
            if (isBlank(type)) {
                return new SnapshotEntry(legacySnapshotEntry.valueType().getLabel(), legacySnapshotEntry.value());
            } else if (legacySnapshotEntry.valueType() == VALUE) {
                return new SnapshotEntry(type, legacySnapshotEntry.value());
            } else {
                return new SnapshotEntry(type + "." + legacySnapshotEntry.valueType().getLabel(), legacySnapshotEntry.value());
            }
        }
    }
}
