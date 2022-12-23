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
import java.io.DataOutput;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * An adapter class that adapts a {@link ByteBuffer} to be a {@link DataOutput}.
 *
 * <p>NOTE: This class is temporary. It will be removed when we move to the PBJ generated classes,
 * since they have another DataOutput type class that we will use instead.
 */
public class ByteBufferDataOutput implements DataOutput {
    private final ByteBuffer buffer;

    public ByteBufferDataOutput(@NonNull final ByteBuffer buffer) {
        this.buffer = Objects.requireNonNull(buffer);
    }

    @Override
    public void write(int b) {
        // Since there is a cast here, test that the semantics are correct!
        buffer.put((byte) (b & 0xFF));
    }

    @Override
    public void write(@NonNull final byte[] b) {
        buffer.put(b);
    }

    @Override
    public void write(@NonNull final byte[] b, int off, int len) {
        buffer.put(b, off, len);
    }

    @Override
    public void writeBoolean(boolean v) {
        buffer.put(v ? (byte) 1 : 0);
    }

    @Override
    public void writeByte(int v) {
        // Since there is a cast here, test that the semantics are correct!
        buffer.put((byte) (v & 0xFF));
    }

    @Override
    public void writeShort(int v) {
        // Make sure to test the semantics here are correct!
        buffer.putShort((short) (v & 0xFFFF));
    }

    @Override
    public void writeChar(int v) {
        // Make sure to test the semantics here are correct!
        buffer.putChar((char) (v & 0xFFFFFF));
    }

    @Override
    public void writeInt(int v) {
        buffer.putInt(v);
    }

    @Override
    public void writeLong(long v) {
        buffer.putLong(v);
    }

    @Override
    public void writeFloat(float v) {
        buffer.putFloat(v);
    }

    @Override
    public void writeDouble(double v) {
        buffer.putDouble(v);
    }

    @Override
    public void writeBytes(@NonNull final String s) {
        throw new UnsupportedOperationException("writeBytes is not supported");
    }

    @Override
    public void writeChars(@NonNull final String s) {
        throw new UnsupportedOperationException("writeChars is not supported");
    }

    @Override
    public void writeUTF(@NonNull final String s) {
        throw new UnsupportedOperationException("writeUTF is not supported");
    }
}
