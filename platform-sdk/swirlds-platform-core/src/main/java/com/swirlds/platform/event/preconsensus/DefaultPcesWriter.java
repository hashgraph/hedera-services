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
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.eventhandling.EventConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This object is responsible for writing events to the database.
 */
public class DefaultPcesWriter implements PcesWriter {

    private static final Logger logger = LogManager.getLogger(DefaultPcesWriter.class);

    /**
     * Keeps track of the event stream files on disk.
     */
    private final PcesFileManager fileManager;

    /**
     * The current file that is being written to.
     */
    private PcesMutableFile currentMutableFile;

    /**
     * The current minimum ancient indicator required to be considered non-ancient. Only read and written on the handle
     * thread. Either a round or generation depending on the {@link AncientMode}.
     */
    private long nonAncientBoundary = 0;

    /**
     * The desired file size, in megabytes. Is not a hard limit, it's possible that we may exceed this value by a small
     * amount (we never stop in the middle of writing an event). It's also possible that we may create files that are
     * smaller than this limit.
     */
    private final int preferredFileSizeMegabytes;

    /**
     * When creating a new file, make sure that it has at least this much capacity between the upper bound and lower
     * bound for events after the first event written to the file.
     */
    private final int minimumSpan;

    /**
     * The minimum ancient indicator that we are required to keep around. Will be either a birth round or a generation,
     * depending on the {@link AncientMode}.
     */
    private long minimumAncientIdentifierToStore;

    /**
     * A running average of the span utilization in each file. Span utilization is defined as the difference between the
     * highest ancient indicator of all events in the file and the minimum legal ancient indicator for that file.
     * Higher utilization is always better, as it means that we have a lower un-utilized span. Un-utilized span is
     * defined as the difference between the highest legal ancient indicator in a file and the highest actual ancient
     * identifier of all events in the file. The reason why we want to minimize un-utilized span is to reduce the
     * overlap between files, which in turn makes it faster to search for events with particular ancient indicator. The
     * purpose of this running average is to intelligently choose upper bound for each new file to minimize un-utilized
     * span while still meeting file size requirements.
     */
    private final LongRunningAverage averageSpanUtilization;

    /**
     * The previous span. Set to a constant at bootstrap time.
     */
    private long previousSpan;

    /**
     * If true then use {@link #bootstrapSpanOverlapFactor} to compute the upper bound new files. If false then use
     * {@link #spanOverlapFactor} to compute the upper bound for new files. Bootstrap mode is used until we create the
     * first file that exceeds the preferred file size.
     */
    private boolean bootstrapMode = true;

    /**
     * During bootstrap mode, multiply this value by the running average when deciding the upper bound for a new file
     * (i.e. the difference between the maximum and the minimum legal ancient indicator).
     */
    private final double bootstrapSpanOverlapFactor;

    /**
     * When not in boostrap mode, multiply this value by the running average when deciding the span for a new file (i.e.
     * the difference between the maximum and the minimum legal ancient indicator).
     */
    private final double spanOverlapFactor;

    /**
     * The highest event sequence number that has been written to the stream (but possibly not yet flushed).
     */
    private long lastWrittenEvent = -1;

    /**
     * The highest event sequence number that has been durably flushed to disk.
     */
    private long lastFlushedEvent = -1;

    /**
     * If true then all added events are new and need to be written to the stream. If false then all added events are
     * already durable and do not need to be written to the stream.
     */
    private boolean streamingNewEvents = false;

    /**
     * The type of the PCES file. There are currently two types: one bound by generations and one bound by birth rounds.
     * The original type of files are bound by generations. The new type of files are bound by birth rounds. Once
     * migration has been completed to birth round bound files, support for the generation bound files will be removed.
     */
    private final AncientMode fileType;

    /**
     * A collection of outstanding flush requests
     * <p>
     * Each flush request is a sequence number that needs to be flushed to disk as soon as possible.
     */
    private final Deque<Long> flushRequests = new ArrayDeque<>();

