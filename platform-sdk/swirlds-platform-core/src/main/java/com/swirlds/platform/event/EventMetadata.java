/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event;

import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.consensus.CandidateWitness;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * A class that stores temporary data that is used while calculating consensus inside the platform.
 * This data is not relevant after consensus has been calculated.
 */
public class EventMetadata implements Clearable {
    /** the self parent of this */
    private EventImpl selfParent;
    /** the other parent of this */
    private EventImpl otherParent;
    /** an estimate of what the consensus timestamp will be (could be a very bad guess) */
    private Instant estimatedTime;
    /** has this event been cleared (because it was old and should be discarded)? */
    private boolean cleared = false;
    /** is this a witness? (is round > selfParent's round, or there is no self parent?) */
    private boolean isWitness;
    /** has this witness decided as famous? */
    private boolean isFamous;
    /** is this both a witness and the fame election is over? */
    private boolean isFameDecided;
    /** is this event a judge? */
    private boolean isJudge;
    /** is this part of the consensus order yet? */
    private boolean isConsensus;
    /** the local time (not consensus time) at which the event reached consensus */
    private Instant reachedConsTimestamp;
    /** lastSee[m] is the last ancestor created by m (memoizes function from Swirlds-TR-2020-01) */
    private EventImpl[] lastSee;
    /**
     * stronglySeeP[m] is strongly-seen witness in parent round by m (memoizes function from
     * Swirlds-TR-2020-01)
     */
    private EventImpl[] stronglySeeP;
    /**
     * The first witness that's a self-ancestor in the self round (memoizes function from
     * Swirlds-TR-2020-01)
     */
    private EventImpl firstSelfWitnessS;
    /**
     * the first witness that's an ancestor in the self round (memoizes function from
     * Swirlds-TR-2020-01)
     */
    private EventImpl firstWitnessS;
    /**
     * temporarily used during any graph algorithm that needs to mark vertices (events) already
     * visited
     */
    private int mark;
    /**
     * the time at which each unique famous witness in the received round first received this event
     */
    private List<Instant> recTimes;
    /**
     * the created round of this event (max of parents', plus either 0 or 1. 1 if no parents. 0 if
     * neg infinity)
     */
    private long roundCreated = ConsensusConstants.ROUND_UNDEFINED;
    /** is there a consensus that this event is stale (no order, transactions ignored) */
    private boolean stale;
    /**
     * an array that holds votes for witness elections. the index for each vote matches the index of
     * the witness in the current election
     */
    private boolean[] votes;

    public EventMetadata() {
        this.estimatedTime = Instant.now(); // until a better estimate is found, just guess the time it is now
        // ConsensusImpl.currMark starts at 1 and counts up, so all events initially count as
        // unmarked
        this.mark = ConsensusConstants.EVENT_UNMARKED;
    }

    public EventMetadata(@Nullable final EventImpl selfParent, @Nullable final EventImpl otherParent) {
        this();
        this.selfParent = selfParent;
        this.otherParent = otherParent;
    }

    /**
     * Erase all references to other events within this event. This can be used so other events can
     * be garbage collected, even if this one still has things pointing to it. The numEventsInMemory
     * count is decremented here, and incremented when the event is instantiated, so it is important
     * to ensure that this is eventually called on every event.
     */
    @Override
    public void clear() {
        if (cleared) {
            return;
        }
        cleared = true;
        EventCounter.eventCleared();
        selfParent = null;
        otherParent = null;
        clearMetadata();
    }

    //////////////////////////////////////////
    // Getters and setters
    //////////////////////////////////////////

    /**
     * @return the self parent of this
     */
    public @Nullable EventImpl getSelfParent() {
        return selfParent;
    }

    /**
     * @param selfParent the self parent of this
     */
    public void setSelfParent(@Nullable final EventImpl selfParent) {
        this.selfParent = selfParent;
    }

    /**
     * @return the other parent of this
     */
    public @Nullable EventImpl getOtherParent() {
        return otherParent;
    }

