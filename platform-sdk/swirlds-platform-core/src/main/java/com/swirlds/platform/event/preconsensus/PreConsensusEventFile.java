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

import static com.swirlds.common.formatting.StringFormattingUtils.parseSanitizedTimestamp;
import static com.swirlds.common.formatting.StringFormattingUtils.sanitizeTimestamp;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.utility.NonCryptographicHashing;
import com.swirlds.platform.internal.EventImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * <p>
 * Describes a pre-consensus event DB file.
 * </p>
 *
 * <p>
 * Files have the following format. Deviation from this format is not allowed. A {@link PreConsensusEventFileManager}
 * will be unable to correctly read files with a different format.
 * </p>
 * <pre>
 * seq[sequence number]-ming[minimum legal generation]-maxg[maximum legal generation]-[Instant.toString().replace(":", "+")].pces
 * </pre>
 * <p>
 * By default, files are stored with the following directory structure. Note that files are not required to be stored
 * with this directory structure in order to be read by a {@link PreConsensusEventFileManager}.
 * </p>
 * <pre>
 * [root directory]/[4 digit year][2 digit month][2 digit day]/[file name]
 * </pre>
 *
 * @param sequenceNumber
 * 		the sequence number of the file. All file sequence numbers are unique.
 * 		Sequence numbers are allocated in monotonically increasing order.
 * @param minimumGeneration
 * 		the minimum generation of events that are permitted to be in this file
 * @param maximumGeneration
 * 		the maximum generation of events that are permitted to be in this file
 * @param timestamp
 * 		the timestamp of when the writing of this file began
 * @param path
 * 		the location where this file can be found
 */
