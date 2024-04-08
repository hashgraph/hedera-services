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

package com.swirlds.logging.benchmark.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Convenience methods for handling logFiles pre and after benchmark runs
 */
public class LogFiles {

    public static final String LOGGING_FOLDER = "logging-out";

    private LogFiles() {}

    /**
     * Provides the path to the log file based on the implementationName of the logging system under benchmark
     * {@code implementationName} and the type of benchmark {@code type}. Previously deleting the file if exists in the
     * FS.
     */
    @NonNull
    public static String provideLogFilePath(
            final @NonNull String implementationName, final @NonNull String type, final @NonNull String mode) {
        final String path = getPath(implementationName, type, mode);
        deleteFile(path);
        return path;
    }

    /**
     * Provides the path to the log file based on the implementation of the logging system under benchmark
     * {@code implementationName} and the type of benchmark {@code type}
     */
    @NonNull
    public static String getPath(final @NonNull String implementation, final @NonNull String type, final String mode) {
        final long pid = ProcessHandle.current().pid();
        return LOGGING_FOLDER + File.separator + "benchmark-" + pid + "-" + implementation + "-" + type + "-" + mode
                + ".log";
    }

    /**
     * Deletes the file
     */
    public static void deleteFile(final @NonNull String logFile) {
        try {
            Files.deleteIfExists(Path.of(logFile));
        } catch (IOException e) {
            throw new RuntimeException("Can not delete old log file", e);
        }
    }

    /**
     * If exists and is possible, remove the {@code LOGGING_FOLDER} dir and all its content
     */
    public static void tryDeleteDirAndContent() {
        final Path path = Path.of(LOGGING_FOLDER);
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .map(File::getAbsolutePath)
                    .forEach(LogFiles::deleteFile);
        } catch (IOException e) {
            // do nothing
        }
    }
}
