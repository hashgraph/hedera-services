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
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

public class BenchmarkKeySerializer implements KeySerializer<BenchmarkKey> {

    static final long CLASS_ID = 0x1bf5b26682153acfL;
    static final int VERSION = 1;

    @Override
    public long getCurrentDataVersion() {
        return BenchmarkKey.VERSION;
    }

    @Override
    public int deserializeKeySize(final ByteBuffer buffer) {
        return Integer.BYTES + buffer.getInt();
    }

    @Override
    public boolean equals(final ByteBuffer buffer, final int dataVersion, final BenchmarkKey keyToCompare)
            throws IOException {
        return keyToCompare.equals(buffer, dataVersion);
    }

    @Override
    public int getSerializedSize() {
        return BenchmarkKey.getSerializedSize();
    }

    @Override
    public BenchmarkKey deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
        BenchmarkKey key = new BenchmarkKey();
        key.deserialize(buffer, (int) dataVersion);
        return key;
    }

    @Override
    public int serialize(final BenchmarkKey key, final SerializableDataOutputStream outputStream) throws IOException {
        outputStream.writeSerializable(key, false);
        return BenchmarkKey.getSerializedSize();
    }

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int dataVersion)
            throws IOException {}

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {}

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }
}
