/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.io.utility.FileUtils.deleteDirectoryAndLog;
import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static java.nio.file.Files.exists;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.io.config.TemporaryFileConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class provides utility methods for constructing temporary files.
 */
public final class TemporaryFileBuilder {

    private TemporaryFileBuilder() {}

    private static long nextFileId = 0;
    private static Path temporaryFileLocation = null;

    /**
     * Get the directory that holds all temporary files created by this utility.
     *
     * @return a directory where temporary files are stored
     */
    public static synchronized Path getTemporaryFileLocation() throws IOException {
        if (temporaryFileLocation == null) {
            final TemporaryFileConfig config = ConfigurationHolder.getConfigData(TemporaryFileConfig.class);
            final StateConfig stateConfig = ConfigurationHolder.getConfigData(StateConfig.class);
            overrideTemporaryFileLocation(getAbsolutePath(config.getTemporaryFilePath(stateConfig)));
        }

        return temporaryFileLocation;
    }

    /**
     * <p>
     * Update the location where temporary files are written. This method may be useful when writing unit tests.
     * </p>
     *
     * <p>
     * WARNING! Calling this method will cause any files in the original location to be deleted. It's ok to
     * set the temporary file location to something like "/myTemporaryFiles" or "~/myTemporaryFiles". If it's set
     * to something like "/" or "~/", EVERYTHING IN THOSE DIRECTORIES WILL BE DELETED!
     * </p>
     *
     * @param newTemporaryFileLocation
     * 		the new location where temporary files will be written
     */
    public static synchronized void overrideTemporaryFileLocation(final Path newTemporaryFileLocation)
            throws IOException {

        temporaryFileLocation = newTemporaryFileLocation.toAbsolutePath().normalize();
        if (exists(temporaryFileLocation)) {
            deleteDirectoryAndLog(temporaryFileLocation);
        }
        Files.createDirectories(temporaryFileLocation);
    }

    /**
     * Return a temporary file. File will not exist when this method returns. File is guaranteed to have a unique
     * name. File will not be automatically deleted until this JVM is restarted.
     *
     * @return a new temporary file
     */
    public static synchronized Path buildTemporaryFile() throws IOException {
        return buildTemporaryFile(null);
    }

    /**
     * Return a temporary file. File will not exist when this method returns. File is guaranteed to have a unique
     * name. File will not be automatically deleted until this JVM is restarted.
     *
     * @param postfix
     * 		an optional postfix, helps to make temporary file directory easier to understand
     * 		if a human ever looks at it directly. Ignored if null.
     * @return a new temporary file
     */
    public static synchronized Path buildTemporaryFile(final String postfix) throws IOException {
        final String fileName = nextFileId + (postfix == null ? "" : ("-" + postfix));
        nextFileId++;

        final Path temporaryFile = getTemporaryFileLocation().resolve(fileName);
        if (exists(temporaryFile)) {
            throw new IOException("Name collision for temporary file " + temporaryFile);
        }

        return temporaryFile;
    }

    /**
     * Return a temporary directory. Directory will exist when this method returns.
     * Directory is guaranteed to have a unique name.
     * Directory will not be automatically deleted until this JVM is restarted.
     *
     * @return a new temporary directory
     */
    public static synchronized Path buildTemporaryDirectory() throws IOException {
        return buildTemporaryDirectory(null);
    }

    /**
     * Return a temporary directory. Directory will exist when this method returns.
     * Directory is guaranteed to have a unique name.
     * Directory will not be automatically deleted until this JVM is restarted.
     *
     * @param postfix
     * 		an optional postfix, helps to make temporary file directory easier to understand
     * 		if a human ever looks at it directly. Ignored if null.
     * @return a new temporary directory
     */
    public static synchronized Path buildTemporaryDirectory(final String postfix) throws IOException {
        final Path directory = buildTemporaryFile(postfix);
        if (!exists(directory)) {
            Files.createDirectories(directory);
        }
        return directory;
    }
}
