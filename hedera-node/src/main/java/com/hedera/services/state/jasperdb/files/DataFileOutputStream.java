package com.hedera.services.state.jasperdb.files;

import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A special version of SerializableDataOutputStream that writes into a byte[] buffer and measures the size written.
 */
public class DataFileOutputStream extends SerializableDataOutputStream {
    private final ExtendedByteArrayOutputStream byteArrayOutputStream;

    /**
     * Create a DataFileOutputStream with given initial capacity
     *
     * @param initialCapacity The initial capacity for buffer
     */
    public DataFileOutputStream(int initialCapacity) {
        super(new ExtendedByteArrayOutputStream(initialCapacity));
        this.byteArrayOutputStream = (ExtendedByteArrayOutputStream)out;
    }

    /**
     * Writes the complete contents of this {@code ByteArrayOutputStream} to
     * the specified output stream argument, as if by calling the output
     * stream's write method using {@code out.write(buf, 0, count)}.
     *
     * @param   out   the output stream to which to write the data.
     * @throws  NullPointerException if {@code out} is {@code null}.
     * @throws  IOException if an I/O error occurs.
     */
    public void writeTo(OutputStream out) throws IOException {
        byteArrayOutputStream.writeTo(out);
    }

    /**
     * Writes the complete contents of this {@code ByteArrayOutputStream} to
     * the specified ByteBuffer.
     *
     * @param   byteBuffer   the output stream to which to write the data.
     * @throws  NullPointerException if {@code out} is {@code null}.
     * @throws  IOException if an I/O error occurs.
     */
    public void writeTo(ByteBuffer byteBuffer) throws IOException {
        byteArrayOutputStream.writeTo(byteBuffer);
    }

    /**
     * Resets the {@code count} field of this {@code ByteArrayOutputStream}
     * to zero, so that all currently accumulated output in the
     * output stream is discarded. The output stream can be used again,
     * reusing the already allocated buffer space.
     */
    public DataFileOutputStream reset() {
        byteArrayOutputStream.reset();
        return this;
    }

    /**
     * Returns the current size of the buffer.
     *
     * @return  the value of the {@code count} field, which is the number
     *          of valid bytes in this output stream.
     */
    public int bytesWritten() {
        return byteArrayOutputStream.size();
    }

    /**
     * Extended version of ByteArrayOutputStream that allows copy free writing of bytes into a ByteBuffer. This is
     * needed because the byte array "buf" in ByteArrayOutputStream is protected access.
     */
    private static class ExtendedByteArrayOutputStream extends ByteArrayOutputStream {
        /**
         * Creates a new {@code ByteArrayOutputStream}, with a buffer capacity of
         * the specified size, in bytes.
         *
         * @param size the initial size.
         * @throws IllegalArgumentException if size is negative.
         */
        public ExtendedByteArrayOutputStream(int size) {
            super(size);
        }

        /**
         * Writes the complete contents of this {@code ByteArrayOutputStream} to
         * the specified ByteBuffer argument.
         *
         * @param   byteBuffer   the ByteBuffer to which to write the data.
         * @throws  NullPointerException if {@code out} is {@code null}.
         * @throws  IOException if an I/O error occurs.
         */
        public synchronized void writeTo(ByteBuffer byteBuffer) {
            byteBuffer.put(Objects.requireNonNull(buf), 0, count);
        }
    }
}
