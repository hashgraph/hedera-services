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

package com.swirlds.logging.file;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.handler.AbstractSyncedHandler;
import com.swirlds.logging.api.internal.format.LineBasedFormat;
import com.swirlds.logging.buffer.BufferedOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A file handler that writes log events to a file.
 * <p>
 * This handler use a {@link BufferedOutputStream} to write {@link LogEvent}s to a file. You can configure the following
 * properties:
 * <ul>
 *     <li>{@code file} - the {@link Path} of the file</li>
 *     <li>{@code append} - whether to append to the file or not</li>
 * </ul>
 */
public class FileHandler extends AbstractSyncedHandler {

    private static final String FILE_NAME_PROPERTY = "%s.file";
    private static final String APPEND_PROPERTY = "%s.append";
    private static final String DEFAULT_FILE_NAME = "swirlds-log.log";
    private final OutputStream writer;
    private final LineBasedFormat format;
    private static final int BUFFER_CAPACITY = 8192 * 4;

    /**
     * Creates a new file handler.
     *
     * @param handlerName   the unique handler name
     * @param configuration the configuration
     */
    public FileHandler(@NonNull final String handlerName, @NonNull final Configuration configuration)
            throws IOException {
        super(handlerName, configuration);

        format = LineBasedFormat.createForHandler(handlerName, configuration);

        final String propertyPrefix = PROPERTY_HANDLER.formatted(handlerName);
        final Path filePath = Objects.requireNonNullElse(
                configuration.getValue(FILE_NAME_PROPERTY.formatted(propertyPrefix), Path.class, null),
                Path.of(DEFAULT_FILE_NAME));
        final boolean append = Objects.requireNonNullElse(
                configuration.getValue(APPEND_PROPERTY.formatted(propertyPrefix), Boolean.class, null), true);
        try {
            if (Files.exists(filePath) && !(append && Files.isWritable(filePath))) {
                throw new IOException("log file exist and is not writable or is not append mode");
            }
            Files.createDirectories(filePath.getParent());
            final OutputStream outputStream = new FileOutputStream(filePath.toFile(), append);
            this.writer = new BufferedOutputStream(outputStream,BUFFER_CAPACITY);
        } catch (IOException e) {
            throw new IOException("Could not create log file " + filePath.toAbsolutePath(), e);
        }
    }


    /**
     * Handles a log event by appending it to the file using the {@link LineBasedFormat}.
     *
     * @param event The log event to be printed.
     */
    @Override
    protected void handleEvent(@NonNull final LogEvent event) {
        final StringBuilder writer = new StringBuilder(4 * 1024);
        format.print(writer, event);
        try {
            this.writer.write(writer.toString().getBytes(StandardCharsets.UTF_8));
        } catch (final Exception exception) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to write to output stream", exception);
        }
    }

    /**
     * Stops the handler and no further elements are processed
     */
    @Override
    protected void handleStopAndFinalize() {
        super.handleStopAndFinalize();
        try {
            writer.flush();
        } catch (final Exception exception) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to close file output stream", exception);
        }
    }


    /**
     * A Writer that uses a ByteBuffer before writing to an OutputStream
     */
    private static class WriterWithBuffer implements Closeable {

        private static final int BUFFER_CAPACITY = 8192 * 4;
        private final ByteBuffer buffer;
        private final OutputStream writer;

        public WriterWithBuffer(@NonNull OutputStream stream) {
            this.writer = stream;
            this.buffer = ByteBuffer.wrap(new byte[BUFFER_CAPACITY]);
        }


        public synchronized void write(
                final byte[] bytes, final int length) {

            if (length >= buffer.capacity()) {
                // if request length exceeds buffer capacity, flush the buffer and write the data directly
                flush();
                writeToDestination(bytes, 0, length);
            } else {
                if (length > buffer.remaining()) {
                    flush();
                }
                buffer.put(bytes);
            }
        }

        /**
         * Writes the specified section of the specified byte array to the stream.
         *
         * @param bytes  the array containing data
         * @param offset from where to write
         * @param length how many bytes to write
         */
        private void writeToDestination(final byte[] bytes, final int offset, final int length) {
            if (writer != null) {
                try {
                    writer.write(bytes, offset, length);
                } catch (final IOException ex) {
                    throw new RuntimeException("Error writing to stream " + "getName()", ex);
                }
            }
        }

        /**
         * Calls {@code flush()} on the underlying output stream.
         */
        public synchronized void flush() {
            flushBuffer(buffer);
            flushDestination();
        }

        private void flushDestination() {
            if (writer != null) {
                try {
                    writer.flush();
                } catch (final IOException ex) {
                    throw new RuntimeException("Error flushing stream " + "getName()", ex);
                }
            }
        }


        private void flushBuffer(final ByteBuffer buf) {
            ((Buffer) buf).flip();
            try {
                if (buf.remaining() > 0) {
                    writeToDestination(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
                }
            } finally {
                buf.clear();
            }
        }

        /**
         * Closes and releases any system resources associated with this instance.
         */
        @Override
        public void close() {
        }
    }
}
