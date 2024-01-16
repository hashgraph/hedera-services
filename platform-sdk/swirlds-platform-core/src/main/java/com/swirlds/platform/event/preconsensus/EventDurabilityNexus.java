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

package com.swirlds.platform.event.preconsensus;

import com.swirlds.common.threading.CountUpLatch;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A class used to determine if an event is guaranteed to be durable, i.e. flushed to disk.
 */
public class EventDurabilityNexus {
    /**
     * The highest event sequence number that has been flushed durably to disk.
     */
    private final CountUpLatch latestDurableSequenceNumber = new CountUpLatch(-1);

    /**
     * Set the highest event sequence number that has been flushed.
     *
     * @param lastFlushedEvent the highest event sequence number that has been flushed
     */
    public void setLatestDurableSequenceNumber(final long lastFlushedEvent) {
        this.latestDurableSequenceNumber.set(lastFlushedEvent);
    }

    /**
     * Determine if an event is guaranteed to be durable, i.e. flushed to disk
     *
     * @param event the event in question
     * @return true if the event is guaranteed to be durable, false otherwise
     */
    public boolean isEventDurable(@NonNull final GossipEvent event) {
        return event.getStreamSequenceNumber() <= latestDurableSequenceNumber.getCount();
    }

    /**
     * Wait until an event is guaranteed to be durable, i.e. flushed to disk
     *
     * @param event the event in question
     * @throws InterruptedException if interrupted while waiting
     */
    public void waitUntilDurable(@NonNull final GossipEvent event) throws InterruptedException {
        latestDurableSequenceNumber.await(event.getStreamSequenceNumber());
    }
}
