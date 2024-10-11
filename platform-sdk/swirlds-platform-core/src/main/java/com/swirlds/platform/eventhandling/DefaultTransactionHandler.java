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
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.platform.eventhandling.TransactionHandlerPhase.CREATING_SIGNED_STATE;
import static com.swirlds.platform.eventhandling.TransactionHandlerPhase.GETTING_STATE_TO_SIGN;
import static com.swirlds.platform.eventhandling.TransactionHandlerPhase.HANDLING_CONSENSUS_ROUND;
import static com.swirlds.platform.eventhandling.TransactionHandlerPhase.IDLE;
import static com.swirlds.platform.eventhandling.TransactionHandlerPhase.SETTING_EVENT_CONSENSUS_DATA;
import static com.swirlds.platform.eventhandling.TransactionHandlerPhase.UPDATING_PLATFORM_STATE;
import static com.swirlds.platform.eventhandling.TransactionHandlerPhase.UPDATING_PLATFORM_STATE_RUNNING_HASH;
import static com.swirlds.platform.eventhandling.TransactionHandlerPhase.WAITING_FOR_PREHANDLE;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.RunningEventHashOverride;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.metrics.RoundHandlingMetrics;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.system.status.actions.FreezePeriodEnteredAction;
import com.swirlds.platform.wiring.PlatformSchedulersConfig;
import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A standard implementation of {@link TransactionHandler}.
 */
public class DefaultTransactionHandler implements TransactionHandler {

    private static final Logger logger = LogManager.getLogger(DefaultTransactionHandler.class);

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
     * The legacy running event hash (used by the soon-to-be-retired consensus event stream) from the previous round. We
     * need to save this here because of a quirk in the way the CES handles empty rounds. This legacy hash is always
     * taken from the last consensus event when a round reaches consensus, which means that when a round has zero events
     * we need to reuse the previous round's hash.
     */
    private Hash previousRoundLegacyRunningEventHash;

    /**
     * Enables submitting platform status actions.
     */
    private final StatusActionSubmitter statusActionSubmitter;

    private final SoftwareVersion softwareVersion;

    /**
     * The number of non-ancient rounds.
     */
    private final int roundsNonAncient;

    private final PlatformContext platformContext;

    /**
     * If true then write the legacy running event hash each round.
     */
    private final boolean writeLegacyRunningEventHash;

    /**
     * If true then wait for application transactions to be prehandled before handling the consensus round.
     */
    private final boolean waitForPrehandle;

    /**
     * Constructor
     *
     * @param platformContext       contains various platform utilities
     * @param swirldStateManager    the swirld state manager to send events to
     * @param statusActionSubmitter enables submitting of platform status actions
     * @param softwareVersion       the current version of the software
     */
    public DefaultTransactionHandler(
            @NonNull final PlatformContext platformContext,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final SoftwareVersion softwareVersion) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.swirldStateManager = Objects.requireNonNull(swirldStateManager);
        this.statusActionSubmitter = Objects.requireNonNull(statusActionSubmitter);
        this.softwareVersion = Objects.requireNonNull(softwareVersion);

        this.roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();
        this.handlerMetrics = new RoundHandlingMetrics(platformContext);

        previousRoundLegacyRunningEventHash = platformContext.getCryptography().getNullHash();

        final PlatformSchedulersConfig schedulersConfig =
                platformContext.getConfiguration().getConfigData(PlatformSchedulersConfig.class);

        // If the CES is using a no-op scheduler then the legacy running event hash won't be computed.
        writeLegacyRunningEventHash = schedulersConfig.consensusEventStream().type() != TaskSchedulerType.NO_OP;

