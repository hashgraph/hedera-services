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

package com.swirlds.platform.uptime;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.platform.uptime.UptimeData.NO_ROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.consensus.GraphGenerations;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.event.ConsensusEvent;
import com.swirlds.common.event.PlatformEvent;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Uptime Tests")
class UptimeTests {

    private static List<PlatformEvent> generateEvents(
            @NonNull final Random random,
            @NonNull final FakeTime time,
            @NonNull final Duration roundDuration,
            @NonNull final AddressBook addressBook,
            final int count,
            @NonNull Set<NodeId> noEvents,
            @NonNull Set<NodeId> noJudges) {

        final List<PlatformEvent> events = new ArrayList<>(count);
        final Set<NodeId> firstEventCreated = new HashSet<>();
        while (events.size() < count) {

            final NodeId nodeId = addressBook.getNodeId(random.nextInt(addressBook.getSize()));
            if (noEvents.contains(nodeId)) {
                continue;
            }
            final PlatformEvent platformEvent = new TestingEventBuilder(random)
                    .setCreatorId(nodeId)
                    .setConsensusTimestamp(time.now())
                    .build();

            if (!noJudges.contains(nodeId) && firstEventCreated.add(nodeId)) {
                // event.setFamous(true);
                // event.setJudgeTrue();
                firstEventCreated.add(nodeId);
            }
            time.tick(roundDuration.dividedBy(count));

            events.add(platformEvent);
        }

        return events;
    }

    private static ConsensusRound mockRound(@NonNull final List<PlatformEvent> events, final long roundNum) {
        final ConsensusSnapshot snapshot = mock(ConsensusSnapshot.class);
        final ConsensusRound round = new ConsensusRound(
                mock(Roster.class),
                events,
                mock(PlatformEvent.class),
                mock(GraphGenerations.class),
                mock(EventWindow.class),
                snapshot,
                false,
                Instant.now());
        final Instant consensusTimestamp = events.get(events.size() - 1).getConsensusTimestamp();
        when(snapshot.consensusTimestamp()).thenReturn(consensusTimestamp);
        when(snapshot.round()).thenReturn(roundNum);
        when(round.getRoundNum()).thenReturn(roundNum);
        return round;
    }

    @Test
    @DisplayName("Round Scan Test")
    void roundScanTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final FakeTime time = new FakeTime();

        final AddressBook addressBook =
                RandomAddressBookBuilder.create(random).withSize(10).build();
        final NodeId selfId = addressBook.getNodeId(0);

        final UptimeTracker uptimeTracker =
                new UptimeTracker(platformContext, addressBook, mock(StatusActionSubmitter.class), selfId, time);
        final UptimeData uptimeData = uptimeTracker.uptimeData;

        // First, simulate a round starting at genesis
        final int eventCount = 100;
        final Set<NodeId> noFirstRoundEvents = Set.of(addressBook.getNodeId(0), addressBook.getNodeId(1));
        final Set<NodeId> noFirstRoundJudges = Set.of(addressBook.getNodeId(8), addressBook.getNodeId(9));
        final List<PlatformEvent> firstRoundEvents = generateEvents(
                random, time, Duration.ofSeconds(1), addressBook, eventCount, noFirstRoundEvents, noFirstRoundJudges);

        final ConsensusRound roundOne = mockRound(firstRoundEvents, 1);
        uptimeTracker.handleRound(roundOne, addressBook);

        for (final Address address : addressBook) {
            ConsensusEvent judge = null;
            ConsensusEvent lastEvent = null;

            for (final ConsensusEvent event : firstRoundEvents) {
                if (!Objects.equals(event.getCreatorId(), address.getNodeId())) {
                    continue;
                }
                if (judge == null && !noFirstRoundJudges.contains(address.getNodeId())) {
                    judge = event;
                }
                lastEvent = event;
            }

            // Temporarily disabled until we properly detect judges in a round
            //            if (judge != null) {
            //                assertEquals(1, genesisUptimeData.getLastJudgeRound(address.getNodeId()));
            //                assertEquals(judge.getConsensusTimestamp(),
            // genesisUptimeData.getLastJudgeTime(address.getNodeId()));
            //            } else {

            assertEquals(NO_ROUND, uptimeData.getLastJudgeRound(address.getNodeId()));
            assertNull(uptimeData.getLastJudgeTime(address.getNodeId()));
            //            }

            if (lastEvent != null) {
                assertEquals(1, uptimeData.getLastEventRound(address.getNodeId()));
                assertEquals(lastEvent.getConsensusTimestamp(), uptimeData.getLastEventTime(address.getNodeId()));
            } else {
                assertEquals(NO_ROUND, uptimeData.getLastEventRound(address.getNodeId()));
                assertNull(uptimeData.getLastEventTime(address.getNodeId()));
            }
        }

