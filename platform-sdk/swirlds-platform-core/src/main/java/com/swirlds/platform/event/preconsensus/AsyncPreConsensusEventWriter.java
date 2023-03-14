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

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.threading.framework.BlockingQueueInserter;
import com.swirlds.common.threading.framework.MultiQueueThread;
import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.internal.EventImpl;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An object capable of writing preconsensus events to disk. Work is done asynchronously on a background thread.
 */
public class AsyncPreConsensusEventWriter implements PreConsensusEventWriter {

    private static final Logger logger = LogManager.getLogger();

    /**
     * The wrapped writer.
     */
    private final PreConsensusEventWriter writer;

    /**
     * Background work is performed on this thread.
     */
    private final MultiQueueThread handleThread;

    /**
     * Used to push the minimum generation non-ancient onto the handle queue.
     */
    private final BlockingQueueInserter<Long> minimumGenerationNonAncientInserter;

    /**
     * Used to push events onto the handle queue.
     */
    private final BlockingQueueInserter<EventImpl> eventInserter;

    /**
     * Create a new AsyncPreConsensusEventWriter.
     *
     * @param threadManager responsible for creating new threads
     * @param config        preconsensus event stream configuration
     * @param writer        the writer to which events will be written, wrapped by this class
     */
    public AsyncPreConsensusEventWriter(
            final ThreadManager threadManager,
            final PreConsensusEventStreamConfig config,
            final PreConsensusEventWriter writer) {

        this.writer = writer;

        handleThread = new MultiQueueThreadConfiguration(threadManager)
                .setComponent("pre-consensus")
                .setThreadName("event-writer")
                .setCapacity(config.writeQueueCapacity())
                .addHandler(Long.class, this::setMinimumGenerationNonAncientHandler)
                .addHandler(EventImpl.class, this::addEventHandler)
                .build();

        minimumGenerationNonAncientInserter = handleThread.getInserter(Long.class);
        eventInserter = handleThread.getInserter(EventImpl.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        writer.start();
        handleThread.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        handleThread.stop();
        writer.stop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeEvent(final EventImpl event) throws InterruptedException {
        if (event.getStreamSequenceNumber() == EventImpl.NO_STREAM_SEQUENCE_NUMBER
                || event.getStreamSequenceNumber() == EventImpl.STALE_EVENT_STREAM_SEQUENCE_NUMBER) {
            throw new IllegalStateException("Event must have a valid stream sequence number");
        }
        eventInserter.put(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) throws InterruptedException {
        minimumGenerationNonAncientInserter.put(minimumGenerationNonAncient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMinimumGenerationToStore(final long minimumGenerationToStore) {
        writer.setMinimumGenerationToStore(minimumGenerationToStore);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventDurable(final EventImpl event) {
        return writer.isEventDurable(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitUntilDurable(final EventImpl event) throws InterruptedException {
        writer.waitUntilDurable(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitUntilDurable(final EventImpl event, final Duration timeToWait) throws InterruptedException {
        return writer.waitUntilDurable(event, timeToWait);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestFlush(final EventImpl event) {
        writer.requestFlush(event);
    }

    /**
     * Pass a minimum generation non-ancient to the wrapped writer.
     */
    private void setMinimumGenerationNonAncientHandler(final Long minimumGenerationNonAncient) {
        try {
            writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);
        } catch (final InterruptedException e) {
            // Unless we do something silly like wrapping an asynchronous writer inside another asynchronous writer,
            // this should never throw an InterruptedException.
            logger.error(
                    EXCEPTION.getMarker(),
                    "interrupted while attempting to call setMinimumGenerationNonAncient on writer",
                    e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Pass an event to the wrapped writer.
     */
    private void addEventHandler(final EventImpl event) {
        try {
            writer.writeEvent(event);
        } catch (final InterruptedException e) {
            // Unless we do something silly like wrapping an asynchronous writer inside another asynchronous writer,
            // this should never throw an InterruptedException.
            logger.error(EXCEPTION.getMarker(), "interrupted while attempting to call addEvent on writer", e);
            Thread.currentThread().interrupt();
        }
    }
}
