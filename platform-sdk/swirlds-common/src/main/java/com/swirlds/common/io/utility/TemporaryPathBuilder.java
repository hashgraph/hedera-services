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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class provides utility methods for constructing temporary files.
 */
public class TemporaryPathBuilder {

    private static final Logger logger = LogManager.getLogger(TemporaryPathBuilder.class);

    private long nextFileId = 0;
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
     * Return a temporary file. File will not exist when this method returns. File is guaranteed to have a unique name.
     * File will not be automatically deleted until this JVM is restarted.
     *
     * @return a new temporary file
     */
    @NonNull
    public synchronized Path getTemporaryPath() throws IOException {
        return getTemporaryPath(null);
    }

    /**
     * Return a temporary file. File will not exist when this method returns. File is guaranteed to have a unique name.
     * File will not be automatically deleted until this JVM is restarted.
     *
     * @param postfix an optional postfix, helps to make temporary file directory easier to understand if a human ever
     *                looks at it directly. Ignored if null.
     * @return a new temporary file
     */
    @NonNull
    public synchronized Path getTemporaryPath(@Nullable final String postfix) throws IOException {
        final String fileName = nextFileId + (postfix == null ? "" : ("-" + postfix));
        nextFileId++;

        final Path temporaryFile = temporaryDataDirectory.resolve(fileName);
        if (exists(temporaryFile)) {
            throw new IOException("Name collision for temporary file " + temporaryFile);
        }

        return temporaryFile;
    }
}
