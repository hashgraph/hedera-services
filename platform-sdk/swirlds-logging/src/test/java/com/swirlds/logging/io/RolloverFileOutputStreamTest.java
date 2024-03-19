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

package com.swirlds.logging.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.test.fixtures.time.FakeTime;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

class RolloverFileOutputStreamTest {

    private static final String FILE_BASE_NAME = "testFile";
    private static final long MAX_FILE_SIZE = 1000; // 1 KB
    private static final boolean APPEND_TO_EXISTING = false;
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    @TempDir(cleanup = CleanupMode.ALWAYS)
    private Path testFolder;

    @Test
    public void testDateSizeBaseRollingOverriding() throws IOException {
        // given: a RolloverFileOutputStream configure to roll by date and size with max 1 roll file
        // when: Writing just enough data to trigger the rolling 3 times plus one below the limit
        // then: there should only exist the index 0. The base file should contain the latest under limit write

        // given
        try (OutputStream rolloverOutputStream = new RolloverFileOutputStream(
                testFolder.resolve(FILE_BASE_NAME), MAX_FILE_SIZE, APPEND_TO_EXISTING, 1, DATE_PATTERN)) {

            // when
            // Write enough data to fill one file
            writeDataToOutputStream(rolloverOutputStream, "Some data to fill one file.", MAX_FILE_SIZE);
            // Write more data to trigger rolling
            writeDataToOutputStream(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);
            writeDataToOutputStream(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);
            writeDataToOutputStream(
                    rolloverOutputStream, "More data to trigger rolling.".getBytes(StandardCharsets.UTF_8));

            // then
            // Verify the existence of the rolled file
            String expectedRolledFileName = FILE_BASE_NAME + "-" + getDateAsString(Instant.now());
            Path rolledFilePath = testFolder.resolve(expectedRolledFileName + ".0");
            assertTrue(Files.exists(testFolder.resolve(FILE_BASE_NAME)), "no file %s found".formatted(FILE_BASE_NAME));
            assertEquals("More data to trigger rolling.", Files.readString(testFolder.resolve(FILE_BASE_NAME)));
            assertTrue(Files.exists(rolledFilePath), "no file %s found".formatted(rolledFilePath.toString()));
            assertFalse(Files.exists(testFolder.resolve(expectedRolledFileName + ".1")));
            assertEquals(MAX_FILE_SIZE, rolledFilePath.toFile().length());
        }
    }

    @Test
    public void testDateSizeBaseRollingNotOverridingExistingFiles() throws IOException {
        // given: a RolloverFileOutputStream configure to roll by size with max 3 roll file.
        //   And 2 previously existing rolling files
        // when: Writing just enough data to trigger the rolling 1 time
        // then: the previously existing files should not be modified and there should exist a roll file with index 2

        // given
        createRolledFiles(testFolder.resolve(FILE_BASE_NAME), "0");
        createRolledFiles(testFolder.resolve(FILE_BASE_NAME), "1");

        try (OutputStream rolloverOutputStream = new RolloverFileOutputStream(
                testFolder.resolve(FILE_BASE_NAME), MAX_FILE_SIZE, APPEND_TO_EXISTING, 3, null)) {

            // when
            // Write enough data to fill one file
            writeDataToOutputStream(rolloverOutputStream, "Some data to fill one file.", MAX_FILE_SIZE);

            // then
            // Verify the existence of the rolled file
            Path rolledFilePath = testFolder.resolve(FILE_BASE_NAME + ".0");
            assertTrue(Files.exists(rolledFilePath), "no file %s found".formatted(rolledFilePath.toString()));
            assertEquals(0, rolledFilePath.toFile().length());
            rolledFilePath = testFolder.resolve(FILE_BASE_NAME + ".1");
            assertTrue(Files.exists(rolledFilePath), "no file %s found".formatted(rolledFilePath.toString()));
            assertEquals(0, rolledFilePath.toFile().length());
            rolledFilePath = testFolder.resolve(FILE_BASE_NAME + ".2");
            assertTrue(Files.exists(rolledFilePath), "no file %s found".formatted(rolledFilePath.toString()));
            assertEquals(MAX_FILE_SIZE, rolledFilePath.toFile().length());
        }
    }

