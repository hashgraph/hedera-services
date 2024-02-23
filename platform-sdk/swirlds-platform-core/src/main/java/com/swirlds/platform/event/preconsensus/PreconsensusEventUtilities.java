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

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.swirlds.common.io.IOIterator;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for preconsensus events.
 * <p>
 * Future work: This class will be deleted once the PCES migration to the new framework is complete.
 */
public final class PreconsensusEventUtilities {

    private static final Logger logger = LogManager.getLogger(PreconsensusEventUtilities.class);

    private PreconsensusEventUtilities() {}

    /**
     * Compact the generational span of a PCES file.
     *
     * @param originalFile              the file to compact
     * @param previousMaximumGeneration the maximum generation of the previous PCES file, used to prevent using a
     *                                  smaller maximum generation than the previous file.
     * @return the new compacted PCES file.
     */
    @NonNull
    public static PreconsensusEventFile compactPreconsensusEventFile(
            @NonNull final PreconsensusEventFile originalFile, final long previousMaximumGeneration) {

        // Find the maximum generation in the file.
        long maxGeneration = originalFile.getMinimumGeneration();
        try (final IOIterator<GossipEvent> iterator = new PreconsensusEventFileIterator(originalFile, 0)) {

            while (iterator.hasNext()) {
                final GossipEvent next = iterator.next();
                maxGeneration = Math.max(maxGeneration, next.getGeneration());
            }

        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "Failed to read file {}", originalFile.getPath(), e);
            return originalFile;
        }

        // Important: do not decrease the maximum generation below the value of the previous file's maximum generation.
        maxGeneration = Math.max(maxGeneration, previousMaximumGeneration);

        if (maxGeneration == originalFile.getMaximumGeneration()) {
            // The file cannot have its span compacted any further.
            logger.info(STARTUP.getMarker(), "No span compaction necessary for {}", originalFile.getPath());
            return originalFile;
        }

        // Now, compact the generational span of the file using the newly discovered maximum generation.
        final PreconsensusEventFile newFile = originalFile.buildFileWithCompressedSpan(maxGeneration);
        try {
            Files.move(originalFile.getPath(), newFile.getPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "Failed to compact span of file {}", originalFile.getPath(), e);
            return originalFile;
        }

        logger.info(
                STARTUP.getMarker(),
                "Span compaction completed for {}, new maximum generation is {}",
                originalFile.getPath(),
                maxGeneration);

        return newFile;
    }

    /**
     * Compact all PCES files within a directory tree.
     *
     * @param rootPath the root of the directory tree
     */
    public static void compactPreconsensusEventFiles(@NonNull final Path rootPath) {
        final List<PreconsensusEventFile> files = new ArrayList<>();
        try (final Stream<Path> fileStream = Files.walk(rootPath)) {
            fileStream
                    .filter(f -> !Files.isDirectory(f))
                    .filter(f -> f.toString().endsWith(PreconsensusEventFile.EVENT_FILE_EXTENSION))
                    .map(PreconsensusEventFileManager::parseFile)
                    .filter(Objects::nonNull)
                    .sorted()
                    .forEachOrdered(files::add);
        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "Failed to walk directory tree {}", rootPath, e);
        }

        long previousMaximumGeneration = 0;
        for (final PreconsensusEventFile file : files) {
            final PreconsensusEventFile compactedFile = compactPreconsensusEventFile(file, previousMaximumGeneration);
            previousMaximumGeneration = compactedFile.getMaximumGeneration();
        }
    }
}
