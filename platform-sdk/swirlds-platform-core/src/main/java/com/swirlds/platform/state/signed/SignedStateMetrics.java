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

package com.swirlds.platform.state.signed;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_3;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_15_3;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_16_2;

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.units.TimeUnit;

/**
 * Encapsulates various signed state metrics.
 */
public class SignedStateMetrics {

    private static final String CATEGORY = "platform";
    private static final String MILLISECONDS = TimeUnit.UNIT_MILLISECONDS.getAbbreviation();

    private static final RunningAverageMetric.Config UNSIGNED_STATES_CONFIG = new RunningAverageMetric.Config(
                    CATEGORY, "unsignedStates")
            .withDescription("Average Number Of Unsigned States Awaiting Signatures")
            .withUnit("count")
            .withFormat(FORMAT_10_2);
    private final RunningAverageMetric unsignedStates;

    private static final RunningAverageMetric.Config SIGNED_STATES_CONFIG = new RunningAverageMetric.Config(
                    CATEGORY, "signedStates")
            .withDescription("Average Number Of Signed States In the Signed State Manager")
            .withUnit("count")
            .withFormat(FORMAT_10_2);
    private final RunningAverageMetric signedStates;

    private static final RunningAverageMetric.Config AVERAGE_TIME_TO_FULLY_SIGN_STATE = new RunningAverageMetric.Config(
                    CATEGORY, "averageTimeToFullySignState")
            .withDescription("The average time spent waiting for enough state signatures to fully sign a state.")
            .withUnit(MILLISECONDS)
            .withFormat(FORMAT_10_2);
    private final RunningAverageMetric averageTimeToFullySignState;

    private static final Counter.Config TOTAL_NEVER_SIGNED_STATES_CONFIG = new Counter.Config(
                    CATEGORY, "totalNeverSignedStates")
            .withDescription("total number of states that did not receive enough signatures in the allowed time")
            .withUnit("count");
    private final Counter totalNeverSignedStates;

    private static final Counter.Config TOTAL_NEVER_SIGNED_DISK_STATES_CONFIG = new Counter.Config(
                    CATEGORY, "totalNeverSignedDiskStates")
            .withDescription(
                    "total number of disk-bound states that did not receive enough signatures in the allowed time")
            .withUnit("count");
    private final Counter totalNeverSignedDiskStates;

    private static final SpeedometerMetric.Config STATES_SIGNED_PER_SECOND_CONFIG = new SpeedometerMetric.Config(
                    CATEGORY, "statesSigned/sec")
            .withDescription("the number of states completely signed per second")
            .withFormat(FORMAT_16_2)
            .withUnit("hz");
    private final SpeedometerMetric statesSignedPerSecond;

    private static final SpeedometerMetric.Config STATE_SIGNATURES_GATHERED_PER_SECOND_CONFIG =
            new SpeedometerMetric.Config(CATEGORY, "stateSignaturesGathered/sec")
                    .withDescription("the number of state signatures gathered from other nodes per second")
                    .withFormat(FORMAT_16_2)
                    .withUnit("hz");
    private final SpeedometerMetric stateSignaturesGatheredPerSecond;

    private static final RunningAverageMetric.Config STATE_SIGNATURE_AGE_CONFIG = new RunningAverageMetric.Config(
                    CATEGORY, "stateSignatureAge")
            .withDescription("the average difference in round number between state "
                    + "signatures and the most recent immutable state. Negative numbers mean"
                    + "the are being received early, large positive numbers mean "
                    + "signatures are being received late.")
            .withFormat(FORMAT_10_3)
            .withUnit("rounds");
    private final RunningAverageMetric stateSignatureAge;

    private static final RunningAverageMetric.Config STATE_ARCHIVAL_TIME_AVG_CONFIG = new RunningAverageMetric.Config(
                    CATEGORY, "stateArchivalTimeAvg")
            .withDescription("avg time to archive a signed state (in milliseconds)")
            .withUnit(MILLISECONDS)
            .withFormat(FORMAT_15_3);
    private final RunningAverageMetric stateArchivalTimeAvg;

    private static final RunningAverageMetric.Config STATE_DELETION_QUEUE_AVG_CONFIG = new RunningAverageMetric.Config(
                    CATEGORY, "stateDeletionQueueAvg")
            .withDescription("avg length of the state deletion queue")
            .withFormat(FORMAT_15_3)
            .withUnit("count");
    private final RunningAverageMetric stateDeletionQueueAvg;

    private static final RunningAverageMetric.Config STATE_DELETION_TIME_AVG_CONFIG = new RunningAverageMetric.Config(
                    CATEGORY, "stateDeletionTimeAvg")
            .withDescription("avg time it takes to delete a signed state (in milliseconds)")
            .withUnit(MILLISECONDS)
            .withFormat(FORMAT_15_3);
    private final RunningAverageMetric stateDeletionTimeAvg;

    private static final RunningAverageMetric.Config STATE_HASHING_TIME_CONFIG = new RunningAverageMetric.Config(
                    CATEGORY, "sigStateHash")
            .withDescription("average time it takes to hash a SignedState (in milliseconds)")
            .withUnit(MILLISECONDS)
            .withFormat(FORMAT_10_3);
    private final RunningAverageMetric stateHashingTime;

