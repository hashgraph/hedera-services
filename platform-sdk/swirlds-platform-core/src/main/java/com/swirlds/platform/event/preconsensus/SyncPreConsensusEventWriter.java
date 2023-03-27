/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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
import com.swirlds.common.threading.CountUpLatch;
import com.swirlds.common.utility.LongRunningAverage;
import com.swirlds.common.utility.Units;
import com.swirlds.platform.internal.EventImpl;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.PriorityQueue;

/**
 * This object is responsible for writing events to the database.
 */
public class SyncPreConsensusEventWriter implements PreConsensusEventWriter, Startable, Stoppable {

    /**
     * Keeps track of the event stream files on disk.
     */
    private final PreConsensusEventFileManager fileManager;

    /**
     * The current file that is being written to.
     */
    private PreConsensusEventMutableFile currentMutableFile;

    /**
     * The current minimum generation required to be considered non-ancient. Only read and written on the handle
     * thread.
     */
    private long minimumGenerationNonAncient = 0;

    /**
     * The desired file size, in megabytes. Is not a hard limit, it's possible that we may exceed this value by a small
     * amount (we never stop in the middle of writing an event). It's also possible that we may create files that are
     * smaller than this limit.
     */
    private final int preferredFileSizeMegabytes;

    /**
     * When creating a new file, make sure that it has at least this much generational capacity for events after the
     * first event written to the file.
     */
    private final int minimumGenerationalCapacity;

    /**
     * The minimum generation that we are required to keep around.
     */
    private long minimumGenerationToStore;

    /**
     * A running average of the generational span utilization in each file. Generational span utilization is defined as
     * the difference between the highest generation of all events in the file and the minimum legal generation for that
     * file. Higher generational utilization is always better, as it means that we have a lower un-utilized generational
     * span. Un-utilized generational span is defined as the difference between the highest legal generation in a file
     * and the highest actual generation of all events in the file. The reason why we want to minimize un-utilized
     * generational span is to reduce the generational overlap between files, which in turn makes it faster to search
     * for events with particular generations. The purpose of this running average is to intelligently choose the
     * maximum generation for each new file to minimize un-utilized generational span while still meeting file size
     * requirements.
     */
    private final LongRunningAverage averageGenerationalSpanUtilization;

    /**
     * Use this value as a stand-in for the running average if we haven't yet collected any data for the running
     * average.
     */
    private final int bootstrapGenerationalSpan;

    /**
     * Multiply this value by the running average when deciding the generation span for a new file (i.e. the difference
     * between the maximum and the minimum legal generation).
     */
    private final double generationalSpanOverlapFactor;

    /**
     * The highest event sequence number that has been written to the stream (but possibly not yet flushed).
     */
    private long lastWrittenEvent = -1;

    /**
     * The highest event sequence number that has been flushed.
     */
    private final CountUpLatch lastFlushedEvent = new CountUpLatch(-1);

    /**
     * Events that should be flushed ASAP.
     */
    private final PriorityQueue<Long> flushableEvents = new PriorityQueue<>();

