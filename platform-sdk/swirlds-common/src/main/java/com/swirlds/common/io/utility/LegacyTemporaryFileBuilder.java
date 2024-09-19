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

import static com.swirlds.common.io.utility.FileUtils.deleteDirectoryAndLog;
import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static java.nio.file.Files.exists;

import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.config.api.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;

/**
 * This class provides utility methods for constructing temporary files.
 *
 * @deprecated use the {@link com.swirlds.common.io.filesystem.FileSystemManager} provided
 * by {@link com.swirlds.common.context.PlatformContext} instead
 */
@Deprecated
public final class LegacyTemporaryFileBuilder {

    private LegacyTemporaryFileBuilder() {}

    private static long nextFileId = 0;
    private static Path temporaryFileLocation = null;

    // TODO: update docs
    /**
     * Get the directory that holds all temporary files created by this utility.
     *
     * @return a directory where temporary files are stored
     */
    public static synchronized Path getTemporaryFileLocation(final Configuration configuration) throws IOException {
        if (temporaryFileLocation == null) {
            final TemporaryFileConfig config = configuration.getConfigData(TemporaryFileConfig.class);
            final StateCommonConfig stateConfig = configuration.getConfigData(StateCommonConfig.class);
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

    // TODO: update docs
    /**
     * Return a temporary file. File will not exist when this method returns. File is guaranteed to have a unique
     * name. File will not be automatically deleted until this JVM is restarted.
     *
     * @return a new temporary file
     * @deprecated use {@link com.swirlds.common.io.filesystem.FileSystemManager#resolveNewTemp(String)} instead.
     */
    @Deprecated
    public static synchronized Path buildTemporaryFile(Configuration configuration) throws IOException {
        return buildTemporaryFile(null, configuration);
    }

    // TODO: update docs
    /**
     * Return a temporary file. File will not exist when this method returns. File is guaranteed to have a unique
     * name. File will not be automatically deleted until this JVM is restarted.
     *
     * @param postfix
     * 		an optional postfix, helps to make temporary file directory easier to understand
     * 		if a human ever looks at it directly. Ignored if null.
     * @return a new temporary file
     * @deprecated use {@link com.swirlds.common.io.filesystem.FileSystemManager#resolveNewTemp(String)} instead.
     */
    @Deprecated
    public static synchronized Path buildTemporaryFile(final String postfix, final Configuration configuration) throws IOException {
        final String fileName = nextFileId + (postfix == null ? "" : ("-" + postfix));
        nextFileId++;

        final Path temporaryFile = getTemporaryFileLocation(configuration).resolve(fileName);
        if (exists(temporaryFile)) {
            throw new IOException("Name collision for temporary file " + temporaryFile);
        }

        return temporaryFile;
    }

    // TODO: update docs
    /**
     * Return a temporary directory. Directory will exist when this method returns.
     * Directory is guaranteed to have a unique name.
     * Directory will not be automatically deleted until this JVM is restarted.
     *
     * @return a new temporary directory
     * @deprecated use {@link com.swirlds.common.io.filesystem.FileSystemManager#resolveNewTemp(String)} instead
     * and then create a directory using {@link Files#createDirectory(Path, FileAttribute[])}
     */
    @Deprecated
    public static synchronized Path buildTemporaryDirectory(final Configuration configuration) throws IOException {
        return buildTemporaryDirectory(null, configuration);
    }

    // TODO: update docs
    /**
     * Return a temporary directory. Directory will exist when this method returns.
     * Directory is guaranteed to have a unique name.
     * Directory will not be automatically deleted until this JVM is restarted.
     *
     * @param postfix
     * 		an optional postfix, helps to make temporary file directory easier to understand
     * 		if a human ever looks at it directly. Ignored if null.
     * @return a new temporary directory
     * @deprecated use {@link com.swirlds.common.io.filesystem.FileSystemManager#resolveNewTemp(String)} instead
     * and then create a directory using {@link Files#createDirectory(Path, FileAttribute[])}
     */
    @Deprecated
    public static synchronized Path buildTemporaryDirectory(final String postfix, final Configuration configuration) throws IOException {
        final Path directory = buildTemporaryFile(postfix, configuration);
        if (!exists(directory)) {
            Files.createDirectories(directory);
        }
        return directory;
    }
}
