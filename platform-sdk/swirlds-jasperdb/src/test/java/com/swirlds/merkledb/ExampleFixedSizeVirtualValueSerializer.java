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

package com.swirlds.merkledb;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.ValueSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class ExampleFixedSizeVirtualValueSerializer implements ValueSerializer<ExampleFixedSizeVirtualValue> {

    public ExampleFixedSizeVirtualValueSerializer() {}

    // SelfSerializable

    private static final long CLASS_ID = 0x954027b17b5b54afL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        // no-op
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        // no-op
    }

    // ValueSerializer

    @Override
    public long getCurrentDataVersion() {
        return ExampleFixedSizeVirtualValue.SERIALIZATION_VERSION;
    }

    @Override
    public int getSerializedSize() {
        return Integer.BYTES + ExampleFixedSizeVirtualValue.RANDOM_BYTES;
    }

    @Override
    public int serialize(final ExampleFixedSizeVirtualValue data, final ByteBuffer buffer) throws IOException {
        buffer.putInt(data.getId());
        buffer.put(data.getData());
        return getSerializedSize();
    }

    @Override
    public ExampleFixedSizeVirtualValue deserialize(final ByteBuffer buffer, final long dataVersion)
            throws IOException {
        assert dataVersion == getCurrentDataVersion();
        final int id = buffer.getInt();
        final byte[] bytes = new byte[ExampleFixedSizeVirtualValue.RANDOM_BYTES];
        buffer.get(bytes);
        return new ExampleFixedSizeVirtualValue(id, bytes);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        return obj instanceof ExampleFixedSizeVirtualValueSerializer;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return (int) CLASS_ID;
    }
}
