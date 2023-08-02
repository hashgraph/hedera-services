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

package com.swirlds.platform.test.event.tipset;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomSignature;
import static com.swirlds.common.utility.CompareTo.isGreaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.components.transaction.TransactionSupplier;
import com.swirlds.platform.event.EventDescriptor;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.tipset.ChildlessEventTracker;
import com.swirlds.platform.event.tipset.TipsetEventCreator;
import com.swirlds.platform.event.tipset.TipsetEventCreatorImpl;
import com.swirlds.platform.event.tipset.TipsetTracker;
import com.swirlds.platform.event.tipset.TipsetUtils;
import com.swirlds.platform.event.tipset.TipsetWeightCalculator;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("TipsetEventCreatorImpl Tests")
class TipsetEventCreatorImplTests {

    /**
     * @param nodeId                 the node ID of the simulated node
     * @param tipsetTracker          tracks tipsets of events
     * @param tipsetEventCreator     the event creator for the simulated node
     * @param tipsetWeightCalculator used to sanity check event creation logic
     */
    private record SimulatedNode(
            @NonNull NodeId nodeId,
            @NonNull TipsetTracker tipsetTracker,
            @NonNull TipsetEventCreator tipsetEventCreator,
            @NonNull TipsetWeightCalculator tipsetWeightCalculator) {}

    /**
     * Build an event creator for a node.
     */
    @NonNull
    private TipsetEventCreator buildEventCreator(
            @NonNull final Random random,
            @NonNull final Time time,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId nodeId,
            @NonNull final TransactionSupplier transactionSupplier) {

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final Signer signer = mock(Signer.class);
        when(signer.sign(any())).thenAnswer(invocation -> randomSignature(random));

        final SoftwareVersion softwareVersion = new BasicSoftwareVersion(1);

        return new TipsetEventCreatorImpl(
                platformContext, time, random, signer, addressBook, nodeId, softwareVersion, transactionSupplier);
    }

    /**
     * Build an event creator for each node in the address book.
     */
    @NonNull
    private Map<NodeId, SimulatedNode> buildSimulatedNodes(
            @NonNull final Random random,
            @NonNull final Time time,
            @NonNull final AddressBook addressBook,
            @NonNull final TransactionSupplier transactionSupplier) {

        final Map<NodeId, SimulatedNode> eventCreators = new HashMap<>();
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        for (final Address address : addressBook) {

            final TipsetEventCreator eventCreator =
                    buildEventCreator(random, time, addressBook, address.getNodeId(), transactionSupplier);

            final TipsetTracker tipsetTracker = new TipsetTracker(time, addressBook);

            final ChildlessEventTracker childlessEventTracker = new ChildlessEventTracker();
            final TipsetWeightCalculator tipsetWeightCalculator = new TipsetWeightCalculator(
                    platformContext, time, addressBook, address.getNodeId(), tipsetTracker, childlessEventTracker);

            eventCreators.put(
                    address.getNodeId(),
                    new SimulatedNode(address.getNodeId(), tipsetTracker, eventCreator, tipsetWeightCalculator));
        }

        return eventCreators;
    }

