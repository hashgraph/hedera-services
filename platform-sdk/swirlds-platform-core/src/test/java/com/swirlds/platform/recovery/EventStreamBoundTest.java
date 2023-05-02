/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.recovery;

import static com.swirlds.platform.recovery.internal.EventStreamBound.NO_ROUND;
import static com.swirlds.platform.recovery.internal.EventStreamBound.NO_TIMESTAMP;
import static com.swirlds.platform.recovery.internal.EventStreamBound.UNBOUNDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.system.events.ConsensusData;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.recovery.internal.EventStreamBound;
import com.swirlds.platform.recovery.internal.EventStreamBound.BoundType;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EventStreamBoundTest Test")
class EventStreamBoundTest {

    @Test
    @DisplayName("Unbounded EventStreamBound Test")
    void unboundedEventStreamBound() {
        final EventStreamBound bound = EventStreamBound.create().build();

        assertTrue(bound.isUnbounded(), "bound should be unbounded");
        assertFalse(bound.hasRound(), "unbounded bound should not have round");
        assertFalse(bound.hasTimestamp(), "unbounded bound should not have timestamp");
        assertEquals(NO_ROUND, bound.getRound(), "round should be NO_ROUND");
        assertEquals(NO_TIMESTAMP, bound.getTimestamp(), "timestamp should be NO_TIMESTAMP");
        assertEquals(NO_ROUND, UNBOUNDED.getRound(), "round should be NO_ROUND");
        assertEquals(NO_TIMESTAMP, UNBOUNDED.getTimestamp(), "timestamp should be NO_TIMESTAMP");

        DetailedConsensusEvent event = createDetailedEventConsensus(1L, Instant.now());
        assertTrue(
                bound.compareTo(event, BoundType.LOWER) > 0,
                "every event must be greater than an unbounded lower bound.");
        assertTrue(
                bound.compareTo(event, BoundType.UPPER) < 0, "every event must be less than an unbounded upper bound.");
    }

    @Test
    @DisplayName("Round Based EventStreamBound Test")
    void roundBasedEventStreamBoundTest() {
        assertThrows(
                IllegalStateException.class,
                () -> EventStreamBound.create().setRound(-2L).build(),
                "round must be positive or NO_ROUND");
        assertThrows(
                IllegalStateException.class,
                () -> EventStreamBound.create().setRound(0L).build(),
                "round must be positive");

        final EventStreamBound bound = EventStreamBound.create().setRound(100L).build();
        assertEquals(100L, bound.getRound(), "round must match");
        assertTrue(bound.hasRound(), "bound should have round");
        assertFalse(bound.hasTimestamp(), "bound should not have timestamp");
        assertEquals(NO_TIMESTAMP, bound.getTimestamp(), "timestamp should be NO_TIMESTAMP");
        assertFalse(bound.isUnbounded(), "bound should not be unbounded");

        EventImpl before = createEventImpl(99L, Instant.now());
        EventImpl after = createEventImpl(101L, Instant.now());
        EventImpl same = createEventImpl(100L, Instant.now());

        // check lower behavior
        assertTrue(bound.compareTo(before, BoundType.LOWER) < 0, "event must be less than the bound.");
        assertTrue(bound.compareTo(after, BoundType.LOWER) > 0, "event must be greater than the bound.");
        assertTrue(bound.compareTo(same, BoundType.LOWER) == 0, "event must be equal to the bound.");

        // check upper behavior
        assertTrue(bound.compareTo(before, BoundType.UPPER) < 0, "event must be less than the bound.");
        assertTrue(bound.compareTo(after, BoundType.UPPER) > 0, "event must be greater than the bound.");
        assertTrue(bound.compareTo(same, BoundType.UPPER) == 0, "event must be equal to the bound.");
    }

    @Test
    @DisplayName("Timestamp Based EventStreamBound Test")
    void timestampBasedEventStreamBoundTest() throws InterruptedException {

        final Instant beforeTime = Instant.now();
        TimeUnit.MILLISECONDS.sleep(10);
        final Instant sameTime = Instant.now();
        TimeUnit.MILLISECONDS.sleep(10);
        final Instant afterTime = Instant.now();

        assertTrue(beforeTime.isBefore(sameTime), "beforeTime must be before sameTime");
        assertTrue(sameTime.isBefore(afterTime), "sameTime must be before afterTime");

        final EventStreamBound bound =
                EventStreamBound.create().setTimestamp(sameTime).build();
        assertEquals(sameTime, bound.getTimestamp(), "timestamp must match");
        assertFalse(bound.hasRound(), "bound should not have round");
        assertTrue(bound.hasTimestamp(), "bound should have timestamp");
        assertEquals(NO_ROUND, bound.getRound(), "round should be NO_ROUND");
        assertFalse(bound.isUnbounded(), "bound should not be unbounded");

        final EventImpl before = createEventImpl(99L, beforeTime);
        final EventImpl after = createEventImpl(101L, afterTime);
        final EventImpl same = createEventImpl(100L, sameTime);

        // check lower behavior
        assertTrue(bound.compareTo(before, BoundType.LOWER) < 0, "event must be less than the bound.");
        assertTrue(bound.compareTo(after, BoundType.LOWER) > 0, "event must be greater than the bound.");
        assertTrue(bound.compareTo(same, BoundType.LOWER) == 0, "event must be equal to the bound.");

        // check upper behavior
        assertTrue(bound.compareTo(before, BoundType.UPPER) < 0, "event must be less than the bound.");
        assertTrue(bound.compareTo(after, BoundType.UPPER) > 0, "event must be greater than the bound.");
        assertTrue(bound.compareTo(same, BoundType.UPPER) == 0, "event must be equal to the bound.");
    }

