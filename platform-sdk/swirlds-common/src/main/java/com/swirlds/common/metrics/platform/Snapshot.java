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

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.utility.CommonUtils;
import java.util.List;

/**
 * An instance of {@code Snapshot} contains the data of a single snapshot of a {@link Metric}.
 */
public record Snapshot(Metric metric, List<SnapshotEntry> entries) {

    /**
     * Create a {@code Snapshot} of a {@link DefaultMetric}
     *
     * @param metric
     * 		The source metric
     * @return the {@code Snapshot}
     */
    public static Snapshot of(final DefaultMetric metric) {
        CommonUtils.throwArgNull(metric, "metric");
        return new Snapshot(metric, metric.takeSnapshot());
    }

    /**
     * Get the main value of this {@code Snapshot}
     *
     * @return the main value
     */
    public Object getValue() {
        for (final SnapshotEntry entry : entries) {
            if (entry.valueType == VALUE) {
                return entry.value;
            }
        }
        throw new IllegalStateException("Snapshot does not contain a value: " + this);
    }

    /**
     * As single entry within a {@code Snapshot}
     *
     * @param valueType
     * 		the {@link com.swirlds.common.metrics.Metric.ValueType} of this entry
     * @param value
     * 		the actual value
     */
    public record SnapshotEntry(Metric.ValueType valueType, Object value) {}
}
