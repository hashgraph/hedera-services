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

import static java.time.ZoneOffset.UTC;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link OnSizeAndDateRolloverFileOutputStream}. This output stream puts content in a file that is rolled over every 24 hours and taking
 * into account a max size.
 * <p>
 * The resulting filename will contain String "yyyy-mm-dd", which is replaced with the actual date when creating and
 * rolling over the file and an index number.
 */
public class OnSizeAndDateRolloverFileOutputStream extends OutputStream {
    private static final long MB_PER_BYTE = 1000000; // 1 MB
    private static final long ESTIMATED_MAX_LOG_VOLUME = MB_PER_BYTE * MB_PER_BYTE * MB_PER_BYTE; // 1 TB
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(UTC);
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final Deque<FileMetadata> newFiles = new ConcurrentLinkedDeque<>();
    private final LinkedBlockingDeque<FileOutputStream> dispose = new LinkedBlockingDeque<>();
    private volatile boolean closed = false;
    private final LogFileConfig fileConfig;
    private FileMetadata current;

    public OnSizeAndDateRolloverFileOutputStream(
            final Path containerFolder,
            final String fileBaseName,
            final long maxFileSize,
            final boolean appendToExisting) {
        this.fileConfig = logConfig(containerFolder, fileBaseName, maxFileSize, appendToExisting);
        final Instant startingDate = Instant.now();
        String formattedDate = DATE_FORMAT.format(startingDate);

        this.current = prepareFile(fileConfig, startingDate, formattedDate, -1);
        this.executorService.submit(
                () -> { // Async clean up thread
                    while (!closed && !Thread.currentThread().isInterrupted()) {
                        try {
                            final FileOutputStream poll = dispose.take();
                            poll.flush();
                            poll.close();
                        } catch (InterruptedException e) {
                            if (Thread.currentThread().isInterrupted()) {
                                // if dispose is not empty we drain resources
                                return;
                            }
                        } catch (Exception e) {
                            // Emergency logger
                        }
                    }
                });
    }

    @Override
    public synchronized void write(@NonNull final byte[] bytes, final int offset, final int length) throws IOException {
        // checkAndPrepare(length);
        current.outputStream().write(bytes, offset, length);
        current.remainingSize.addAndGet(-length);
        if (current.remainingSizeLong() <= 0) {
            useNextFile();
        }
    }

