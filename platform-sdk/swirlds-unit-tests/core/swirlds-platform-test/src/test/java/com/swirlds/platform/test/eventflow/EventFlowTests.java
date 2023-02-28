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

package com.swirlds.platform.test.eventflow;

import static com.swirlds.common.test.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.test.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.platform.test.eventflow.EventFlowTestUtils.inaccurateConsensusTimeEstimater;
import static com.swirlds.test.framework.ResourceLoader.loadLog4jContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.PreConsensusEventHandler;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.ConsensusHandlingMetrics;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import com.swirlds.platform.state.DualStateImpl;
import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.SwirldStateManagerDouble;
import com.swirlds.platform.state.SwirldStateManagerSingle;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.stats.CycleTimingStat;
import com.swirlds.platform.test.NoOpConsensusMetrics;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.stubbing.Answer;

/**
 * Tests the flow of events through the system and their interaction with {@link SwirldState}.
 */
class EventFlowTests {

    /** Delay in milliseconds between shuffles. */
    private static final long SHUFFLE_DELAY_MS = 600;

    /** The maximum allowed bytes per transaction */
    private static final Integer TX_MAX_BYTES = 10;

    protected static final long selfId = 0L;
    private static final int THROTTLE_TRANSACTION_QUEUE_SIZE = 100_000;

    private final NodeId selfNodeId = new NodeId(false, selfId);

    private final SettingsProvider settingsProvider = mock(SettingsProvider.class);
    protected AddressBook addressBook;
    private BlockingQueue<SignedState> signedStateTracker;
    protected SystemTransactionTracker systemTransactionTracker;

    protected SwirldStateManager swirldStateManager;
    protected PreConsensusEventHandler preConsensusEventHandler;
    protected ConsensusRoundHandler consensusEventHandler;

    protected static Stream<Arguments> postConsHandleParams() {
        return Stream.of(Arguments.of(null, 4, 1_000), Arguments.of(null, 4, 10_000));
    }

    protected static Stream<Arguments> sysTransParams() {
        return Stream.of(Arguments.of(null, 4, 500), Arguments.of(null, 4, 1_000));
    }

    protected static Stream<Arguments> freezePeriodParams() {
        return Stream.of(Arguments.of(null, 4, 500, 5, 4), Arguments.of(null, 7, 1000, 9, 10));
    }

    protected static Stream<Arguments> signedStateParams() {
        return Stream.of(Arguments.of(null, 4, 500, 5), Arguments.of(null, 7, 1000, 9), Arguments.of(null, 4, 500, 0));
    }

    @BeforeAll
    static void staticSetup() throws FileNotFoundException {
        loadLog4jContext();
    }

    @AfterEach
    void cleanup() {
        consensusEventHandler.clear();
        preConsensusEventHandler.clear();
        swirldStateManager.clear();
        while (!signedStateTracker.isEmpty()) {
            signedStateTracker.poll().release();
        }
    }

    /**
     * <p>Verifies that all transactions are sent to SwirldState.preHandle() exactly once.</p>
     *
     * @param seed            random seed this test uses for address book generation
     * @param numNodes        the number of nodes in the network
     * @param origSwirldState the {@link SwirldState} instance to use in the test
     * @param applyToWrapper  a function that applies transactions or events to an event flow wrapper
     */
    void testPreHandle(
            final Long seed,
            final int numNodes,
            final SwirldState origSwirldState,
            final Function<EventFlowWrapper, HashSet<ConsensusTransactionImpl>> applyToWrapper) {

        final Random random = RandomUtils.initRandom(seed);
        init(random, numNodes, origSwirldState);
        final EventFlowWrapper wrapper = createEventFlowWrapper(random, numNodes);

        // Submits events
        final HashSet<ConsensusTransactionImpl> transactions = applyToWrapper.apply(wrapper);

        // Give the threads some time to process the transactions
        assertEventuallyEquals(
                0,
                () -> preConsensusEventHandler.getQueueSize(),
                Duration.ofSeconds(1),
                "Pre-consensus event queue not drained");

        for (final Transaction t : transactions) {
            assertTrue((Boolean) t.getMetadata(), "Metadata should be a boolean with a value of 'true'");
        }

        verifyPreHandleTransactions(transactions.size(), origSwirldState);

        final TransactionTracker currSwirldState = getCurrState();

        verifyNoFailures(currSwirldState);
    }

