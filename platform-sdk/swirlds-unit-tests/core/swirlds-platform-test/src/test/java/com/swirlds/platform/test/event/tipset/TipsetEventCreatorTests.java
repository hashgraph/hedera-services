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

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.RandomUtils.randomSignature;
import static com.swirlds.common.utility.CompareTo.isGreaterThanOrEqualTo;
import static com.swirlds.platform.event.tipset.TipsetUtils.getParentDescriptors;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.platform.components.transaction.TransactionSupplier;
import com.swirlds.platform.event.EventDescriptor;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.tipset.TipsetBuilder;
import com.swirlds.platform.event.tipset.TipsetEventCreator;
import com.swirlds.platform.event.tipset.TipsetScoreCalculator;
import com.swirlds.platform.internal.EventImpl;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TipsetEventCreator Tests")
class TipsetEventCreatorTests {

    /**
     * @param nodeId                the node ID of the simulated node
     * @param tipsetEventCreator    the event creator for the simulated node
     * @param tipsetScoreCalculator used to sanity check event creation logic
     */
    private record SimulatedNode(
            @NonNull NodeId nodeId,
            @NonNull TipsetEventCreator tipsetEventCreator,
            @NonNull TipsetScoreCalculator tipsetScoreCalculator) {}

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

        return new TipsetEventCreator(
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
            @NonNull final TipsetBuilder tipsetBuilder,
            @NonNull final TransactionSupplier transactionSupplier) {

        final Map<NodeId, SimulatedNode> eventCreators = new HashMap<>();
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        for (final Address address : addressBook) {

            final TipsetEventCreator eventCreator =
                    buildEventCreator(random, time, addressBook, address.getNodeId(), transactionSupplier);

            final TipsetScoreCalculator tipsetScoreCalculator =
                    new TipsetScoreCalculator(platformContext, addressBook, address.getNodeId(), tipsetBuilder);

            eventCreators.put(
                    address.getNodeId(), new SimulatedNode(address.getNodeId(), eventCreator, tipsetScoreCalculator));
        }

        return eventCreators;
    }

