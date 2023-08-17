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

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.platform.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;

/**
 * An object capable of writing preconsensus events to disk.
 */
public interface PreconsensusEventWriter extends Startable, Stoppable {

    /**
     * Prior to this method being called, all events added to the preconsensus event stream are assumed to be events
     * read from the preconsensus event stream on disk. The events from the stream on disk are not re-written to the
     * disk, and are considered to be durable immediately upon ingest.
     */
    void beginStreamingNewEvents() throws InterruptedException;

    /**
     * Write an event to the stream.
     *
     * @param event the event to be written
     * @throws InterruptedException if interrupted while waiting on queue to drain
     */
    void writeEvent(@NonNull EventImpl event) throws InterruptedException;

    /**
     * Let the event writer know the minimum generation for non-ancient events. Ancient events will be ignored if added
     * to the event writer.
     *
     * @param minimumGenerationNonAncient the minimum generation of a non-ancient event
     */
    void setMinimumGenerationNonAncient(long minimumGenerationNonAncient) throws InterruptedException;

    /**
     * Inform the preconsensus event writer that a discontinuity has occurred in the preconsensus event stream.
     */
    void registerDiscontinuity() throws InterruptedException;

    /**
     * Set the minimum generation needed to be kept on disk.
     *
     * @param minimumGenerationToStore the minimum generation required to be stored on disk
     */
    void setMinimumGenerationToStore(long minimumGenerationToStore) throws InterruptedException;

    /**
     * Request that the event writer perform a flush as soon as all events currently added have been written.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    void requestFlush() throws InterruptedException;

    /**
     * Check if an event is guaranteed to be durable, i.e. flushed to disk.
     *
     * @param event the event in question
     * @return true if the event can is guaranteed to be durable
     */
    boolean isEventDurable(@NonNull EventImpl event);

    /**
     * Wait until an event is guaranteed to be durable, i.e. flushed to disk. Prior to blocking on this method, the
     * event in question should have been passed to {@link #writeEvent(EventImpl)} and {@link #requestFlush()} should
     * have been called. Otherwise, this method may block indefinitely.
     *
     * @param event the event in question
     * @throws InterruptedException if interrupted while waiting
     */
    void waitUntilDurable(@NonNull EventImpl event) throws InterruptedException;

    /**
     * Wait until an event is guaranteed to be durable, i.e. flushed to disk. Prior to blocking on this method, the
     * event in question should have been passed to {@link #writeEvent(EventImpl)} and {@link #requestFlush()} should
     * have been called. Otherwise, this method may block until the end of its timeout and return false.
     *
     * @param event      the event in question
     * @param timeToWait the maximum time to wait
     * @return true if the event is durable, false if the time to wait has elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    boolean waitUntilDurable(@NonNull EventImpl event, @NonNull final Duration timeToWait) throws InterruptedException;
}
