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

import static com.swirlds.logging.LogMarker.ADD_EVENT;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.internal.EventImpl.MIN_TRANS_TIMESTAMP_INCR_NANOS;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.state.signed.SignedState;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * All the code for calculating the consensus for events in a hashgraph. This calculates
 * the consensus timestamp and consensus order, according to the hashgraph consensus algorithm.
 *
 * Every method in this file is private, except for some getters and the addEvent method. Therefore,
 * if care is taken so that only one thread at a time can be in the call to addEvent, then only one
 * thread at a time will be anywhere in this is class (except for the getters). None of the variables
 * are volatile, so calls to the getters by other threads may not see effects of addEvent immediately.
 *
 * The consensus order is calculated incrementally: each time a new event is added to the hashgraph, it
 * immediately finds the consensus order for all the older events for which that is possible. It uses a
 * fundamental theorem that was not included in the tech report. That theorem is:
 *
 * Theorem: If every known witness in round R in hashgraph A has its fame decided by A, and S_{A,R} is the
 * set of known famous witnesses in round R in hashgraph A, and if at least one event created in round R+2
 * is known in A, then S_{A,R} is immutable and will never change as the hashgraph grows in the future.
 * Furthermore, any consistent hashgraph B will have an S_{B,R} that is a subset of S_{A,R}, and as B grows
 * during gossip, it will eventually be the case that S_{B,R} = S_{A,R} with probability one.
 *
 * Proof: the R+2 event strongly sees more than 2/3 of members having R+1 witnesses that vote NO on the fame
 * of any unknown R event X that will be discovered in the future. Any future R+2 voter will strongly see a
 * (possibly) different set of more than 2/3 of the R+1 population, and the intersection of the two sets
 * will all be NO votes for the new voter. So the new voter will see less than 1/3 YES votes, and more than
 * 1/3 NO votes, and will therefore vote no. Therefore every R+3 voter will see unanimous NO votes, and will
 * decide NO. Therefore X will not be famous. So the set of famous in R will never grow in the future. (And
 * so the consensus theorems imply that B will eventually agree).
 *
 * In other words, you never know whether new events and witnesses will be added to round R in the future.
 * But if all the known witnesses in that round have their fame decided (and if a round R+2 event is known),
 * then you know for sure that there will never be any more famous witnesses discovered for round R. So you
 * can safely calculate the received round and consensus time stamp for every event that will have a
 * received round of R. This is the key to the incremental algorithm: as soon as all known witnesses in R
 * have their fame decided (and there is at least one R+2 event), then we can decide the consensus for a new
 * batch of events: all those with received round R.
 *
 * There will be at least one famous event in each round. This is a theorem in the tech report, but both the
 * theorem and its proof should be adjusted to say the following:
 *
 * Definition: a "voter" is a witness in round R that is strongly seen by at least one witness in round R+1.
 *
 * Theorem: For any R, there exists a witness X in round R that will be famous, and this will be decided at
 * the latest when one event in round R+3 is known.
 *
 * Proof: Each voter in R+1 strongly sees more than 2n/3 witnesses in R, therefore each witness in R is on
 * average strongly seen by more than 2n/3 of the voters in R+1. There must be at least one that is not
 * below average, so let X be an R witness that is strongly seen by more than 2n/3 round R+1 voters. Those
 * voters will vote YES on the fame of X, because they see X. Any round R+2 witness will receive votes from
 * more than 2n/3 round R+1 voters, therefore it will receive a majority of its votes for X being YES,
 * therefore it will either vote or decide YES. If any R+2 witness decides, then X is known to be famous at
 * that time. If none do, then as soon as an R+3 witness exists, it will see unanimous YES votes, and it
 * will decide YES. So X will be known to be famous after the first witness of R+3 is known (or earlier).
 *
 * In normal operation, with everyone online and everyone honest, we might expect that all of the round R
 * witnesses will be known to be famous after the first event of round R+2 is known. But even in the worst
 * case, where some computers are down (even honest ones), and many dishonest members are forking, the
 * theorem still guarantees at least one famous witness is known by R+3.
 *
 * It is another theorem that the d12 and d2 algorithm have more than two thirds of the population creating
 * unique famous witnesses (judges) in each round. It is a theorem that d1 does, too, for the algorithmn described in
 * 2016, and is conjectured to be true for the 2019 version, too.
 *
 * Another new theorem used here:
 *
 * Theorem: If a new witness X is added to round R, but at least one already exists in round R+2, then X
 * will not be famous (so there is no need to hold the elections).
 *
 * Proof: If an event X currently exists in round R+2, then when the new event Y is added to round R, it
 * won't be an ancestor of X, nor of the witnesses that X strongly sees. Therefore, X will collect unanimous
 * votes of NO for the fame of Y, so X will decide that Y is not famous. Therefore, once a round R+2 event
 * is added to the hashgraph, the set of possible unique famous witnesses for round R is fixed, and the
 * unique famous witnesses will end up being a subset of it.
 *
 * NOTE: for concision, all of the above talks about things like "2/3 of the members" or "2/3 of the witnesses". In
 * every case, it should be interpreted to actually mean "members whose weight adds up to more than 2/3 of the total
 * weight", and "witnesses created by members whose weight is more than 2/3 of the total".
 **/
public class ConsensusImpl implements Consensus {

    private static final Logger logger = LogManager.getLogger(ConsensusImpl.class);
    /** consensus configuration */
    private final ConsensusConfig config;

    // ------------------------ Variable passed to the constructor ------------------------
    /** the only address book currently, until address book changes are implemented */
    private final AddressBook addressBook;

    /** metrics related to consensus */
    private final ConsensusMetrics consensusMetrics;

    /** a method that accepts minimum generation info */
    private final BiConsumer<Long, Long> minGenConsumer;

    ///////////////////////////

    // the following are used for handling the judge hashes from 3 rounds, which were saved in the signed state,
    // and loaded during a restart or reconnect. When certain events are later loaded, they immediately have their
    // roundCreated set, if their hashes match any in the 3 lists. And when the first list has all of its events
    // loaded, then any event that is an ancestor of all of them will have its consensus set to true, so that it
    // won't be treated as reaching consensus later, and its transactions will never be handled.

    /** the roundCreated to set for a new event, if its hash is here (used during restart and reconnect) */
    private Map<Hash, Long> hashRoundCreated = null; // null means none are known

    /** hashRoundCreated is either null, or it contains entries for only hashRound, hashRound-1, hashRound-2 */
    private long hashRound = -1; // -1 means none are known

    /** the number of judges in round hashRound that are not yet added to the hashgraph  (or 0 if not needed) */
    private int numInitJudgesMissing = 0;

    /** all judges in round hashRound that have been added so far (still need numInitJudgesMissing more) */
    private List<EventImpl> hashRoundJudges = null;

    /**
     * the triple hash list for each round. In this class, it's only used in the getter. Deleted when round
     * discarded
     */
    private final Map<Long, List<List<Hash>>> hashLists = new HashMap<>(); // null means none are known

    // -----------------------------------------------------------------------------------------------------------------

    //////////////// WHERE EVENTS ARE STORED IN THE HASHGRAPH //////////////////////////////////////////////////////////
    // Each event is stored in eventsByCreator, in the list for its creator.
    // Each event is stored in one of the rounds, in its allEvents list, and initially in its
    // notConsensusStaleEvents list (though it is removed from that one when it reaches
    // consensus or becomes stale).
    //
    // If it is a witness, then it is also stored in exactly
    // one of the RoundInfo.witnesses lists, for the appropriate round.
    // If it is, then it might also be in the RoundInfo.famousWitnesses
    // for that same round.
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /** stores all round information */
    private final ConsensusRounds rounds;

    /**
     * Number of events that have reached consensus order. This is used for setting consensus order numbers
     * in events, so it must be part of the signed state.
     */
    private volatile long numConsensus = 0;

