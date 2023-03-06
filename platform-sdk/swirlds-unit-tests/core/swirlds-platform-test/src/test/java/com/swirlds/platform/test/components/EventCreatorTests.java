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

package com.swirlds.platform.test.components;

import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.EventCreationRule;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.PlatformEvent;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.platform.components.EventCreationRules;
import com.swirlds.platform.components.EventHandler;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.TransactionPool;
import com.swirlds.platform.components.TransactionSupplier;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.event.creation.AncientParentsRule;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.sync.Generations;
import com.swirlds.platform.test.event.EventMocks;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.swirlds.common.system.EventCreationRuleResponse.DONT_CREATE;
import static com.swirlds.common.system.EventCreationRuleResponse.PASS;
import static com.swirlds.common.test.RandomUtils.randomHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Event Creator Tests")
class EventCreatorTests {
    private static final NodeId selfId = new NodeId(false, 1234);
    private static final Signer noOpSigner =
            (bytes) -> new Signature(SignatureType.RSA, new byte[SignatureType.RSA.signatureLength()]);
    private static final GraphGenerations defaultGenerations = new Generations(
            GraphGenerations.FIRST_GENERATION, GraphGenerations.FIRST_GENERATION, GraphGenerations.FIRST_GENERATION);
    private static final Supplier<GraphGenerations> defaultGenerationsSupplier = () -> defaultGenerations;

    private static final TransactionSupplier defaultTransactionSupplier = () -> new SwirldTransaction[0];
    private static final EventHandler noOpEventHandler = (event) -> {};

    static final TransactionPool defaultTransactionPool = mock(TransactionPool.class);

    static {
        when(defaultTransactionPool.numTransForEvent()).thenReturn(0);
    }

    static final EventCreationRules defaultThrottles = new EventCreationRules(List.of());

    private EventMapper mockMapper() {
        return mockMapper(null, null);
    }

