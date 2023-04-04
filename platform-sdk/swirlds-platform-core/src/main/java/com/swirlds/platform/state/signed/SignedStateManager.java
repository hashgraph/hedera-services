/*
 * Copyright (C) 2017-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static com.swirlds.platform.state.signed.SignedStateUtilities.newSignedStateWrapper;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.sequence.set.SequenceSet;
import com.swirlds.common.sequence.set.StandardSequenceSet;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * Data structures and methods to manage the lifecycle of signed states. This class ensures that the following states
 * are always in memory:
 * </p>
 *
 * <ul>
 * <li>
 * The most recent fully-signed state
 * </li>
 * <li>
 * All the non-ancient states that are not fully signed
 * </li>
 * <li>
 * Any state that is currently in the process of being written to disk (no matter how old it is)
 * </li>
 * <li>
 * Any state that is being used for a reconnect
 * </li>
 * <li>
 * Any state that the application has taken a reservation on
 * </li>
 * </ul>
 */
public class SignedStateManager {

    private static final Logger logger = LogManager.getLogger(SignedStateManager.class);

    /**
     * The latest signed state. May be unhashed. May or may not have all of its signatures.
     */
    private final SignedStateReference lastState = new SignedStateReference();

    /**
     * Signed states awaiting signatures.
     */
    private final SignedStateMap unsignedStates = new SignedStateMap();

    /**
     * States that have already been signed.
     */
    private final SignedStateMap signedStates = new SignedStateMap();

    private final StateConfig stateConfig;

    /**
     * A signature that was received when there was no state with a matching round.
     */
    private record SavedSignature(long round, long memberId, Signature signature) {
    }

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

        this.stateConfig = throwArgNull(stateConfig, "stateConfig");
        this.signedStateMetrics = throwArgNull(signedStateMetrics, "signedStateMetrics");
        this.newLatestCompleteStateConsumer =
                throwArgNull(newLatestCompleteStateConsumer, "newLatestCompleteStateConsumer");
        this.stateHasEnoughSignaturesConsumer =
                throwArgNull(stateHasEnoughSignaturesConsumer, "stateHasEnoughSignaturesConsumer");
        this.stateLacksSignaturesConsumer = throwArgNull(stateLacksSignaturesConsumer, "stateLacksSignaturesConsumer");

