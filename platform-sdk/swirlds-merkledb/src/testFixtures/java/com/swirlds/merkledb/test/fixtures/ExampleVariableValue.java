/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.test.fixtures;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

public final class ExampleVariableValue extends ExampleByteArrayVirtualValue {

    public static final ExampleVariableValueCodec CODEC = new ExampleVariableValueCodec();

    private static final Random RANDOM = new Random(12234);

    private static final int RANDOM_BYTES = 1024;

    private static final byte[] RANDOM_DATA = new byte[RANDOM_BYTES];

    static {
        RANDOM.nextBytes(RANDOM_DATA);
    }

    private int id;
    private byte[] data;

    public static Bytes intToValue(final int v) {
        return intToValue(v, RANDOM_DATA, 0, 256 + (v % 768));
    }

    public static Bytes intToValue(final int v, final byte[] data, final int off, final int len) {
        final byte[] bytes = new byte[Integer.BYTES + len];
        ByteBuffer.wrap(bytes).putInt(v).put(data, off, len);
        return Bytes.wrap(bytes);
    }

    public ExampleVariableValue(final int id) {
        this.id = id;
        data = new byte[256 + (id % 768)];
        System.arraycopy(RANDOM_DATA, 0, data, 0, data.length);
    }

    public ExampleVariableValue(final int id, final byte[] data) {
        this.id = id;
        this.data = new byte[data.length];
        System.arraycopy(data, 0, this.data, 0, data.length);
    }

    public ExampleVariableValue(final ReadableSequentialData in) {
        this.id = in.readInt();
        final int len = in.readInt();
        this.data = new byte[len];
        in.readBytes(this.data);
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public byte[] getData() {
        return data;
    }

    public int getSizeInBytes() {
        return Integer.BYTES + Integer.BYTES + data.length;
    }

    public void writeTo(final WritableSequentialData out) {
        out.writeInt(id);
        out.writeInt(data.length);
        out.writeBytes(data);
    }

    public static class ExampleVariableValueCodec implements Codec<ExampleVariableValue> {

        @NonNull
        @Override
        public ExampleVariableValue parse(@NonNull ReadableSequentialData in, boolean strictMode, int maxDepth) {
            return new ExampleVariableValue(in);
        }

        @Override
        public void write(@NonNull ExampleVariableValue value, @NonNull WritableSequentialData out) throws IOException {
            value.writeTo(out);
        }

        @Override
        public int measure(@NonNull ReadableSequentialData in) {
            throw new UnsupportedOperationException("ExampleVariableValueCodec.measure() not implemented");
        }

        @Override
        public int measureRecord(ExampleVariableValue value) {
            return value.getSizeInBytes();
        }

        @Override
        public boolean fastEquals(@NonNull ExampleVariableValue value, @NonNull ReadableSequentialData in)
                throws ParseException {
            final ExampleVariableValue other = parse(in);
            return other.equals(value);
        }
    }
}
