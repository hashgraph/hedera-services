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

package com.swirlds.demo.virtualmerkle.map.smartcontracts.data;

import com.swirlds.merkledb.serialize.ValueSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

/** This class is the serializer of {@link SmartContractMapValue}. */
public final class SmartContractMapValueSerializerMerkleDb implements ValueSerializer<SmartContractMapValue> {

    // Serializer class ID
    private static final long CLASS_ID = 0xed6c1e1f0b6bda21L;

    // Serializer version
    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    // Value data version
    private static final int DATA_VERSION = 1;

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    @Override
    public int getSerializedSize() {
        return SmartContractMapValue.getSizeInBytes();
    }

    @Override
    public int serialize(final SmartContractMapValue data, final ByteBuffer buffer) throws IOException {
        final byte[] value = data.getValue();
        buffer.put(value);
        return SmartContractMapValue.getSizeInBytes();
    }

    @Override
    public SmartContractMapValue deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
        final byte[] value = new byte[SmartContractMapValue.getSizeInBytes()];
        buffer.get(value);
        return new SmartContractMapValue(value);
    }
}
