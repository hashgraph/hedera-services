/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.units.DataUnit.UNIT_BYTES;
import static com.swirlds.common.units.DataUnit.UNIT_MEGABYTES;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.LongRunningAverage;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.wiring.DoneStreamingPcesTrigger;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This object is responsible for writing events to the database.
 */
public class PcesWriter {

    private static final Logger logger = LogManager.getLogger(PcesWriter.class);

    /**
     * Keeps track of the event stream files on disk.
     */
    private final PcesFileManager fileManager;

    /**
     * The current file that is being written to.
     */
    private PcesMutableFile currentMutableFile;

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
     * The previous generational span. Set to a constant at bootstrap time.
     */
    private long previousGenerationalSpan;

    /**
     * If true then use {@link #bootstrapGenerationalSpanOverlapFactor} to compute the maximum generation for new files.
     * If false then use {@link #generationalSpanOverlapFactor} to compute the maximum generation for new files.
     * Bootstrap mode is used until we create the first file that exceeds the preferred file size.
     */
    private boolean bootstrapMode = true;

    /**
     * During bootstrap mode, multiply this value by the running average when deciding the generation span for a new
     * file (i.e. the difference between the maximum and the minimum legal generation).
     */
    private final double bootstrapGenerationalSpanOverlapFactor;

    /**
     * When not in boostrap mode, multiply this value by the running average when deciding the generation span for a new
     * file (i.e. the difference between the maximum and the minimum legal generation).
     */
    private final double generationalSpanOverlapFactor;

    /**
     * The highest event sequence number that has been written to the stream (but possibly not yet flushed).
     */
    private long lastWrittenEvent = -1;

    /**
     * If true then all added events are new and need to be written to the stream. If false then all added events
     * are already durable and do not need to be written to the stream.
     */
    private boolean streamingNewEvents = false;

    /**
     * Constructor
     *
     * @param platformContext the platform context
     * @param fileManager     manages all preconsensus event stream files currently on disk
     */
    public PcesWriter(@NonNull final PlatformContext platformContext, @NonNull final PcesFileManager fileManager) {

        Objects.requireNonNull(platformContext, "platformContext must not be null");
        Objects.requireNonNull(fileManager, "fileManager must not be null");

        final PcesConfig config = platformContext.getConfiguration().getConfigData(PcesConfig.class);

        preferredFileSizeMegabytes = config.preferredFileSizeMegabytes();

        averageGenerationalSpanUtilization =
                new LongRunningAverage(config.generationalUtilizationSpanRunningAverageLength());
        previousGenerationalSpan = config.bootstrapGenerationalSpan();
        bootstrapGenerationalSpanOverlapFactor = config.bootstrapGenerationalSpanOverlapFactor();
        generationalSpanOverlapFactor = config.generationalSpanOverlapFactor();
        minimumGenerationalCapacity = config.minimumGenerationalCapacity();

        this.fileManager = fileManager;
    }

    /**
     * Prior to this method being called, all events added to the preconsensus event stream are assumed to be events
     * read from the preconsensus event stream on disk. The events from the stream on disk are not re-written to the
     * disk, and are considered to be durable immediately upon ingest.
     *
     * @param ignored empty trigger object, to indicate that events are done being streamed
     */
    public void beginStreamingNewEvents(final @NonNull DoneStreamingPcesTrigger ignored) {
        if (streamingNewEvents) {
            logger.error(EXCEPTION.getMarker(), "beginStreamingNewEvents() called while already streaming new events");
        }
        streamingNewEvents = true;
    }

