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

package com.swirlds.base.utility;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * Provides file system-related utility methods.
 */
public final class FileSystem {
    /**
     * Private constructor to prevent utility class instantiation.
     */
    private FileSystem() {}

    /**
     * Evaluates a given {@code path} to ensure it exists.
     *
     * <p>
     * This method includes retry logic to handle files which may not yet exist but will become available within a
     * given threshold. Examples of these cases are Kubernetes projected volumes and persistent volumes.
     *
     * <p>
     * This overloaded method uses a default of 20 attempts with a 2-second delay between each attempt.
     *
     * @param path the path to check for existence.
     * @return true if the file exists before the retries have been exhausted; otherwise false.
     * @throws NullPointerException if the {@code path} argument is a {@code null} value.
     */
    public static boolean waitForPathPresence(@NonNull Path path) {
        return waitForPathPresence(path, 20, 2_000);
    }

    /**
     * Evaluates a given {@code path} to ensure it exists.
     *
     * <p>
     * This method includes retry logic to handle files which may not yet exist but will become available within a
     * given threshold. Examples of these cases are Kubernetes projected volumes and persistent volumes.
     *
     * @param path        the path to check for existence.
     * @param maxAttempts the maximum number of retry attempts.
     * @param delayMs     the delay between retry attempts.
     * @return true if the file exists before the retries have been exhausted; otherwise false.
     * @throws IllegalArgumentException if the {@code maxAttempts} argument is less than or equal to zero (0) or the
     *                                 {@code delayMs} argument is less than zero (0).
     * @throws NullPointerException    if the {@code path} argument is a {@code null} value.
     */
    public static boolean waitForPathPresence(@NonNull Path path, int maxAttempts, int delayMs) {
        Objects.requireNonNull(path, "path must not be null");
        try {
            return Retry.check(FileSystem::waitForPathPresenceInternal, path, maxAttempts, delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            return false;
        }
    }

    /**
     * Internal method to evaluate a given {@code path} to ensure it is present.
     *
     * @param path the path to check for existence.
     * @return true if the file, directory, block device, or symlink exists and is not empty if the path references a
     * file; otherwise false.
     */
    private static boolean waitForPathPresenceInternal(@NonNull final Path path) {
        // If the path does not exist, then we should consider it not present
        if (!Files.exists(path)) {
            return false;
        }

        if (Files.isRegularFile(path)) {
            try {
                // If the path exists and is a file, then we must check the size to ensure it is not empty
                return Files.size(path) > 0;
            } catch (IOException ignored) {
                // If an exception occurs while checking the file size, then we should consider the file not present
                return false;
            }
        }

        // If the path exists and is a directory, block device or symlink, then we can consider it present
        return true;
    }
}
