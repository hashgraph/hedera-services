/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.address;

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.platform.NodeId;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A utility class to encapsulate the metrics for the roster.
 */
public final class RosterMetrics {

    private RosterMetrics() {}

    /**
     * Register the metrics for the roster.
     *
     * @param metrics     the metrics engine
     * @param rosterSize  the size of the roster
     * @param selfId      the ID of the node
     */
    public static void registerRosterMetrics(
            @NonNull final Metrics metrics, @NonNull final int rosterSize, @NonNull final NodeId selfId) {

        metrics.getOrCreate(new FunctionGauge.Config<>(Metrics.INFO_CATEGORY, "memberID", Long.class, selfId::id)
                .withUnit("nodeID")
                .withDescription("The node ID number of this member"));

        metrics.getOrCreate(
                new FunctionGauge.Config<>(Metrics.INFO_CATEGORY, "members", Integer.class, () -> rosterSize)
                        .withUnit("count")
                        .withDescription("total number of nodes currently in the roster"));
    }
}
