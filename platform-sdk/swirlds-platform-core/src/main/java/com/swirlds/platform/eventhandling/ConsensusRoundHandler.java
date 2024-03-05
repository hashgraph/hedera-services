/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.eventhandling;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.platform.eventhandling.ConsensusRoundHandlerPhase.CREATING_SIGNED_STATE;
import static com.swirlds.platform.eventhandling.ConsensusRoundHandlerPhase.GETTING_STATE_TO_SIGN;
import static com.swirlds.platform.eventhandling.ConsensusRoundHandlerPhase.HANDLING_CONSENSUS_ROUND;
import static com.swirlds.platform.eventhandling.ConsensusRoundHandlerPhase.IDLE;
import static com.swirlds.platform.eventhandling.ConsensusRoundHandlerPhase.SETTING_EVENT_CONSENSUS_DATA;
import static com.swirlds.platform.eventhandling.ConsensusRoundHandlerPhase.UPDATING_PLATFORM_STATE;
import static com.swirlds.platform.eventhandling.ConsensusRoundHandlerPhase.UPDATING_PLATFORM_STATE_RUNNING_HASH;
import static com.swirlds.platform.eventhandling.ConsensusRoundHandlerPhase.WAITING_FOR_EVENT_DURABILITY;
import static com.swirlds.platform.eventhandling.ConsensusRoundHandlerPhase.WAITING_FOR_PREHANDLE;

import com.swirlds.base.function.CheckedConsumer;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.stream.RunningEventHashUpdate;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.RoundHandlingMetrics;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.system.status.actions.FreezePeriodEnteredAction;
import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Applies transactions from consensus rounds to the state
 */
public class ConsensusRoundHandler {

    private static final Logger logger = LogManager.getLogger(ConsensusRoundHandler.class);

    /**
     * The name of the thread that handles transactions. For the sake of the app, to allow logging.
     */
    public static final String TRANSACTION_HANDLING_THREAD_NAME = "<scheduler consensusRoundHandler>";

    /**
     * The class responsible for all interactions with the swirld state
     */
    private final SwirldStateManager swirldStateManager;

    private final RoundHandlingMetrics handlerMetrics;

    /**
     * Whether a round in a freeze period has been received. This may never be reset to false after it is set to true.
     */
    private boolean freezeRoundReceived = false;

