/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import com.swirlds.platform.crypto.CryptoConstants;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.internal.EventImpl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hold all of the information about a round, such as lists of witnesses, statistics about them, and all the
 * elections that are active in that round.
 * <p>
 * An inner class ElectionRound is used to store information about every member's vote in one round on the
 * question of whether some previous witness is famous. The ElectionRound objects connect to each other as a
 * double-linked grid, and each row of it is linked to from a RoundInfo object.
 */
public class RoundInfo {

    /**
     * A value which represents that the minimum witness generation number of a round is unassigned, so
     * is undefined.
     *
     * An event's computed generation number is always non-negative, so after the min witness generation
     * for a round has been assigned to a value that was computed from parent events, it is never again undefined.
     *
     * This is the initial value of the minimum witness generation for a round.
     */
    protected static final long MIN_FAMOUS_WITNESS_GENERATION_UNDEFINED = EventConstants.GENERATION_UNDEFINED;

    /**
     * the round this is about ({@link EventConstants#MINIMUM_ROUND_CREATED} is first)
     */
    private final long round;

    /**
     * are all the famous witnesses known for this round?
     */
    protected boolean fameDecided = false;

    /**
     * number of known witnesses in this round
     */
    protected int numWitnesses = 0;

    /**
     * number of known witnesses in this round with unknown fame
     */
    protected int numUnknownFame = 0;

    /**
     * these witnesses are the first event in this round by each member
     */
    protected final List<EventImpl> witnesses = Collections.synchronizedList(new ArrayList<>());

    /**
     * The judges (unique famous witnesses) in this round. Element i is the one created by member i, or null if none
     */
    protected final EventImpl[] judges;

    /**
     * XOR of sigs of all famous events
     */
    protected final byte[] whitening = new byte[CryptoConstants.SIG_SIZE_BYTES];

    /**
     * each witness has one election per future round, to decide whether it is famous. This should
     * only be accessed by Hashgraph from within a synchronized method, because it is not thread-safe.
     * this is a quadruply linked list, with 4 links in each ElectionRound object.
     */
    protected ElectionRound elections = null;

    /**
     * the minimum generation of all the famous witnesses in this round. Initialized to
     * {@link RoundInfo#MIN_FAMOUS_WITNESS_GENERATION_UNDEFINED}.
     */
    private volatile long minGeneration = MIN_FAMOUS_WITNESS_GENERATION_UNDEFINED;

    /**
     * @return the round number
     */
    protected long getRound() {
        return round;
    }

    /**
     * Get the minimum generation number of all the famous witnesses in this round. If there are no witnesses
     * (which may happen when loading from signed state), the value
     * {@link RoundInfo#MIN_FAMOUS_WITNESS_GENERATION_UNDEFINED} is returned.
     *
     * @return the generation number
     */
    protected long getMinGeneration() {
        return minGeneration;
    }

    /**
     * Set the minimum gen number for this round. If the min gen at entry is
     * {@link RoundInfo#MIN_FAMOUS_WITNESS_GENERATION_UNDEFINED}, the given generation is used,
     * else the min gen is updated to min{min gen, new min gen}.
     *
     * This assumes that {@code generation} &gt;= {@link RoundInfo#MIN_FAMOUS_WITNESS_GENERATION_UNDEFINED}
     *
     * @param generation
     * 		the generation value to use
     */
    protected void updateMinGeneration(long generation) {
        minGeneration = (minGeneration == -1) ? generation : Math.min(minGeneration, generation);
    }

    /**
     * One round of votes about the fame of one witness. These link to form a doubly-linked grid, with one
     * doubly-linked list per RoundInfo.
     */
    public static class ElectionRound {
        final boolean[] vote; // vote by each member in this round for event being famous
        final EventImpl event; // the event whose fame is being voted on
        final long age; // = round(this ElectionRound) - round(event)
        final RoundInfo roundInfo; // the RoundInfo for the round holding this election

        // the following 4 form a 2D linked list (or "linked grid"), with
        // each row being a doubly-linked list, and each column being a doubly-linked list.
        ElectionRound nextRound; // Election in next-higher round for this witness (null if no more)
        ElectionRound prevRound; // Election in the round before this one about the same witness
        ElectionRound nextElection; // the next Election (for next witness to decide) in this round
        ElectionRound prevElection; // the previous Election (for prev witness to decide) in this round

        ElectionRound(RoundInfo roundInfo, int numMembers, EventImpl event, long age) {
            this.vote = new boolean[numMembers];
            this.event = event;
            this.age = age;
            this.roundInfo = roundInfo;
            this.nextRound = null;
            this.prevRound = null;
            this.nextElection = null;
            this.prevElection = null;
        }

        /**
         * @return vote by each member in this round for event being famous
         */
        public boolean[] getVote() {
            return vote.clone();
        }

        /**
         * @return round(this ElectionRound) - round(event)
         */
        public long getAge() {
            return age;
        }
    }

    /**
     * constructor for the RoundInfo to describe the given round number (0 is first)
     *
     * @param round
     * 		the round it will be used to describe
     * @param numMembers
     * 		the number of members currently in the address book
     */
    protected RoundInfo(long round, int numMembers) {
        this.round = round;
        this.judges = new EventImpl[numMembers];
    }

    /**
     * Add a famous witness to the round (ignore if it's not a judge: a unique famous witness) and set the minGeneration
     *
     * @param w
     * 		the witness to add
     */
    protected void addFamousWitness(EventImpl w) {
        int creator = (int) w.getCreatorId();
        if (judges[creator] == null) {
            judges[creator] = w;
        } else {
            // if this creator forked, then the judge is the "unique" famous witness, which is the one with minimum hash
            // (where "minimum" is the lexicographically-least signed byte array)
            if (Utilities.arrayCompare(
                            w.getBaseHash().getValue(),
                            judges[creator].getBaseHash().getValue())
                    < 0) {
                judges[creator] = w;
            }
        }

        updateMinGeneration(w.getGeneration());
    }
}
