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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.test.fixtures.time.FakeTime;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RolloverFileOutputStreamTest {

    private static final String TEST_FOLDER = "testFolder";
    private static final String FILE_BASE_NAME = "testFile";
    private static final long MAX_FILE_SIZE = 1000; // 1 KB
    private static final boolean APPEND_TO_EXISTING = false;
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    private Path testFolder;

    @BeforeEach
    public void setUp() throws IOException {
        testFolder = Files.createTempDirectory(TEST_FOLDER);
    }

    @AfterEach
    public void tearDown() throws IOException {
        try (Stream<Path> paths = Files.walk(testFolder)) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    @Test
    public void testDateSizeBaseRollingOverriding() throws IOException {

        // given
        try (OutputStream rolloverOutputStream = new RolloverFileOutputStream(
                testFolder.resolve(FILE_BASE_NAME), MAX_FILE_SIZE, APPEND_TO_EXISTING, 1, DATE_PATTERN)) {

            // when
            // Write enough data to fill one file
            writeDataToFile(rolloverOutputStream, "Some data to fill one file.", MAX_FILE_SIZE);
            // Write more data to trigger rolling
            writeDataToFile(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);
            writeDataToFile(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);

            // then
            // Verify the existence of the rolled file
            String expectedRolledFileName = FILE_BASE_NAME + "-" + getDateAsString(Instant.now());
            Path rolledFilePath = testFolder.resolve(expectedRolledFileName + ".0");
            assertTrue(Files.exists(rolledFilePath), "no file %s found".formatted(rolledFilePath.toString()));
            assertFalse(Files.exists(testFolder.resolve(expectedRolledFileName + ".1")));
            assertEquals(MAX_FILE_SIZE, rolledFilePath.toFile().length());
        }
    }

    @Test
    public void testDateSizeBaseRollingNotOverridingExistingFiles() throws IOException {

        // given
        createRolledFiles(testFolder.resolve(FILE_BASE_NAME), "0");
        createRolledFiles(testFolder.resolve(FILE_BASE_NAME), "1");

        try (OutputStream rolloverOutputStream = new RolloverFileOutputStream(
                testFolder.resolve(FILE_BASE_NAME), MAX_FILE_SIZE, APPEND_TO_EXISTING, 3, null)) {

            // when
            // Write enough data to fill one file
            writeDataToFile(rolloverOutputStream, "Some data to fill one file.", MAX_FILE_SIZE);

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

        // given
        createRolledFiles(testFolder.resolve(FILE_BASE_NAME), "0");
        createRolledFiles(testFolder.resolve(FILE_BASE_NAME), "1");

        try (OutputStream rolloverOutputStream = new RolloverFileOutputStream(
                testFolder.resolve(FILE_BASE_NAME), MAX_FILE_SIZE, APPEND_TO_EXISTING, 2, null)) {

            // when
            // Write enough data to fill one file
            writeDataToFile(rolloverOutputStream, "Some data to fill one file.", MAX_FILE_SIZE);

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
        // given
        try (OutputStream rolloverOutputStream = new RolloverFileOutputStream(
                testFolder.resolve(FILE_BASE_NAME), MAX_FILE_SIZE, APPEND_TO_EXISTING, 1, null)) {

            // when
            // Write enough data to fill one file
            writeDataToFile(rolloverOutputStream, "Some data to fill one file.", MAX_FILE_SIZE);
            // Write more data to trigger rolling
            writeDataToFile(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);
            writeDataToFile(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);

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
            writeDataToFile(rolloverOutputStream, "Some data to fill one file.", MAX_FILE_SIZE);
            // Write more data to trigger rolling
            writeDataToFile(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);
            writeDataToFile(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);

            timeProvider.tick(Duration.of(1, ChronoUnit.DAYS));
            writeDataToFile(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);
            writeDataToFile(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE);

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
    public void testDateSizeBaseRollingNoOverriding() throws IOException, InterruptedException {
        // given
        try (OutputStream rolloverOutputStream = new RolloverFileOutputStream(
                testFolder.resolve(FILE_BASE_NAME), MAX_FILE_SIZE, APPEND_TO_EXISTING, 2, DATE_PATTERN)) {

            // when
            // Write enough data to fill one file
            writeDataToFile(rolloverOutputStream, "Some data to fill one file.", MAX_FILE_SIZE);

            // Wait for a short duration to ensure that the file creation time is different
            Thread.sleep(1000);

            // Write more data to trigger rolling
            writeDataToFile(rolloverOutputStream, "More data to trigger rolling.", MAX_FILE_SIZE * 2);

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
        Path logFilePath = testFolder.resolve(FILE_BASE_NAME);
        // Creating an instance of OnSizeAndDateRolloverFileOutputStream
        RolloverFileOutputStream outputStream = new RolloverFileOutputStream(logFilePath, MAX_FILE_SIZE, true, 1, null);

        // Writing data to the stream
        String testData = "Test data";
        outputStream.write(testData.getBytes(), 5, 4);

        // Flushing the stream
        outputStream.flush();

        // Closing the stream
        outputStream.close();

        // Verifying that the log file exists and contains the written data
        assertTrue(Files.exists(logFilePath));
        assertEquals("data", Files.readString(logFilePath));
    }

    @Test
    void testWriteFlushAndClose() throws IOException {
        Path logFilePath = testFolder.resolve(FILE_BASE_NAME);
        RolloverFileOutputStream outputStream = new RolloverFileOutputStream(logFilePath, MAX_FILE_SIZE, true, 1, null);

        // Writing data to the stream
        String testData = "Test data";
        outputStream.write(testData.getBytes());

        // Flushing the stream
        outputStream.flush();

        // Closing the stream
        outputStream.close();

        // Verifying that the log file exists and contains the written data
        assertTrue(Files.exists(logFilePath));
        assertEquals(testData, Files.readString(logFilePath));
    }

    @Test
    void testWriteSingleFlushAndClose() throws IOException {
        // Setting up test parameters
        Path logFilePath = testFolder.resolve(FILE_BASE_NAME);

        // Creating an instance of OnSizeAndDateRolloverFileOutputStream
        RolloverFileOutputStream outputStream = new RolloverFileOutputStream(logFilePath, MAX_FILE_SIZE, true, 1, null);

        // Writing data to the stream
        outputStream.write('c');

        // Flushing the stream
        outputStream.flush();

        // Closing the stream
        outputStream.close();

        // Verifying that the log file exists and contains the written data
        assertTrue(Files.exists(logFilePath));
        assertEquals("c", Files.readString(logFilePath));
    }

    @Test
    void testAppendWriteSingleFlushAndClose() throws IOException {
        // Setting up test parameters
        Path logFilePath = testFolder.resolve(FILE_BASE_NAME);
        String initialContent = "Initial Content String\n";
        Files.writeString(logFilePath, initialContent);

        // Creating an instance of OnSizeAndDateRolloverFileOutputStream
        RolloverFileOutputStream outputStream =
                new RolloverFileOutputStream(logFilePath, initialContent.length() * 2 + 1, true, 1, null);

        // Writing data to the stream
        outputStream.write(initialContent.getBytes(StandardCharsets.UTF_8));

        // Flushing the stream
        outputStream.flush();

        // Closing the stream
        outputStream.close();

        // Verifying that the log file exists and contains the written data
        assertTrue(Files.exists(logFilePath));
        assertEquals(initialContent + initialContent, Files.readString(logFilePath));
    }

    @Test
    void testNoAppendWriteSingleFlushAndClose() throws IOException {
        // Setting up test parameters
        Path logFilePath = testFolder.resolve(FILE_BASE_NAME);
        String initialContent = "Initial Content String\n";
        Files.writeString(logFilePath, initialContent);

        // Creating an instance of OnSizeAndDateRolloverFileOutputStream
        RolloverFileOutputStream outputStream =
                new RolloverFileOutputStream(logFilePath, initialContent.length() * 2 + 1, false, 1, null);

        // Writing data to the stream
        outputStream.write(initialContent.getBytes(StandardCharsets.UTF_8));

        // Flushing the stream
        outputStream.flush();

        // Closing the stream
        outputStream.close();

        // Verifying that the log file exists and contains the written data
        assertTrue(Files.exists(logFilePath));
        assertEquals(initialContent, Files.readString(logFilePath));
    }

    @Test
    void testUnwritableFile() throws IOException {
        // Create a temporary directory
        Path logFilePath = testFolder.resolve(FILE_BASE_NAME + "_tmp");
        Files.createDirectory(logFilePath);

        // Set the file permissions to read-only
        File file = logFilePath.toFile();
        file.setReadOnly();

        // Attempt to write to the file and assert that an IOException is thrown
        assertThrows(IllegalStateException.class, () -> {
            // Try to write something to the file
            new RolloverFileOutputStream(logFilePath, MAX_FILE_SIZE, true, 1, null);
        });
    }

    private static void writeDataToFile(OutputStream outputStream, String data, long dataSize) throws IOException {
        byte[] bytes = new byte[(int) dataSize];
        for (int i = 0; i < dataSize; i++) {
            bytes[i] = (byte) data.charAt(i % data.length());
        }
        outputStream.write(bytes);
        outputStream.flush();
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