    /**
     * a RunningHash object which calculates running hash of all consensus events so far with their transactions handled
     * <p>
     * Future work: this will need to be changed to a proper null hash when this component is updated to handle empty
     * rounds, since a hash of all 0s isn't valid when deserializing a state. In the current system, this hash is
     * always overwritten before being put into the state, so it doesn't matter that it starts as all 0s.
     */
    private RunningHash consensusEventsRunningHash =
            new RunningHash(new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]));

    /**
     * A queue that accepts signed states and rounds for hashing and signature collection.
     */
    private final BlockingQueue<StateAndRound> stateHashSignQueue;

    /**
     * Enables submitting platform status actions.
     */
    private final StatusActionSubmitter statusActionSubmitter;

    private final SoftwareVersion softwareVersion;

    /**
     * A method that blocks until an event becomes durable.
     */
    private final CheckedConsumer<GossipEvent, InterruptedException> waitForEventDurability;

    /**
     * The number of non-ancient rounds.
     */
    private final int roundsNonAncient;

    private final PlatformContext platformContext;

    private static final RunningAverageMetric.Config AVG_STATE_TO_HASH_SIGN_DEPTH_CONFIG =
            new RunningAverageMetric.Config(Metrics.INTERNAL_CATEGORY, "stateToHashSignDepth")
                    .withDescription("average depth of the stateToHashSign queue (number of SignedStates)")
                    .withUnit("count");

    /**
     * Constructor
     *
     * @param platformContext        contains various platform utilities
     * @param swirldStateManager     the swirld state manager to send events to
     * @param stateHashSignQueue     the queue thread that handles hashing and collecting signatures of new
     *                               self-signed states
     * @param waitForEventDurability a method that blocks until an event becomes durable
     * @param statusActionSubmitter  enables submitting of platform status actions
     * @param softwareVersion        the current version of the software
     */
    public ConsensusRoundHandler(
            @NonNull final PlatformContext platformContext,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final BlockingQueue<StateAndRound> stateHashSignQueue,
            @NonNull final CheckedConsumer<GossipEvent, InterruptedException> waitForEventDurability,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final SoftwareVersion softwareVersion) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.swirldStateManager = Objects.requireNonNull(swirldStateManager);
        this.stateHashSignQueue = Objects.requireNonNull(stateHashSignQueue);
        this.waitForEventDurability = Objects.requireNonNull(waitForEventDurability);
        this.statusActionSubmitter = Objects.requireNonNull(statusActionSubmitter);
        this.softwareVersion = Objects.requireNonNull(softwareVersion);

        this.roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();
        this.handlerMetrics = new RoundHandlingMetrics(platformContext);

        // Future work: This metric should be moved to a suitable component once the stateHashSignQueue is migrated
        // to the framework
        final RunningAverageMetric avgStateToHashSignDepth =
                platformContext.getMetrics().getOrCreate(AVG_STATE_TO_HASH_SIGN_DEPTH_CONFIG);
        platformContext.getMetrics().addUpdater(() -> avgStateToHashSignDepth.update(stateHashSignQueue.size()));
    }

    /**
     * Update the consensus event running hash
     * <p>
     * Future work: in the current system, it isn't actually necessary to update this running hash when a new state
     * is loaded. The running hash will be overwritten anyway by the first round that contains events, before the
     * initial hash is ever set in the state. This method is being called anyway, though, since it will be a necessary
     * workflow in the future to support handling of empty rounds by this component.
     *
     * @param runningHashUpdate the update to the running hash
     */
    public void updateRunningHash(@NonNull final RunningEventHashUpdate runningHashUpdate) {
        consensusEventsRunningHash = new RunningHash(runningHashUpdate.runningEventHash());
    }

    /**
     * Applies the transactions in the consensus round to the state
     *
     * @param consensusRound the consensus round to apply
     */
    public void handleConsensusRound(@NonNull final ConsensusRound consensusRound) {
        // consensus rounds with no events are ignored
        if (consensusRound.isEmpty()) {
            // Future work: the long term goal is for empty rounds to not be ignored here. For now, the way that the
            // running hash of consensus events is calculated by the EventStreamManager prevents that from being
            // possible.
            return;
        }

        // Once there is a saved state created in a freeze period, we will never apply any more rounds to the state.
        if (freezeRoundReceived) {
            return;
        }

        if (swirldStateManager.isInFreezePeriod(consensusRound.getConsensusTimestamp())) {
            statusActionSubmitter.submitStatusAction(new FreezePeriodEnteredAction(consensusRound.getRoundNum()));
            freezeRoundReceived = true;
        }

        handlerMetrics.recordEventsPerRound(consensusRound.getNumEvents());
        handlerMetrics.recordConsensusTime(consensusRound.getConsensusTimestamp());

        try {
            handlerMetrics.setPhase(WAITING_FOR_EVENT_DURABILITY);
            waitForEventDurability.accept(consensusRound.getKeystoneEvent().getBaseEvent());

            handlerMetrics.setPhase(SETTING_EVENT_CONSENSUS_DATA);
            for (final EventImpl event : consensusRound.getConsensusEvents()) {
                event.consensusReached();
            }

            handlerMetrics.setPhase(UPDATING_PLATFORM_STATE);
            // it is important to update the platform state before handling the consensus round, since the platform
            // state is passed into the application handle method, and should contain the data for the current round
            updatePlatformState(consensusRound);

            handlerMetrics.setPhase(WAITING_FOR_PREHANDLE);
            consensusRound.forEach(event -> ((EventImpl) event).getBaseEvent().awaitPrehandleCompletion());

            handlerMetrics.setPhase(HANDLING_CONSENSUS_ROUND);
            swirldStateManager.handleConsensusRound(consensusRound);

            handlerMetrics.setPhase(UPDATING_PLATFORM_STATE_RUNNING_HASH);
            updatePlatformStateRunningHash(consensusRound);

            createSignedState(consensusRound);
        } catch (final InterruptedException e) {
            logger.error(EXCEPTION.getMarker(), "handleConsensusRound interrupted");
            Thread.currentThread().interrupt();
        } finally {
            handlerMetrics.setPhase(IDLE);
        }
    }

    /**
     * Populate the {@link com.swirlds.platform.state.PlatformState PlatformState} with all needed data for this round.
     *
     * @param round the consensus round
     */
    private void updatePlatformState(@NonNull final ConsensusRound round) {
        final PlatformState platformState =
                swirldStateManager.getConsensusState().getPlatformState();

        platformState.setRound(round.getRoundNum());
        platformState.setConsensusTimestamp(round.getConsensusTimestamp());
        platformState.setCreationSoftwareVersion(softwareVersion);
        platformState.setRoundsNonAncient(roundsNonAncient);
        platformState.setSnapshot(round.getSnapshot());
    }

    /**
     * Update the running hash of the consensus events in the platform state
     * <p>
     * This method is called after the consensus round has been applied to the state, to minimize the chance that
     * we have to wait for the running hash to finish being calculated.
     *
     * @param round the consensus round
     * @throws InterruptedException if this thread is interrupted
     */
    private void updatePlatformStateRunningHash(@NonNull final ConsensusRound round) throws InterruptedException {
        final PlatformState platformState =
                swirldStateManager.getConsensusState().getPlatformState();

        // Update the running hash object. If there are no events, the running hash does not change.
        // Future work: this is a redundant check, since empty rounds are currently ignored entirely. The check is here
        // anyway, for when that changes in the future.
        if (!round.isEmpty()) {
            consensusEventsRunningHash = round.getConsensusEvents().getLast().getRunningHash();
        }

        final Hash runningHash = consensusEventsRunningHash.getFutureHash().getAndRethrow();
        platformState.setRunningEventHash(runningHash);
    }

    /**
     * Create a signed state
     *
     * @param consensusRound the consensus round that resulted in the state being created
     * @throws InterruptedException if this thread is interrupted
     */
    private void createSignedState(@NonNull final ConsensusRound consensusRound) throws InterruptedException {
        if (freezeRoundReceived) {
            // Let the swirld state manager know we are about to write the saved state for the freeze period
            swirldStateManager.savedStateInFreezePeriod();
        }

        handlerMetrics.setPhase(GETTING_STATE_TO_SIGN);
        final State immutableStateCons = swirldStateManager.getStateForSigning();

        handlerMetrics.setPhase(CREATING_SIGNED_STATE);
        final SignedState signedState = new SignedState(
                platformContext, immutableStateCons, "ConsensusRoundHandler.createSignedState()", freezeRoundReceived);

        stateHashSignQueue.put(
                new StateAndRound(signedState.reserve("ConsensusRoundHandler.createSignedState()"), consensusRound));
    }
}
