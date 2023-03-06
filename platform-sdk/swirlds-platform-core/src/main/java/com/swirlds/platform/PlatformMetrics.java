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
import com.swirlds.platform.eventhandling.SwirldStateSingleTransactionPool;
import com.swirlds.platform.state.SwirldStateManagerDouble;
import com.swirlds.platform.stats.AverageAndMax;
import com.swirlds.platform.stats.AverageStat;

/**
 * Collection of metrics related to the platform
 */
public class PlatformMetrics {

    private static final SpeedometerMetric.Config INTERRUPTED_CALL_SYNCS_PER_SECOND_CONFIG =
            new SpeedometerMetric.Config(PLATFORM_CATEGORY, "icSync/sec")
                    .withDescription("(interrupted call syncs) syncs interrupted per second initiated by this member")
                    .withFormat(FORMAT_14_7);
    private final SpeedometerMetric interruptedCallSyncsPerSecond;

    private static final SpeedometerMetric.Config INTERRUPTED_REC_SYNCS_PER_SECOND_CONFIG =
            new SpeedometerMetric.Config(PLATFORM_CATEGORY, "irSync/sec")
                    .withDescription(
                            "(interrupted receive syncs) syncs interrupted per second initiated by other " + "member")
                    .withFormat(FORMAT_14_7);
    private final SpeedometerMetric interruptedRecSyncsPerSecond;

    private static final RunningAverageMetric.Config AVG_SELF_ID_CONFIG = new RunningAverageMetric.Config(
                    INFO_CATEGORY, "memberID")
            .withDescription("ID number of this member")
            .withFormat(FORMAT_3_0);
    private final RunningAverageMetric avgSelfId;

