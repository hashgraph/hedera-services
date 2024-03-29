/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.serialize.KeySerializer;
import java.nio.ByteBuffer;

public class TestObjectKeySerializer implements KeySerializer<TestObjectKey> {

    public TestObjectKeySerializer() {
        // required for deserialization
    }

    @Override
    public long getClassId() {
        return 8838922;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public int getSerializedSize() {
        return TestObjectKey.BYTES;
    }

    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    @Override
    public void serialize(final TestObjectKey data, final WritableSequentialData out) {
        data.serialize(out);
    }

    @Override
    public void serialize(TestObjectKey data, ByteBuffer buffer) {
        data.serialize(buffer);
    }

    @Override
    public TestObjectKey deserialize(final ReadableSequentialData in) {
        final TestObjectKey key = new TestObjectKey();
        key.deserialize(in);
        return key;
    }

    @Override
    public TestObjectKey deserialize(final ByteBuffer buffer, final long dataVersion) {
        final TestObjectKey key = new TestObjectKey();
        key.deserialize(buffer);
        return key;
    }

    @Override
    public boolean equals(final BufferedData buffer, final TestObjectKey keyToCompare) {
        return (buffer.readLong() == keyToCompare.getValue()) && (buffer.readLong() == keyToCompare.getValue());
    }

    @Override
    public boolean equals(final ByteBuffer buffer, final int dataVersion, final TestObjectKey keyToCompare) {
        return (buffer.getLong() == keyToCompare.getValue()) && (buffer.getLong() == keyToCompare.getValue());
    }
}
