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

import com.swirlds.common.utility.ValueReference;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for reading PCES files from disk.
 */
public final class PreconsensusEventFileReader {

    private static final Logger logger = LogManager.getLogger(PreconsensusEventFileReader.class);

    private PreconsensusEventFileReader() {}

    public static List<PreconsensusEventFile> readFilesFromDisk(
            @NonNull final Path rootDirectory, final boolean permitGaps) throws IOException {

        final List<PreconsensusEventFile> files = new ArrayList<>();
        readFilesFromDisk(rootDirectory, files::add, false);
        return files;
    }

    /**
     * Scan the file system for event files and add them to the collection of tracked files.
     *
     * @param rootDirectory     the root directory to look for files in
     * @param validFileConsumer each file is passed to this method after it is read and validated
     * @param permitGaps        if gaps are permitted in sequence number
     */
    public static void readFilesFromDisk(
            @NonNull final Path rootDirectory,
            @NonNull final Consumer<PreconsensusEventFile> validFileConsumer,
            final boolean permitGaps)
            throws IOException {

        try (final Stream<Path> fileStream = Files.walk(rootDirectory)) {
            fileStream
                    .filter(f -> !Files.isDirectory(f))
                    .map(PreconsensusEventFileReader::parseFile)
                    .filter(Objects::nonNull)
                    .sorted()
                    .forEachOrdered(buildFileHandler(validFileConsumer, permitGaps));
        }
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
     * @param validFileConsumer the consumer to pass valid files to
     * @param permitGaps        if gaps are permitted in sequence number
     * @return the handler
     */
    @NonNull
    private static Consumer<PreconsensusEventFile> buildFileHandler(
            @NonNull final Consumer<PreconsensusEventFile> validFileConsumer, final boolean permitGaps) {

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

            validFileConsumer.accept(descriptor);
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
    public static void fileSanityChecks(
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
}
