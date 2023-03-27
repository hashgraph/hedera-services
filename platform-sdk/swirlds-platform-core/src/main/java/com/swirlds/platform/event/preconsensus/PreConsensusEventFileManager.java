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

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.BinarySearch;
import com.swirlds.common.utility.RandomAccessDeque;
import com.swirlds.common.utility.Units;
import com.swirlds.common.utility.ValueReference;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongToIntFunction;
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
public class PreConsensusEventFileManager {

    private static final Logger logger = LogManager.getLogger(PreConsensusEventFileManager.class);

    /**
     * This constant can be used when the caller wants all events, regardless of generation.
     */
    public static final long NO_MINIMUM_GENERATION = Long.MIN_VALUE;

    /**
     * The initial size of the ring buffer used to track event files.
     */
    private static final int INITIAL_RING_BUFFER_SIZE = 1024 * 1024;

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
     * Tracks all files currently on disk.
     */
    private final RandomAccessDeque<PreConsensusEventFile> files;

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
     * @param time          provides wall clock time
     * @param selfId        the ID of this node
     */
    public PreConsensusEventFileManager(
            @NonNull final PlatformContext platformContext, @NonNull final Time time, final long selfId)
            throws IOException {

        throwArgNull(platformContext, "platformContext");
        throwArgNull(time, "time");

        final PreConsensusEventStreamConfig preConsensusEventStreamConfig =
                platformContext.getConfiguration().getConfigData(PreConsensusEventStreamConfig.class);
        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);

        this.time = time;
        this.metrics = new PreconsensusEventMetrics(platformContext.getMetrics());

        minimumRetentionPeriod = preConsensusEventStreamConfig.minimumRetentionPeriod();

        this.databaseDirectory = stateConfig
                .savedStateDirectory()
                .resolve(preConsensusEventStreamConfig.databaseDirectory())
                .resolve(Long.toString(selfId));

        if (!Files.exists(databaseDirectory)) {
            Files.createDirectories(databaseDirectory);
        }

        files = new RandomAccessDeque<>(INITIAL_RING_BUFFER_SIZE);

        try (final Stream<Path> fileStream = Files.walk(databaseDirectory)) {
            fileStream
                    .filter(f -> !Files.isDirectory(f))
                    .map(PreConsensusEventFileManager::parseFile)
                    .filter(Objects::nonNull)
                    .sorted()
                    .forEachOrdered(buildFileHandler(preConsensusEventStreamConfig.permitGaps()));
        }

        // Measure the size of each file.
        for (final PreConsensusEventFile file : files) {
            totalFileByteCount += Files.size(file.path());
        }

