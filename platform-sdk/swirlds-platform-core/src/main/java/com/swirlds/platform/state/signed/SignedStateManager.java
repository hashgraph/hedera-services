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

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.sequence.set.SequenceSet;
import com.swirlds.common.sequence.set.StandardSequenceSet;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.reconnect.emergency.EmergencyStateFinder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
public class SignedStateManager implements EmergencyStateFinder {

    private static final Logger logger = LogManager.getLogger(SignedStateManager.class);

    /**
     * The latest signed state signed by a sufficient threshold of nodes. If no recent fully signed state is available,
     * then this will hold a null value.
     */
    private final SignedStateReference lastCompleteSignedState = new SignedStateReference();

    /**
     * The latest signed state. May be unhashed. May or may not have all of its signatures.
     */
    private final SignedStateReference lastState = new SignedStateReference();

    /**
     * Signed states awaiting signatures. These states are fresh. That is, there exist no states from later rounds that
     * have collected enough signatures to be complete.
     */
    private final SignedStateMap freshUnsignedStates = new SignedStateMap();

    /**
     * Signed states awaiting signatures. These states are stale. That is, there are states from later rounds that have
     * collected enough signatures to be complete. We keep these states around for a while since we may want to write
     * them to disk or use them to detect ISS events.
     */
    private final SignedStateMap staleUnsignedStates = new SignedStateMap();

    private final StateConfig stateConfig;

