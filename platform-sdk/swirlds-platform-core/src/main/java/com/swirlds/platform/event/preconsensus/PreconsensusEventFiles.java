package com.swirlds.platform.event.preconsensus;

import com.swirlds.common.utility.RandomAccessDeque;
import com.swirlds.common.utility.UnmodifiableIterator;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.platform.event.preconsensus.PreconsensusEventFileManager.NO_MINIMUM_GENERATION;

public class PreconsensusEventFiles {
    private static final Logger logger = LogManager.getLogger(PreconsensusEventFiles.class);

    /**
     * The initial size of the ring buffer used to track event files.
     */
    private static final int INITIAL_RING_BUFFER_SIZE = 1024;

    /**
     * Tracks all files currently on disk.
     */
    private final RandomAccessDeque<PreconsensusEventFile> files = new RandomAccessDeque<>(INITIAL_RING_BUFFER_SIZE);

    public PreconsensusEventFile getFirstFile() {
        return files.getFirst();
    }

    public PreconsensusEventFile getLastFile() {
        return files.getLast();
    }

    public PreconsensusEventFile removeFirstFile() {
        return files.removeFirst();
    }

    public PreconsensusEventFile removeLastFile() {
        return files.removeLast();
    }

    public int getFileCount() {
        return files.size();
    }

    public long getTotalFileByteCount() throws IOException {
        long totalFileByteCount = 0;

        // Measure the size of each file.
        for (final PreconsensusEventFile file : files) {
            totalFileByteCount += Files.size(file.getPath());
        }

        return totalFileByteCount;
    }

    /**
     * Add a new files to the end of the file list.
     *
     * @param file the file to be added
     */
    public void addFile(@NonNull final PreconsensusEventFile file) {
        Objects.requireNonNull(file);
        files.addLast(file);
    }

    public PreconsensusEventFile getFile(final int index) {
        return files.get(index);
    }

    public void setFile(final int index, @NonNull final PreconsensusEventFile file) {
        Objects.requireNonNull(file);
        files.set(index, file);
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
     *                          will be returned. A value of {@link PreconsensusEventFileManager::NO_MINIMUM_GENERATION}
     *                          will cause the returned iterator to walk over all available events.
     * @return an iterator that walks over events
     */
    public @NonNull PreconsensusEventMultiFileIterator getEventIterator(final long minimumGeneration, final long startingRound) {
        return new PreconsensusEventMultiFileIterator(minimumGeneration, getFileIterator(minimumGeneration, startingRound));
    }

    /**
     * Get an iterator that walks over all event files currently being tracked, in order.
     * <p>
     * Note: this method only works at system startup time, using this iterator after startup has undefined behavior. A
     * future task will be to enable event iteration after startup.
     *
     * @param minimumGeneration the desired minimum generation, iterator is guaranteed to walk over all files that may
     *                          contain events with a generation greater or equal to this value. A value of
     *                          {@link PreconsensusEventFileManager::NO_MINIMUM_GENERATION} will cause the returned
     *                          iterator to walk over all available event files.
     * @return an unmodifiable iterator that walks over event files in order
     */
    public @NonNull Iterator<PreconsensusEventFile> getFileIterator(final long minimumGeneration, final long startingRound) {
        final int firstFileIndex = getFirstFileIndex(startingRound);

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
     * Get the index of the first file to consider given a certain starting round. If no file is compatible with
     * the starting round, return -1.
     *
     * @param startingRound the round to start streaming from
     * @return the index of the first file to consider for a given starting round
     */
    public int getFirstFileIndex(final long startingRound) {
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
}
