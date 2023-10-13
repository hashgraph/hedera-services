/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.state;

import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STATE_TO_DISK;
import static com.swirlds.platform.state.signed.StateToDiskReason.FATAL_ERROR;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.stream.HashSigner;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.status.PlatformStatusGetter;
import com.swirlds.common.system.status.StatusActionSubmitter;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.components.common.query.PrioritySystemTransactionSubmitter;
import com.swirlds.platform.components.state.output.IssConsumer;
import com.swirlds.platform.components.state.output.MinimumGenerationNonAncientConsumer;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateToDiskAttemptConsumer;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.Observer;
import com.swirlds.platform.dispatch.triggers.control.HaltRequestedConsumer;
import com.swirlds.platform.dispatch.triggers.control.StateDumpRequestedTrigger;
import com.swirlds.platform.dispatch.triggers.flow.StateHashedTrigger;
import com.swirlds.platform.event.preconsensus.PreconsensusEventWriter;
import com.swirlds.platform.state.SignatureTransmitter;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateGarbageCollector;
import com.swirlds.platform.state.signed.SignedStateHasher;
import com.swirlds.platform.state.signed.SignedStateInfo;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.SignedStateSentinel;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import com.swirlds.platform.state.signed.StateToDiskReason;
import com.swirlds.platform.util.HashLogger;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The default implementation of {@link StateManagementComponent}.
 */
public class DefaultStateManagementComponent implements StateManagementComponent {

    private static final Logger logger = LogManager.getLogger(DefaultStateManagementComponent.class);

    /**
     * An object responsible for signing states with this node's key.
     */
    private final HashSigner signer;

    /**
     * Submits state signature transactions to the transaction pool
     */
    private final SignatureTransmitter signatureTransmitter;

    /**
     * Signed states are deleted on this background thread.
     */
    private final SignedStateGarbageCollector signedStateGarbageCollector;

    /**
     * Hashes SignedStates.
     */
    private final SignedStateHasher signedStateHasher;

    /**
     * Keeps track of various signed states in various stages of collecting signatures
     */
    private final SignedStateManager signedStateManager;

    /**
     * Manages the pipeline of signed states to be written to disk
     */
    private final SignedStateFileManager signedStateFileManager;

    /**
     * A logger for hash stream data
     */
    private final HashLogger hashLogger;

    /**
     * Used to track signed state leaks, if enabled
     */
    private final SignedStateSentinel signedStateSentinel;

    private final StateConfig stateConfig;

