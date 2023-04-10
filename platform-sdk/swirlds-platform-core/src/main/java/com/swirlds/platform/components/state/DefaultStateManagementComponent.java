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

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STATE_TO_DISK;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.stream.HashSigner;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.components.common.query.PrioritySystemTransactionSubmitter;
import com.swirlds.platform.components.state.output.IssConsumer;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateToDiskAttemptConsumer;
import com.swirlds.platform.components.transaction.system.PostConsensusSystemTransactionTypedHandler;
import com.swirlds.platform.components.transaction.system.PreConsensusSystemTransactionTypedHandler;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.dispatch.Observer;
import com.swirlds.platform.dispatch.triggers.control.HaltRequestedConsumer;
import com.swirlds.platform.dispatch.triggers.control.StateDumpRequestedTrigger;
import com.swirlds.platform.dispatch.triggers.flow.StateHashedTrigger;
import com.swirlds.platform.event.preconsensus.PreConsensusEventWriter;
import com.swirlds.platform.metrics.IssMetrics;
import com.swirlds.platform.state.SignatureTransmitter;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.iss.ConsensusHashManager;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateGarbageCollector;
import com.swirlds.platform.state.signed.SignedStateHasher;
import com.swirlds.platform.state.signed.SignedStateInfo;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.SignedStateSentinel;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import com.swirlds.platform.util.HashLogger;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
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
     * Various metrics about signed states
     */
    private final SignedStateMetrics signedStateMetrics;

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
     * Tracks the state hashes reported by peers and detects ISSes.
     */
    private final ConsensusHashManager consensusHashManager;

    /**
     * A logger for hash stream data
     */
    private final HashLogger hashLogger;

    /**
     * Builds dispatches for communication internal to this component
     */
    private final DispatchBuilder dispatchBuilder;

    /**
     * Used to track signed state leaks, if enabled
     */
    private final SignedStateSentinel signedStateSentinel;

    private final StateConfig stateConfig;

    /**
     * @param context                            the platform context
     * @param threadManager                      manages platform thread resources
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
     */
    public DefaultStateManagementComponent(
            @NonNull final PlatformContext context,
            @NonNull final ThreadManager threadManager,
            @NonNull final AddressBook addressBook,
            @NonNull final PlatformSigner signer,
            @NonNull final String mainClassName,
            @NonNull final NodeId selfId,
            @NonNull final String swirldName,
            @NonNull final PrioritySystemTransactionSubmitter prioritySystemTransactionSubmitter,
            @NonNull final StateToDiskAttemptConsumer stateToDiskEventConsumer,
            @NonNull final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer,
            @NonNull final StateLacksSignaturesConsumer stateLacksSignaturesConsumer,
            @NonNull final StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer,
            @NonNull final IssConsumer issConsumer,
            @NonNull final HaltRequestedConsumer haltRequestedConsumer,
            @NonNull final FatalErrorConsumer fatalErrorConsumer,
            @NonNull final PreConsensusEventWriter preConsensusEventWriter) {

        throwArgNull(context, "context");
        throwArgNull(threadManager, "threadManager");
        throwArgNull(addressBook, "addressBook");
        throwArgNull(signer, "signer");
        throwArgNull(mainClassName, "mainClassName");
        throwArgNull(selfId, "selfId");
        throwArgNull(swirldName, "swirldName");
        throwArgNull(prioritySystemTransactionSubmitter, "prioritySystemTransactionSubmitter");
        throwArgNull(stateToDiskEventConsumer, "stateToDiskEventConsumer");
        throwArgNull(newLatestCompleteStateConsumer, "newLatestCompleteStateConsumer");
        throwArgNull(stateLacksSignaturesConsumer, "stateLacksSignaturesConsumer");
        throwArgNull(stateHasEnoughSignaturesConsumer, "stateHasEnoughSignaturesConsumer");
        throwArgNull(issConsumer, "issConsumer");
        throwArgNull(haltRequestedConsumer, "haltRequestedConsumer");
        throwArgNull(fatalErrorConsumer, "fatalErrorConsumer");
        throwArgNull(preConsensusEventWriter, "preConsensusEventWriter");

        this.signer = signer;
        this.signatureTransmitter = new SignatureTransmitter(addressBook, selfId, prioritySystemTransactionSubmitter);
        this.signedStateMetrics = new SignedStateMetrics(context.getMetrics());
        this.signedStateGarbageCollector = new SignedStateGarbageCollector(threadManager, signedStateMetrics);
        this.stateConfig = context.getConfiguration().getConfigData(StateConfig.class);
        this.signedStateSentinel = new SignedStateSentinel(threadManager, OSTime.getInstance());

        dispatchBuilder = new DispatchBuilder(context.getConfiguration().getConfigData(DispatchConfiguration.class));

        hashLogger = new HashLogger(threadManager, selfId);

        final StateHashedTrigger stateHashedTrigger =
                dispatchBuilder.getDispatcher(this, StateHashedTrigger.class)::dispatch;
        signedStateHasher = new SignedStateHasher(signedStateMetrics, stateHashedTrigger, fatalErrorConsumer);

        signedStateFileManager = new SignedStateFileManager(
                context,
                threadManager,
                signedStateMetrics,
                OSTime.getInstance(),
                mainClassName,
                selfId,
                swirldName,
                stateToDiskEventConsumer,
                preConsensusEventWriter::setMinimumGenerationToStore);

        final StateHasEnoughSignaturesConsumer combinedStateHasEnoughSignaturesConsumer = ssw -> {
            stateHasEnoughSignatures(ssw.get());
            // This consumer releases the wrapper, so it must be last
            stateHasEnoughSignaturesConsumer.stateHasEnoughSignatures(ssw);
        };

        final StateLacksSignaturesConsumer combinedStateLacksSignaturesConsumer = ssw -> {
            stateLacksSignatures(ssw.get());
            // This consumer releases the wrapper, so it must be last.
            stateLacksSignaturesConsumer.stateLacksSignatures(ssw);
        };

        signedStateManager = new SignedStateManager(
                context.getConfiguration().getConfigData(StateConfig.class),
                signedStateMetrics,
                newLatestCompleteStateConsumer,
                combinedStateHasEnoughSignaturesConsumer,
                combinedStateLacksSignaturesConsumer);

        consensusHashManager = new ConsensusHashManager(
                OSTime.getInstance(),
                dispatchBuilder,
                addressBook,
                context.getConfiguration().getConfigData(ConsensusConfig.class),
                stateConfig);

        final IssHandler issHandler = new IssHandler(
                OSTime.getInstance(),
                dispatchBuilder,
                stateConfig,
                selfId.getId(),
                haltRequestedConsumer,
                fatalErrorConsumer,
                issConsumer);

        final IssMetrics issMetrics = new IssMetrics(context.getMetrics(), addressBook);

        dispatchBuilder
                .registerObservers(issHandler)
                .registerObservers(consensusHashManager)
                .registerObservers(issMetrics)
                .registerObservers(this);
    }

    /**
     * Handles a signed state that is now complete by saving it to disk, if it should be saved.
     *
     * @param signedState the newly complete signed state
     */
    private void stateHasEnoughSignatures(final SignedState signedState) {
        if (signedState.isStateToSave()) {
            signedStateFileManager.saveSignedStateToDisk(signedState);
        }
    }

    /**
     * Handles a signed state that did not collect enough signatures before being ejected from memory.
     *
     * @param signedState the signed state that lacks signatures
     */
    private void stateLacksSignatures(final SignedState signedState) {
        if (signedState.isStateToSave()) {
            final long previousCount =
                    signedStateMetrics.getTotalUnsignedDiskStatesMetric().get();
            signedStateMetrics.getTotalUnsignedDiskStatesMetric().increment();
            final long newCount =
                    signedStateMetrics.getTotalUnsignedDiskStatesMetric().get();

            if (newCount <= previousCount) {
                logger.error(EXCEPTION.getMarker(), "Metric for total unsigned disk states not updated");
            }

            logger.error(
                    EXCEPTION.getMarker(),
                    "state written to disk for round {} did not have enough signatures. "
                            + "Collected signatures representing {}/{} stake. Total unsigned disk states so far: {}. "
                            + "AB={}",
                    signedState.getRound(),
                    signedState.getSigningStake(),
                    signedState.getAddressBook().getTotalStake(),
                    newCount,
                    signedState.getAddressBook());
            signedStateFileManager.saveSignedStateToDisk(signedState);
        }
    }

    private void newSignedStateBeingTracked(final SignedState signedState, final SourceOfSignedState source) {
        // When we begin tracking a new signed state, "introduce" the state to the SignedStateFileManager
        if (source == SourceOfSignedState.DISK) {
            signedStateFileManager.registerSignedStateFromDisk(signedState);
        } else {
            signedStateFileManager.determineIfStateShouldBeSaved(signedState);
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
    public void newSignedStateFromTransactions(final SignedState signedState) {
        signedState.setGarbageCollector(signedStateGarbageCollector);

        if (stateRoundIsTooOld(signedState)) {
            return; // do not process older states.
        }
        signedStateHasher.hashState(signedState);

        newSignedStateBeingTracked(signedState, SourceOfSignedState.TRANSACTIONS);

        final Signature signature = signer.sign(signedState.getState().getHash());
        signatureTransmitter.transmitSignature(
                signedState.getRound(), signature, signedState.getState().getHash());

        signedStateManager.addState(signedState);
    }

    /**
     * Do pre consensus handling for a state signature transaction
     *
     * @param creatorId                 the id of the transaction creator
     * @param stateSignatureTransaction the pre-consensus state signature transaction
     */
    public void handleStateSignatureTransactionPreConsensus(
            final long creatorId, final StateSignatureTransaction stateSignatureTransaction) {

        signedStateManager.preConsensusSignatureObserver(
                stateSignatureTransaction.getRound(), creatorId, stateSignatureTransaction.getStateSignature());
    }

    /**
     * Do post-consensus handling for a state signature transaction
     * <p>
     * The {@code state} parameter isn't used in this function, since a signature transaction doesn't modify the state
     */
    public void handleStateSignatureTransactionPostConsensus(
            final State state, final long creatorId, final StateSignatureTransaction stateSignatureTransaction) {

        consensusHashManager.postConsensusSignatureObserver(
                stateSignatureTransaction.getRound(), creatorId, stateSignatureTransaction.getStateHash());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AutoCloseableWrapper<SignedState> getLatestSignedState() {
        return signedStateManager.getLatestSignedState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AutoCloseableWrapper<SignedState> getLatestImmutableState() {
        return signedStateManager.getLatestImmutableState();
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
    public void roundAppliedToState(final long round) {
        consensusHashManager.roundCompleted(round);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        signedStateGarbageCollector.start();
        signedStateFileManager.start();
        dispatchBuilder.start();

        if (stateConfig.signedStateSentinelEnabled()) {
            signedStateSentinel.start();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        signedStateFileManager.stop();
        if (stateConfig.signedStateSentinelEnabled()) {
            signedStateSentinel.stop();
        }
        signedStateGarbageCollector.stop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onFatalError() {
        if (stateConfig.dumpStateOnFatal()) {
            try (final AutoCloseableWrapper<SignedState> wrapper = signedStateManager.getLatestSignedState()) {
                final SignedState state = wrapper.get();
                if (state != null) {
                    signedStateFileManager.dumpState(state, "fatal", true);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public AutoCloseableWrapper<SignedState> find(final @NonNull Predicate<SignedState> criteria) {
        return signedStateManager.find(criteria);
    }

    /**
     * This observer is called when the most recent signed state is requested to be dumped to disk.
     *
     * @param round    the round that should be dumped if available. If this parameter is null or if the requested round
     *                 is unavailable then the latest immutable round should be dumped.
     * @param reason   reason why the state is being dumped, e.g. "fatal" or "iss". Is used as a part of the file path
     *                 for the dumped state files, so this string should not contain any special characters or
     *                 whitespace.
     * @param blocking if this method should block until the operation has been completed
     */
    @Observer(StateDumpRequestedTrigger.class)
    public void stateDumpRequestedObserver(
            @Nullable final Long round, @NonNull final String reason, @NonNull final Boolean blocking) {

        if (round == null) {
            // No round is specified, dump the latest immutable state.
            dumpLatestImmutableState(reason, blocking);
            return;
        }

        try (final AutoCloseableWrapper<SignedState> wrapper =
                signedStateManager.find(state -> state.getRound() == round)) {
            if (wrapper.get() != null) {
                // We were able to find the requested round. Dump it.
                signedStateFileManager.dumpState(wrapper.get(), reason, blocking);
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

    /**
     * Dump the latest immutable state if it is available.
     *
     * @param reason   the reason why the state is being dumped
     * @param blocking if true then block until the state dump is complete
     */
    private void dumpLatestImmutableState(@NonNull final String reason, final boolean blocking) {
        try (final AutoCloseableWrapper<SignedState> wrapper = signedStateManager.getLatestImmutableState()) {
            if (wrapper.get() == null) {
                logger.warn(STATE_TO_DISK.getMarker(), "State dump requested, but no state is available.");
            } else {
                signedStateFileManager.dumpState(wrapper.get(), reason, blocking);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PreConsensusSystemTransactionTypedHandler<?>> getPreConsensusHandleMethods() {
        return List.of(new PreConsensusSystemTransactionTypedHandler<>(
                StateSignatureTransaction.class, this::handleStateSignatureTransactionPreConsensus));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PostConsensusSystemTransactionTypedHandler<?>> getPostConsensusHandleMethods() {
        return List.of(new PostConsensusSystemTransactionTypedHandler<>(
                StateSignatureTransaction.class, this::handleStateSignatureTransactionPostConsensus));
    }
}