    private void validateNewEvent(
            @NonNull final Map<Hash, EventImpl> events,
            @NonNull final GossipEvent newEvent,
            @NonNull final ConsensusTransactionImpl[] expectedTransactions,
            @NonNull final SimulatedNode simulatedNode,
            final boolean slowNode) {

        final EventImpl selfParent = events.get(newEvent.getHashedData().getSelfParentHash());
        final long selfParentGeneration = selfParent == null ? -1 : selfParent.getGeneration();
        final EventImpl otherParent = events.get(newEvent.getHashedData().getOtherParentHash());
        final long otherParentGeneration = otherParent == null ? -1 : otherParent.getGeneration();

        if (selfParent == null) {
            // The only legal time to have a null self parent is genesis.
            for (final EventImpl event : events.values()) {
                if (event.getHashedData()
                        .getHash()
                        .equals(newEvent.getHashedData().getHash())) {
                    // comparing to self
                    continue;
                }
                Assertions.assertNotEquals(
                        event.getCreatorId(), newEvent.getHashedData().getCreatorId());
            }
        }

        if (otherParent == null) {
            if (slowNode) {
                // During the slow node test, we intentionally don't distribute an event that ends up in the
                // events map. So it's possible for this map to contain two events at this point in time.
                assertTrue(events.size() == 1 || events.size() == 2);
            } else {
                // The only legal time to have no other-parent is at genesis before other events are received.
                assertEquals(1, events.size());
            }
            assertTrue(events.containsKey(newEvent.getHashedData().getHash()));
        }

        // Generation should be max of parents plus one
        final long expectedGeneration = Math.max(selfParentGeneration, otherParentGeneration) + 1;
        assertEquals(expectedGeneration, newEvent.getHashedData().getGeneration());

        // Timestamp must always increase by 1 nanosecond, and there must always be a unique timestamp
        // with nanosecond precision for transaction.
        if (selfParent != null) {
            final int minimumIncrement = Math.max(1, selfParent.getHashedData().getTransactions().length);
            final Instant minimumTimestamp =
                    selfParent.getHashedData().getTimeCreated().plus(Duration.ofNanos(minimumIncrement));
            assertTrue(isGreaterThanOrEqualTo(newEvent.getHashedData().getTimeCreated(), minimumTimestamp));
        }

        // Validate tipset constraints.
        final EventDescriptor descriptor = newEvent.getDescriptor();
        if (selfParent != null) {
            // Except for a genesis event, all other new events must have a positive advancement score.
            assertTrue(simulatedNode
                    .tipsetWeightCalculator
                    .addEventAndGetAdvancementWeight(descriptor)
                    .isNonZero());
        } else {
            simulatedNode.tipsetWeightCalculator.addEventAndGetAdvancementWeight(descriptor);
        }

        // We should see the expected transactions
        assertArrayEquals(expectedTransactions, newEvent.getHashedData().getTransactions());

        assertDoesNotThrow(() -> simulatedNode.tipsetEventCreator.toString());
    }

    /**
     * Link the event into its parents and distribute to all nodes in the network.
     */
    private void linkAndDistributeEvent(
            @NonNull final Map<NodeId, SimulatedNode> eventCreators,
            @NonNull final Map<Hash, EventImpl> events,
            @NonNull final GossipEvent event) {

        distributeEvent(eventCreators, linkEvent(eventCreators, events, event));
    }

    /**
     * Link an event to its parents.
     */
    @NonNull
    private EventImpl linkEvent(
            @NonNull final Map<NodeId, SimulatedNode> eventCreators,
            @NonNull final Map<Hash, EventImpl> events,
            @NonNull final GossipEvent event) {

        eventCreators
                .get(event.getHashedData().getCreatorId())
                .tipsetTracker
                .addEvent(event.getDescriptor(), TipsetUtils.getParentDescriptors(event));

        final EventImpl selfParent = events.get(event.getHashedData().getSelfParentHash());
        final EventImpl otherParent = events.get(event.getHashedData().getOtherParentHash());

        final EventImpl eventImpl =
                new EventImpl(event.getHashedData(), event.getUnhashedData(), selfParent, otherParent);
        events.put(event.getHashedData().getHash(), eventImpl);

        return eventImpl;
    }

    /**
     * Distribute an event to all nodes in the network.
     */
    private void distributeEvent(
            @NonNull final Map<NodeId, SimulatedNode> eventCreators, @NonNull final EventImpl eventImpl) {

        for (final SimulatedNode eventCreator : eventCreators.values()) {
            eventCreator.tipsetEventCreator.registerEvent(eventImpl);
            eventCreator.tipsetTracker.addEvent(
                    eventImpl.getBaseEvent().getDescriptor(), TipsetUtils.getParentDescriptors(eventImpl));
        }
    }

    /**
     * Generate a small number of random transactions.
     */
    @NonNull
    private ConsensusTransactionImpl[] generateRandomTransactions(@NonNull final Random random) {
        final int transactionCount = random.nextInt(0, 10);
        final ConsensusTransactionImpl[] transactions = new ConsensusTransactionImpl[transactionCount];

        for (int i = 0; i < transactionCount; i++) {
            final byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            final ConsensusTransactionImpl transaction = new SwirldTransaction(bytes);
            transactions[i] = transaction;
        }

        return transactions;
    }

