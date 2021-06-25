package com.hedera.services.state.merkle.virtual.persistence;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * An extended version of SerializableDataOutputStream for writing to a ByteBuffer
 */
public class PositionableByteBufferSerializableDataOutputStream extends SerializableDataOutputStream {
    private final ByteBuffer buffer;

    public PositionableByteBufferSerializableDataOutputStream(ByteBuffer buffer) {
        super(new ByteBufferOutputStream(buffer));
        this.buffer = buffer;
    }

    public int position() {
        return buffer.position();
    }

    public void position(int position) {
        buffer.position(position);
    }

    public void writeSelfSerializable(int startOffset, SelfSerializable object, int maxNumberOfBytes) throws IOException {
        buffer.position(startOffset);
        writeSelfSerializable(object, maxNumberOfBytes);
    }

    public void writeSelfSerializable(SelfSerializable object, int maxNumberOfBytes) throws IOException {
        SerializableDataOutputStream outputStream = new SerializableDataOutputStream(new ByteBufferOutputStream(buffer, maxNumberOfBytes));
        outputStream.writeInt(object.getVersion());
        object.serialize(outputStream);
    }

    /**
     * Simple OutputStream for writing to a ByteBuffer
     */
    private static class ByteBufferOutputStream extends OutputStream {
        private final ByteBuffer byteBuffer;
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
}
