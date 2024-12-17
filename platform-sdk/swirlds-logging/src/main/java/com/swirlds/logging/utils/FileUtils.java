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

public class FileUtils {
    private FileUtils() {}

    /**
     * Given a file path, checks if the parent directory exist If it doesn't exist, creates it.
     *
     * @param path the path to check
     * @throws IllegalStateException if the parent dir exists, but it can be written to or exist and is not a directory
     */
    public static void checkOrCreateParentDirectory(final @NonNull Path path) {
        try {
            String pathString = path.toFile().getCanonicalPath();
            File file = new File(pathString);
            File dir = file.getParentFile();

            if (!dir.exists()) {
                Files.createDirectories(dir.toPath());
            } else if (dir.exists() && !dir.isDirectory()) {
                throw new IllegalStateException("Path for Log directory is not a directory. Path:" + dir);
            } else if (!dir.canWrite()) {
                throw new IllegalStateException("Cannot write log directory. Dir:" + dir);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write to log directory. Path:" + path, e);
        }
    }
}
