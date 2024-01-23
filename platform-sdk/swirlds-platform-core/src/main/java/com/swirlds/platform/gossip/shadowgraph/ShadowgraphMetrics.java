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

package com.swirlds.platform.gossip.shadowgraph;

import static com.swirlds.metrics.api.FloatFormats.FORMAT_5_3;
import static com.swirlds.metrics.api.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.stats.AverageStat;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Encapsulates metrics for the shadowgraph.
 */
public class ShadowgraphMetrics {

    private final AverageStat indicatorsWaitingForExpiry;

    /**
     * Constructor
     *
     * @param platformContext the platform context
     */
    public ShadowgraphMetrics(@NonNull final PlatformContext platformContext) {
        indicatorsWaitingForExpiry = new AverageStat(
                platformContext.getMetrics(),
                PLATFORM_CATEGORY,
                "indicatorsWaitingForExpiry",
                "the average number of indicators waiting to be expired by the shadowgraph",
                FORMAT_5_3,
                AverageStat.WEIGHT_VOLATILE);
    }

    /**
     * Called by {@link Shadowgraph} to update the number of generations that should be expired but can't be yet due to
     * reservations.
     *
     * @param numGenerations the new number of generations
     */
    public void updateIndicatorsWaitingForExpiry(final long numGenerations) {
        indicatorsWaitingForExpiry.update(numGenerations);
    }
}
