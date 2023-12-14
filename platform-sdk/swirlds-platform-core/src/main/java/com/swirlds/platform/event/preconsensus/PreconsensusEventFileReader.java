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

import static com.swirlds.platform.event.preconsensus.PreconsensusEventUtilities.compactPreconsensusEventFile;
import static com.swirlds.platform.event.preconsensus.PreconsensusEventUtilities.fileSanityChecks;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.ValueReference;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * This class is responsible for reading event files from disk and adding them to the collection of tracked files.
 */
public class PreconsensusEventFileReader {
    /**
     * Hidden constructor.
     */
    private PreconsensusEventFileReader() {}

    /**
     * Scan the file system for event files and add them to the collection of tracked files.
     *
     * @param platformContext   the platform context
     * @param databaseDirectory the directory to scan for files
     * @param permitGaps        if gaps are permitted in sequence number
     * @return the files read from disk
     * @throws IOException if there is an error reading the files
     */
    public static PreconsensusEventFiles readFilesFromDisk(
            @NonNull final PlatformContext platformContext,
            @NonNull final Path databaseDirectory,
            final boolean permitGaps)
            throws IOException {
        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(databaseDirectory);

        final PreconsensusEventFiles files = new PreconsensusEventFiles();

        try (final Stream<Path> fileStream = Files.walk(databaseDirectory)) {
            fileStream
                    .filter(f -> !Files.isDirectory(f))
                    .map(PreconsensusEventUtilities::parseFile)
                    .filter(Objects::nonNull)
                    .sorted()
                    .forEachOrdered(buildFileHandler(files, permitGaps));
        }

        final PreconsensusEventStreamConfig preconsensusEventStreamConfig =
                platformContext.getConfiguration().getConfigData(PreconsensusEventStreamConfig.class);
        final boolean doInitialGenerationalCompaction = preconsensusEventStreamConfig.compactLastFileOnStartup();

        if (files.getFileCount() != 0 && doInitialGenerationalCompaction) {
            compactGenerationalSpanOfLastFile(files);
        }

        return files;
    }

    /**
     * It's possible (if not probable) that the node was shut down prior to the last file being closed and having its
     * generational span compaction. This method performs that compaction if necessary.
     */
    private static void compactGenerationalSpanOfLastFile(@NonNull final PreconsensusEventFiles files) {
        Objects.requireNonNull(files);

        final PreconsensusEventFile lastFile = files.getFile(files.getFileCount() - 1);

        final long previousMaximumGeneration;
        if (files.getFileCount() > 1) {
            final PreconsensusEventFile secondToLastFile = files.getFile(files.getFileCount() - 2);
            previousMaximumGeneration = secondToLastFile.getMaximumGeneration();
        } else {
            previousMaximumGeneration = 0;
        }

        final PreconsensusEventFile compactedFile = compactPreconsensusEventFile(lastFile, previousMaximumGeneration);
        files.setFile(files.getFileCount() - 1, compactedFile);
    }

    /**
     * Build a handler for new files parsed from disk. Does basic sanity checks on the files, and adds them to the file
     * list if they are valid.
     *
     * @param permitGaps if gaps are permitted in sequence number
     * @return the handler
     */
    @NonNull
    private static Consumer<PreconsensusEventFile> buildFileHandler(
            @NonNull final PreconsensusEventFiles files, final boolean permitGaps) {
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
            files.addFile(descriptor);
        };
    }
}
