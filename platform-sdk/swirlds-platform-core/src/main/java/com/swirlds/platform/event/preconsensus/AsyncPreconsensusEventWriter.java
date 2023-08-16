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

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.threading.framework.BlockingQueueInserter;
import com.swirlds.common.threading.framework.MultiQueueThread;
import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An object capable of writing preconsensus events to disk. Work is done asynchronously on a background thread.
 */
public class AsyncPreconsensusEventWriter implements PreconsensusEventWriter {

    private static final Logger logger = LogManager.getLogger();

    /**
     * The wrapped writer.
     */
    private final PreconsensusEventWriter writer;

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
     * This class is used as a flag to indicate where in the queue events start being new (as opposed to being events
     * from the preconsensus event stream on disk).
     */
    private static class BeginStreamingNewEvents {}

    private static final BeginStreamingNewEvents BEGIN_STREAMING_NEW_EVENTS = new BeginStreamingNewEvents();

    /**
     * Used to push the BeginStreamingNewEvents flag onto the handle queue.
     */
    private final BlockingQueueInserter<BeginStreamingNewEvents> beginStreamingNewEventsInserter;

    /**
     * This class is used as a flag to indicate that the handle thread should flush the writer.
     */
    private static class FlushRequested {}

    private static final FlushRequested FLUSH_REQUESTED = new FlushRequested();

    /**
     * Used to push the FlushRequested flag onto the handle queue.
     */
    private final BlockingQueueInserter<FlushRequested> flushRequestedInserter;

    /**
     * This class is used as a flag to indicate that there is a discontinuity in the stream.
     */
    private static class Discontinuity {}

    private static final Discontinuity DISCONTINUITY = new Discontinuity();

    /**
     * Used to push the Discontinuity flag onto the handle queue.
     */
    private final BlockingQueueInserter<Discontinuity> discontinuityInserter;

    /**
     * This class is used to indicate the new minimum generation to store.
     */
    private record MinimumGenerationToStore(long minimumGenerationToStore) {}

    /**
     * Used to push the MinimumGenerationToStore message onto the handle queue.
     */
    private final BlockingQueueInserter<MinimumGenerationToStore> minimumGenerationToStoreInserter;

