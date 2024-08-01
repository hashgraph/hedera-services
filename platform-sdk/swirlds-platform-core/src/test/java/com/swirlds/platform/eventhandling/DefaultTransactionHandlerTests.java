/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.eventhandling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.consensus.SyntheticSnapshot;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.actions.FreezePeriodEnteredAction;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link DefaultTransactionHandler}.
 */
class DefaultTransactionHandlerTests {
    private Randotron random;
    private AddressBook addressBook;

    @BeforeEach
    void setUp() {
        random = Randotron.create();
        addressBook = RandomAddressBookBuilder.create(random)
                .withRealKeysEnabled(false)
                .withSize(4)
                .build();
    }

    private ConsensusRound newConsensusRound(
            final boolean pcesRound) {
        //TODO events with no transactions
        final List<PlatformEvent> events = Stream.generate(
                        () -> new TestingEventBuilder(random).setConsensusTimestamp(random.nextInstant()).build())
                .limit(3)
                .toList();
        events.forEach(PlatformEvent::signalPrehandleCompletion);
        final PlatformEvent keystone = new TestingEventBuilder(random).build();
        keystone.signalPrehandleCompletion();
        final ConsensusRound round = new ConsensusRound(
                addressBook,
                events,
                keystone,
                new Generations(),
                EventWindow.getGenesisEventWindow(AncientMode.GENERATION_THRESHOLD),
                SyntheticSnapshot.GENESIS_SNAPSHOT,
                pcesRound,
                random.nextInstant());

        round.getStreamedEvents().forEach(cesEvent -> cesEvent.getRunningHash().setHash(random.nextHash()));
        return round;
    }

    private static void assertEventReachedConsensus(@NonNull final PlatformEvent event) {
        assertTrue(event.getTransactionCount() > 0, "event should have transactions");
        event.consensusTransactionIterator()
                .forEachRemaining(transaction -> assertNotNull(
                        transaction.getConsensusTimestamp(), "transaction should have a consensus timestamp"));
    }

    private static void assertEventDidNotReachConsensus(@NonNull final PlatformEvent event) {
        assertTrue(event.getTransactionCount() > 0, "event should have transactions");
        event                .consensusTransactionIterator()
                .forEachRemaining(transaction -> assertNull(
                        transaction.getConsensusTimestamp(), "transaction should not have a consensus timestamp"));
    }

    @DisplayName("Normal operation")
    @ParameterizedTest
    @CsvSource({"false", "true"})
    void normalOperation(final boolean pcesRound) throws InterruptedException {
        final TransactionHandlerTester tester = new TransactionHandlerTester(addressBook);
        final ConsensusRound consensusRound = newConsensusRound(pcesRound);

        final StateAndRound handlerOutput = tester.getTransactionHandler().handleConsensusRound(consensusRound);
        assertNotEquals(null, handlerOutput, "new state should have been created");
        assertEquals(
                1,
                handlerOutput.reservedSignedState().get().getReservationCount(),
                "state should be returned with a reservation");

        consensusRound.getConsensusEvents().forEach(DefaultTransactionHandlerTests::assertEventReachedConsensus);

        assertTrue(tester.getSubmittedActions().isEmpty(), "no status should have been submitted");
        assertEquals(1, tester.getHandledRounds().size(), "a round should have been handled");
        assertSame(consensusRound, tester.getHandledRounds().getFirst());
        assertNull(tester.getPlatformState().getLastFrozenTime());

        assertEquals(
                tester.getPlatformState().getLegacyRunningEventHash(),
                consensusRound
                        .getStreamedEvents()
                        .getLast()
                        .getRunningHash()
                        .getFutureHash()
                        .getAndRethrow(),
                "the running hash should be updated");
        assertEquals(pcesRound, handlerOutput.reservedSignedState().get().isPcesRound());
    }

    @Test
    @DisplayName("Round in freeze period")
    void freezeHandling() throws InterruptedException {
        final TransactionHandlerTester tester = new TransactionHandlerTester(addressBook);
        final ConsensusRound consensusRound = newConsensusRound(false);
        tester.getPlatformState().setFreezeTime(consensusRound.getConsensusTimestamp());


        final StateAndRound handlerOutput = tester.getTransactionHandler().handleConsensusRound(consensusRound);
        assertNotNull( handlerOutput, "new state should have been created");
        assertEquals(
                1,
                handlerOutput.reservedSignedState().get().getReservationCount(),
                "state should be returned with a reservation");

        consensusRound.getConsensusEvents().forEach(DefaultTransactionHandlerTests::assertEventReachedConsensus);

        assertEquals(1, tester.getSubmittedActions().size(), "the freeze status should have been submitted");
        assertEquals(FreezePeriodEnteredAction.class, tester.getSubmittedActions().getFirst().getClass());
        assertEquals(1, tester.getHandledRounds().size(), "a round should have been handled");
        assertSame(consensusRound, tester.getHandledRounds().getFirst(), "it should be the round we provided");
        assertNotNull(tester.getPlatformState().getLastFrozenTime());

        final ConsensusRound postFreezeConsensusRound = newConsensusRound(false);
        final StateAndRound postFreezeOutput = tester.getTransactionHandler().handleConsensusRound(postFreezeConsensusRound);
        assertNull(postFreezeOutput, "no state should be created after freeze period");

        postFreezeConsensusRound.getConsensusEvents().forEach(DefaultTransactionHandlerTests::assertEventDidNotReachConsensus);

        assertEquals(1, tester.getSubmittedActions().size(), "no new status should have been submitted");
        assertEquals(1, tester.getHandledRounds().size(), "no new rounds should have been handled");
        assertSame(consensusRound, tester.getHandledRounds().getFirst(), "it should same round as before");
        assertEquals(
                tester.getPlatformState().getLegacyRunningEventHash(),
                consensusRound
                        .getStreamedEvents()
                        .getLast()
                        .getRunningHash()
                        .getFutureHash()
                        .getAndRethrow(),
                "the running hash should from the freeze round");
    }
}
