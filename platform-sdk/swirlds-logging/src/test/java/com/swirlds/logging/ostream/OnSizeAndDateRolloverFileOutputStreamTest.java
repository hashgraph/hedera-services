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

package com.swirlds.logging.ostream;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OnSizeAndDateRolloverFileOutputStreamTest {

    private static final String TEST_FOLDER = "testFolder";
    private static final String FILE_BASE_NAME = "testFile";
    private static final long MAX_FILE_SIZE = 1000; // 1 KB
    private static final boolean APPEND_TO_EXISTING = false;

    private static Path testFolder;

    @BeforeAll
    public static void setUp() throws IOException {
        testFolder = Files.createTempDirectory(TEST_FOLDER);
    }

    @AfterAll
    public static void tearDown() throws IOException {
        try (Stream<Path> paths = Files.walk(testFolder)) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    @Test
    public void testSizeBaseRolling() throws IOException, InterruptedException {
        try (OutputStream rolloverOutputStream = new OnSizeAndDateRolloverFileOutputStream(
                testFolder.resolve(FILE_BASE_NAME), MAX_FILE_SIZE, APPEND_TO_EXISTING)) {

            // Write enough data to fill one file
            writeDataToFile(rolloverOutputStream, "Some data to fill one file.", MAX_FILE_SIZE);

            // Wait for a short duration to ensure that the file creation time is different
            Thread.sleep(1000);

            // Write more data to trigger rolling
            writeDataToFile(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE * 2);

            // Verify the existence of the rolled file
            String expectedRolledFileName = FILE_BASE_NAME + "-" + getDateAsString(Instant.now());
            Path rolledFilePath = testFolder.resolve(expectedRolledFileName + ".000");
            assertTrue(Files.exists(rolledFilePath), "no file %s found".formatted(rolledFilePath.toString()));
            rolledFilePath = testFolder.resolve(expectedRolledFileName + ".001");
            assertTrue(Files.exists(rolledFilePath));
        }
    }

    private void writeDataToFile(OutputStream outputStream, String data, long dataSize) throws IOException {
        byte[] bytes = new byte[(int) dataSize];
        for (int i = 0; i < dataSize; i++) {
            bytes[i] = (byte) data.charAt(i % data.length());
        }
        outputStream.write(bytes);
        outputStream.flush();
    }

    private String getDateAsString(Instant instant) {
        return instant.toString().substring(0, 10); // Get date part in "yyyy-MM-dd" format
    }
}