    @Test
    public void testDateSizeBaseRollingOverridingFirstExistingFile() throws IOException {
        // given: a RolloverFileOutputStream configure to roll by size with max 2 roll file.
        //   And 2 previously existing rolling files
        // when: Writing just enough data to trigger the rolling 1 time
        // then: the first previously existing file should be deleted and no newer roll file should exist

        // given
        createRolledFiles(testFolder.resolve(FILE_BASE_NAME), "0");
        createRolledFiles(testFolder.resolve(FILE_BASE_NAME), "1");

        try (OutputStream rolloverOutputStream = new RolloverFileOutputStream(
                testFolder.resolve(FILE_BASE_NAME), MAX_FILE_SIZE, APPEND_TO_EXISTING, 2, null)) {

            // when
            // Write enough data to fill one file
            writeDataToOutputStream(rolloverOutputStream, "Some data to fill one file.", MAX_FILE_SIZE);

            // then
            // Verify the existence of the rolled file
            Path rolledFilePath = testFolder.resolve(FILE_BASE_NAME + ".0");
            assertTrue(Files.exists(rolledFilePath), "no file %s found".formatted(rolledFilePath.toString()));
            assertEquals(MAX_FILE_SIZE, rolledFilePath.toFile().length());
            rolledFilePath = testFolder.resolve(FILE_BASE_NAME + ".1");
            assertTrue(Files.exists(rolledFilePath), "no file %s found".formatted(rolledFilePath.toString()));
            assertEquals(0, rolledFilePath.toFile().length());
            rolledFilePath = testFolder.resolve(FILE_BASE_NAME + ".2");
            assertFalse(
                    Files.exists(rolledFilePath),
                    "no file %s should have been found".formatted(rolledFilePath.toString()));
        }
    }

    @Test
    public void tesSizeBaseRollingOverriding() throws IOException {
        // given: a RolloverFileOutputStream configure to roll by size with max 1 roll file.
        // when: Writing just enough data to trigger the rolling 3 times
        // then: there should only exist the index 0. The base file should contain the latest under limit write

        // given
        try (OutputStream rolloverOutputStream = new RolloverFileOutputStream(
                testFolder.resolve(FILE_BASE_NAME), MAX_FILE_SIZE, APPEND_TO_EXISTING, 1, null)) {

            // when
            // Write enough data to fill one file
            writeDataToOutputStream(rolloverOutputStream, "Some data to fill one file.", MAX_FILE_SIZE);
            // Write more data to trigger rolling
            writeDataToOutputStream(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);
            writeDataToOutputStream(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);

            // then
            // Verify the existence of the rolled file
            Path rolledFilePath = testFolder.resolve(FILE_BASE_NAME + ".0");
            assertTrue(Files.exists(rolledFilePath), "no file %s found".formatted(rolledFilePath.toString()));
            assertFalse(Files.exists(testFolder.resolve(FILE_BASE_NAME + ".1")));
            assertEquals(MAX_FILE_SIZE, rolledFilePath.toFile().length());
        }
    }

