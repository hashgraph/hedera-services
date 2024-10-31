/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.benchmark;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.LongUnaryOperator;

public class BenchmarkValue implements VirtualValue {

    static final long CLASS_ID = 0x2af5b26682153acfL;
    static final int VERSION = 1;

    private static int valueSize = 16;
    private byte[] valueBytes;

    public static void setValueSize(int size) {
        valueSize = size;
    }

    public static int getValueSize() {
        return valueSize;
    }

    public BenchmarkValue() {
        // default constructor for deserialize
    }

    public BenchmarkValue(long seed) {
        valueBytes = new byte[valueSize];
        Utils.toBytes(seed, valueBytes);
    }

    public BenchmarkValue(BenchmarkValue other) {
        valueBytes = Arrays.copyOf(other.valueBytes, other.valueBytes.length);
    }

    public long toLong() {
        return Utils.fromBytes(valueBytes);
    }

    public void update(LongUnaryOperator updater) {
        long value = Utils.fromBytes(valueBytes);
        value = updater.applyAsLong(value);
        Utils.toBytes(value, valueBytes);
    }

    @Override
    public VirtualValue copy() {
        return new BenchmarkValue(this);
    }

    @Override
    public VirtualValue asReadOnly() {
        return new BenchmarkValue(this);
    }

    public static int getSerializedSize() {
        return Integer.BYTES + valueSize;
    }

    @Override
    public void serialize(SerializableDataOutputStream outputStream) throws IOException {
        outputStream.writeInt(valueBytes.length);
        outputStream.write(valueBytes);
    }

    public void serialize(final WritableSequentialData out) {
        out.writeInt(valueBytes.length);
        out.writeBytes(valueBytes);
    }

    @Deprecated
    void serialize(ByteBuffer buffer) {
        buffer.putInt(valueBytes.length);
        buffer.put(valueBytes);
    }

    public void deserialize(final ReadableSequentialData in) {
        int n = in.readInt();
        valueBytes = new byte[n];
        in.readBytes(valueBytes);
    }

    @Override
    public void deserialize(SerializableDataInputStream inputStream, int dataVersion) throws IOException {
        assert dataVersion == getClassVersion() : "dataVersion=" + dataVersion + " != getVersion()=" + getClassVersion();
        int n = inputStream.readInt();
        valueBytes = new byte[n];
        while (n > 0) {
            n -= inputStream.read(valueBytes, valueBytes.length - n, n);
        }
    }

    @Deprecated
    void deserialize(ByteBuffer buffer, int dataVersion) {
        assert dataVersion == getClassVersion() : "dataVersion=" + dataVersion + " != getVersion()=" + getClassVersion();
        int n = buffer.getInt();
        valueBytes = new byte[n];
        buffer.get(valueBytes);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getClassVersion() {
        return VERSION;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BenchmarkValue that)) return false;
        return Arrays.equals(this.valueBytes, that.valueBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(valueBytes);
    }
}
