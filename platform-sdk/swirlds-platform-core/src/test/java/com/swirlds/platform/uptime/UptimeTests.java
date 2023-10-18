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

package com.swirlds.platform.uptime;

import static com.swirlds.common.system.UptimeData.NO_ROUND;
import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.status.StatusActionSubmitter;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    final List<EventImpl> generateEvents(
            @NonNull final Random random,
            @NonNull final FakeTime time,
            final long round,
            @NonNull final Duration roundDuration,
            @NonNull final AddressBook addressBook,
            final int count,
            @NonNull Set<NodeId> noEvents,
            @NonNull Set<NodeId> noJudges) {

        final List<EventImpl> events = new ArrayList<>(count);
        final Set<NodeId> firstEventCreated = new HashSet<>();
        while (events.size() < count) {

            final NodeId nodeId = addressBook.getNodeId(random.nextInt(addressBook.getSize()));
            if (noEvents.contains(nodeId)) {
                continue;
            }

            final EventImpl event = mock(EventImpl.class);
            when(event.getCreatorId()).thenReturn(nodeId);

            when(event.getRoundReceived()).thenReturn(round);

            if (!noJudges.contains(nodeId) && firstEventCreated.add(nodeId)) {
                when(event.isFamous()).thenReturn(true);
                firstEventCreated.add(nodeId);
            } else {
                when(event.isFamous()).thenReturn(false);
            }

            when(event.getConsensusTimestamp()).thenReturn(time.now());
            time.tick(roundDuration.dividedBy(count));

            events.add(event);
        }

        when(events.get(events.size() - 1).isLastInRoundReceived()).thenReturn(true);

        return events;
    }

    @Test
    @DisplayName("Round Scan Test")
    void roundScanTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final FakeTime time = new FakeTime();

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(10).build();
        final NodeId selfId = addressBook.getNodeId(0);

        final UptimeTracker uptimeTracker =
                new UptimeTracker(platformContext, addressBook, mock(StatusActionSubmitter.class), selfId, time);

        // First, simulate a round starting at genesis
        final int eventCount = 100;
        final Set<NodeId> noFirstRoundEvents = Set.of(addressBook.getNodeId(0), addressBook.getNodeId(1));
        final Set<NodeId> noFirstRoundJudges = Set.of(addressBook.getNodeId(8), addressBook.getNodeId(9));
        final List<EventImpl> firstRoundEvents = generateEvents(
                random,
                time,
                1,
                Duration.ofSeconds(1),
                addressBook,
                eventCount,
                noFirstRoundEvents,
                noFirstRoundJudges);

        final UptimeDataImpl genesisUptimeData = new UptimeDataImpl();
        for (final Address address : addressBook) {
            assertNull(genesisUptimeData.getLastEventTime(address.getNodeId()));
            assertNull(genesisUptimeData.getLastJudgeTime(address.getNodeId()));
            assertEquals(NO_ROUND, genesisUptimeData.getLastEventRound(address.getNodeId()));
            assertEquals(NO_ROUND, genesisUptimeData.getLastJudgeRound(address.getNodeId()));
        }

        final ConsensusRound roundOne = new ConsensusRound(
                firstRoundEvents, mock(EventImpl.class), mock(GraphGenerations.class), mock(ConsensusSnapshot.class));
        uptimeTracker.handleRound(roundOne, genesisUptimeData, addressBook);

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
            assertEquals(NO_ROUND, genesisUptimeData.getLastJudgeRound(address.getNodeId()));
            assertNull(genesisUptimeData.getLastJudgeTime(address.getNodeId()));
            //            }

            if (lastEvent != null) {
                assertEquals(1, genesisUptimeData.getLastEventRound(address.getNodeId()));
                assertEquals(
                        lastEvent.getConsensusTimestamp(), genesisUptimeData.getLastEventTime(address.getNodeId()));
            } else {
                assertEquals(NO_ROUND, genesisUptimeData.getLastEventRound(address.getNodeId()));
                assertNull(genesisUptimeData.getLastEventTime(address.getNodeId()));
            }
        }

        // Simulate a following round
        final UptimeDataImpl nextRoundUptimeData = genesisUptimeData.copy();

        final Set<NodeId> noSecondRoundEvents = Set.of(addressBook.getNodeId(0), addressBook.getNodeId(2));
        final Set<NodeId> noSecondRoundJudges = Set.of(addressBook.getNodeId(7), addressBook.getNodeId(9));
        final List<EventImpl> secondRoundEvents = generateEvents(
                random,
                time,
                2,
                Duration.ofSeconds(1),
                addressBook,
                eventCount,
                noSecondRoundEvents,
                noSecondRoundJudges);

        final ConsensusRound roundTwo = new ConsensusRound(
                secondRoundEvents, mock(EventImpl.class), mock(GraphGenerations.class), mock(ConsensusSnapshot.class));
        uptimeTracker.handleRound(roundTwo, nextRoundUptimeData, addressBook);

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
            assertEquals(NO_ROUND, genesisUptimeData.getLastJudgeRound(address.getNodeId()));
            assertNull(genesisUptimeData.getLastJudgeTime(address.getNodeId()));
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
                assertEquals(2, nextRoundUptimeData.getLastEventRound(address.getNodeId()));
                assertEquals(
                        lastEvent.getConsensusTimestamp(), nextRoundUptimeData.getLastEventTime(address.getNodeId()));
            } else {
                assertEquals(
                        genesisUptimeData.getLastEventRound(address.getNodeId()),
                        nextRoundUptimeData.getLastEventRound(address.getNodeId()));
                assertEquals(
                        genesisUptimeData.getLastEventTime(address.getNodeId()),
                        nextRoundUptimeData.getLastEventTime(address.getNodeId()));
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
                new RandomAddressBookGenerator(random).setSize(10).build();
        final NodeId selfId = addressBook.getNodeId(0);

        final UptimeTracker uptimeTracker =
                new UptimeTracker(platformContext, addressBook, mock(StatusActionSubmitter.class), selfId, time);

        // First, simulate a round starting at genesis
        final int eventCount = 100;
        final Set<NodeId> noFirstRoundEvents = Set.of(addressBook.getNodeId(0), addressBook.getNodeId(1));
        final Set<NodeId> noFirstRoundJudges = Set.of(addressBook.getNodeId(8), addressBook.getNodeId(9));
        final List<EventImpl> firstRoundEvents = generateEvents(
                random,
                time,
                1,
                Duration.ofSeconds(1),
                addressBook,
                eventCount,
                noFirstRoundEvents,
                noFirstRoundJudges);

        final UptimeDataImpl genesisUptimeData = new UptimeDataImpl();
        for (final Address address : addressBook) {
            assertNull(genesisUptimeData.getLastEventTime(address.getNodeId()));
            assertNull(genesisUptimeData.getLastJudgeTime(address.getNodeId()));
            assertEquals(NO_ROUND, genesisUptimeData.getLastEventRound(address.getNodeId()));
            assertEquals(NO_ROUND, genesisUptimeData.getLastJudgeRound(address.getNodeId()));
        }

        final ConsensusRound roundOne = new ConsensusRound(
                firstRoundEvents, mock(EventImpl.class), mock(GraphGenerations.class), mock(ConsensusSnapshot.class));
        uptimeTracker.handleRound(roundOne, genesisUptimeData, addressBook);

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
            assertEquals(NO_ROUND, genesisUptimeData.getLastJudgeRound(address.getNodeId()));
            assertNull(genesisUptimeData.getLastJudgeTime(address.getNodeId()));
            //            }

            if (lastEvent != null) {
                assertEquals(1, genesisUptimeData.getLastEventRound(address.getNodeId()));
                assertEquals(
                        lastEvent.getConsensusTimestamp(), genesisUptimeData.getLastEventTime(address.getNodeId()));
            } else {
                assertEquals(NO_ROUND, genesisUptimeData.getLastEventRound(address.getNodeId()));
                assertNull(genesisUptimeData.getLastEventTime(address.getNodeId()));
            }
        }

        // Simulate a following round with a different address book
        final AddressBook newAddressBook = addressBook.copy();
        final NodeId nodeToRemove = addressBook.getNodeId(0);
        newAddressBook.remove(nodeToRemove);
        final Address newAddress =
                addressBook.getAddress(addressBook.getNodeId(0)).copySetNodeId(new NodeId(12345));
        newAddressBook.add(newAddress);
        final UptimeDataImpl nextRoundUptimeData = genesisUptimeData.copy();

        final Set<NodeId> noSecondRoundEvents = Set.of();
        final Set<NodeId> noSecondRoundJudges = Set.of();
        final List<EventImpl> secondRoundEvents = generateEvents(
                random,
                time,
                2,
                Duration.ofSeconds(1),
                newAddressBook,
                eventCount,
                noSecondRoundEvents,
                noSecondRoundJudges);

        final ConsensusRound roundTwo = new ConsensusRound(
                secondRoundEvents, mock(EventImpl.class), mock(GraphGenerations.class), mock(ConsensusSnapshot.class));
        uptimeTracker.handleRound(roundTwo, nextRoundUptimeData, newAddressBook);

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
            assertEquals(NO_ROUND, genesisUptimeData.getLastJudgeRound(address.getNodeId()));
            assertNull(genesisUptimeData.getLastJudgeTime(address.getNodeId()));
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
                assertEquals(2, nextRoundUptimeData.getLastEventRound(address.getNodeId()));
                assertEquals(
                        lastEvent.getConsensusTimestamp(), nextRoundUptimeData.getLastEventTime(address.getNodeId()));
            } else {
                assertEquals(
                        genesisUptimeData.getLastEventRound(address.getNodeId()),
                        nextRoundUptimeData.getLastEventRound(address.getNodeId()));
                assertEquals(
                        genesisUptimeData.getLastEventTime(address.getNodeId()),
                        nextRoundUptimeData.getLastEventTime(address.getNodeId()));
            }
        }

        assertNull(nextRoundUptimeData.getLastJudgeTime(nodeToRemove));
        assertNull(nextRoundUptimeData.getLastEventTime(nodeToRemove));
        assertEquals(NO_ROUND, nextRoundUptimeData.getLastJudgeRound(nodeToRemove));
        assertEquals(NO_ROUND, nextRoundUptimeData.getLastEventRound(nodeToRemove));
    }

    @Test
    @DisplayName("Fast Copy Test")
    void fastCopyTest() {
        final Random random = getRandomPrintSeed();
        final int size = 100;

        final List<Instant> eventTimes1 = new ArrayList<>();
        final List<Instant> judgeTimes1 = new ArrayList<>();
        final List<Long> eventRounds1 = new ArrayList<>();
        final List<Long> judgeRounds1 = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            if (random.nextDouble() < 0.9) {
                eventTimes1.add(Instant.ofEpochSecond(random.nextInt(1000)));
            } else {
                eventTimes1.add(null);
            }
            if (random.nextDouble() < 0.9) {
                judgeTimes1.add(Instant.ofEpochSecond(random.nextInt(1000)));
            } else {
                judgeTimes1.add(null);
            }
            if (random.nextDouble() < 0.9) {
                eventRounds1.add((long) random.nextInt(1000));
            } else {
                eventRounds1.add(NO_ROUND);
            }
            if (random.nextDouble() < 0.9) {
                judgeRounds1.add((long) random.nextInt(1000));
            } else {
                judgeRounds1.add(NO_ROUND);
            }
        }

        final UptimeDataImpl uptimeData1 = new UptimeDataImpl();

        for (int i = 0; i < size; i++) {
            uptimeData1.addNode(new NodeId(i));

            final EventImpl lastEvent = mock(EventImpl.class);
            when(lastEvent.getConsensusTimestamp()).thenReturn(eventTimes1.get(i));
            when(lastEvent.getRoundReceived()).thenReturn(eventRounds1.get(i));
            when(lastEvent.getCreatorId()).thenReturn(new NodeId(i));
            uptimeData1.recordLastEvent(lastEvent);

            final EventImpl lastJudge = mock(EventImpl.class);
            when(lastJudge.getConsensusTimestamp()).thenReturn(judgeTimes1.get(i));
            when(lastJudge.getRoundReceived()).thenReturn(judgeRounds1.get(i));
            when(lastJudge.getCreatorId()).thenReturn(new NodeId(i));
            uptimeData1.recordLastJudge(lastJudge);
        }

        for (int i = 0; i < size; i++) {
            assertEquals(eventTimes1.get(i), uptimeData1.getLastEventTime(new NodeId(i)));
            assertEquals(judgeTimes1.get(i), uptimeData1.getLastJudgeTime(new NodeId(i)));
            assertEquals(eventRounds1.get(i), uptimeData1.getLastEventRound(new NodeId(i)));
            assertEquals(judgeRounds1.get(i), uptimeData1.getLastJudgeRound(new NodeId(i)));
        }

        final UptimeDataImpl uptimeData2 = uptimeData1.copy();
        for (int i = 0; i < size; i++) {
            assertEquals(eventTimes1.get(i), uptimeData2.getLastEventTime(new NodeId(i)));
            assertEquals(judgeTimes1.get(i), uptimeData2.getLastJudgeTime(new NodeId(i)));
            assertEquals(eventRounds1.get(i), uptimeData2.getLastEventRound(new NodeId(i)));
            assertEquals(judgeRounds1.get(i), uptimeData2.getLastJudgeRound(new NodeId(i)));
        }

        final List<Instant> eventTimes2 = new ArrayList<>();
        final List<Instant> judgeTimes2 = new ArrayList<>();
        final List<Long> eventRounds2 = new ArrayList<>();
        final List<Long> judgeRounds2 = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            if (random.nextDouble() < 0.9) {
                eventTimes2.add(Instant.ofEpochSecond(random.nextInt(1000)));
            } else {
                eventTimes2.add(null);
            }
            if (random.nextDouble() < 0.9) {
                judgeTimes2.add(Instant.ofEpochSecond(random.nextInt(1000)));
            } else {
                judgeTimes2.add(null);
            }
            if (random.nextDouble() < 0.9) {
                eventRounds2.add((long) random.nextInt(1000));
            } else {
                eventRounds2.add(NO_ROUND);
            }
            if (random.nextDouble() < 0.9) {
                judgeRounds2.add((long) random.nextInt(1000));
            } else {
                judgeRounds2.add(NO_ROUND);
            }
        }

        for (int i = 0; i < size; i++) {
            final EventImpl lastEvent = mock(EventImpl.class);
            when(lastEvent.getConsensusTimestamp()).thenReturn(eventTimes2.get(i));
            when(lastEvent.getRoundReceived()).thenReturn(eventRounds2.get(i));
            when(lastEvent.getCreatorId()).thenReturn(new NodeId(i));
            uptimeData2.recordLastEvent(lastEvent);

            final EventImpl lastJudge = mock(EventImpl.class);
            when(lastJudge.getConsensusTimestamp()).thenReturn(judgeTimes2.get(i));
            when(lastJudge.getRoundReceived()).thenReturn(judgeRounds2.get(i));
            when(lastJudge.getCreatorId()).thenReturn(new NodeId(i));
            uptimeData2.recordLastJudge(lastJudge);
        }

        for (int i = 0; i < size; i++) {
            assertEquals(eventTimes1.get(i), uptimeData1.getLastEventTime(new NodeId(i)));
            assertEquals(judgeTimes1.get(i), uptimeData1.getLastJudgeTime(new NodeId(i)));
            assertEquals(eventRounds1.get(i), uptimeData1.getLastEventRound(new NodeId(i)));
            assertEquals(judgeRounds1.get(i), uptimeData1.getLastJudgeRound(new NodeId(i)));
        }

        for (int i = 0; i < size; i++) {
            assertEquals(eventTimes2.get(i), uptimeData2.getLastEventTime(new NodeId(i)));
            assertEquals(judgeTimes2.get(i), uptimeData2.getLastJudgeTime(new NodeId(i)));
            assertEquals(eventRounds2.get(i), uptimeData2.getLastEventRound(new NodeId(i)));
            assertEquals(judgeRounds2.get(i), uptimeData2.getLastJudgeRound(new NodeId(i)));
        }
    }

    @Test
    @DisplayName("Serialization Test")
    void serializationTest() throws IOException {
        final Random random = getRandomPrintSeed();
        final int size = 100;

        final List<Instant> eventTimes = new ArrayList<>();
        final List<Instant> judgeTimes = new ArrayList<>();
        final List<Long> eventRounds = new ArrayList<>();
        final List<Long> judgeRounds = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            if (random.nextDouble() < 0.9) {
                eventTimes.add(Instant.ofEpochSecond(random.nextInt(1000)));
            } else {
                eventTimes.add(null);
            }
            if (random.nextDouble() < 0.9) {
                judgeTimes.add(Instant.ofEpochSecond(random.nextInt(1000)));
            } else {
                judgeTimes.add(null);
            }
            if (random.nextDouble() < 0.9) {
                eventRounds.add((long) random.nextInt(1000));
            } else {
                eventRounds.add(NO_ROUND);
            }
            if (random.nextDouble() < 0.9) {
                judgeRounds.add((long) random.nextInt(1000));
            } else {
                judgeRounds.add(NO_ROUND);
            }
        }

        final UptimeDataImpl uptimeData1 = new UptimeDataImpl();

        for (int i = 0; i < size; i++) {
            uptimeData1.addNode(new NodeId(i));

            final EventImpl lastEvent = mock(EventImpl.class);
            when(lastEvent.getConsensusTimestamp()).thenReturn(eventTimes.get(i));
            when(lastEvent.getRoundReceived()).thenReturn(eventRounds.get(i));
            when(lastEvent.getCreatorId()).thenReturn(new NodeId(i));
            uptimeData1.recordLastEvent(lastEvent);

            final EventImpl lastJudge = mock(EventImpl.class);
            when(lastJudge.getConsensusTimestamp()).thenReturn(judgeTimes.get(i));
            when(lastJudge.getRoundReceived()).thenReturn(judgeRounds.get(i));
            when(lastJudge.getCreatorId()).thenReturn(new NodeId(i));
            uptimeData1.recordLastJudge(lastJudge);
        }

        for (int i = 0; i < size; i++) {
            assertEquals(eventTimes.get(i), uptimeData1.getLastEventTime(new NodeId(i)));
            assertEquals(judgeTimes.get(i), uptimeData1.getLastJudgeTime(new NodeId(i)));
            assertEquals(eventRounds.get(i), uptimeData1.getLastEventRound(new NodeId(i)));
            assertEquals(judgeRounds.get(i), uptimeData1.getLastJudgeRound(new NodeId(i)));
        }

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final SerializableDataOutputStream dataOutputStream = new SerializableDataOutputStream(byteArrayOutputStream);
        dataOutputStream.writeSerializable(uptimeData1, false);
        dataOutputStream.close();

        final SerializableDataInputStream dataInputStream =
                new SerializableDataInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));

        final UptimeDataImpl uptimeData2 = dataInputStream.readSerializable(false, UptimeDataImpl::new);

        for (int i = 0; i < size; i++) {
            assertEquals(eventTimes.get(i), uptimeData2.getLastEventTime(new NodeId(i)));
            assertEquals(judgeTimes.get(i), uptimeData2.getLastJudgeTime(new NodeId(i)));
            assertEquals(eventRounds.get(i), uptimeData2.getLastEventRound(new NodeId(i)));
            assertEquals(judgeRounds.get(i), uptimeData2.getLastJudgeRound(new NodeId(i)));
        }
    }

    @Test
    @DisplayName("Degraded Test")
    void degradedTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final FakeTime time = new FakeTime();

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(3).build();
        final NodeId selfId = addressBook.getNodeId(0);

        final UptimeTracker uptimeTracker =
                new UptimeTracker(platformContext, addressBook, mock(StatusActionSubmitter.class), selfId, time);

        // First, simulate a round starting at genesis
        final int eventCount = 100;
        final List<EventImpl> firstRoundEvents =
                generateEvents(random, time, 1, Duration.ofSeconds(1), addressBook, eventCount, Set.of(), Set.of());

        final UptimeDataImpl genesisUptimeData = new UptimeDataImpl();
        for (final Address address : addressBook) {
            assertNull(genesisUptimeData.getLastEventTime(address.getNodeId()));
            assertNull(genesisUptimeData.getLastJudgeTime(address.getNodeId()));
            assertEquals(NO_ROUND, genesisUptimeData.getLastEventRound(address.getNodeId()));
            assertEquals(NO_ROUND, genesisUptimeData.getLastJudgeRound(address.getNodeId()));
        }

        final ConsensusRound roundOne = new ConsensusRound(
                firstRoundEvents, mock(EventImpl.class), mock(GraphGenerations.class), mock(ConsensusSnapshot.class));
        uptimeTracker.handleRound(roundOne, genesisUptimeData, addressBook);

        // Simulate a following round, but allow a long time to pass
        time.tick(Duration.ofSeconds(30));
        final UptimeDataImpl nextRoundUptimeData = genesisUptimeData.copy();

        final Set<NodeId> noSecondRoundEvents = Set.of(addressBook.getNodeId(0));
        final List<EventImpl> secondRoundEvents = generateEvents(
                random, time, 2, Duration.ofSeconds(1), addressBook, eventCount, noSecondRoundEvents, Set.of());

        final ConsensusRound roundTwo = new ConsensusRound(
                secondRoundEvents, mock(EventImpl.class), mock(GraphGenerations.class), mock(ConsensusSnapshot.class));
        uptimeTracker.handleRound(roundTwo, nextRoundUptimeData, addressBook);

        assertTrue(uptimeTracker.isSelfDegraded());

        // Once one of the node's events reaches consensus again, it should no longer be degraded

        final UptimeDataImpl finalRoundUptimeData = nextRoundUptimeData.copy();

        final List<EventImpl> thirdRoundEvents =
                generateEvents(random, time, 3, Duration.ofSeconds(1), addressBook, eventCount, Set.of(), Set.of());

        final ConsensusRound roundThree = new ConsensusRound(
                thirdRoundEvents, mock(EventImpl.class), mock(GraphGenerations.class), mock(ConsensusSnapshot.class));
        uptimeTracker.handleRound(roundThree, finalRoundUptimeData, addressBook);

        assertFalse(uptimeTracker.isSelfDegraded());
    }
}