    private static final RunningAverageMetric.Config WRITE_STATE_TO_DISK_TIME_CONFIG = new RunningAverageMetric.Config(
                    CATEGORY, "writeStateToDisk")
            .withDescription("average time it takes to write a SignedState to disk (in milliseconds)")
            .withUnit(MILLISECONDS)
            .withFormat(FORMAT_10_3);

    private final RunningAverageMetric writeStateToDiskTime;

    private static final RunningAverageMetric.Config STATE_TO_DISK_TIME_CONFIG = new RunningAverageMetric.Config(
                    CATEGORY, "stateToDisk")
            .withDescription("average time it takes to do perform all actions when writing a SignedState to disk "
                    + "(in milliseconds)")
            .withUnit(MILLISECONDS)
            .withFormat(FORMAT_10_3);
    private final RunningAverageMetric stateToDiskTime;

    /**
     * Get a metric tracking unsigned states.
     */
    public RunningAverageMetric getUnsignedStatesMetric() {
        return unsignedStates;
    }

    /**
     * Get a metric tracking signed states.
     */
    public RunningAverageMetric geSignedStatesMetric() {
        return signedStates;
    }

    /**
     * Get a metric tracking average state signing time in milliseconds.
     */
    public RunningAverageMetric getAverageTimeToFullySignStateMetric() {
        return averageTimeToFullySignState;
    }

    /**
     * Get a metric tracking the total number of unsigned states that were skipped.
     */
    public Counter getTotalUnsignedStatesMetric() {
        return totalNeverSignedStates;
    }

    /**
     * Get a metric tracking the total number of unsigned states written to disk that were skipped.
     */
    public Counter getTotalUnsignedDiskStatesMetric() {
        return totalNeverSignedDiskStates;
    }

    /**
     * Get a metric tracking the total number of states signed per second.
     */
    public SpeedometerMetric getStatesSignedPerSecondMetric() {
        return statesSignedPerSecond;
    }

    /**
     * Get a metric tracking the total number of state signatures gathered from other nodes per second.
     */
    public SpeedometerMetric getStateSignaturesGatheredPerSecondMetric() {
        return stateSignaturesGatheredPerSecond;
    }

    /**
     * Get a metric tracking the average time required to archive a state.
     */
    public RunningAverageMetric getStateArchivalTimeAvgMetric() {
        return stateArchivalTimeAvg;
    }

    /**
     * Get a metric tracking the average size of the signed state deletion/archive queue.
     */
    public RunningAverageMetric getStateDeletionQueueAvgMetric() {
        return stateDeletionQueueAvg;
    }

    /**
     * Get a metric tracking the average time to delete a signed state.
     */
    public RunningAverageMetric getStateDeletionTimeAvgMetric() {
        return stateDeletionTimeAvg;
    }

    /**
     * Get a metric tracking the average time required to hash a state.
     */
    public RunningAverageMetric getSignedStateHashingTimeMetric() {
        return stateHashingTime;
    }

    /**
     * Get a metric tracking the average time required to write a state to disk.
     */
    public RunningAverageMetric getWriteStateToDiskTimeMetric() {
        return writeStateToDiskTime;
    }

    /**
     * Get a metric tracking the average time required to perform all actions when saving a state to disk, i.e.
     * notifying listeners and cleaning up old states on disk.
     */
    public RunningAverageMetric getStateToDiskTimeMetric() {
        return stateToDiskTime;
    }

    /**
     * Get a metric tracking the average difference in round number between signature transactions and
     * the most recent immutable state.
     */
    public RunningAverageMetric getStateSignatureAge() {
        return stateSignatureAge;
    }

    /**
     * Register all metrics with a registry.
     *
     * @param metrics
     * 		a reference to the metrics-system
     */
    public SignedStateMetrics(final Metrics metrics) {
        unsignedStates = metrics.getOrCreate(UNSIGNED_STATES_CONFIG);
        averageTimeToFullySignState = metrics.getOrCreate(AVERAGE_TIME_TO_FULLY_SIGN_STATE);
        totalNeverSignedStates = metrics.getOrCreate(TOTAL_NEVER_SIGNED_STATES_CONFIG);
        totalNeverSignedDiskStates = metrics.getOrCreate(TOTAL_NEVER_SIGNED_DISK_STATES_CONFIG);
        statesSignedPerSecond = metrics.getOrCreate(STATES_SIGNED_PER_SECOND_CONFIG);
        stateSignaturesGatheredPerSecond = metrics.getOrCreate(STATE_SIGNATURES_GATHERED_PER_SECOND_CONFIG);
        stateArchivalTimeAvg = metrics.getOrCreate(STATE_ARCHIVAL_TIME_AVG_CONFIG);
        stateDeletionQueueAvg = metrics.getOrCreate(STATE_DELETION_QUEUE_AVG_CONFIG);
        stateDeletionTimeAvg = metrics.getOrCreate(STATE_DELETION_TIME_AVG_CONFIG);
        stateHashingTime = metrics.getOrCreate(STATE_HASHING_TIME_CONFIG);
        stateToDiskTime = metrics.getOrCreate(STATE_TO_DISK_TIME_CONFIG);
        writeStateToDiskTime = metrics.getOrCreate(WRITE_STATE_TO_DISK_TIME_CONFIG);
        stateSignatureAge = metrics.getOrCreate(STATE_SIGNATURE_AGE_CONFIG);
        signedStates = metrics.getOrCreate(SIGNED_STATES_CONFIG);
    }
}
