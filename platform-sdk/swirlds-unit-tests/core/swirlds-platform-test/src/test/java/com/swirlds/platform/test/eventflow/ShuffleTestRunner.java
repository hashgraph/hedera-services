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
import static com.swirlds.common.test.AssertionUtils.assertEventuallyTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.components.EventIntake;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.PreConsensusEventHandler;
import com.swirlds.platform.intake.IntakeCycleStats;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.state.SwirldStateManagerSingle;
import com.swirlds.platform.sync.ShadowGraph;
import com.swirlds.platform.test.consensus.ConsensusUtils;
import com.swirlds.platform.test.event.emitter.EventEmitterFactory;
import com.swirlds.platform.test.event.emitter.StandardEventEmitter;
import com.swirlds.platform.test.event.source.EventSource;
import com.swirlds.platform.test.event.source.StandardEventSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Provides input to the various classes that move transactions and events through the system and records outputs to
 * support testing the shuffle process used by {@link SwirldState} type 1.
 */
public class ShuffleTestRunner {

    private final PreConsensusEventHandler preConsensusEventHandler;
    private final ConsensusRoundHandler consensusEventHandler;
    private final SwirldStateManagerSingle swirldStateManager;
    private final AddressBook addressBook;
    private final int numNodes;
    private final Random random;
    private final long selfId;
    private final SystemTransactionTracker systemTransactionTracker;

    private TransactionFeeder transactionFeeder;
    private EventSubmitter selfEventSubmitter;
    private EventSubmitter otherEventSubmitter;
    private ConsensusEventObserver consensusEventObserver;

    /** A reference to the original current state prior to handling any events */
    private SwirldState1Tracker originalState;

    public ShuffleTestRunner(
            final Random random,
            final long selfId,
            final AddressBook addressBook,
            final int numNodes,
            final PreConsensusEventHandler preConsensusEventHandler,
            final ConsensusRoundHandler consensusEventHandler,
            final SystemTransactionTracker systemTransactionTracker,
            final SwirldStateManagerSingle swirldStateManager) {
        this.random = random;
        this.selfId = selfId;
        this.preConsensusEventHandler = preConsensusEventHandler;
        this.consensusEventHandler = consensusEventHandler;
        this.systemTransactionTracker = systemTransactionTracker;
        this.swirldStateManager = swirldStateManager;
        this.numNodes = numNodes;
        this.addressBook = addressBook;

        initialize();
    }

    /**
     * Wires up all the classes that provide input and record output.
     */
    private void initialize() {
        final Consensus consensus =
                ConsensusUtils.buildSimpleConsensus(addressBook);

        // Keeps track of all the consensus events
        consensusEventObserver = new ConsensusEventObserver();

        // Set up a dispatcher to forward pre and/or post consensus events to these classes according to what they are
        // set up to receive
        final EventObserverDispatcher dispatcher =
                new EventObserverDispatcher(preConsensusEventHandler, consensusEventHandler, consensusEventObserver);

        // Set up event intake to get consensus events and send them to the dispatcher
        final EventIntake eventIntake = new EventIntake(
                new NodeId(false, selfId),
                mock(EventLinker.class),
                () -> consensus,
                addressBook,
                dispatcher,
                mock(IntakeCycleStats.class),
                mock(ShadowGraph.class));

        // Submit self transactions to the transaction submitter, which sends them to the SwirldStateManager and
        // EventTransactionPool
        transactionFeeder =
                new TransactionFeeder(random, selfId, swirldStateManager::submitTransaction, Duration.ofMillis(100));

        // Create self events with self transactions and submit them to event intake
        selfEventSubmitter = new EventSubmitter(
                // Create events with the self transaction in event flow
                createEventGeneratorWithEventPoolTransactions(random),
                (l) -> l == selfId,
                eventIntake::addEvent,
                1,
                Duration.ofMillis(150));

        // Create other events to simulate gossip threads and submit them to event intake
        otherEventSubmitter = new EventSubmitter(
                EventFlowTestUtils.createEventEmitter(random, numNodes, 0.1),
                (l) -> l != selfId,
                eventIntake::addEvent,
                10,
                Duration.ofMillis(100));
    }

