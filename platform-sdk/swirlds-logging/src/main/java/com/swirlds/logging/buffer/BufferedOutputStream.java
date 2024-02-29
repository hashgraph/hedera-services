/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.logging.buffer;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * An OutputStream that uses {@link ByteBuffer} before writing to an underlying {@link OutputStream}
 */
public class BufferedOutputStream extends OutputStream {
    private final ByteBuffer buffer;
    private final OutputStream outputStream;

    /**
     * Creates a Writer that uses an internal {@link ByteBuffer} to buffer writes to the given {@code outputStream}.
     *
     * @param outputStream   the underlying {@link OutputStream} to write to
     * @param bufferCapacity the capacity of the buffer has to be grater than 0
     * @throws IllegalArgumentException in case {@code bufferCapacity} is less or equals to 0
     * @throws NullPointerException     in case {@code outputStream} is null
     */
    public BufferedOutputStream(@NonNull final OutputStream outputStream,
            final int bufferCapacity) {
        if (bufferCapacity <= 0) {
            throw new IllegalArgumentException("bufferCapacity must be > than 0");
        }
        this.outputStream = Objects.requireNonNull(outputStream, "outputStream must not be null");
        this.buffer = ByteBuffer.wrap(new byte[bufferCapacity]);
    }

    @Override
    public synchronized void write(@NonNull final byte[] bytes, final int offset, final int length) throws IOException {

        if (length >= buffer.capacity()) {
            // if request length exceeds buffer capacity, flush the buffer and write the data directly
            flush();
            writeToDestination(bytes, offset, length);
        } else {
            if (length > buffer.remaining()) {
                flush();
            }
            for (int i = offset; i < length; i++) {
                buffer.put(bytes[i]);
            }
        }
    }

    /**
     * if {@code length} is less than the remaining capacity of the buffer, buffers the {@code bytes} and eventually
     * writes it to the underlying stream. if the buffer is full or {@code length} is greater than buffers capacity,
     * writes bytes and the buffer content to the underlying output stream
     *
     * @param bytes information to write
     * @throws IOException in case there was an error writing to the underlying outputStream
     */
    @Override
    public synchronized void write(@NonNull final byte[] bytes) throws IOException {

        if (bytes.length >= buffer.capacity()) {
            // if request length exceeds buffer capacity, flush the buffer and write the data directly
            flush();
            writeToDestination(bytes, 0, bytes.length);
        } else {
            if (bytes.length > buffer.remaining()) {
                flush();
            }
            buffer.put(bytes);
        }
    }

    /**
     *
     */
    @Override
    public synchronized void write(final int b) throws IOException {
        if (buffer.capacity() >= 1) {
            buffer.put((byte) b);
            // if request length exceeds buffer capacity, flush the buffer and write the data directly
        } else {
            flush();
            outputStream.write(b);
        }
    }

    /**
     * Calls {@code flush()} on the underlying output stream.
     */
    @Override
    public synchronized void flush() throws IOException {
        flushBuffer(buffer);
        flushDestination();
    }

    /**
     * Writes the specified section of the specified byte array to the stream.
     *
     * @param bytes  the array containing data
     * @param offset from where to write
     * @param length how many bytes to write
     */
    private void writeToDestination(final byte[] bytes, final int offset, final int length) throws IOException {
        outputStream.write(bytes, offset, length);
    }


    private void flushDestination() throws IOException {
        outputStream.flush();
    }


    private void flushBuffer(final ByteBuffer buf) throws IOException {
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
    public void close() throws IOException {
        outputStream.close();
    }
}