    /**
     * Same as {@link #testPostConsensusHandle(Long, int, int, SwirldState)}, with additional assertions to verify that
     * the next epoch hash is copied to the epoch hash when set.
     *
     * @param seed            random seed this test uses for address book generation
     * @param numNodes        the number of nodes in the network
     * @param numEvents       the number of events to submit
     * @param origSwirldState the {@link SwirldState} instance to use in the test
     */
    void testPostConsensusHandleEpochUpdate(
            final Long seed, final int numNodes, final int numEvents, final SwirldState origSwirldState) {
        final State state = getInitialState(origSwirldState, null);
        final Random random = RandomUtils.initRandom(seed);
        final Hash nextEpochHash = RandomUtils.randomHash(random);

        state.getPlatformState().getPlatformData().setNextEpochHash(nextEpochHash);

        testPostConsensusHandle(seed, numNodes, numEvents, origSwirldState, state);

        final State consensusState = swirldStateManager.getConsensusState();

        assertEquals(
                nextEpochHash,
                consensusState.getPlatformState().getPlatformData().getEpochHash(),
                "Next epoch hash should have been copied to the epoch hash");
        assertNull(
                consensusState.getPlatformState().getPlatformData().getNextEpochHash(),
                "Next epoch hash should be null");
    }

    /**
     * Verifies that all transactions from consensus events created are sent to {@link
     * SwirldState#handleConsensusRound(Round, SwirldDualState)} in a round exactly once.
     *
     * @param seed            random seed this test uses for address book generation
     * @param numNodes        the number of nodes in the network
     * @param numEvents       the number of events to submit
     * @param origSwirldState the {@link SwirldState} instance to use in the test
     */
    void testPostConsensusHandle(
            final Long seed, final int numNodes, final int numEvents, final SwirldState origSwirldState) {
        testPostConsensusHandle(seed, numNodes, numEvents, origSwirldState, null);
    }

    /**
     * Verifies that all transactions from consensus events created are sent to {@link
     * SwirldState#handleConsensusRound(Round, SwirldDualState)} in a round exactly once.
     *
     * @param seed      random seed this test uses for address book generation
     * @param numEvents the number of events to submit
     * @param state     initial state to use, or null to use a genesis state
     */
    void testPostConsensusHandle(
            final Long seed,
            final int numNodes,
            final int numEvents,
            final SwirldState origSwirldState,
            final State state) {

        final Random random = RandomUtils.initRandom(seed);
        init(random, numNodes, origSwirldState, state);
        final EventFlowWrapper wrapper = createEventFlowWrapper(random, numNodes);

        final List<ConsensusRound> consensusRounds = wrapper.applyConsensusRounds(addressBook, numEvents);

        // Extract the transactions from self events
        final HashSet<Transaction> selfConsensusTransactions =
                extractTransactions((id) -> id == selfId, consensusRounds);

        // Extract the transactions from other events
        final HashSet<Transaction> otherConsensusTransactions =
                extractTransactions((id) -> id != selfId, consensusRounds);

        final TransactionTracker consensusState =
                (TransactionTracker) swirldStateManager.getConsensusState().getSwirldState();

        // Verify that all the transactions in the self consensus events were handled by state post-consensus
        assertContainsExactly(
                selfConsensusTransactions,
                consensusState.getPostConsensusSelfTransactions(),
                "Post-consensus self transactions");

        // Verify that all the transactions in the other consensus events were handled by state post-consensus
        assertContainsExactly(
                otherConsensusTransactions,
                consensusState.getPostConsensusOtherTransactions(),
                "Post-consensus other transactions");

        verifyNoFailures(consensusState);
    }

