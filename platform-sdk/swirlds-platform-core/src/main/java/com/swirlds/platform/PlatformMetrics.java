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

package com.swirlds.platform;

import static com.swirlds.common.metrics.FloatFormats.*;
import static com.swirlds.common.metrics.Metrics.INFO_CATEGORY;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.PlatformStatNames;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.event.EventCounter;
import com.swirlds.platform.stats.AverageAndMax;
import com.swirlds.platform.stats.AverageStat;

/**
 * Collection of metrics related to the platform
 */
public class PlatformMetrics {

    private static final RunningAverageMetric.Config AVG_Q_SIGNED_STATE_EVENTS_CONFIG = new RunningAverageMetric.Config(
                    INTERNAL_CATEGORY, "queueSignedStateEvents")
            .withDescription("number of handled consensus events that will be part of the next signed state")
            .withFormat(FORMAT_10_1);
    private final RunningAverageMetric avgQSignedStateEvents;

    private static final RunningAverageMetric.Config AVG_SIM_SYNCS_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "simSyncs")
            .withDescription("avg number of simultaneous syncs happening at any given time")
            .withFormat(FORMAT_9_6);
    private final RunningAverageMetric avgSimSyncs;

    private static final RunningAverageMetric.Config AVG_SIM_LISTEN_SYNCS_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "simListenSyncs")
            .withDescription("avg number of simultaneous listening syncs happening at any given time")
            .withFormat(FORMAT_9_6);
    private final RunningAverageMetric avgSimListenSyncs;

    private static final RunningAverageMetric.Config AVG_STATE_TO_HASH_SIGN_DEPTH_CONFIG =
            new RunningAverageMetric.Config(INTERNAL_CATEGORY, "stateToHashSignDepth")
                    .withDescription("average depth of the stateToHashSign queue (number of SignedStates)")
                    .withFormat(FORMAT_16_2);
    private final RunningAverageMetric avgStateToHashSignDepth;

    private static final RunningAverageMetric.Config AVG_ROUND_SUPERMAJORITY_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "roundSup")
            .withDescription("latest round with state signed by a supermajority")
            .withFormat(FORMAT_10_0);
    private final RunningAverageMetric avgRoundSupermajority;

    private static final RunningAverageMetric.Config AVG_EVENTS_IN_MEM_CONFIG = new RunningAverageMetric.Config(
                    INTERNAL_CATEGORY, "eventsInMem")
            .withDescription("total number of events in memory, for all members on the local machine together")
            .withFormat(FORMAT_16_2);
    private final RunningAverageMetric avgEventsInMem;

    private static final SpeedometerMetric.Config SLEEP_1_PER_SECOND_CONFIG = new SpeedometerMetric.Config(
                    INTERNAL_CATEGORY, "sleep1/sec")
            .withDescription("sleeps per second because caller thread had too many failed connects")
            .withFormat(FORMAT_9_6);
    private final SpeedometerMetric sleep1perSecond;

    private final AverageAndMax avgQ1PreConsEvents;
    private final AverageAndMax avgQ2ConsEvents;
    private final SwirldsPlatform platform;

    /**
     * Constructor of {@code PlatformMetrics}
     *
     * @param platform
     * 		a reference to the {@link SwirldsPlatform}
     */
    public PlatformMetrics(final SwirldsPlatform platform) {
        this.platform = CommonUtils.throwArgNull(platform, "platform");
        final Metrics metrics = platform.getContext().getMetrics();

        avgQ1PreConsEvents = new AverageAndMax(
                metrics,
                INTERNAL_CATEGORY,
                PlatformStatNames.PRE_CONSENSUS_QUEUE_SIZE,
                "average number of events in the pre-consensus queue (q1) waiting to be handled",
                FORMAT_10_3,
                AverageStat.WEIGHT_VOLATILE);
        avgQ2ConsEvents = new AverageAndMax(
                metrics,
                INTERNAL_CATEGORY,
                PlatformStatNames.CONSENSUS_QUEUE_SIZE,
                "average number of events in the consensus queue (q2) waiting to be handled",
                FORMAT_10_3,
                AverageStat.WEIGHT_VOLATILE);
        avgQSignedStateEvents = metrics.getOrCreate(AVG_Q_SIGNED_STATE_EVENTS_CONFIG);
        avgSimSyncs = metrics.getOrCreate(AVG_SIM_SYNCS_CONFIG);
        avgSimListenSyncs = metrics.getOrCreate(AVG_SIM_LISTEN_SYNCS_CONFIG);
        avgStateToHashSignDepth = metrics.getOrCreate(AVG_STATE_TO_HASH_SIGN_DEPTH_CONFIG);
        avgRoundSupermajority = metrics.getOrCreate(AVG_ROUND_SUPERMAJORITY_CONFIG);
        avgEventsInMem = metrics.getOrCreate(AVG_EVENTS_IN_MEM_CONFIG);
        sleep1perSecond = metrics.getOrCreate(SLEEP_1_PER_SECOND_CONFIG);

        addFunctionGauges(metrics);
    }

    private void addFunctionGauges(final Metrics metrics) {
        metrics.getOrCreate(
                new FunctionGauge.Config<>(INFO_CATEGORY, "transEvent", Integer.class, this::getTransEventSize)
                        .withDescription("transEvent queue size")
                        .withFormat("%d"));
        metrics.getOrCreate(new FunctionGauge.Config<>(
                        INFO_CATEGORY, "priorityTransEvent", Integer.class, this::getPriorityTransEventSize)
                .withDescription("priorityTransEvent queue size")
                .withFormat("%d"));
        metrics.getOrCreate(new FunctionGauge.Config<>(
                        INTERNAL_CATEGORY,
                        "hasFallenBehind",
                        Object.class,
                        () -> platform.getSyncManager() == null
                                ? 0
                                : platform.getSyncManager().hasFallenBehind())
                .withFormat("%b"));
        metrics.getOrCreate(new FunctionGauge.Config<>(
                        INTERNAL_CATEGORY,
                        "numReportFallenBehind",
                        Integer.class,
                        () -> platform.getSyncManager() == null
                                ? 0
                                : platform.getSyncManager().numReportedFallenBehind())
                .withFormat("%d"));
    }

    private int getTransEventSize() {
        if (platform.getSwirldStateManager() == null
                || platform.getSwirldStateManager().getTransactionPool() == null) {
            return 0;
        }
        return platform.getSwirldStateManager().getTransactionPool().getTransEventSize();
    }

    private int getPriorityTransEventSize() {
        if (platform.getSwirldStateManager() == null
                || platform.getSwirldStateManager().getTransactionPool() == null) {
            return 0;
        }
        return platform.getSwirldStateManager().getTransactionPool().getPriorityTransEventSize();
    }

    void update() {
        sleep1perSecond.update(0);
        avgQ1PreConsEvents.update(platform.getPreConsensusHandler().getQueueSize());
        avgQ2ConsEvents.update(platform.getConsensusHandler().getNumEventsInQueue());
        avgQSignedStateEvents.update(platform.getConsensusHandler().getSignedStateEventsSize());
        avgSimSyncs.update(platform.getSimultaneousSyncThrottle().getNumSyncs());
        avgSimListenSyncs.update(platform.getSimultaneousSyncThrottle().getNumListenerSyncs());
        avgStateToHashSignDepth.update(platform.getConsensusHandler().getStateToHashSignSize());
        avgRoundSupermajority.update(platform.getStateManagementComponent().getLastCompleteRound());
        avgEventsInMem.update(EventCounter.getNumEventsInMemory());
    }

    public void incrementSleep1perSecond() {
        sleep1perSecond.cycle();
    }
}