    /**
     * Creates a {@link StandardEventEmitter} with custom event sources that use the transactions in {@code
     * swirldStateManager} intended for self events.
     *
     * @param random
     * 		the random instance to use for the {@link EventEmitterFactory}
     * @return the event generator
     */
    private StandardEventEmitter createEventGeneratorWithEventPoolTransactions(final Random random) {
        // Create standard event sources that generate self events with the self-transactions in swirldStateManager
        final Supplier<EventSource<?>> eventSourceSupplier = () -> new StandardEventSource(
                false, (r) -> swirldStateManager.getTransactionPool().getTransactions());

        final EventEmitterFactory eventGeneratorFactory = new EventEmitterFactory(random, numNodes);
        eventGeneratorFactory
                .getSourceFactory()
                .addCustomSource((nodeIndex) -> nodeIndex == selfId, eventSourceSupplier);
        return eventGeneratorFactory.newStandardEmitter();
    }

    /**
     * Runs a shuffle test using real consensus and various threads for submitting events from other nodes and this
     * node. The test runs until the number of other node events reaches at leave {@code numOtherEvents}.
     *
     * @param numOtherEvents
     * 		the number of other node events to submit in this test
     * @throws InterruptedException
     */
    public void runTest(final int numOtherEvents) throws InterruptedException {
        // Get a reference to the original current state before any events or transactions are handled
        originalState = (SwirldState1Tracker) swirldStateManager.getCurrentSwirldState();
        swirldStateManager.releaseCurrentSwirldState();

        // Start the event handling threads
        preConsensusEventHandler.start();
        consensusEventHandler.start();

        // Start submitting self transaction to event flow
        transactionFeeder.start();
        // Start submitting self events to event intake
        selfEventSubmitter.start();

        // Submit other node events to event intake and stop once numOtherEvents events are sent
        otherEventSubmitter.submitEventsAndStop(numOtherEvents);

        // Stop submitting self transactions
        transactionFeeder.stop();
        // Stop submitting self events
        selfEventSubmitter.stop();

        // Wait for event flow to finish applying the events to stateCurr and stateCons
        assertEventuallyEquals(
                0,
                () -> swirldStateManager.getTransactionPool().getCurrSize(),
                Duration.ofSeconds(5),
                "transCurr queue did not drain, size = "
                        + swirldStateManager.getTransactionPool().getCurrSize());
        assertEventuallyEquals(
                0,
                consensusEventHandler::getRoundsInQueue,
                Duration.ofSeconds(5),
                "round queue did not drain, size = " + consensusEventHandler.getRoundsInQueue());
    }

    /**
     * Performs several assertions to verify that the latest current state has been updated to contain transactions from
     * consensus ordered events.
     */
    public void verifyShuffle() {
        // A reference to the latest current state after events have finished processing
        final TransactionTracker currentState = (TransactionTracker) swirldStateManager.getCurrentSwirldState();

        verifySystemTransactions();
        verifyNoStateFailures(currentState);
        verifyNumTransaction(currentState);
        verifyTransactionOrderUpdated(currentState);
        verifyCurrentStateHasAllConsensusTxns(currentState);
        verifyCorrectTransactionOrder(currentState);

        swirldStateManager.releaseCurrentSwirldState();
    }

    private void verifySystemTransactions() {

        // System transactions generated by self, and received from others
        final int expectedNumSystemTrans =
                transactionFeeder.getNumSystemTransactions() + otherEventSubmitter.getNumSysTransactions();

        assertEventuallyEquals(
                transactionFeeder.getNumSystemTransactions(),
                () -> systemTransactionTracker.getNumPreConsByCreator(selfId),
                Duration.ofSeconds(2),
                String.format(
                        "Incorrect number of self pre-consensus system transactions handled. Expected %d but was %d",
                        transactionFeeder.getNumSystemTransactions(),
                        systemTransactionTracker.getNumPreConsByCreator(selfId)));

        assertEventuallyTrue(
                () -> systemTransactionTracker.getPreConsensusTransactions().size() == expectedNumSystemTrans,
                Duration.ofSeconds(5),
                String.format(
                        "All system transactions should have been handled pre-consensus. Expected %d but was %d",
                        expectedNumSystemTrans,
                        systemTransactionTracker.getPreConsensusTransactions().size()));
    }