public record PreConsensusEventFile(
        long sequenceNumber, long minimumGeneration, long maximumGeneration, Instant timestamp, Path path)
        implements Comparable<PreConsensusEventFile> {

    /**
     * The file extension. Stands for "Pre-Consensus EventS".
     */
    public static final String EVENT_FILE_EXTENSION = ".pces";

    /**
     * The character used to separate fields in the file name.
     */
    public static final String EVENT_FILE_SEPARATOR = "_";

    /**
     * Written before the sequence number in the file name. Improves readability for humans.
     */
    public static final String SEQUENCE_NUMBER_PREFIX = "seq";

    /**
     * Written before the minimum generation in the file name. Improves readability for humans.
     */
    public static final String MINIMUM_GENERATION_PREFIX = "ming";

    /**
     * Written before the maximum generation in the file name. Improves readability for humans.
     */
    public static final String MAXIMUM_GENERATION_PREFIX = "maxg";

    /**
     * Create a new event file descriptor.
     *
     * @param sequenceNumber
     * 		the sequence number of the descriptor
     * @param minimumGeneration
     * 		the minimum event generation permitted to be in this file (inclusive)
     * @param maximumGeneration
     * 		the maximum event generation permitted to be in this file (inclusive)
     * @param timestamp
     * 		the timestamp when this file was created (wall clock time)
     * @param rootDirectory
     * 		the directory where event stream files are stored
     * @return a description of the file
     */
    public static PreConsensusEventFile of(
            final long sequenceNumber,
            final long minimumGeneration,
            final long maximumGeneration,
            final Instant timestamp,
            final Path rootDirectory) {

        final Path parentDirectory = buildParentDirectory(rootDirectory, timestamp);
        final String fileName = buildFileName(sequenceNumber, minimumGeneration, maximumGeneration, timestamp);
        final Path path = parentDirectory.resolve(fileName);

        return new PreConsensusEventFile(
                sequenceNumber,
                minimumGeneration,
                maximumGeneration,
                Instant.ofEpochMilli(timestamp.toEpochMilli()),
                path);
    }

    /**
     * Create a new event file descriptor by parsing a file path.
     *
     * @param filePath
     * 		the path to the file
     * @return a description of the file
     * @throws IOException
     * 		if the file could not be parsed
     */
    public static PreConsensusEventFile of(final Path filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath");

        if (!filePath.toString().endsWith(EVENT_FILE_EXTENSION)) {
            throw new IOException("File " + filePath + " has the wrong type");
        }

        final String fileName = filePath.getFileName().toString();

        final String[] elements = fileName.substring(0, fileName.length() - EVENT_FILE_EXTENSION.length())
                .split(EVENT_FILE_SEPARATOR);

        if (elements.length != 4) {
            throw new IOException("Unable to parse fields from " + filePath);
        }

        try {
            return new PreConsensusEventFile(
                    Long.parseLong(elements[0].replace(SEQUENCE_NUMBER_PREFIX, "")),
                    Long.parseLong(elements[1].replace(MINIMUM_GENERATION_PREFIX, "")),
                    Long.parseLong(elements[2].replace(MAXIMUM_GENERATION_PREFIX, "")),
                    parseSanitizedTimestamp(elements[3]),
                    filePath);
        } catch (final NumberFormatException | DateTimeParseException ex) {
            throw new IOException("unable to parse " + filePath, ex);
        }
    }

    /**
     * Get an object that can be used to write events to this file. Throws if there already exists
     * a file on disk with the same path.
     *
     * @return a writer for this file
     */
    public PreConsensusEventMutableFile getMutableFile() throws IOException {
        return new PreConsensusEventMutableFile(this);
    }

    /**
     * Delete a file. Automatically deletes parent directories if
     * empty up until the root directory is reached, which is never deleted.
     *
     * @param rootDirectory
     * 		the root directory where event files are stored
     */
    public void deleteFile(final Path rootDirectory) throws IOException {
        if (!Files.exists(path)) {
            // Nothing to delete.
            return;
        }

        Files.delete(path);

        // Delete parent directories if they are empty
        Path target = path.getParent();
        while (!target.equals(rootDirectory)) {
            try (final Stream<Path> list = Files.list(target)) {
                if (list.findAny().isPresent()) {
                    // This directory is not empty, stop deleting
                    return;
                }
            }

            // This will fail if we attempt to delete a non-empty directory, so there is no danger
            // of this loop walking all the way up to / and deleting the entire file system.
            Files.delete(target);

            target = target.getParent();
        }
    }

    /**
     * Get an iterator that walks over the events in this file. The iterator will only return events that have a
     * generation equal to or greater to the minimum generation.
     *
     * @param minimumGeneration
     * 		the minimum generation of the events to return
     * @return an iterator over the events in this file
     */
    public PreConsensusEventFileIterator iterator(final long minimumGeneration) throws IOException {
        return new PreConsensusEventFileIterator(this, minimumGeneration);
    }

    /**
     * Build the parent directory for a new event file.
     *
     * @param rootDirectory
     * 		the root directory where all event files are stored
     * @param timestamp
     * 		the timestamp of the new file
     * @return the parent directory of the new file
     */
    private static Path buildParentDirectory(final Path rootDirectory, final Instant timestamp) {
        final ZonedDateTime zonedDateTime = timestamp.atZone(ZoneId.systemDefault());
        return rootDirectory
                .resolve(String.format("%04d", zonedDateTime.getYear()))
                .resolve(String.format("%02d", zonedDateTime.getMonthValue()))
                .resolve(String.format("%02d", zonedDateTime.getDayOfMonth()));
    }

    /**
     * Derive the name for this file.
     *
     * @param sequenceNumber
     * 		the sequence number of the file
     * @param minimumGeneration
     * 		the minimum generation of events permitted in this file
     * @param maximumGeneration
     * 		the maximum generation of events permitted in this file
     * @param timestamp
     * 		the timestamp of when the file was created
     * @return the file name
     */
    private static String buildFileName(
            final long sequenceNumber,
            final long minimumGeneration,
            final long maximumGeneration,
            final Instant timestamp) {

        return SEQUENCE_NUMBER_PREFIX
                + sequenceNumber
                + EVENT_FILE_SEPARATOR
                + MINIMUM_GENERATION_PREFIX
                + minimumGeneration
                + EVENT_FILE_SEPARATOR
                + MAXIMUM_GENERATION_PREFIX
                + maximumGeneration
                + EVENT_FILE_SEPARATOR
                + sanitizeTimestamp(timestamp)
                + EVENT_FILE_EXTENSION;
    }

    /**
     * Get the file name of this file.
     *
     * @return this file's name
     */
    public String getFileName() {
        return path.getFileName().toString();
    }

    /**
     * Check if it is legal for the file described by this object to contain a particular event.
     *
     * @param event
     * 		the event in question
     * @return true if it is legal for this event to be in the file described by this object
     */
    public boolean canContain(final EventImpl event) {
        return event.getGeneration() >= minimumGeneration && event.getGeneration() <= maximumGeneration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(final PreConsensusEventFile that) {
        return Long.compare(sequenceNumber, that.sequenceNumber);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getFileName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof final PreConsensusEventFile that) {
            return this.sequenceNumber == that.sequenceNumber;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return NonCryptographicHashing.hash32(sequenceNumber);
    }
}
