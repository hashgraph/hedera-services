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

import com.swirlds.logging.utils.GeneralUtilities;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link OnSizeAndDateRolloverFileOutputStream}. This output stream puts content in a file that is rolled over every 24
 * hours and taking into account a max size.
 * <p>
 * The resulting filename will contain String "yyyy-mm-dd", which is replaced with the actual date when creating and
 * rolling over the file and an index number.
 */
public class OnSizeAndDateRolloverFileOutputStream extends OutputStream {
    private static final long MB_PER_BYTE = 1000000; // 1 MB
    private static final long ESTIMATED_MAX_LOG_VOLUME = MB_PER_BYTE * MB_PER_BYTE * MB_PER_BYTE; // 1 TB
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(UTC);
    private final LogFileConfig fileConfig;
    private FileMetadata current;

    public OnSizeAndDateRolloverFileOutputStream(
            final @NonNull Path file, final long maxFileSize, final boolean append) {

        this.fileConfig = logConfig(file, maxFileSize, append);

        this.current = prepareFile(fileConfig);
    }

    /**
     * Writes {@code length} bytes from the specified {@code bytes} array starting at {@code offset} off to this file output stream.
     * Rolls over the file if necessary.
     */
    @Override
    public synchronized void write(@NonNull final byte[] bytes, final int offset, final int length) throws IOException {
        current.outputStream().write(bytes, offset, length);
        current.remainingSize.addAndGet(-length);
        rollIfNeeded();
    }

    /**
     * Writes a byte array into the stream.
     * Rolls over the file if necessary
     */
    @Override
    public synchronized void write(@NonNull final byte[] bytes) throws IOException {
        current.outputStream().write(bytes);
        current.remainingSize().addAndGet(-bytes.length);
        rollIfNeeded();
    }

    /**
     * Writes a single byte to the stream.
     * Rolls over the file if necessary
     */
    @Override
    public synchronized void write(final int b) throws IOException {
        current.outputStream().write(b);
        current.remainingSize.decrementAndGet();
        rollIfNeeded();
    }

    @Override
    public void flush() throws IOException {
        current.outputStream().flush();
    }

    @Override
    public void close() throws IOException {
        current.outputStream().flush();
        current.outputStream().close();
    }

    @NonNull
    private static LogFileConfig logConfig(final Path logPath, final long maxFileSize, boolean append) {
        final int possibleMaxFiles = Math.max((int) (ESTIMATED_MAX_LOG_VOLUME / maxFileSize), 100);
        FileNameComponents fileNameComponents = FileNameComponents.create(logPath);
        return new LogFileConfig(
                logPath.toAbsolutePath().getParent(),
                fileNameComponents,
                String.valueOf(possibleMaxFiles).length(),
                maxFileSize,
                append);
    }

    private synchronized void rollIfNeeded() {
        if (current.remainingSizeLong() <= 0) {
            this.current = roll(fileConfig, this.current);
        }
    }

    @NonNull
    private static FileMetadata prepareFile(@NonNull final LogFileConfig fc) {
        final File file = fc.logFilePath().toFile();
        if ((!fc.append() && file.exists()) || (file.exists() && !file.canWrite())) {
            throw new IllegalStateException("Cannot write log file " + file);
        }

        long remainingSize = fc.maxFileSize() - file.length();
        FileOutputStream os;
        try {
            os = new FileOutputStream(file.toString(), fc.append());
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(e);
        }

        return new FileMetadata(Instant.now().truncatedTo(ChronoUnit.DAYS), 0, new AtomicLong(remainingSize), os);
    }

    @NonNull
    private static FileMetadata roll(@NonNull final LogFileConfig fc, @NonNull final FileMetadata metadata) {
        final double maxIndex = Math.pow(10, fc.indexPositions);
        final String formattedDate = DATE_FORMAT.format(metadata.instant());
        int index = metadata.index();
        Path newPath = fc.getPathFor(formattedDate, index);

        while (Files.exists(newPath) && index < maxIndex) {
            index++;
            newPath = fc.getPathFor(formattedDate, index);
        }

        if (index > maxIndex) {
            final Path pathFor = fc.getPathFor(formattedDate, metadata.index());
            GeneralUtilities.delete(pathFor);
        }

        final Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
        int nextIndex = Duration.between(now, metadata.instant()).toDays() > 0 ? 0 : index + 1;

        try {
            metadata.outputStream.close();
            final File file = fc.logFilePath().toFile();
            GeneralUtilities.renameFile(file, newPath.toFile());
            FileOutputStream os = new FileOutputStream(file, fc.append());
            return new FileMetadata(now, nextIndex, new AtomicLong(fc.maxFileSize()), os);
        } catch (IOException e) {
            throw new RuntimeException("Something happened while rolling over", e);
        }
    }

    public record LogFileConfig(
            Path logPath, FileNameComponents fileName, int indexPositions, long maxFileSize, boolean append) {

        String logFileName() {
            return fileName.baseName + fileName().dotExtension();
        }

        Path logFilePath() {
            return this.logPath.resolve(logFileName());
        }

        Path getPathFor(String date, int index) {
            return this.logPath.resolve(fileName.baseName + "-"
                    + (date + "." + GeneralUtilities.toPaddedDigitsString(index, this.indexPositions))
                    + fileName.dotExtension());
        }
    }

    public record FileMetadata(Instant instant, int index, AtomicLong remainingSize, FileOutputStream outputStream) {
        public Long remainingSizeLong() {
            return remainingSize.get();
        }
    }

    public record FileNameComponents(String baseName, String extension) {
        @NonNull
        private static FileNameComponents create(final Path fileName) {
            String baseFile = fileName.getFileName().toString();
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