        // If the application transaction prehandler is a no-op then we don't need to wait for it.
        waitForPrehandle = schedulersConfig.applicationTransactionPrehandler().type() != TaskSchedulerType.NO_OP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateLegacyRunningEventHash(@NonNull final RunningEventHashOverride runningHashUpdate) {
        previousRoundLegacyRunningEventHash = runningHashUpdate.legacyRunningEventHash();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public StateAndRound handleConsensusRound(@NonNull final ConsensusRound consensusRound) {
        // consensus rounds with no events are ignored
        if (consensusRound.isEmpty()) {
            // Future work: the long term goal is for empty rounds to not be ignored here. For now, the way that the
            // running hash of consensus events is calculated by the EventStreamManager prevents that from being
            // possible.
            logger.info(STARTUP.getMarker(), "Ignoring empty consensus round {}", consensusRound.getRoundNum());
            return null;
        }

        // Once there is a saved state created in a freeze period, we will never apply any more rounds to the state.
        if (freezeRoundReceived) {
            logger.info(
                    STARTUP.getMarker(),
                    "Round {} reached consensus after freeze. Round will not be processed until after network "
                            + "restarts.",
                    consensusRound.getRoundNum());
            return null;
        }

        if (swirldStateManager.isInFreezePeriod(consensusRound.getConsensusTimestamp())) {
            statusActionSubmitter.submitStatusAction(new FreezePeriodEnteredAction(consensusRound.getRoundNum()));
            freezeRoundReceived = true;
        }

        handlerMetrics.recordEventsPerRound(consensusRound.getNumEvents());
        handlerMetrics.recordConsensusTime(consensusRound.getConsensusTimestamp());

        try {
            handlerMetrics.setPhase(SETTING_EVENT_CONSENSUS_DATA);
            for (final PlatformEvent event : consensusRound.getConsensusEvents()) {
                event.setConsensusTimestampsOnTransactions();
            }

            handlerMetrics.setPhase(UPDATING_PLATFORM_STATE);
            // it is important to update the platform state before handling the consensus round, since the platform
            // state is passed into the application handle method, and should contain the data for the current round
            updatePlatformState(consensusRound);

            if (waitForPrehandle) {
                handlerMetrics.setPhase(WAITING_FOR_PREHANDLE);
                consensusRound.getConsensusEvents().forEach(PlatformEvent::awaitPrehandleCompletion);
            }

            handlerMetrics.setPhase(HANDLING_CONSENSUS_ROUND);
            swirldStateManager.handleConsensusRound(consensusRound);

            handlerMetrics.setPhase(UPDATING_PLATFORM_STATE_RUNNING_HASH);
            updateRunningEventHash(consensusRound);

            return createSignedState(consensusRound);
        } catch (final InterruptedException e) {
            logger.error(EXCEPTION.getMarker(), "handleConsensusRound interrupted");
            Thread.currentThread().interrupt();

            return null;
        } finally {
            handlerMetrics.setPhase(IDLE);
        }
    }

    /**
     * Populate the {@link PlatformStateModifier} with all needed data for this round.
     *
     * @param round the consensus round
     */
    private void updatePlatformState(@NonNull final ConsensusRound round) {
        final PlatformStateModifier platformState =
                swirldStateManager.getConsensusState().getWritablePlatformState();
        platformState.bulkUpdate(v -> {
            v.setRound(round.getRoundNum());
            v.setConsensusTimestamp(round.getConsensusTimestamp());
            v.setCreationSoftwareVersion(softwareVersion);
            v.setRoundsNonAncient(roundsNonAncient);
            v.setSnapshot(round.getSnapshot());
        });
    }

    /**
     * Update the state with the running event hash.
     *
     * @param round the consensus round
     * @throws InterruptedException if this thread is interrupted
     */
    private void updateRunningEventHash(@NonNull final ConsensusRound round) throws InterruptedException {
        final PlatformStateModifier platformState =
                swirldStateManager.getConsensusState().getWritablePlatformState();

        if (writeLegacyRunningEventHash) {
            // Update the running hash object. If there are no events, the running hash does not change.
            // Future work: this is a redundant check, since empty rounds are currently ignored entirely. The check is
            // here anyway, for when that changes in the future.
            if (!round.isEmpty()) {
                previousRoundLegacyRunningEventHash = round.getStreamedEvents()
                        .getLast()
                        .getRunningHash()
                        .getFutureHash()
                        .getAndRethrow();
            }

            platformState.setLegacyRunningEventHash(previousRoundLegacyRunningEventHash);
        } else {
            platformState.setLegacyRunningEventHash(
                    platformContext.getCryptography().getNullHash());
        }
    }

    /**
     * Create a signed state
     *
     * @param consensusRound the consensus round that resulted in the state being created
     * @return a StateAndRound object containing the signed state and the consensus round
     * @throws InterruptedException if this thread is interrupted
     */
    @NonNull
    private StateAndRound createSignedState(@NonNull final ConsensusRound consensusRound) throws InterruptedException {
        if (freezeRoundReceived) {
            // Let the swirld state manager know we are about to write the saved state for the freeze period
            swirldStateManager.savedStateInFreezePeriod();
        }
        swirldStateManager.sealConsensusRound(consensusRound);

        handlerMetrics.setPhase(GETTING_STATE_TO_SIGN);
        final MerkleRoot immutableStateCons = swirldStateManager.getStateForSigning();

        handlerMetrics.setPhase(CREATING_SIGNED_STATE);
        final SignedState signedState = new SignedState(
                platformContext,
                CryptoStatic::verifySignature,
                immutableStateCons,
                "TransactionHandler.createSignedState()",
                freezeRoundReceived,
                true,
                consensusRound.isPcesRound());

        final ReservedSignedState reservedSignedState = signedState.reserve("transaction handler output");
        return new StateAndRound(reservedSignedState, consensusRound);
    }
}