    /**
     * Verifies that signed states are created for the appropriate rounds given the {@link
     * SettingsProvider#getSignedStateFreq()}.
     * <p>
     * Some developers have seen this test hang when running locally. There is a known memory leak with SS1
     * (https://github.com/swirlds/swirlds-platform/issues/4776) that causes the hang-up when running with multiple SS1
     * test parameters back to back if the JVM is not given enough memory. If you experience this test hanging, increase
     * the memory. The hang-up is not necessarily limited to this test.
     *
     * @param seed            random seed
     * @param numNodes        the number of nodes in the network
     * @param numEvents       the number of events to generate
     * @param signedStateFreq the frequency at which to create signed states
     * @param origSwirldState the {@link SwirldState} instance to use in the test
     */
    void testSignedStateSettings(
            final Long seed,
            final int numNodes,
            final int numEvents,
            final int signedStateFreq,
            final SwirldState origSwirldState) {
        final Random random = RandomUtils.initRandom(seed);

        when(settingsProvider.getSignedStateFreq()).thenReturn(signedStateFreq);

        init(random, numNodes, origSwirldState);
        final EventFlowWrapper wrapper = createEventFlowWrapper(random, numNodes);

        final List<ConsensusRound> consensusRounds = wrapper.applyConsensusRounds(addressBook, numEvents);

        // Get all the rounds for which we expect a signed state,
        // then remove all the rounds for which we have a signed state
        // to get all the rounds that should have a signed state but do not.
        final Set<Long> missingSignedStateRounds = getExpectedSignedStateRounds(signedStateFreq, consensusRounds);

        // wait until we received the number of signed states we expect to receive
        assertEventuallyTrue(
                () -> signedStateTracker.size() >= missingSignedStateRounds.size(),
                Duration.ofSeconds(5),
                "The expected number of signed states were not created");

        final List<SignedState> signedStates = new ArrayList<>();
        signedStateTracker.drainTo(signedStates);

        signedStates.forEach(ss -> missingSignedStateRounds.remove(ss.getRound()));

        // Check that there were no failures in the state
        final TransactionTracker currSwirldState = getCurrState();

        verifyNoFailures(currSwirldState);

        swirldStateManager.releaseCurrentSwirldState();

        assertEquals(
                0,
                missingSignedStateRounds.size(),
                String.format("Missing signed states for rounds %s", missingSignedStateRounds));

        for (final SignedState ss : signedStates) {
            final TransactionTracker ssSwirldState =
                    (TransactionTracker) ss.getState().getSwirldState();
            verifyNoFailures(ssSwirldState);
            ss.release();
        }
    }

    /**
     * Verifies that signed states are created with {@link SignedState#isFreezeState()} equal to {@code true} when
     * creating a signed state in the freeze period.
     *
     * @param seed               random seed
     * @param numNodes           the number of nodes in the network
     * @param numEvents          the number of events to generate
     * @param origSwirldState    the {@link SwirldState} instance to use in the test
     * @param desiredFreezeRound the round to freeze in
     */
    void testSignedStateFreezePeriod(
            final Long seed,
            final int numNodes,
            final int numEvents,
            final int signedStateFreq,
            final int desiredFreezeRound,
            final SwirldState origSwirldState) {
        final Random random = RandomUtils.initRandom(seed);

        when(settingsProvider.getSignedStateFreq()).thenReturn(signedStateFreq);

        // Will hold the freeze round when the last event of the round is generated
        final AtomicLong freezeRound = new AtomicLong(-1);

        init(random, numNodes, origSwirldState);
        final EventFlowWrapper wrapper = createEventFlowWrapper(random, numNodes);

        final List<ConsensusRound> consensusRounds =
                wrapper.applyConsensusRounds(addressBook, numEvents, newConsRound -> {
                    final long round = newConsRound.getRoundNum();

                    // Setup this test to indicate there is a freeze period when the last event for
                    // round == signedStateFreq has reached consensus, then not again.
                    if (round == desiredFreezeRound && freezeRound.compareAndSet(-1, round)) {
                        System.out.println("Setting freezeTime to "
                                + newConsRound.getLastEvent().getLastTransTime());
                        swirldStateManager
                                .getConsensusState()
                                .getSwirldDualState()
                                .setFreezeTime(newConsRound.getLastEvent().getLastTransTime());
                    }
                });

        final int finalExpectedNum =
                getExpectedSignedStateRoundsWithFreeze(signedStateFreq, freezeRound.get(), consensusRounds);

        assertEventuallyTrue(
                () -> signedStateTracker.size() >= finalExpectedNum,
                Duration.ofSeconds(5),
                "The number of expected signed states were not created");

        // Check that there were no failures in the state
        final TransactionTracker currSwirldState = getCurrState();

        verifyNoFailures(currSwirldState);

        // Verify the correct number of signed states
        final List<SignedState> signedStates = new ArrayList<>();
        signedStateTracker.drainTo(signedStates);
        assertEquals(finalExpectedNum, signedStates.size(), "Incorrect number of signed states created.");

        // Verify the correct freeze round signed state
        for (final SignedState signedState : signedStates) {
            final TransactionTracker ssSwirldState =
                    (TransactionTracker) signedState.getState().getSwirldState();
            verifyNoFailures(ssSwirldState);

            if (signedState.getRound() == freezeRound.get()) {
                assertTrue(
                        signedState.isFreezeState(),
                        String.format("Signed state for round %s should be a freeze state.", freezeRound));
            }

            signedState.release();
        }
        swirldStateManager.releaseCurrentSwirldState();
    }

