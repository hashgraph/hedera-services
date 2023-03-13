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

package com.swirlds.common.metrics.platform.prometheus;

import com.swirlds.common.metrics.platform.Snapshot;
import com.swirlds.common.system.NodeId;
import io.prometheus.client.CollectorRegistry;

/**
 * Common interface of all adapters, which synchronize a {@link com.swirlds.common.metrics.Metric}
 * with a corresponding Prometheus {@link io.prometheus.client.Collector}.
 */
public interface MetricAdapter {

    /**
     * Update the {@link io.prometheus.client.Collector} with the data of the given snapshot.
     *
     * @param snapshot
     * 		The snapshot, which value should be used for the update.
     * @param nodeId
     * 		The {@link NodeId} in which context the metric is used. May be {@code null}, if it is a global metric.
     * @throws IllegalArgumentException if {@code snapshot} is {@code null}
     */
    void update(final Snapshot snapshot, final NodeId nodeId);

    /**
     * Increase the reference count
     *
     * @return the new reference count
     */
    int incAndGetReferenceCount();

    /**
     * Decrease the reference count
     *
     * @return the new reference count
     */
    int decAndGetReferenceCount();

    /**
     * Unregister all created Prometheus metrics
     *
     * @param registry
     * 		The {@link CollectorRegistry} from which to unregister
     */
    void unregister(final CollectorRegistry registry);
}
