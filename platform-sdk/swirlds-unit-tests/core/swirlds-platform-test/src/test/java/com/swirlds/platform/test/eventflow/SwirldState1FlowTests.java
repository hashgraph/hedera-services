/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.SwirldState1;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.eventhandling.SwirldStateSingleTransactionPool;
import com.swirlds.platform.state.SwirldStateManagerSingle;
import com.swirlds.test.framework.TestQualifierTags;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests the flow of transactions and events through the system for {@link SwirldState1} applications
 */
public class SwirldState1FlowTests extends EventFlowTests {

    private SwirldState1Tracker origSwirldState;

    private static Stream<Arguments> preConsParams() {
        return Stream.of(Arguments.of(null, 4, 1_000), Arguments.of(null, 4, 10_000));
    }

    private static Stream<Arguments> shuffleParams() {
        return Stream.of(Arguments.of(null, 4, 100), Arguments.of(null, 7, 1000));
    }

    @BeforeEach
    void setup() {
        origSwirldState = new SwirldState1Tracker();
    }

    @Override
    @AfterEach
    void cleanup() {
        origSwirldState.getPreHandleTransactions().clear();
        preConsensusEventHandler.stop();
        consensusEventHandler.stop();
    }

    /**
     * @see #testPreHandle(Long, int, SwirldState, Function)
     */
    @Disabled
    @ParameterizedTest
    @MethodSource({"preConsParams"})
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Self transactions sent to preHandle")
    void testPreHandleSelfTransaction(final Long seed, final int numNodes, final int numTransactions) {
        testPreHandle(
                seed,
                numNodes,
                origSwirldState.waitForMetadata(true),
                (w) -> w.submitSelfTransactions(numTransactions));
        verifyNoStateFailures();
    }

    /**
     * @see #testPreHandle(Long, int, SwirldState, Function)
     */
    @Disabled
    @ParameterizedTest
    @MethodSource({"preConsParams"})
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Other transactions sent to preHandle")
    void testPreHandleOtherTransaction(final Long seed, final int numNodes, final int numTransactions) {
        testPreHandle(
                seed,
                numNodes,
                origSwirldState.waitForMetadata(true),
                (w) -> w.applyPreConsensusEvents(numTransactions, (event) -> event.getCreatorId() != selfId));
        verifyNoStateFailures();
    }

    /**
     * <p>Verifies that all transactions submitted by this node are sent to
     * {@link SwirldState1#handleTransaction(long, Instant, Instant, Transaction, SwirldDualState)} exactly once.</p>
     *
     * <p>This test only applies to {@link SwirldState}</p>
     *
     * @param seed
     * 		random seed this test uses for address book generation
     * @param numTransactions
     * 		the number of self transactions to submit
     */
    @Disabled
    @ParameterizedTest
    @MethodSource({"preConsParams"})
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Self transactions handled pre-consensus")
    void testSelfTxnPreConsHandle(final Long seed, final int numNodes, final int numTransactions) {
        final Random random = RandomUtils.initRandom(seed);
        init(random, numNodes, origSwirldState.waitForMetadata(true));
        final EventFlowWrapper wrapper = createEventFlowWrapper(random, numNodes);

        // Submits transactions
        final HashSet<ConsensusTransactionImpl> selfTransactions = wrapper.submitSelfTransactions(numTransactions);

        // Give the threads some time to process the transactions
        assertEventuallyEquals(
                0,
                () -> ((SwirldStateSingleTransactionPool) swirldStateManager.getTransactionPool()).getCurrSize(),
                Duration.ofSeconds(1),
                "transEvent was not drained");

        // Retrieve the current state. The reference to the current state changes when a shuffle is performed.
        final SwirldState1Tracker currSwirldState = getCurrState1();

        // Basic checks on the states
        verifyNoFailures(origSwirldState);
        verifyNoFailures(getConsState1());
        verifyNoFailures(currSwirldState);

        assertContainsExactly(
                selfTransactions, currSwirldState.getPreConsensusSelfTransactions(), "Pre-consensus self transactions");

        swirldStateManager.releaseCurrentSwirldState();
    }

    /**
     * Verifies that all transactions from pre-consensus events created by other nodes are sent to {@link
     * SwirldState1#handleTransaction(long, Instant, Instant, Transaction, SwirldDualState)} exactly once.
     *
     * @param seed
     * 		random seed this test uses for address book generation
     * @param numTransactions
     * 		the number of transactions to submit
     */
    @Disabled
    @ParameterizedTest
    @MethodSource("preConsParams")
    @Tag(TIME_CONSUMING)
    @DisplayName("Other transactions handled pre-consensus")
    void testOtherTxnPreConsensusHandle(final Long seed, final int numNodes, final int numTransactions) {
        final Random random = RandomUtils.initRandom(seed);
        init(random, numNodes, origSwirldState.waitForMetadata(true));
        final EventFlowWrapper wrapper = createEventFlowWrapper(random, numNodes);

        final HashSet<ConsensusTransactionImpl> otherTransactions =
                wrapper.applyPreConsensusEvents(numTransactions, indexedEvent -> indexedEvent.getCreatorId() != selfId);

        // Retrieve the current state. The reference to the current state changes if a shuffle is performed.
        final SwirldState1Tracker currSwirldState = getCurrState1();

        // Basic checks on the states
        verifyNoFailures(origSwirldState);
        verifyNoFailures(getConsState1());
        verifyNoFailures(currSwirldState);

        assertContainsExactly(
                otherTransactions,
                currSwirldState.getPreConsensusOtherTransactions(),
                "Pre-consensus other transactions");

        verifyPreHandleTransactions(otherTransactions.size(), origSwirldState);

        swirldStateManager.releaseCurrentSwirldState();
    }