        this.savedSignatures =
                new StandardSequenceSet<>(0, stateConfig.maxAgeOfFutureStateSignatures(), SavedSignature::round);
    }

    /**
     * Get the round number of the last complete round. Will return -1 if there is not any recent round that has
     * gathered sufficient signatures.
     *
     * @return latest round for which we have a majority of signatures
     */
    public long getLastCompleteRound() {
        return signedStates.getLatestRound();
    }

    /**
     * Get a wrapper containing the last complete signed state.
     *
     * @return a wrapper with the latest complete signed state, or null if no recent states that are complete
     */
    public @NonNull AutoCloseableWrapper<SignedState> getLatestSignedState() {
        return signedStates.getLatest();
    }

    /**
     * Get a wrapper containing the latest immutable signed state. May be unhashed, may or may not have all required
     * signatures. State is returned with a reservation.
     *
     * @return a wrapper with the latest signed state, or null if none are complete
     */
    public @NonNull AutoCloseableWrapper<SignedState> getLatestImmutableState() {
        return lastState.get();
    }

    /**
     * Get the round of the latest immutable signed state.
     *
     * @return the round of the latest immutable signed state.
     */
    public long getLastImmutableStateRound() {
        return lastState.getRound();
    }

    /**
     * <p>
     * Get the latest signed states stored by this manager.
     * </p>
     *
     * <p>
     * This method is not thread safe. Do not use it for any new use cases.
     * </p>
     *
     * @return the latest signed states
     * @deprecated this method is not thread safe
     */
    @Deprecated
    public @NonNull List<SignedStateInfo> getSignedStateInfo() {
        // Since this method is not synchronized, it's possible we may add a state multiple times to this collection.
        // The map makes sure that duplicates are not returned to the caller.
        final Map<Long, SignedState> stateMap = new HashMap<>();

        unsignedStates.atomicIteration(iterator ->
                iterator.forEachRemaining(signedState -> stateMap.put(signedState.getRound(), signedState)));
        signedStates.atomicIteration(iterator ->
                iterator.forEachRemaining(signedState -> stateMap.put(signedState.getRound(), signedState)));

        // Sort the states based on round number
        final List<Long> rounds = new ArrayList<>(stateMap.keySet());
        Collections.sort(rounds);
        final List<SignedStateInfo> sortedStates = new ArrayList<>(rounds.size());
        for (final long round : rounds) {
            sortedStates.add(stateMap.get(round));
        }

        return sortedStates;
    }

    /**
     * Add a completed signed state, e.g. a state from reconnect or a state from disk. State is ignored if it is too old
     * (i.e. there is a newer complete state) or if it is not actually complete.
     *
     * @param signedState the signed state to add
     */
    public synchronized void addState(@NonNull final SignedState signedState) {
        throwArgNull(signedState, "signedState");

        if (signedState.getState().getHash() == null) {
            throw new IllegalArgumentException(
                    "Unhashed state for round " + signedState.getRound() + " added to the signed state manager");
        }

        // Double check that the signatures on this state are valid.
        // They may no longer be valid if we have done a data migration.
        signedState.pruneInvalidSignatures();

        if (signedState.isComplete()) {
            signedStates.put(signedState);
            notifyNewLatestCompleteState(signedState);
        } else {
            unsignedStates.put(signedState);
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
            lastState.set(signedState);
        }
    }

    /**
     * An observer of pre-consensus state signatures.
     *
     * @param round     the round that was signed
     * @param signerId  the ID of the signer
     * @param signature the signature on the hash
     */
    public synchronized void preConsensusSignatureObserver(
            @NonNull final Long round, @NonNull final Long signerId, @NonNull final Signature signature) {

        throwArgNull(round, "round");
        throwArgNull(signerId, "signerId");
        throwArgNull(signature, "signature");

        signedStateMetrics.getStateSignaturesGatheredPerSecondMetric().cycle();

        final long lastStateRound = lastState.getRound();
        if (lastStateRound != -1) {
            final long signatureAge = round - lastStateRound;
            signedStateMetrics.getStateSignatureAge().update(signatureAge);
        }

        try (final AutoCloseableWrapper<SignedState> wrapper = getIncompleteState(round)) {

            final SignedState signedState = wrapper.get();
            if (signedState == null) {
                // This round has already been completed, or it is really old or in the future
                savedSignatures.add(new SavedSignature(round, signerId, signature));
                return;
            }

            addSignature(signedState, signerId, signature);
        }
    }

    /**
     * Find the latest state that matches a predicate. States are scanned starting with the latest round, and the first
     * state encountered that matches the criteria is returned.
     *
     * @param criteria a predicate, the first encountered state that causes this to return true is returned
     * @return a wrapper around the first state encountered that causes the criteria to pass, or a wrapper around null
     * if no state causes the criteria to pass.
     */
    public synchronized @NonNull AutoCloseableWrapper<SignedState> findState( // TODO test
            @NonNull final Predicate<SignedState> criteria) {

        final List<SignedState> allStates = new ArrayList<>();

        signedStates.atomicIteration(it -> it.forEachRemaining(allStates::add));
        unsignedStates.atomicIteration(it -> it.forEachRemaining(allStates::add));

        // Sort the list from the highest round to the lowest round
        allStates.sort((a, b) -> Long.compare(b.getRound(), a.getRound()));

        for (final SignedState signedState : allStates) {
            if (criteria.test(signedState)) {
                return newSignedStateWrapper(signedState);
            }
        }

        return newSignedStateWrapper(null);
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
     * Given an iterator that walks over the states in {@link #unsignedStates}, remove any states that are too old.
     *
     * @param iterator an iterator that walks over a collection of signed states
     */
    private void removeOldUnsignedStates(@NonNull final Iterator<SignedState> iterator) {

        // Any state older than this is unconditionally released.
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
     * Given an iterator that walks over the states in {@link #signedStates}, remove any states that are too old.
     *
     * @param iterator an iterator that walks over a collection of signed states
     */
    private void removeOldSignedStates(@NonNull final Iterator<SignedState> iterator) {

        // Any state older than this is unconditionally removed.
        final long earliestPermittedRound = getEarliestPermittedRound();

        // The latest state that has gathered all of its signatures.
        final long latestSignedRound = signedStates.getLatestRound();

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
        unsignedStates.atomicIteration(this::removeOldUnsignedStates);
        signedStates.atomicIteration(this::removeOldSignedStates);

        signedStateMetrics.getUnsignedStatesMetric().update(unsignedStates.getSize());
        signedStateMetrics.geSignedStatesMetric().update(signedStates.getSize());
    }

    /**
     * Get an unsigned state for a particular round, if it exists.
     *
     * @param round the round in question
     * @return a wrapper around a signed state for a round, or a wrapper around null if a signed state for that round is
     * not present
     */
    private @NonNull AutoCloseableWrapper<SignedState> getIncompleteState(final long round) {
        return unsignedStates.get(round);
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
        if (signedStates.getLatestRound() < signedState.getRound()) {
            notifyNewLatestCompleteState(signedState);
        }

        signedStates.put(signedState);
        unsignedStates.remove(signedState.getRound());

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
            @NonNull final SignedState signedState, final long nodeId, @NonNull final Signature signature) {
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
        newLatestCompleteStateConsumer.newLatestCompleteStateEvent(new SignedStateWrapper(signedState));
    }

    /**
     * Send out a notification that a signed state was unable to be completely signed.
     *
     * @param signedState the state that was unable to be complete signed
     */
    private void notifyStateLacksSignatures(@NonNull final SignedState signedState) {
        stateLacksSignaturesConsumer.stateLacksSignatures(new SignedStateWrapper(signedState));
    }

    /**
     * Send out a notification that a signed state was able to collect enough signatures to become complete.
     *
     * @param signedState the state that now has enough signatures
     */
    private void notifyStateHasEnoughSignatures(@NonNull final SignedState signedState) {
        stateHasEnoughSignaturesConsumer.stateHasEnoughSignatures(new SignedStateWrapper(signedState));
    }
}