    /**
     * Write an event to the stream.
     *
     * @param event the event to be written
     * @return the sequence number of the last event durably written to the stream, or null if this method call didn't
     * result in any additional events being durably written to the stream
     */
    @Nullable
    public Long writeEvent(@NonNull final GossipEvent event) {
        validateSequenceNumber(event);

        if (!streamingNewEvents) {
            lastWrittenEvent = event.getStreamSequenceNumber();
            return lastWrittenEvent;
        }

        if (event.getGeneration() < minimumGenerationNonAncient) {
            event.setStreamSequenceNumber(GossipEvent.STALE_EVENT_STREAM_SEQUENCE_NUMBER);
            return null;
        }

        try {
            final Long latestDurableSequenceNumberUpdate = prepareOutputStream(event);
            currentMutableFile.writeEvent(event);
            lastWrittenEvent = event.getStreamSequenceNumber();

            return latestDurableSequenceNumberUpdate;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Inform the preconsensus event writer that a discontinuity has occurred in the preconsensus event stream.
     *
     * @param newOriginRound the round of the state that the new stream will be starting from
     * @return the sequence number of the last event durably written to the stream, or null if this method call didn't
     * result in any additional events being durably written to the stream
     */
    @Nullable
    public Long registerDiscontinuity(final long newOriginRound) {
        if (!streamingNewEvents) {
            logger.error(EXCEPTION.getMarker(), "registerDiscontinuity() called while replaying events");
        }

        final Long latestDurableSequenceNumberUpdate;
        if (currentMutableFile != null) {
            closeFile();
            latestDurableSequenceNumberUpdate = lastWrittenEvent;
        } else {
            latestDurableSequenceNumberUpdate = null;
        }

        fileManager.registerDiscontinuity(newOriginRound);

        return latestDurableSequenceNumberUpdate;
    }

    /**
     * Make sure that the event has a valid stream sequence number.
     */
    private static void validateSequenceNumber(@NonNull final GossipEvent event) {
        if (event.getStreamSequenceNumber() == GossipEvent.NO_STREAM_SEQUENCE_NUMBER
                || event.getStreamSequenceNumber() == GossipEvent.STALE_EVENT_STREAM_SEQUENCE_NUMBER) {
            throw new IllegalStateException("Event must have a valid stream sequence number");
        }
    }

    /**
     * Let the event writer know the minimum generation for non-ancient events. Ancient events will be ignored if added
     * to the event writer.
     *
     * @param minimumGenerationNonAncient the minimum generation of a non-ancient event
     * @return the sequence number of the last event durably written to the stream if this method call resulted in any
     * additional events being durably written to the stream, otherwise null
     */
    @Nullable
    public Long setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        if (minimumGenerationNonAncient < this.minimumGenerationNonAncient) {
            throw new IllegalArgumentException("Minimum generation non-ancient cannot be decreased. Current = "
                    + this.minimumGenerationNonAncient + ", requested = " + minimumGenerationNonAncient);
        }

        this.minimumGenerationNonAncient = minimumGenerationNonAncient;

        if (!streamingNewEvents || currentMutableFile == null) {
            return null;
        }

        try {
            currentMutableFile.flush();
            return lastWrittenEvent;
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to flush", e);
        }
    }

    /**
     * Set the minimum generation needed to be kept on disk.
     *
     * @param minimumGenerationToStore the minimum generation required to be stored on disk
     */
    public void setMinimumGenerationToStore(final long minimumGenerationToStore) {
        this.minimumGenerationToStore = minimumGenerationToStore;
        pruneOldFiles();
    }

    /**
     * Delete old files from the disk.
     */
    private void pruneOldFiles() {
        if (!streamingNewEvents) {
            // Don't attempt to prune files until we are done replaying the event stream (at start up).
            // Files are being iterated on a different thread, and it isn't thread safe to prune files
            // while they are being iterated.
            return;
        }

        try {
            fileManager.pruneOldFiles(minimumGenerationToStore);
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to prune old files", e);
        }
    }

    /**
     * Close the output file.
     * <p>
     * Should only be called if {@link #currentMutableFile} is not null.
     */
    private void closeFile() {
        try {
            previousGenerationalSpan = currentMutableFile.getUtilizedGenerationalSpan();
            if (!bootstrapMode) {
                averageGenerationalSpanUtilization.add(previousGenerationalSpan);
            }
            currentMutableFile.close();

            fileManager.finishedWritingFile(currentMutableFile);
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

        final long basisSpan = (bootstrapMode || averageGenerationalSpanUtilization.isEmpty())
                ? previousGenerationalSpan
                : averageGenerationalSpanUtilization.getAverage();

        final double overlapFactor =
                bootstrapMode ? bootstrapGenerationalSpanOverlapFactor : generationalSpanOverlapFactor;

        final long desiredSpan = (long) (basisSpan * overlapFactor);

        final long minimumSpan = (nextGenerationToWrite + minimumGenerationalCapacity) - minimumFileGeneration;

        return Math.max(desiredSpan, minimumSpan);
    }

    /**
     * Prepare the output stream for a particular event. May create a new file/stream if needed.
     *
     * @param eventToWrite the event that is about to be written
     * @return the latest sequence number durably written to disk, if preparing the output stream caused a file to be
     * closed. Otherwise, null.
     */
    private Long prepareOutputStream(@NonNull final GossipEvent eventToWrite) throws IOException {
        Long latestDurableSequenceNumberUpdate = null;
        if (currentMutableFile != null) {
            final boolean fileCanContainEvent = currentMutableFile.canContain(eventToWrite.getGeneration());
            final boolean fileIsFull =
                    UNIT_BYTES.convertTo(currentMutableFile.fileSize(), UNIT_MEGABYTES) >= preferredFileSizeMegabytes;

            if (!fileCanContainEvent || fileIsFull) {
                closeFile();
                latestDurableSequenceNumberUpdate = lastWrittenEvent;
            }

            if (fileIsFull) {
                bootstrapMode = false;
            }
        }

        if (currentMutableFile == null) {
            final long maximumGeneration = minimumGenerationNonAncient
                    + computeNewFileSpan(minimumGenerationNonAncient, eventToWrite.getGeneration());

            currentMutableFile = fileManager
                    .getNextFileDescriptor(minimumGenerationNonAncient, maximumGeneration)
                    .getMutableFile();
        }

        return latestDurableSequenceNumberUpdate;
    }

    /**
     * Close the current mutable file.
     */
    public void closeCurrentMutableFile() {
        if (currentMutableFile != null) {
            try {
                currentMutableFile.close();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