    /**
     * @see #testPostConsensusHandle(Long, int, int, SwirldState)
     */
    @Disabled
    @ParameterizedTest
    @MethodSource("postConsHandleParams")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Transactions handled post-consensus")
    void testPostConsensusHandle(final Long seed, final int numNodes, final int numEvents) {
        testPostConsensusHandle(seed, numNodes, numEvents, origSwirldState);

        verifyNoFailures(origSwirldState);
    }

    /**
     * @see #testPostConsensusHandleEpochUpdate(Long, int, int, SwirldState)
     */
    @Disabled
    @ParameterizedTest
    @MethodSource("postConsHandleParams")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Next epoch copied to epoch")
    void testPostConsensusHandleEpochUpdate(final Long seed, final int numNodes, final int numEvents) {
        testPostConsensusHandleEpochUpdate(seed, numNodes, numEvents, origSwirldState);
        verifyNoStateFailures();
    }

    /**
     * @see #testSignedStateSettings(Long, int, int, int, SwirldState)
     */
    @Disabled
    @ParameterizedTest
    @MethodSource("signedStateParams")
    @Tag(TIME_CONSUMING)
    @DisplayName("Signed states created for the correct rounds")
    void testSignedStateSettings(final Long seed, final int numNodes, final int numEvents, final int signedStateFreq) {
        testSignedStateSettings(seed, numNodes, numEvents, signedStateFreq, origSwirldState);
        verifyNoStateFailures();
    }

    /**
     * @see #testSignedStateFreezePeriod(Long, int, int, int, int, SwirldState)
     */
    @Disabled
    @ParameterizedTest
    @MethodSource("freezePeriodParams")
    @Tag(TIME_CONSUMING)
    @DisplayName("Signed state created for freeze periods")
    void testSignedStateFreezePeriod(
            final Long seed,
            final int numNodes,
            final int numEvents,
            final int signedStateFreq,
            final int desiredFreezeRound) {
        testSignedStateFreezePeriod(seed, numNodes, numEvents, signedStateFreq, desiredFreezeRound, origSwirldState);
        verifyNoStateFailures();
    }

    /**
     * @see #testPreConsensusSystemTransactions(Long, int, int, SwirldState)
     */
    // @Order(9)
    @Disabled
    @ParameterizedTest
    @MethodSource({"sysTransParams"})
    @DisplayName("System transactions are handled pre-consensus")
    void testPreConsensusSystemTransactions(final Long seed, final int numNodes, final int numTransactions) {
        testPreConsensusSystemTransactions(seed, numNodes, numTransactions, origSwirldState);
        verifyNoStateFailures();
    }

    /**
     * @see #testConsensusSystemTransactions(Long, int, int)
     */
    @Disabled
    @ParameterizedTest
    @MethodSource("sysTransParams")
    @DisplayName("System transactions are handled post-consensus")
    void testConsensusSystemTransactions(final Long seed, final int numNodes, final int numEvents) {
        testConsensusSystemTransactions(seed, numNodes, numEvents, origSwirldState);

        final SwirldState1Tracker currSwirldState = getCurrState1();

        assertEquals(
                0,
                currSwirldState.getPreConsensusSelfTransactions().size(),
                "Self system transactions should NOT be handled by SwirldState.");
        assertEquals(
                0,
                currSwirldState.getPreConsensusOtherTransactions().size(),
                "Other system transactions should NOT be handled by SwirldState.");

        verifyNoStateFailures();
    }

    /**
     * Verifies that the order of transactions in the current state are updated, and that the latest current state
     * contains all the transactions in the original current state.
     *
     * @throws InterruptedException
     * 		if this thread is interrupted
     */
    @Disabled
    @ParameterizedTest
    @MethodSource("shuffleParams")
    @DisplayName("Shuffle test")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void testShuffle(final Long seed, final int numNodes, final int numOtherEvents) throws InterruptedException {
        final Random random = RandomUtils.initRandom(seed);
        init(random, numNodes, origSwirldState.waitForMetadata(true));

        final ShuffleTestRunner runner = new ShuffleTestRunner(
                random,
                selfId,
                addressBook,
                numNodes,
                preConsensusEventHandler,
                consensusEventHandler,
                systemTransactionTracker,
                (SwirldStateManagerSingle) swirldStateManager);

        runner.runTest(numOtherEvents);
        runner.verifyShuffle();

        verifyNoStateFailures();
    }

    private void verifyNoStateFailures() {
        verifyNoFailures(origSwirldState);
        verifyNoFailures(getConsState1());
    }

    private SwirldState1Tracker getCurrState1() {
        return (SwirldState1Tracker) swirldStateManager.getCurrentSwirldState();
    }

    private SwirldState1Tracker getConsState1() {
        return (SwirldState1Tracker) swirldStateManager.getConsensusState().getSwirldState();
    }
}
