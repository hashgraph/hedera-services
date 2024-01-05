/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.platform.event.preconsensus.PcesFileManager.NO_MINIMUM_GENERATION;

import com.swirlds.common.utility.RandomAccessDeque;
import com.swirlds.common.utility.UnmodifiableIterator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Tracks preconsensus event files currently on disk.
 */
public class PcesFileTracker {
    private static final Logger logger = LogManager.getLogger(PcesFileTracker.class);

    /**
     * The initial size of the ring buffer used to track event files.
     */
    private static final int INITIAL_RING_BUFFER_SIZE = 1024;

    /**
     * Tracks all files currently on disk.
     */
    private final RandomAccessDeque<PcesFile> files = new RandomAccessDeque<>(INITIAL_RING_BUFFER_SIZE);

    /**
     * Get the first file in the file list.
     *
     * @return the first file in the file list
     */
    public PcesFile getFirstFile() {
        return files.getFirst();
    }

    /**
     * Get the last file in the file list.
     *
     * @return the last file in the file list
     */
    public PcesFile getLastFile() {
        return files.getLast();
    }

    /**
     * Remove the first file in the file list.
     *
     * @return the file that was removed
     */
    public PcesFile removeFirstFile() {
        return files.removeFirst();
    }

    /**
     * Remove the last file in the file list.
     *
     * @return the file that was removed
     */
    public PcesFile removeLastFile() {
        return files.removeLast();
    }

    /**
     * Get the number of files currently being tracked.
     *
     * @return the number of files currently being tracked
     */
    public int getFileCount() {
        return files.size();
    }

    /**
     * Get the number of bytes in all files currently being tracked.
     *
     * @return the number of bytes in all files currently being tracked
     * @throws IOException if there is an error reading the files
     */
    public long getTotalFileByteCount() throws IOException {
        long totalFileByteCount = 0;

        // Measure the size of each file.
        for (final PcesFile file : files) {
            totalFileByteCount += Files.size(file.getPath());
        }

        return totalFileByteCount;
    }

    /**
     * Add a new files to the end of the file list.
     *
     * @param file the file to be added
     */
    public void addFile(@NonNull final PcesFile file) {
        Objects.requireNonNull(file);
        files.addLast(file);
    }

    /**
     * Get the file at a specified index.
     *
     * @param index the index of the file to get
     * @return the file at the specified index
     */
    public PcesFile getFile(final int index) {
        return files.get(index);
    }

    /**
     * Set the file at a specified index.
     *
     * @param index the index of the file to set
     * @param file  the file to set
     */
    public void setFile(final int index, @NonNull final PcesFile file) {
        Objects.requireNonNull(file);
        files.set(index, file);
    }

    /**
     * Get an iterator that walks over all events starting with a specified generation.
     * <p>
     * Note: this method only works at system startup time, using this iterator after startup has undefined behavior. A
     * future task will be to enable event iteration after startup.
     *
     * @param minimumGeneration the desired minimum generation, iterator is guaranteed to return all available events
     *                          with a generation greater or equal to this value. No events with a smaller generation
     *                          will be returned. A value of {@link PcesFileManager ::NO_MINIMUM_GENERATION}
     *                          will cause the returned iterator to walk over all available events.
     * @param startingRound     the round to start iterating from
     * @return an iterator that walks over events
     */
    public @NonNull PcesMultiFileIterator getEventIterator(final long minimumGeneration, final long startingRound) {
        return new PcesMultiFileIterator(minimumGeneration, getFileIterator(minimumGeneration, startingRound));
    }

    /**
     * Get an iterator that walks over all event files currently being tracked, in order.
     * <p>
     * Note: this method only works at system startup time, using this iterator after startup has undefined behavior. A
     * future task will be to enable event iteration after startup.
     *
     * @param minimumGeneration the desired minimum generation, iterator is guaranteed to walk over all files that may
     *                          contain events with a generation greater or equal to this value. A value of
     *                          {@link PcesFileManager#NO_MINIMUM_GENERATION} will cause the returned
     *                          iterator to walk over all available event files.
     * @param startingRound     the round to start iterating from
     * @return an unmodifiable iterator that walks over event files in order
     */
    public @NonNull Iterator<PcesFile> getFileIterator(final long minimumGeneration, final long startingRound) {
        final int firstFileIndex = getFirstRelevantFileIndex(startingRound);

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
            final PcesFile file = files.get(index);
            if (file.getMaximumGeneration() >= minimumGeneration) {
                // We have found the first file that may contain events at the requested generation.
                return new UnmodifiableIterator<>(files.iterator(index));
            }
        }

        // It should not be possible to reach this point.
        throw new IllegalStateException("Failed to find a file that may contain events at the requested generation");
    }

    /**
     * Get the index of the first file to consider given a certain starting round. This will be the file with the
     * largest origin that does not exceed the starting round.
     * <p>
     * If no file is compatible with the starting round, return -1. If there are no compatible files, that means there
     * are either no files, or all files have an origin that exceeds the starting round.
     *
     * @param startingRound the round to start streaming from
     * @return the index of the first file to consider for a given starting round
     */
    public int getFirstRelevantFileIndex(final long startingRound) {
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
