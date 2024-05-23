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

package com.swirlds.platform.state.snapshot;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Encapsulates metrics for the state snapshot manager.
 */
public class StateSnapshotManagerMetrics {

    private static final RunningAverageMetric.Config WRITE_STATE_TO_DISK_TIME_CONFIG = new RunningAverageMetric.Config(
                    "platform", "writeStateToDisk")
            .withDescription("average time it takes to write a SignedState to disk (in milliseconds)")
            .withUnit("ms");

    private final RunningAverageMetric writeStateToDiskTime;

    private static final RunningAverageMetric.Config STATE_TO_DISK_TIME_CONFIG = new RunningAverageMetric.Config(
                    "platform", "stateToDisk")
            .withDescription("average time it takes to do perform all actions when writing a SignedState to disk "
                    + "(in milliseconds)")
            .withUnit("ms");
    private final RunningAverageMetric stateToDiskTime;

    private static final Counter.Config TOTAL_NEVER_SIGNED_DISK_STATES_CONFIG = new Counter.Config(
                    "platform", "totalNeverSignedDiskStates")
            .withDescription(
                    "total number of disk-bound states that did not receive enough signatures in the allowed time")
            .withUnit("count");
    private final Counter totalNeverSignedDiskStates;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     */
    public StateSnapshotManagerMetrics(@NonNull final PlatformContext platformContext) {
        final Metrics metrics = platformContext.getMetrics();

        stateToDiskTime = metrics.getOrCreate(STATE_TO_DISK_TIME_CONFIG);
        writeStateToDiskTime = metrics.getOrCreate(WRITE_STATE_TO_DISK_TIME_CONFIG);
        totalNeverSignedDiskStates = metrics.getOrCreate(TOTAL_NEVER_SIGNED_DISK_STATES_CONFIG);
    }

    /**
     * Get a metric tracking the average time required to write a state to disk.
     *
     * @return the metric tracking the average time required to write a state to disk
     */
    @NonNull
    public RunningAverageMetric getWriteStateToDiskTimeMetric() {
        return writeStateToDiskTime;
    }

    /**
     * Get a metric tracking the average time required to perform all actions when saving a state to disk, i.e.
     * notifying listeners and cleaning up old states on disk.
     *
     * @return the metric tracking the average time required to perform all actions when saving a state to disk
     */
    @NonNull
    public RunningAverageMetric getStateToDiskTimeMetric() {
        return stateToDiskTime;
    }

    /**
     * Get a metric tracking the total number of unsigned states written to disk.
     *
     * @return the metric tracking the total number of unsigned states written to disk
     */
    @NonNull
    public Counter getTotalUnsignedDiskStatesMetric() {
        return totalNeverSignedDiskStates;
    }
}
