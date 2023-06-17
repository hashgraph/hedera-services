/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.tipset;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Encapsulates metrics for the tipset event creator.
 */
public class TipsetMetrics {

    private static final RunningAverageMetric.Config TIPSET_SCORE_CONFIG = new RunningAverageMetric.Config(
                    "platform", "tipsetScore")
            .withDescription("The score, based on tipset advancements, of each new event created by this "
                    + "node. A score of 0.0 means the event did not advance consensus at all, while a score "
                    + "of 1.0 means that the event advanced consensus as much as a single event can.");
    private final RunningAverageMetric tipsetScoreMetric;

    private static final RunningAverageMetric.Config BULLY_SCORE_CONFIG = new RunningAverageMetric.Config(
                    "platform", "bullyScore")
            .withDescription("The score, based on tipset advancements, of how much of a 'bully' "
                    + "this node is being to other nodes. Bullying is defined as refusing to use a "
                    + "node's events as other parents.");
    private final RunningAverageMetric bullyScoreMetric;

    /**
     * Create metrics for the tipset event creator.
     *
     * @param platformContext the platform context
     */
    public TipsetMetrics(@NonNull final PlatformContext platformContext) {
        final Metrics metrics = platformContext.getMetrics();
        tipsetScoreMetric = metrics.getOrCreate(TIPSET_SCORE_CONFIG);
        bullyScoreMetric = metrics.getOrCreate(BULLY_SCORE_CONFIG);
    }

    /**
     * Get the metric used to track the tipset score of events created by this node.
     *
     * @return the tipset score metric
     */
    @NonNull
    public RunningAverageMetric getTipsetScoreMetric() {
        return tipsetScoreMetric;
    }

    /**
     * Get the metric used to track the bully score of this node.
     *
     * @return the bully score metric
     */
    @NonNull
    public RunningAverageMetric getBullyScoreMetric() {
        return bullyScoreMetric;
    }
}
