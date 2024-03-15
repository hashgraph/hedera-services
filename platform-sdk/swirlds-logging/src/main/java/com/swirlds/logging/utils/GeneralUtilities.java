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

package com.swirlds.logging.utils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * General utilities class
 */
public class GeneralUtilities {
    private GeneralUtilities() {}

    /**
     * Creates a String of digits of the number and pads to the left with 0. Examples:
     * <ul>
     * <li>{@code toPaddedDigitsString(1, 1)} --> 1</li>
     * <li>{@code toPaddedDigitsString(1, 2)} --> 01</li>
     * <li>{@code toPaddedDigitsString(12, 1)} --> 2</li>
     * <li>{@code toPaddedDigitsString(12, 2)} --> 12</li>
     * <li>{@code toPaddedDigitsString(12, 3)} --> 012</li>
     * <li>{@code toPaddedDigitsString(123, 3)} --> 123</li>
     * <li>{@code toPaddedDigitsString(758, 4)} --> 0758</li>
     * </ul>
     *
     * @param number        The number to append in reverse order.
     * @param desiredLength The maximum length of the number to append.
     */
    public static String toPaddedDigitsString(final int number, final int desiredLength) {
        StringBuilder buffer = new StringBuilder();
        int actualLength = 0;
        int num = number;
        while ((num > 0) && actualLength < desiredLength) {
            int digit = num % 10;
            buffer.append(digit);
            num /= 10;
            actualLength++;
        }
        while (desiredLength > actualLength) {
            buffer.append(0);
            actualLength++;
        }
        return buffer.reverse().toString();
    }

    /**
     * Given a file path, checks if the parent directory exist If it doesn't exist, creates it.
     *
     * @param path the path to check
     * @throws IllegalStateException if the dir exists but it can be written to
     */
    public static void checkOrCreateParentDirectory(final @NonNull Path path) {
        try {
            String pathString = path.toFile().getCanonicalPath();
            File file = new File(pathString);
            File dir = file.getParentFile();

            if (!dir.exists()) {
                Files.createDirectories(dir.toPath());
            } else if (dir.exists() && !dir.isDirectory()) {
                throw new IllegalStateException("Path for Log directory is not a directory. Path=" + dir);
            } else if (!dir.canWrite()) {
                throw new IllegalStateException("Cannot write log directory " + dir);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write to log directory. Path=" + path, e);
        }
    }

    /**
     * Tries to rename the file using all possible strategies in case
     *
     * @param src         the file to be renamed
     * @param destination the desired destination of the src file
     * @throws IOException in case none of the strategies for renaming worked
     */
    public static void renameFile(@NonNull final File src, @NonNull final File destination) throws IOException {
        // Try old school renames
        if (!src.renameTo(destination)) {
            try {
                // Try new move
                Files.move(src.toPath(), destination.toPath());
            } catch (IOException e) {
                // Copy
                Files.copy(src.toPath(), destination.toPath());
                // Delete
                Files.deleteIfExists(src.toPath());
            }
        }
    }

    public static void delete(final Path pathFor) {
        try {
            Files.delete(pathFor);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot delete file ", e);
        }
    }
}
