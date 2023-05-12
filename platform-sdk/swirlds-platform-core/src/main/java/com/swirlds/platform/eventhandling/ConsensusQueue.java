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

import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.metrics.ConsensusHandlingMetrics;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A specialized implementation of {@link BlockingQueue} for the consensus queue (q2) that stores consensus rounds
 * to be handled. Despite the queue storing rounds, the capacity to store additional rounds is determined by the total
 * number of events in those rounds.
 * <p>
 * The implementation does not support multiple threads adding elements or multiple threads removing elements, but it
 * does support a single thread that adds elements that is different from the thread removing elements.
 */
public class ConsensusQueue implements BlockingQueue<ConsensusRound> {

    /** The total number of events in all the rounds in the queue at any given time. */
    private final AtomicInteger eventsInQueue = new AtomicInteger(0);

    /** The statistics instance to update */
    private final ConsensusHandlingMetrics consensusHandlingMetrics;

    /** The maximum number of events allowed in rounds in the queue */
    private final int eventCapacity;

    /** The queue that holds the rounds */
    private final LinkedBlockingQueue<ConsensusRound> queue = new LinkedBlockingQueue<>();

    /**
     * Creates a new instance with no items in the queue.
     *
     * @param consensusHandlingMetrics
     * 		the stats object to record stats on
     * @param eventCapacity
     * 		the maximum number of events allowed in all the rounds in the queue, unless there is a single round in the
     * 		queue with more than this many events
     */
    ConsensusQueue(final ConsensusHandlingMetrics consensusHandlingMetrics, final int eventCapacity) {
        super();
        this.consensusHandlingMetrics = consensusHandlingMetrics;
        this.eventCapacity = eventCapacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add(final ConsensusRound consensusRound) {
        throw new UnsupportedOperationException("use put() instead");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean offer(final ConsensusRound consensusRound) {
        if (queueHasRoom(consensusRound)) {
            final boolean ans = queue.offer(consensusRound);
            if (ans) {
                incrementEventsInQueue(consensusRound);
            }
            return ans;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsensusRound remove() {
        final ConsensusRound round = queue.remove();
        if (round == null) {
            throw new NoSuchElementException("queue is empty");
        }
        decrementEventsInQueue(round);
        return round;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsensusRound poll() {
        final ConsensusRound round = queue.poll();
        if (round != null) {
            decrementEventsInQueue(round);
        }
        return round;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsensusRound element() {
        final ConsensusRound round = queue.element();
        if (round == null) {
            throw new NoSuchElementException("queue is empty");
        }
        return round;
    }

    /**
     * Retrieves, but does not remove, the head of this queue,
     * or returns {@code null} if this queue is empty.
     *
     * @return the head of this queue, or {@code null} if this queue is empty
     */
    @Override
    public ConsensusRound peek() {
        return queue.peek();
    }

    /**
     * Adds the round to the queue (q2) if there is room according to the event capacity. If there is no room, wait
     * until there is.
     *
     * @param consensusRound
     * 		the round to add to the queue
     * @throws InterruptedException
     * 		if this thread is interrupted while waiting or adding to the queue
     */
    @Override
    public synchronized void put(final ConsensusRound consensusRound) throws InterruptedException {
        consensusHandlingMetrics.recordEventsPerRound(consensusRound.getNumEvents());
        while (!queueHasRoom(consensusRound)) {
            this.wait();
        }

        queue.put(consensusRound);
        incrementEventsInQueue(consensusRound);
    }

    private void incrementEventsInQueue(final ConsensusRound consensusRound) {
        eventsInQueue.updateAndGet(currValue -> currValue + consensusRound.getNumEvents());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean offer(final ConsensusRound consensusRound, final long timeout, final TimeUnit unit)
            throws InterruptedException {
        consensusHandlingMetrics.recordEventsPerRound(consensusRound.getNumEvents());
        final boolean ans;
        long millisWaited = 0;
        final long maxMillisToWait = unit.toMillis(timeout);
        while (!queueHasRoom(consensusRound) && millisWaited <= maxMillisToWait) {
            millisWaited++;
            this.wait(1);
        }

        if (!queueHasRoom(consensusRound)) {
            return false;
        }

        ans = queue.offer(consensusRound);
        if (ans) {
            incrementEventsInQueue(consensusRound);
        }
        return ans;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsensusRound take() throws InterruptedException {
        final ConsensusRound round = queue.take();
        decrementEventsInQueue(round);
        return round;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsensusRound poll(final long timeout, final TimeUnit unit) throws InterruptedException {
        final ConsensusRound round = queue.poll(timeout, unit);
        if (round != null) {
            decrementEventsInQueue(round);
        }
        return round;
    }

    /**
     * Returns the number of additional elements that this queue can ideally
     * (in the absence of memory or resource constraints) accept without
     * blocking, or {@code Integer.MAX_VALUE} if there is no intrinsic
     * limit.
     *
     * <p>Note that you <em>cannot</em> always tell if an attempt to insert
     * an element will succeed by inspecting {@code remainingCapacity}
     * because it may be the case that another thread is about to
     * insert or remove an element.
     *
     * @return the remaining capacity
     */
    @Override
    public int remainingCapacity() {
        return eventCapacity - eventsInQueue.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(final Object o) {
        throw new UnsupportedOperationException("remove() is not supported by ConsensusQueue");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsAll(final Collection<?> c) {
        return queue.containsAll(c);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(final Object o) {
        return queue.contains(o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<ConsensusRound> iterator() {
        return queue.iterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object[] toArray() {
        return queue.toArray();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T[] toArray(final T[] a) {
        return queue.toArray(a);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addAll(final Collection<? extends ConsensusRound> c) {
        throw new UnsupportedOperationException("addAll() is not supported by ConsensusQueue");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException("removeAll() is not supported by ConsensusQueue");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException("retainAll() is not supported by ConsensusQueue");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        queue.clear();
        eventsInQueue.set(0);
    }

    /**
     * Returns the total number of events in all the rounds in this queue.
     */
    @Override
    public int size() {
        return eventsInQueue.get();
    }

    /**
     * Returns {@code true} if this collection contains no elements.
     *
     * @return {@code true} if this collection contains no elements
     */
    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int drainTo(final Collection<? super ConsensusRound> c) {
        final int numDrained = queue.drainTo(c);
        for (final Object round : c) {
            decrementEventsInQueue((ConsensusRound) round);
        }
        return numDrained;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int drainTo(final Collection<? super ConsensusRound> c, final int maxElements) {
        final int numDrained = queue.drainTo(c, maxElements);
        for (final Object round : c) {
            decrementEventsInQueue((ConsensusRound) round);
        }
        return numDrained;
    }

    /**
     * Determines if the {@code consensusRound} can be added to the queue based on the event capacity. If the queue is
     * currently empty, return true regardless of the number of events in the round. If the queue is not empty, check if
     * adding the round will exceed the maximum number of events.
     *
     * @param consensusRound
     * 		the round to add
     * @return true if the round can be added
     */
    private boolean queueHasRoom(final ConsensusRound consensusRound) {
        final int numEvents = consensusRound.getNumEvents();
        if (queue.isEmpty()) {
            return true;
        }
        return eventsInQueue.get() + numEvents <= eventCapacity;
    }

    /**
     * Decrement the number of events in the queue by the number of events in {@code consensusRound}. The handler of
     * items in the queue is responsible for calling this method.
     *
     * @param consensusRound
     * 		the round just removed from the queue
     */
    private synchronized void decrementEventsInQueue(final ConsensusRound consensusRound) {
        eventsInQueue.updateAndGet(currValue -> currValue - consensusRound.getNumEvents());
        this.notifyAll();
    }
}
