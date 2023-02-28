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

package com.swirlds.platform.event.preconsensus;

import com.swirlds.common.utility.Startable;
import com.swirlds.common.utility.Stoppable;
import com.swirlds.platform.internal.EventImpl;
import java.time.Duration;

/**
 * An object capable of writing preconsensus events to disk.
 */
public interface PreConsensusEventWriter extends Startable, Stoppable {

    /**
     * Write an event to the stream.
     *
     * @param event
     * 		the event to be written
     * @throws InterruptedException
     * 		if interrupted while waiting on queue to drain
     */
    void addEvent(EventImpl event) throws InterruptedException;

    /**
     * Let the event writer know the minimum generation for non-ancient events. Ancient events will be
     * ignored if added to the event writer.
     *
     * @param minimumGenerationNonAncient
     * 		the minimum generation of a non-ancient event
     */
    void setMinimumGenerationNonAncient(long minimumGenerationNonAncient) throws InterruptedException;

    /**
     * Set the minimum generation needed to be kept on disk.
     *
     * @param minimumGenerationToStore
     * 		the minimum generation required to be stored on disk
     */
    void setMinimumGenerationToStore(long minimumGenerationToStore); // TODO should this method be here?

    /**
     * Check if an event is guaranteed to be durable, i.e. flushed to disk.
     * @param event the event in question
     * @return true if the event can is guaranteed to be durable
     */
    boolean isEventDurable(EventImpl event);

    /**
     * Wait until an event is guaranteed to be durable, i.e. flushed to disk.
     * @param event the event in question
     * @throws InterruptedException if interrupted while waiting
     */
    void waitUntilDurable(EventImpl event) throws InterruptedException;

    /**
     * Wait until an event is guaranteed to be durable, i.e. flushed to disk.
     * @param event the event in question
     * @param  timeToWait the maximum time to wait
     * @return true if the event is durable, false if the time to wait has elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    boolean waitUntilDurable(EventImpl event, final Duration timeToWait) throws InterruptedException;

    /**
     * Flush events if necessary. May not flush if we have flushed very recently or if there is nothing to flush.
     *
     * @param force
     * 		if true then force the flush if there is any unflushed data, even if we have flushed recently.
     * 		If false then only flush if we haven't flushed recently
     */
    void flushIfNeeded(final boolean force);

    /**
     * Request that the event writer flushes an event to disk as soon as possible.
     * @param event the event that should be flushed as soon as possible
     */
    void requestUrgentFlushFor(EventImpl event); // TODO perhaps we only need this flush...?

}