    /**
     * A signature that was received when there was no state with a matching round.
     */
    private record SavedSignature(long round, long memberId, Signature signature) {}

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
            final StateConfig stateConfig,
            final SignedStateMetrics signedStateMetrics,
            final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer,
            final StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer,
            final StateLacksSignaturesConsumer stateLacksSignaturesConsumer) {

        this.stateConfig = stateConfig;
        this.signedStateMetrics = signedStateMetrics;

        this.savedSignatures =
                new StandardSequenceSet<>(0, stateConfig.maxAgeOfFutureStateSignatures(), SavedSignature::round);

        this.newLatestCompleteStateConsumer = newLatestCompleteStateConsumer;
        this.stateHasEnoughSignaturesConsumer = stateHasEnoughSignaturesConsumer;
        this.stateLacksSignaturesConsumer = stateLacksSignaturesConsumer;
    }

    /**
     * Get the round number of the last complete round. Will return -1 if there is not any recent round that has
     * gathered sufficient signatures.
     *
     * @return latest round for which we have a majority of signatures
     */
    public long getLastCompleteRound() {
        return lastCompleteSignedState.getRound();
    }

    /**
     * Get a wrapper containing the last complete signed state.
     *
     * @return a wrapper with the latest complete signed state, or null if no recent states that are complete
     */
    public AutoCloseableWrapper<SignedState> getLatestSignedState() {
        return lastCompleteSignedState.get();
    }

    /**
     * Get a wrapper containing the latest immutable signed state. May be unhashed, may or may not have all required
     * signatures. State is returned with a reservation.
     *
     * @return a wrapper with the latest signed state, or null if none are complete
     */
    public AutoCloseableWrapper<SignedState> getLatestImmutableState() {
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
    public List<SignedStateInfo> getSignedStateInfo() {
        // Since this method is not synchronized, it's possible we may add a state multiple times to this collection.
        // The map makes sure that duplicates are not returned to the caller.
        final Map<Long, SignedState> stateMap = new HashMap<>();

        try (final AutoCloseableWrapper<SignedState> wrapper = lastCompleteSignedState.get()) {
            if (wrapper.get() != null) {
                stateMap.put(wrapper.get().getRound(), wrapper.get());
            }
        }

        freshUnsignedStates.atomicIteration(iterator ->
                iterator.forEachRemaining(signedState -> stateMap.put(signedState.getRound(), signedState)));

        staleUnsignedStates.atomicIteration(iterator ->
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
     * Hash a state and start collecting signatures for it.
     *
     * @param signedState the signed state to be kept by the manager
     */
    public synchronized void addUnsignedState(final SignedState signedState) {
        lastState.set(signedState);

        freshUnsignedStates.put(signedState);

        gatherSavedSignatures(signedState);

        adjustSavedSignaturesWindow(signedState.getRound());
        purgeOldStates();
    }

    /**
     * Add a completed signed state, e.g. a state from reconnect or a state from disk. State is ignored if it is too old
     * (i.e. there is a newer complete state) or if it is not actually complete.
     *
     * @param signedState the signed state to add
     */
    public synchronized void addCompleteSignedState(final SignedState signedState) {

        if (signedState.getState().getHash() == null) {
            throw new IllegalStateException(
                    "Unhashed state for round " + signedState.getRound() + " added to the signed state manager");
        }

        // Double check that the signatures on this state are valid.
        // They may no longer be valid if we have done a data migration.
        signedState.pruneInvalidSignatures();

        adjustSavedSignaturesWindow(signedState.getRound());

        if (!signedState.isComplete()) {
            logger.warn(
                    STARTUP.getMarker(),
                    "Signed state for round {} lacks a complete set of valid signatures",
                    signedState.getRound());
            // If the state is an emergency recovery state, it will not be fully signed. But it needs
            // to be stored here regardless so this node is able to be the teacher in emergency reconnects.
            freshUnsignedStates.put(signedState);
            setLastStateIfNewer(signedState);
        } else if (signedState.getRound() <= lastCompleteSignedState.getRound()) {
            // We have a newer signed state
            logger.warn(
                    EXCEPTION.getMarker(),
                    "Latest complete signed state is from round {}, " + "completed signed state for round {} rejected",
                    lastCompleteSignedState.getRound(),
                    signedState.getRound());
        } else {
            setLastCompleteSignedState(signedState);
            setLastStateIfNewer(signedState);
            purgeOldStates();
        }
    }

    /**
     * Sets the last state to this signed state, iff it is more recent than the current last state
     *
     * @param signedState the signed state to use
     */
    private void setLastStateIfNewer(final SignedState signedState) {
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
            final Long round, final Long signerId, final Signature signature) {

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
     * {@inheritDoc}
     */
    @Override
    public synchronized AutoCloseableWrapper<SignedState> find(final long round, final Hash hash) {
        if (!lastCompleteSignedState.isNull()) {
            // Return a more recent fully signed state, if available
            if (round < lastCompleteSignedState.getRound()) {
                return lastCompleteSignedState.get();
            }

            // If the latest state exactly matches the request, return it
            try (final AutoCloseableWrapper<SignedState> lastComplete = lastCompleteSignedState.get()) {
                if (stateMatches(lastComplete.get(), round, hash)) {
                    lastComplete.get().reserve();
                    return lastComplete;
                }
            }
        }

        // The requested round is later than the latest fully signed state.
        // Check if any of the fresh states match the request exactly.
        return freshUnsignedStates.find(ss -> stateMatches(ss, round, hash));
    }

    private static boolean stateMatches(final SignedState signedState, final long round, final Hash hash) {
        return signedState != null
                && signedState.getRound() == round
                && signedState.getState().getHash().equals(hash);
    }

    /**
     * Mark a SignedState as the most recent completed signed state.
     */
    private void setLastCompleteSignedState(final SignedState signedState) {

        if (signedState.getRound() <= lastCompleteSignedState.getRound()) {
            throw new IllegalStateException(
                    "last complete signed state is from round " + lastCompleteSignedState.getRound()
                            + ", cannot set last complete state from round " + signedState.getRound());
        }

        lastCompleteSignedState.set(signedState);

        freshUnsignedStates.atomicIteration(iterator -> {
            while (iterator.hasNext()) {
                final SignedState next = iterator.next();

                if (next.getRound() < signedState.getRound()) {
                    // This state is older than the most recently signed state, so it is stale now
                    staleUnsignedStates.put(next);
                    iterator.remove();
                }
            }
        });

        notifyNewLatestCompleteState(signedState);
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
     * Given an iterator that walks over a collection of signed states, remove any states that are too old.
     *
     * @param iterator an iterator that walks over a collection of signed states
     */
    private void removeOldStatesFromIterator(final Iterator<SignedState> iterator) {
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
     * Get rid of old states.
     */
    private void purgeOldStates() {
        freshUnsignedStates.atomicIteration(this::removeOldStatesFromIterator);
        staleUnsignedStates.atomicIteration(this::removeOldStatesFromIterator);

        signedStateMetrics.getFreshStatesMetric().update(freshUnsignedStates.getSize());
        signedStateMetrics.getStaleStatesMetric().update(staleUnsignedStates.getSize());

        if (lastCompleteSignedState.getRound() < getEarliestPermittedRound()) {
            lastCompleteSignedState.set(null);
        }
    }

    /**
     * Get an unsigned state for a particular round, if it exists.
     *
     * @param round the round in question
     * @return a wrapper around a signed state for a round, or a wrapper around null if a signed state for that round is
     * not present
     */
    private AutoCloseableWrapper<SignedState> getIncompleteState(final long round) {
        final AutoCloseableWrapper<SignedState> wrapper = freshUnsignedStates.get(round);
        if (wrapper.get() != null) {
            return wrapper;
        }
        return staleUnsignedStates.get(round);
    }

    /**
     * Gather and apply all signatures that were previously saved for a signed state.
     *
     * @param signedState a signed state that is now able to collect signatures
     */
    private void gatherSavedSignatures(final SignedState signedState) {
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
    private void signedStateNewlyComplete(final SignedState signedState) {
        signedStateMetrics.getStatesSignedPerSecondMetric().cycle();
        signedStateMetrics
                .getAverageTimeToFullySignStateMetric()
                .update(Duration.between(signedState.getCreationTimestamp(), Instant.now())
                        .toMillis());

        notifyStateHasEnoughSignatures(signedState);

        if (signedState.getRound() > lastCompleteSignedState.getRound()) {
            setLastCompleteSignedState(signedState);
        }

        freshUnsignedStates.remove(signedState.getRound());
        staleUnsignedStates.remove(signedState.getRound());
    }

    /**
     * Add a new signature to a signed state.
     *
     * @param signedState the state being signed
     * @param nodeId      the ID of the signer
     * @param signature   the signature on the state
     */
    private void addSignature(final SignedState signedState, final long nodeId, final Signature signature) {
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
    private void notifyNewLatestCompleteState(final SignedState signedState) {
        newLatestCompleteStateConsumer.newLatestCompleteStateEvent(new SignedStateWrapper(signedState));
    }

    /**
     * Send out a notification that a signed state was unable to be completely signed.
     *
     * @param signedState the state that was unable to be complete signed
     */
    private void notifyStateLacksSignatures(final SignedState signedState) {
        stateLacksSignaturesConsumer.stateLacksSignatures(new SignedStateWrapper(signedState));
    }

    /**
     * Send out a notification that a signed state was able to collect enough signatures to become complete.
     *
     * @param signedState the state that now has enough signatures
     */
    private void notifyStateHasEnoughSignatures(final SignedState signedState) {
        stateHasEnoughSignaturesConsumer.stateHasEnoughSignatures(new SignedStateWrapper(signedState));
    }
}
