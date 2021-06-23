package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Simple OutputStream for writing to a ByteBuffer
 */
public class ByteBufferOutputStream extends OutputStream {

    private ByteBuffer byteBuffer;
    private int limit = -1;

    public ByteBufferOutputStream(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    /**
     * Create a ByteBufferOutputStream that has a set limit on the number of bytes that can be written before an
     * exception is thrown,
     *
     * @param byteBuffer The buffer to write to
     * @param limit the number of bytes to allow to be written
     */
    public ByteBufferOutputStream(ByteBuffer byteBuffer, int limit) {
        this.byteBuffer = byteBuffer;
        this.limit = limit;
    }

    /**
     * Write a byte to bytebuffer
     *
     * @param b Byte to write
     * @throws IOException only if underlying byteBuffer throws
     */
    @Override
    public void write (int b) throws IOException {
        if (!byteBuffer.hasRemaining()) flush(); // TODO no idea why, was in some example code
        if(limit == 0) throw new IOException("ByteBufferOutputStream has hit limit.");
        limit --;
        byteBuffer.put((byte)b);
    }

    /**
     * Write a bunch of bytes to byte buffer
     *
     * @param bytes The byte array to write from
     * @param offset The offset in bytes to start writing from
     * @param length The number of bytes to write
     * @throws IOException only if underlying byteBuffer throws
     */
    public void write (@NotNull byte[] bytes, int offset, int length) throws IOException {
        if(limit > 0 && limit < length) throw new IOException("ByteBufferOutputStream has hit limit.");
        limit -= length;
        byteBuffer.put(bytes, offset, length);
    }
}
