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

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An object capable of writing preconsensus events to disk.
 */
public interface PreconsensusEventWriterInterface extends Startable, Stoppable {
    Logger logger = LogManager.getLogger();

    void beginStreamingNewEvents() throws InterruptedException;


    void writeEvent(@NonNull EventImpl event) throws InterruptedException;

    void setMinimumGenerationNonAncient(long minimumGenerationNonAncient) throws InterruptedException;

    void registerDiscontinuity(long newOriginRound) throws InterruptedException;

    void setMinimumGenerationToStore(long minimumGenerationToStore) throws InterruptedException;

    /**
     * Same as {@link #setMinimumGenerationToStore(long)} but does not throw an {@link InterruptedException}. If
     * interrupted, it will set the interrupted flag and log an error.
     */
    default void setMinimumGenerationToStoreUninterruptably(final long minimumGenerationToStore) {
        try {
            setMinimumGenerationToStore(minimumGenerationToStore);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error(EXCEPTION.getMarker(), "interrupted while setting minimum generation to store");
        }
    }

    /**
     * Request that the event writer perform a flush as soon as all events currently added have been written.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    void requestFlush() throws InterruptedException;


    boolean isEventDurable(@NonNull EventImpl event);


    void waitUntilDurable(@NonNull EventImpl event) throws InterruptedException;

    boolean waitUntilDurable(@NonNull EventImpl event, @NonNull final Duration timeToWait) throws InterruptedException;
}
