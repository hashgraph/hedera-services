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

package com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.serialize.AbstractFixedSizeKeySerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This class is the serializer of {@link SmartContractByteCodeMapKey}.
 */
public final class SmartContractByteCodeMapKeySerializerMerkleDb
        extends AbstractFixedSizeKeySerializer<SmartContractByteCodeMapKey> {

    private static final long CLASS_ID = 0xee36c20c7ccc69daL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    public SmartContractByteCodeMapKeySerializerMerkleDb() {
        super(CLASS_ID, ClassVersion.ORIGINAL, SmartContractByteCodeMapKey.getSizeInBytes(), 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected SmartContractByteCodeMapKey newKey() {
        return new SmartContractByteCodeMapKey();
    }

    @Override
    public void serialize(final SmartContractByteCodeMapKey key, final WritableSequentialData out) throws IOException {
        key.serialize(out);
    }

    @Override
    public SmartContractByteCodeMapKey deserialize(final ReadableSequentialData in) throws IOException {
        final SmartContractByteCodeMapKey key = new SmartContractByteCodeMapKey();
        key.deserialize(in);
        return key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(
            final BufferedData buffer, final SmartContractByteCodeMapKey keyToCompare) throws IOException {
        return keyToCompare.equals(buffer);
    }

    @Override
    @Deprecated(forRemoval = true)
    public boolean equals(ByteBuffer buffer, int dataVersion, SmartContractByteCodeMapKey keyToCompare)
            throws IOException {
        return keyToCompare.equals(buffer);
    }
}
