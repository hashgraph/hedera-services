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

import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndThrowIfInterrupted;

import com.swirlds.common.threading.framework.BlockingQueueInserter;
import com.swirlds.common.threading.framework.MultiQueueThread;
import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.throttle.MinimumTime;
import com.swirlds.platform.internal.EventImpl;
import java.time.Duration;

/**
 * An object capable of writing preconsensus events to disk. Work is done asynchronously on a background thread.
 */
public class AsyncPreConsensusEventWriter implements PreConsensusEventWriter {

    /**
     * The wrapped writer.
     */
    private final PreConsensusEventWriter writer;

    /**
     * Background work is performed on this thread.
     */
    private final MultiQueueThread handleThread;

    /**
     * Used to the minimum generation non-ancient onto the handle queue.
     */
    private final BlockingQueueInserter<Long> minimumGenerationNonAncientInserter;

    /**
     * Used to push events onto the handle queue.
     */
    private final BlockingQueueInserter<EventImpl> eventInserter;

    /**
     * Provides wall clock time.
     */
    private final Time time;

    /**
     * When there is no work to do, wait this amount of time before checking for more work. Prevents busy loop.
     */
    private final Duration idleWaitPeriod;

    /**
     * Create a new AsyncPreConsensusEventWriter.
     * @param threadManager responsible for creating new threads
     * @param config preconsensus event stream configuration
     * @param writer the writer to which events will be written, wrapped by this class
     */
    public AsyncPreConsensusEventWriter(
            final ThreadManager threadManager,
            final Time time,
            final PreConsensusEventStreamConfig config,
            final PreConsensusEventWriter writer) {

        this.time = time;
        this.writer = writer;
        idleWaitPeriod = config.idleWaitPeriod();

        handleThread = new MultiQueueThreadConfiguration(threadManager)
                .setComponent("pre-consensus")
                .setThreadName("event-writer")
                .setCapacity(config.writeQueueCapacity())
                .setWaitForItemRunnable(this::waitForNextEvent)
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
        writer.stop();
        handleThread.stop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addEvent(final EventImpl event) throws InterruptedException {
        // TODO we should we update sequence number here?
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
    public void flushIfNeeded(boolean force) {
        writer.flushIfNeeded(force);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestUrgentFlushFor(EventImpl event) {
        writer.requestUrgentFlushFor(event);
    }

    /**
     * Pass a minimum generation non-ancient to the wrapped writer.
     */
    private void setMinimumGenerationNonAncientHandler(final Long minimumGenerationNonAncient) {
        // Unless we do something silly like wrapping an asynchronous writer inside another asynchronous writer,
        // this should never throw an InterruptedException.
        abortAndThrowIfInterrupted(
                () -> writer.setMinimumGenerationNonAncient(minimumGenerationNonAncient),
                "interrupted while attempting to call setMinimumGenerationNonAncient on writer");
    }

    /**
     * Pass an event to the wrapped writer.
     */
    private void addEventHandler(final EventImpl event) {
        // Unless we do something silly like wrapping an asynchronous writer inside another asynchronous writer,
        // this should never throw an InterruptedException.
        abortAndThrowIfInterrupted(
                () -> writer.addEvent(event), "interrupted while attempting to call addEvent on writer");
    }

    /**
     * This method is called when we run out of events to write and are waiting for more events
     * to enter the queue. When this happens, we might as well spend our time flushing, even if we
     * have flushed recently.
     */
    private void waitForNextEvent() throws InterruptedException {
        MinimumTime.runWithMinimumTime(time, () -> flushIfNeeded(true), idleWaitPeriod);
    }
}
