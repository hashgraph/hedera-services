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

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.consensus.SyntheticSnapshot;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.CesEvent;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.system.status.actions.FreezePeriodEnteredAction;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.platform.test.fixtures.event.EventImplTestUtils;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link DefaultTransactionHandler}.
 */
class DefaultTransactionHandlerTests {
    private static final long ROUND_NUMBER = 5L;

    private Randotron random;
    private Time time;
    private AddressBook addressBook;

    @BeforeEach
    void setUp() {
        random = Randotron.create();
        time = new FakeTime();
        addressBook = RandomAddressBookBuilder.create(random)
                .withRealKeysEnabled(false)
                .withSize(4)
                .build();
    }

    private ConsensusRound newConsensusRound(
            final boolean pcesRound) {
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


    private static ConsensusRound mockConsensusRound(
            @NonNull final EventImpl keystoneEvent,
            @NonNull final List<EventImpl> events,
            final boolean pcesRound) {
        final ArrayList<CesEvent> streamedEvents = new ArrayList<>();
        for (final Iterator<EventImpl> iterator = events.iterator(); iterator.hasNext(); ) {
            final EventImpl event = iterator.next();
            final CesEvent cesEvent = new CesEvent(event.getBaseEvent(), ROUND_NUMBER, !iterator.hasNext());
            streamedEvents.add(cesEvent);
            cesEvent.getRunningHash().setHash(mock(Hash.class));
        }

        final ConsensusRound consensusRound = mock(ConsensusRound.class);
        when(consensusRound.getConsensusEvents())
                .thenReturn(events.stream().map(EventImpl::getBaseEvent).collect(Collectors.toList()));
        when(consensusRound.getNumEvents()).thenReturn(events.size());
        when(consensusRound.getConsensusTimestamp())
                .thenReturn(Time.getCurrent().now());
        when(consensusRound.getKeystoneEvent()).thenReturn(keystoneEvent.getBaseEvent());
        when(consensusRound.getRoundNum()).thenReturn(ROUND_NUMBER);
        when(consensusRound.isEmpty()).thenReturn(events.isEmpty());
        when(consensusRound.isPcesRound()).thenReturn(pcesRound);
        when(consensusRound.getStreamedEvents()).thenReturn(streamedEvents);

        return consensusRound;
    }

    private EventImpl buildEvent() {
        final EventImpl event = EventImplTestUtils.createEventImpl(
                new TestingEventBuilder(random).setConsensusTimestamp(time.now()), null, null);

        event.getBaseEvent().signalPrehandleCompletion();

        return event;
    }

    private static SwirldStateManager mockSwirldStateManager(@NonNull final PlatformState platformState) {
        final MerkleRoot consensusState = mock(MerkleRoot.class);
        final MerkleRoot stateForSigning = mock(MerkleRoot.class);
        when(consensusState.getPlatformState()).thenReturn(platformState);
        final SwirldStateManager swirldStateManager = mock(SwirldStateManager.class);
        when(swirldStateManager.getConsensusState()).thenReturn(consensusState);
        when(swirldStateManager.getStateForSigning()).thenReturn(stateForSigning);

        return swirldStateManager;
    }

    private static void assertEventReachedConsensus(@NonNull final EventImpl event) {
        assertTrue(event.getBaseEvent().getTransactionCount() > 0, "event should have transactions");
        event.getBaseEvent()
                .consensusTransactionIterator()
                .forEachRemaining(transaction -> assertNotNull(
                        transaction.getConsensusTimestamp(), "transaction should have a consensus timestamp"));
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
    void normalOperation(final boolean pcesRound) {
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
        tester.verifyRoundHandled(consensusRound);
        tester.verifyFrozen(false);

        assertEquals(pcesRound, handlerOutput.reservedSignedState().get().isPcesRound());
    }

    @Test
    @DisplayName("Round in freeze period")
    void freezeHandling() {
        final TransactionHandlerTester tester = new TransactionHandlerTester(addressBook);
        final ConsensusRound consensusRound = newConsensusRound(false);
        tester.platformState.setFreezeTime(consensusRound.getConsensusTimestamp());


        final StateAndRound handlerOutput = tester.getTransactionHandler().handleConsensusRound(consensusRound);
        assertNotEquals(null, handlerOutput, "new state should have been created");
        assertEquals(
                1,
                handlerOutput.reservedSignedState().get().getReservationCount(),
                "state should be returned with a reservation");

        consensusRound.getConsensusEvents().forEach(DefaultTransactionHandlerTests::assertEventReachedConsensus);

        assertEquals(1, tester.getSubmittedActions().size(), "the freeze status should have been submitted");
        assertEquals(FreezePeriodEnteredAction.class, tester.getSubmittedActions().getFirst().getClass());
        tester.verifyRoundHandled(consensusRound);
        tester.verifyFrozen(true);

        final ConsensusRound postFreezeConsensusRound = newConsensusRound(false);
        final StateAndRound postFreezeOutput = tester.getTransactionHandler().handleConsensusRound(postFreezeConsensusRound);
        assertNull(postFreezeOutput, "no state should be created after freeze period");

        postFreezeConsensusRound.getConsensusEvents().forEach(DefaultTransactionHandlerTests::assertEventDidNotReachConsensus);

        assertEquals(1, tester.getSubmittedActions().size(), "no new status should have been submitted");
        //verify(swirldStateManager).handleConsensusRound(consensusRound);
        //TODO verify round ont handled
        tester.verifyRoundHandled(consensusRound);
    }
}