    @Override
    public synchronized void write(@NonNull final byte[] bytes) throws IOException {
        // checkAndPrepare(bytes.length);
        current.outputStream().write(bytes);
        current.remainingSize().addAndGet(-bytes.length);
        if (current.remainingSizeLong() <= 0) {
            useNextFile();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void write(final int b) throws IOException {
        // checkAndPrepare(1);
        current.outputStream().write(b);
        current.remainingSize.decrementAndGet();
        if (current.remainingSizeLong() <= 0) {
            useNextFile();
        }
    }

    @Override
    public void flush() throws IOException {
        current.outputStream().flush();
    }

    @Override
    public void close() throws IOException {
        this.closed = true;
        final ArrayList<FileOutputStream> fileOutputStreams = new ArrayList<>(dispose);
        dispose.clear();
        for (FileOutputStream os : fileOutputStreams) {
            os.flush();
            try {
                os.close();
            } catch (Exception e) {
                // emergency logger
            }
        }
        current.outputStream().flush();
        current.outputStream().close();
        executorService.shutdownNow();
        executorService.close();
    }

    @NonNull
    private static LogFileConfig logConfig(
            final Path logPath, String fileName, final long maxFileSize, boolean append) {
        final int possibleMaxFiles = Math.max((int) (ESTIMATED_MAX_LOG_VOLUME / maxFileSize), 100);
        FileNameComponents fileNameComponents = FileNameComponents.getFileNameComponents(fileName);
        return new LogFileConfig(
                logPath, fileNameComponents, String.valueOf(possibleMaxFiles).length(), maxFileSize, append);
    }

    private void checkAndPrepare(final int length) {
        final long size = current.remainingSizeLong();
        if (size - length <= fileConfig.maxFileSize * 0.15 && newFiles.isEmpty()) {
            final String formattedDate = current.formattedDate();
            final int index = current.index();
            final Instant instant = Instant.now().truncatedTo(ChronoUnit.DAYS);

            executorService.submit(() -> {
                final FileMetadata last = newFiles.peekLast();
                if (last != null && instant.equals(last.date()) && index > last.index()) {
                    newFiles.add(prepareFile(fileConfig, instant, formattedDate, index));
                }
            });
        }
    }

    private void useNextFile() {
        dispose.offer(current.outputStream());
        this.current = prepareNextFile(fileConfig, current);
    }

    private FileMetadata prepareNextFile(final LogFileConfig fileConfig, final FileMetadata left) {
        final Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
        String formattedDate = left.formattedDate();
        int index = left.index;
        if (Duration.between(left.date(), now).toDays() >= 1) {
            formattedDate = DATE_FORMAT.format(now);
            index = 0;
        }

        return prepareFile(fileConfig, now, formattedDate, index);
    }

    @NonNull
    private static FileMetadata prepareFile(
            final LogFileConfig fc, final Instant now, final String date, final int index) {
        final double maxIndex = Math.pow(10, fc.indexPositions);
        int i = index;
        boolean nextFileFound = false;
        long fileLeftCapacity = fc.maxFileSize;
        FileOutputStream os = null;
        File file;

        while (!nextFileFound) {
            // While we have room to keep increasing the i
            // Search for the first available file of the day.
            do {
                i++;
                file = fc.getPathFor(date, i).toFile();
                fileLeftCapacity = fc.maxFileSize - file.length();
            } while ((file.exists() && !fc.append())
                    || (file.exists() && fc.append() && !file.canWrite())
                    || (file.exists() && fc.append() && file.canWrite() && (fileLeftCapacity <= 0)) && i < maxIndex);

            if (i > maxIndex) {
                // this means that we tried to create a file for each possible index and was not possible.
                // So we either miscalculate the max size or
                // Emergency logger
                file = createEmergencyTempFile(fc, date, file, i);
                // Create a temporal file for this case ?
                fileLeftCapacity = fc.maxFileSize;
            }
            try {
                os = new FileOutputStream(file, fc.append());
                nextFileFound = true;
            } catch (FileNotFoundException e) {
                // Emergency logger
            }
        }

        return new FileMetadata(now, date, i, new AtomicLong(fileLeftCapacity), os);
    }

    private static File createEmergencyTempFile(final LogFileConfig fc, final String date, File file, final int index) {

        try {
            file = Files.createTempFile(fc.nameWithPrefix(date + "-" + index + "-" + UUID.randomUUID()), "")
                    .toFile();
        } catch (IOException e) {
            // Emergency logger super bad situation
        }
        return file;
    }

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
    private static String toPaddedDigitsString(final int number, final int desiredLength) {
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

    private static void checkDirectory(String filename) throws IOException {
        File file = new File(filename);
        filename = file.getCanonicalPath();
        file = new File(filename);
        File dir = file.getParentFile();
        if (!dir.exists()) throw new IOException("Log directory does not exist. Path=" + dir);
        else if (!dir.isDirectory()) throw new IOException("Path for Log directory is not a directory. Path=" + dir);
        else if (!dir.canWrite()) throw new IOException("Cannot write log directory " + dir);
    }

    public record LogFileConfig(
            Path logPath, FileNameComponents fileName, int indexPositions, long maxFileSize, boolean append) {

        String nameWithPrefix(String middleFix) {
            return fileName.baseName + "-" + middleFix + fileName().dotExtension();
        }

        Path getPathFor(String date, int index) {
            return this.logPath.resolve(nameWithPrefix(date + "." + toPaddedDigitsString(index, this.indexPositions)));
        }
    }

    public record FileMetadata(
            Instant date, String formattedDate, int index, AtomicLong remainingSize, FileOutputStream outputStream) {
        public Long remainingSizeLong() {
            return remainingSize.get();
        }
    }

    public record FileNameComponents(String baseName, String extension) {
        @NonNull
        private static FileNameComponents getFileNameComponents(final String fileBaseName) {
            String baseFile = Path.of(fileBaseName).getFileName().toString();
            final int i = baseFile.lastIndexOf(".");
            final String baseFileName = i >= 0 ? baseFile.substring(0, i) : baseFile;
            final String baseFileExtension = i >= 0 ? baseFile.substring(i + 1) : "";
            return new FileNameComponents(baseFileName, baseFileExtension);
        }

        String dotExtension() {
            if (extension == null || extension.isEmpty()) {
                return "";
            }
            return "." + extension;
        }
    }
}