    /**
     * Create a new AsyncPreConsensusEventWriter.
     *
     * @param platformContext the platform context
     * @param threadManager   responsible for creating new threads
     * @param writer          the writer to which events will be written, wrapped by this class
     */
    public AsyncPreconsensusEventWriter(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final PreconsensusEventWriter writer) {

        Objects.requireNonNull(platformContext, "platformContext must not be null");
        Objects.requireNonNull(threadManager, "threadManager must not be null");
        this.writer = Objects.requireNonNull(writer, "writer must not be null");

        final PreconsensusEventStreamConfig config =
                platformContext.getConfiguration().getConfigData(PreconsensusEventStreamConfig.class);

        handleThread = new MultiQueueThreadConfiguration(threadManager)
                .setComponent("preconsensus")
                .setThreadName("event-writer")
                .setCapacity(config.writeQueueCapacity())
                .addHandler(Long.class, this::setMinimumGenerationNonAncientHandler)
                .addHandler(EventImpl.class, this::addEventHandler)
                .addHandler(BeginStreamingNewEvents.class, this::beginStreamingNewEventsHandler)
                .addHandler(FlushRequested.class, this::flushRequestedHandler)
                .addHandler(Discontinuity.class, this::discontinuityHandler)
                .addHandler(MinimumGenerationToStore.class, this::minimumGenerationToStoreHandler)
                .setMetricsConfiguration(
                        new QueueThreadMetricsConfiguration(platformContext.getMetrics()).enableBusyTimeMetric())
                .build();

        minimumGenerationNonAncientInserter = handleThread.getInserter(Long.class);
        eventInserter = handleThread.getInserter(EventImpl.class);
        beginStreamingNewEventsInserter = handleThread.getInserter(BeginStreamingNewEvents.class);
        flushRequestedInserter = handleThread.getInserter(FlushRequested.class);
        discontinuityInserter = handleThread.getInserter(Discontinuity.class);
        minimumGenerationToStoreInserter = handleThread.getInserter(MinimumGenerationToStore.class);
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
    public void beginStreamingNewEvents() throws InterruptedException {
        beginStreamingNewEventsInserter.put(BEGIN_STREAMING_NEW_EVENTS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeEvent(@NonNull final EventImpl event) throws InterruptedException {
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
    public void requestFlush() throws InterruptedException {
        flushRequestedInserter.put(FLUSH_REQUESTED);
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
    public void registerDiscontinuity() throws InterruptedException {
        discontinuityInserter.put(DISCONTINUITY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMinimumGenerationToStore(final long minimumGenerationToStore) throws InterruptedException {
        minimumGenerationToStoreInserter.put(new MinimumGenerationToStore(minimumGenerationToStore));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventDurable(@NonNull final EventImpl event) {
        return writer.isEventDurable(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitUntilDurable(@NonNull final EventImpl event) throws InterruptedException {
        writer.waitUntilDurable(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitUntilDurable(@NonNull final EventImpl event, @NonNull final Duration timeToWait)
            throws InterruptedException {
        return writer.waitUntilDurable(event, timeToWait);
    }

    /**
     * Pass a minimum generation non-ancient to the wrapped writer.
     */
    private void setMinimumGenerationNonAncientHandler(@NonNull final Long minimumGenerationNonAncient) {
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
    private void addEventHandler(@NonNull final EventImpl event) {
        try {
            writer.writeEvent(event);
        } catch (final InterruptedException e) {
            // Unless we do something silly like wrapping an asynchronous writer inside another asynchronous writer,
            // this should never throw an InterruptedException.
            logger.error(EXCEPTION.getMarker(), "interrupted while attempting to call addEvent on writer", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Notify the wrapped writer that we are now streaming new events.
     */
    private void beginStreamingNewEventsHandler(@NonNull final BeginStreamingNewEvents beginStreamingNewEvents) {
        try {
            writer.beginStreamingNewEvents();
        } catch (final InterruptedException e) {
            // Unless we do something silly like wrapping an asynchronous writer inside another asynchronous writer,
            // this should never throw an InterruptedException.
            logger.error(
                    EXCEPTION.getMarker(), "interrupted while attempting to call beginStreamingNewEvents on writer", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Notify the wrapped writer that it should flush.
     */
    private void flushRequestedHandler(@NonNull final FlushRequested flushRequested) {
        try {
            writer.requestFlush();
        } catch (final InterruptedException e) {
            // Unless we do something silly like wrapping an asynchronous writer inside another asynchronous writer,
            // this should never throw an InterruptedException.
            logger.error(EXCEPTION.getMarker(), "interrupted while attempting to call flush on writer", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Notify the wrapped writer that there is a discontinuity.
     */
    private void discontinuityHandler(@NonNull final Discontinuity discontinuity) {
        try {
            writer.registerDiscontinuity();
        } catch (final InterruptedException e) {
            // Unless we do something silly like wrapping an asynchronous writer inside another asynchronous writer,
            // this should never throw an InterruptedException.
            logger.error(EXCEPTION.getMarker(), "interrupted while attempting to register a discontinuity", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Set the minimum generation to store on the wrapped writer.
     */
    private void minimumGenerationToStoreHandler(@NonNull final MinimumGenerationToStore minimumGenerationToStore) {
        try {
            writer.setMinimumGenerationToStore(minimumGenerationToStore.minimumGenerationToStore);
        } catch (final InterruptedException e) {
            // Unless we do something silly like wrapping an asynchronous writer inside another asynchronous writer,
            // this should never throw an InterruptedException.
            logger.error(EXCEPTION.getMarker(), "interrupted while attempting to set minimum generation to store", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Wait until the handle thread is not busy. May block indefinitely if work is continuously added.
     */
    public void waitUntilNotBusy() throws InterruptedException {
        handleThread.waitUntilNotBusy();
    }
}
