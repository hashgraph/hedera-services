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

package com.swirlds.platform.event;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomInstant;
import static com.swirlds.platform.event.AncientMode.BIRTH_ROUND_THRESHOLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.eventhandling.EventConfig_;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class FutureEventBufferTests {

    /**
     * Generate an event with random data. Most fields don't need to be non-null, just enough to ensure events are
     * unique.
     *
     * @param random     the random number generator
     * @param birthRound the birth round of the event
     * @return the event
     */
    @NonNull
    final GossipEvent generateEvent(@NonNull final Random random, final long birthRound) {
        final BaseEventHashedData baseEventHashedData = new BaseEventHashedData(
                new BasicSoftwareVersion(1),
                new NodeId(random.nextInt(100)),
                null,
                List.of(),
                birthRound,
                randomInstant(random),
                null);
        final BaseEventUnhashedData baseEventUnhashedData = new BaseEventUnhashedData();

        return new GossipEvent(baseEventHashedData, baseEventUnhashedData);
    }

    /**
     * This test verifies the following:
     * <ul>
     *     <li>Events that are from the future are buffered.</li>
     *     <li>Buffered events are returned in topological order</li>
     *     <li>ancient events are discarded</li>
     *     <li>non-ancient non-future events are returned immediately</li>
     * </ul>
     */
    @Test
    void futureEventsBufferedTest() {
        final Random random = getRandomPrintSeed();

        final Configuration configuration = new TestConfigBuilder()
                .withValue(EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD, true)
                .getOrCreateConfig();

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();

        final FutureEventBuffer futureEventBuffer = new DefaultFutureEventBuffer(platformContext);

        final long nonAncientBirthRound = 100;
        final long pendingConsensusRound = nonAncientBirthRound * 2;
        final long maxFutureRound = nonAncientBirthRound * 3;

        final NonAncientEventWindow eventWindow =
                new NonAncientEventWindow(pendingConsensusRound - 1, nonAncientBirthRound, 1, BIRTH_ROUND_THRESHOLD);

        futureEventBuffer.updateEventWindow(eventWindow);

        final int count = 1000;
        final List<GossipEvent> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final GossipEvent event = generateEvent(random, random.nextLong(1, maxFutureRound));
            events.add(event);
        }
        // Put the events in topological order
        events.sort(Comparator.comparingLong(a -> a.getHashedData().getBirthRound()));

        final List<GossipEvent> futureEvents = new ArrayList<>();
        for (final GossipEvent event : events) {
            final List<GossipEvent> returnedEvents = futureEventBuffer.addEvent(event);
            assertTrue(returnedEvents == null || returnedEvents.size() == 1);
            final GossipEvent returnedEvent = returnedEvents == null ? null : returnedEvents.get(0);
            if (eventWindow.isAncient(event)) {
                // Ancient events should be discarded.
                assertNull(returnedEvent);
            } else if (event.getHashedData().getBirthRound() <= eventWindow.getPendingConsensusRound()) {
                // Non-future events should be returned immediately.
                assertSame(event, returnedEvent);
            } else {
                // Future events should be buffered.
                futureEvents.add(event);
                assertNull(returnedEvent);
            }
        }

        // Gradually shift the window forward and collect buffered events as they stop being future events.
        final List<GossipEvent> unBufferedEvents = new ArrayList<>();
        for (long newPendingConsensusRound = pendingConsensusRound + 1;
                newPendingConsensusRound <= maxFutureRound;
                newPendingConsensusRound++) {

            final NonAncientEventWindow newEventWindow = new NonAncientEventWindow(
                    newPendingConsensusRound - 1, nonAncientBirthRound, 1, BIRTH_ROUND_THRESHOLD);

            final List<GossipEvent> bufferedEvents = futureEventBuffer.updateEventWindow(newEventWindow);

            for (final GossipEvent event : bufferedEvents) {
                assertEquals(newPendingConsensusRound, event.getHashedData().getBirthRound());
                unBufferedEvents.add(event);
            }
        }

        // When we are finished, we should have all of the future events in the same order that they were inserted.
        assertEquals(futureEvents, unBufferedEvents);

        // Make a big window shift. There should be no events that come out of the buffer.
        final NonAncientEventWindow newEventWindow =
                new NonAncientEventWindow(pendingConsensusRound * 1000, nonAncientBirthRound, 1, BIRTH_ROUND_THRESHOLD);
        final List<GossipEvent> bufferedEvents = futureEventBuffer.updateEventWindow(newEventWindow);
        assertTrue(bufferedEvents.isEmpty());
    }

    /**
     * It is plausible that we have a big jump in rounds due to a reconnect. Verify that we don't emit events if they
     * become ancient while buffered.
     */
    @Test
    void eventsGoAncientWhileBufferedTest() {
        final Random random = getRandomPrintSeed();

        final Configuration configuration = new TestConfigBuilder()
                .withValue(EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD, true)
                .getOrCreateConfig();

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();

        final FutureEventBuffer futureEventBuffer = new DefaultFutureEventBuffer(platformContext);

        final long nonAncientBirthRound = 100;
        final long pendingConsensusRound = nonAncientBirthRound * 2;
        final long maxFutureRound = nonAncientBirthRound * 3;

        final NonAncientEventWindow eventWindow =
                new NonAncientEventWindow(pendingConsensusRound - 1, nonAncientBirthRound, 1, BIRTH_ROUND_THRESHOLD);

        futureEventBuffer.updateEventWindow(eventWindow);

        final int count = 1000;
        final List<GossipEvent> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final GossipEvent event = generateEvent(random, random.nextLong(1, maxFutureRound));
            events.add(event);
        }
        // Put the events in topological order
        events.sort(Comparator.comparingLong(a -> a.getHashedData().getBirthRound()));

        for (final GossipEvent event : events) {
            final List<GossipEvent> returnedEvents = futureEventBuffer.addEvent(event);
            assertTrue(returnedEvents == null || returnedEvents.size() == 1);
            final GossipEvent returnedEvent = returnedEvents == null ? null : returnedEvents.get(0);
            if (eventWindow.isAncient(event)) {
                // Ancient events should be discarded.
                assertNull(returnedEvent);
            } else if (event.getHashedData().getBirthRound() <= eventWindow.getPendingConsensusRound()) {
                // Non-future events should be returned immediately.
                assertSame(event, returnedEvent);
            } else {
                // Future events should be buffered.
                assertNull(returnedEvent);
            }
        }

        final NonAncientEventWindow newEventWindow = new NonAncientEventWindow(
                pendingConsensusRound * 1000, nonAncientBirthRound * 1000, 1, BIRTH_ROUND_THRESHOLD);

        final List<GossipEvent> bufferedEvents = futureEventBuffer.updateEventWindow(newEventWindow);
        assertTrue(bufferedEvents.isEmpty());
    }

    /**
     * Verify that an event that is buffered gets released at the exact moment we expect.
     */
    @Test
    void eventInBufferIsReleasedOnTimeTest() {
        final Random random = getRandomPrintSeed();

        final Configuration configuration = new TestConfigBuilder()
                .withValue(EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD, true)
                .getOrCreateConfig();

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();

        final FutureEventBuffer futureEventBuffer = new DefaultFutureEventBuffer(platformContext);

        final long pendingConsensusRound = random.nextLong(100, 1_000);
        final long nonAncientBirthRound = pendingConsensusRound / 2;

        final NonAncientEventWindow eventWindow =
                new NonAncientEventWindow(pendingConsensusRound - 1, nonAncientBirthRound, 1, BIRTH_ROUND_THRESHOLD);
        futureEventBuffer.updateEventWindow(eventWindow);

        final long roundsUntilRelease = random.nextLong(10, 20);
        final long eventBirthRound = pendingConsensusRound + roundsUntilRelease;
        final GossipEvent event = generateEvent(random, eventBirthRound);

        // Event is from the future, we can't release it yet
        assertNull(futureEventBuffer.addEvent(event));

        // While the (newPendingConsensusRound-1) is less than the event's birth round, the event should be buffered
        for (long currentConsensusRound = pendingConsensusRound - 1;
                currentConsensusRound < eventBirthRound - 1;
                currentConsensusRound++) {

            final NonAncientEventWindow newEventWindow =
                    new NonAncientEventWindow(currentConsensusRound, nonAncientBirthRound, 1, BIRTH_ROUND_THRESHOLD);
            final List<GossipEvent> bufferedEvents = futureEventBuffer.updateEventWindow(newEventWindow);
            assertTrue(bufferedEvents.isEmpty());
        }

        // When the pending consensus round is equal to the event's birth round, the event should be released
        // Note: the pending consensus round is equal to the current consensus round + 1, but the argument
        // for an event window takes the current consensus round, not the pending consensus round.
        // To land with the pending consensus round at the exact value as the event's birth round, we need to
        // set the current consensus round to the event's birth round - 1.

        final NonAncientEventWindow newEventWindow =
                new NonAncientEventWindow(eventBirthRound - 1, nonAncientBirthRound, 1, BIRTH_ROUND_THRESHOLD);
        final List<GossipEvent> bufferedEvents = futureEventBuffer.updateEventWindow(newEventWindow);
        assertEquals(1, bufferedEvents.size());
        assertSame(event, bufferedEvents.getFirst());
    }
}
