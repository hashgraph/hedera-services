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

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.RandomAccessDeque;
import com.swirlds.common.utility.Units;
import com.swirlds.common.utility.ValueReference;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * This object tracks a group of event files.
 * </p>
 *
 * <p>
 * This object is not thread safe.
 * </p>
 */
public class PreconsensusEventFileManager {

    private static final Logger logger = LogManager.getLogger(PreconsensusEventFileManager.class);

    /**
     * This constant can be used when the caller wants all events, regardless of generation.
     */
    public static final long NO_MINIMUM_GENERATION = Long.MIN_VALUE;

    /**
     * The initial size of the ring buffer used to track event files.
     */
    private static final int INITIAL_RING_BUFFER_SIZE = 1024;

    /**
     * Provides the wall clock time.
     */
    private final Time time;

    private final PreconsensusEventMetrics metrics;

    /**
     * The root directory where event files are stored.
     */
    private final Path databaseDirectory;

    /**
     * The location where we will move files that are invalid due to a discontinuity. Better than deleting the files, in
     * case the files end up being useful for debugging.
     */
    private final Path recycleBinDirectory;

    /**
     * Tracks all files currently on disk.
     */
    private final RandomAccessDeque<PreconsensusEventFile> files;

    /**
     * The minimum amount of time that must pass before a file becomes eligible for deletion.
     */
    private final Duration minimumRetentionPeriod;

    /**
     * The size of all tracked files, in bytes.
     */
    private long totalFileByteCount = 0;

    /**
     * Instantiate an event file collection. Loads all event files in the specified directory.
     *
     * @param platformContext the platform context for this node
     * @param time            provides wall clock time
     * @param selfId          the ID of this node
     */
    public PreconsensusEventFileManager(
            @NonNull final PlatformContext platformContext, @NonNull final Time time, @NonNull final NodeId selfId)
            throws IOException {

        Objects.requireNonNull(platformContext, "platformContext");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(selfId, "selfId");

        final PreconsensusEventStreamConfig preconsensusEventStreamConfig =
                platformContext.getConfiguration().getConfigData(PreconsensusEventStreamConfig.class);
        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);

        this.time = time;
        this.metrics = new PreconsensusEventMetrics(platformContext.getMetrics());

        minimumRetentionPeriod = preconsensusEventStreamConfig.minimumRetentionPeriod();

        final Path savedStateDirectory = stateConfig.savedStateDirectory();

        this.databaseDirectory = savedStateDirectory
                .resolve(preconsensusEventStreamConfig.databaseDirectory())
                .resolve(Long.toString(selfId.id()));

        this.recycleBinDirectory = savedStateDirectory.resolve(preconsensusEventStreamConfig.recycleBinDirectory());

        if (!Files.exists(databaseDirectory)) {
            Files.createDirectories(databaseDirectory);
        }

        files = new RandomAccessDeque<>(INITIAL_RING_BUFFER_SIZE);

        try (final Stream<Path> fileStream = Files.walk(databaseDirectory)) {
            fileStream
                    .filter(f -> !Files.isDirectory(f))
                    .map(PreconsensusEventFileManager::parseFile)
                    .filter(Objects::nonNull)
                    .sorted()
                    .forEachOrdered(buildFileHandler(preconsensusEventStreamConfig.permitGaps()));
        }

        // Measure the size of each file.
        for (final PreconsensusEventFile file : files) {
            totalFileByteCount += Files.size(file.getPath());
        }