    /**
     * Calculates the number of expected signed state when there is a freeze.
     *
     * @param signedStateFreq the frequency of states to sign
     * @param freezeRound     the round of the freeze
     * @param consensusRounds the consensus rounds created
     * @return the number of expected signed states
     */
    private int getExpectedSignedStateRoundsWithFreeze(
            final int signedStateFreq, final long freezeRound, final List<ConsensusRound> consensusRounds) {
        // get the number of state signed before the freeze
        int numStatesSigned = (int) Math.floorDiv(freezeRound, signedStateFreq);

        // increment by one for the freeze state if it wasn't already accounted for above
        if (freezeRound % signedStateFreq != 0) {
            numStatesSigned++;
        }

        for (final ConsensusRound round : consensusRounds) {
            for (final EventImpl consEvent : round.getConsensusEvents()) {
                // Round 1 is always signed regardless of the signedStateFreq,
                // so if we had a round 1, and it wasn't signed due to the signedStateFreq,
                // and wasn't the freeze round, then increase the expected count by 1
                if (signedStateFreq != 1 && freezeRound != 1 && consEvent.getRoundReceived() == 1) {
                    numStatesSigned++;
                    break;
                }
            }
        }
        return numStatesSigned;
    }

    /**
     * Verifies that system transaction are handled pre-consensus.
     *
     * @param seed            random seed
     * @param numNodes        the number of nodes in the network
     * @param numTransactions the number of transactions to generate
     * @param origSwirldState the {@link SwirldState} instance to use in the test
     */
    void testPreConsensusSystemTransactions(
            final Long seed, final int numNodes, final int numTransactions, final SwirldState origSwirldState) {
        final Random random = RandomUtils.initRandom(seed);
        init(random, numNodes, origSwirldState);
        final EventFlowWrapper wrapper = createEventFlowWrapper(random, numNodes);

        final Set<ConsensusTransactionImpl> transactions = wrapper.applyPreConsensusEvents(
                numTransactions, EventFlowTestUtils.createEventEmitter(random, numNodes, 1.0));

        assertEventuallyEquals(
                transactions.size(),
                () -> systemTransactionTracker.getPreConsensusTransactions().size(),
                Duration.ofSeconds(1),
                "Pre-consensus system transactions not handled");

        final TransactionTracker currSwirldState = getCurrState();

        verifyNoFailures(currSwirldState);
        verifyNoFailures(systemTransactionTracker);

        assertContainsExactly(
                transactions, systemTransactionTracker.getPreConsensusTransactions(), "System transactions");

        swirldStateManager.releaseCurrentSwirldState();
    }