    private void verifyCorrectTransactionOrder(final TransactionTracker currentState) {
        final List<HandledTransaction> currOrderedTransactions = currentState.getOrderedTransactions();
        for (int i = 1; i < currOrderedTransactions.size(); i++) {
            final HandledTransaction prevTrans = currOrderedTransactions.get(i - 1);
            final HandledTransaction trans = currOrderedTransactions.get(i);
            if (prevTrans.consensusTime() != null && trans.consensusTime() != null) {
                assertTrue(
                        prevTrans.consensusTime().isBefore(trans.consensusTime()),
                        "Consensus transactions were not applied to state in consensus order.");
            }
        }
    }

    /**
     * Verifies that the original and latest current states do not have any failures, such as seeing the same
     * transaction more than once.
     *
     * @param currentState
     * 		the latest current state
     */
    private void verifyNoStateFailures(final TransactionTracker currentState) {
        assertFalse(
                originalState.hasFailure(),
                String.format("Original current state has failure: %s", originalState.getFailure()));

        // The latest current state
        assertFalse(
                currentState.hasFailure(),
                String.format("Current current state has failure: %s", currentState.getFailure()));
    }

    /**
     * Verifies that the number of transactions in the latest current state is greater than those in the original
     * current state.
     *
     * @param currentState
     * 		the latest current state
     */
    private void verifyNumTransaction(final TransactionTracker currentState) {
        final List<HandledTransaction> currOrderedTransactions = currentState.getOrderedTransactions();
        assertTrue(
                currOrderedTransactions.size()
                        > originalState.getOrderedTransactions().size(),
                String.format(
                        "The latest current state should have more transactions than the original current state after"
                                + " two or more shuffles. Latest: %s, Original: %s",
                        currOrderedTransactions.size(),
                        originalState.getOrderedTransactions().size()));
    }

    /**
     * Verifies that the order of at least one transaction in the latest current state is different from the order in
     * the original current state.
     *
     * @param currentState
     * 		the latest current state
     */
    private void verifyTransactionOrderUpdated(final TransactionTracker currentState) {
        final List<HandledTransaction> origOrderedTransactions = originalState.getOrderedTransactions();
        final List<HandledTransaction> currOrderedTransactions = currentState.getOrderedTransactions();

        // Verify that the order of at least one transaction changed its order from the first current state and the
        // latest current state (updated by the shuffle process).
        boolean hasDifferentOrder = false;
        for (int i = 0; i < origOrderedTransactions.size(); i++) {
            final HandledTransaction originalOrderTx = origOrderedTransactions.get(i);
            if (!originalOrderTx.equals(currOrderedTransactions.get(i))) {
                hasDifferentOrder = true;
                break;
            }
        }

        assertTrue(hasDifferentOrder, "The current state transaction order was not updated.");
    }

    /**
     * Verifies that the latest current state contains all the transactions from all the consensus ordered events.
     *
     * @param currentState
     * 		the latest current state
     */
    private void verifyCurrentStateHasAllConsensusTxns(final TransactionTracker currentState) {

        // Get a list of all the transactions in the consensus events
        final List<ConsensusTransaction> consensusTransactions = new ArrayList<>();
        for (final EventImpl consensusEvent : consensusEventObserver.getConsensusEvents()) {
            Collections.addAll(consensusTransactions, consensusEvent.getTransactions());
        }

        final List<HandledTransaction> origOrderedTransactions = originalState.getOrderedTransactions();
        final List<HandledTransaction> currOrderedTransactions = currentState.getOrderedTransactions();

        int numNonConsensus = 0;
        for (final HandledTransaction originalOrderTx : origOrderedTransactions) {
            // Verify that the transaction is in the current state only if it reached consensus
            if (consensusTransactions.contains(originalOrderTx.transaction())) {
                assertTrue(
                        listContainsHandledTransaction(currOrderedTransactions, originalOrderTx),
                        String.format(
                                "The latest current state does not contain consensus transaction %s which is present "
                                        + "in the original current state.",
                                originalOrderTx));
            } else {
                numNonConsensus++;
            }
        }

        // For information only
        System.out.printf("%s transactions did not reach consensus.%n", numNonConsensus);
    }

    private boolean listContainsHandledTransaction(final List<HandledTransaction> list, final HandledTransaction tx) {
        for (final HandledTransaction listTx : list) {
            if (listTx.transaction() == tx.transaction()) {
                return true;
            }
        }
        return false;
    }
}