    /**
     * Constructor
     *
     * @param platformContext the platform context
     * @param fileManager     manages all preconsensus event stream files currently on disk
     */
    public DefaultPcesWriter(
            @NonNull final PlatformContext platformContext, @NonNull final PcesFileManager fileManager) {
        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(fileManager);

        final PcesConfig config = platformContext.getConfiguration().getConfigData(PcesConfig.class);

        preferredFileSizeMegabytes = config.preferredFileSizeMegabytes();
        averageSpanUtilization = new LongRunningAverage(config.spanUtilizationRunningAverageLength());
        previousSpan = config.bootstrapSpan();
        bootstrapSpanOverlapFactor = config.bootstrapSpanOverlapFactor();
        spanOverlapFactor = config.spanOverlapFactor();
        minimumSpan = config.minimumSpan();

        this.fileManager = fileManager;

        fileType = platformContext
                        .getConfiguration()
                        .getConfigData(EventConfig.class)
                        .useBirthRoundAncientThreshold()
                ? AncientMode.BIRTH_ROUND_THRESHOLD
                : AncientMode.GENERATION_THRESHOLD;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beginStreamingNewEvents() {
        if (streamingNewEvents) {
            logger.error(EXCEPTION.getMarker(), "beginStreamingNewEvents() called while already streaming new events");
        }
        streamingNewEvents = true;
    }

    /**
     * Consider outstanding flush requests and perform a flush if needed.
     *
     * @return true if a flush was performed, otherwise false
     */
    private boolean processFlushRequests() {
        boolean flushRequired = false;
        while (!flushRequests.isEmpty() && flushRequests.peekFirst() <= lastWrittenEvent) {
            final long flushRequest = flushRequests.removeFirst();

            if (flushRequest > lastFlushedEvent) {
                flushRequired = true;
            }
        }

        if (flushRequired) {
            if (currentMutableFile == null) {
                logger.error(EXCEPTION.getMarker(), "Flush required, but no file is open. This should never happen");
            }

            try {
                currentMutableFile.flush();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }

            lastFlushedEvent = lastWrittenEvent;
        }

        return flushRequired;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Long writeEvent(@NonNull final PlatformEvent event) {
        if (event.getStreamSequenceNumber() == PlatformEvent.NO_STREAM_SEQUENCE_NUMBER) {
            throw new IllegalStateException("Event must have a valid stream sequence number");
        }

        // if we aren't streaming new events yet, assume that the given event is already durable
        if (!streamingNewEvents) {
            lastWrittenEvent = event.getStreamSequenceNumber();
            lastFlushedEvent = event.getStreamSequenceNumber();
            return event.getStreamSequenceNumber();
        }

        // don't do anything with ancient events
        if (event.getAncientIndicator(fileType) < nonAncientBoundary) {
            return null;
        }

        try {
            final boolean fileClosed = prepareOutputStream(event);
            currentMutableFile.writeEvent(event);
            lastWrittenEvent = event.getStreamSequenceNumber();

            final boolean flushPerformed = processFlushRequests();

            return fileClosed || flushPerformed ? lastFlushedEvent : null;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Long registerDiscontinuity(@NonNull final Long newOriginRound) {
        if (!streamingNewEvents) {
            logger.error(EXCEPTION.getMarker(), "registerDiscontinuity() called while replaying events");
        }

        try {
            if (currentMutableFile != null) {
                closeFile();
                return lastFlushedEvent;
            } else {
                return null;
            }
        } finally {
            fileManager.registerDiscontinuity(newOriginRound);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Long submitFlushRequest(@NonNull final Long sequenceNumber) {
        flushRequests.add(sequenceNumber);

        return processFlushRequests() ? lastFlushedEvent : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateNonAncientEventBoundary(@NonNull final EventWindow nonAncientBoundary) {
        if (nonAncientBoundary.getAncientThreshold() < this.nonAncientBoundary) {
            throw new IllegalArgumentException("Non-ancient boundary cannot be decreased. Current = "
                    + this.nonAncientBoundary + ", requested = " + nonAncientBoundary);
        }

        this.nonAncientBoundary = nonAncientBoundary.getAncientThreshold();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMinimumAncientIdentifierToStore(@NonNull final Long minimumAncientIdentifierToStore) {
        this.minimumAncientIdentifierToStore = minimumAncientIdentifierToStore;
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
            fileManager.pruneOldFiles(minimumAncientIdentifierToStore);
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
            previousSpan = currentMutableFile.getUtilizedSpan();
            if (!bootstrapMode) {
                averageSpanUtilization.add(previousSpan);
            }
            currentMutableFile.close();
            lastFlushedEvent = lastWrittenEvent;

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
     * Calculate the span for a new file that is about to be created.
     *
     * @param minimumLowerBound            the minimum lower bound that is legal to use for the new file
     * @param nextAncientIdentifierToWrite the ancient indicator of the next event that will be written
     */
    private long computeNewFileSpan(final long minimumLowerBound, final long nextAncientIdentifierToWrite) {

        final long basisSpan = (bootstrapMode || averageSpanUtilization.isEmpty())
                ? previousSpan
                : averageSpanUtilization.getAverage();

        final double overlapFactor = bootstrapMode ? bootstrapSpanOverlapFactor : spanOverlapFactor;

        final long desiredSpan = (long) (basisSpan * overlapFactor);

        final long minimumSpan = (nextAncientIdentifierToWrite + this.minimumSpan) - minimumLowerBound;

        return Math.max(desiredSpan, minimumSpan);
    }

    /**
     * Prepare the output stream for a particular event. May create a new file/stream if needed.
     *
     * @param eventToWrite the event that is about to be written
     * @return true if this method call resulted in the current file being closed
     */
    private boolean prepareOutputStream(@NonNull final PlatformEvent eventToWrite) throws IOException {
        boolean fileClosed = false;
        if (currentMutableFile != null) {
            final boolean fileCanContainEvent =
                    currentMutableFile.canContain(eventToWrite.getAncientIndicator(fileType));
            final boolean fileIsFull =
                    UNIT_BYTES.convertTo(currentMutableFile.fileSize(), UNIT_MEGABYTES) >= preferredFileSizeMegabytes;

            if (!fileCanContainEvent || fileIsFull) {
                closeFile();
                fileClosed = true;
            }

            if (fileIsFull) {
                bootstrapMode = false;
            }
        }

        // if the block above closed the file, then we need to create a new one
        if (currentMutableFile == null) {
            final long upperBound = nonAncientBoundary
                    + computeNewFileSpan(nonAncientBoundary, eventToWrite.getAncientIndicator(fileType));

            currentMutableFile = fileManager
                    .getNextFileDescriptor(nonAncientBoundary, upperBound)
                    .getMutableFile();
        }

        return fileClosed;
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