    /**
     * Verifies that system transaction are handled post-consensus by the system transaction handler.
     *
     * @param seed            random seed
     * @param numNodes        the number of nodes in the network
     * @param numEvents       the number of events to generate
     * @param origSwirldState the {@link SwirldState} instance to use in the test
     */
    void testConsensusSystemTransactions(
            final Long seed, final int numNodes, final int numEvents, final SwirldState origSwirldState) {
        final Random random = RandomUtils.initRandom(seed);
        init(random, numNodes, origSwirldState);
        final EventFlowWrapper wrapper = createEventFlowWrapper(random, numNodes);

        final List<ConsensusRound> consensusRounds = wrapper.applyConsensusRounds(
                addressBook, numEvents, EventFlowTestUtils.createEventEmitter(random, numNodes, 1.0));

        final HashSet<Transaction> systemTransactions = extractTransactions((selfNodeId) -> true, consensusRounds);

        assertEventuallyEquals(
                systemTransactions.size(),
                () -> systemTransactionTracker.getConsensusTransactions().size(),
                Duration.ofSeconds(5),
                "Consensus system transactions not handled");

        // Retrieve the current state. The reference to the current state changes if a shuffle is performed.
        final TransactionTracker currSwirldState = getCurrState();

        verifyNoFailures(currSwirldState);
        verifyNoFailures(systemTransactionTracker);

        for (final Transaction t : systemTransactions) {
            assertNull(t.getMetadata(), "Metadata future should always be null on system transactions");
        }

        assertContainsExactly(
                systemTransactions,
                systemTransactionTracker.getConsensusTransactions(),
                "Consensus system transactions");

        swirldStateManager.releaseCurrentSwirldState();
    }

    /**
     * Calculates a set of round numbers that should have signed states created.
     *
     * @param signedStateFreq the frequency at which to create signed states
     * @param consensusRounds rounds with events that reached consensus
     * @return the round numbers for which signed states should be created
     */
    private Set<Long> getExpectedSignedStateRounds(
            final int signedStateFreq, final List<ConsensusRound> consensusRounds) {
        if (signedStateFreq < 1) {
            return Collections.emptySet();
        }

        final Set<Long> expectedRounds = new HashSet<>();
        for (final ConsensusRound round : consensusRounds) {
            final long roundNum = round.getRoundNum();
            if (roundNum == 1 || roundNum % signedStateFreq == 0) {
                expectedRounds.add(roundNum);
            }
        }
        return expectedRounds;
    }

    protected void init(final Random random, final int numNodes, final SwirldState swirldState) {
        init(random, numNodes, swirldState, null);
    }

    protected void init(
            final Random random, final int numNodes, final SwirldState swirldState, final State initialState) {
        addressBook = new RandomAddressBookGenerator(random)
                .setSize(numNodes)
                .setStakeDistributionStrategy(RandomAddressBookGenerator.StakeDistributionStrategy.BALANCED)
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.REAL_HASH)
                .setSequentialIds(true)
                .build();
        when(settingsProvider.getTransactionMaxBytes()).thenReturn(TX_MAX_BYTES);
        when(settingsProvider.getDelayShuffle()).thenReturn(SHUFFLE_DELAY_MS);
        when(settingsProvider.getThrottleTransactionQueueSize()).thenReturn(THROTTLE_TRANSACTION_QUEUE_SIZE);
        when(settingsProvider.getMaxEventQueueForCons()).thenReturn(500);
        when(settingsProvider.getMaxTransactionBytesPerEvent()).thenReturn(2048);

        final ConsensusHandlingMetrics consStats = mock(ConsensusHandlingMetrics.class);
        when(consStats.getConsCycleStat()).thenReturn(mock(CycleTimingStat.class));
        when(consStats.getNewSignedStateCycleStat()).thenReturn(mock(CycleTimingStat.class));

        final ConsensusMetrics consensusMetrics = new NoOpConsensusMetrics();

        final SwirldsPlatform platform = mock(SwirldsPlatform.class);
        when(platform.getSelfId()).thenReturn(selfNodeId);

        final EventStreamManager<EventImpl> eventStreamManager = mock(EventStreamManager.class);
        final RunningHashCalculator runningHashCalculator = new RunningHashCalculator();

        // Set up the running hash calculator, required if signed states are created
        doAnswer((Answer<Void>) invocation -> {
                    final Object[] args = invocation.getArguments();
                    final List<EventImpl> events = (List<EventImpl>) args[0];

                    // calculates and updates runningHash
                    events.forEach(runningHashCalculator::calculateRunningHash);

                    return null;
                })
                .when(eventStreamManager)
                .addEvents(anyList());

        final State state = getInitialState(swirldState, initialState);