    /**
     * Create a new PreConsensusEventWriter.
     *
     * @param config      configuration for preconsensus event streams
     * @param fileManager manages all preconsensus event stream files currently on disk
     */
    public SyncPreConsensusEventWriter(
            final PreConsensusEventStreamConfig config, final PreConsensusEventFileManager fileManager) {

        preferredFileSizeMegabytes = config.preferredFileSizeMegabytes();

        averageGenerationalSpanUtilization =
                new LongRunningAverage(config.generationalUtilizationSpanRunningAverageLength());
        bootstrapGenerationalSpan = config.bootstrapGenerationalSpan();
        generationalSpanOverlapFactor = config.generationalSpanOverlapFactor();
        minimumGenerationalCapacity = config.minimumGenerationalCapacity();

        this.fileManager = fileManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void writeEvent(final EventImpl event) {
        validateSequenceNumber(event);
        if (event.getGeneration() >= minimumGenerationNonAncient) {
            writeEventToStream(event);
            flushIfNeeded();
        } else {
            event.setStreamSequenceNumber(EventImpl.STALE_EVENT_STREAM_SEQUENCE_NUMBER);
        }
    }

    /**
     * Make sure that the event has a valid stream sequence number.
     */
    private static void validateSequenceNumber(final EventImpl event) {
        if (event.getStreamSequenceNumber() == EventImpl.NO_STREAM_SEQUENCE_NUMBER
                || event.getStreamSequenceNumber() == EventImpl.STALE_EVENT_STREAM_SEQUENCE_NUMBER) {
            throw new IllegalStateException("Event must have a valid stream sequence number");
        }
    }

    /**
     * Flush the stream if needed. Should be called after each event is written.
     */
    private void flushIfNeeded() {
        // Remove all events that have already been flushed. This scenario is relevant after a file is closed.
        while (!flushableEvents.isEmpty() && flushableEvents.peek() < lastWrittenEvent) {
            flushableEvents.remove();
        }
        if (!flushableEvents.isEmpty()) {
            final long nextFlushableEvent = flushableEvents.peek();
            if (nextFlushableEvent == lastWrittenEvent) {
                try {
                    currentMutableFile.flush();
                } catch (final IOException e) {
                    throw new UncheckedIOException("unable to flush", e);
                }
                markEventsAsFlushed();
                flushableEvents.remove();
            }
        }
    }

    /**
     * Write an event to the file stream.
     *
     * @param event the event to write
     */
    private void writeEventToStream(final EventImpl event) {
        try {
            prepareOutputStream(event);
            currentMutableFile.writeEvent(event);
            lastWrittenEvent = event.getStreamSequenceNumber();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        if (minimumGenerationNonAncient < this.minimumGenerationNonAncient) {
            throw new IllegalArgumentException("Minimum generation non-ancient cannot be decreased. Current = "
                    + this.minimumGenerationNonAncient + ", requested = " + minimumGenerationNonAncient);
        }

        this.minimumGenerationNonAncient = minimumGenerationNonAncient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void setMinimumGenerationToStore(final long minimumGenerationToStore) {
        this.minimumGenerationToStore = minimumGenerationToStore;
        pruneOldFiles();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean isEventDurable(final EventImpl event) {
        if (event.getStreamSequenceNumber() == EventImpl.STALE_EVENT_STREAM_SEQUENCE_NUMBER) {
            // Stale events are not written to disk.
            return false;
        }
        return event.getStreamSequenceNumber() <= lastFlushedEvent.getCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitUntilDurable(final EventImpl event) throws InterruptedException {
        if (event.getStreamSequenceNumber() == EventImpl.STALE_EVENT_STREAM_SEQUENCE_NUMBER) {
            throw new IllegalStateException("Event is stale and will never be durable");
        }

        lastFlushedEvent.await(event.getStreamSequenceNumber());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitUntilDurable(final EventImpl event, final Duration timeToWait) throws InterruptedException {
        if (event.getStreamSequenceNumber() == EventImpl.STALE_EVENT_STREAM_SEQUENCE_NUMBER) {
            throw new IllegalStateException("Event is stale and will never be durable");
        }

        return lastFlushedEvent.await(event.getStreamSequenceNumber(), timeToWait);
    }

    /**
     * Delete old files from the disk.
     */
    private void pruneOldFiles() {
        try {
            fileManager.pruneOldFiles(minimumGenerationToStore);
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to prune old files", e);
        }
    }

    /**
     * Mark all unflushed events as durable.
     */
    private void markEventsAsFlushed() {
        lastFlushedEvent.set(lastWrittenEvent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void requestFlush(final EventImpl event) {
        final long eventSequenceNumber = event.getStreamSequenceNumber();
        if (eventSequenceNumber == EventImpl.STALE_EVENT_STREAM_SEQUENCE_NUMBER) {
            // Stale events are not written to disk.
            throw new IllegalStateException("Event is stale and will never be written to disk");
        }

        if (lastFlushedEvent.getCount() >= eventSequenceNumber) {
            // The event has already been flushed.
            return;
        }

        if (lastWrittenEvent < eventSequenceNumber) {
            // We haven't yet written this event, event will be flushed as soon as it is written.
            flushableEvents.add(eventSequenceNumber);
            return;
        }

        // We have written the event to the stream, flush immediately
        try {
            currentMutableFile.flush();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        markEventsAsFlushed();
    }

    /**
     * Close the output file.
     */
    private void closeFile() {
        try {
            averageGenerationalSpanUtilization.add(currentMutableFile.getUtilizedGenerationalSpan());
            currentMutableFile.close();

            // Future work: "compactify" file name here

            fileManager.finishedWritingFile(currentMutableFile);
            markEventsAsFlushed();
            currentMutableFile = null;

            // Not strictly required here, but not a bad place to ensure we delete
            // files incrementally (as opposed to deleting a bunch of files all at once).
            pruneOldFiles();
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to prune files", e);
        }
    }

    /**
     * Calculate the generation span for a new file that is about to be created.
     */
    private long computeNewFileSpan(final long minimumFileGeneration, final long nextGenerationToWrite) {
        if (averageGenerationalSpanUtilization.isEmpty()) {
            return bootstrapGenerationalSpan;
        }

        final long desiredSpan =
                (long) (averageGenerationalSpanUtilization.getAverage() * generationalSpanOverlapFactor);

        final long minimumSpan = (nextGenerationToWrite + minimumGenerationalCapacity) - minimumFileGeneration;

        return Math.max(desiredSpan, minimumSpan);
    }

    /**
     * Prepare the output stream for a particular event. May create a new file/stream if needed.
     *
     * @param eventToWrite the event that is about to be written
     */
    private void prepareOutputStream(final EventImpl eventToWrite) throws IOException {
        if (currentMutableFile != null
                && (!currentMutableFile.canContain(eventToWrite)
                        || currentMutableFile.fileSize() * Units.BYTES_TO_MEBIBYTES >= preferredFileSizeMegabytes)) {
            closeFile();
        }

        if (currentMutableFile == null) {
            final long maximumGeneration = minimumGenerationNonAncient
                    + computeNewFileSpan(minimumGenerationNonAncient, eventToWrite.getGeneration());

            currentMutableFile = fileManager
                    .getNextFileDescriptor(minimumGenerationNonAncient, maximumGeneration)
                    .getMutableFile();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        // no work needed
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void stop() {
        if (currentMutableFile != null) {
            try {
                currentMutableFile.close();
                markEventsAsFlushed();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