    /**
     * @param otherParent the other parent of this
     */
    public void setOtherParent(@Nullable final EventImpl otherParent) {
        this.otherParent = otherParent;
    }

    /**
     * @return an estimate of what the consensus timestamp will be (could be a very bad guess)
     */
    public @NonNull Instant getEstimatedTime() {
        return estimatedTime;
    }

    /**
     * @param estimatedTime an estimate of what the consensus timestamp will be (could be a very bad
     *     guess)
     */
    public void setEstimatedTime(@NonNull final Instant estimatedTime) {
        this.estimatedTime = estimatedTime;
    }

    public boolean isWitness() {
        return isWitness;
    }

    public void setWitness(final boolean witness) {
        isWitness = witness;
    }

    public boolean isFamous() {
        return isFamous;
    }

    public void setFamous(final boolean famous) {
        isFamous = famous;
    }

    /**
     * @return is this both a witness and the fame election is over?
     */
    public boolean isFameDecided() {
        return isFameDecided;
    }

    /**
     * @param fameDecided is this both a witness and the fame election is over?
     */
    public void setFameDecided(final boolean fameDecided) {
        isFameDecided = fameDecided;
    }

    /**
     * @return true if this event is a judge
     */
    public boolean isJudge() {
        return isJudge;
    }

    /** Mark this event as a judge */
    public void setJudgeTrue() {
        isJudge = true;
    }

    /**
     * @return is this part of the consensus order yet?
     */
    public boolean isConsensus() {
        return isConsensus;
    }

    /**
     * @param consensus is this part of the consensus order yet?
     */
    public void setConsensus(final boolean consensus) {
        isConsensus = consensus;
    }

    /**
     * @return the local time (not consensus time) at which the event reached consensus
     */
    public @Nullable Instant getReachedConsTimestamp() {
        return reachedConsTimestamp;
    }

    /**
     * @param reachedConsTimestamp the local time (not consensus time) at which the event reached
     *     consensus
     */
    public void setReachedConsTimestamp(@NonNull final Instant reachedConsTimestamp) {
        this.reachedConsTimestamp = reachedConsTimestamp;
    }

    /**
     * @param m the member ID
     * @return last ancestor created by m (memoizes lastSee function from Swirlds-TR-2020-01)
     */
    public @Nullable EventImpl getLastSee(final int m) {
        return lastSee[m];
    }

    /**
     * remember event, the last ancestor created by m (memoizes lastSee function from
     * Swirlds-TR-2020-01)
     *
     * @param m the member ID
     * @param event the last seen {@link EventImpl} object created by m
     */
    public void setLastSee(final int m, @Nullable final EventImpl event) {
        lastSee[m] = event;
    }

    /**
     * Initialize the lastSee array to hold n elements (for n &ge; 0) (memoizes lastSee function
     * from Swirlds-TR-2020-01)
     *
     * @param n number of members in the initial address book
     */
    public void initLastSee(final int n) {
        lastSee = n == 0 ? null : new EventImpl[n];
    }

    /**
     * @return the number of elements lastSee holds (memoizes lastSee function from
     *     Swirlds-TR-2020-01)
     */
    public int sizeLastSee() {
        return lastSee == null ? 0 : lastSee.length;
    }

    /**
     * @param m the member ID
     * @return strongly-seen witness in parent round by m (memoizes stronglySeeP function from
     *     Swirlds-TR-2020-01)
     */
    public @Nullable EventImpl getStronglySeeP(final int m) {
        return stronglySeeP[m];
    }

    /**
     * remember event, the strongly-seen witness in parent round by m (memoizes stronglySeeP
     * function from Swirlds-TR-2020-01)
     *
     * @param m the member ID
     * @param event the strongly-seen witness in parent round created by m
     */
    public void setStronglySeeP(final int m, @Nullable final EventImpl event) {
        stronglySeeP[m] = event;
    }