    @Test
    public void tesDateSizeBaseRollingOverridingOnlyWithingDate() throws IOException {
        // given: a RolloverFileOutputStream configure to roll by size and date with max 1 roll file.
        //    and a fake instant provider.
        // when: Writing just enough data to trigger the rolling multiple times in original time and one after 1 unit of
        // time elapsed.
        // then: there should only exist the index 0 for both days.

        // given
        FakeTime timeProvider = new FakeTime(Instant.now(), Duration.ZERO);
        try (OutputStream rolloverOutputStream = new RolloverFileOutputStream(
                testFolder.resolve(FILE_BASE_NAME),
                MAX_FILE_SIZE,
                APPEND_TO_EXISTING,
                1,
                DATE_PATTERN,
                timeProvider::now)) {

            // when
            // Write enough data to fill one file
            writeDataToOutputStream(rolloverOutputStream, "Some data to fill one file.", MAX_FILE_SIZE);
            // Write more data to trigger rolling
            writeDataToOutputStream(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);
            writeDataToOutputStream(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);

            // Simulate the passing of time
            timeProvider.tick(Duration.of(1, ChronoUnit.DAYS));
            writeDataToOutputStream(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);
            writeDataToOutputStream(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);

            // then
            // Verify the existence of the rolled file
            String expectedRolledFileName = FILE_BASE_NAME + "-" + getDateAsString(Instant.now());
            Path rolledFilePath = testFolder.resolve(expectedRolledFileName + ".0");
            assertTrue(Files.exists(rolledFilePath), "no file %s found".formatted(rolledFilePath.toString()));
            assertFalse(
                    Files.exists(testFolder.resolve(expectedRolledFileName + ".1")),
                    "file %s should not have been found".formatted(expectedRolledFileName + ".1"));

            assertEquals(MAX_FILE_SIZE, rolledFilePath.toFile().length());
            expectedRolledFileName =
                    FILE_BASE_NAME + "-" + getDateAsString(Instant.now().plus(1, ChronoUnit.DAYS));
            assertTrue(Files.exists(testFolder.resolve(expectedRolledFileName + ".0")));
        }
    }

    @Test
    public void testDateSizeBaseRollingNoOverriding() throws IOException {
        // given: a RolloverFileOutputStream configure to roll by size and date with max 2 roll file.
        // when: Writing just enough data to trigger the rolling 2
        // then: there should exist the index 0 and index 1

        // given
        try (OutputStream rolloverOutputStream = new RolloverFileOutputStream(
                testFolder.resolve(FILE_BASE_NAME), MAX_FILE_SIZE, APPEND_TO_EXISTING, 2, DATE_PATTERN)) {

            // when
            // Write enough data to fill one file
            writeDataToOutputStream(rolloverOutputStream, "Some data to fill one file.", MAX_FILE_SIZE);

            // Write more data to trigger rolling
            writeDataToOutputStream(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE * 2);

            // then
            // Verify the existence of the rolled file
            String expectedRolledFileName = FILE_BASE_NAME + "-" + getDateAsString(Instant.now());
            Path rolledFilePath = testFolder.resolve(expectedRolledFileName + ".0");
            assertTrue(Files.exists(rolledFilePath), "no file %s found".formatted(rolledFilePath.toString()));
            rolledFilePath = testFolder.resolve(expectedRolledFileName + ".1");
            assertTrue(Files.exists(rolledFilePath));
        }
    }

    @Test
    void testWriteArrayFlushAndClose() throws IOException {
        // Creating an instance of RolloverFileOutputStream
        // Writing data to the stream
        // Flushing the stream
        // Closing the stream
        // Verifying that the log file exists and contains the written data
        // The written data should be in the file

        // given:
        Path logFilePath = testFolder.resolve(FILE_BASE_NAME);
        RolloverFileOutputStream outputStream = new RolloverFileOutputStream(logFilePath, MAX_FILE_SIZE, true, 1, null);
        // when:
        outputStream.write("Test data".getBytes(), 5, 4);
        outputStream.flush();
        outputStream.close();
        // then:
        assertTrue(Files.exists(logFilePath));
        assertEquals("data", Files.readString(logFilePath));
    }

    @Test
    void testWriteFlushAndClose() throws IOException {
        // Creating an instance of RolloverFileOutputStream
        // Writing data to the stream
        // Flushing the stream
        // Closing the stream
        // Verifying that the log file exists and contains the written data
        // The written data should be in the file

        // given:
        Path logFilePath = testFolder.resolve(FILE_BASE_NAME);
        RolloverFileOutputStream outputStream = new RolloverFileOutputStream(logFilePath, MAX_FILE_SIZE, true, 1, null);

        // when:
        String testData = "Test data";
        writeDataToOutputStream(outputStream, testData.getBytes());
        outputStream.flush();
        outputStream.close();

        // then:
        assertTrue(Files.exists(logFilePath));
        assertEquals(testData, Files.readString(logFilePath));
    }

