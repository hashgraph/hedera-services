/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class BenchmarkKey implements VirtualLongKey {

    static final long CLASS_ID = 0x1af5b26682153acfL;
    static final int VERSION = 1;

    private static int keySize = 8;
    private byte[] keyBytes;

    public static void setKeySize(int size) {
        keySize = size;
    }

    public static int getKeySize() {
        return keySize;
    }

    public BenchmarkKey() {
        // default constructor for deserialize
    }

    public BenchmarkKey(long seed) {
        keyBytes = new byte[keySize];
        Utils.toBytes(seed, keyBytes);
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        buffer.put(keyBytes);
    }

    @Override
    public void deserialize(ByteBuffer buffer, int dataVersion) {
        assert dataVersion == getVersion() : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        keyBytes = new byte[keySize];
        buffer.get(keyBytes);
    }

    @Override
    public void serialize(SerializableDataOutputStream outputStream) throws IOException {
        outputStream.write(keyBytes);
    }

    @Override
    public void deserialize(SerializableDataInputStream inputStream, int dataVersion) throws IOException {
        assert dataVersion == getVersion() : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        keyBytes = new byte[keySize];
        int n = keySize;
        while (n > 0) {
            n -= inputStream.read(keyBytes, keyBytes.length - n, n);
        }
    }

    public static int getSerializedSize() {
        return keySize;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public int compareTo(VirtualLongKey o) {
        BenchmarkKey that = (BenchmarkKey) o;
        return Arrays.compare(this.keyBytes, that.keyBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(keyBytes);
    }

    @Override
    public long getKeyAsLong() {
        return Utils.fromBytes(keyBytes);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BenchmarkKey that)) return false;
        return Arrays.equals(this.keyBytes, that.keyBytes);
    }

    public boolean equals(ByteBuffer buffer, int dataVersion) {
        if (dataVersion != VERSION) return false;
        for (int i = 0; i < keySize; ++i) {
            if (buffer.get() != keyBytes[i]) return false;
        }
        return true;
    }
}