    private void validateNewEvent(
            @NonNull final Map<Hash, EventImpl> events,
            @NonNull final GossipEvent newEvent,
            @NonNull final ConsensusTransactionImpl[] expectedTransactions,
            @NonNull final SimulatedNode simulatedNode,
            @NonNull final TipsetBuilder tipsetBuilder) {

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
                assertNotEquals(event.getCreatorId(), newEvent.getHashedData().getCreatorId());
            }
        }

        if (otherParent == null) {
            // The only legal time to have no other-parent is at genesis before other events are received.
            assertEquals(1, events.size());
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
        tipsetBuilder.addEvent(descriptor, getParentDescriptors(newEvent));
        if (selfParent != null) {
            // Except for a genesis event, all other new events must have a positive advancement score.
            assertTrue(simulatedNode.tipsetScoreCalculator.addEventAndGetAdvancementScore(descriptor) > 0);
        } else {
            simulatedNode.tipsetScoreCalculator.addEventAndGetAdvancementScore(descriptor);
        }

        // We should see the expected transactions
        assertArrayEquals(expectedTransactions, newEvent.getHashedData().getTransactions());
    }

    /**
     * Link the event into its parents and distribute to all nodes in the network.
     */
    private void linkAndDistributeEvent(
            @NonNull final Map<NodeId, SimulatedNode> eventCreators,
            @NonNull final Map<Hash, EventImpl> events,
            @NonNull final GossipEvent event) {

        final EventImpl selfParent = events.get(event.getHashedData().getSelfParentHash());
        final EventImpl otherParent = events.get(event.getHashedData().getOtherParentHash());

        final EventImpl eventImpl =
                new EventImpl(event.getHashedData(), event.getUnhashedData(), selfParent, otherParent);
        events.put(event.getHashedData().getHash(), eventImpl);

        for (final SimulatedNode eventCreator : eventCreators.values()) {
            eventCreator.tipsetEventCreator.registerEvent(eventImpl);
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
    @Test
    @DisplayName("Round Robin Test")
    void roundRobinTest() {
        final Random random = getRandomPrintSeed();

        final int networkSize = 10;

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(networkSize).build();

        final FakeTime time = new FakeTime();

        final AtomicReference<ConsensusTransactionImpl[]> transactionSupplier = new AtomicReference<>();

        // This tipset builder is used for validation. It's ok to use the same one for all nodes,
        // since it just needs to build a tipset for each event.
        final TipsetBuilder tipsetBuilder = new TipsetBuilder(addressBook);

        final Map<NodeId, SimulatedNode> nodes =
                buildSimulatedNodes(random, time, addressBook, tipsetBuilder, transactionSupplier::get);

        final Map<Hash, EventImpl> events = new HashMap<>();

        for (int eventIndex = 0; eventIndex < 100; eventIndex++) {
            for (final Address address : addressBook) {
                time.tick(Duration.ofMillis(10));

                transactionSupplier.set(generateRandomTransactions(random));

                final NodeId nodeId = address.getNodeId();
                final TipsetEventCreator eventCreator = nodes.get(nodeId).tipsetEventCreator;

                final GossipEvent event = eventCreator.maybeCreateEvent();

                // In this test, it should be impossible for a node to be unable to create an event.
                assertNotNull(event);

                linkAndDistributeEvent(nodes, events, event);

                assertEquals(event.getHashedData().getTimeCreated(), time.now());
                validateNewEvent(events, event, transactionSupplier.get(), nodes.get(nodeId), tipsetBuilder);
            }
        }
    }

    /**
     * Each cycle, randomize the order in which nodes are asked to create events.
     */
    @Test
    @DisplayName("Random Order Test")
    void randomOrderTest() {
        final Random random = getRandomPrintSeed();

        final int networkSize = 10;

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(networkSize).build();

        final FakeTime time = new FakeTime();

        final AtomicReference<ConsensusTransactionImpl[]> transactionSupplier = new AtomicReference<>();

        // This tipset builder is used for validation. It's ok to use the same one for all nodes,
        // since it just needs to build a tipset for each event.
        final TipsetBuilder tipsetBuilder = new TipsetBuilder(addressBook);

        final Map<NodeId, SimulatedNode> nodes =
                buildSimulatedNodes(random, time, addressBook, tipsetBuilder, transactionSupplier::get);

        final Map<Hash, EventImpl> events = new HashMap<>();

        for (int eventIndex = 0; eventIndex < 100; eventIndex++) {

            final List<Address> addresses = new ArrayList<>();
            addressBook.iterator().forEachRemaining(addresses::add);
            Collections.shuffle(addresses, random);

            boolean atLeastOneEventCreated = false;

            for (final Address address : addresses) {
                time.tick(Duration.ofMillis(10));

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

                assertEquals(event.getHashedData().getTimeCreated(), time.now());
                validateNewEvent(events, event, transactionSupplier.get(), nodes.get(nodeId), tipsetBuilder);
            }

            assertTrue(atLeastOneEventCreated);
        }
    }

    /**
     * Each node creates many events in a row without allowing others to take a turn. Eventually, a node should be
     * unable to create another event without first receiving an event from another node.
     */
    @Test
    @DisplayName("Create Many Events In A Row Test")
    void createManyEventsInARowTest() {
        final Random random = getRandomPrintSeed();

        final int networkSize = 10;

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(networkSize).build();

        final FakeTime time = new FakeTime();

        final AtomicReference<ConsensusTransactionImpl[]> transactionSupplier = new AtomicReference<>();

        // This tipset builder is used for validation. It's ok to use the same one for all nodes,
        // since it just needs to build a tipset for each event.
        final TipsetBuilder tipsetBuilder = new TipsetBuilder(addressBook);

        final Map<NodeId, SimulatedNode> nodes =
                buildSimulatedNodes(random, time, addressBook, tipsetBuilder, transactionSupplier::get);

        final Map<Hash, EventImpl> events = new HashMap<>();

        for (int eventIndex = 0; eventIndex < 100; eventIndex++) {
            for (final Address address : addressBook) {

                int count = 0;
                while (true) {
                    time.tick(Duration.ofMillis(10));

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

                    assertEquals(event.getHashedData().getTimeCreated(), time.now());
                    validateNewEvent(events, event, transactionSupplier.get(), nodes.get(nodeId), tipsetBuilder);

                    // At best, we can create a genesis event and one event per node in the network.
                    // We are unlikely to create this many, but we definitely shouldn't be able to go beyond this.
                    assertTrue(count < networkSize);
                    count++;
                }
            }
        }
    }
}
