/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state.merkle.data;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.DataInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * An adapter class that adapts a {@link ByteBuffer} to be a {@link DataInput}.
 *
 * <p>NOTE: This class is temporary. It will be removed when we move to the PBJ generated classes,
 * since they have another DataInput type class that we will use instead.
 */
public class ByteBufferDataInput implements DataInput {
    private final ByteBuffer buffer;

    public ByteBufferDataInput(@NonNull final ByteBuffer buffer) {
        this.buffer = Objects.requireNonNull(buffer);
    }

    @Override
    public void readFully(@NonNull final byte[] b) {
        buffer.get(b);
    }

    @Override
    public void readFully(@NonNull final byte[] b, int off, int len) {
        buffer.get(b, off, len);
    }

    @Override
    public int skipBytes(int n) {
        final var toRead = Math.min(buffer.remaining(), n);
        buffer.position(buffer.position() + toRead);
        return toRead;
    }

    @Override
    public boolean readBoolean() {
        return buffer.get() != 0;
    }

    @Override
    public byte readByte() {
        return buffer.get();
    }

    @Override
    public int readUnsignedByte() {
        // Avoid sign extension! Make sure that scenario is tested
        return buffer.get() & 0xFF;
    }

    @Override
    public short readShort() {
        return buffer.getShort();
    }

    @Override
    public int readUnsignedShort() {
        // Avoid sign extension! Make sure that scenario is tested
        return buffer.getShort() & 0xFFFF;
    }

    @Override
    public char readChar() {
        return buffer.getChar();
    }

    @Override
    public int readInt() {
        return buffer.getInt();
    }

    @Override
    public long readLong() {
        return buffer.getLong();
    }

    @Override
    public float readFloat() {
        return buffer.getFloat();
    }

    @Override
    public double readDouble() {
        return buffer.getDouble();
    }

    @Override
    public String readLine() {
        throw new UnsupportedOperationException("readLine is not supported");
    }

    @Override
    public String readUTF() throws IOException {
        throw new UnsupportedOperationException("readUTF is not supported");
    }
}