    @Test
    void testWriteSingleFlushAndClose() throws IOException {
        // Creating an instance of RolloverFileOutputStream
        // Writing data to the stream
        // Flushing the stream
        // Closing the stream
        // Verifying that the log file exists and contains the written data
        // The written data should be in the file

        // given:
        Path logFilePath = testFolder.resolve(FILE_BASE_NAME);
        RolloverFileOutputStream outputStream = new RolloverFileOutputStream(logFilePath, MAX_FILE_SIZE, true, 1, null);
        // when:
        outputStream.write('c');
        outputStream.flush();
        outputStream.close();
        // then:
        assertTrue(Files.exists(logFilePath));
        assertEquals("c", Files.readString(logFilePath));
    }

    @Test
    void testAppendWriteSingleFlushAndClose() throws IOException {
        // Creating an instance of RolloverFileOutputStream
        // Writing data to the stream with previously existing content and append mode on
        // Flushing the stream
        // Closing the stream
        // Verifying that the log file exists and contains the written data
        // both the previous data and the new written data should be in the file

        // given:
        Path logFilePath = testFolder.resolve(FILE_BASE_NAME);
        String initialContent = "Initial Content String\n";
        Files.writeString(logFilePath, initialContent);

        RolloverFileOutputStream outputStream =
                new RolloverFileOutputStream(logFilePath, initialContent.length() * 2 + 1, true, 1, null);

        // when:
        writeDataToOutputStream(outputStream, initialContent.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        outputStream.close();

        // then:
        assertTrue(Files.exists(logFilePath));
        assertEquals(initialContent + initialContent, Files.readString(logFilePath));
    }

    @Test
    void testNoAppendWriteSingleFlushAndClose() throws IOException {
        // Creating an instance of RolloverFileOutputStream
        // Writing data to the stream with previously existing content and append mode off
        // Flushing the stream
        // Closing the stream
        // Verifying that the log file exists and contains the written data
        // only the new written data should be in the file

        // given
        Path logFilePath = testFolder.resolve(FILE_BASE_NAME);
        String initialContent = "Initial Content String\n";
        Files.writeString(logFilePath, initialContent);

        RolloverFileOutputStream outputStream =
                new RolloverFileOutputStream(logFilePath, initialContent.length() * 2 + 1, false, 1, null);

        // when
        writeDataToOutputStream(outputStream, initialContent.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        outputStream.close();

        // then
        assertTrue(Files.exists(logFilePath));
        assertEquals(initialContent, Files.readString(logFilePath));
    }

    @Test
    void testUnwritableFile() throws IOException {
        // Creating an non-writable path
        // When used to instantiate a  RolloverFileOutputStream
        // Should throw an IllegalStateException

        Path logFilePath = testFolder.resolve(FILE_BASE_NAME + "_tmp");
        Files.createDirectory(logFilePath);

        // Attempt to write to the file and assert that an IllegalStateException is thrown
        assertThrows(IllegalStateException.class, () -> {
            // Try to write something to the file
            try (RolloverFileOutputStream v =
                    new RolloverFileOutputStream(logFilePath, MAX_FILE_SIZE, true, 1, null); ) {}
        });
    }

    private static void writeDataToOutputStream(final OutputStream outputStream, final String data, long dataSize)
            throws IOException {
        byte[] bytes = new byte[(int) dataSize];
        for (int i = 0; i < dataSize; i++) {
            bytes[i] = (byte) data.charAt(i % data.length());
        }
        outputStream.write(bytes);
        outputStream.flush();
    }

    private static void writeDataToOutputStream(final OutputStream outputStream, final byte[] initialContent)
            throws IOException {
        outputStream.write(initialContent);
    }

    /**
     * Get date part in "yyyy-MM-dd" format
     */
    private static String getDateAsString(Instant instant) {
        return instant.toString().substring(0, 10);
    }

    /**
     * Create rolled files with index suffixes
     */
    private static void createRolledFiles(Path logPath, String index) throws IOException {
        final String buffer = logPath.toString() + "." + index;
        Files.createFile(Paths.get(buffer));
    }
}