    private EventMapper mockMapper(final Map<Long, EventImpl> recentEvents, final EventImpl recentSelfEvent) {
        final EventMapper mapper = mock(EventMapper.class);

        if (recentEvents != null) {
            for (final Long nodeId : recentEvents.keySet()) {
                Mockito.when(mapper.getMostRecentEvent(nodeId)).thenReturn(recentEvents.get(nodeId));
                Mockito.when(mapper.getMostRecentSelfEvent()).thenReturn(recentSelfEvent);
            }
        }

        return mapper;
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("areBothParentsOld() Test")
    void areBothParentsOldTest() {

        final Set<BaseEvent> ancientEvents = new HashSet<>();
        final AtomicBoolean areAnyEventsAncient = new AtomicBoolean(false);
        final GraphGenerations graphGenerations = new GraphGenerations() {
            @Override
            public long getMaxRoundGeneration() {
                return FIRST_GENERATION;
            }

            @Override
            public long getMinGenerationNonAncient() {
                return FIRST_GENERATION;
            }

            @Override
            public long getMinRoundGeneration() {
                return FIRST_GENERATION;
            }

            @Override
            public boolean areAnyEventsAncient() {
                return areAnyEventsAncient.get();
            }

            @Override
            public boolean isAncient(final PlatformEvent event) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isAncient(final BaseEvent event) {
                return ancientEvents.contains(event);
            }
        };

        final AncientParentsRule ancientParentsCheck = new AncientParentsRule(() -> graphGenerations);

        final EventImpl oldSelfParent = mock(EventImpl.class);
        ancientEvents.add(oldSelfParent);
        final EventImpl oldOtherParent = mock(EventImpl.class);
        ancientEvents.add(oldOtherParent);

        final EventImpl youngSelfParent = mock(EventImpl.class);
        final EventImpl youngOtherParent = mock(EventImpl.class);

        final List<EventImpl> otherParents = new LinkedList<>();
        otherParents.add(oldOtherParent);
        otherParents.add(youngOtherParent);
        otherParents.add(null);

        final List<EventImpl> selfParents = new LinkedList<>();
        selfParents.add(oldSelfParent);
        selfParents.add(youngSelfParent);
        selfParents.add(null);

        for (final EventImpl otherParent : otherParents) {
            for (final EventImpl selfParent : selfParents) {
                assertFalse(
                        ancientParentsCheck.areBothParentsAncient(selfParent, otherParent),
                        "there should be no ancient events yet");
            }
        }
        areAnyEventsAncient.set(true);
        for (final EventImpl otherParent : otherParents) {
            for (final EventImpl selfParent : selfParents) {
                if (selfParent == youngSelfParent || otherParent == youngOtherParent) {
                    assertFalse(
                            ancientParentsCheck.areBothParentsAncient(selfParent, otherParent),
                            "if either parent is non-ancient, then we should create an event");
                } else {
                    assertTrue(
                            ancientParentsCheck.areBothParentsAncient(selfParent, otherParent),
                            "if neither parent is non-ancient, then we should NOT create an event");
                }
            }
        }
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("getTimeCreated() Test")
    void getTimeCreatedTest() {
        final Instant now = Instant.now();

        assertEquals(
                now, EventUtils.getChildTimeCreated(now, null), "time should not be increased for null self parent");

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);

        Mockito.when(hashedData.getTimeCreated()).thenReturn(now.minusNanos(1000));

        assertEquals(
                now,
                EventUtils.getChildTimeCreated(now, EventMocks.mockEvent(hashedData)),
                "time should not be increased if parent is in the past with no transactions");

        Mockito.when(hashedData.getTransactions()).thenReturn(new SwirldTransaction[100]);
        assertEquals(
                now,
                EventUtils.getChildTimeCreated(now, EventMocks.mockEvent(hashedData)),
                "time should not be increased if parent is in the past with a few transactions");

        Mockito.when(hashedData.getTransactions()).thenReturn(new SwirldTransaction[2000]);
        assertEquals(
                now.plusNanos(1000),
                EventUtils.getChildTimeCreated(now, EventMocks.mockEvent(hashedData)),
                "time should be increased so that 1 nanosecond passes per previous transaction");

        Mockito.when(hashedData.getTransactions()).thenReturn(new SwirldTransaction[0]);
        Mockito.when(hashedData.getTimeCreated()).thenReturn(now);
        assertEquals(
                now.plusNanos(1),
                EventUtils.getChildTimeCreated(now, EventMocks.mockEvent(hashedData)),
                "time should be increased so that 1 nanosecond since event with 0 transactions");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("getEventGeneration() Test")
    void getEventGenerationTest() {
        assertEquals(-1, EventUtils.getEventGeneration(null), "generation of a null event should equal -1");

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        Mockito.when(hashedData.getGeneration()).thenReturn(1234L);

        assertEquals(
                hashedData.getGeneration(),
                EventUtils.getEventGeneration(EventMocks.mockEvent(hashedData)),
                "should return generation of non-null event");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("getEventHash() Test")
    void getEventHashTest() {
        assertNull(EventUtils.getEventHash(null), "hash of null event should be null");

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        Mockito.when(hashedData.getHash()).thenReturn(randomHash());

        assertSame(
                hashedData.getHash().getValue(),
                EventUtils.getEventHash(EventMocks.mockEvent(hashedData)),
                "should return the hash of a non-null event");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("getOtherParentCreatorId() Test")
    void getOtherParentCreatorIdTest() {
        assertEquals(
                EventConstants.CREATOR_ID_UNDEFINED,
                EventUtils.getCreatorId(null),
                "null event should have creator ID = CREATOR_ID_UNDEFINED");

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        Mockito.when(hashedData.getCreatorId()).thenReturn(4321L);

        assertEquals(
                hashedData.getCreatorId(),
                EventUtils.getCreatorId(EventMocks.mockEvent(hashedData)),
                "should have returned creator id of event");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Transactions Are Put Into Events Test")
    void transactionsArePutIntoEventsTest() {

        final SwirldTransaction[] transactions = new SwirldTransaction[10];
        for (int index = 0; index < transactions.length; index++) {
            transactions[index] = new SwirldTransaction(new byte[1]);
        }

        final EventImpl parent = EventMocks.mockEvent(mock(BaseEventHashedData.class));
        Mockito.when(parent.getBaseEventHashedData().getHash()).thenReturn(randomHash());
        final Map<Long, EventImpl> recentEvents = new HashMap<>();
        recentEvents.put(0L, parent);
        final Queue<EventImpl> events = new LinkedList<>();

        final AccessibleEventCreator eventCreator = new AccessibleEventCreator(
                selfId,
                mockMapper(recentEvents, null),
                noOpSigner,
                defaultGenerationsSupplier,
                () -> transactions,
                events::add,
                defaultTransactionPool,
                () -> false,
                defaultThrottles);

        eventCreator.createEvent(0);

        assertEquals(1, events.size(), "expected for exactly 1 event to have been created");
        final Transaction[] transactionsInEvent = events.remove().getTransactions();

        assertEquals(transactionsInEvent.length, transactions.length, "expected number of transactions to match");
        for (int index = 0; index < transactions.length; index++) {
            assertSame(transactions[index], transactionsInEvent[index], "expected transaction to be in event");
        }
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Verify Event Data Test")
    void verifyEventDataTest() {

        final Queue<EventImpl> events = new LinkedList<>();

        final SwirldTransaction[] transactions = new SwirldTransaction[1000];
        for (int index = 0; index < transactions.length; index++) {
            transactions[index] = new SwirldTransaction(new byte[1]);
        }

        final BaseEventHashedData selfParent = mock(BaseEventHashedData.class);
        Mockito.when(selfParent.getHash()).thenReturn(randomHash());

        // Attempt to provoke an invalid timestamp by picking previous timestamp in the future
        final Instant prevEventTime = Instant.now().plusSeconds(10);
        Mockito.when(selfParent.getTimeCreated()).thenReturn(prevEventTime);
        final SwirldTransaction[] previousTransactions = new SwirldTransaction[1000];
        Mockito.when(selfParent.getTransactions()).thenReturn(previousTransactions);

        final BaseEventHashedData otherParentHashed = mock(BaseEventHashedData.class);
        Mockito.when(otherParentHashed.getHash()).thenReturn(randomHash());
        final EventImpl otherParent = EventMocks.mockEvent(otherParentHashed);

        final EventImpl selfParentImpl = EventMocks.mockEvent(selfParent);

        final Map<Long, EventImpl> recentEvents = new HashMap<>();
        recentEvents.put(selfId.getId(), selfParentImpl);
        recentEvents.put(1L, otherParent);

        final AccessibleEventCreator eventCreator = new AccessibleEventCreator(
                selfId,
                mockMapper(recentEvents, selfParentImpl),
                noOpSigner,
                defaultGenerationsSupplier,
                () -> transactions,
                events::add,
                defaultTransactionPool,
                () -> false,
                defaultThrottles);

        eventCreator.createEvent(1);

        assertEquals(1, events.size(), "expected an event to have been created");
        final EventImpl event = events.remove();

        assertEquals(selfId.getId(), event.getCreatorId(), "expected id to match self ID");
        assertTrue(
                event.getTimeCreated().isAfter(prevEventTime.plusNanos(previousTransactions.length - 1)),
                "expected timestamp to be greater than previous timestamp");

        assertSame(otherParent, event.getOtherParent(), "expected event to have other parent");
        assertSame(selfParentImpl, event.getSelfParent(), "expected event to self parent");

        assertEquals(
                selfParent.getGeneration(),
                event.getBaseEventHashedData().getSelfParentGen(),
                "self parent generation to match");

        assertEquals(
                otherParent.getGeneration(),
                event.getBaseEventHashedData().getOtherParentGen(),
                "expected other parent generation to match");

        assertEquals(selfParent.getHash(), event.getSelfParentHash(), "expected self parent hash to match");

        assertEquals(otherParentHashed.getHash(), event.getOtherParentHash(), "expected other parent hash to match");

        assertNotNull(event.getBaseEventHashedData().getHash(), "base event hashed data should be hashed");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Sequence Of Events Test")
    void sequenceOfEventsTest() {

        final Queue<EventImpl> events = new LinkedList<>();

        final BaseEventHashedData selfParentHashed = mock(BaseEventHashedData.class);
        final EventImpl selfParent = EventMocks.mockEvent(selfParentHashed);
        Mockito.when(selfParentHashed.getHash()).thenReturn(randomHash());
        Mockito.when(selfParentHashed.getTimeCreated()).thenReturn(Instant.now());
        final BaseEventHashedData otherParentHashed = mock(BaseEventHashedData.class);
        final EventImpl otherParent = EventMocks.mockEvent(otherParentHashed);
        Mockito.when(otherParentHashed.getHash()).thenReturn(randomHash());
        Mockito.when(otherParentHashed.getTimeCreated()).thenReturn(Instant.now());

        final EventMapper mapper = mock(EventMapper.class);
        Mockito.when(mapper.getMostRecentSelfEvent()).thenReturn(selfParent);
        Mockito.when(mapper.getMostRecentEvent(0L)).thenReturn(otherParent);

        final AccessibleEventCreator eventCreator = new AccessibleEventCreator(
                selfId,
                mapper,
                noOpSigner,
                defaultGenerationsSupplier,
                defaultTransactionSupplier,
                (e) -> {
                    events.add(e);
                    Mockito.when(mapper.getMostRecentSelfEvent()).thenReturn(e);
                },
                defaultTransactionPool,
                () -> false,
                defaultThrottles);

        for (int index = 0; index < 100; index++) {
            eventCreator.createEvent(0);
        }

        assertEquals(100, events.size(), "expected 100 events");

        EventImpl prev = selfParent;
        while (events.size() > 0) {
            final EventImpl next = events.remove();
            assertSame(next.getSelfParent(), prev, "expected event to be child of previous event");
            prev = next;
        }
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Test Throttle")
    void testThrottle() {
        final EventCreationRule mockRule = mock(EventCreationRule.class);
        when(mockRule.shouldCreateEvent()).thenReturn(DONT_CREATE);

        final EventImpl parent = EventMocks.mockEvent(mock(BaseEventHashedData.class));
        Mockito.when(parent.getBaseEventHashedData().getHash()).thenReturn(randomHash());
        final Map<Long, EventImpl> recentEvents = new HashMap<>();
        recentEvents.put(0L, parent);
        final Queue<EventImpl> events = new LinkedList<>();

        final AccessibleEventCreator eventCreator = new AccessibleEventCreator(
                selfId,
                mockMapper(recentEvents, null),
                noOpSigner,
                defaultGenerationsSupplier,
                defaultTransactionSupplier,
                events::add,
                defaultTransactionPool,
                () -> false,
                new EventCreationRules(List.of(mockRule)));

        eventCreator.createEvent(0);
        assertEquals(0, events.size(), "throttle should stop event creation");

        when(mockRule.shouldCreateEvent()).thenReturn(PASS);
        eventCreator.createEvent(0);
        assertEquals(1, events.size(), "throttle should not stop event creation");
    }
}