    /**
     * The minimum consensus timestamp for the next event that reaches consensus. This is null if no event
     * has reached consensus yet. As each event reaches its consensus, its timestamp is moved forward (if
     * necessary) to be at least this time.
     * And then minTimestamp is moved forward by n * {@link EventImpl#MIN_TRANS_TIMESTAMP_INCR_NANOS} nanoseconds, if
     * the
     * event had n transactions (or n=1 if no transactions).
     * Then minTimestamp is rounded up to the nearest multiple of {@link EventImpl#MIN_TRANS_TIMESTAMP_INCR_NANOS}
     */
    private Instant minTimestamp = null;

    /** an event with this number is "marked", all others are "unmarked". Used by the ValidAncestorsIterator */
    private int currMark = 1;

    /** the round number passed to setAddressBook the last time it was called. The next call must be 1 greater */
    private long prevRoundSetAddressBook;

    /** the number of coin rounds that have happened so far (used to update the statistics) */
    private long numCoinRounds = 0;

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Public constructors
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs an empty object (no events) to keep track of elections and calculate consensus.
     *
     * @param config
     * 		consensus configuration
     * @param consensusMetrics
     * 		metrics related to consensus
     * @param minGenConsumer
     * 		a method that accepts minimum generation info
     * @param addressBook
     * 		the global address book, which never changes
     */
    public ConsensusImpl(
            final ConsensusConfig config,
            final ConsensusMetrics consensusMetrics,
            final BiConsumer<Long, Long> minGenConsumer,
            final AddressBook addressBook) {
        this.config = config;
        this.consensusMetrics = consensusMetrics;
        this.minGenConsumer = minGenConsumer;

        // until we implement address book changes, we will just use the use this address book
        this.addressBook = addressBook;

        this.rounds = new ConsensusRounds(config, addressBook);
    }

    /**
     * Constructs an object to keep track of elections and calculate consensus. It will read from the given state
     * to process all its events, and to read and store its lastRoundReceived.
     *
     * @param config
     * 		consensus configuration
     * @param consensusMetrics
     * 		metrics related to consensus
     * @param minGenConsumer
     * 		a method that accepts minimum generation info
     * @param addressBook
     * 		the global address book, which never changes
     * @param signedState
     * 		a state to read from
     */
    public ConsensusImpl(
            final ConsensusConfig config,
            final ConsensusMetrics consensusMetrics,
            final BiConsumer<Long, Long> minGenConsumer,
            final AddressBook addressBook,
            final SignedState signedState) {
        this(config, consensusMetrics, minGenConsumer, addressBook);
        // create all the rounds that we have events for
        rounds.createRoundsForSignedStateConstructor(signedState.getMinGenInfo());

        for (final EventImpl event : signedState.getEvents()) {
            event.setConsensus(true);
            // events are stored in consensus order, so the last event in consensus order should be
            // incremented by 1 to get the numConsensus
            numConsensus = event.getConsensusOrder() + 1;
        }

        // The minTimestamp is just above the last transaction that has been handled
        minTimestamp = calcMinTimestampForNextEvent(signedState.getLastTransactionTimestamp());

        logger.debug(
                STARTUP.getMarker(),
                "ConsensusImpl is initialized from signed state. minRound: {}(min gen = {}), maxRound: {}(max gen = "
                        + "{})",
                rounds::getMinRound,
                rounds::getMinRoundGeneration,
                rounds::getMaxRound,
                rounds::getMaxRoundGeneration);
    }

    /**
     * Constructor for the consensus object that takes a round number and a list of 3 lists of hashes.
     *
     * The list contains 3 lists. The first is the hashes of the famous witnesses in round "round".
     * The second is the hashes of witnesses in round "round"-1 which are ancestors of those in the first
     * list. The third is the hashes of witnesses in round "round"-2 which are ancestors of those in
     * the first list.
     *
     * As each event is added, it will be given an appropriate roundCreated if it
     * has a hash in any of the 3 lists, or has an ancestor in any of the 3 lists. But if it doesn't have an
     * ancestor in those lists, then it will be given a roundCreated of -1, which represents negative infinity.
     *
     * @param consensusMetrics
     * 		metrics related to consensus
     * @param minGenConsumer
     * 		a method that accepts minimum generation info
     * @param addressBook
     * 		the global address book, which never changes
     * @param round
     * 		the 3 lists are for witnesses in rounds: round, round-1, round-2
     * @param witnessHashes
     * 		the list of 3 lists of hashes of witnesses (famous for round "round", and ancestors of those). This must
     * 		must be non-null and contain exactly 3 lists.
     */
    public ConsensusImpl(
            final ConsensusConfig config,
            final ConsensusMetrics consensusMetrics,
            final BiConsumer<Long, Long> minGenConsumer,
            final AddressBook addressBook,
            final long round,
            final List<List<Hash>> witnessHashes) {
        this.config = config;
        this.consensusMetrics = consensusMetrics;
        this.minGenConsumer = minGenConsumer;
        // until we implement address book changes, we will just use the use this address book
        this.addressBook = addressBook;
        this.hashRound = round;
        hashRoundCreated = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            for (Hash hash : witnessHashes.get(i)) {
                hashRoundCreated.put(hash, round - i);
            }
        }
        // once we get this many, mark appropriate events as already having consensus before we started
        numInitJudgesMissing = witnessHashes.get(0).size();
        hashRoundJudges = new ArrayList<>();

