/*
 * Copyright (C) 2017-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.sequence.set.SequenceSet;
import com.swirlds.common.sequence.set.StandardSequenceSet;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * Data structures and methods to manage the lifecycle of signed states. This class ensures that the following states
 * are always in memory:
 * </p>
 *
 * <ul>
 * <li>
 * The most recent fully-signed state (as long as it's not too old)
 * </li>
 * <li>
 * All the non-ancient states that are not fully signed (as long as they are not too old)
 * </li>
 * <li>
 * Recently signed states, if configured to do so.
 * </li>
 * </ul>
 */
public class SignedStateManager {

    /**
     * The latest signed state. May be unhashed. May or may not have all of its signatures.
     */
    private final SignedStateReference lastState = new SignedStateReference();

    /**
     * Signed states awaiting signatures.
     */
    private final SignedStateMap incompleteStates = new SignedStateMap();

    /**
     * States that have already been signed.
     */
    private final SignedStateMap completeStates = new SignedStateMap();

    private final StateConfig stateConfig;

    /**
     * A signature that was received when there was no state with a matching round.
     */
    private record SavedSignature(long round, @NonNull NodeId memberId, @NonNull Signature signature) {}

    /**
     * Signatures for rounds in the future.
     */
    private final SequenceSet<SavedSignature> savedSignatures;

    /**
     * A collection of signed state metrics.
     */
    private final SignedStateMetrics signedStateMetrics;

    private final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer;
    private final StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer;
    private final StateLacksSignaturesConsumer stateLacksSignaturesConsumer;

    /**
     * Start empty, with no known signed states. The number of addresses in platform.hashgraph.getAddressBook() must not
     * change in the future. The addressBook must contain exactly the set of members who can sign the state. A signed
     * state is considered completed when it has signatures from a sufficient threshold of nodes.
     *
     * @param stateConfig                      configuration for state
     * @param signedStateMetrics               a collection of signed state metrics
     * @param newLatestCompleteStateConsumer   this method should be called each time we change which state is being
     *                                         considered the latest fully signed state
     * @param stateHasEnoughSignaturesConsumer this method should be called when a state gathers enough signatures to be
     *                                         complete, even if that state does not become the "latest complete state"
     * @param stateLacksSignaturesConsumer     this method is called when we have to delete a state before it gathers
     *                                         sufficient signatures
     */
    public SignedStateManager(
            @NonNull final StateConfig stateConfig,
            @NonNull final SignedStateMetrics signedStateMetrics,
            @NonNull final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer,
            @NonNull final StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer,
            @NonNull final StateLacksSignaturesConsumer stateLacksSignaturesConsumer) {

        this.stateConfig = Objects.requireNonNull(stateConfig, "stateConfig");
        this.signedStateMetrics = Objects.requireNonNull(signedStateMetrics, "signedStateMetrics");
        this.newLatestCompleteStateConsumer =
                Objects.requireNonNull(newLatestCompleteStateConsumer, "newLatestCompleteStateConsumer");
        this.stateHasEnoughSignaturesConsumer =
                Objects.requireNonNull(stateHasEnoughSignaturesConsumer, "stateHasEnoughSignaturesConsumer");
        this.stateLacksSignaturesConsumer =
                Objects.requireNonNull(stateLacksSignaturesConsumer, "stateLacksSignaturesConsumer");

        this.savedSignatures =
                new StandardSequenceSet<>(0, stateConfig.maxAgeOfFutureStateSignatures(), SavedSignature::round);
    }

    public synchronized List<ReservedSignedState> addReservedState(@NonNull final ReservedSignedState reservedSignedState) {
        try(reservedSignedState){
            addState(reservedSignedState.get());
        }
        return null;
    }

    /**
     * Add a state. State may be ignored if it is too old.
     *
     * @param signedState the signed state to add
     */
    public synchronized void addState(@NonNull final SignedState signedState) {
        Objects.requireNonNull(signedState, "reservedSignedState");

        if (signedState.getState().getHash() == null) {
            throw new IllegalArgumentException(
                    "Unhashed state for round " + signedState.getRound() + " added to the signed state manager");
        }

        // Double check that the signatures on this state are valid.
        // They may no longer be valid if we have done a data migration.
        signedState.pruneInvalidSignatures();

        if (signedState.isComplete()) {
            completeStates.put(signedState, "SignedStateManager.addState(complete)");
            if (completeStates.getLatestRound() == signedState.getRound()) {
                notifyNewLatestCompleteState(signedState);
            }
        } else {
            incompleteStates.put(signedState, "SignedStateManager.addState(incomplete)");
            gatherSavedSignatures(signedState);
        }

        setLastStateIfNewer(signedState);
        adjustSavedSignaturesWindow(signedState.getRound());
        purgeOldStates();
    }

