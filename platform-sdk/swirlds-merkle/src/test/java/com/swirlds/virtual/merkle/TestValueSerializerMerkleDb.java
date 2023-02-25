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
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.merkledb.serialize.ValueSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

public class TestValueSerializerMerkleDb implements ValueSerializer<TestValue> {

    public TestValueSerializerMerkleDb() {
        // required for deserialization
    }

    @Override
    public long getClassId() {
        return 53543454;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        // no-op
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        // no-op
    }

    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int serialize(final TestValue data, final SerializableDataOutputStream outputStream) throws IOException {
        final byte[] bytes = CommonUtils.getNormalisedStringBytes(data.getValue());
        outputStream.writeInt(bytes.length);
        outputStream.write(bytes);
        return Integer.BYTES + bytes.length;
    }

    @Override
    public TestValue deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
        final int size = buffer.getInt();
        final byte[] bytes = new byte[size];
        buffer.get(bytes);
        final String value = CommonUtils.getNormalisedStringFromBytes(bytes);
        return new TestValue(value);
    }
}
