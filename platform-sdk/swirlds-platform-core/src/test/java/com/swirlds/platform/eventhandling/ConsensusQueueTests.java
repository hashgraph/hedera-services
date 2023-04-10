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

package com.swirlds.platform.eventhandling;

import static com.swirlds.common.threading.manager.internal.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.test.AssertionUtils;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.ConsensusHandlingMetrics;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsensusQueueTests {

    private static final long SELF_ID = 0L;

    private static final int EVENT_CAPACITY = 100;
    public static final String PUT_ERROR = "put() did not update eventsInQueue.";

    private QueueThread<ConsensusRound> queueThread;

    private final AtomicInteger roundsHandled = new AtomicInteger(0);

    private static ConsensusQueue newQueue() {
        return new ConsensusQueue(mock(ConsensusHandlingMetrics.class), EVENT_CAPACITY);
    }

    @BeforeEach
    public void setup() {
        queueThread = new QueueThreadConfiguration<ConsensusRound>(getStaticThreadManager())
                .setQueue(newQueue())
                .setNodeId(SELF_ID)
                .setHandler(this::handleRound)
                .build();
    }

    private int getNumEventsInQueue() {
        return queueThread.size();
    }

    /**
     * Tests that a single round with a number of events that exceeds the event capacity can be added only when the
     * queue is empty.
     *
     * @throws InterruptedException
     * 		if this thread is interrupted
     */
    @Test
    void testPutLargeRoundEmptyQueue() throws InterruptedException {
        final ConsensusRound bigRound = createRound(EVENT_CAPACITY + 1);

        queueThread.put(bigRound);

        assertEquals(bigRound.getNumEvents(), queueThread.size(), PUT_ERROR);

        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Future<Void> future = executorService.submit(() -> {
            queueThread.put(bigRound);
            return null;
        });

        boolean putBlocked = false;
        try {
            future.get(100, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            putBlocked = true;
        }

        assertTrue(
                putBlocked,
                "Adding a round with more events than the" + " event capacity should block until the queue is empty.");
    }

    /**
     * Test that the q2 stat is updated when rounds are added and removed.
     *
     * @throws InterruptedException
     * 		if this thread is interrupted
     */
    @Test
    void testQ2StatUpdated() throws InterruptedException {
        final ConsensusRound smallRound = createRound(10);

        // The queue is empty. Add a round that will not exceed capacity
        queueThread.put(smallRound);
        assertEquals(smallRound.getNumEvents(), queueThread.size(), PUT_ERROR);

        // Add the round again and check the stats
        queueThread.put(smallRound);
        assertEquals(smallRound.getNumEvents() * 2, queueThread.size(), PUT_ERROR);

        queueThread.start();

        AssertionUtils.assertEventuallyEquals(
                2, roundsHandled::get, Duration.ofMillis(1000), "Rounds were not handled");
        assertEquals(0, getNumEventsInQueue(), "eventsInQueue was not updated when a consensus was removed.");
    }

    /**
     * Test that adding another round is blocked when adding that round would exceed the event capacity.
     *
     * @throws InterruptedException
     * 		if this thread is interrupted
     */
    @Test
    void testBlockWhenQueueFull() throws InterruptedException {
        final ConsensusRound bigRound = createRound(EVENT_CAPACITY - 10);
        final ConsensusRound smallRound = createRound(10);

        // The queue is empty. Add a  that will not exceed capacity
        queueThread.put(bigRound);
        assertEquals(EVENT_CAPACITY - 10, queueThread.size(), PUT_ERROR);

        // Try adding the big round again. It should block because the number of events would exceed the capacity.
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Future<Void> future = executorService.submit(() -> {
            queueThread.put(bigRound);
            return null;
        });

        boolean putBlocked = false;
        try {
            future.get(100, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            putBlocked = true;
        }

        assertTrue(
                putBlocked,
                "Adding a  with events that would exceed" + " event capacity should block until the queue is empty.");

        // Should succeed, because the number of events does not exceed the capacity
        queueThread.put(smallRound);

        assertEquals(bigRound.getNumEvents() + smallRound.getNumEvents(), queueThread.size(), PUT_ERROR);
    }

    @Test
    void testPoll() throws InterruptedException {
        assertNull(queueThread.poll());
        assertEquals(0, queueThread.size(), "eventsInQueue should not be modified when the queue is already empty.");
        queueThread.put(createRound(15));
        assertEquals(15, queueThread.size(), PUT_ERROR);
        assertNotNull(queueThread.poll());
        assertEquals(0, queueThread.size(), "poll() did not update eventsInQueue.");
    }

    @Test
    void testPollWithTimeout() throws InterruptedException {
        assertNull(queueThread.poll(10, TimeUnit.MILLISECONDS));
        assertEquals(0, queueThread.size(), "eventsInQueue should not be modified when the queue is already empty.");
        queueThread.put(createRound(15));
        assertEquals(15, queueThread.size(), PUT_ERROR);
        assertNotNull(queueThread.poll(10, TimeUnit.MILLISECONDS));
        assertEquals(0, queueThread.size(), "poll() did not update eventsInQueue.");
    }

    @Test
    void testOffer() throws InterruptedException {
        queueThread.put(createRound(100));
        assertFalse(queueThread.offer(createRound(1)), "offer() did not respect the event capacity.");

        queueThread.clear();
        queueThread.put(createRound(10));
        assertEquals(10, queueThread.size(), PUT_ERROR);
        assertTrue(queueThread.offer(createRound(2)));
        assertEquals(12, queueThread.size(), "offer() did not update eventsInQueue.");
    }

    @Test
    void testOfferWithTimeout() throws InterruptedException {
        queueThread.offer(createRound(10), 100, TimeUnit.MILLISECONDS);
        assertEquals(10, queueThread.size(), "offer() did not update eventsInQueue.");

        queueThread.put(createRound(80));
        assertEquals(90, queueThread.size(), PUT_ERROR);

        assertFalse(
                queueThread.offer(createRound(20), 100, TimeUnit.MILLISECONDS),
                "offer(timeout, unit) should return false if no room is available after the timeout.");
        assertEquals(
                90,
                queueThread.size(),
                "offer(timeout, unit) should not change the eventsInQueue number when nothing was inserted.");
    }

    @Test
    void testRemove() throws InterruptedException {
        assertThrows(
                NoSuchElementException.class,
                () -> queueThread.remove(),
                "Attempting to remove from an empty queue should throw an exception.");
        final ConsensusRound round = createRound(10);
        queueThread.put(round);
        assertEquals(10, queueThread.size(), PUT_ERROR);
        assertEquals(round, queueThread.remove(), "remove() did not supply the correct round instance.");

        assertEquals(0, queueThread.size(), "remove() did not update eventsInQueue.");
    }

    void handleRound(final ConsensusRound round) {
        roundsHandled.addAndGet(1);
    }

    private ConsensusRound createRound(final int numEvents) {
        final ConsensusRound round = mock(ConsensusRound.class);
        final List<EventImpl> eventList = mock(List.class);
        when(round.getNumEvents()).thenReturn(numEvents);
        when(round.getConsensusEvents()).thenReturn(eventList);
        return round;
    }
}
