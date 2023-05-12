/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.stats.cycle;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.utility.Units;

/**
 * Tracks the fraction of time spent in a single interval of a cycle
 */
public class IntervalPercentageMetric extends PercentageMetric {

    public IntervalPercentageMetric(final Metrics metrics, final CycleDefinition definition, final int intervalIndex) {
        super(
                metrics,
                definition.getCategory(),
                definition.getName() + "-" + definition.getIntervalName(intervalIndex),
                definition.getIntervalDescription(intervalIndex));
    }

    /**
     * Update the time taken for this interval
     *
     * @param cycleNanoTime
     * 		the number of nanoseconds the whole cycle lasted
     * @param intervalNanoTime
     * 		the number of nanoseconds this interval lasted
     */
    public void updateTime(final long cycleNanoTime, final long intervalNanoTime) {
        super.update(toMicros(cycleNanoTime), toMicros(intervalNanoTime));
    }

    private int toMicros(final long nanos) {
        return (int) (nanos * Units.NANOSECONDS_TO_MICROSECONDS);
    }
}