        if (files.size() > 0) {
            metrics.getPreconsensusEventFileOldestGeneration()
                    .set(files.getFirst().minimumGeneration());
            metrics.getPreconsensusEventFileYoungestGeneration()
                    .set(files.getLast().maximumGeneration());
            final Duration age = Duration.between(files.getFirst().timestamp(), time.now());
            metrics.getPreconsensusEventFileOldestSeconds().set(age.toSeconds());
        }
        updateFileSizeMetrics();
    }

    /**
     * Parse a file into a PreConsensusEventFile wrapper object.
     *
     * @param path the path to the file
     * @return the wrapper object, or null if the file can't be parsed
     */
    private static @Nullable PreConsensusEventFile parseFile(@NonNull final Path path) {
        try {
            return PreConsensusEventFile.of(path);
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
    private @NonNull Consumer<PreConsensusEventFile> buildFileHandler(final boolean permitGaps) {

        final ValueReference<Long> previousSequenceNumber = new ValueReference<>(-1L);
        final ValueReference<Long> previousMinimumGeneration = new ValueReference<>(-1L);
        final ValueReference<Long> previousMaximumGeneration = new ValueReference<>(-1L);
        final ValueReference<Instant> previousTimestamp = new ValueReference<>();

        return descriptor -> {
            fileSanityChecks(
                    permitGaps,
                    previousSequenceNumber,
                    previousMinimumGeneration,
                    previousMaximumGeneration,
                    previousTimestamp,
                    descriptor);

            previousSequenceNumber.setValue(descriptor.sequenceNumber());
            previousMinimumGeneration.setValue(descriptor.minimumGeneration());
            previousMaximumGeneration.setValue(descriptor.maximumGeneration());
            previousTimestamp.setValue(descriptor.timestamp());

            // If the sequence number is good then add it to the collection of tracked files
            files.addLast(descriptor);
        };
    }

    /**
     * Do a sanity check on an event file parsed from disk. Throw if an irregularity is detected. Better to crash than
     * to have an ISS due to improper event playback.
     */
    private static void fileSanityChecks(
            final boolean permitGaps,
            @NonNull final ValueReference<Long> previousSequenceNumber,
            @NonNull final ValueReference<Long> previousMinimumGeneration,
            @NonNull final ValueReference<Long> previousMaximumGeneration,
            @NonNull final ValueReference<Instant> previousTimestamp,
            @NonNull final PreConsensusEventFile descriptor) {

        // Sequence number should always monotonically increase
        if (!permitGaps
                && previousSequenceNumber.getValue() != -1
                && previousSequenceNumber.getValue() + 1 != descriptor.sequenceNumber()) {
            throw new IllegalStateException("Gap in pre-consensus event files detected! Previous sequence number was "
                    + previousSequenceNumber.getValue() + ", next sequence number is " + descriptor.sequenceNumber());
        }

        // Maximum generation should never be less than minimum generation
        if (descriptor.maximumGeneration() < descriptor.minimumGeneration()) {
            throw new IllegalStateException(
                    "File " + descriptor.path() + " has a maximum generation that is less than its minimum generation");
        }

        // Sanity check on the minimum generation
        if (descriptor.minimumGeneration() < previousMinimumGeneration.getValue()) {
            throw new IllegalStateException("Minimum generation must never decrease, file " + descriptor.path()
                    + " has a minimum generation that is less than the previous minimum generation of "
                    + previousMinimumGeneration.getValue());
        }

        // Sanity check on the maximum generation
        if (descriptor.maximumGeneration() < previousMaximumGeneration.getValue()) {
            throw new IllegalStateException("Maximum generation must never decrease, file " + descriptor.path()
                    + " has a maximum generation that is less than the previous maximum generation of "
                    + previousMaximumGeneration.getValue());
        }

        // Sanity check on timestamp
        if (previousTimestamp.getValue() != null && descriptor.timestamp().isBefore(previousTimestamp.getValue())) {
            throw new IllegalStateException("Timestamp must never decrease, file " + descriptor.path()
                    + " has a timestamp that is less than the previous timestamp of "
                    + previousTimestamp.getValue());
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
     * @param minimumGeneration the desired minimum generation, iterator is guaranteed to walk over all files that may
     *                          contain events with a generation greater or equal to this value. A value of
     *                          {@link #NO_MINIMUM_GENERATION} will cause the returned iterator to walk over all
     *                          available event files.
     * @return an iterator that walks over event files in order
     */
    public @NonNull Iterator<PreConsensusEventFile> getFileIterator(final long minimumGeneration) {
        return getFileIterator(minimumGeneration, false);
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
     * @param minimumGeneration        the desired minimum generation, iterator is guaranteed to walk over all files
     *                                 that may contain events with a generation greater or equal to this value. A value
     *                                 of {@link #NO_MINIMUM_GENERATION} will cause the returned iterator to walk over
     *                                 all available event files.
     * @param requireMinimumGeneration if true, then throw if data for the requested minimum generation is not available
     *                                 in the event files. If false, then the iterator will walk over events starting at
     *                                 the first generation on disk that is greater or equal to the requested minimum
     *                                 generation.
     * @return an iterator that walks over event files in order
     */
    public @NonNull Iterator<PreConsensusEventFile> getFileIterator(
            final long minimumGeneration, final boolean requireMinimumGeneration) {
        try {
            // Returns the index of the last file with a maximum generation that is
            // less than or equal to the minimum requested generation.
            int index =
                    (int) BinarySearch.search(0, files.size(), buildBinarySearchComparisonLambda(minimumGeneration));

            // If there are multiple files in a row with the same maximum generation then the binary search
            // may not land on the first file in the sequence. Shift to the left until the first file
            // with the desired maximum generation is discovered.
            while (index > 0 && files.get(index - 1).maximumGeneration() == minimumGeneration) {
                index--;
            }

            // If there is no file with a maximum generation exactly matching the target generation
            // then the file at the index may not have any relevant events. In that scenario, shifting
            // to the right will guarantee that the first file returned by the iterator is a file that
            // may contain events we are targeting.
            if (files.get(index).maximumGeneration() < minimumGeneration) {
                index++;
            }

            return files.iterator(index);
        } catch (final NoSuchElementException e) {
            if (requireMinimumGeneration && minimumGeneration != NO_MINIMUM_GENERATION) {
                throw new IllegalStateException(
                        "No event file contains data for the requested minimum generation: " + minimumGeneration, e);
            } else {
                // This exception will be thrown if the requested minimum generation is strictly less than the maximum
                // generation in the oldest file. No big deal, just start iterating at the oldest file.
                return files.iterator();
            }
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
     * @param minimumGeneration the minimum generation, all events in the stream with this generation or higher are
     *                          guaranteed to be returned by the iterator.
     * @return an iterator that walks over events
     */
    public @NonNull PreConsensusEventMultiFileIterator getEventIterator(final long minimumGeneration) {
        return new PreConsensusEventMultiFileIterator(minimumGeneration, getFileIterator(minimumGeneration));
    }

    /**
     * Build a function used to do a binary file search.
     *
     * @param minimumGeneration the minimum generation desired by the caller
     * @return a function for finding a starting file guaranteed for the generation requested by the user
     */
    private @NonNull LongToIntFunction buildBinarySearchComparisonLambda(final long minimumGeneration) {
        return (final long index) -> {
            final PreConsensusEventFile file = files.get((int) index);
            final long maxGenerationInFile = file.maximumGeneration();
            return Long.compare(maxGenerationInFile, minimumGeneration);
        };
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
        return files.getLast().sequenceNumber() + 1;
    }

    /**
     * Create a new event file descriptor for the next event file, and start tracking it. (Note, this method doesn't
     * actually open the file, it just permits the file to be opened by the caller.)
     *
     * @return a new event file descriptor
     */
    public @NonNull PreConsensusEventFile getNextFileDescriptor(
            final long minimumGeneration, final long maximumGeneration) {

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

            if (minimumGeneration < files.getLast().minimumGeneration()) {
                minimumGenerationForFile = files.getLast().minimumGeneration();
            } else {
                minimumGenerationForFile = minimumGeneration;
            }

            if (maximumGeneration < files.getLast().maximumGeneration()) {
                maximumGenerationForFile = files.getLast().maximumGeneration();
            } else {
                maximumGenerationForFile = maximumGeneration;
            }
        }

        final PreConsensusEventFile descriptor = PreConsensusEventFile.of(
                getNextSequenceNumber(),
                minimumGenerationForFile,
                maximumGenerationForFile,
                time.now(),
                databaseDirectory);

        files.addLast(descriptor);

        metrics.getPreconsensusEventFileYoungestGeneration().set(descriptor.maximumGeneration());

        return descriptor;
    }

    /**
     * The event file writer calls this method when it finishes writing an event file. This allows metrics to be
     * updated.
     *
     * @param file the file that has been completely written
     */
    public void finishedWritingFile(@NonNull final PreConsensusEventMutableFile file) {
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
                && files.getFirst().maximumGeneration() < minimumGeneration
                && files.getFirst().timestamp().isBefore(minimumTimestamp)) {

            final PreConsensusEventFile file = files.removeFirst();
            totalFileByteCount -= Files.size(file.path());
            file.deleteFile(databaseDirectory);
        }

        if (files.size() > 0) {
            metrics.getPreconsensusEventFileOldestGeneration()
                    .set(files.getFirst().minimumGeneration());
            final Duration age = Duration.between(files.getFirst().timestamp(), time.now());
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
