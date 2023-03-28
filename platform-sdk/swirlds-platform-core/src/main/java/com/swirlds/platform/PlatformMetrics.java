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

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static com.swirlds.common.metrics.FloatFormats.*;
import static com.swirlds.common.metrics.Metrics.INFO_CATEGORY;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.PlatformStatNames;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.event.EventCounter;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.PreConsensusEventHandler;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.stats.AverageAndMax;
import com.swirlds.platform.stats.AverageStat;
import com.swirlds.platform.sync.SimultaneousSyncThrottle;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

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
    private final NodeId selfId;
    private final EventMapper eventMapper;
    private final SwirldStateManager swirldStateManager;
    private final CriticalQuorum criticalQuorum;
    private final AddressBook addressBook;
    private final PreConsensusEventHandler preConsensusEventHandler;
    private final ConsensusRoundHandler consensusRoundHandler;
    private final SimultaneousSyncThrottle simultaneousSyncThrottle;
    private final EventStreamManager<EventImpl> eventStreamManager;
    private final StateManagementComponent stateManagementComponent;

    /**
     * Constructor of {@code PlatformMetrics}
     *
     */
    public PlatformMetrics(
            @NonNull final Metrics metrics,
            @NonNull final NodeId selfId,
            @NonNull final FreezeManager freezeManager,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager,
            @Nullable final SyncManagerImpl syncManager,
            @NonNull final EventMapper eventMapper,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final CriticalQuorum criticalQuorum,
            @NonNull final AddressBook addressBook,
            @NonNull final PreConsensusEventHandler preConsensusEventHandler,
            @NonNull final ConsensusRoundHandler consensusRoundHandler,
            @NonNull final SimultaneousSyncThrottle simultaneousSyncThrottle,
            @NonNull final EventStreamManager<EventImpl> eventStreamManager,
            @NonNull final StateManagementComponent stateManagementComponent) {

        this.selfId = throwArgNull(selfId, "selfId");
        this.eventMapper = throwArgNull(eventMapper, "eventMapper");
        this.swirldStateManager = throwArgNull(swirldStateManager, "swirldStateManager");
        this.criticalQuorum = throwArgNull(criticalQuorum, "criticalQuorum");
        this.addressBook = throwArgNull(addressBook, "addressBook");
        this.preConsensusEventHandler = throwArgNull(preConsensusEventHandler, "preConsensusEventHandler");
        this.consensusRoundHandler = throwArgNull(consensusRoundHandler, "consensusRoundHandler");
        this.simultaneousSyncThrottle = throwArgNull(simultaneousSyncThrottle, "simultaneousSyncThrottle");
        this.eventStreamManager = throwArgNull(eventStreamManager, "eventStreamManager");
        this.stateManagementComponent = throwArgNull(stateManagementComponent, "stateManagementComponent");

        throwArgNull(metrics, "metrics");
        throwArgNull(freezeManager, "freezeManager");
        throwArgNull(startUpEventFrozenManager, "startUpEventFrozenManager");

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

        addFunctionGauges(metrics, freezeManager, startUpEventFrozenManager, syncManager);
    }

    private void addFunctionGauges(
            @NonNull final Metrics metrics,
            @NonNull FreezeManager freezeManager,
            @NonNull StartUpEventFrozenManager startUpEventFrozenManager,
            @Nullable SyncManagerImpl syncManager) {

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
        metrics.getOrCreate(new FunctionGauge.Config<>(
                        INTERNAL_CATEGORY,
                        "isEvFrozen",
                        Boolean.class,
                        () -> freezeManager.isEventCreationFrozen()
                                || startUpEventFrozenManager.isEventCreationPausedAfterStartUp())
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
                        () -> syncManager == null ? 0 : syncManager.hasFallenBehind())
                .withFormat("%b"));
        metrics.getOrCreate(new FunctionGauge.Config<>(
                        INTERNAL_CATEGORY,
                        "numReportFallenBehind",
                        Integer.class,
                        () -> syncManager == null ? 0 : syncManager.numReportedFallenBehind())
                .withFormat("%d"));
    }

    private String getMemberName() {
        if (selfId.isMirror()) {
            return "Mirror-" + selfId.getId();
        }
        return Long.toString(selfId.getId());
    }

    private long getLastEventGenerationNumber() {
        if (selfId.isMirror()) {
            return -1L;
        }
        return eventMapper.getHighestGenerationNumber(selfId.getId());
    }

    private int getTransEventSize() {
        return swirldStateManager.getTransactionPool().getTransEventSize();
    }

    private int getPriorityTransEventSize() {
        return swirldStateManager.getTransactionPool().getPriorityTransEventSize();
    }

    private Boolean isStrongMinorityInMaxRound() {
        if (selfId.isMirror()) {
            return false;
        }
        return criticalQuorum.isInCriticalQuorum(selfId.getId());
    }

    void update() {
        interruptedCallSyncsPerSecond.update(0);
        interruptedRecSyncsPerSecond.update(0);
        sleep1perSecond.update(0);
        avgSelfId.update(selfId.getId());
        avgNumMembers.update(addressBook.getSize());
        avgWrite.update(Settings.getInstance().getCsvWriteFrequency());
        avgSimCallSyncsMax.update(Settings.getInstance().getMaxOutgoingSyncs());
        avgQ1PreConsEvents.update(preConsensusEventHandler.getQueueSize());
        avgQ2ConsEvents.update(consensusRoundHandler.getNumEventsInQueue());
        avgQSignedStateEvents.update(consensusRoundHandler.getSignedStateEventsSize());
        avgSimSyncs.update(simultaneousSyncThrottle.getNumSyncs());
        avgSimListenSyncs.update(simultaneousSyncThrottle.getNumListenerSyncs());
        eventStreamQueueSize.update(eventStreamManager.getEventStreamingQueueSize());
        hashQueueSize.update(eventStreamManager.getHashQueueSize());
        avgStateToHashSignDepth.update(consensusRoundHandler.getStateToHashSignSize());
        avgRoundSupermajority.update(stateManagementComponent.getLastCompleteRound());
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