        final Set<NodeId> noSecondRoundEvents = Set.of(addressBook.getNodeId(0), addressBook.getNodeId(2));
        final Set<NodeId> noSecondRoundJudges = Set.of(addressBook.getNodeId(7), addressBook.getNodeId(9));
        final List<PlatformEvent> secondRoundEvents = generateEvents(
                random, time, Duration.ofSeconds(1), addressBook, eventCount, noSecondRoundEvents, noSecondRoundJudges);

        final ConsensusRound roundTwo = mockRound(secondRoundEvents, 2);
        uptimeTracker.handleRound(roundTwo, addressBook);

        for (final Address address : addressBook) {
            ConsensusEvent judge = null;
            ConsensusEvent lastEvent = null;

            for (final ConsensusEvent event : secondRoundEvents) {
                if (!Objects.equals(event.getCreatorId(), address.getNodeId())) {
                    continue;
                }
                if (judge == null && !noSecondRoundJudges.contains(address.getNodeId())) {
                    judge = event;
                }
                lastEvent = event;
            }

            // Temporarily disabled until we properly detect judges in a round
            assertEquals(NO_ROUND, uptimeData.getLastJudgeRound(address.getNodeId()));
            assertNull(uptimeData.getLastJudgeTime(address.getNodeId()));
            //            if (judge != null) {
            //                assertEquals(2, nextRoundUptimeData.getLastJudgeRound(address.getNodeId()));
            //                assertEquals(judge.getConsensusTimestamp(),
            // nextRoundUptimeData.getLastJudgeTime(address.getNodeId()));
            //            } else {
            //                assertEquals(
            //                        genesisUptimeData.getLastJudgeRound(address.getNodeId()),
            //                        nextRoundUptimeData.getLastJudgeRound(address.getNodeId()));
            //                assertEquals(
            //                        genesisUptimeData.getLastJudgeTime(address.getNodeId()),
            //                        nextRoundUptimeData.getLastJudgeTime(address.getNodeId()));
            //            }

            if (lastEvent != null) {
                assertEquals(2, uptimeData.getLastEventRound(address.getNodeId()));
                assertEquals(lastEvent.getConsensusTimestamp(), uptimeData.getLastEventTime(address.getNodeId()));
            }
        }
    }

    @Test
    @DisplayName("Round Scan Changing Address Book Test")
    void roundScanChangingAddressBookTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final FakeTime time = new FakeTime();

        final AddressBook addressBook =
                RandomAddressBookBuilder.create(random).withSize(10).build();
        final NodeId selfId = addressBook.getNodeId(0);

        final UptimeTracker uptimeTracker =
                new UptimeTracker(platformContext, addressBook, mock(StatusActionSubmitter.class), selfId, time);
        final UptimeData uptimeData = uptimeTracker.uptimeData;
        // First, simulate a round starting at genesis
        final int eventCount = 100;
        final Set<NodeId> noFirstRoundEvents = Set.of(addressBook.getNodeId(0), addressBook.getNodeId(1));
        final Set<NodeId> noFirstRoundJudges = Set.of(addressBook.getNodeId(8), addressBook.getNodeId(9));
        final List<PlatformEvent> firstRoundEvents = generateEvents(
                random, time, Duration.ofSeconds(1), addressBook, eventCount, noFirstRoundEvents, noFirstRoundJudges);

        for (final Address address : addressBook) {
            assertNull(uptimeData.getLastEventTime(address.getNodeId()));
            assertNull(uptimeData.getLastJudgeTime(address.getNodeId()));
            assertEquals(NO_ROUND, uptimeData.getLastEventRound(address.getNodeId()));
            assertEquals(NO_ROUND, uptimeData.getLastJudgeRound(address.getNodeId()));
        }

        final ConsensusRound roundOne = mockRound(firstRoundEvents, 1);
        uptimeTracker.handleRound(roundOne, addressBook);

        for (final Address address : addressBook) {
            ConsensusEvent judge = null;
            ConsensusEvent lastEvent = null;

            for (final ConsensusEvent event : firstRoundEvents) {
                if (!Objects.equals(event.getCreatorId(), address.getNodeId())) {
                    continue;
                }
                if (judge == null && !noFirstRoundJudges.contains(address.getNodeId())) {
                    judge = event;
                }
                lastEvent = event;
            }

            // Temporarily disabled until we properly detect judges in a round
            //            if (judge != null) {
            //                assertEquals(1, genesisUptimeData.getLastJudgeRound(address.getNodeId()));
            //                assertEquals(judge.getConsensusTimestamp(),
            // genesisUptimeData.getLastJudgeTime(address.getNodeId()));
            //            } else {
            assertEquals(NO_ROUND, uptimeData.getLastJudgeRound(address.getNodeId()));
            assertNull(uptimeData.getLastJudgeTime(address.getNodeId()));
            //            }

            if (lastEvent != null) {
                assertEquals(1, uptimeData.getLastEventRound(address.getNodeId()));
                assertEquals(lastEvent.getConsensusTimestamp(), uptimeData.getLastEventTime(address.getNodeId()));
            } else {
                assertEquals(NO_ROUND, uptimeData.getLastEventRound(address.getNodeId()));
                assertNull(uptimeData.getLastEventTime(address.getNodeId()));
            }
        }

        // Simulate a following round with a different address book
        final AddressBook newAddressBook = addressBook.copy();
        final NodeId nodeToRemove = addressBook.getNodeId(0);
        newAddressBook.remove(nodeToRemove);
        final Address newAddress =
                addressBook.getAddress(addressBook.getNodeId(0)).copySetNodeId(NodeId.of(12345));
        newAddressBook.add(newAddress);
        final Set<NodeId> noSecondRoundEvents = Set.of();
        final Set<NodeId> noSecondRoundJudges = Set.of();
        final List<PlatformEvent> secondRoundEvents = generateEvents(
                random,
                time,
                Duration.ofSeconds(1),
                newAddressBook,
                eventCount,
                noSecondRoundEvents,
                noSecondRoundJudges);

        final ConsensusRound roundTwo = mockRound(secondRoundEvents, 2);

        uptimeTracker.handleRound(roundTwo, newAddressBook);

        for (final Address address : newAddressBook) {
            ConsensusEvent judge = null;
            ConsensusEvent lastEvent = null;

            for (final ConsensusEvent event : secondRoundEvents) {
                if (!Objects.equals(event.getCreatorId(), address.getNodeId())) {
                    continue;
                }
                if (judge == null) {
                    judge = event;
                }
                lastEvent = event;
            }

            // Temporarily disabled until we properly detect judges in a round
            assertEquals(NO_ROUND, uptimeData.getLastJudgeRound(address.getNodeId()));
            assertNull(uptimeData.getLastJudgeTime(address.getNodeId()));
            //            if (judge != null) {
            //                assertEquals(2, nextRoundUptimeData.getLastJudgeRound(address.getNodeId()));
            //                assertEquals(judge.getConsensusTimestamp(),
            // nextRoundUptimeData.getLastJudgeTime(address.getNodeId()));
            //            }

            if (lastEvent != null) {
                assertEquals(2, uptimeData.getLastEventRound(address.getNodeId()));
                assertEquals(lastEvent.getConsensusTimestamp(), uptimeData.getLastEventTime(address.getNodeId()));
            }
        }

        assertNull(uptimeData.getLastJudgeTime(nodeToRemove));
        assertNull(uptimeData.getLastEventTime(nodeToRemove));
        assertEquals(NO_ROUND, uptimeData.getLastJudgeRound(nodeToRemove));
        assertEquals(NO_ROUND, uptimeData.getLastEventRound(nodeToRemove));
    }

    @Test
    @DisplayName("Degraded Test")
    void degradedTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final FakeTime time = new FakeTime();

        final AddressBook addressBook =
                RandomAddressBookBuilder.create(random).withSize(3).build();
        final NodeId selfId = addressBook.getNodeId(0);

        final UptimeTracker uptimeTracker =
                new UptimeTracker(platformContext, addressBook, mock(StatusActionSubmitter.class), selfId, time);
        final UptimeData uptimeData = uptimeTracker.uptimeData;

        // First, simulate a round starting at genesis
        final int eventCount = 100;
        final List<PlatformEvent> firstRoundEvents =
                generateEvents(random, time, Duration.ofSeconds(1), addressBook, eventCount, Set.of(), Set.of());

        for (final Address address : addressBook) {
            assertNull(uptimeData.getLastEventTime(address.getNodeId()));
            assertNull(uptimeData.getLastJudgeTime(address.getNodeId()));
            assertEquals(NO_ROUND, uptimeData.getLastEventRound(address.getNodeId()));
            assertEquals(NO_ROUND, uptimeData.getLastJudgeRound(address.getNodeId()));
        }

        final ConsensusRound roundOne = mockRound(firstRoundEvents, 1);
        uptimeTracker.handleRound(roundOne, addressBook);

        // Simulate a following round, but allow a long time to pass
        time.tick(Duration.ofSeconds(30));

        final Set<NodeId> noSecondRoundEvents = Set.of(addressBook.getNodeId(0));
        final List<PlatformEvent> secondRoundEvents = generateEvents(
                random, time, Duration.ofSeconds(1), addressBook, eventCount, noSecondRoundEvents, Set.of());

        final ConsensusRound roundTwo = mockRound(secondRoundEvents, 2);
        uptimeTracker.handleRound(roundTwo, addressBook);

        assertTrue(uptimeTracker.isSelfDegraded());

        // Once one of the node's events reaches consensus again, it should no longer be degraded

        final List<PlatformEvent> thirdRoundEvents =
                generateEvents(random, time, Duration.ofSeconds(1), addressBook, eventCount, Set.of(), Set.of());

        final ConsensusRound roundThree = mockRound(thirdRoundEvents, 3);
        uptimeTracker.handleRound(roundThree, addressBook);

        assertFalse(uptimeTracker.isSelfDegraded());
    }
}