        if (files.size() > 0) {
            metrics.getPreconsensusEventFileOldestGeneration()
                    .set(files.getFirst().getMinimumGeneration());
            metrics.getPreconsensusEventFileYoungestGeneration()
                    .set(files.getLast().getMaximumGeneration());
            final Duration age = Duration.between(files.getFirst().getTimestamp(), time.now());
            metrics.getPreconsensusEventFileOldestSeconds().set(age.toSeconds());
        } else {
            metrics.getPreconsensusEventFileOldestGeneration().set(NO_MINIMUM_GENERATION);
            metrics.getPreconsensusEventFileYoungestGeneration().set(NO_MINIMUM_GENERATION);
            metrics.getPreconsensusEventFileOldestSeconds().set(0);
        }
        updateFileSizeMetrics();
    }

    /**
     * Parse a file into a PreConsensusEventFile wrapper object.
     *
     * @param path the path to the file
     * @return the wrapper object, or null if the file can't be parsed
     */
    private static @Nullable PreconsensusEventFile parseFile(@NonNull final Path path) {
        try {
            return PreconsensusEventFile.of(path);
        } catch (final IOException exception) {
            // ignore any file that can't be parsed
            logger.warn(EXCEPTION.getMarker(), "Failed to parse file: {}", path, exception);
            return null;
        }
    }

    /**
     * Build a handler for new files parsed from disk. Does basic sanity checks on the files, and adds them to the file
     * list if they are valid.
     *
     * @param permitGaps if gaps are permitted in sequence number
     * @return the handler
     */
    private @NonNull Consumer<PreconsensusEventFile> buildFileHandler(final boolean permitGaps) {

        final ValueReference<Long> previousSequenceNumber = new ValueReference<>(-1L);
        final ValueReference<Long> previousMinimumGeneration = new ValueReference<>(-1L);
        final ValueReference<Long> previousMaximumGeneration = new ValueReference<>(-1L);
        final ValueReference<Instant> previousTimestamp = new ValueReference<>();

        return descriptor -> {
            if (previousSequenceNumber.getValue() != -1) {
                fileSanityChecks(
                        permitGaps,
                        previousSequenceNumber.getValue(),
                        previousMinimumGeneration.getValue(),
                        previousMaximumGeneration.getValue(),
                        previousTimestamp.getValue(),
                        descriptor);
            }

            previousSequenceNumber.setValue(descriptor.getSequenceNumber());
            previousMinimumGeneration.setValue(descriptor.getMinimumGeneration());
            previousMaximumGeneration.setValue(descriptor.getMaximumGeneration());
            previousTimestamp.setValue(descriptor.getTimestamp());

            // If the sequence number is good then add it to the collection of tracked files
            files.addLast(descriptor);
        };
    }

    /**
     * Perform sanity checks on the properties of the next file in the sequence, to ensure that we maintain various
     * invariants.
     *
     * @param permitGaps                if gaps are permitted in sequence number
     * @param previousSequenceNumber    the sequence number of the previous file
     * @param previousMinimumGeneration the minimum generation of the previous file
     * @param previousMaximumGeneration the maximum generation of the previous file
     * @param previousTimestamp         the timestamp of the previous file
     * @param descriptor                the descriptor of the next file
     * @throws IllegalStateException if any of the required invariants are violated by the next file
     */
    private static void fileSanityChecks(
            final boolean permitGaps,
            final long previousSequenceNumber,
            final long previousMinimumGeneration,
            final long previousMaximumGeneration,
            @NonNull final Instant previousTimestamp,
            @NonNull final PreconsensusEventFile descriptor) {

        // Sequence number should always monotonically increase
        if (!permitGaps && previousSequenceNumber + 1 != descriptor.getSequenceNumber()) {
            throw new IllegalStateException("Gap in preconsensus event files detected! Previous sequence number was "
                    + previousSequenceNumber + ", next sequence number is "
                    + descriptor.getSequenceNumber());
        }

        // Sanity check on the minimum generation
        if (descriptor.getMinimumGeneration() < previousMinimumGeneration) {
            throw new IllegalStateException("Minimum generation must never decrease, file " + descriptor.getPath()
                    + " has a minimum generation that is less than the previous minimum generation of "
                    + previousMinimumGeneration);
        }

        // Sanity check on the maximum generation
        if (descriptor.getMaximumGeneration() < previousMaximumGeneration) {
            throw new IllegalStateException("Maximum generation must never decrease, file " + descriptor.getPath()
                    + " has a maximum generation that is less than the previous maximum generation of "
                    + previousMaximumGeneration);
        }

        // Sanity check on timestamp
        if (descriptor.getTimestamp().isBefore(previousTimestamp)) {
            throw new IllegalStateException("Timestamp must never decrease, file " + descriptor.getPath()
                    + " has a timestamp that is less than the previous timestamp of "
                    + previousTimestamp);
        }
    }

    /**
     * <p>
     * Get an iterator that walks over all event files currently being tracked, in order.
     * </p>
     *
     * <p>
     * Note: this iterator is not thread safe when events are actively being written. A future task will be to make it
     * thread safe. Until then, don't use this iterator while events are being written.
     * </p>
     *
     * @param minimumGeneration  the desired minimum generation, iterator is guaranteed to walk over all files that may
     *                           contain events with a generation greater or equal to this value. A value of
     *                           {@link #NO_MINIMUM_GENERATION} will cause the returned iterator to walk over all
     *                           available event files.
     * @param fixDiscontinuities if true, any discontinuities after the requested minimum generation will be "fixed" by
     *                           deleting all data following the first discontinuity.
     * @return an iterator that walks over event files in order
     */
    public @NonNull Iterator<PreconsensusEventFile> getFileIterator(
            final long minimumGeneration, final boolean fixDiscontinuities) {

        // Edge case: we want all events regardless of generation
        if (minimumGeneration == NO_MINIMUM_GENERATION) {
            if (fixDiscontinuities) {
                scanForDiscontinuities(0);
            }
            return files.iterator();
        }

        // Edge case: there are no files
        if (files.size() == 0) {
            logger.warn(STARTUP.getMarker(), "No preconsensus event files available");
            return Collections.emptyIterator();
        }

        // Edge case: our first file comes after the requested starting generation
        if (files.getFirst().getMinimumGeneration() >= minimumGeneration) {
            // Unless we observe at least one file with a minimum generation less than the requested minimum,
            // then we can't know for certain that we have all data for the requested minimum generation.
            logger.warn(
                    STARTUP.getMarker(),
                    "The preconsensus event stream has insufficient data to guarantee that all events with the "
                            + "requested generation of {} are present, the first file has a minimum generation of {}",
                    minimumGeneration,
                    files.getFirst().getMinimumGeneration());

            if (fixDiscontinuities) {
                scanForDiscontinuities(0);
            }
            return files.iterator();
        }

        // Edge case: all of our data comes before the requested starting generation
        if (files.getLast().getMaximumGeneration() < minimumGeneration) {
            logger.warn(
                    STARTUP.getMarker(),
                    "The preconsensus event stream has insufficient data to guarantee that "
                            + "all events with the requested minimum generation of {} are present, "
                            + "the last file has a maximum generation of {}",
                    minimumGeneration,
                    files.getLast().getMaximumGeneration());
            return Collections.emptyIterator();
        }

        // Standard case: we need to stream data starting from a file somewhere in the middle of stream
        final int fileCount = files.size();
        for (int index = 0; index < fileCount; index++) {
            final PreconsensusEventFile file = files.get(index);
            if (file.getMaximumGeneration() >= minimumGeneration) {
                // We have found the first file that may contain events at the requested generation.
                if (fixDiscontinuities) {
                    scanForDiscontinuities(index);
                }
                return files.iterator(index);
            }
        }

        // It should not be possible to reach this point.
        throw new IllegalStateException("Failed to find a file that may contain events at the requested generation");
    }

    /**
     * Scan the event files starting at a specified index. If discontinuities are found at or after this index, perform
     * necessary cleanup on the event stream.
     *
     * @param startingIndex the file index to start scanning at
     */
    private void scanForDiscontinuities(final int startingIndex) {
        final int fileCount = files.size();
        for (int index = startingIndex; index < fileCount; index++) {
            final PreconsensusEventFile file = files.get(index);
            if (file.marksDiscontinuity()) {
                // We have found a discontinuity, remove this and all following files.
                resolveDiscontinuity(index);
                return;
            }
        }
    }

    /**
     * Resolve a discontinuity at a specified index by deleting all files written after the discontinuity.
     *
     * @param indexOfDiscontinuity the file index of the discontinuity
     */
    private void resolveDiscontinuity(final int indexOfDiscontinuity) {

        if (files.size() == 0) {
            throw new IllegalStateException("The preconsensus event stream has no files, "
                    + "there should not be any detected discontinuities.");
        }

        if (indexOfDiscontinuity == 0) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Discontinuity detected at beginning of preconsensus event stream, "
                            + "unable to replay any events in the stream. "
                            + "All events in the stream will be deleted. "
                            + "first deleted file: {}, last file in stream: {}",
                    files.get(indexOfDiscontinuity),
                    files.getLast());
        } else {
            final PreconsensusEventFile lastUndeletedFile = files.get(indexOfDiscontinuity - 1);

            logger.error(
                    EXCEPTION.getMarker(),
                    "Discontinuity detected in preconsensus event stream, "
                            + "unable to replay all events in the stream. "
                            + "Events written to the stream after the discontinuity will be deleted. "
                            + "Last undeleted file: {}, first deleted file: {}, last file in stream: {}",
                    lastUndeletedFile,
                    files.get(indexOfDiscontinuity),
                    files.getLast());
        }

        try {
            Files.createDirectories(recycleBinDirectory);

            // Delete files in reverse order, so that if we crash prior to finishing at least
            // the stream does not have gaps in sequence numbers.
            for (int index = files.size() - 1; index >= indexOfDiscontinuity; index--) {
                files.removeLast().deleteFile(databaseDirectory, recycleBinDirectory);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to delete file after discontinuity", e);
        }

        if (files.size() > 0) {
            metrics.getPreconsensusEventFileOldestGeneration()
                    .set(files.getFirst().getMinimumGeneration());
        } else {
            metrics.getPreconsensusEventFileOldestGeneration().set(NO_MINIMUM_GENERATION);
            metrics.getPreconsensusEventFileYoungestGeneration().set(NO_MINIMUM_GENERATION);
            metrics.getPreconsensusEventFileOldestSeconds().set(0);
        }
    }

    /**
     * <p>
     * Get an iterator that walks over all events starting with a specified generation.
     * </p>
     *
     * <p>
     * Note: this iterator is not thread safe when events are actively being written. A future task will be to make it
     * thread safe. Until then, don't use this iterator while events are being written.
     * </p>
     *
     * @param minimumGeneration  the desired minimum generation, iterator is guaranteed to return all available events
     *                           with a generation greater or equal to this value. No events with a smaller generation
     *                           will be returned. A value of {@link #NO_MINIMUM_GENERATION} will cause the returned
     *                           iterator to walk over all available events.
     * @param fixDiscontinuities if true, any discontinuities after the requested minimum generation will be "fixed" by
     *                           deleting all data following the first discontinuity.
     * @return an iterator that walks over events
     */
    public @NonNull PreconsensusEventMultiFileIterator getEventIterator(
            final long minimumGeneration, final boolean fixDiscontinuities) {
        return new PreconsensusEventMultiFileIterator(
                minimumGeneration, getFileIterator(minimumGeneration, fixDiscontinuities));
    }

    /**
     * Get the sequence number that should be allocated next.
     *
     * @return the sequence number that should be allocated next
     */
    private long getNextSequenceNumber() {
        if (files.size() == 0) {
            return 0;
        }
        return files.getLast().getSequenceNumber() + 1;
    }

    /**
     * Create a new event file descriptor for the next event file, and start tracking it. (Note, this method doesn't
     * actually open the file, it just permits the file to be opened by the caller.)
     *
     * @param minimumGeneration the minimum generation that can be stored in the file
     * @param maximumGeneration the maximum generation that can be stored in the file
     * @return a new event file descriptor
     */
    public @NonNull PreconsensusEventFile getNextFileDescriptor(
            final long minimumGeneration, final long maximumGeneration) {
        return getNextFileDescriptor(minimumGeneration, maximumGeneration, false);
    }

    /**
     * Create a new event file descriptor for the next event file, and start tracking it. (Note, this method doesn't
     * actually open the file, it just permits the file to be opened by the caller.)
     *
     * @param minimumGeneration the minimum generation that can be stored in the file
     * @param maximumGeneration the maximum generation that can be stored in the file
     * @param discontinuity     true if the file is being created due to a discontinuity in the event stream
     * @return a new event file descriptor
     */
    public @NonNull PreconsensusEventFile getNextFileDescriptor(
            final long minimumGeneration, final long maximumGeneration, final boolean discontinuity) {

        if (minimumGeneration > maximumGeneration) {
            throw new IllegalArgumentException("minimum generation must be less than or equal to maximum generation");
        }

        final long minimumGenerationForFile;
        final long maximumGenerationForFile;

        if (files.size() == 0) {
            // This is the first file
            minimumGenerationForFile = minimumGeneration;
            maximumGenerationForFile = maximumGeneration;
        } else {
            // This is not the first file, min/max values are constrained to only increase
            minimumGenerationForFile =
                    Math.max(minimumGeneration, files.getLast().getMinimumGeneration());
            maximumGenerationForFile =
                    Math.max(maximumGeneration, files.getLast().getMaximumGeneration());
        }

        final PreconsensusEventFile descriptor = PreconsensusEventFile.of(
                getNextSequenceNumber(),
                minimumGenerationForFile,
                maximumGenerationForFile,
                time.now(),
                databaseDirectory,
                discontinuity);

        if (files.size() > 0) {
            // There are never enough sanity checks. This is the same sanity check that is run when we parse
            // the files from disk, so if it doesn't pass now it's not going to pass when we read the files.
            final PreconsensusEventFile previousFile = files.getLast();
            fileSanityChecks(
                    false,
                    previousFile.getSequenceNumber(),
                    previousFile.getMinimumGeneration(),
                    previousFile.getMaximumGeneration(),
                    previousFile.getTimestamp(),
                    descriptor);
        }

        files.addLast(descriptor);
        metrics.getPreconsensusEventFileYoungestGeneration().set(descriptor.getMaximumGeneration());

        return descriptor;
    }

    /**
     * The event file writer calls this method when it finishes writing an event file.
     *
     * @param file the file that has been completely written
     */
    public void finishedWritingFile(@NonNull final PreconsensusEventMutableFile file) {
        final long previousFileHighestGeneration;
        if (files.size() == 1) {
            previousFileHighestGeneration = 0;
        } else {
            previousFileHighestGeneration = files.get(files.size() - 2).getMaximumGeneration();
        }

        // Compress the generational span of the file. Reduces overlap between files.
        final PreconsensusEventFile compressedDescriptor = file.compressGenerationalSpan(previousFileHighestGeneration);
        files.set(files.size() - 1, compressedDescriptor);

        // Update metrics
        totalFileByteCount += file.fileSize();
        metrics.getPreconsensusEventFileRate().cycle();
        metrics.getPreconsensusEventAverageFileSpan().update(file.getGenerationalSpan());
        metrics.getPreconsensusEventAverageUnUtilizedFileSpan().update(file.getUnUtilizedGenerationalSpan());
        updateFileSizeMetrics();
    }

    /**
     * Prune old event files. Files are pruned if they are too old AND if they do not contain events with high enough
     * generations.
     *
     * @param minimumGeneration the minimum generation that we need to keep in the database. It's possible that this
     *                          operation won't delete all files with events older than this value, but this operation
     *                          is guaranteed not to delete any files that may contain events with a higher generation.
     */
    public void pruneOldFiles(final long minimumGeneration) throws IOException {
        final Instant minimumTimestamp = time.now().minus(minimumRetentionPeriod);

        while (files.size() > 0
                && files.getFirst().getMaximumGeneration() < minimumGeneration
                && files.getFirst().getTimestamp().isBefore(minimumTimestamp)) {

            final PreconsensusEventFile file = files.removeFirst();
            totalFileByteCount -= Files.size(file.getPath());
            file.deleteFile(databaseDirectory);
        }

        if (files.size() > 0) {
            metrics.getPreconsensusEventFileOldestGeneration()
                    .set(files.getFirst().getMinimumGeneration());
            final Duration age = Duration.between(files.getFirst().getTimestamp(), time.now());
            metrics.getPreconsensusEventFileOldestSeconds().set(age.toSeconds());
        }
        updateFileSizeMetrics();
    }

    /**
     * Update metrics with the latest data on file size.
     */
    private void updateFileSizeMetrics() {
        metrics.getPreconsensusEventFileCount().set(files.size());

        metrics.getPreconsensusEventFileTotalSizeGB().set(totalFileByteCount * Units.BYTES_TO_GIBIBYTES);

        if (files.size() > 0) {
            metrics.getPreconsensusEventFileAverageSizeMB()
                    .set(((double) totalFileByteCount) / files.size() * Units.BYTES_TO_MEBIBYTES);
        }
    }
}
