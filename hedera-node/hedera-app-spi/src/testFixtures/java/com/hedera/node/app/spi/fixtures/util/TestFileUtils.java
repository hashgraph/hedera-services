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

package com.hedera.node.app.spi.fixtures.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Utility class for file operations in tests.
 * <p></p>
 * This was copied over from mono-service. When mono-service module goes away, it will be deleted from there and this
 * will be the only copy.
 */
public class TestFileUtils {
    private TestFileUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static void blowAwayDirIfPresent(String loc) throws IOException {
        if (!new File(loc).exists()) {
            return;
        }

        Files.walkFileTree(Paths.get(loc), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                if (e == null) {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                } else {
                    throw e;
                }
            }
        });
    }

    /** Convenience method for adding the OS file separator to a path */
    public static String toPath(final File dir, final String filename) {
        var withSeparator = filename.startsWith(File.separator) ? filename : File.separator + filename;
        return dir.getPath() + withSeparator;
    }

    public static String toPath(final String dir, final String filename) {
        return toPath(new File(dir), filename);
    }

    public static String toPath(final Path dir, final String filename) {
        return toPath(dir.toFile(), filename);
    }
}
