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
package com.hedera.node.app.service.mono.state.virtual;

import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.KeyIndexType;
import com.swirlds.merkledb.serialize.KeySerializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class EntityNumMerkleDbKeySerializer implements KeySerializer<EntityNumVirtualKey> {

    // Serializer class ID
    static final long CLASS_ID = 0xc7b4f042fcf1e2a3L;

    // Serializer version
    static final int CURRENT_VERSION = 1;

    // Key data version
    static final long DATA_VERSION = 1;

    // Serializer info

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    // Data version

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    // Key serialization

    @Override
    public KeyIndexType getIndexType() {
        // By default, if serialized size is 8 bytes, key index type is
        // SEQUENTIAL_INCREMENTING_LONGS,
        // but this is not the case for EntityNumVirtualKey. Hence, override getIndexType() here
        return KeyIndexType.GENERIC;
    }

    @Override
    public int getSerializedSize() {
        return EntityNumVirtualKey.sizeInBytes();
    }

    @Override
    public int serialize(EntityNumVirtualKey key, SerializableDataOutputStream out)
            throws IOException {
        Objects.requireNonNull(key);
        Objects.requireNonNull(out);
        key.serialize(out);
        return getSerializedSize();
    }

    @Override
    public int serialize(final EntityNumVirtualKey key, final ByteBuffer buffer)
            throws IOException {
        Objects.requireNonNull(key);
        Objects.requireNonNull(buffer);
        key.serialize(buffer);
        return EntityNumVirtualKey.sizeInBytes();
    }

    // Key deserialization

    @Override
    public int deserializeKeySize(ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        return EntityNumVirtualKey.sizeInBytes();
    }

    @Override
    public EntityNumVirtualKey deserialize(ByteBuffer buffer, long version) throws IOException {
        Objects.requireNonNull(buffer);
        final var key = new EntityNumVirtualKey();
        key.deserialize(buffer, (int) version);
        return key;
    }

    @Override
    public boolean equals(ByteBuffer buffer, int version, EntityNumVirtualKey key)
            throws IOException {
        Objects.requireNonNull(buffer);
        return key.equals(buffer, version);
    }
}
