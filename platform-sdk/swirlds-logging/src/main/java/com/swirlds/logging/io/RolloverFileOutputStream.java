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

import static com.swirlds.logging.utils.StringUtils.toPaddedDigitsString;
import static java.time.ZoneOffset.UTC;

import com.swirlds.logging.utils.FileUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
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
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link RolloverFileOutputStream} is an {@link OutputStream} implementation that includes a rollover functionality
 * based on size and date configurations for the underlying file.
 * <p>
 * It supports both size-based rollover and size-and-date-based rollover strategies.
 * <p>
 * Usage:
 * <pre>
 *     // Example usage with size-based rollover
 *     Path logPath = Paths.get("logs", "example.log");
 *     long maxFileSize = 1024 * 1024; // 1 MB
 *     boolean append = true;
 *     int maxRollover = 5; // Maximum number of rolling files
 *     RolloverFileOutputStream outputStream = new RolloverFileOutputStream(logPath, maxFileSize, append, maxRollover, null);
 *
 *     // Example usage with size-and-date-based rollover
 *     Path logPath = Paths.get("logs", "example.log");
 *     long maxFileSize = 1024 * 1024; // 1 MB
 *     boolean append = true;
 *     int maxRollover = 5; // Maximum number of rolling files
 *     String datePattern = "yyyy-MM-dd"; // Date pattern for naming rolled files
 *     RolloverFileOutputStream outputStream = new RolloverFileOutputStream(logPath, maxFileSize, append, maxRollover, datePattern);
 * </pre>
 * <p>
 * Note: It's important to manage the lifecycle of the output stream properly by calling {@link #flush()} and
 * {@link #close()} methods when finished using it to ensure proper resource cleanup.
 */
public class RolloverFileOutputStream extends OutputStream {
    private final Path logPath;
    private final int maxRollover;
    private final long maxFileSize;
    private final boolean append;
    private final String baseName;
    private final String extension;
    private final int indexLength;
    private final RollingManager rollingManager;
    private int index;
    private long remainingSize;
    private FileOutputStream outputStream;

    /**
     * Creates an {@link RolloverFileOutputStream}. This constructor is meant for internal uses and test.
     *
     * @param logPath         path where the logging file is located. Should contain the base dir + the name of the
     *                        logging file.
     * @param maxFileSize     maximum size for the file. The limit is checked with best effort
     * @param append          if true and the file exists, appends the content to the file. if not, the file is rolled.
     * @param maxRollover     Within a rolling period, how many rolling files are allowed.
     * @param datePattern     if not null a {@link SizeAndDateRollingManager} is applied if not
     *                        {@link SizeRollingManager}
     * @param instantSupplier is used to get the current {@link Instant} instance. if not set and
     *                        {@link SizeAndDateRollingManager} the behaviour is defaulted to {@link Instant#now()}
     */
    RolloverFileOutputStream(
            final @NonNull Path logPath,
            final long maxFileSize,
            final boolean append,
            final int maxRollover,
            final @Nullable String datePattern,
            final @Nullable Supplier<Instant> instantSupplier) {
        this.logPath = logPath.toAbsolutePath().getParent();
        this.append = append;
        this.maxRollover = maxRollover;
        this.maxFileSize = maxFileSize;
        String baseFile = logPath.getFileName().toString();
        final int i = baseFile.lastIndexOf(".");
        this.baseName = i >= 0 ? baseFile.substring(0, i) : baseFile;
        this.extension = i >= 0 ? baseFile.substring(i + 1) : "";
        this.index = 0;
        this.indexLength = String.valueOf(maxRollover).length();

        if (datePattern == null || datePattern.isEmpty()) {
            this.rollingManager = new SizeRollingManager();
        } else {
            this.rollingManager = new SizeAndDateRollingManager(
                    datePattern, Objects.requireNonNullElse(instantSupplier, Instant::now));
        }

        try {
            this.outputStream = new FileOutputStream(logPath.toString(), append);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Log file cannot be created", e);
        }

        if (Files.exists(logPath) && append) {
            this.remainingSize = maxFileSize - logPath.toFile().length();
        } else {
            this.remainingSize = maxFileSize;
            this.rollingManager.roll();
        }
    }

    /**
     * Creates an {@link RolloverFileOutputStream}
     *
     * @param logPath     path where the logging file is located. Should contain the base dir + the name of the logging
     *                    file.
     * @param maxFileSize maximum size for the file. The limit is checked with best effort
     * @param append      if true and the file exists, appends the content to the file. if not, the file is rolled.
     * @param maxRollover Within a rolling period, how many rolling files are allowed.
     * @param datePattern if not null a {@link SizeAndDateRollingManager} is applied if not {@link SizeRollingManager}
     */
    public RolloverFileOutputStream(
            final @NonNull Path logPath,
            final long maxFileSize,
            final boolean append,
            final int maxRollover,
            final @Nullable String datePattern) {
        this(logPath, maxFileSize, append, maxRollover, datePattern, null);
    }

    /**
     * Writes {@code length} bytes from the specified {@code bytes} array starting at {@code offset} off to this file
     * output stream. Rolls over the file if necessary.
     */
    @Override
    public synchronized void write(@NonNull final byte[] bytes, final int offset, final int length) throws IOException {
        ((OutputStream) outputStream).write(bytes, offset, length);
        this.remainingSize -= length;
        rollingManager.rollIfNeeded();
    }

    /**
     * Writes a byte array into the stream. Rolls over the file if necessary
     */
    @Override
    public synchronized void write(@NonNull final byte[] bytes) throws IOException {
        ((OutputStream) outputStream).write(bytes);
        this.remainingSize -= (bytes.length);
        rollingManager.rollIfNeeded();
    }

    /**
     * Writes a single byte to the stream. Rolls over the file if necessary
     */
    @Override
    public synchronized void write(final int b) throws IOException {
        ((OutputStream) outputStream).write(b);
        this.remainingSize--;
        rollingManager.rollIfNeeded();
    }

    @Override
    public void flush() throws IOException {
        outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        outputStream.flush();
        ((OutputStream) outputStream).close();
    }

    /**
     * Inner class that handles the logic of rolling the files when applicable
     */
    abstract class RollingManager {

        protected void doRoll(final @NonNull Function<Integer, Path> newPathSupplier) {
            final long maxIndex = maxRollover - 1;
            int currentIndex = index;
            Path newPath = newPathSupplier.apply(currentIndex);

            while (Files.exists(newPath) && currentIndex <= maxIndex) {
                currentIndex++;
                newPath = newPathSupplier.apply(currentIndex % maxRollover);
            }

            if (currentIndex > maxIndex) {
                currentIndex = index;
                newPath = newPathSupplier.apply(currentIndex % maxRollover);
                FileUtils.delete(newPath);
            }

            final File file = logFilePath().toFile();
            try {
                outputStream.close();
                FileUtils.renameFile(file, newPath.toFile());
                outputStream = new FileOutputStream(file, append);
            } catch (IOException e) {
                throw new IllegalStateException("Something happened while rolling over", e);
            }
            index = getNextIndex(currentIndex);
            remainingSize = maxFileSize;
        }

        protected int getNextIndex(final int currentIndex) {
            return currentIndex;
        }

        public void rollIfNeeded() {
            if (remainingSize <= 0) {
                this.roll();
            }
        }

        protected abstract void roll();

        protected String dotExtension() {
            if (extension.isEmpty()) {
                return "";
            }
            return "." + extension;
        }

        private String logFileName() {
            return baseName + this.dotExtension();
        }

        protected Path logFilePath() {
            return logPath.resolve(logFileName());
        }
    }

    /**
     * A {@link RollingManager} that roll the file based on a configured size and date
     */
    private class SizeAndDateRollingManager extends RollingManager {

        private final DateTimeFormatter dateTimeFormatter;
        private final Supplier<Instant> instantSupplier;
        private Instant instant;

        /**
         * Creates a {@link SizeAndDateRollingManager}
         *
         * @param datePattern     the pattern to use to name the files
         * @param instantSupplier a supplier for {@link Instant} instances
         */
        public SizeAndDateRollingManager(
                final @NonNull String datePattern, final @NonNull Supplier<Instant> instantSupplier) {
            this.dateTimeFormatter = DateTimeFormatter.ofPattern(datePattern).withZone(UTC);
            this.instantSupplier = instantSupplier;
            this.instant = instantSupplier.get();
        }

        @Override
        protected void roll() {

            final String formattedDate = dateTimeFormatter.format(instant);
            Function<Integer, Path> pathSupplier = i -> getPathFor(formattedDate, i);
            doRoll(pathSupplier);
            this.instant = instantSupplier.get();
        }

        protected int getNextIndex(final int currentIndex) {
            // This is simple enough but makes that the only way it works is with the pattern yyyy-MM-dd
            // It would be nice to support extra fields like hours of minutes.
            // FUTURE-WORK, guess if 1 unit of time happened for whatever pattern we receive.
            return Duration.between(instantSupplier.get(), instant).toDays() > 0 ? 0 : currentIndex;
        }

        private Path getPathFor(String date, int index) {
            return logPath.resolve(
                    baseName + "-" + (date + "." + toPaddedDigitsString(index, indexLength)) + dotExtension());
        }
    }

    /**
     * A {@link RollingManager} that roll the file based on a configured size
     */
    private class SizeRollingManager extends RollingManager {

        @Override
        protected void roll() {
            doRoll(this::getPathFor);
        }

        private Path getPathFor(int index) {
            return logPath.resolve(baseName + "." + toPaddedDigitsString(index, indexLength) + dotExtension());
        }
    }
}