    /**
     * Sets the last state to this signed state, iff it is more recent than the current last state
     *
     * @param signedState the signed state to use
     */
    private void setLastStateIfNewer(@NonNull final SignedState signedState) {
        if (signedState.getRound() > lastState.getRound()) {
            lastState.set(signedState, "SignedStateManager.setLastStateIfNewer()");
        }
    }

    public synchronized List<ReservedSignedState> handlePreconsensusScopedSystemTransactions(
            @NonNull final List<ScopedSystemTransaction<StateSignatureTransaction>> transactions) {
        for (final ScopedSystemTransaction<StateSignatureTransaction> transaction : transactions) {
            handlePreconsensusSignatureTransaction(transaction.submitterId(), transaction.transaction());
        }
        return null;
    }

    /**
     * An observer of pre-consensus state signatures.
     *
     * @param signerId             the node that created the signature
     * @param signatureTransaction the signature transaction
     */
    public synchronized void handlePreconsensusSignatureTransaction(
            @NonNull final NodeId signerId, @NonNull final StateSignatureTransaction signatureTransaction) {

        Objects.requireNonNull(signerId);
        Objects.requireNonNull(signatureTransaction);

        final long round = signatureTransaction.getRound();
        final Signature signature = signatureTransaction.getStateSignature();

        signedStateMetrics.getStateSignaturesGatheredPerSecondMetric().cycle();

        final long lastStateRound = lastState.getRound();
        if (lastStateRound != -1) {
            final long signatureAge = round - lastStateRound;
            signedStateMetrics.getStateSignatureAge().update(signatureAge);
        }

        try (final ReservedSignedState reservedState = getIncompleteState(round)) {
            if (reservedState.isNull()) {
                // This round has already been completed, or it is really old or in the future
                savedSignatures.add(new SavedSignature(round, signerId, signature));
                return;
            }

            addSignature(reservedState.get(), signerId, signature);
        }
    }

    public synchronized List<ReservedSignedState> handlePostconsensusScopedSystemTransactions(
            @NonNull final List<ScopedSystemTransaction<StateSignatureTransaction>> transactions) {
        for (final ScopedSystemTransaction<StateSignatureTransaction> transaction : transactions) {
            handlePostconsensusSignatureTransaction(transaction.submitterId(), transaction.transaction());
        }
        return null;
    }

    /**
     * An observer of post-consensus state signatures.
     *
     * @param signerId    the node that created the signature
     * @param transaction the signature transaction
     */
    public synchronized void handlePostconsensusSignatureTransaction(
            @NonNull final NodeId signerId, @NonNull final StateSignatureTransaction transaction) {

        Objects.requireNonNull(signerId);

        final long round = transaction.getRound();

        try (final ReservedSignedState reservedState = getIncompleteState(round)) {
            // it isn't possible to receive a post-consensus signature transaction for a future round,
            // and if we don't have the state for an old round, we never will.
            // in both cases, the signature can be ignored
            if (reservedState.isNull()) {
                return;
            }

            addSignature(reservedState.get(), signerId, transaction.getStateSignature());
        }
    }

    /**
     * Get the earliest round that is permitted to be stored in this data structure.
     *
     * @return the earliest round permitted to be stored
     */
    private long getEarliestPermittedRound() {
        return lastState.getRound() - stateConfig.roundsToKeepForSigning() + 1;
    }

    /**
     * Given an iterator that walks over the states in {@link #incompleteStates}, remove any states that are too old.
     *
     * @param iterator an iterator that walks over a collection of signed states
     */
    private void removeOldUnsignedStates(@NonNull final Iterator<SignedState> iterator) {

        // Any state older than this is unconditionally removed.
        final long earliestPermittedRound = getEarliestPermittedRound();

        while (iterator.hasNext()) {
            final SignedState signedState = iterator.next();
            if (signedState.getRound() < earliestPermittedRound) {
                signedStateMetrics.getTotalUnsignedStatesMetric().increment();
                notifyStateLacksSignatures(signedState);
                iterator.remove();
            }
        }
    }

