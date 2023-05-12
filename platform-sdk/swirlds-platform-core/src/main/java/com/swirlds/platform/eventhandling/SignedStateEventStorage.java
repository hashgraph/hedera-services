/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import com.swirlds.platform.internal.EventImpl;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Keeps events that need to be stored in the signed state when it is created. It makes sure it has last {@code
 * staleRound} number of rounds created at all times.
 */
public class SignedStateEventStorage {
    /** a queue of all the latest events that need to be saved in state */
    private final Deque<EventImpl> queue = new LinkedList<>();
    /** the latest round received */
    private long latestRoundReceived;

    /** the minimum round created in the queue */
    private long minRoundCreatedInQueue;

    public SignedStateEventStorage() {
        clear();
    }

    public synchronized void add(final List<EventImpl> events) {
        for (final EventImpl event : events) {
            add(event);
        }
    }

    public synchronized void add(final EventImpl event) {
        queue.add(event);
        latestRoundReceived = event.getRoundReceived();
        updateMinRoundCreated(event);
    }

    public synchronized EventImpl[] getEventsForLatestRound() {
        return queue.toArray(new EventImpl[] {});
    }

    /**
     * Remove all events older than {@code minGenerationNonAncient} from storage
     *
     * @param minGenerationNonAncient
     * 		generation below which all events are ancient
     */
    public synchronized void expireEvents(final long minGenerationNonAncient) {
        minRoundCreatedInQueue = Long.MAX_VALUE;
        // we traverse the queue from newest events to oldest
        final Iterator<EventImpl> i = queue.iterator();
        while (i.hasNext()) {
            final EventImpl event = i.next();
            // remove all events whose generation is older than minGenerationNonAncient
            if (event.getGeneration() < minGenerationNonAncient) {
                i.remove();
            } else {
                updateMinRoundCreated(event);
            }
        }
    }

    private void updateMinRoundCreated(final EventImpl event) {
        minRoundCreatedInQueue = Math.min(minRoundCreatedInQueue, event.getRoundCreated());
    }

    public synchronized void clear() {
        latestRoundReceived = -1;
        minRoundCreatedInQueue = Long.MAX_VALUE;
    }

    public synchronized void loadDataFromSignedState(
            final EventImpl[] signedStateEvents, final long minGenerationNonAncient) {
        for (final EventImpl event : signedStateEvents) {
            add(event);
        }
        expireEvents(minGenerationNonAncient);
    }

    public synchronized int getQueueSize() {
        return queue.size();
    }

    /**
     * @return the highest round received in storage
     */
    public synchronized long getLatestRoundReceived() {
        return latestRoundReceived;
    }

    /**
     * @return the lowest round number a stored event was created in
     */
    public synchronized long getMinRoundCreatedInQueue() {
        return minRoundCreatedInQueue;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SignedStateEventStorage that = (SignedStateEventStorage) o;

        return new EqualsBuilder()
                .append(latestRoundReceived, that.latestRoundReceived)
                .append(queue, that.queue)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(queue)
                .append(latestRoundReceived)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("queue", queue)
                .append("latestRoundReceived", latestRoundReceived)
                .toString();
    }
}
