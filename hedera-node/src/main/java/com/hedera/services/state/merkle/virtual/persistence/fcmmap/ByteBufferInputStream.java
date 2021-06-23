package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * A simple InputStream for reading from a input stream
 */
public class ByteBufferInputStream extends InputStream {

    private ByteBuffer byteBuffer;

    public ByteBufferInputStream(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    /**
     * Returns a byte from the ByteBuffer.
     *
     * Increments position().
     */
    @Override
    public int read() throws IOException {
        if (byteBuffer == null) throw new IOException("read on a closed InputStream");
        if (byteBuffer.remaining() == 0) {
            System.out.println("(byteBuffer.remaining() == 0)");
            return -1;
        }
        return Byte.toUnsignedInt(byteBuffer.get());
    }

    /**
     * Returns a byte array from the ByteBuffer.
     *
     * Increments position().
     */
    @Override
    public int read(@NotNull byte[] bytes) throws IOException {
        if (byteBuffer == null) throw new IOException("read on a closed InputStream");
        return read(bytes, 0, bytes.length);
    }

    /**
     * Returns a byte array from the ByteBuffer.
     *
     * Increments position().
     */
    @Override
    public int read(@NotNull byte[] bytes, int off, int len) throws IOException {
        if (byteBuffer == null) throw new IOException("read on a closed InputStream");
        if (off < 0 || len < 0 || len > bytes.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }
        int length = Math.min(byteBuffer.remaining(), len);
        if (length == 0) {
            return -1;
        }
        byteBuffer.get(bytes, off, length);
        return length;
    }

    /**
     * Skips over and discards <code>n bytes of data from this input
     * stream.
     */
    @Override
    public long skip(long n) throws IOException {
        if (byteBuffer == null) throw new IOException("skip on a closed InputStream");
        if (n <= 0) return 0;
        /*
         * ByteBuffers have at most an int, so lose the upper bits.
         * The contract allows this.
         */
        int nInt = (int) n;
        int skip = Math.min(byteBuffer.remaining(), nInt);
        byteBuffer.position(byteBuffer.position() + skip);
        return nInt;
    }

    /**
     * Returns the number of bytes that can be read (or skipped over)
     * from this input stream without blocking by the next caller of a
     * method for this input stream.
     */
    @Override
    public int available() throws IOException {
        if (byteBuffer == null) throw new IOException("available on a closed InputStream");
        return byteBuffer.remaining();
    }

    /**
     * Closes this input stream and releases any system resources associated
     * with the stream.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        byteBuffer = null;
    }

    /**
     * Marks the current position in this input stream.
     */
    @Override
    public synchronized void mark(int readLimit) {}

    /**
     * Repositions this stream to the position at the time the
     * <code>mark method was last called on this input stream.
     */
    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    /**
     * Tests if this input stream supports the <code>mark and
     * <code>reset methods.
     */
    @Override
    public boolean markSupported() {
        return false;
    }
}