    /**
     * Initialize the stronglySeeP array to hold n elements (for n &ge; 0) (memoizes stronglySeeP
     * function from Swirlds-TR-2020-01)
     *
     * @param n number of members in AddressBook
     */
    public void initStronglySeeP(final int n) {
        stronglySeeP = n == 0 ? null : new EventImpl[n];
    }

    /**
     * @return the number of elements stronglySeeP holds (memoizes stronglySeeP function from
     *     Swirlds-TR-2020-01)
     */
    public int sizeStronglySeeP() {
        return stronglySeeP == null ? 0 : stronglySeeP.length;
    }

    /**
     * @return The first witness that's a self-ancestor in the self round (memoizes function from
     *     Swirlds-TR-2020-01)
     */
    public @Nullable EventImpl getFirstSelfWitnessS() {
        return firstSelfWitnessS;
    }

    /**
     * @param firstSelfWitnessS The first witness that's a self-ancestor in the self round (memoizes
     *     function from Swirlds-TR-2020-01)
     */
    public void setFirstSelfWitnessS(@Nullable final EventImpl firstSelfWitnessS) {
        this.firstSelfWitnessS = firstSelfWitnessS;
    }

    /**
     * @return the first witness that's an ancestor in the self round (memoizes function from
     *     Swirlds-TR-2020-01)
     */
    public @Nullable EventImpl getFirstWitnessS() {
        return firstWitnessS;
    }

    /**
     * @param firstWitnessS the first witness that's an ancestor in the self round (memoizes
     *     function from Swirlds-TR-2020-01)
     */
    public void setFirstWitnessS(@Nullable final EventImpl firstWitnessS) {
        this.firstWitnessS = firstWitnessS;
    }

    /**
     * @return temporarily used during any graph algorithm that needs to mark vertices (events)
     *     already visited
     */
    public int getMark() {
        return mark;
    }

    /**
     * @param mark temporarily used during any graph algorithm that needs to mark vertices (events)
     *     already visited
     */
    public void setMark(final int mark) {
        this.mark = mark;
    }

    /**
     * @return the time at which each unique famous witness in the received round first received
     *     this event
     */
    public @Nullable List<Instant> getRecTimes() {
        return recTimes;
    }

    /**
     * @param recTimes the time at which each unique famous witness in the received round first
     *     received this event
     */
    public void setRecTimes(@Nullable final List<Instant> recTimes) {
        this.recTimes = recTimes;
    }

    public long getRoundCreated() {
        return roundCreated;
    }

    public void setRoundCreated(final long roundCreated) {
        this.roundCreated = roundCreated;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(final boolean stale) {
        this.stale = stale;
    }

    /**
     * Initialize the voting array
     *
     * @param numWitnesses the number of witnesses we are voting on
     */
    public void initVoting(final int numWitnesses) {
        if (votes == null || votes.length < numWitnesses) {
            votes = new boolean[numWitnesses];
            return;
        }
        Arrays.fill(votes, false);
    }

    /**
     * Get this witness' vote on the witness provided
     *
     * @param witness the witness being voted on
     * @return true if it's a YES vote, false if it's a NO vote
     */
    public boolean getVote(@NonNull final CandidateWitness witness) {
        return votes != null && votes.length > witness.getElectionIndex() && votes[witness.getElectionIndex()];
    }

    /**
     * Set this witness' vote on the witness provided
     *
     * @param witness the witness being voted on
     * @param vote true if it's a YES vote, false if it's a NO vote
     */
    public void setVote(@NonNull final CandidateWitness witness, final boolean vote) {
        this.votes[witness.getElectionIndex()] = vote;
    }

    /** Clear all metadata used to calculate consensus, this metadata changes with every round */
    public void clearMetadata() {
        clearJudgeFlags();
        clearNonJudgeMetadata();
    }

    private void clearJudgeFlags() {
        setWitness(false);
        setFamous(false);
        setFameDecided(false);
        isJudge = false;
    }

    private void clearNonJudgeMetadata() {
        initLastSee(0);
        initStronglySeeP(0);
        setFirstSelfWitnessS(null);
        setFirstWitnessS(null);
        setRecTimes(null);
    }
}
