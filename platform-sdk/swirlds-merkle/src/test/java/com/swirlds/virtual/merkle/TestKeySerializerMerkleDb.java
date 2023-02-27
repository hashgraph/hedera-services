/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtual.merkle;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.KeySerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

public class TestKeySerializerMerkleDb implements KeySerializer<TestKey> {

    public TestKeySerializerMerkleDb() {
        // required for deserialization
    }

    @Override
    public long getClassId() {
        return 8838921;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public int getSerializedSize() {
        return TestKey.BYTES;
    }

    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    @Override
    public int deserializeKeySize(final ByteBuffer buffer) {
        return buffer.getInt();
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) {
        // nop
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) {
        // nop
    }

    @Override
    public TestKey deserialize(final ByteBuffer buffer, final long dataVersion) {
        final TestKey key = new TestKey();
        key.deserialize(buffer, (int) dataVersion);
        return key;
    }

    @Override
    public int serialize(final TestKey data, final ByteBuffer buffer) {
        data.serialize(buffer);
        return TestKey.BYTES;
    }

    @Override
    public boolean equals(final ByteBuffer buffer, final int dataVersion, final TestKey keyToCompare)
            throws IOException {
        return buffer.getLong() == keyToCompare.getKeyAsLong();
    }
}
