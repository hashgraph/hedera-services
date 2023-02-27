/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.consensus.RoundCalculationUtils;
import com.swirlds.platform.consensus.RoundNumberProvider;
import com.swirlds.platform.state.MinGenInfo;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Stores all hashgraph round information in a single place.
 *
 * It maps round numbers to RoundInfo, as well as keeping track of what rounds are stored. It updates graph generations,
 * that are based on rounds, when the appropriate event happens (new consensus round, expired a round, etc.).
 *
 * This is a first version, the plan is to put all round storing logic into this class.
 */
class ConsensusRounds implements GraphGenerations, RoundNumberProvider {
    private static final Logger logger = LogManager.getLogger(ConsensusRounds.class);
    private final ConsensusConfig config;

    /** the address book of the network */
    private final AddressBook addressBook;
    /**
     * "rounds" is a HashMap mapping round number (starts at 0) to a RoundInfo that knows the witnesses etc.
     * for that round. It includes the max created round of all events ever added to the hashgraph (by
     * consRecordEvent()), but it may not include very old rounds that have already been decided and
     * discarded.
     */
    private final Map<Long, RoundInfo> rounds;
    /**
     * maximum round number of all events stored in "rounds", or -1 if none. This is the max round created
     * of all events ever added to the hashgraph.
     */
    private final AtomicLong maxRound = new AtomicLong(RoundNumberProvider.ROUND_UNDEFINED);
    /**
     * minimum round number of all events stored in "rounds", or -1 if none. This may not be the min round
     * created of all events ever added to the hashgraph, since some of the older rounds may have been
     * decided and discarded.
     */
    private final AtomicLong minRound = new AtomicLong(RoundNumberProvider.ROUND_UNDEFINED);
    /** fame has been decided for all rounds less than this, but not for this round. */
    private final AtomicLong fameDecidedBelow = new AtomicLong(1);
    /**
     * The minimum judge generation number from the oldest non-expired round, if we have expired any rounds.
     * Else, this is {@link GraphGenerations#FIRST_GENERATION}.
     * <p>
     * Updated only on consensus thread, read concurrently from gossip threads.
     */
    private volatile long minRoundGeneration = GraphGenerations.FIRST_GENERATION;

    /**
     * the minimum generation of all the judges that are not ancient
     */
    private volatile long minGenNonAncient = GraphGenerations.FIRST_GENERATION;

    /**
     * The minimum judge generation number from the most recent fame-decided round, if there is one.
     * Else, this is {@link GraphGenerations#FIRST_GENERATION}.
     * <p>
     * Updated only on consensus thread, read concurrently from gossip threads.
     */
    private volatile long maxRoundGeneration = GraphGenerations.FIRST_GENERATION;

    /**
     * Constructs an empty object
     *
     * @param addressBook
     * 		the address book of the network
     */
    ConsensusRounds(final ConsensusConfig config, final AddressBook addressBook) {
        this.config = config;
        this.addressBook = addressBook;
        rounds = new ConcurrentHashMap<>();
    }

    /**
     * @param round
     * 		the round number to retrieve
     * @return the {@link RoundInfo} for this round number, or null if it doesn't exist
     */
    RoundInfo get(final long round) {
        return rounds.get(round);
    }

    /**
     * Stores a new round
     *
     * @param round
     * 		the round number
     * @param roundInfo
     * 		the data of the round
     */
    void put(final long round, final RoundInfo roundInfo) {
        rounds.put(round, roundInfo);
        maxRound.set(Math.max(maxRound.get(), round));
        minRound.set((minRound.get() == -1) ? round : Math.min(minRound.get(), round));
    }

    /**
     * Removes a round from storage.
     *
     * Note: this method does not update minRound since it is only called after {@link #aboutToRemoveBelow(long)} where
     * that value is updated
     *
     * @param round
     * 		the round number to remove
     */
    void remove(final long round) {
        rounds.remove(round);
    }

