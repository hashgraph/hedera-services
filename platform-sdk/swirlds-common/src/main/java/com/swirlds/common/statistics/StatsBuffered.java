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

package com.swirlds.common.statistics;

import com.swirlds.common.statistics.internal.StatsBuffer;

/**
 * A statistic such as StatsSpeedometer or StatsRunningAverage should implement this if it will
 * contain a
 * {@link StatsBuffer} for recent history and another for all of history. The user can then retrieve them from
 * {@link com.swirlds.common.metrics.Metrics}.
 */
public interface StatsBuffered {
    /**
     * get the entire history of values of this statistic. The caller should not modify it.
     *
     * @return A {@link StatsBuffer} object keeps all history statistic.
     */
    StatsBuffer getAllHistory();

    /**
     * get the recent history of values of this statistic. The caller should not modify it.
     *
     * @return A {@link StatsBuffer} object keeps recent history of this statistic.
     */
    StatsBuffer getRecentHistory();

    /**
     * reset the statistic, and make it use the given halflife
     *
     * @param halflife
     * 		half of the exponential weighting comes from the last halfLife seconds
     */
    void reset(double halflife);

    /**
     * get average of values per cycle()
     *
     * @return average of values
     */
    double getMean();

    /**
     * get maximum value from all the values of this statistic
     *
     * @return maximum value
     */
    double getMax();

    /**
     * get minimum value from all the values of this statistic
     *
     * @return minimum value
     */
    double getMin();

    /**
     * get standard deviation of this statistic
     *
     * @return standard deviation
     */
    double getStdDev();
}