    private static final RunningAverageMetric.Config AVG_ROUND_SUPERMAJORITY_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "roundSup")
            .withDescription("latest round with state signed by a supermajority")
            .withUnit("round");

    /**
     * @param platformContext                    the platform context
     * @param threadManager                      manages platform thread resources
     * @param dispatchBuilder                    builds dispatchers. This is deprecated, do not wire new things together
     *                                           with this.
     * @param addressBook                        the initial address book
     * @param signer                             an object capable of signing with the platform's private key
     * @param mainClassName                      the name of the app class inheriting from SwirldMain
     * @param selfId                             this node's id
     * @param swirldName                         the name of the swirld being run
     * @param prioritySystemTransactionSubmitter submits priority system transactions
     * @param stateToDiskEventConsumer           consumer to invoke when a state is attempted to be written to disk
     * @param newLatestCompleteStateConsumer     consumer to invoke when there is a new latest complete signed state
     * @param stateLacksSignaturesConsumer       consumer to invoke when a state is about to be ejected from memory with
     *                                           enough signatures to be complete
     * @param stateHasEnoughSignaturesConsumer   consumer to invoke when a state accumulates enough signatures to be
     *                                           complete
     * @param issConsumer                        consumer to invoke when an ISS is detected
     * @param fatalErrorConsumer                 consumer to invoke when a fatal error has occurred
     * @param platformStatusGetter               gets the current platform status
     * @param statusActionSubmitter              enables submitting platform status actions
     */
    public DefaultStateManagementComponent(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final DispatchBuilder dispatchBuilder,
            @NonNull final AddressBook addressBook,
            @NonNull final PlatformSigner signer,
            @NonNull final String mainClassName,
            @NonNull final NodeId selfId,
            @NonNull final String swirldName,
            @NonNull final PrioritySystemTransactionSubmitter prioritySystemTransactionSubmitter,
            @NonNull final StateToDiskAttemptConsumer stateToDiskEventConsumer,
            @NonNull final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer,
            @Nullable final StateLacksSignaturesConsumer stateLacksSignaturesConsumer,
            @Nullable final StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer,
            @NonNull final IssConsumer issConsumer,
            @NonNull final HaltRequestedConsumer haltRequestedConsumer,
            @NonNull final FatalErrorConsumer fatalErrorConsumer,
            @NonNull final PreconsensusEventWriter preconsensusEventWriter,
            @NonNull final PlatformStatusGetter platformStatusGetter,
            @NonNull final StatusActionSubmitter statusActionSubmitter) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(threadManager);
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(signer);
        Objects.requireNonNull(mainClassName);
        Objects.requireNonNull(selfId);
        Objects.requireNonNull(swirldName);
        Objects.requireNonNull(prioritySystemTransactionSubmitter);
        Objects.requireNonNull(stateToDiskEventConsumer);
        Objects.requireNonNull(newLatestCompleteStateConsumer);
        Objects.requireNonNull(issConsumer);
        Objects.requireNonNull(haltRequestedConsumer);
        Objects.requireNonNull(fatalErrorConsumer);
        Objects.requireNonNull(preconsensusEventWriter);
        Objects.requireNonNull(platformStatusGetter);
        Objects.requireNonNull(statusActionSubmitter);

        this.signer = signer;
        this.signatureTransmitter = new SignatureTransmitter(prioritySystemTransactionSubmitter, platformStatusGetter);
        /**
         * Various metrics about signed states
         */
        SignedStateMetrics signedStateMetrics = new SignedStateMetrics(platformContext.getMetrics());
        this.signedStateGarbageCollector = new SignedStateGarbageCollector(threadManager, signedStateMetrics);
        this.stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        this.signedStateSentinel = new SignedStateSentinel(platformContext, threadManager, Time.getCurrent());

        hashLogger = new HashLogger(threadManager, stateConfig);

        final StateHashedTrigger stateHashedTrigger =
                dispatchBuilder.getDispatcher(this, StateHashedTrigger.class)::dispatch;
        signedStateHasher = new SignedStateHasher(signedStateMetrics, stateHashedTrigger, fatalErrorConsumer);

        final MinimumGenerationNonAncientConsumer setMinimumGenerationToStore = generation -> {
            try {
                preconsensusEventWriter.setMinimumGenerationToStore(generation);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error(EXCEPTION.getMarker(), "interrupted while setting minimum generation non-ancient");
            }
        };

        signedStateFileManager = new SignedStateFileManager(
                platformContext,
                threadManager,
                signedStateMetrics,
                Time.getCurrent(),
                mainClassName,
                selfId,
                swirldName,
                stateToDiskEventConsumer,
                setMinimumGenerationToStore,
                statusActionSubmitter);

        final StateHasEnoughSignaturesConsumer combinedStateHasEnoughSignaturesConsumer;
        if (stateHasEnoughSignaturesConsumer != null) {
            combinedStateHasEnoughSignaturesConsumer = ss -> {
                stateHasEnoughSignatures(ss);
                stateHasEnoughSignaturesConsumer.stateHasEnoughSignatures(ss);
            };
        } else {
            combinedStateHasEnoughSignaturesConsumer = this::stateHasEnoughSignatures;
        }

        final StateLacksSignaturesConsumer combinedStateLacksSignaturesConsumer;
        if (stateLacksSignaturesConsumer != null) {
            combinedStateLacksSignaturesConsumer = ss -> {
                stateLacksSignatures(ss);
                stateLacksSignaturesConsumer.stateLacksSignatures(ss);
            };
        } else {
            combinedStateLacksSignaturesConsumer = this::stateLacksSignatures;
        }

        signedStateManager = new SignedStateManager(
                platformContext.getConfiguration().getConfigData(StateConfig.class),
                signedStateMetrics,
                newLatestCompleteStateConsumer,
                combinedStateHasEnoughSignaturesConsumer,
                combinedStateLacksSignaturesConsumer);

        final RunningAverageMetric avgRoundSupermajority =
                platformContext.getMetrics().getOrCreate(AVG_ROUND_SUPERMAJORITY_CONFIG);
        platformContext.getMetrics().addUpdater(() -> avgRoundSupermajority.update(getLastCompleteRound()));
    }

    /**
     * Handles a signed state that is now complete by saving it to disk, if it should be saved.
     *
     * @param signedState the newly complete signed state
     */
    private void stateHasEnoughSignatures(@NonNull final SignedState signedState) {
        if (signedState.isStateToSave()) {
            signedStateFileManager.saveSignedStateToDisk(signedState, false);
        }
    }

    /**
     * Handles a signed state that did not collect enough signatures before being ejected from memory.
     *
     * @param signedState the signed state that lacks signatures
     */
    private void stateLacksSignatures(@NonNull final SignedState signedState) {
        if (signedState.isStateToSave()) {
            signedStateFileManager.saveSignedStateToDisk(signedState, true);
        }
    }

    private void newSignedStateBeingTracked(final SignedState signedState, final SourceOfSignedState source) {
        // When we begin tracking a new signed state, "introduce" the state to the SignedStateFileManager
        if (source == SourceOfSignedState.DISK) {
            signedStateFileManager.registerSignedStateFromDisk(signedState);
        } else {
            signedStateFileManager.determineIfStateShouldBeSaved(signedState, source);
        }
        if (source == SourceOfSignedState.RECONNECT) {
            // a state received from reconnect should be saved to disk, but the method stateHasEnoughSignatures will not
            // be called for it by the signed state manager, so we need to call it here
            stateHasEnoughSignatures(signedState);
        }

        if (signedState.getState().getHash() != null) {
            hashLogger.logHashes(signedState);
        }
    }

    /**
     * Checks if the signed state's round is older than the round of the latest state in the signed state manager.
     *
     * @param signedState the signed state whose round needs to be compared to the latest state in the signed state
     *                    manager.
     * @return true if the signed state's round is < the round of the latest state in the signed state manager,
     * otherwise false.
     */
    private boolean stateRoundIsTooOld(final SignedState signedState) {
        final long roundOfLatestState = signedStateManager.getLastImmutableStateRound();
        if (signedState.getRound() < roundOfLatestState) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "State received from transactions is in an incorrect order. "
                            + "Latest state is from round {}, provided state is from round {}",
                    roundOfLatestState,
                    signedState.getRound());
            return true;
        }
        return false;
    }

    @Override
    public void newSignedStateFromTransactions(@NonNull final ReservedSignedState signedState) {
        try (signedState) {
            signedState.get().setGarbageCollector(signedStateGarbageCollector);

            if (stateRoundIsTooOld(signedState.get())) {
                return; // do not process older states.
            }
            signedStateHasher.hashState(signedState.get());

            newSignedStateBeingTracked(signedState.get(), SourceOfSignedState.TRANSACTIONS);

            final Signature signature = signer.sign(signedState.get().getState().getHash());
            signatureTransmitter.transmitSignature(
                    signedState.get().getRound(),
                    signature,
                    signedState.get().getState().getHash());

            signedStateManager.addState(signedState.get());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public ReservedSignedState getLatestSignedState(@NonNull final String reason) {
        return signedStateManager.getLatestSignedState(reason);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReservedSignedState getLatestImmutableState(@NonNull final String reason) {
        return signedStateManager.getLatestImmutableState(reason);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLastCompleteRound() {
        return signedStateManager.getLastCompleteRound();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SignedStateInfo> getSignedStateInfo() {
        return signedStateManager.getSignedStateInfo();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stateToLoad(final SignedState signedState, final SourceOfSignedState sourceOfSignedState) {
        signedState.setGarbageCollector(signedStateGarbageCollector);
        newSignedStateBeingTracked(signedState, sourceOfSignedState);
        signedStateManager.addState(signedState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        signedStateGarbageCollector.start();
        signedStateFileManager.start();
        signedStateSentinel.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        signedStateFileManager.stop();
        signedStateSentinel.stop();
        signedStateGarbageCollector.stop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onFatalError() {
        if (stateConfig.dumpStateOnFatal()) {
            try (final ReservedSignedState reservedState =
                    signedStateManager.getLatestSignedState("DefaultStateManagementComponent.onFatalError()")) {
                if (reservedState.isNotNull()) {
                    signedStateFileManager.dumpState(reservedState.get(), FATAL_ERROR, true);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ReservedSignedState find(final @NonNull Predicate<SignedState> criteria, @NonNull final String reason) {
        return signedStateManager.find(criteria, reason);
    }

    /**
     * This observer is called when a signed state is requested to be dumped to disk.
     *
     * @param round    the round that should be dumped if available. If this parameter is null or if the requested round
     *                 is unavailable then the latest immutable round should be dumped.
     * @param reason   reason why the state is being dumped
     * @param blocking if this method should block until the operation has been completed
     */
    @Observer(StateDumpRequestedTrigger.class)
    public void stateDumpRequestedObserver(
            @Nullable final Long round, @NonNull final StateToDiskReason reason, @NonNull final Boolean blocking) {

        Objects.requireNonNull(reason);
        Objects.requireNonNull(blocking);

        if (round == null) {
            // No round is specified, dump the latest immutable state.
            dumpLatestImmutableState(reason, blocking);
            return;
        }

        try (final ReservedSignedState reservedState =
                signedStateManager.find(state -> state.getRound() == round, "state dump requested for " + reason)) {

            if (reservedState.isNotNull()) {
                // We were able to find the requested round. Dump it.
                signedStateFileManager.dumpState(reservedState.get(), reason, blocking);
                return;
            }
        }

        // We weren't able to find the requested round, so the best we can do is the latest round.
        logger.info(
                STATE_TO_DISK.getMarker(),
                "State dump for round {} requested, but round could not be "
                        + "found in the signed state manager. Dumping latest immutable round instead.",
                round);
        dumpLatestImmutableState(reason, blocking);
    }

    @Override
    public void dumpLatestImmutableState(@NonNull final StateToDiskReason reason, final boolean blocking) {
        Objects.requireNonNull(reason);

        try (final ReservedSignedState reservedState = signedStateManager.getLatestImmutableState(
                "DefaultStateManagementComponent.dumpLatestImmutableState()")) {

            if (reservedState.isNull()) {
                logger.warn(STATE_TO_DISK.getMarker(), "State dump requested, but no state is available.");
            } else {
                signedStateFileManager.dumpState(reservedState.get(), reason, blocking);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Instant getFirstStateTimestamp() {
        return signedStateManager.getFirstStateTimestamp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getFirstStateRound() {
        return signedStateManager.getFirstStateRound();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLatestSavedStateRound() {
        return signedStateFileManager.getLatestSavedStateRound();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public SignedStateManager getSignedStateManager() {
        return signedStateManager;
    }
}