    /**
     * Notifies the round storage that it is about to expire all rounds below a certain number.
     *
     * @param round
     * 		the highest round number that will be kept
     */
    void aboutToRemoveBelow(final long round) {
        minRound.set(round);
        updateMinRoundGeneration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMaxRound() {
        return maxRound.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMinRound() {
        return minRound.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getFameDecidedBelow() {
        return fameDecidedBelow.get();
    }

    /**
     * Notifies the instance that fame has been decided for all rounds below this one.
     *
     * Since the maxRoundGeneration and minGenNonAncient depend on this value, they are updated as well.
     *
     * @param round
     * 		fame has been decided for all rounds less than this, but not for this round
     */
    void setFameDecidedBelow(final long round) {
        fameDecidedBelow.set(round);
        updateMaxRoundGeneration();
        updateMinGenNonAncient();
    }

    /**
     * Return {@link RoundInfo#getMinGeneration()} for the requested round
     *
     * @param round
     * 		the round number
     * @return the round generation of the requested round
     */
    public long getRoundGeneration(final long round) {
        return rounds.get(round).getMinGeneration();
    }

    @Override
    public long getMinRoundGeneration() {
        return minRoundGeneration;
    }

    @Override
    public long getMinGenerationNonAncient() {
        return minGenNonAncient;
    }

    @Override
    public long getMaxRoundGeneration() {
        return maxRoundGeneration;
    }

    /**
     * Update the max round generation
     *
     * Executed only on consensus thread.
     */
    private void updateMaxRoundGeneration() {
        final RoundInfo round = rounds.get(fameDecidedBelow.get() - 1);
        if (round == null) {
            // this should never happen
            logger.error(
                    LogMarker.EXCEPTION.getMarker(),
                    "(fameDecidedBelow.get() - 1) round is null in updateMaxRoundGeneration()");
            return;
        }
        long newMaxRoundGeneration = round.getMinGeneration();

        // 13 May 2021
        // Guarantee that the round generation is non-decreasing. When we have a local events file,
        // this condition will be implicit, so this clause will be removed.
        newMaxRoundGeneration = Math.max(newMaxRoundGeneration, maxRoundGeneration);

        maxRoundGeneration = newMaxRoundGeneration;
    }

    /**
     * Update the oldest non-ancient round generation
     *
     * Executed only on consensus thread.
     */
    private void updateMinGenNonAncient() {
        final long nonAncientRound =
                RoundCalculationUtils.getOldestNonAncientRound(config.roundsNonAncient(), fameDecidedBelow.get());
        final RoundInfo ri = rounds.get(nonAncientRound);
        if (ri == null) {
            // should never happen
            logger.error(LogMarker.EXCEPTION.getMarker(), "nonAncientRound is null in updateMinGenNonAncient()");
            return;
        }
        minGenNonAncient = ri.getMinGeneration();
    }

    /**
     * Update the min round judge generation.
     *
     * Executed only on consensus thread.
     */
    private void updateMinRoundGeneration() {
        final RoundInfo round = rounds.get(getMinRound());
        if (round == null) {
            // this should never happen
            logger.error(LogMarker.EXCEPTION.getMarker(), "min round is null in updateMinRoundGeneration()");
            return;
        }

        long newMinRoundGeneration = round.getMinGeneration();

        // 13 May 2021
        // Guarantee that the round generation is non-decreasing. When we have a local events file,
        // this condition will be implicit, so this clause will be removed.
        newMinRoundGeneration = Math.max(newMinRoundGeneration, minRoundGeneration);

        minRoundGeneration = newMinRoundGeneration;
    }

    /**
     * Used ONLY by the constructor that loads data from a signed state.
     * It will create all the rounds that we have round generation numbers for.
     *
     * @param minGen
     * 		a list of round numbers and round generation pairs, in ascending round numbers
     */
    void createRoundsForSignedStateConstructor(final List<MinGenInfo> minGen) {
        minRound.set(minGen.get(0).round());
        maxRound.set(minGen.get(minGen.size() - 1).round());
        for (final MinGenInfo roundGenPair : minGen) {
            long round = roundGenPair.round();
            RoundInfo roundInfo = new RoundInfo(round, addressBook.getSize());
            rounds.put(round, roundInfo);

            // set the minGeneration as stored in state
            long minGeneration = roundGenPair.minimumGeneration();
            roundInfo.updateMinGeneration(minGeneration);
            roundInfo.fameDecided = true;
        }
        updateMinRoundGeneration();
        setFameDecidedBelow(maxRound.get() + 1);
    }
}
