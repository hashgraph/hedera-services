/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.runninghash;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.test.fixtures.RandomUtils.randomInstant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.RunningEventHashOverride;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RunningEventHasherTests {

    /**
     * Build a consensus round and fill it with the fields needed by the hasher.
     *
     * @param roundNumber the round number
     * @param events      the events in the round
     * @return the consensus round
     */
    @NonNull
    private static ConsensusRound buildRound(final long roundNumber, @NonNull final List<EventImpl> events) {
        final ConsensusSnapshot snapshot = mock(ConsensusSnapshot.class);
        when(snapshot.round()).thenReturn(roundNumber);

        return new ConsensusRound(
                mock(AddressBook.class),
                events,
                mock(EventImpl.class),
                mock(Generations.class),
                mock(EventWindow.class),
                snapshot);
    }

    @Test
    void sequenceOfRegularRoundsTest() throws InterruptedException {
        final Random random = getRandomPrintSeed();
        final FakeTime time = new FakeTime(randomInstant(random), Duration.ZERO);
        final int eventCount = random.nextInt(1, 10);

        final List<ConsensusRound> rounds = new ArrayList<>();

        final long firstRound = random.nextLong(1, 100);
        for (int roundOffset = 0; roundOffset < 10; roundOffset++) {
            final List<EventImpl> events = new ArrayList<>();
            for (int i = 0; i < eventCount; i++) {
                final GossipEvent event = new TestingEventBuilder(random).build();
                final EventImpl eventImpl = new EventImpl(event);
                eventImpl.setConsensusTimestamp(time.now());
                events.add(eventImpl);

                time.tick(Duration.ofMillis(random.nextInt(1, 100)));
            }
            final ConsensusRound round = buildRound(firstRound + roundOffset, events);
            rounds.add(round);
        }

        final Hash initialHash = randomHash(random);

        final RunningEventHasher hasherA = new DefaultRunningEventHasher();
        final RunningEventHasher hasherB = new DefaultRunningEventHasher();

        hasherA.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));
        hasherB.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));

        for (final ConsensusRound round : rounds) {
            hasherA.computeRunningEventHash(round);
            final Hash hashA = round.getRunningEventHash();

            // Recreate the round to reset the running hash.
            final ConsensusRound roundB = buildRound(round.getRoundNum(), round.getConsensusEvents());
            hasherB.computeRunningEventHash(roundB);
            final Hash hashB = roundB.getRunningEventHash();

            assertEquals(hashA, hashB);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void differentInitialHashTest(final boolean emptyRound) throws InterruptedException {
        final Random random = getRandomPrintSeed();
        final FakeTime time = new FakeTime(randomInstant(random), Duration.ZERO);
        final int eventCount = emptyRound ? 0 : random.nextInt(1, 10);

        final long roundNumber = random.nextLong(1, 100);

        final List<EventImpl> events = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            final GossipEvent event = new TestingEventBuilder(random).build();
            final EventImpl eventImpl = new EventImpl(event);
            eventImpl.setConsensusTimestamp(time.now());
            events.add(eventImpl);

            time.tick(Duration.ofMillis(random.nextInt(1, 100)));
        }
        final ConsensusRound round = buildRound(roundNumber, events);

        final Hash initialHashA = randomHash(random);
        final Hash initialHashB = randomHash(random);

        final RunningEventHasher hasherA = new DefaultRunningEventHasher();
        final RunningEventHasher hasherB = new DefaultRunningEventHasher();

        hasherA.overrideRunningEventHash(new RunningEventHashOverride(null, initialHashA, false));
        hasherB.overrideRunningEventHash(new RunningEventHashOverride(null, initialHashB, false));

        hasherA.computeRunningEventHash(round);
        final Hash hashA = round.getRunningEventHash();

        // Recreate the round to reset the running hash.
        final ConsensusRound roundB = buildRound(round.getRoundNum(), round.getConsensusEvents());
        hasherB.computeRunningEventHash(roundB);
        final Hash hashB = roundB.getRunningEventHash();

        assertNotEquals(hashA, hashB);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void differentRoundNumberTest(final boolean emptyRound) throws InterruptedException {
        final Random random = getRandomPrintSeed();
        final FakeTime time = new FakeTime(randomInstant(random), Duration.ZERO);
        final int eventCount = emptyRound ? 0 : random.nextInt(1, 10);

        final long roundNumberA = random.nextLong(2, 100);
        final long roundNumberB = roundNumberA + random.nextInt(1, 100);

        final List<EventImpl> events = new ArrayList<>();
        for (int i = 0; i < random.nextInt(1, 10); i++) {
            final GossipEvent event = new TestingEventBuilder(random).build();
            final EventImpl eventImpl = new EventImpl(event);
            eventImpl.setConsensusTimestamp(time.now());
            events.add(eventImpl);

            time.tick(Duration.ofMillis(random.nextInt(1, 100)));
        }

        final ConsensusRound roundA = buildRound(roundNumberA, events);
        final ConsensusRound roundB = buildRound(roundNumberB, events);

        final Hash initialHash = randomHash(random);

        final RunningEventHasher hasherA = new DefaultRunningEventHasher();
        final RunningEventHasher hasherB = new DefaultRunningEventHasher();

        hasherA.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));
        hasherB.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));

        hasherA.computeRunningEventHash(roundA);
        final Hash hashA = roundA.getRunningEventHash();

        hasherB.computeRunningEventHash(roundB);
        final Hash hashB = roundB.getRunningEventHash();

        assertNotEquals(hashA, hashB);
    }

    @Test
    void differentEventOrderTest() throws InterruptedException {
        final Random random = getRandomPrintSeed();
        final FakeTime time = new FakeTime(randomInstant(random), Duration.ZERO);
        final int eventCount = random.nextInt(2, 10);

        final long roundNumber = random.nextLong(1, 100);

        final List<EventImpl> events = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            final GossipEvent event = new TestingEventBuilder(random).build();
            final EventImpl eventImpl = new EventImpl(event);
            eventImpl.setConsensusTimestamp(time.now());
            events.add(eventImpl);

            time.tick(Duration.ofMillis(random.nextInt(1, 100)));
        }
        // Swap the order of two events. Event list is guaranteed to have at least 2 events.
        final List<EventImpl> eventsB = new ArrayList<>(events);
        eventsB.set(0, events.get(1));
        eventsB.set(1, events.get(0));

        final ConsensusRound roundA = buildRound(roundNumber, events);
        final ConsensusRound roundB = buildRound(roundNumber, eventsB);

        final Hash initialHash = randomHash(random);

        final RunningEventHasher hasherA = new DefaultRunningEventHasher();
        final RunningEventHasher hasherB = new DefaultRunningEventHasher();

        hasherA.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));
        hasherB.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));

        hasherA.computeRunningEventHash(roundA);
        final Hash hashA = roundA.getRunningEventHash();

        hasherB.computeRunningEventHash(roundB);
        final Hash hashB = roundB.getRunningEventHash();

        assertNotEquals(hashA, hashB);
    }

    @Test
    void differentEventSeconds() throws InterruptedException {
        final Random random = getRandomPrintSeed();
        final FakeTime time = new FakeTime(randomInstant(random), Duration.ZERO);
        final int eventCount = random.nextInt(1, 10);

        final long roundNumber = random.nextLong(1, 100);

        final List<EventImpl> events = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            final GossipEvent event = new TestingEventBuilder(random).build();
            final EventImpl eventImpl = new EventImpl(event);
            eventImpl.setConsensusTimestamp(time.now());
            events.add(eventImpl);

            time.tick(Duration.ofMillis(random.nextInt(1, 100)));
        }

        final List<EventImpl> eventsB = new ArrayList<>(events);
        final EventImpl originalEvent = events.get(0);
        final EventImpl modifiedEvent =
                new EventImpl(originalEvent.getBaseEvent());
        final Instant modifiedTimestamp =
                originalEvent.getConsensusData().getConsensusTimestamp().plusSeconds(1);
        modifiedEvent.setConsensusTimestamp(modifiedTimestamp);
        eventsB.set(0, modifiedEvent);

        final ConsensusRound roundA = buildRound(roundNumber, events);
        final ConsensusRound roundB = buildRound(roundNumber, eventsB);

        final Hash initialHash = randomHash(random);

        final RunningEventHasher hasherA = new DefaultRunningEventHasher();
        final RunningEventHasher hasherB = new DefaultRunningEventHasher();

        hasherA.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));
        hasherB.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));

        hasherA.computeRunningEventHash(roundA);
        final Hash hashA = roundA.getRunningEventHash();

        hasherB.computeRunningEventHash(roundB);
        final Hash hashB = roundB.getRunningEventHash();

        assertNotEquals(hashA, hashB);
    }

    @Test
    void differentEventNanoseconds() throws InterruptedException {
        final Random random = getRandomPrintSeed();
        final FakeTime time = new FakeTime(randomInstant(random), Duration.ZERO);
        final int eventCount = random.nextInt(1, 10);

        final long roundNumber = random.nextLong(1, 100);

        final List<EventImpl> events = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            final GossipEvent event = new TestingEventBuilder(random).build();
            final EventImpl eventImpl = new EventImpl(event);
            eventImpl.setConsensusTimestamp(time.now());
            events.add(eventImpl);

            time.tick(Duration.ofMillis(random.nextInt(1, 100)));
        }

        final List<EventImpl> eventsB = new ArrayList<>(events);
        final EventImpl originalEvent = events.get(0);
        final EventImpl modifiedEvent =
                new EventImpl(originalEvent.getBaseEvent());
        final Instant modifiedTimestamp =
                originalEvent.getConsensusData().getConsensusTimestamp().plusNanos(1);
        modifiedEvent.setConsensusTimestamp(modifiedTimestamp);
        eventsB.set(0, modifiedEvent);

        final ConsensusRound roundA = buildRound(roundNumber, events);
        final ConsensusRound roundB = buildRound(roundNumber, eventsB);

        final Hash initialHash = randomHash(random);

        final RunningEventHasher hasherA = new DefaultRunningEventHasher();
        final RunningEventHasher hasherB = new DefaultRunningEventHasher();

        hasherA.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));
        hasherB.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));

        hasherA.computeRunningEventHash(roundA);
        final Hash hashA = roundA.getRunningEventHash();

        hasherB.computeRunningEventHash(roundB);
        final Hash hashB = roundB.getRunningEventHash();

        assertNotEquals(hashA, hashB);
    }

    @Test
    void differentEventHashTest() throws InterruptedException {
        final Random random = getRandomPrintSeed();
        final FakeTime time = new FakeTime(randomInstant(random), Duration.ZERO);
        final int eventCount = random.nextInt(1, 10);

        final long roundNumber = random.nextLong(1, 100);

        final List<EventImpl> events = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            final GossipEvent event = new TestingEventBuilder(random).build();
            final EventImpl eventImpl = new EventImpl(event);
            eventImpl.setConsensusTimestamp(time.now());
            events.add(eventImpl);

            time.tick(Duration.ofMillis(random.nextInt(1, 100)));
        }

        final List<EventImpl> eventsB = new ArrayList<>(events);
        final EventImpl originalEvent = events.get(0);
        final GossipEvent newGossipEvent = new TestingEventBuilder(random).build();
        final EventImpl modifiedEvent = new EventImpl(newGossipEvent);
        modifiedEvent.setConsensusTimestamp(originalEvent.getConsensusData().getConsensusTimestamp());
        eventsB.set(0, modifiedEvent);

        final ConsensusRound roundA = buildRound(roundNumber, events);
        final ConsensusRound roundB = buildRound(roundNumber, eventsB);

        final Hash initialHash = randomHash(random);

        final RunningEventHasher hasherA = new DefaultRunningEventHasher();
        final RunningEventHasher hasherB = new DefaultRunningEventHasher();

        hasherA.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));
        hasherB.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));

        hasherA.computeRunningEventHash(roundA);
        final Hash hashA = roundA.getRunningEventHash();

        hasherB.computeRunningEventHash(roundB);
        final Hash hashB = roundB.getRunningEventHash();

        assertNotEquals(hashA, hashB);
    }

    @Test
    void missingEventTest() throws InterruptedException {
        final Random random = getRandomPrintSeed();
        final FakeTime time = new FakeTime(randomInstant(random), Duration.ZERO);
        final int eventCount = random.nextInt(1, 10);

        final long roundNumber = random.nextLong(1, 100);

        final List<EventImpl> events = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            final GossipEvent event = new TestingEventBuilder(random).build();
            final EventImpl eventImpl = new EventImpl(event);
            eventImpl.setConsensusTimestamp(time.now());
            events.add(eventImpl);

            time.tick(Duration.ofMillis(random.nextInt(1, 100)));
        }

        final List<EventImpl> eventsB = new ArrayList<>(events);
        eventsB.removeLast();

        final ConsensusRound roundA = buildRound(roundNumber, events);
        final ConsensusRound roundB = buildRound(roundNumber, eventsB);

        final Hash initialHash = randomHash(random);

        final RunningEventHasher hasherA = new DefaultRunningEventHasher();
        final RunningEventHasher hasherB = new DefaultRunningEventHasher();

        hasherA.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));
        hasherB.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));

        hasherA.computeRunningEventHash(roundA);
        final Hash hashA = roundA.getRunningEventHash();

        hasherB.computeRunningEventHash(roundB);
        final Hash hashB = roundB.getRunningEventHash();

        assertNotEquals(hashA, hashB);
    }

    @Test
    void extraEventTest() throws InterruptedException {
        final Random random = getRandomPrintSeed();
        final FakeTime time = new FakeTime(randomInstant(random), Duration.ZERO);
        final int eventCount = random.nextInt(1, 10);

        final long roundNumber = random.nextLong(1, 100);

        final List<EventImpl> events = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            final GossipEvent event = new TestingEventBuilder(random).build();
            final EventImpl eventImpl = new EventImpl(event);
            eventImpl.setConsensusTimestamp(time.now());
            events.add(eventImpl);

            time.tick(Duration.ofMillis(random.nextInt(1, 100)));
        }

        final List<EventImpl> eventsB = new ArrayList<>(events);
        final GossipEvent newGossipEvent = new TestingEventBuilder(random).build();
        final EventImpl extraEvent = new EventImpl(newGossipEvent);
        extraEvent.setConsensusTimestamp(time.now());
        eventsB.add(extraEvent);

        final ConsensusRound roundA = buildRound(roundNumber, events);
        final ConsensusRound roundB = buildRound(roundNumber, eventsB);

        final Hash initialHash = randomHash(random);

        final RunningEventHasher hasherA = new DefaultRunningEventHasher();
        final RunningEventHasher hasherB = new DefaultRunningEventHasher();

        hasherA.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));
        hasherB.overrideRunningEventHash(new RunningEventHashOverride(null, initialHash, false));

        hasherA.computeRunningEventHash(roundA);
        final Hash hashA = roundA.getRunningEventHash();

        hasherB.computeRunningEventHash(roundB);
        final Hash hashB = roundB.getRunningEventHash();

        assertNotEquals(hashA, hashB);
    }
}
