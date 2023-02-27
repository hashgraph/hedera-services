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

package com.swirlds.platform.consensus;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.MinGenInfo;
import java.util.List;
import java.util.Objects;
import java.util.stream.LongStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Stores all hashgraph round information in a single place.
 *
 * <p>It keeps track of what rounds are stored and creates elections when needed.
 */
public class ConsensusRounds {
    private static final Logger LOG = LogManager.getLogger(ConsensusRounds.class);
    /** consensus configuration */
    private final ConsensusConfig config;
    /** stores the minimum generation for all decided and non-expired rounds */
    private final SequentialRingBuffer<MinGenInfo> minGenStorage;
    /** The maximum round created of all the known witnesses */
    private long maxRoundCreated = ConsensusConstants.ROUND_UNDEFINED;
    /** The round we are currently voting on */
    private final ElectionRound electionRound = new ElectionRound();

    /** Constructs an empty object */
    public ConsensusRounds(final ConsensusConfig config, final SequentialRingBuffer<MinGenInfo> minGenStorage) {
        this.config = config;
        this.minGenStorage = minGenStorage;
        reset();
    }

    /** Reset this instance to its initial state, equivalent to creating a new instance */
    public void reset() {
        minGenStorage.reset(ConsensusConstants.ROUND_FIRST);
        maxRoundCreated = ConsensusConstants.ROUND_UNDEFINED;
        electionRound.reset();
    }

    /**
     * @return the round number below which the fame of all witnesses has been decided for all
     *     earlier rounds
     */
    public long getFameDecidedBelow() {
        return electionRound.getRound();
    }

    /**
     * A new witness has been received, add it to the appropriate round and create an election if
     * needed
     *
     * @param witness the new witness
     */
    public void newWitness(final EventImpl witness) {
        // track the latest known round created
        maxRoundCreated = Math.max(witness.getRoundCreated(), maxRoundCreated);
        if (!isElectionRound(witness.getRoundCreated())) {
            // this is not a witness we are currently voting on, so no need to create elections for
            // it yet
            return;
        }

        // theorem says this witness can't be famous if round R+2 exists
        // if this is true, we immediately mark this witness as not famous without any elections
        if (maxRoundCreated >= witness.getRoundCreated() + 2) {
            witness.setFamous(false);
            witness.setFameDecided(true);
            return;
        }
        // the theorem doesn't apply, so we can't decide yet, so elections are needed
        electionRound.addWitness(witness);
    }

    /**
     * Notifies this instance that recalculating is starting again, so that any metadata can be
     * reset.
     */
    public void recalculating() {
        // when starting recalculation, the highest round created will be the last round that is
        // decided
        maxRoundCreated = getLastRoundDecided();
    }

    /**
     * Checks if the event is older than the round generation of the latest decided round. If no
     * round has been decided yet, returns false.
     *
     * @param event the event to check
     * @return true if its older
     */
    public boolean isOlderThanDecidedRoundGeneration(final EventImpl event) {
        return isAnyRoundDecided() // if no round has been decided, it can't be older
                && minGenStorage.get(getLastRoundDecided()).minimumGeneration() > event.getGeneration();
    }

    /**
     * @return true if we have decided fame for at least one round, this is false only at genesis
     */
    private boolean isAnyRoundDecided() {
        return electionRound.getRound() > ConsensusConstants.ROUND_FIRST;
    }

    /**
     * Notifies the instance that the current elections have been decided. This will start the next
     * election.
     */
    public void currentElectionDecided() {
        minGenStorage.add(electionRound.getRound(), electionRound.creatMinGenInfo());
        electionRound.startNextElection();
        // Delete the oldest rounds with round number which is expired
        minGenStorage.removeOlderThan(getFameDecidedBelow() - config.roundsExpired());
    }

    /**
     * Check if this event is a judge in the latest decided round
     *
     * @param event the event to check
     * @return true if it is a judge in the last round that was decided
     */
    public boolean isLastDecidedJudge(final EventImpl event) {
        return event.isJudge() && event.getRoundCreated() == getLastRoundDecided();
    }

    private long getLastRoundDecided() {
        return electionRound.getRound() - 1;
    }

    /**
     * @return the current round we are voting on
     */
    public long getElectionRoundNumber() {
        return electionRound.getRound();
    }

    /**
     * @return the round we are currently voting on
     */
    public ElectionRound getElectionRound() {
        return electionRound;
    }

    /**
     * Is the supplied round number the current election round
     *
     * @param round the round number being queried
     * @return true if this is the election round number
     */
    private boolean isElectionRound(final long round) {
        return getElectionRoundNumber() == round;
    }

    /**
     * Used when loading rounds from a starting point (a signed state). It will create rounds with
     * their minimum generation numbers, but we won't know about the witnesses in these rounds. We
     * also don't care about any other information except for minimum generation since these rounds
     * have already been decided beforehand.
     *
     * @param minGen a list of round numbers and round generation pairs, in ascending round numbers
     */
    public void loadFromMinGen(final List<MinGenInfo> minGen) {
        minGenStorage.reset(minGen.get(0).round());
        for (final MinGenInfo roundGenPair : minGen) {
            minGenStorage.add(roundGenPair.round(), roundGenPair);
        }
        electionRound.setRound(minGenStorage.getLatest().round() + 1);
    }

    /**
     * @return A list of {@link MinGenInfo} for all decided and non-ancient rounds
     */
    public List<MinGenInfo> getMinGenInfo() {
        final long oldestNonAncientRound =
                RoundCalculationUtils.getOldestNonAncientRound(config.roundsNonAncient(), getFameDecidedBelow());
        return LongStream.range(oldestNonAncientRound, getFameDecidedBelow())
                .mapToObj(this::getMinGen)
                .filter(Objects::nonNull)
                .toList();
    }

    private MinGenInfo getMinGen(final long round) {
        final MinGenInfo minGenInfo = minGenStorage.get(round);
        if (minGenInfo == null) {
            LOG.error(
                    LogMarker.EXCEPTION.getMarker(),
                    "Missing round {}. Fame decided below {}, oldest non-ancient round {}",
                    round,
                    getFameDecidedBelow(),
                    RoundCalculationUtils.getOldestNonAncientRound(config.roundsNonAncient(), getFameDecidedBelow()));
            return null;
        }
        return minGenInfo;
    }
}
