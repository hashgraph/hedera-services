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

package com.swirlds.metrics.api.snapshot;

import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.snapshot.Snapshot.SnapshotEntry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A metric that can provide a snapshot of its current values.
 */
public interface SnapshotableMetric extends Metric {

    /**
     * Take entries of the current values and return them. If the functionality of this {@code PlatformMetric} requires
     * it to be reset in regular intervals, it is done automatically after the snapshot was generated. The list of
     * {@code ValueTypes} will always be in the same order.
     *
     * @return the list of {@code ValueTypes} with their current values
     */
    @NonNull
    List<SnapshotEntry> takeSnapshot();
}
