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

import com.swirlds.logging.utils.FileUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * {@link RolloverFileOutputStream} is an {@link OutputStream} implementation that supports general rollover
 * functionality for the underlying file. Note: It's important to manage the lifecycle of the output stream properly by
 * calling {@link #flush()} and {@link #close()} methods when finished using it to ensure proper resource cleanup.
 */
abstract class RolloverFileOutputStream extends OutputStream {
    protected final Path logPath;
    private final int maxRollover;
    private final long maxFileSize;
    private final boolean append;
    private long remainingSize;
    private FileOutputStream outputStream;
    protected final String baseName;
    protected final String extension;
    protected final int indexLength;
    protected int index;

    /**
     * Creates an {@link RolloverFileOutputStream}
     *
     * @param logPath     path where the logging file is located. Should contain the base dir + the name of the logging
     *                    file.
     * @param maxFileSize maximum size for the file. The limit is checked with best effort
     * @param append      if true and the file exists, appends the content to the file. if not, the file is rolled.
     * @param maxRollover Within a rolling period, how many rolling files are allowed.
     */
    protected RolloverFileOutputStream(
            final @NonNull Path logPath, final long maxFileSize, final boolean append, final int maxRollover) {
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
    }

    protected void init(final @NonNull Path logPath) {
        try {
            this.outputStream = new FileOutputStream(logPath.toString(), this.append);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Log file cannot be created", e);
        }

        if (Files.exists(logPath) && append) {
            this.remainingSize = maxFileSize - logPath.toFile().length();
        } else {
            this.remainingSize = maxFileSize;
            this.roll();
        }
    }

    /**
     * Writes {@code length} bytes from the specified {@code bytes} array starting at {@code offset} off to this file
     * output stream. Rolls over the file if necessary.
     */
    @Override
    public synchronized void write(@NonNull final byte[] bytes, final int offset, final int length) throws IOException {
        ((OutputStream) outputStream).write(bytes, offset, length);
        this.remainingSize -= length;
        this.rollIfNeeded();
    }

    /**
     * Writes a byte array into the stream. Rolls over the file if necessary
     */
    @Override
    public synchronized void write(@NonNull final byte[] bytes) throws IOException {
        ((OutputStream) outputStream).write(bytes);
        this.remainingSize -= (bytes.length);
        this.rollIfNeeded();
    }

    /**
     * Writes a single byte to the stream. Rolls over the file if necessary
     */
    @Override
    public synchronized void write(final int b) throws IOException {
        ((OutputStream) outputStream).write(b);
        this.remainingSize--;
        this.rollIfNeeded();
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
     * Method that holds the logic of rolling. Can make use of {@link RolloverFileOutputStream#doRoll(Function)}
     */
    protected abstract void roll();

    /**
     * Performs the actual rolling of the file. Each subclass can call this when needed or reimplement the way this is
     * done.
     *
     * @param newPathSupplier a function that will convert an index into a path.
     */
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

    protected String dotExtension() {
        if (extension.isEmpty()) {
            return "";
        }
        return "." + extension;
    }

    private void rollIfNeeded() {
        if (remainingSize <= 0) {
            this.roll();
        }
    }

    private String logFileName() {
        return baseName + this.dotExtension();
    }

    protected Path logFilePath() {
        return logPath.resolve(logFileName());
    }
}