    /**
     * Nodes take turns creating events in a round-robin fashion.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Round Robin Test")
    void roundRobinTest(final boolean advancingClock) {
        final Random random = getRandomPrintSeed();

        final int networkSize = 10;

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(networkSize).build();

        final FakeTime time = new FakeTime();

        final AtomicReference<ConsensusTransactionImpl[]> transactionSupplier = new AtomicReference<>();

        final Map<NodeId, SimulatedNode> nodes =
                buildSimulatedNodes(random, time, addressBook, transactionSupplier::get);

        final Map<Hash, EventImpl> events = new HashMap<>();

        for (int eventIndex = 0; eventIndex < 100; eventIndex++) {
            for (final Address address : addressBook) {
                if (advancingClock) {
                    time.tick(Duration.ofMillis(10));
                }

                transactionSupplier.set(generateRandomTransactions(random));

                final NodeId nodeId = address.getNodeId();
                final TipsetEventCreator eventCreator = nodes.get(nodeId).tipsetEventCreator;

                final GossipEvent event = eventCreator.maybeCreateEvent();

                // In this test, it should be impossible for a node to be unable to create an event.
                assertNotNull(event);

                linkAndDistributeEvent(nodes, events, event);

                if (advancingClock) {
                    assertEquals(event.getHashedData().getTimeCreated(), time.now());
                }

                validateNewEvent(events, event, transactionSupplier.get(), nodes.get(nodeId), false);
            }
        }
    }

    /**
     * Each cycle, randomize the order in which nodes are asked to create events.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Random Order Test")
    void randomOrderTest(final boolean advancingClock) {
        final Random random = getRandomPrintSeed();

        final int networkSize = 10;

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(networkSize).build();

        final FakeTime time = new FakeTime();

        final AtomicReference<ConsensusTransactionImpl[]> transactionSupplier = new AtomicReference<>();

        final Map<NodeId, SimulatedNode> nodes =
                buildSimulatedNodes(random, time, addressBook, transactionSupplier::get);

        final Map<Hash, EventImpl> events = new HashMap<>();

        for (int eventIndex = 0; eventIndex < 100; eventIndex++) {

            final List<Address> addresses = new ArrayList<>();
            addressBook.iterator().forEachRemaining(addresses::add);
            Collections.shuffle(addresses, random);

            boolean atLeastOneEventCreated = false;

            for (final Address address : addresses) {
                if (advancingClock) {
                    time.tick(Duration.ofMillis(10));
                }

                transactionSupplier.set(generateRandomTransactions(random));

                final NodeId nodeId = address.getNodeId();
                final TipsetEventCreator eventCreator = nodes.get(nodeId).tipsetEventCreator;

                final GossipEvent event = eventCreator.maybeCreateEvent();

                // It's possible a node may not be able to create an event. But we are guaranteed
                // to be able to create at least one event per cycle.
                if (event == null) {
                    continue;
                }
                atLeastOneEventCreated = true;

                linkAndDistributeEvent(nodes, events, event);

                if (advancingClock) {
                    assertEquals(event.getHashedData().getTimeCreated(), time.now());
                }
                validateNewEvent(events, event, transactionSupplier.get(), nodes.get(nodeId), false);
            }

            assertTrue(atLeastOneEventCreated);
        }
    }

    /**
     * Each node creates many events in a row without allowing others to take a turn. Eventually, a node should be
     * unable to create another event without first receiving an event from another node.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Create Many Events In A Row Test")
    void createManyEventsInARowTest(final boolean advancingClock) {
        final Random random = getRandomPrintSeed();

        final int networkSize = 10;

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(networkSize).build();

        final FakeTime time = new FakeTime();

        final AtomicReference<ConsensusTransactionImpl[]> transactionSupplier = new AtomicReference<>();

        final Map<NodeId, SimulatedNode> nodes =
                buildSimulatedNodes(random, time, addressBook, transactionSupplier::get);

        final Map<Hash, EventImpl> events = new HashMap<>();

        for (int eventIndex = 0; eventIndex < 100; eventIndex++) {
            for (final Address address : addressBook) {

                int count = 0;
                while (true) {
                    if (advancingClock) {
                        time.tick(Duration.ofMillis(10));
                    }

                    transactionSupplier.set(generateRandomTransactions(random));

                    final NodeId nodeId = address.getNodeId();
                    final TipsetEventCreator eventCreator = nodes.get(nodeId).tipsetEventCreator;

                    final GossipEvent event = eventCreator.maybeCreateEvent();

                    if (count == 0) {
                        // The first time we attempt to create an event we should be able to do so.
                        assertNotNull(event);
                    } else if (event == null) {
                        // we can't create any more events
                        break;
                    }

                    linkAndDistributeEvent(nodes, events, event);

                    if (advancingClock) {
                        assertEquals(event.getHashedData().getTimeCreated(), time.now());
                    }
                    validateNewEvent(events, event, transactionSupplier.get(), nodes.get(nodeId), false);

                    // At best, we can create a genesis event and one event per node in the network.
                    // We are unlikely to create this many, but we definitely shouldn't be able to go beyond this.
                    assertTrue(count < networkSize);
                    count++;
                }
            }
        }
    }

    /**
     * The tipset algorithm must still build on top of zero weight nodes, even though they don't help consensus to
     * advance.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Zero Weight Node Test")
    void zeroWeightNodeTest(final boolean advancingClock) {
        final Random random = getRandomPrintSeed();

        final int networkSize = 10;

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(networkSize).build();

        final NodeId zeroWeightNode = addressBook.getNodeId(0);

        for (final Address address : addressBook) {
            if (address.getNodeId().equals(zeroWeightNode)) {
                addressBook.add(address.copySetWeight(0));
            } else {
                addressBook.add(address.copySetWeight(1));
            }
        }

        final FakeTime time = new FakeTime();

        final AtomicReference<ConsensusTransactionImpl[]> transactionSupplier = new AtomicReference<>();

        final Map<NodeId, SimulatedNode> nodes =
                buildSimulatedNodes(random, time, addressBook, transactionSupplier::get);

        final Map<Hash, EventImpl> events = new HashMap<>();

        int zeroWeightNodeOtherParentCount = 0;

        for (int eventIndex = 0; eventIndex < 100; eventIndex++) {

            final List<Address> addresses = new ArrayList<>();
            addressBook.iterator().forEachRemaining(addresses::add);
            Collections.shuffle(addresses, random);

            boolean atLeastOneEventCreated = false;

            for (final Address address : addresses) {
                if (advancingClock) {
                    time.tick(Duration.ofMillis(10));
                }

                transactionSupplier.set(generateRandomTransactions(random));

                final NodeId nodeId = address.getNodeId();
                final TipsetEventCreator eventCreator = nodes.get(nodeId).tipsetEventCreator;

                final GossipEvent event = eventCreator.maybeCreateEvent();

                // It's possible a node may not be able to create an event. But we are guaranteed
                // to be able to create at least one event per cycle.
                if (event == null) {
                    continue;
                }
                atLeastOneEventCreated = true;

                final NodeId otherId = event.getUnhashedData().getOtherId();
                if (otherId != null && otherId.equals(zeroWeightNode)) {
                    zeroWeightNodeOtherParentCount++;
                }

                linkAndDistributeEvent(nodes, events, event);

                if (advancingClock) {
                    assertEquals(event.getHashedData().getTimeCreated(), time.now());
                }
                validateNewEvent(events, event, transactionSupplier.get(), nodes.get(nodeId), false);
            }

            assertTrue(atLeastOneEventCreated);
        }

        // This is just a heuristic. When running this, I typically see numbers around 100.
        // Essentially, we need to make sure that we are choosing the zero weight node's events
        // as other parents. Precisely how often is less important to this test, as long as we are
        // doing it at least some of the time.
        assertTrue(zeroWeightNodeOtherParentCount > 20);
    }

    /**
     * The tipset algorithm must still build on top of zero weight nodes, even though they don't help consensus to
     * advance. Further disadvantage the zero weight node by delaying the propagation of its events, so that others find
     * that they do not get transitive tipset score improvements by using it.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Zero Weight Slow Node Test")
    void zeroWeightSlowNodeTest(final boolean advancingClock) {
        final Random random = getRandomPrintSeed();

        final int networkSize = 10;

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(networkSize).build();

        final NodeId zeroWeightNode = addressBook.getNodeId(0);

        for (final Address address : addressBook) {
            if (address.getNodeId().equals(zeroWeightNode)) {
                addressBook.add(address.copySetWeight(0));
            } else {
                addressBook.add(address.copySetWeight(1));
            }
        }

        final FakeTime time = new FakeTime();

        final AtomicReference<ConsensusTransactionImpl[]> transactionSupplier = new AtomicReference<>();

        final Map<NodeId, SimulatedNode> nodes =
                buildSimulatedNodes(random, time, addressBook, transactionSupplier::get);

        final Map<Hash, EventImpl> events = new HashMap<>();
        final List<EventImpl> slowNodeEvents = new ArrayList<>();
        int zeroWeightNodeOtherParentCount = 0;

        for (int eventIndex = 0; eventIndex < 100; eventIndex++) {

            final List<Address> addresses = new ArrayList<>();
            addressBook.iterator().forEachRemaining(addresses::add);
            Collections.shuffle(addresses, random);

            boolean atLeastOneEventCreated = false;

            for (final Address address : addresses) {
                if (advancingClock) {
                    time.tick(Duration.ofMillis(10));
                }

                transactionSupplier.set(generateRandomTransactions(random));

                final NodeId nodeId = address.getNodeId();
                final TipsetEventCreator eventCreator = nodes.get(nodeId).tipsetEventCreator;

                final GossipEvent event = eventCreator.maybeCreateEvent();

                // It's possible a node may not be able to create an event. But we are guaranteed
                // to be able to create at least one event per cycle.
                if (event == null) {
                    continue;
                }
                atLeastOneEventCreated = true;

                final NodeId otherId = event.getUnhashedData().getOtherId();
                if (otherId != null && otherId.equals(zeroWeightNode)) {
                    zeroWeightNodeOtherParentCount++;
                }

                if (nodeId.equals(zeroWeightNode)) {
                    if (random.nextDouble() < 0.1 || slowNodeEvents.size() > 10) {
                        // Once in a while, take all the slow events and distribute them.
                        for (final EventImpl slowEvent : slowNodeEvents) {
                            distributeEvent(nodes, slowEvent);
                        }
                        slowNodeEvents.clear();
                        linkAndDistributeEvent(nodes, events, event);
                    } else {
                        // Most of the time, we don't immediately distribute the slow events.
                        final EventImpl eventImpl = linkEvent(nodes, events, event);
                        slowNodeEvents.add(eventImpl);
                    }
                } else {
                    // immediately distribute all events not created by the zero stake node
                    linkAndDistributeEvent(nodes, events, event);
                }

                if (advancingClock) {
                    assertEquals(event.getHashedData().getTimeCreated(), time.now());
                }
                validateNewEvent(events, event, transactionSupplier.get(), nodes.get(nodeId), true);
            }

            assertTrue(atLeastOneEventCreated);
        }

        // This is just a heuristic. When running this, I typically see numbers around 10.
        // Essentially, we need to make sure that we are choosing the zero weight node's events
        // as other parents. Precisely how often is less important to this test, as long as we are
        // doing it at least some of the time.
        assertTrue(zeroWeightNodeOtherParentCount > 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Size One Network Test")
    void sizeOneNetworkTest(final boolean advancingClock) {
        final Random random = getRandomPrintSeed();

        final int networkSize = 1;

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(networkSize).build();

        final FakeTime time = new FakeTime();

        final AtomicReference<ConsensusTransactionImpl[]> transactionSupplier = new AtomicReference<>();

        final Map<NodeId, SimulatedNode> nodes =
                buildSimulatedNodes(random, time, addressBook, transactionSupplier::get);

        final Map<Hash, EventImpl> events = new HashMap<>();

        final Address address = addressBook.getAddress(addressBook.getNodeId(0));

        for (int eventIndex = 0; eventIndex < 100; eventIndex++) {
            if (advancingClock) {
                time.tick(Duration.ofMillis(10));
            }

            transactionSupplier.set(generateRandomTransactions(random));

            final NodeId nodeId = address.getNodeId();
            final TipsetEventCreator eventCreator = nodes.get(nodeId).tipsetEventCreator;

            final GossipEvent event = eventCreator.maybeCreateEvent();

            // In this test, it should be impossible for a node to be unable to create an event.
            assertNotNull(event);

            linkAndDistributeEvent(nodes, events, event);

            if (advancingClock) {
                assertEquals(event.getHashedData().getTimeCreated(), time.now());
            }
        }
    }
}