    @Test
    @DisplayName("Round And Timestamp Based EventStreamBound Test")
    void reoundAndTimestampBasedEventStreamBoundTest() throws InterruptedException {

        final Instant beforeTime = Instant.now();
        TimeUnit.MILLISECONDS.sleep(10);
        final Instant sameTime = Instant.now();
        TimeUnit.MILLISECONDS.sleep(10);
        final Instant afterTime = Instant.now();

        assertTrue(beforeTime.isBefore(sameTime), "beforeTime must be before sameTime");
        assertTrue(sameTime.isBefore(afterTime), "sameTime must be before afterTime");

        final EventStreamBound bound =
                EventStreamBound.create().setTimestamp(sameTime).setRound(100L).build();
        assertEquals(sameTime, bound.getTimestamp(), "timestamp must match");
        assertEquals(100L, bound.getRound(), "round must match");
        assertTrue(bound.hasRound(), "bound should not have round");
        assertTrue(bound.hasTimestamp(), "bound should have timestamp");
        assertFalse(bound.isUnbounded(), "bound should not be unbounded");

        final EventImpl before = createEventImpl(99L, beforeTime);
        final EventImpl after = createEventImpl(101L, afterTime);
        final EventImpl same = createEventImpl(100L, sameTime);
        final EventImpl sameTimeBeforeRound = createEventImpl(99L, sameTime);
        final EventImpl sameTimeAfterRound = createEventImpl(101L, sameTime);
        final EventImpl sameRoundAfterTime = createEventImpl(100L, afterTime);
        final EventImpl sameRoundBeforeTime = createEventImpl(100L, beforeTime);

        // check lower behavior
        assertTrue(bound.compareTo(before, BoundType.LOWER) < 0, "event must be less than the bound.");
        assertTrue(bound.compareTo(after, BoundType.LOWER) > 0, "event must be greater than the bound.");
        assertTrue(bound.compareTo(same, BoundType.LOWER) == 0, "event must be equal to the bound.");
        assertTrue(bound.compareTo(sameTimeBeforeRound, BoundType.LOWER) < 0, "event must be equal to the bound.");
        assertTrue(bound.compareTo(sameTimeAfterRound, BoundType.LOWER) == 0, "event must be equal to the bound.");
        assertTrue(bound.compareTo(sameRoundAfterTime, BoundType.LOWER) == 0, "event must be equal to the bound.");
        assertTrue(bound.compareTo(sameRoundBeforeTime, BoundType.LOWER) < 0, "event must be equal to the bound.");

        // check upper behavior
        assertTrue(bound.compareTo(before, BoundType.UPPER) < 0, "event must be less than the bound.");
        assertTrue(bound.compareTo(after, BoundType.UPPER) > 0, "event must be greater than the bound.");
        assertTrue(bound.compareTo(same, BoundType.UPPER) == 0, "event must be equal to the bound.");
        assertTrue(bound.compareTo(sameTimeBeforeRound, BoundType.UPPER) == 0, "event must be equal to the bound.");
        assertTrue(bound.compareTo(sameTimeAfterRound, BoundType.UPPER) > 0, "event must be equal to the bound.");
        assertTrue(bound.compareTo(sameRoundAfterTime, BoundType.UPPER) > 0, "event must be equal to the bound.");
        assertTrue(bound.compareTo(sameRoundBeforeTime, BoundType.UPPER) == 0, "event must be equal to the bound.");
    }

    /**
     * Creates a mock {@link DetailedConsensusEvent} with the given round and timestamp.
     *
     * @param round     the round to use
     * @param timestamp the timestamp to use
     * @return the mock event
     */
    private DetailedConsensusEvent createDetailedEventConsensus(long round, Instant timestamp) {
        final ConsensusData data = createConsensusData(round, timestamp);
        final DetailedConsensusEvent event = mock(DetailedConsensusEvent.class);
        when(event.getConsensusData()).thenReturn(data);
        return event;
    }

    /**
     * Creates a mock {@link EventImpl} with the given round and timestamp.
     *
     * @param round     the round to use
     * @param timestamp the timestamp to use
     * @return the mock event
     */
    private EventImpl createEventImpl(long round, Instant timestamp) {
        final ConsensusData data = createConsensusData(round, timestamp);
        final EventImpl event = mock(EventImpl.class);
        when(event.getConsensusData()).thenReturn(data);
        return event;
    }

    /**
     * Creates a mock {@link ConsensusData} with the given round and timestamp.
     *
     * @param round     the round to use
     * @param timestamp the timestamp to use
     * @return the mock event
     */
    private ConsensusData createConsensusData(long round, Instant timestamp) {
        final ConsensusData data = mock(ConsensusData.class);
        when(data.getRoundReceived()).thenReturn(round);
        when(data.getConsensusTimestamp()).thenReturn(timestamp);
        return data;
    }
}
