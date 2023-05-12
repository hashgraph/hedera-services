/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
 * Tracks the fraction of busy time to idle in a cycle
 */
public class CycleBusyTime extends PercentageMetric {
    private static final int PERCENT = 100;

    public CycleBusyTime(final Metrics metrics, final String category, final String name, final String description) {
        super(metrics, category, name, description, CycleBusyTime::busyPercentage);
    }

    private static double busyPercentage(final int b, final int i) {
        final int t = b + i;
        if (t == 0) {
            return 0;
        }
        return (((double) b) / t) * PERCENT;
    }

    /**
     * Add the amount of time the thread was busy
     *
     * @param nanoTime
     * 		the number of nanoseconds a thread was busy
     */
    public void addBusyTime(final long nanoTime) {
        super.update((int) (nanoTime * Units.NANOSECONDS_TO_MICROSECONDS), 0);
    }

    /**
     * Add the amount of time the thread was idle
     *
     * @param nanoTime
     * 		the number of nanoseconds a thread was idle
     */
    public void addIdleTime(final long nanoTime) {
        super.update(0, (int) (nanoTime * Units.NANOSECONDS_TO_MICROSECONDS));
    }
}