    private static final RunningAverageMetric.Config AVG_NUM_MEMBERS_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "members")
            .withDescription("total number of members participating")
            .withFormat(FORMAT_10_0);
    private final RunningAverageMetric avgNumMembers;

    private static final RunningAverageMetric.Config AVG_WRITE_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "write")
            .withDescription("the app claimed to log statistics every this many milliseconds")
            .withFormat(FORMAT_8_0);
    private final RunningAverageMetric avgWrite;

    private static final RunningAverageMetric.Config AVG_SIM_CALL_SYNCS_MAX_CONFIG = new RunningAverageMetric.Config(
                    INTERNAL_CATEGORY, "simCallSyncsMax")
            .withDescription("max number of syncs this can initiate simultaneously")
            .withFormat(FORMAT_2_0);
    private final RunningAverageMetric avgSimCallSyncsMax;

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

    private static final RunningAverageMetric.Config EVENT_STREAM_QUEUE_SIZE_CONFIG = new RunningAverageMetric.Config(
                    INFO_CATEGORY, "eventStreamQueueSize")
            .withDescription("size of the queue from which we take events and write to EventStream file")
            .withFormat(FORMAT_13_0);
    private final RunningAverageMetric eventStreamQueueSize;

    private static final RunningAverageMetric.Config HASH_QUEUE_SIZE_CONFIG = new RunningAverageMetric.Config(
                    INFO_CATEGORY, "hashQueueSize")
            .withDescription("size of the queue from which we take events, calculate Hash and RunningHash")
            .withFormat(FORMAT_13_0);
    private final RunningAverageMetric hashQueueSize;

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

    private static final FunctionGauge.Config<Integer> TLS_CONFIG = new FunctionGauge.Config<>(
                    INFO_CATEGORY,
                    "TLS",
                    Integer.class,
                    () -> Settings.getInstance().isUseTLS() ? 1 : 0)
            .withDescription("1 if using TLS, 0 if not")
            .withFormat("%6d");

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

        interruptedCallSyncsPerSecond = metrics.getOrCreate(INTERRUPTED_CALL_SYNCS_PER_SECOND_CONFIG);
        interruptedRecSyncsPerSecond = metrics.getOrCreate(INTERRUPTED_REC_SYNCS_PER_SECOND_CONFIG);
        avgSelfId = metrics.getOrCreate(AVG_SELF_ID_CONFIG);
        avgNumMembers = metrics.getOrCreate(AVG_NUM_MEMBERS_CONFIG);
        avgWrite = metrics.getOrCreate(AVG_WRITE_CONFIG);
        avgSimCallSyncsMax = metrics.getOrCreate(AVG_SIM_CALL_SYNCS_MAX_CONFIG);
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
        eventStreamQueueSize = metrics.getOrCreate(EVENT_STREAM_QUEUE_SIZE_CONFIG);
        hashQueueSize = metrics.getOrCreate(HASH_QUEUE_SIZE_CONFIG);
        avgStateToHashSignDepth = metrics.getOrCreate(AVG_STATE_TO_HASH_SIGN_DEPTH_CONFIG);
        avgRoundSupermajority = metrics.getOrCreate(AVG_ROUND_SUPERMAJORITY_CONFIG);
        avgEventsInMem = metrics.getOrCreate(AVG_EVENTS_IN_MEM_CONFIG);
        metrics.getOrCreate(TLS_CONFIG);
        sleep1perSecond = metrics.getOrCreate(SLEEP_1_PER_SECOND_CONFIG);

        addFunctionGauges(metrics);
    }

    private void addFunctionGauges(final Metrics metrics) {
        metrics.getOrCreate(new FunctionGauge.Config<>(INFO_CATEGORY, "name", String.class, this::getMemberName)
                .withDescription("name of this member")
                .withFormat("%8s"));
        metrics.getOrCreate(
                new FunctionGauge.Config<>(INFO_CATEGORY, "lastGen", Long.class, this::getLastEventGenerationNumber)
                        .withDescription("last event generation number by me")
                        .withFormat("%d"));
        metrics.getOrCreate(
                new FunctionGauge.Config<>(INFO_CATEGORY, "transEvent", Integer.class, this::getTransEventSize)
                        .withDescription("transEvent queue size")
                        .withFormat("%d"));
        metrics.getOrCreate(new FunctionGauge.Config<>(
                        INFO_CATEGORY, "priorityTransEvent", Integer.class, this::getPriorityTransEventSize)
                .withDescription("priorityTransEvent queue size")
                .withFormat("%d"));
        metrics.getOrCreate(new FunctionGauge.Config<>(INFO_CATEGORY, "transCons", Long.class, this::getTransConsSize)
                .withDescription("transCons queue size")
                .withFormat("%d"));
        metrics.getOrCreate(new FunctionGauge.Config<>(
                        INTERNAL_CATEGORY,
                        "isEvFrozen",
                        Boolean.class,
                        () -> platform.getFreezeManager().isEventCreationFrozen()
                                || platform.getStartUpEventFrozenManager().isEventCreationPausedAfterStartUp())
                .withDescription("isEventCreationFrozen")
                .withFormat("%b"));
        metrics.getOrCreate(new FunctionGauge.Config<>(
                        INTERNAL_CATEGORY,
                        "isStrongMinorityInMaxRound",
                        Boolean.class,
                        this::isStrongMinorityInMaxRound)
                .withFormat("%b"));
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

    private String getMemberName() {
        if (platform.isMirrorNode()) {
            return "Mirror-" + platform.getSelfId().getId();
        }
        return platform.getAddressBook()
                .getAddress(platform.getSelfId().getId())
                .getSelfName();
    }

    private long getLastEventGenerationNumber() {
        if (platform.isMirrorNode()) {
            return -1L;
        }
        return platform.getLastGen(platform.getSelfId().getId());
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

    private long getTransConsSize() {
        if (platform.getSwirldStateManager() == null
                || platform.getSwirldStateManager() instanceof SwirldStateManagerDouble) {
            return 0;
        }
        return ((SwirldStateSingleTransactionPool)
                        platform.getSwirldStateManager().getTransactionPool())
                .getConsSize();
    }

    private Boolean isStrongMinorityInMaxRound() {
        if (platform.isMirrorNode()) {
            return false;
        }
        return platform.getCriticalQuorum()
                .isInCriticalQuorum(platform.getSelfId().getId());
    }

    void update() {
        interruptedCallSyncsPerSecond.update(0);
        interruptedRecSyncsPerSecond.update(0);
        sleep1perSecond.update(0);
        avgSelfId.update(platform.getSelfId().getId());
        avgNumMembers.update(platform.getAddressBook().getSize());
        avgWrite.update(Settings.getInstance().getCsvWriteFrequency());
        avgSimCallSyncsMax.update(Settings.getInstance().getMaxOutgoingSyncs());
        avgQ1PreConsEvents.update(platform.getPreConsensusHandler().getQueueSize());
        avgQ2ConsEvents.update(platform.getConsensusHandler().getNumEventsInQueue());
        avgQSignedStateEvents.update(platform.getConsensusHandler().getSignedStateEventsSize());
        avgSimSyncs.update(platform.getSimultaneousSyncThrottle().getNumSyncs());
        avgSimListenSyncs.update(platform.getSimultaneousSyncThrottle().getNumListenerSyncs());
        eventStreamQueueSize.update(
                platform.getEventStreamManager() != null
                        ? platform.getEventStreamManager().getEventStreamingQueueSize()
                        : 0);
        hashQueueSize.update(
                platform.getEventStreamManager() != null
                        ? platform.getEventStreamManager().getHashQueueSize()
                        : 0);
        avgStateToHashSignDepth.update(platform.getConsensusHandler().getStateToHashSignSize());
        avgRoundSupermajority.update(platform.getStateManagementComponent().getLastCompleteRound());
        avgEventsInMem.update(EventCounter.getNumEventsInMemory());
    }

    public void incrementSleep1perSecond() {
        sleep1perSecond.cycle();
    }

    public void incrementInterruptedCallSyncs() {
        interruptedCallSyncsPerSecond.cycle();
    }

    public void incrementInterruptedRecSyncs() {
        interruptedRecSyncsPerSecond.cycle();
    }
}
