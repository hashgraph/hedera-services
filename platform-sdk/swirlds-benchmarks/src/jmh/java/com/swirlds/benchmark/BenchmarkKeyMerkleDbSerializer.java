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

import com.swirlds.merkledb.serialize.KeySerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

public class BenchmarkKeyMerkleDbSerializer implements KeySerializer<BenchmarkKey> {

    // Serializer class ID
    private static final long CLASS_ID = 0xbfadab77596df06L;

    // Serializer version
    private static final int VERSION = 1;

    // Key data version
    private static final int DATA_VERSION = 1;

    public BenchmarkKeyMerkleDbSerializer() {
        // required for deserialization
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
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    @Override
    public int getSerializedSize() {
        return BenchmarkKey.getKeySize();
    }

    @Override
    public int serialize(final BenchmarkKey data, final ByteBuffer buffer) throws IOException {
        data.serialize(buffer);
        return getSerializedSize();
    }

    @Override
    public int deserializeKeySize(final ByteBuffer buffer) {
        return getSerializedSize();
    }

    @Override
    public BenchmarkKey deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
        BenchmarkKey key = new BenchmarkKey();
        key.deserialize(buffer, (int) dataVersion);
        return key;
    }

    @Override
    public boolean equals(final ByteBuffer buffer, final int dataVersion, final BenchmarkKey keyToCompare) {
        return keyToCompare.equals(buffer, dataVersion);
    }
}
