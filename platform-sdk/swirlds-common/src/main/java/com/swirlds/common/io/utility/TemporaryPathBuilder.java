/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.io.utility;

import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static java.nio.file.Files.exists;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class provides utility methods for constructing temporary files.
 */
public class TemporaryPathBuilder {

    private static final Logger logger = LogManager.getLogger(TemporaryPathBuilder.class);

    private final AtomicLong nextFileId = new AtomicLong(0);
    private final Path temporaryDataDirectory;

    /**
     * Constructor.
     *
     * @param temporaryDataDirectory the directory where temporary files will be created. If there are files already in
     *                               this location they will be deleted. Directory will be created in this location if
     *                               it does not exist.
     * @throws IOException if there is a problem setting up the temporary directory
     */
    public TemporaryPathBuilder(@NonNull final Path temporaryDataDirectory) throws IOException {
        this.temporaryDataDirectory = Objects.requireNonNull(temporaryDataDirectory);
        if (exists(temporaryDataDirectory)) {
            logger.info(STARTUP.getMarker(), "Deleting temporary data directory {}", temporaryDataDirectory);
            FileUtils.deleteDirectory(temporaryDataDirectory);
        }
        Files.createDirectories(temporaryDataDirectory);
    }

    /**
     * Return a path where temporary files can be created. Nothing will exist at this path when this method returns.
     * Path is guaranteed to have a unique name. Any files or directories stored at this path will be automatically
     * deleted the next time the JVM is restarted.
     *
     * @return a new temporary path
     */
    @NonNull
    public Path getTemporaryPath() throws IOException {
        return getTemporaryPath(null);
    }

    /**
     * Return a path where temporary files can be created. Nothing will exist at this path when this method returns.
     * Path is guaranteed to have a unique name. Any files or directories stored at this path will be automatically
     * deleted the next time the JVM is restarted.
     *
     * @param postfix an optional postfix to append to the file name. This is intended to improve readability of the
     *                file system by a human.
     * @return a new temporary path
     */
    @NonNull
    public Path getTemporaryPath(@Nullable final String postfix) throws IOException {
        final String fileName = nextFileId.getAndIncrement() + (postfix == null ? "" : ("-" + postfix));

        final Path temporaryFile = temporaryDataDirectory.resolve(fileName);
        if (exists(temporaryFile)) {
            throw new IOException("Name collision for temporary file " + temporaryFile);
        }

        return temporaryFile;
    }
}
