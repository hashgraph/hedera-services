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

package com.swirlds.platform.metrics;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_3;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.platform.stats.AverageAndMax;
import com.swirlds.platform.stats.AverageStat;

/**
 * Metrics related to how platform components are wired together.
 */
public class WiringMetrics {

    /**
     * The size of the queue holding tasks for
     * {@link com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer}s
     */
    private final AverageAndMax asyncLatestCompleteStateQueueSize;

    /**
     * @param metrics
     * 		reference to the metrics-system
     */
    public WiringMetrics(final Metrics metrics) {
        asyncLatestCompleteStateQueueSize = new AverageAndMax(
                metrics,
                INTERNAL_CATEGORY,
                "asyncLatestCompleteStateQueueSize",
                "average number of new latest complete state occurrences waiting to be sent to consumers",
                FORMAT_10_3,
                AverageStat.WEIGHT_VOLATILE);
    }

    /**
     * Update the size of the task queue for
     * {@link com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer}s
     *
     * @param value
     * 		the current size of the queue
     */
    public void updateLatestCompleteStateQueueSize(final long value) {
        asyncLatestCompleteStateQueueSize.update(value);
    }
}