        systemTransactionTracker = new SystemTransactionTracker();
        signedStateTracker = new ArrayBlockingQueue<>(100);

        if (swirldState instanceof SwirldState2) {
            swirldStateManager = new SwirldStateManagerDouble(
                    selfNodeId,
                    systemTransactionTracker,
                    mock(SwirldStateMetrics.class),
                    settingsProvider,
                    () -> false,
                    state);
        } else {
            swirldStateManager = new SwirldStateManagerSingle(
                    getStaticThreadManager(),
                    selfNodeId,
                    systemTransactionTracker,
                    platform.getContext(),
                    mock(SwirldStateMetrics.class),
                    consensusMetrics,
                    settingsProvider,
                    inaccurateConsensusTimeEstimater(random),
                    () -> false,
                    state);
        }

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        preConsensusEventHandler = new PreConsensusEventHandler(
                getStaticThreadManager(), selfNodeId, platformContext, swirldStateManager, consensusMetrics);
        consensusEventHandler = new ConsensusRoundHandler(
                platformContext,
                getStaticThreadManager(),
                selfId,
                settingsProvider,
                swirldStateManager,
                consStats,
                eventStreamManager,
                signedStateTracker,
                () -> {},
                (round) -> {},
                SoftwareVersion.NO_VERSION);
    }

    private State getInitialState(final SwirldState swirldState, final State initialState) {
        final State state;
        if (initialState == null) {
            state = new State();
            state.setDualState(new DualStateImpl());
            final PlatformState platformState = new PlatformState();
            platformState.setPlatformData(new PlatformData());
            state.setPlatformState(platformState);
        } else {
            state = initialState;
        }
        state.setSwirldState(swirldState);
        return state;
    }

    protected EventFlowWrapper createEventFlowWrapper(final Random random, final int numNodes) {
        return new EventFlowWrapper(
                random, numNodes, preConsensusEventHandler, consensusEventHandler, swirldStateManager);
    }

    /**
     * Extracts the transactions from events whose node id passes the {@code nodeIdMatcher} predicate.
     *
     * @param nodeIdMatcher predicate determining if the transactions in an event should be added to the returned set
     * @param rounds        consensus rounds with events to extract transactions from
     * @return a set of transactions from the matching events
     */
    protected HashSet<Transaction> extractTransactions(
            final Predicate<Long> nodeIdMatcher, final List<ConsensusRound> rounds) {
        final HashSet<Transaction> selfTransactions = new HashSet<>();
        for (final ConsensusRound round : rounds) {
            for (final EventImpl event : round.getConsensusEvents()) {
                if (nodeIdMatcher.test(event.getCreatorId())) {
                    selfTransactions.addAll(Arrays.asList(event.getTransactions()));
                }
            }
        }
        return selfTransactions;
    }

    protected void assertContainsExactly(
            final Collection<? extends Transaction> expected,
            final Set<? extends Transaction> actual,
            final String desc) {

        // Verify that all the transactions in the self consensus events were handled by state post-consensus
        assertEventuallyEquals(
                expected.size(),
                actual::size,
                Duration.ofSeconds(4),
                String.format(
                        "%s contains a different number of transactions. Expected: %s, actual: %s",
                        desc, expected.size(), actual.size()));
        expected.forEach(tx -> assertTrue(actual.contains(tx), desc + " does not contain expected transaction " + tx));
    }

    protected TransactionTracker getCurrState() {
        return (TransactionTracker) swirldStateManager.getCurrentSwirldState();
    }

    protected void verifyNoFailures(final Failable failable) {
        failable.assertNoFailure();
    }

    protected void verifyPreHandleTransactions(final int expectedNumPreHandle, final SwirldState origSwirldState) {
        assertEventuallyEquals(
                expectedNumPreHandle,
                () -> ((TransactionTracker) origSwirldState)
                        .getPreHandleTransactions()
                        .size(),
                Duration.ofSeconds(1),
                String.format(
                        "Incorrect number of preHandle() transactions. Expected %s, found %s",
                        expectedNumPreHandle,
                        ((TransactionTracker) origSwirldState)
                                .getPreHandleTransactions()
                                .size()));
    }
}
