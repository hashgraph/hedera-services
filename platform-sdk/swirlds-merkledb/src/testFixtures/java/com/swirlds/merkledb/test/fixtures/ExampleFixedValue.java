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
import java.util.Random;

public final class ExampleFixedValue extends ExampleByteArrayVirtualValue {

    public static final ExampleFixedValueCodec CODEC = new ExampleFixedValueCodec();

    public static final int RANDOM_BYTES = 32;

    static final byte[] RANDOM_DATA = new byte[RANDOM_BYTES];

    static {
        new Random(12234).nextBytes(RANDOM_DATA);
    }

    private final int id;

    private final byte[] data;

    public static int valueToId(final Bytes value) {
        return value.getInt(0);
    }

    public static byte[] valueToData(final Bytes value) {
        return value.toByteArray(Integer.BYTES, Math.toIntExact(value.length() - Integer.BYTES));
    }

    public ExampleFixedValue(int id) {
        this.id = id;
        this.data = RANDOM_DATA;
    }

    public ExampleFixedValue(int id, final byte[] data) {
        this.id = id;
        this.data = new byte[data.length];
        System.arraycopy(data, 0, this.data, 0, data.length);
    }

    public ExampleFixedValue(final ReadableSequentialData in) {
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

    public static class ExampleFixedValueCodec implements Codec<ExampleFixedValue> {

        @NonNull
        @Override
        public ExampleFixedValue parse(@NonNull ReadableSequentialData in, boolean strictMode, int maxDepth)
                throws ParseException {
            return new ExampleFixedValue(in);
        }

        @Override
        public void write(@NonNull ExampleFixedValue value, @NonNull WritableSequentialData out) throws IOException {
            value.writeTo(out);
        }

        @Override
        public int measure(@NonNull ReadableSequentialData in) throws ParseException {
            throw new UnsupportedOperationException("ExampleFixedValueCodec.measure() not implemented");
        }

        @Override
        public int measureRecord(ExampleFixedValue value) {
            return value.getSizeInBytes();
        }

        @Override
        public boolean fastEquals(@NonNull ExampleFixedValue value, @NonNull ReadableSequentialData in)
                throws ParseException {
            final ExampleFixedValue other = parse(in);
            return other.equals(value);
        }
    }
}