        this.rounds = new ConsensusRounds(config, addressBook);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Public getters
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMaxRoundGeneration() {
        return rounds.getMaxRoundGeneration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMinRoundGeneration() {
        return rounds.getMinRoundGeneration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getFameDecidedBelow() {
        return rounds.getFameDecidedBelow(); // not synchronized because it's AtomicLong
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMaxRound() {
        return rounds.getMaxRound(); // not synchronized because it's AtomicLong
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMinRound() {
        return rounds.getMinRound(); // not synchronized because it's AtomicLong
    }

    /**
     * Return the minimum generation of all the famous witnesses that are not in ancient rounds.
     *
     * <p>Define gen(R) to be the minimum generation of all the events that were famous witnesses in round R.
     *
     * If round R is the most recent round for which we have decided the fame of all the witnesses, then any event with
     * a generation less than gen(R - {@code Settings.state.roundsExpired}) is called an “expired” event. And any
     * non-expired event with a generation less than gen(R - {@code Settings.state.roundsNonAncient} + 1) is an
     * “ancient” event. If the event failed to achieve consensus before becoming ancient, then it is “stale”. So every
     * non-expired event with a generation before gen(R - {@code Settings.state.roundsNonAncient} + 1) is either
     * stale or consensus, not both.
     *
     * Expired events can be removed from memory unless they are needed for an old signed state that is still being used
     * for something (such as still in the process of being written to disk).
     * </p>
     *
     * @return the minimum generation
     */
    @Override
    public long getMinGenerationNonAncient() {
        return rounds.getMinGenerationNonAncient();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized List<List<Hash>> getWitnessHashes(final long round) {
        return hashLists.get(round);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // all non-private methods are synchronized (other than constructors).

    /**
     * Register the immutable address book resulting from handling all transactions received in the given round. This
     * address book, which is the result of handling the events with roundReceived==round, will be used for
     * calculating the roundCreated for all the events that are voting on the fame of witnesses created in round + 2. An
     * event can vote for multiple previous rounds, so an event might have its roundCreated recalculated multiple times.
     *
     * <p>This method must always be called with a round number that is exactly 1 greater than the last time it was
     * called.
     * If not, then an exception is thrown.
     *
     * <p>It is possible that when this is called, some existing unfrozen events will have their roundCreated
     * recalculated,
     * which causes the voting to change, which causes new witnesses to be decided, which causes a round to be decided
     * that had not been decided before. In that case, this method call will trigger events to reach consensus. The
     * list of those new consensus events (if any) is returned.
     *
     * @param addressBook
     * 		the address book to be used
     * @param round
     * 		the receivedRound of the last consensus events handled to generate addressBook
     * @return A list of consensus events, or null if no consensus was reached
     * @throws IllegalArgumentException
     * 		if round is not exactly 1 more than it was the last time this was called
     */
    public synchronized List<EventImpl> setAddressBook(AddressBook addressBook, long round)
            throws IllegalArgumentException {
        if (round != prevRoundSetAddressBook + 1) {
            throw new IllegalArgumentException(
                    "called with round == " + round + " but it should have been " + (prevRoundSetAddressBook + 1));
        }
        prevRoundSetAddressBook = round;
        return null;
    }

    /**
     * Add an event to the hashgraph. It must already have been instantiated, checked for being a duplicate
     * of an existing event, had its signature created or checked.
     *
     * <p>This method will add it to the hashgraph and propagate all its effects. So if the consensus order can
     * now be calculated for an event (which wasn't possible before), then it will do so and return a list of
     * consensus events.
     *
     * <p>It is possible that adding this event will decide the fame of the last witness in a round, and so the
     * round will become decided, and so a batch of events will reach consensus.  The list of events that reached
     * consensus (if any) will be returned.
     *
     * @param event
     * 		the event to be added
     * @param addressBook
     * 		the address book to be used
     * @return A list of consensus events, or null if no consensus was reached
     */
    @Override
    public synchronized List<EventImpl> addEvent(EventImpl event, AddressBook addressBook) {
        // all events that reached consensus because of a single addEvent call, in consensus order
        // List<EventImpl> newConsensusEvents = new LinkedList<>();

        // fill in any event.* that can be calculated by looking only at self and parents
        setFastVars(event);

        // find event.roundCreated which is max of parents', plus either 0 or 1
        ArrayList<EventImpl> stronglySeen = new ArrayList<>(); // witnesses this event strongly sees in previous round
        RoundInfo roundInfo = setRoundCreated(event, stronglySeen);
        consensusMetrics.addedEvent(event);

        // force it to memoize for this event now, to avoid deep recursion of these methods later
        stronglySeeP(event, 0);
        firstSelfWitnessS(event);

        // set event.isWitness appropriately, and propagate consensus info
        setIsWitness(event, roundInfo);

        // check if it's a witness. If so, vote in all elections in the current round, put new consensus events in nce
        final List<EventImpl> newConsensusEvents = vote(event, roundInfo, stronglySeen);
        logger.info(
                ADD_EVENT.getMarker(),
                "Event to be added: {} for round: {}, " + "stronglySeen: {}, newConsensusEvents: {}",
                () -> EventUtils.toShortString(event),
                () -> roundInfo.getRound(),
                () -> EventUtils.toShortStrings(stronglySeen),
                () -> EventUtils.toShortStrings(newConsensusEvents));

        return newConsensusEvents;
    }

    /**
     * Check if event is a witness, and if it is, make it vote in all elections in its created round. This should be
     * called on a round R only if it has already been called on round R-1.
     *
     * @param event
     * 		the event that will vote (if it's a witness)
     * @param roundInfo
     * 		the RoundInfo for the created round of event.
     * @param stronglySeen
     * 		a list of all the witnesses in the previous round that it strongly sees
     * @return list of all events that reach consensus during this method call, in consensus order (or null if none)
     */
    private List<EventImpl> vote(EventImpl event, RoundInfo roundInfo, ArrayList<EventImpl> stronglySeen) {
        if (!event.isWitness()) { // only witnesses are allowed to vote
            return null;
        }
        List<EventImpl> cons = new LinkedList<>(); // all events reaching consensus now, in consensus order
        int voterId = (int) event.getCreatorId();
        for (RoundInfo.ElectionRound election = roundInfo.elections;
                election != null;
                election = election.nextElection) { // for all elections
            if (election.age == 1) {
                // first round of an election. Vote TRUE for self-ancestors of those you firstSee. Don't decide.
                EventImpl w = firstSee(event, election.event.getCreatorId());
                while (w != null && w.getRoundCreated() > event.getRoundCreated() - 1 && w.getSelfParent() != null) {
                    w = firstSelfWitnessS(w.getSelfParent());
                }
                election.vote[voterId] = (election.event == w);
            } else {
                // either a coin round or normal round, so count votes from witnesses you strongly see
                long yesWeight = 0; // total weight of all members voting yes
                long noWeight = 0; // total weight of all members voting yes
                for (EventImpl w : stronglySeen) {
                    int id = (int) w.getCreatorId();
                    long weight = addressBook.getAddress(id).getWeight();
                    if (election.prevRound.vote[id]) {
                        yesWeight += weight;
                    } else {
                        noWeight += weight;
                    }
                }
                long totalWeight = addressBook.getTotalWeight();
                boolean superMajority = Utilities.isSuperMajority(yesWeight, totalWeight)
                        || Utilities.isSuperMajority(noWeight, totalWeight);

                election.vote[voterId] = (yesWeight >= noWeight);
                if ((election.age % config.coinFreq()) == 0) {
                    // a coin round. Vote randomly unless you strongly see a supermajority. Don't decide.
                    numCoinRounds++;
                    if (!superMajority) {
                        if ((election.age % (2 * config.coinFreq())) == config.coinFreq()) {
                            election.vote[voterId] = true; // every other "coin round" is just coin=true
                        } else {
                            // coin is one bit from signature (LSB of second of two middle bytes)
                            election.vote[voterId] = coin(event);
                        }
                    }
                } else {
                    // a normal round. Vote with the majority of those you strongly see.
                    // If you strongly see a supermajority one way, then decide that way.
                    if (superMajority) {
                        // we've decided one famous event. Set it as famous. If that round is now decided, remember the
                        // new consensus events
                        List<EventImpl> c = setFamous(
                                election.event,
                                rounds.get(election.event.getRoundCreated()),
                                election.vote[voterId],
                                election);
                        if (c != null) {
                            cons.addAll(c);
                        }
                    }
                }
            }
        }
        consensusMetrics.coinRounds(numCoinRounds);
        return cons.size() == 0 ? null : cons;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // All methods and inner classes below this line are private
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // The following is the complete call graph for all the consensus methods:
    //
    // ----addEvent
    // --------setFastVars
    // --------consSetAncestorFirstSeq
    // --------setRoundCreated
    // ------------getOrCreateRoundInfo (called twice)
    // ----------------*** newElection (called twice)
    // --------setIsWitness
    // ------------*** newElection
    // ------------### setFamous (see below)
    // --------vote
    // ------------### setFamous
    // ----------------setRoundFameDecidedTrue
    // --------------------findReceivedInRound
    // ------------------------setIsConsensusTrue
    // --------------------setConsensusOrder
    // --------------------delRounds
    //
    // Each method calls those indented under it. Each method calls that one only once, except where
    // noted otherwise in parentheses after it. If a method appears twice, it is prefaced with *** or ###.
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Set all the variables in the given Event that can be filled in quickly, without looking at the rest
     * of the hashgraph.
     *
     * @param event
     * 		the event to modify
     */
    private void setFastVars(EventImpl event) {
        if (event.isCleared()) {
            return; // no need to update discarded events
        }
        event.setWitness(false);
        event.setFameDecided(false);
        event.setFamous(false);
        event.setConsensus(false);
    }

    /**
     * Set event.roundCreated to the round created (the parent round, plus either 0 or 1). Add to the stronglySeen
     * list all the witnesses that event can strongly see, in the round before event's round created.
     *
     * @param event
     * 		the event to modify
     * @param stronglySeen
     * 		an empty list should be passed in. All the witnesses in the round before this round that event strongly
     * 		sees will be added to this list
     * @return the roundInfo for this event's round
     */
    private RoundInfo setRoundCreated(EventImpl event, ArrayList<EventImpl> stronglySeen) {
        int numMembers = addressBook.getSize();
        long round;

        round(event); // find the round, and store it using event.setRoundCreated()
        for (long m = 0; m < numMembers; m++) {
            EventImpl s = stronglySeeS1(event, m);
            if (s != null) {
                stronglySeen.add(s);
            }
        }
        round = event.getRoundCreated();

        getOrCreateRoundInfo(round - 1); // ensure the roundInfo exists for this round, and the one before it
        return getOrCreateRoundInfo(round);
    }

    /**
     * Get the RoundInfo for the given round. If it doesn't exist, then create it and register it in the
     * various data structures that track it. The (possibly new) RoundInfo is returned.
     *
     * @param round
     * 		the round whose RoundInfo should be retrieved (or created if it doesn't exist)
     * @return the (possibly new) RoundInfo
     */
    private RoundInfo getOrCreateRoundInfo(long round) {
        RoundInfo roundInfo = rounds.get(round);
        if (roundInfo != null) {
            return roundInfo;
        }
        roundInfo = new RoundInfo(round, addressBook.getSize());
        rounds.put(round, roundInfo);

        // create elections in this round based on the previous one
        RoundInfo oldRoundInfo = rounds.get(round - 1);
        if (oldRoundInfo != null) { // if there is a previous round, use it 2 ways:
            // create elections copying all from previous round to this new round
            for (RoundInfo.ElectionRound e = oldRoundInfo.elections; e != null; e = e.nextElection) {
                newElection(e.event, roundInfo, e);
            }
            for (EventImpl witness : oldRoundInfo.witnesses) {
                newElection(witness, roundInfo, null);
            }
        }

        return roundInfo;
    }

    /**
     * Set event.isWitness appropriately. And if it is a witness, propagate the effects. This assumes that
     * event.roundCreated has already been filled in. And so has event.selfParent.round, if there is a self
     * parent.
     *
     * @param event
     * 		the event to modify
     * @param roundInfo
     * 		the roundInfo for the round this event is created in
     * @return list of all events that reach consensus during this method call, in consensus order (or null if none)
     */
    private List<EventImpl> setIsWitness(EventImpl event, RoundInfo roundInfo) {
        if (event.getSelfParent() != null
                && event.getRoundCreated() == event.getSelfParent().getRoundCreated()) {
            event.setWitness(false);
            return null;
        }
        // the event is a witness, so mark it as such, and record it.
        event.setWitness(true);
        roundInfo.witnesses.add(event); // this is the only place new witnesses are ever added
        roundInfo.numWitnesses++;

        if (rounds.get(event.getRoundCreated() + 2) != null) {
            // theorem says it can't be famous, so decide it now, with no elections.
            roundInfo.numUnknownFame++;
            // numUnknownFame will be decremented in setFamous(), we need to increment it here so we don't loose
            // track of how many witnesses we need to determine fame for. This was the cause of bug #197
            return setFamous(event, roundInfo, false, null);
        } else {
            // the theorem doesn't apply, so we can't decide yet
            roundInfo.numUnknownFame++;
            RoundInfo nextRound = rounds.get(roundInfo.getRound() + 1);
            if (nextRound != null) {
                // event is round R, there is a round R+1, but not an R+2, so create election in R+1
                newElection(event, nextRound, null);
            }
        }
        return null;
    }

    /**
     * create a new election for the given witness in the given round, and add it to the various data
     * structures.
     *
     * @param witness
     * 		the witness that needs another election
     * @param roundInfo
     * 		the round that the new election should be added to
     * @param prevRound
     * 		the election for the same witness in the previous round (or null if none)
     * @return the created election
     */
    private RoundInfo.ElectionRound newElection(
            EventImpl witness, RoundInfo roundInfo, RoundInfo.ElectionRound prevRound) {
        RoundInfo.ElectionRound election = new RoundInfo.ElectionRound(
                roundInfo,
                getAddressBook().getSize(),
                witness,
                roundInfo.getRound() - witness.getRoundCreated()); // this is the only place this is called
        election.nextRound = null;
        election.prevRound = prevRound;
        election.prevElection = null;
        election.nextElection = roundInfo.elections;
        if (witness.getFirstElection() == null) {
            witness.setFirstElection(election);
        }
        if (prevRound != null) {
            prevRound.nextRound = election;
        }
        if (roundInfo.elections != null) {
            roundInfo.elections.prevElection = election;
        }
        roundInfo.elections = election;
        return election;
    }

    /**
     * Set a witness as being famous or not. Then propagate consensus info.
     *
     * @param event
     * 		the witness in the given round
     * @param roundInfo
     * 		the roundInfo for the given round (must be same round as event)
     * @param isFamous
     * 		is this witness famous?
     * @return list of all events that reach consensus during this method call, in consensus order (or null if none)
     */
    private List<EventImpl> setFamous(
            EventImpl event, RoundInfo roundInfo, boolean isFamous, RoundInfo.ElectionRound TEMP) {
        event.setFamous(isFamous);
        event.setFameDecided(true);
        if (isFamous) {
            // remember it as a judge (or don't, if it's a fork that won't be the unique famous witness)
            roundInfo.addFamousWitness(event);
        }
        roundInfo.numUnknownFame--;

        // fame is now decided for event, so remove all its elections
        for (RoundInfo.ElectionRound e = event.getFirstElection(); e != null; e = e.nextRound) {
            if (e.prevElection == null) {
                e.roundInfo.elections = e.nextElection;
            } else {
                e.prevElection.nextElection = e.nextElection;
            }
            if (e.nextElection != null) {
                e.nextElection.prevElection = e.prevElection;
            }
        }
        event.setFirstElection(null);

        if (roundInfo.numUnknownFame == 0) {
            // all famous witnesses for this round are now known. None will ever be added again. We know this
            // round has at least one witness, because this method was called. We know they all have fame
            // decided, because this IF statement checked for that. We know the next 2 rounds have events in
            // them, because otherwise we couldn't have decided the fame here.
            // Therefore any new witness added to this round
            // in the future will be instantly decided as not famous. Therefore, the set of famous witnesses
            // in this round is now completely known and immutable. So we can call the following, to record
            // that fact, and propagate appropriately.
            consensusMetrics.lastFamousInRound(event);
            return setRoundFameDecidedTrue(roundInfo);
        }
        return null;
    }

    /**
     * Set roundInfo.fameDecided to be true, then propagate consensus info. Setting fameDecided to true
     * means that the fame of all known witnesses in that round has been decided, and so any new witnesses
     * discovered in the future will be guaranteed to not be famous.
     *
     * Since fame for this round is now decided, it is now possible to decide consensus and time stamps for
     * events in earlier rounds. If an event is an ancestor of a famous witness, its isFrozen becomes true.
     * If it's an ancestor of all the famous witnesses, then it reaches consensus. If its isFrozen is still
     * false, then it may have its roundCreated changed as a result of address book changes due to the effects
     * of the new consensus events being handled.
     *
     * @param roundInfo
     * 		the round information to modify
     * @return list of all events that reach consensus during this method call, in consensus order (or null if none)
     */
    private List<EventImpl> setRoundFameDecidedTrue(RoundInfo roundInfo) {
        // all events that reach consensus during this method call, in consensus order
        List<EventImpl> newConsensusEvents = new LinkedList<>();

        // the current round just had its fame decided. There may have
        // been some later rounds that already had fame decided, but
        // couldn't be used to determine consensus, because this round
        // was still undecided. So calculate consensus now using this
        // round, and using all later rounds that already have fame decided.
        // Note: more witnesses may be added to this round in the future, but
        // they'll all be instantly marked as not famous.
        roundInfo.fameDecided = true;
        long round = roundInfo.getRound();
        while (rounds.getFameDecidedBelow() == round && roundInfo.fameDecided) {
            minGenConsumer.accept(round, roundInfo.getMinGeneration());
            findReceivedInRound(roundInfo, newConsensusEvents);
            round++;
            rounds.setFameDecidedBelow(
                    round); // all rounds before this round are now decided, and appropriate events marked consensus
            consensusMetrics.consensusReachedOnRound();
            roundInfo = rounds.get(round);
        }

        delRounds(); // we could delete old rounds more often, but once per new decided round is enough

        return newConsensusEvents.size() == 0 ? null : newConsensusEvents;
    }

    /**
     * Find all events that are ancestors of the judges in round roundInfo.round and update them. A
     * non-consensus event that is an ancestor of all of them should be marked as consensus, and have its consensus
     * roundReceived  and timestamp set. An event that is an ancestor of at least one should be marked as frozen (so
     * its roundCreated won't change when the address book changes). Events with very old generations should be marked
     * as stale or expired, as appropriate. Expired events can be deleted. This should not be called on any round
     * greater than R until after it has been called on round R.
     *
     * @param roundInfo
     * 		the info for the round with the unique famous witnesses, which is also the round received for these events
     * 		reaching consensus now
     * @param newConsensusEvents
     * 		a (possibly-nonempty) list that will have added to it all events that reach consensus during this
     * 		method call, adding them in consensus order
     */
    private void findReceivedInRound(RoundInfo roundInfo, List<EventImpl> newConsensusEvents) {
        byte[] whitening; // an XOR of the signatures of unique famous witnesses in a round, used during sorting
        ArrayList<EventImpl> consensus =
                new ArrayList<>(); // the newly-consensus events where round received is "round"
        EventImpl[] judges = roundInfo.judges; // all judges for this round
        int numJudges = 0; // number of judges in this round
        long round =
                roundInfo.getRound(); // the round where we just got consensus on the set of unique famous witnesses
        List<Hash> hashesR0 = new ArrayList<>(); // hashes of judges in round
        List<Hash> hashesR1 = new ArrayList<>(); // hashes of round-1 witnesses that are ancestors of hashesR0
        List<Hash> hashesR2 = new ArrayList<>(); // hashes of round-2 witnesses that are ancestors of hashesR0
        List<List<Hash>> hashes = new ArrayList<>();
        hashes.add(hashesR0);
        hashes.add(hashesR1);
        hashes.add(hashesR2);

        // now is the first time that roundInfo.round and all earlier
        // rounds have fame decided. So set roundReceived for earlier events.
        // Do a recursive search of the hashgraph, without using the Java stack, and being efficient when it's a
        // DAG that isn't a tree.

        // find whitening for round
        Arrays.fill(roundInfo.whitening, (byte) 0);
        for (EventImpl w : judges) { // calculate the whitening byte array
            if (w != null) {
                numJudges++;
                int mn = Math.min(roundInfo.whitening.length, w.getSignature().length);
                for (int i = 0; i < mn; i++) {
                    roundInfo.whitening[i] ^= w.getSignature()[i];
                }
            }
        }
        whitening = roundInfo.whitening;

        // get the minimum generation of famous witnesses for oldest non-ancient round
        // any event with generation less than minGenConsensus is ancient, and will be stale if not already consensus
        final long minGenConsensus = rounds.getMinGenerationNonAncient();

        ArrayList<EventImpl> visited =
                new ArrayList<>(); // each event visited by iterator from at least one ufw witness

        // for each judge in this round that just decided fame
        for (EventImpl w : roundInfo.judges) {
            // search from every judge that exists
            if (w != null) {
                ValidAncestorsView nonConsensusAncestors =
                        new ValidAncestorsView(w, e -> (!e.isConsensus() && !e.isStale()));
                ValidAncestorsView threeRoundAncestors = new ValidAncestorsView(
                        w, e -> (e.getRoundCreated() == round - 1 || e.getRoundCreated() == round - 2));
                hashesR0.add(new Hash(w.getBaseHash())); // remember hash of each UFW in round
                // find hashes of all ancestors of w that are witnesses in rounds round-1 or round-2
                for (EventImpl event : threeRoundAncestors) {
                    if (event.isWitness() && event.getRoundCreated() == round - 1) {
                        hashesR1.add(new Hash(event.getBaseHash()));
                    } else if (event.isWitness() && event.getRoundCreated() == round - 2) {
                        hashesR2.add(new Hash(event.getBaseHash()));
                    }
                }
                // walk through all non-consensus, non-stale ancestors of w, using a predicate lambda to check for that
                // for every ancestor of the ufw that isn't consensus/stale/expired yet
                for (EventImpl event : nonConsensusAncestors) {
                    if (event.getGeneration() < minGenConsensus) {
                        continue;
                    }
                    if (event.getRecTimes() == null) {
                        event.setRecTimes(new ArrayList<>());
                        visited.add(event);
                    }
                    // this is one of the times that will affect the median
                    event.getRecTimes().add(nonConsensusAncestors.getTime()); // this is reset to null after this loop

                    // if it reached all the ufws, then it now has consensus
                    if (event.getRecTimes().size() == numJudges) {
                        // event has reached consensus, so store it, set consensus timestamp, and set isConsensus to
                        // true
                        setIsConsensusTrue(event, roundInfo);
                        consensus.add(event);
                    }
                }
            }
        }
        hashLists.put(round, hashes);

        // "consensus" now has all events in history with receivedRound==round
        // there will never be any more events with receivedRound<=round (not even if the address book changes)

        // consensus order is to sort by roundReceived, then consensusTimestamp,
        // then generation, then whitened signature.
        Collections.sort(consensus, (EventImpl e1, EventImpl e2) -> {
            int c;

            // sort by consensus timestamp
            c = (e1.getConsensusTimestamp().compareTo(e2.getConsensusTimestamp()));
            if (c != 0) return c;

            // subsort ties by extended median timestamp
            ArrayList<Instant> recTimes1 = e1.getRecTimes();
            ArrayList<Instant> recTimes2 = e2.getRecTimes();

            int m1 = recTimes1.size() / 2; // middle position of e1 (the later of the two middles, if even length)
            int m2 = recTimes2.size() / 2; // middle position of e2 (the later of the two middles, if even length)
            int d = -1; // offset from median position to look at
            while (m1 + d >= 0 && m2 + d >= 0 && m1 + d < recTimes1.size() && m2 + d < recTimes2.size()) {
                c = recTimes1.get(m1 + d).compareTo(recTimes2.get(m2 + d));
                if (c != 0) return c;
                d = d < 0 ? -d : -d - 1; // use the median position plus -1, 1, -2, 2, -3, 3, ...
            }

            // subsort ties by generation
            c = Long.compare(e1.getGeneration(), e2.getGeneration());
            if (c != 0) return c;

            // subsort ties by whitened signature
            return Utilities.arrayCompare(e1.getSignature(), e2.getSignature(), whitening);
        });

        // Set the consensus number for every event that just became a consensus
        // event. Add more info about it to the hashgraph. Set event.lastInRoundReceived
        // to true for the last event in "consensus".
        setConsensusOrder(consensus);

        for (EventImpl e : consensus) { // add them in consensus order
            newConsensusEvents.add(e);
        }
        for (EventImpl e : visited) {
            e.setFrozen(true); // never recalculate roundCreated again for an event that was an ancestor of a ufw
            e.setRecTimes(null); // reclaim the memory for the list of received times
        }
    }

    /**
     * Delete the oldest rounds with round number which is expired.
     */
    private void delRounds() {
        // delete rounds before newMinRound
        final long newMinRound = getFameDecidedBelow() - config.roundsExpired();
        final long curMinRound = rounds.getMinRound();

        if (newMinRound > curMinRound) {
            rounds.aboutToRemoveBelow(newMinRound);
        }

        for (long r = curMinRound; r < newMinRound; r++) {
            // at this point, every event in the round is expired, every witness has fame decided, no
            // elections exist, so this round can be removed
            rounds.remove(r);
            hashLists.remove(r);
            if (r == hashRound) {
                // we've now discarded the most recent of the hashList events, so clear everything for all 3 lists,
                // except don't clear hashLists, because it is storing lists to be saved in the future, not the
                // lists that were loaded at startup
                hashRoundCreated = null;
                hashRound = -1;
                numInitJudgesMissing = 0;
                hashRoundJudges = null;
            }
        }
    }

    /**
     * Set event.isConsensus to true, set its consensusTimestamp, and record speed statistics.
     *
     * @param event
     * 		the event to modify, with event.getRecTimes() containing all the times judges first saw it
     * @param receivedRoundInfo
     * 		information about the round in which event was received
     */
    private void setIsConsensusTrue(EventImpl event, RoundInfo receivedRoundInfo) {
        if (event.isCleared()) {
            return; // no need to update discarded events
        }
        event.setRoundReceived(receivedRoundInfo.getRound());
        event.setConsensus(true);

        ArrayList<Instant> times = event.getRecTimes(); // list of when e1 first became ancestor of each ufw
        // sort ascending the received times. Used to find the median now, and the extended median later.
        Collections.sort(times);
        // take middle. If there are 2 middle (even length) then use the 2nd (max) of them
        event.setConsensusTimestamp(times.get(times.size() / 2));

        event.setReachedConsTimestamp(Instant.now()); // used for statistics

        consensusMetrics.consensusReached(event);
    }

    /**
     * Set event.consensusOrder for every event that just reached consensus, and update the count
     * numConsensus accordingly. The last event in events is marked as being the last received in its round.
     * Consensus timestamps are adjusted, if necessary, to ensure that each event in consensus order is
     * later than the previous one, by enough nanoseconds so that each transaction can be given a later
     * timestamp than the last.
     *
     * @param events
     * 		the events to set (such that a for(EventImpl e:events) loop visits them in consensus order)
     */
    private synchronized void setConsensusOrder(Collection<EventImpl> events) {
        EventImpl last = null;
        for (EventImpl e : events) {
            last = e;
            e.setConsensusOrder(numConsensus);
            numConsensus++;

            // advance this event's consensus timestamp to be at least minTimestamp. Update minTimestamp
            if (minTimestamp != null && e.getConsensusTimestamp().isBefore(minTimestamp)) {
                e.setConsensusTimestamp(minTimestamp);
            }
            minTimestamp = calcMinTimestampForNextEvent(e.getLastTransTime());
        }
        if (last != null) {
            last.setLastInRoundReceived(true);
        }
    }

    private AddressBook getAddressBook() {
        return addressBook;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Functions from SWIRLDS-TR-2020-01, verified by Coq proof
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The parent round (1 plus max of parents' rounds) of event x (function from SWIRLDS-TR-2020-01).
     * This result is not memoized.
     *
     * @param x
     * 		the event being queried
     * @return the parent round of x
     */
    private long parentRound(EventImpl x) {
        long round = 0;
        EventImpl sp, op;
        if (x == null) {
            return 0;
        }
        sp = x.getSelfParent();
        if (sp != null) {
            round = round(sp);
        }
        op = x.getOtherParent();
        if (x.getOtherParent() != null) {
            round = Math.max(round, round(op));
        }
        return round;
    }

    /**
     * The last event created by m that is an ancestor of x (function from SWIRLDS-TR-2020-01).
     * This has aggressive memoization: the first time it is called with a given x, it immediately calculates and stores
     * results for all m.
     * This result is memoized.
     *
     * @param x
     * 		the event being queried
     * @param m
     * 		the member ID of the creator
     * @return the last event created by m that is an ancestor of x, or null if none
     */
    private EventImpl lastSee(EventImpl x, long m) {
        int numMembers;
        EventImpl sp, op;

        if (x == null) {
            return null;
        }
        if (x.sizeLastSee() != 0) { // return memoized answer, if available
            return x.getLastSee((int) m);
        }
        // memoize answers for all choices of m, then return answer for just this m
        numMembers = getAddressBook().getSize();
        x.initLastSee(numMembers);

        op = x.getOtherParent();
        sp = x.getSelfParent();

        for (int mm = 0; mm < numMembers; mm++) {
            if (x.getCreatorId() == mm) {
                x.setLastSee(mm, x);
            } else if (sp == null && op == null) {
                x.setLastSee(mm, null);
            } else {
                EventImpl lsop = lastSee(op, mm);
                EventImpl lssp = lastSee(sp, mm);
                long lsopGen = lsop == null ? 0 : lsop.getGeneration();
                long lsspGen = lssp == null ? 0 : lssp.getGeneration();
                if ((round(lsop) > round(lssp)) || ((lsopGen > lsspGen) && (firstSee(op, mm) == firstSee(sp, mm)))) {
                    x.setLastSee(mm, lsop);
                } else {
                    x.setLastSee(mm, lssp);
                }
            }
        }
        return x.getLastSee((int) m);
    }

    /**
     * The witness y created by m that is seen by event x through an event z created by m2 (function from
     * SWIRLDS-TR-2020-01).
     * This result is not memoized.
     *
     * @param x
     * 		the event being queried
     * @param m
     * 		the creator of y, the event seen
     * @param m2
     * 		the creator of z, the intermediate event through which x sees y
     * @return the event y that is created by m and seen by x through an event by m2
     */
    private EventImpl seeThru(EventImpl x, long m, long m2) {
        if (x == null) {
            return null;
        }
        if (m == m2 && m2 == x.getCreatorId()) {
            return firstSelfWitnessS(x.getSelfParent());
        }
        return firstSee(lastSee(x, m2), m);
    }

    /**
     * The witness created by m in the parent round of x that x strongly sees (function from SWIRLDS-TR-2020-01).
     * This result is memoized.
     *
     * This method is called multiple times by both round() and stronglySeeP1(). A measure of the total time spent in
     * this method gives an indication of how much time is being devoted to what can be thought of as a kind of
     * generalized dot product (not a literal dot product). So it is timed and it updates the statistic for that.
     *
     * @param x
     * 		the event being queried
     * @param m
     * 		the member ID of the creator
     * @return witness created by m in the parent round of x that x strongly sees, or null if none
     */
    private EventImpl stronglySeeP(EventImpl x, long m) {
        long t = System.nanoTime(); // Used to update statistic for dot product time
        EventImpl result; // the witness to return (possibly null)

        if (x == null) { // if there is no event, then it can't see anything
            result = null;
        } else if (x.sizeStronglySeeP() != 0) { // return memoized answer, if available
            result = x.getStronglySeeP((int) m);
        } else { // calculate the answer, and remember it for next time
            // find and memoize answers for all choices of m, then return answer for just this m
            int numMembers = getAddressBook().getSize(); // number of members
            long totalWeight = addressBook.getTotalWeight(); // total weight in existence
            EventImpl sp = x.getSelfParent(); // self parent
            EventImpl op = x.getOtherParent(); // other parent
            long prx = parentRound(x); // parent round of x
            long prsp = parentRound(sp); // parent round of self parent of x
            long prop = parentRound(op); // parent round of other parent of x

            x.initStronglySeeP(numMembers);
            for (int mm = 0; mm < numMembers; mm++) {
                if (stronglySeeP(sp, mm) != null && prx == prsp) {
                    x.setStronglySeeP(mm, stronglySeeP(sp, mm));
                } else if (stronglySeeP(op, mm) != null && prx == prop) {
                    x.setStronglySeeP(mm, stronglySeeP(op, mm));
                } else {
                    EventImpl st =
                            seeThru(x, mm, mm); // the canonical witness by mm that is seen by x thru someone else
                    if (round(st) != prx) { // ignore if the canonical is in the wrong round, or doesn't exist
                        x.setStronglySeeP(mm, null);
                    } else {
                        long weight = 0;
                        for (long m3 = 0; m3 < numMembers; m3++) {
                            if (seeThru(x, mm, m3) == st) { // only count intermediates that see the canonical witness
                                weight += addressBook.getAddress(m3).getWeight();
                            }
                        }
                        if (Utilities.isSuperMajority(weight, totalWeight)) { // strongly see supermajority of
                            // intermediates
                            x.setStronglySeeP(mm, st);
                        } else {
                            x.setStronglySeeP(mm, null);
                        }
                    }
                }
            }
            result = x.getStronglySeeP((int) m);
        }
        t = System.nanoTime() - t; // nanoseconds spent doing the dot product
        consensusMetrics.dotProductTime(t);
        return result;
    }

    /**
     * The round-created for event x (first round is 1), or 0 if x is null (function from SWIRLDS-TR-2020-01).
     * It also stores the round number with x.setRoundCreated().
     * This result is memoized.
     *
     * If the event has a hash in the hash lists given to the ConsensusImpl constructor, then the roundCreated is set
     * to that round number, rather than calculating it from the parents.
     *
     * If a parent has a round of -1, that is treated as negative infinity. So if all parents are -1, then this one
     * will also be -1.
     *
     * @param x
     * 		the event being queried
     * @return the round-created for event x, or 0 if x is null
     */
    private long round(EventImpl x) {
        int numMembers = getAddressBook().getSize(); // number of members that are voting, with ID 0 to numMembers-1
        EventImpl op, sp; // other parent, self parent
        long rop, rsp, weight; // roundCreated of other parent, roundCerated of self parent, sum of weight involved

        if (x == null) {
            return 0;
        }
        if (x.getRoundCreated() > 0) {
            return x.getRoundCreated();
        }
        // calculate the round, memoize it, and return it

        // if this was in the hash lists given to the constructor, then assign it the roundCreated they specified
        if (hashRoundCreated != null) {
            Long r = hashRoundCreated.get(x.getBaseHash()); // the round to assign to x
            if (r != null) {
                if (r == hashRound) {
                    // we found one of the missing judges in round hashRound
                    hashRoundJudges.add(x);
                    numInitJudgesMissing--;
                    if (numInitJudgesMissing == 0) {
                        // we now have the last of the missing judges, so find every known event that is an ancestor
                        // of all of them, and mark it as having consensus.  We won't handle its transactions or do
                        // anything else with it, since it had earlier achieved consensus and affected the signed state
                        // that we started from. We won't even set its consensus fields such as roundReceived, because
                        // they aren't known, will never be known, and aren't needed.  We'll just mark it as having
                        // consensus, so we don't calculate consensus for it again in the future.

                        for (EventImpl w : hashRoundJudges) {
                            ValidAncestorsView nonConsensusAncestors =
                                    new ValidAncestorsView(w, e -> (!e.isConsensus() && !e.isStale()));
                            // temporarily use consensusOrder as a counter of how many judges it's an ancestor of
                            for (EventImpl event : nonConsensusAncestors) {
                                event.setConsensusOrder(0);
                            }
                        }
                        for (EventImpl w : hashRoundJudges) {
                            ValidAncestorsView nonConsensusAncestors =
                                    new ValidAncestorsView(w, e -> (!e.isConsensus() && !e.isStale()));
                            // temporarily use consensusOrder as a counter of how many judges it's an ancestor of
                            for (EventImpl event : nonConsensusAncestors) {
                                long count = 1 + event.getConsensusOrder();
                                event.setConsensusOrder(count);
                                if (count == hashRoundJudges.size()) {
                                    // this event is an ancestor of all the judges, and so would have reached consensus
                                    // sometime before the signed state was created.  Mark it as having consensus so it
                                    // won't reach consensus again.
                                    event.setConsensus(true);
                                }
                            }
                        }
                        for (EventImpl w : hashRoundJudges) {
                            ValidAncestorsView nonConsensusAncestors =
                                    new ValidAncestorsView(w, e -> (!e.isConsensus() && !e.isStale()));
                            // it's no longer needed as a counter. We'll never get consensus. So just leave it as a zero
                            for (EventImpl event : nonConsensusAncestors) {
                                event.setConsensusOrder(0);
                            }
                        }
                    }
                }
                x.setRoundCreated(r);
                return x.getRoundCreated();
            }
        }

        op = x.getOtherParent();
        sp = x.getSelfParent();
        // if no parents, then it's round 1
        if (op == null && sp == null) {
            x.setRoundCreated(1);
            return x.getRoundCreated();
        }
        rsp = round(sp);
        rop = round(op);

        // if parents have unequal rounds, then copy the round of the later parent
        if (rsp > rop) {
            x.setRoundCreated(rsp);
            return x.getRoundCreated();
        }
        if (rop > rsp) {
            x.setRoundCreated(rop);
            return x.getRoundCreated();
        }

        // parents have equal rounds. But if both are -1, then this is -1 (because -1 represents negative infinity)
        if (rsp == -1) {
            x.setRoundCreated(-1);
            return x.getRoundCreated();
        }

        // parents have equal rounds (not -1), so check if x can strongly see witnesses with a supermajority of weight
        weight = 0;
        int numStronglySeen = 0;
        for (long m = 0; m < numMembers; m++) {
            if (stronglySeeP(x, m) != null) {
                weight += addressBook.getAddress(m).getWeight();
                numStronglySeen++;
            }
        }
        consensusMetrics.witnessesStronglySeen(numStronglySeen);
        if (Utilities.isSuperMajority(weight, addressBook.getTotalWeight())) {
            // it's a supermajority, so advance to the next round
            x.setRoundCreated(1 + parentRound(x));
            consensusMetrics.roundIncrementedByStronglySeen();
            return x.getRoundCreated();
        }
        // it's not a supermajority, so don't advance to the next round
        x.setRoundCreated(parentRound(x));
        return x.getRoundCreated();
    }

    /**
     * The self-ancestor of x in the same round that is a witness (function from SWIRLDS-TR-2020-01).
     * This result is memoized.
     *
     * @param x
     * 		the event being queried
     * @return The ancestor of x in the same round that is a witness, or null if x is null
     */
    private EventImpl firstSelfWitnessS(EventImpl x) {
        if (x == null) {
            return null;
        }
        if (x.getFirstSelfWitnessS() != null) { // if already found and memoized, return it
            return x.getFirstSelfWitnessS();
        }
        // calculate, memoize, and return the result
        if (round(x) > round(x.getSelfParent())) {
            x.setFirstSelfWitnessS(x);
        } else {
            x.setFirstSelfWitnessS(firstSelfWitnessS(x.getSelfParent()));
        }
        return x.getFirstSelfWitnessS();
    }

    /**
     * The earliest witness that is an ancestor of x in the same round as x (function from SWIRLDS-TR-2020-01).
     * This result is memoized.
     *
     * @param x
     * 		the event being queried
     * @return the earliest witness that is an ancestor of x in the same round as x, or null if x is null
     */
    private EventImpl firstWitnessS(EventImpl x) {
        if (x == null) {
            return null;
        }
        if (x.getFirstWitnessS() != null) { // if already found and memoized, return it
            return x.getFirstWitnessS();
        }
        // calculate, memoize, and return the result
        if (round(x) > parentRound(x)) {
            x.setFirstWitnessS(x);
        } else if (round(x) == round(x.getSelfParent())) {
            x.setFirstWitnessS(firstWitnessS(x.getSelfParent()));
        } else {
            x.setFirstWitnessS(firstWitnessS(x.getOtherParent()));
        }
        return x.getFirstWitnessS();
    }

    /**
     * The event by m that x strongly sees in the round before the created round of x (function from
     * SWIRLDS-TR-2020-01).
     * This result is not memoized.
     *
     * @param x
     * 		the event being queried
     * @param m
     * 		the member ID of the creator
     * @return event by m that x strongly sees in the round before the created round of x, or null if none
     */
    private EventImpl stronglySeeS1(EventImpl x, long m) {
        return stronglySeeP(firstWitnessS(x), m);
    }

    /**
     * The first witness in round r that is a self-ancestor of x, where r is the round of the last event by m
     * that is
     * seen by x (function from SWIRLDS-TR-2020-01).
     * This result is not memoized.
     *
     * @param x
     * 		the event being queried
     * @param m
     * 		the member ID of the creator
     * @return firstSelfWitnessS(lastSee ( x, m)), which is the first witness in round r that is a
     * 		self-ancestor
     * 		of x, where r is the round of the last event by m that is seen by x, or null if none
     */
    private EventImpl firstSee(EventImpl x, long m) {
        return firstSelfWitnessS(lastSee(x, m));
    }

    /**
     * Return the result of a "coin flip". It doesn't need to be cryptographicaly strong. It just needs to be the case
     * that an attacker cannot predict the coin flip results before seeing the event, even if they can manipulate the
     * internet traffic to the creator of this event earlier. It's even OK if the attacker can predict the coin flip
     * 90% of the time. There simply needs to be some epsilon such that the probability of a wrong prediction is always
     * greater than epsilon (and less than 1-epsilon).
     * This result is not memoized.
     *
     * @param event
     * 		the event that will vote with a coin flip
     * @return true if voting for famous, false if voting for not famous
     */
    private boolean coin(EventImpl event) {
        return ((event.getSignature()[(event.getSignature().length / 2)] & 1) == 1);
    }

    /**
     * Calculates the minimum consensus timestamp for the next event based on current event's last transaction timestamp
     *
     * @param lastTransTimestamp
     * 		current event's last transaction timestamp
     * @return the minimum consensus timestamp for the next event that reaches consensus
     */
    static Instant calcMinTimestampForNextEvent(final Instant lastTransTimestamp) {
        // adds minTransTimestampIncrNanos
        Instant t = lastTransTimestamp.plusNanos(MIN_TRANS_TIMESTAMP_INCR_NANOS);
        // rounds up to the nearest multiple of minTransTimestampIncrNanos
        t = t.plusNanos(MIN_TRANS_TIMESTAMP_INCR_NANOS
                - 1
                - ((MIN_TRANS_TIMESTAMP_INCR_NANOS + t.getNano() - 1) % MIN_TRANS_TIMESTAMP_INCR_NANOS));
        return t;
    }

    /////////////////////////////////////////////////////////////
    // Graph search Iterator and view
    /////////////////////////////////////////////////////////////

    /**
     * This is an unmodifiable view collection for the collection of all valid ancestors of a given
     * root event, that are reachable through valid ancestors. The "valid" ancestors are defined by a lambda predicate
     * passed in to the constructor.
     *
     * It can be used to get an iterator for those events, or to read or modify the events. This does not implement
     * Collection, because its only purpose to allow iteration over those events. The iteration is depth first, and
     * backtracks each time it reaches an invalid event (one for which the predicate returns false).
     *
     * It is not threadsafe, and will silently fail without throwing any exceptions if you attempt to create and use
     * two at the same time, even if they are in the same thread, and even if they are only used to read and not write.
     * So always create only one at a time, and ensure that you are done with it before creating another one.
     *
     * This returns all ancestors of the root event that are valid. It iterates in an order that always returns
     * a parent before its child. The root itself is considered to be one of the ancestors, and is last in the
     * iteration.
     *
     * Recursion happens on self parents before other parents. So if there are multiple paths from the root to an event,
     * it will use the path that stays on line of self parents for as far down as possible before leaving that line.
     */
    private class ValidAncestorsView implements Iterable<EventImpl> {
        /** the event whose ancestors we are getting */
        private final EventImpl root;

        /** the time when the event last returned by the iterator's next() first reached the creator of root */
        private Instant timeReachedRoot;

        /** a lambda which filters which ancestors are of interest: only each event e for which valid(e)==true */
        private final Predicate<EventImpl> valid;

        /**
         * The set of ancestors of the given event (the root of the search) for which valid is true.
         * This will not include a valid ancestor that is only reachable through invalid ancestors.
         *
         * @param root
         * 		the root event whose ancestors should be searched
         * @param valid
         * 		do a depth-first search, but backtrack from any event e where valid(e)==false
         */
        public ValidAncestorsView(EventImpl root, Predicate<EventImpl> valid) {
            this.root = root;
            this.valid = valid;
        }

        /** @return the time when the event last returned by the iterator first reached a self-ancestor of the root */
        public Instant getTime() {
            return timeReachedRoot;
        }

        /** @return an iterator for this ValidAncestorsView collection of Events. */
        @Override
        public Iterator<EventImpl> iterator() {
            return new ValidAncestorsIterator(root, valid);
        }

        /** An iterator over the events in a ValidAncestorsView view. */
        private class ValidAncestorsIterator implements Iterator<EventImpl> {
            boolean hasNext = true; // becomes false when done and hashNext should return false
            EventImpl curr; // the current event reached in the search
            byte state = 0; // the state of the state machine searching from curr
            boolean selfAncestor = true; // is curr a self ancestor of the judge?
            Deque<EventImpl> stackRef = new ArrayDeque<>(300); // stack of EventImpl on the path to curr
            Deque<Byte> stackState = new ArrayDeque<>(300); // stack of state
            Deque<Boolean> stackSelfAncestor = new ArrayDeque<>(300); // stack of selfAncestor
            Deque<Instant> stackTime = new ArrayDeque<>(300); // stack of timeReachedRoot
            Predicate<EventImpl> valid;

            /** construct iterator that will iterate over all ancestors of root (including itself) */
            private ValidAncestorsIterator(EventImpl root, Predicate<EventImpl> valid) {
                this.valid = valid;
                curr = root;
                currMark++; // unmark all the events, so the search can find them all again
                timeReachedRoot = root.getTimeCreated(); // ancestors of curr reached creator then
            }

            /**
             * Returns {@code true} if the iteration has more elements.
             * (In other words, returns {@code true} if {@link #hasNext} would
             * return an element rather than throwing an exception.)
             *
             * @return {@code true} if the iteration has more elements
             */
            @Override
            public boolean hasNext() {
                return hasNext;
            }

            /**
             * Returns the next element in the iteration.
             *
             * @return the next element in the iteration
             * @throws NoSuchElementException
             * 		if the iteration has no more elements
             */
            @Override
            public EventImpl next() {
                if (!hasNext) {
                    throw new NoSuchElementException("no more events left to iterator over");
                }
                while (true) { // keep recursing until we reach the return statement in the case state == 2
                    curr.setMark(currMark); // mark this event so we don't explore it again later for this ufw
                    if (state == 0) { // try to recurse into selfParent
                        EventImpl p = curr.getSelfParent();
                        state = 1;
                        if (p != null && p.getMark() != currMark && valid.test(p)) {
                            stackRef.push(curr);
                            stackState.push(state);
                            stackSelfAncestor.push(selfAncestor);
                            stackTime.push(timeReachedRoot);
                            curr = p;
                            state = 0;
                            if (selfAncestor) {
                                timeReachedRoot = curr.getTimeCreated(); // ancestors of curr reached creator then
                            }
                        } else { // there is no selfParent, or it was already visited, or it was consensus
                            state = 1;
                        }
                    } else if (state == 1) { // try to recurse into otherParent
                        EventImpl p = curr.getOtherParent();
                        state = 2;
                        if (p != null && p.getMark() != currMark && valid.test(p)) {
                            stackRef.push(curr);
                            stackState.push(state);
                            stackSelfAncestor.push(selfAncestor);
                            stackTime.push(timeReachedRoot);
                            curr = p;
                            state = 0;
                            selfAncestor = false; // first step off the selfAncestor path makes all the events below
                            // false
                        } // else there is no otherParent, or it was already visited, or it was consensus
                    } else if (state == 2) { // done with ancestors of curr, so return curr then backtrack
                        if (stackRef.size() == 0) { // if we're back to the root
                            hasNext = false; // then there are no more
                            return curr; // return this root
                        }
                        EventImpl toReturn = curr; // else we are done with all the descendents, so backtrack
                        curr = stackRef.pop();
                        state = stackState.pop();
                        selfAncestor = stackSelfAncestor.pop();
                        timeReachedRoot = stackTime.pop();
                        return toReturn; // return the child of the vertex we just backtracked to
                    } else { // this should never happen (illegal state number)
                    }
                }
            }
        }
    }
}
