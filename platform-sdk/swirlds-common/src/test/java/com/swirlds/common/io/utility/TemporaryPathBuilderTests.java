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

package com.swirlds.common.io.utility;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemporaryPathBuilderTests {

    @TempDir
    Path testDirectory;

    @Test
    void basicBehaviorTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final Path temporaryDataDirectory = testDirectory.resolve("temporaryDataDirectory");

        final int iterations = 5;
        final int filesPerIteration = 5;

        for (int i = 0; i < iterations; i++) {
            final TemporaryPathBuilder builder = new TemporaryPathBuilder(temporaryDataDirectory);

            // temporary data directory should exist and be empty
            assertTrue(Files.exists(temporaryDataDirectory));
            assertTrue(Files.isDirectory(temporaryDataDirectory));
            assertTrue(Files.list(temporaryDataDirectory).findAny().isEmpty());

            final Set<Path> paths = new HashSet<>();
            for (int j = 0; j < filesPerIteration; j++) {
                final String tag = "tag" + random.nextInt();

                final Path temporaryPath = builder.getTemporaryPath(tag);

                // Path should end with the provided tag
                assertTrue(temporaryPath.getFileName().toString().endsWith(tag));

                // Path should be unique
                assertTrue(paths.add(temporaryPath));

                // Nothing should exist at the path yet
                assertTrue(Files.notExists(temporaryPath));

                final double choice = random.nextDouble();

                // Randomly create a file or a directory, or do nothing (which is legal)
                if (choice < 0.4) {
                    Files.createFile(temporaryPath);
                    assertTrue(Files.exists(temporaryPath));
                    assertTrue(Files.isRegularFile(temporaryPath));
                } else if (choice < 0.9) {
                    Files.createDirectory(temporaryPath);
                    assertTrue(Files.exists(temporaryPath));
                    assertTrue(Files.isDirectory(temporaryPath));
                }
            }
        }

        FileUtils.deleteDirectory(testDirectory);
    }
}
