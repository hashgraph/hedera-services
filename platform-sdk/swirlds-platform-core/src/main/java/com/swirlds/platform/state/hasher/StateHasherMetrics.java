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

package com.swirlds.platform.state.hasher;

import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;

/**
 * Encapsulates metrics for state hashing.
 */
public class StateHasherMetrics {

    private static final RunningAverageMetric.Config STATE_HASHING_TIME_CONFIG = new RunningAverageMetric.Config(
                    "platform", "sigStateHash")
            .withDescription("average time it takes to hash a SignedState (in milliseconds)")
            .withUnit("ms");
    private final RunningAverageMetric stateHashingTime;

    /**
     * Constructor.
     *
     * @param metrics the metrics object
     */
    public StateHasherMetrics(@NonNull final Metrics metrics) {
        stateHashingTime = metrics.getOrCreate(STATE_HASHING_TIME_CONFIG);
    }
    /**
     * Report the time taken to hash a state.
     *
     * @param hashingTime the time taken to hash a state
     */
    public void reportHashingTime(@NonNull final Duration hashingTime) {
        stateHashingTime.update(hashingTime.toMillis());
    }
}
