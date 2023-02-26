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

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.stream.HashSigner;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.components.common.output.StateSignature;
import com.swirlds.platform.components.common.query.PrioritySystemTransactionSubmitter;
import com.swirlds.platform.components.state.output.IssConsumer;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateToDiskAttemptConsumer;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.dispatch.Observer;
import com.swirlds.platform.dispatch.triggers.control.HaltRequestedConsumer;
import com.swirlds.platform.dispatch.triggers.control.StateDumpRequestedTrigger;
import com.swirlds.platform.dispatch.triggers.flow.StateHashedTrigger;
import com.swirlds.platform.metrics.IssMetrics;
import com.swirlds.platform.state.SignatureTransmitter;
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
import java.util.List;
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
            final PlatformContext context,
            final ThreadManager threadManager,
            final AddressBook addressBook,
            final PlatformSigner signer,
            final String mainClassName,
            final NodeId selfId,
            final String swirldName,
            final PrioritySystemTransactionSubmitter prioritySystemTransactionSubmitter,
            final StateToDiskAttemptConsumer stateToDiskEventConsumer,
            final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer,
            final StateLacksSignaturesConsumer stateLacksSignaturesConsumer,
            final StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer,
            final IssConsumer issConsumer,
            final HaltRequestedConsumer haltRequestedConsumer,
            final FatalErrorConsumer fatalErrorConsumer) {

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
                stateToDiskEventConsumer);

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
        newSignedStateBeingTracked(signedState, SourceOfSignedState.TRANSACTIONS);

        signedStateHasher.hashState(signedState);
        final Signature signature = signer.sign(signedState.getState().getHash());
        signatureTransmitter.transmitSignature(
                signedState.getRound(), signature, signedState.getState().getHash());

        signedStateManager.addUnsignedState(signedState);
    }

    @Override
    public void handleStateSignature(final StateSignature stateSignature, final boolean isConsensus) {
        if (isConsensus) {
            consensusHashManager.postConsensusSignatureObserver(
                    stateSignature.round(), stateSignature.signerId(), stateSignature.stateHash());
        } else {
            signedStateManager.preConsensusSignatureObserver(
                    stateSignature.round(), stateSignature.signerId(), stateSignature.signature());
        }
    }

    @Override
    public AutoCloseableWrapper<SignedState> getLatestSignedState() {
        return signedStateManager.getLatestSignedState();
    }

    @Override
    public AutoCloseableWrapper<SignedState> getLatestImmutableState() {
        return signedStateManager.getLatestImmutableState();
    }

    @Override
    public long getLastCompleteRound() {
        return signedStateManager.getLastCompleteRound();
    }

    @Override
    public long getLastRoundSavedToDisk() {
        return signedStateFileManager.getLastRoundSavedToDisk();
    }

    @Override
    public List<SignedStateInfo> getSignedStateInfo() {
        return signedStateManager.getSignedStateInfo();
    }

    @Override
    public void stateToLoad(final SignedState signedState, final SourceOfSignedState sourceOfSignedState) {
        signedState.setGarbageCollector(signedStateGarbageCollector);
        newSignedStateBeingTracked(signedState, sourceOfSignedState);
        signedStateManager.addCompleteSignedState(signedState);
    }

    @Override
    public void roundAppliedToState(final long round) {
        consensusHashManager.roundCompleted(round);
    }

    @Override
    public void start() {
        signedStateGarbageCollector.start();
        signedStateFileManager.start();
        dispatchBuilder.start();

        if (stateConfig.signedStateSentinelEnabled()) {
            signedStateSentinel.start();
        }
    }

    @Override
    public void stop() {
        signedStateFileManager.stop();
        if (stateConfig.signedStateSentinelEnabled()) {
            signedStateSentinel.stop();
        }
        signedStateGarbageCollector.stop();
    }

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

    @Override
    public AutoCloseableWrapper<SignedState> find(final long round, final Hash hash) {
        return signedStateManager.find(round, hash);
    }

    /**
     * This observer is called when the most recent signed state is requested to be dumped to disk.
     *
     * @param reason   reason why the state is being dumped, e.g. "fatal" or "iss". Is used as a part of the file path
     *                 for the dumped state files, so this string should not contain any special characters or
     *                 whitespace.
     * @param blocking if this method should block until the operation has been completed
     */
    @Observer(StateDumpRequestedTrigger.class)
    public void stateDumpRequestedObserver(final String reason, final Boolean blocking) {
        try (final AutoCloseableWrapper<SignedState> wrapper = signedStateManager.getLatestImmutableState()) {
            signedStateFileManager.dumpState(wrapper.get(), reason, blocking);
        }
    }
}
