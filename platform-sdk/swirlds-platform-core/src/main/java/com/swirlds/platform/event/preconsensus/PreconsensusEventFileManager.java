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

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.units.UnitConstants;
import com.swirlds.common.utility.RandomAccessDeque;
import com.swirlds.common.utility.UnmodifiableIterator;
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
    public static final long NO_MINIMUM_GENERATION = -1;

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
     * If we ever have to delete files out of band, move them to the recycle bin.
     */
    private final RecycleBin recycleBin;

    /**
     * Tracks all files currently on disk.
     */
    private final RandomAccessDeque<PreconsensusEventFile> files = new RandomAccessDeque<>(INITIAL_RING_BUFFER_SIZE);

    /**
     * When streaming files at startup time, this is the index of the first file to consider. Note that the file at this
     * index may not contain any events in the requested generational range. This is just the first file that we may
     * legally begin streaming from, regardless of generations.
     */
    private final int firstFileIndex;

    /**
     * The current origin round.
     */
    private long currentOrigin;

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
     * @param recycleBin      can remove files in a way that allows them to be possibly recovered for debugging
     * @param selfId          the ID of this node
     * @param startingRound   the round number of the initial state of the system
     */
    public PreconsensusEventFileManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final RecycleBin recycleBin,
            @NonNull final NodeId selfId,
            final long startingRound)
            throws IOException {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(time);
        Objects.requireNonNull(selfId);

        if (startingRound < 0) {
            throw new IllegalArgumentException("starting round must be non-negative");
        }

        final PreconsensusEventStreamConfig preconsensusEventStreamConfig =
                platformContext.getConfiguration().getConfigData(PreconsensusEventStreamConfig.class);

        this.time = time;
        this.metrics = new PreconsensusEventMetrics(platformContext.getMetrics());
        minimumRetentionPeriod = preconsensusEventStreamConfig.minimumRetentionPeriod();
        databaseDirectory = getDatabaseDirectory(platformContext, selfId);
        this.recycleBin = Objects.requireNonNull(recycleBin);

        readFilesFromDisk(preconsensusEventStreamConfig.permitGaps());

        firstFileIndex = getFirstFileIndex(startingRound);
        currentOrigin = getInitialOrigin(startingRound);

        resolveDiscontinuities();
        initializeMetrics();
    }

    /**
     * Get the directory where event files are stored. If that directory doesn't exist, create it.
     *
     * @param platformContext the platform context for this node
     * @param selfId          the ID of this node
     * @return the directory where event files are stored
     */
    @NonNull
    private static Path getDatabaseDirectory(
            @NonNull final PlatformContext platformContext, @NonNull final NodeId selfId) throws IOException {

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        final PreconsensusEventStreamConfig preconsensusEventStreamConfig =
                platformContext.getConfiguration().getConfigData(PreconsensusEventStreamConfig.class);

        final Path savedStateDirectory = stateConfig.savedStateDirectory();
        final Path databaseDirectory = savedStateDirectory
                .resolve(preconsensusEventStreamConfig.databaseDirectory())
                .resolve(Long.toString(selfId.id()));

        if (!Files.exists(databaseDirectory)) {
            Files.createDirectories(databaseDirectory);
        }

        return databaseDirectory;
    }

    /**
     * Scan the file system for event files and add them to the collection of tracked files.
     *
     * @param permitGaps if gaps are permitted in sequence number
     */
    private void readFilesFromDisk(final boolean permitGaps) throws IOException {
        try (final Stream<Path> fileStream = Files.walk(databaseDirectory)) {
            fileStream
                    .filter(f -> !Files.isDirectory(f))
                    .map(PreconsensusEventFileManager::parseFile)
                    .filter(Objects::nonNull)
                    .sorted()
                    .forEachOrdered(buildFileHandler(permitGaps));
        }
    }

    /**
     * Get the index of the first file to consider when streaming files at startup time. If no file is compatible with
     * the starting round, return -1.
     */
    private int getFirstFileIndex(final long startingRound) {
        // When streaming from the preconsensus event stream, we need to start at
        // the file with the largest origin that does not exceed the starting round.

        int candidateIndex = -1;
        long candidateOrigin = -1;

        for (int index = 0; index < files.size(); index++) {
            final long fileOrigin = files.get(index).getOrigin();

            if (fileOrigin > startingRound) {
                // Once we find the first file with an origin that exceeds the starting round, we can stop searching.
                // File origins only increase, so we know that all files after this one will also exceed the round.
                return candidateIndex;
            } else if (fileOrigin > candidateOrigin) {
                // We've discovered a higher legal origin. Keep track of the first file with this origin.
                candidateIndex = index;
                candidateOrigin = fileOrigin;
            }
        }

        // If all files have a legal origin, return the index of the first file with the highest index.
        return candidateIndex;
    }

    /**
     * Get the origin round that should be used at startup time.
     *
     * @param startingRound the round number of the initial state of the system
     * @return the origin round that should be used at startup time
     */
    private long getInitialOrigin(final long startingRound) {
        if (firstFileIndex >= 0) {
            return files.get(firstFileIndex).getOrigin();
        }
        return startingRound;
    }

    /**
     * If there is a discontinuity in the stream after the location where we will begin streaming, delete all files that
     * come after the discontinuity.
     */
    private void resolveDiscontinuities() throws IOException {
        int firstIndexToDelete = firstFileIndex + 1;
        for (; firstIndexToDelete < files.size(); firstIndexToDelete++) {
            final PreconsensusEventFile file = files.get(firstIndexToDelete);
            if (file.getOrigin() != currentOrigin) {
                break;
            }
        }

        if (firstIndexToDelete == files.size()) {
            // No discontinuities were detected
            return;
        }

        final PreconsensusEventFile lastUndeletedFile =
                firstIndexToDelete > 0 ? files.get(firstIndexToDelete - 1) : null;

        logger.warn(
                STARTUP.getMarker(),
                """
                        Discontinuity detected in the preconsensus event stream. Purging {} file(s).
                            Last undeleted file: {}
                            First deleted file:  {}
                            Last deleted file:   {}""",
                files.size() - firstIndexToDelete,
                lastUndeletedFile,
                files.get(firstIndexToDelete),
                files.getLast());

        // Delete files in reverse order so that if we crash we don't leave gaps in the sequence number if we crash.
        while (files.size() > firstIndexToDelete) {
            files.removeLast().deleteFile(databaseDirectory, recycleBin);
        }
    }

    /**
     * Initialize metrics given the files currently on disk.
     */
    private void initializeMetrics() throws IOException {
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
        final ValueReference<Long> previousOrigin = new ValueReference<>(-1L);
        final ValueReference<Instant> previousTimestamp = new ValueReference<>();

        return descriptor -> {
            if (previousSequenceNumber.getValue() != -1) {
                fileSanityChecks(
                        permitGaps,
                        previousSequenceNumber.getValue(),
                        previousMinimumGeneration.getValue(),
                        previousMaximumGeneration.getValue(),
                        previousOrigin.getValue(),
                        previousTimestamp.getValue(),
                        descriptor);
            }

            previousSequenceNumber.setValue(descriptor.getSequenceNumber());
            previousMinimumGeneration.setValue(descriptor.getMinimumGeneration());
            previousMaximumGeneration.setValue(descriptor.getMaximumGeneration());
            previousTimestamp.setValue(descriptor.getTimestamp());

            // If the sequence number is good then add it to the collection of tracked files
            addFile(descriptor);
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
     * @param previousOrigin            the origin round of the previous file
     * @param previousTimestamp         the timestamp of the previous file
     * @param descriptor                the descriptor of the next file
     * @throws IllegalStateException if any of the required invariants are violated by the next file
     */
    private static void fileSanityChecks(
            final boolean permitGaps,
            final long previousSequenceNumber,
            final long previousMinimumGeneration,
            final long previousMaximumGeneration,
            final long previousOrigin,
            @NonNull final Instant previousTimestamp,
            @NonNull final PreconsensusEventFile descriptor) {

        // Sequence number should always monotonically increase
        if (!permitGaps && previousSequenceNumber + 1 != descriptor.getSequenceNumber()) {
            throw new IllegalStateException("Gap in preconsensus event files detected! Previous sequence number was "
                    + previousSequenceNumber + ", next sequence number is "
                    + descriptor.getSequenceNumber());
        }

        // Minimum generation may never decrease
        if (descriptor.getMinimumGeneration() < previousMinimumGeneration) {
            throw new IllegalStateException("Minimum generation must never decrease, file " + descriptor.getPath()
                    + " has a minimum generation that is less than the previous minimum generation of "
                    + previousMinimumGeneration);
        }

        // Maximum generation may never decrease
        if (descriptor.getMaximumGeneration() < previousMaximumGeneration) {
            throw new IllegalStateException("Maximum generation must never decrease, file " + descriptor.getPath()
                    + " has a maximum generation that is less than the previous maximum generation of "
                    + previousMaximumGeneration);
        }

        // Timestamp must never decrease
        if (descriptor.getTimestamp().isBefore(previousTimestamp)) {
            throw new IllegalStateException("Timestamp must never decrease, file " + descriptor.getPath()
                    + " has a timestamp that is less than the previous timestamp of "
                    + previousTimestamp);
        }

        // Origin round must never decrease
        if (descriptor.getOrigin() < previousOrigin) {
            throw new IllegalStateException("Origin round must never decrease, file " + descriptor.getPath()
                    + " has an origin round that is less than the previous origin round of "
                    + previousOrigin);
        }
    }

    /**
     * <p>
     * Get an iterator that walks over all event files currently being tracked, in order.
     * </p>
     *
     * <p>
     * Note: this method only works at system startup time, using this iterator after startup has undefined behavior. A
     * future task will be to enable event iteration after startup.
     * </p>
     *
     * @param minimumGeneration the desired minimum generation, iterator is guaranteed to walk over all files that may
     *                          contain events with a generation greater or equal to this value. A value of
     *                          {@link #NO_MINIMUM_GENERATION} will cause the returned iterator to walk over all
     *                          available event files.
     * @return an unmodifiable iterator that walks over event files in order
     */
    public @NonNull Iterator<PreconsensusEventFile> getFileIterator(final long minimumGeneration) {

        // Edge case: we want all events regardless of generation
        if (minimumGeneration == NO_MINIMUM_GENERATION) {
            return new UnmodifiableIterator<>(files.iterator(firstFileIndex));
        }

        // Edge case: there are no files
        if (files.size() == 0) {
            logger.warn(STARTUP.getMarker(), "No preconsensus event files available");
            return Collections.emptyIterator();
        }

        // Edge case: our first file comes after the requested starting generation
        if (files.get(firstFileIndex).getMinimumGeneration() >= minimumGeneration) {
            // Unless we observe at least one file with a minimum generation less than the requested minimum,
            // then we can't know for certain that we have all data for the requested minimum generation.
            logger.warn(
                    STARTUP.getMarker(),
                    "The preconsensus event stream has insufficient data to guarantee that all events with the "
                            + "requested generation of {} are present, the first file has a minimum generation of {}",
                    minimumGeneration,
                    files.getFirst().getMinimumGeneration());

            return new UnmodifiableIterator<>(files.iterator(firstFileIndex));
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
        for (int index = firstFileIndex; index < fileCount; index++) {
            final PreconsensusEventFile file = files.get(index);
            if (file.getMaximumGeneration() >= minimumGeneration) {
                // We have found the first file that may contain events at the requested generation.
                return new UnmodifiableIterator<>(files.iterator(index));
            }
        }

        // It should not be possible to reach this point.
        throw new IllegalStateException("Failed to find a file that may contain events at the requested generation");
    }

    /**
     * <p>
     * Get an iterator that walks over all events starting with a specified generation.
     * </p>
     *
     * <p>
     * Note: this method only works at system startup time, using this iterator after startup has undefined behavior. A
     * future task will be to enable event iteration after startup.
     *
     * @param minimumGeneration the desired minimum generation, iterator is guaranteed to return all available events
     *                          with a generation greater or equal to this value. No events with a smaller generation
     *                          will be returned. A value of {@link #NO_MINIMUM_GENERATION} will cause the returned
     *                          iterator to walk over all available events.
     * @return an iterator that walks over events
     */
    public @NonNull PreconsensusEventMultiFileIterator getEventIterator(final long minimumGeneration) {
        return new PreconsensusEventMultiFileIterator(minimumGeneration, getFileIterator(minimumGeneration));
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
     * Register a discontinuity in the stream.
     *
     * @param newOriginRound the new origin for stream files written after this method is called
     */
    public void registerDiscontinuity(final long newOriginRound) {
        if (newOriginRound <= currentOrigin) {
            throw new IllegalArgumentException("New origin round must be greater than the current origin round. "
                    + "Current origin round: " + currentOrigin + ", new origin round: " + newOriginRound);
        }

        final PreconsensusEventFile lastFile = files.size() > 0 ? files.getLast() : null;

        logger.info(
                STARTUP.getMarker(),
                "Due to recent operations on this node, the local preconsensus event stream"
                        + " will have a discontinuity. The last file with the old origin round is {}. "
                        + "All future files will have an origin round of {}.",
                lastFile,
                newOriginRound);

        currentOrigin = newOriginRound;
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
                time.now(),
                getNextSequenceNumber(),
                minimumGenerationForFile,
                maximumGenerationForFile,
                currentOrigin,
                databaseDirectory);

        if (files.size() > 0) {
            // There are never enough sanity checks. This is the same sanity check that is run when we parse
            // the files from disk, so if it doesn't pass now it's not going to pass when we read the files.
            final PreconsensusEventFile previousFile = files.getLast();
            fileSanityChecks(
                    false,
                    previousFile.getSequenceNumber(),
                    previousFile.getMinimumGeneration(),
                    previousFile.getMaximumGeneration(),
                    currentOrigin,
                    previousFile.getTimestamp(),
                    descriptor);
        }

        addFile(descriptor);
        metrics.getPreconsensusEventFileYoungestGeneration().set(descriptor.getMaximumGeneration());

        return descriptor;
    }

    /**
     * Add a new files to the end of the file list.
     *
     * @param file the file to be added
     */
    private void addFile(@NonNull final PreconsensusEventFile file) {
        Objects.requireNonNull(file);
        files.addLast(file);
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
     * Delete all files in the preconsensus event stream. If this method is called, it must be called before a
     * {@link PreconsensusEventFileManager} is instantiated. Any manager open when this method is called will be
     * corrupted.
     *
     * @param platformContext the platform context for this node
     * @param recycleBin      can remove files in a way that allows them to be possibly recovered for debugging
     * @param selfId          the ID of this node
     */
    public static void clear(
            @NonNull final PlatformContext platformContext,
            @NonNull final RecycleBin recycleBin,
            @NonNull final NodeId selfId) {
        try {
            final Path path = getDatabaseDirectory(platformContext, selfId);
            if (Files.exists(path)) {
                recycleBin.recycle(path);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to recycle preconsensus event files", e);
        }
    }

    /**
     * Update metrics with the latest data on file size.
     */
    private void updateFileSizeMetrics() {
        metrics.getPreconsensusEventFileCount().set(files.size());

        metrics.getPreconsensusEventFileTotalSizeGB().set(totalFileByteCount * UnitConstants.BYTES_TO_GIBIBYTES);

        if (files.size() > 0) {
            metrics.getPreconsensusEventFileAverageSizeMB()
                    .set(((double) totalFileByteCount) / files.size() * UnitConstants.BYTES_TO_MEBIBYTES);
        }
    }
}
