/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

@SuppressWarnings("ResultOfMethodCallIgnored")
public final class ExampleFixedSizeVirtualValue extends ExampleByteArrayVirtualValue {

    public static final int RANDOM_BYTES = 32;
    public static final int SIZE_BYTES = Integer.BYTES + RANDOM_BYTES;
    static final byte[] RANDOM_DATA = new byte[RANDOM_BYTES];
    public static final int SERIALIZATION_VERSION = 287;

    static {
        new Random(12234).nextBytes(RANDOM_DATA);
    }

    private int id;
    private byte[] data;

    public ExampleFixedSizeVirtualValue() {}

    public ExampleFixedSizeVirtualValue(final int id) {
        this.id = id;
        this.data = RANDOM_DATA;
    }

    public ExampleFixedSizeVirtualValue(final int id, final byte[] data) {
        this.id = id;
        this.data = new byte[data.length];
        System.arraycopy(data, 0, this.data, 0, data.length);
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public byte[] getData() {
        return data;
    }

    @Override
    public void deserialize(final SerializableDataInputStream inputStream, final int dataVersion) throws IOException {
        assert dataVersion == getVersion() : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        id = inputStream.readInt();
        data = new byte[RANDOM_BYTES];
        inputStream.read(data);
    }

    @Override
    public void serialize(final SerializableDataOutputStream outputStream) throws IOException {
        outputStream.writeInt(id);
        outputStream.write(data);
    }

    @Override
    public void serialize(final ByteBuffer buffer) {
        buffer.putInt(id);
        buffer.put(data);
    }

    @Override
    public void deserialize(final ByteBuffer buffer, final int dataVersion) {
        assert dataVersion == getVersion() : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        id = buffer.getInt();
        data = new byte[RANDOM_BYTES];
        buffer.get(data);
    }

    @Override
    public VirtualValue copy() {
        return new ExampleFixedSizeVirtualValue(id);
    }

    @Override
    public VirtualValue asReadOnly() {
        return this;
    }

    @Override
    public long getClassId() {
        return 1438455686395469L;
    }

    @Override
    public int getVersion() {
        return SERIALIZATION_VERSION;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExampleFixedSizeVirtualValue that = (ExampleFixedSizeVirtualValue) o;
        return id == that.id && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return "TestLeafData{" + "id=" + id + ", data=" + Arrays.toString(data) + '}';
    }
}
