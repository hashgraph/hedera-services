/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.sync;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.platform.gossip.shadowgraph.SyncUtils;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookGenerator;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.EventSource;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SyncFilteringTest {

    /**
     * Generate a random list of events.
     *
     * @param platformContext the platform context
     * @param random          a random number generator
     * @param addressBook     the address book
     * @param time            provides the current time
     * @param timeStep        the time between events
     * @param count           the number of events to generate
     * @return the list of events
     */
    private static List<EventImpl> generateEvents(
            @NonNull final PlatformContext platformContext,
            @NonNull final Random random,
            @NonNull final AddressBook addressBook,
            @NonNull final FakeTime time,
            final Duration timeStep,
            final int count) {

        final List<EventImpl> events = new ArrayList<>(count);

        final List<EventSource<?>> sources = new ArrayList<>();
        for (int i = 0; i < addressBook.getSize(); i++) {
            sources.add(new StandardEventSource(false));
        }
        final StandardGraphGenerator generator =
                new StandardGraphGenerator(platformContext, random.nextLong(), sources, addressBook);

        for (int i = 0; i < count; i++) {
            final EventImpl event = generator.generateEvent();
            event.getBaseEvent().setTimeReceived(time.now());
            time.tick(timeStep);
            events.add(event);
        }

        return events;
    }

    /**
     * Find all ancestors of expected events, and add them to the list of expected events.
     *
     * @param expectedEvents the list of expected events
     */
    private static void findAncestorsOfExpectedEvents(@NonNull final List<EventImpl> expectedEvents) {

        final Set<Hash> expectedEventHashes = new HashSet<>();
        for (final EventImpl event : expectedEvents) {
            expectedEventHashes.add(event.getBaseHash());
        }

        for (int index = 0; index < expectedEvents.size(); index++) {

            final EventImpl event = expectedEvents.get(index);

            final EventImpl selfParent = event.getSelfParent();
            if (selfParent != null) {
                final Hash selfParentHash = selfParent.getBaseHash();
                if (!expectedEventHashes.contains(selfParentHash)) {
                    expectedEvents.add(selfParent);
                    expectedEventHashes.add(selfParentHash);
                }
            }
            final EventImpl otherParent = event.getOtherParent();
            if (otherParent != null) {
                final Hash otherParentHash = otherParent.getBaseHash();
                if (!expectedEventHashes.contains(otherParentHash)) {
                    expectedEvents.add(otherParent);
                    expectedEventHashes.add(otherParentHash);
                }
            }
        }
    }

    @Test
    void filterLikelyDuplicatesTest() {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(32).build();
        final NodeId selfId = addressBook.getNodeId(0);

        final Instant startingTime = Instant.ofEpochMilli(random.nextInt());
        final Duration timeStep = Duration.ofMillis(10);

        final FakeTime time = new FakeTime(startingTime, Duration.ZERO);
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final int eventCount = 1000;
        final List<EventImpl> events = generateEvents(platformContext, random, addressBook, time, timeStep, eventCount);

        events.sort(Comparator.comparingLong(EventImpl::getGeneration));

        final Duration nonAncestorSendThreshold = platformContext
                .getConfiguration()
                .getConfigData(SyncConfig.class)
                .nonAncestorFilterThreshold();

        final Instant endTime =
                startingTime.plus(timeStep.multipliedBy(eventCount)).plus(nonAncestorSendThreshold.multipliedBy(2));

        // Test filtering multiple times. Each iteration, move time forward. We should see more and more events
        // returned as they age.
        while (time.now().isBefore(endTime)) {
            final List<EventImpl> filteredEvents =
                    SyncUtils.filterLikelyDuplicates(selfId, nonAncestorSendThreshold, time.now(), events);

            // Gather a list of events we expect to see.
            final List<EventImpl> expectedEvents = new ArrayList<>();
            for (int index = events.size() - 1; index >= 0; index--) {
                final EventImpl event = events.get(index);
                if (event.getCreatorId().equals(selfId)) {
                    expectedEvents.add(event);
                } else {
                    final Duration eventAge =
                            Duration.between(event.getBaseEvent().getTimeReceived(), time.now());
                    if (CompareTo.isGreaterThan(eventAge, nonAncestorSendThreshold)) {
                        expectedEvents.add(event);
                    }
                }
            }

            // The ancestors of events that meet the above criteria are also expected to be seen.
            findAncestorsOfExpectedEvents(expectedEvents);

            // Gather a list of hashes that were allowed through by the filter.
            final Set<Hash> filteredHashes = new HashSet<>();
            for (final EventImpl event : filteredEvents) {
                filteredHashes.add(event.getBaseHash());
            }

            // Make sure we see exactly the events we are expecting.
            assertEquals(expectedEvents.size(), filteredEvents.size());
            for (final EventImpl expectedEvent : expectedEvents) {
                assertTrue(filteredHashes.contains(expectedEvent.getBaseHash()));
            }

            // Verify topological ordering.
            long maxGeneration = -1;
            for (final EventImpl event : filteredEvents) {
                final long generation = event.getBaseEvent().getGeneration();
                assertTrue(generation >= maxGeneration);
                maxGeneration = generation;
            }

            time.tick(Duration.ofMillis(100));
        }
    }
}
