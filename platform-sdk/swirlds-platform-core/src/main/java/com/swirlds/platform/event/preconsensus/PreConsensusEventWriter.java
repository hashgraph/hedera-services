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
    void writeEvent(EventImpl event) throws InterruptedException;

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
    void setMinimumGenerationToStore(long minimumGenerationToStore); // TODO actually call into this

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
     * Request that the event writer flushes an event to disk as soon as possible.
     * @param event the event that should be flushed as soon as possible
     */
    void requestFlush(EventImpl event);
}
