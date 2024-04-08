/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.util;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.logging.legacy.LogMarker;
import com.swirlds.platform.config.PathsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Writes marker files with the given filename to disk in the configured directory.  If there is no configured
 * directory, no marker files are written.
 */
public class MarkerFileWriter {

    /**
     * The logger for this class.
     */
    private static final Logger LOG = LogManager.getLogger(MarkerFileWriter.class);

    /**
     * The directory where the marker files are written.  If null, no marker files are written.
     */
    private final File markerFileDirectory;

    /**
     * Creates a new {@link MarkerFileWriter} with the given {@link PlatformContext}.  If the marker file writer is
     * enabled, files are written to the configured directory.
     *
     * @param platformContext the platform context containing configuration.
     */
    public MarkerFileWriter(@NonNull final PlatformContext platformContext) {
        final Path markerFileDirectoryPath = platformContext
                .getConfiguration()
                .getConfigData(PathsConfig.class)
                .getMarkerFilesDir();
        File directory = null;
        if (markerFileDirectoryPath != null) {
            try {
                Files.createDirectories(markerFileDirectoryPath);
                directory = markerFileDirectoryPath.toFile();
            } catch (final IOException e) {
                LOG.error(
                        LogMarker.EXCEPTION.getMarker(),
                        "Failed to create marker file directory: {}",
                        markerFileDirectoryPath,
                        e);
            }
            if (!Files.isDirectory(markerFileDirectoryPath)) {
                directory = null;
                LOG.error(
                        LogMarker.ERROR.getMarker(),
                        "failed to create marker file directory, path is already a file: {}",
                        markerFileDirectoryPath);
            }
        }
        markerFileDirectory = directory;
    }

    /**
     * Writes a marker file with the given filename to the configured directory.
     *
     * @param filename the name of the marker file to write.
     */
    public void writeMarkerFile(@NonNull final String filename) {
        if (markerFileDirectory == null) {
            // Configuration did not set a marker file directory.  No need to write marker file.
            return;
        }
        final File markerFile = new File(markerFileDirectory, filename);
        if (markerFile.exists()) {
            // No need to create file when it already exists.
            return;
        }
        try {
            Files.createFile(markerFile.toPath());
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create marker file: " + markerFile, e);
        }
    }

    /**
     * Returns the directory where the marker files are written.  May be null.
     *
     * @return the directory where the marker files are written.  This may be null.
     */
    public Path getMarkerFileDirectory() {
        if (markerFileDirectory == null) {
            return null;
        }
        return markerFileDirectory.toPath();
    }
}
