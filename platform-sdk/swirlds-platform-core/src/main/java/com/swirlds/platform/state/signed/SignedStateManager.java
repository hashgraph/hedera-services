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
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

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

    /** The latest signed state round*/
    private long lastStateRound = ConsensusConstants.ROUND_UNDEFINED;
    /** Signed states awaiting signatures */
    private final Map<Long, ReservedSignedState> incompleteStates = new HashMap<>();
    /** State config */
    private final StateConfig stateConfig;
    /** Signatures for rounds in the future */
    private final SequenceSet<SavedSignature> savedSignatures;
    /** A collection of signed state metrics */
    private final SignedStateMetrics signedStateMetrics;

    /**
     * Start empty, with no known signed states. The number of addresses in platform.hashgraph.getAddressBook() must not
     * change in the future. The addressBook must contain exactly the set of members who can sign the state. A signed
     * state is considered completed when it has signatures from a sufficient threshold of nodes.
     *
     * @param stateConfig        configuration for state
     * @param signedStateMetrics a collection of signed state metrics
     */
    public SignedStateManager(
            @NonNull final StateConfig stateConfig, @NonNull final SignedStateMetrics signedStateMetrics) {

        this.stateConfig = Objects.requireNonNull(stateConfig, "stateConfig");
        this.signedStateMetrics = Objects.requireNonNull(signedStateMetrics, "signedStateMetrics");

        this.savedSignatures =
                new StandardSequenceSet<>(0, stateConfig.maxAgeOfFutureStateSignatures(), SavedSignature::round);
    }

    /**
     * Add a state. State may be ignored if it is too old.
     *
     * @param reservedSignedState the signed state to add
     */
    public List<ReservedSignedState> addReservedState(@NonNull final ReservedSignedState reservedSignedState) {
        Objects.requireNonNull(reservedSignedState, "reservedSignedState");
        final SignedState signedState = reservedSignedState.get();

        if (signedState.getState().getHash() == null) {
            throw new IllegalArgumentException(
                    "Unhashed state for round " + signedState.getRound() + " added to the signed state manager");
        }

        // Double check that the signatures on this state are valid.
        // They may no longer be valid if we have done a data migration.
        signedState.pruneInvalidSignatures();

        // find any signatures that have been saved
        final List<SavedSignature> signatures = savedSignatures.getEntriesWithSequenceNumber(signedState.getRound());
        savedSignatures.removeSequenceNumber(signedState.getRound());
        signatures.forEach(ss -> addSignature(reservedSignedState, ss.memberId, ss.signature));

        lastStateRound = Math.max(lastStateRound, signedState.getRound());
        adjustSavedSignaturesWindow(signedState.getRound());

        if (!signedState.isComplete()) {
            final ReservedSignedState previousState = incompleteStates.put(signedState.getRound(), reservedSignedState);
            Optional.ofNullable(previousState).ifPresent(ReservedSignedState::close);
            // TODO log a warning, this should not happen
            return purgeOldStates();
        }
        return Stream.concat(Stream.of(reservedSignedState), purgeOldStates().stream())
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ReservedSignedState> handlePreconsensusScopedSystemTransactions(
            @NonNull final List<ScopedSystemTransaction<StateSignatureTransaction>> transactions) {
        return transactions.stream()
                .map(this::handlePreconsensusSignatureTransaction)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * An observer of pre-consensus state signatures.
     *
     * @param scopedTransaction the signature transaction
     */
    private ReservedSignedState handlePreconsensusSignatureTransaction(
            @NonNull final ScopedSystemTransaction<StateSignatureTransaction> scopedTransaction) {
        Objects.requireNonNull(scopedTransaction);

        final long round = scopedTransaction.transaction().getRound();
        final Signature signature = scopedTransaction.transaction().getStateSignature();

        signedStateMetrics.getStateSignaturesGatheredPerSecondMetric().cycle();

        if (lastStateRound != -1) {
            final long signatureAge = round - lastStateRound;
            signedStateMetrics.getStateSignatureAge().update(signatureAge);
        }

        final ReservedSignedState reservedState = incompleteStates.get(round);
        if (reservedState == null) {
            // This round has already been completed, or it is really old or in the future
            savedSignatures.add(new SavedSignature(round, scopedTransaction.submitterId(), signature));
            return null;
        }
        return addSignature(reservedState, scopedTransaction.submitterId(), signature);
    }

    public List<ReservedSignedState> handlePostconsensusScopedSystemTransactions(
            @NonNull final List<ScopedSystemTransaction<StateSignatureTransaction>> transactions) {
        return transactions.stream()
                .map(this::handlePostconsensusSignatureTransaction)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * An observer of post-consensus state signatures.
     *
     * @param scopedTransaction the signature transaction
     */
    private ReservedSignedState handlePostconsensusSignatureTransaction(
            @NonNull final ScopedSystemTransaction<StateSignatureTransaction> scopedTransaction) {
        Objects.requireNonNull(scopedTransaction);

        final long round = scopedTransaction.transaction().getRound();

        final ReservedSignedState reservedState = incompleteStates.get(round);
        // it isn't possible to receive a post-consensus signature transaction for a future round,
        // and if we don't have the state for an old round, we never will.
        // in both cases, the signature can be ignored
        if (reservedState == null) {
            return null;
        }

        return addSignature(
                reservedState,
                scopedTransaction.submitterId(),
                scopedTransaction.transaction().getStateSignature());
    }

    /**
     * Add a new signature to a signed state.
     *
     * @param reservedSignedState the state being signed
     * @param nodeId              the ID of the signer
     * @param signature           the signature on the state
     */
    private ReservedSignedState addSignature(
            @NonNull final ReservedSignedState reservedSignedState,
            @NonNull final NodeId nodeId,
            @NonNull final Signature signature) {
        Objects.requireNonNull(reservedSignedState, "signedState must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(signature, "signature must not be null");
        final SignedState signedState = reservedSignedState.get();

        if (signedState.addSignature(nodeId, signature)) {
            // at this point the signed state is complete for the first time
            signedStateMetrics.getStatesSignedPerSecondMetric().cycle();
            signedStateMetrics
                    .getAverageTimeToFullySignStateMetric()
                    .update(Duration.between(signedState.getCreationTimestamp(), Instant.now())
                            .toMillis());

            return incompleteStates.remove(signedState.getRound());
        }
        return null;
    }

    /**
     * Get the earliest round that is permitted to be stored in this data structure.
     *
     * @return the earliest round permitted to be stored
     */
    private long getEarliestPermittedRound() {
        return lastStateRound - stateConfig.roundsToKeepForSigning() + 1;
    }

    /**
     * Get rid of old states.
     */
    private List<ReservedSignedState> purgeOldStates() {
        final List<ReservedSignedState> purgedStates = new ArrayList<>();

        // Any state older than this is unconditionally removed.
        final long earliestPermittedRound = getEarliestPermittedRound();
        for (final Iterator<ReservedSignedState> iterator =
                        incompleteStates.values().iterator();
                iterator.hasNext(); ) {
            final ReservedSignedState reservedSignedState = iterator.next();
            final SignedState signedState = reservedSignedState.get();
            if (signedState.getRound() < earliestPermittedRound) {
                signedStateMetrics.getTotalUnsignedStatesMetric().increment();
                purgedStates.add(reservedSignedState);
                iterator.remove();
            }
        }

        signedStateMetrics.getUnsignedStatesMetric().update(incompleteStates.size());
        return purgedStates;
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
     * A signature that was received when there was no state with a matching round.
     */
    private record SavedSignature(long round, @NonNull NodeId memberId, @NonNull Signature signature) {}
}