    /**
     * Given an iterator that walks over the states in {@link #completeStates}, remove any states that are too old.
     *
     * @param iterator an iterator that walks over a collection of signed states
     */
    private void removeOldSignedStates(@NonNull final Iterator<SignedState> iterator) {

        // Any state older than this is unconditionally removed.
        final long earliestPermittedRound = getEarliestPermittedRound();

        // The latest state that has gathered all of its signatures.
        final long latestSignedRound = completeStates.getLatestRound();

        // If state signing is working as desired, then this will be the oldest signed state we keep in this structure.
        // If state signing breaks down, we will not remove the latest signed state when it becomes older than this
        // round (unless it becomes so old that it is before the earliest permitted round).
        final long desiredEarliestSignedRound = lastState.getRound() - stateConfig.roundsToKeepAfterSigning();

        while (iterator.hasNext()) {
            final SignedState signedState = iterator.next();
            final long stateRound = signedState.getRound();

            final boolean stateIsVeryOld = stateRound < earliestPermittedRound;

            final boolean stateIsOldAndNotTheMostRecentlySigned =
                    stateRound < desiredEarliestSignedRound && stateRound != latestSignedRound;

            if (stateIsVeryOld || stateIsOldAndNotTheMostRecentlySigned) {
                iterator.remove();
            }
        }
    }

    /**
     * Get rid of old states.
     */
    private void purgeOldStates() {
        incompleteStates.atomicIteration(this::removeOldUnsignedStates);
        completeStates.atomicIteration(this::removeOldSignedStates);

        signedStateMetrics.getUnsignedStatesMetric().update(incompleteStates.getSize());
        signedStateMetrics.geSignedStatesMetric().update(completeStates.getSize());
    }

    /**
     * Get an unsigned state for a particular round, if it exists.
     *
     * @param round the round in question
     * @return a signed state for a round, or a null reservation if a signed state for that round is not present
     */
    private @NonNull ReservedSignedState getIncompleteState(final long round) {
        return incompleteStates.getAndReserve(round, "SignedStateManager.getIncompleteState()");
    }

    /**
     * Gather and apply all signatures that were previously saved for a signed state.
     *
     * @param signedState a signed state that is now able to collect signatures
     */
    private void gatherSavedSignatures(@NonNull final SignedState signedState) {
        savedSignatures.removeSequenceNumber(
                signedState.getRound(),
                savedSignature -> addSignature(signedState, savedSignature.memberId, savedSignature.signature));
    }

    /**
     * Adjust the window where we are willing to save future signatures.
     *
     * @param currentRound the round of the most recently signed state
     */
    private void adjustSavedSignaturesWindow(final long currentRound) {
        // Only save signatures for round N+1 and after.
        // Any rounds behind this one will either have already had a SignedState
        // added to this manager, or will never have a SignedState added to this manager.
        if (savedSignatures.getFirstSequenceNumberInWindow() < currentRound + 1) {
            savedSignatures.shiftWindow(currentRound + 1);
        }
    }

    /**
     * Called when a signed state is first completed.
     */
    private void signedStateNewlyComplete(@NonNull final SignedState signedState) {
        signedStateMetrics.getStatesSignedPerSecondMetric().cycle();
        signedStateMetrics
                .getAverageTimeToFullySignStateMetric()
                .update(Duration.between(signedState.getCreationTimestamp(), Instant.now())
                        .toMillis());

        notifyStateHasEnoughSignatures(signedState);
        if (completeStates.getLatestRound() < signedState.getRound()) {
            notifyNewLatestCompleteState(signedState);
        }

        completeStates.put(signedState, "SignedStateManager.signedStateNewlyComplete()");
        incompleteStates.remove(signedState.getRound());

        purgeOldStates();
    }

    /**
     * Add a new signature to a signed state.
     *
     * @param signedState the state being signed
     * @param nodeId      the ID of the signer
     * @param signature   the signature on the state
     */
    private void addSignature(
            @NonNull final SignedState signedState, @NonNull final NodeId nodeId, @NonNull final Signature signature) {
        Objects.requireNonNull(signedState, "signedState must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(signature, "signature must not be null");

        if (signedState.addSignature(nodeId, signature)) {
            // at this point the signed state is complete for the first time
            signedStateNewlyComplete(signedState);
        }
    }

    /**
     * Send out a notification that the most up-to-date and complete signed state has changed.
     *
     * @param signedState the new most recently and complete signed state
     */
    private void notifyNewLatestCompleteState(@NonNull final SignedState signedState) {
        newLatestCompleteStateConsumer.newLatestCompleteStateEvent(signedState);
    }

    /**
     * Send out a notification that a signed state was unable to be completely signed.
     *
     * @param signedState the state that was unable to be complete signed
     */
    private void notifyStateLacksSignatures(@NonNull final SignedState signedState) {
        Objects.requireNonNull(signedState, "signedState must not be null");
        stateLacksSignaturesConsumer.stateLacksSignatures(signedState);
    }

    /**
     * Send out a notification that a signed state was able to collect enough signatures to become complete.
     *
     * @param signedState the state that now has enough signatures
     */
    private void notifyStateHasEnoughSignatures(@NonNull final SignedState signedState) {
        Objects.requireNonNull(signedState, "signedState must not be null");
        stateHasEnoughSignaturesConsumer.stateHasEnoughSignatures(signedState);
    }
}
