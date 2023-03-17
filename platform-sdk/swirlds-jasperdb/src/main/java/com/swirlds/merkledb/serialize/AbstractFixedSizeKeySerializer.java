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

package com.swirlds.merkledb.serialize;

import com.swirlds.virtualmap.VirtualKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * Utility class to extend by key serializers for fixed-size keys. Delegates serialization and
 * deserialization to the corresponding virtual key class, leaving only {@link #equals(ByteBuffer,
 * int, VirtualKey)} method to implement.
 *
 * @param <K> Virtual key type
 */
public abstract class AbstractFixedSizeKeySerializer<K extends VirtualKey<? super K>> implements KeySerializer<K> {

    private final long classId;
    private final int classVersion;

    private final int serializedKeySize;
    private final long serializedKeyVersion;
    private final Supplier<K> keyConstructor;

    protected AbstractFixedSizeKeySerializer(
            final long classId, final int classVersion, final int serializedKeySize, final long serializedKeyVersion) {
        this(classId, classVersion, serializedKeySize, serializedKeyVersion, null);
    }

    protected AbstractFixedSizeKeySerializer(
            final long classId,
            final int classVersion,
            final int serializedKeySize,
            final long serializedKeyVersion,
            final Supplier<K> keyConstructor) {
        this.classId = classId;
        this.classVersion = classVersion;
        this.serializedKeySize = serializedKeySize;
        this.serializedKeyVersion = serializedKeyVersion;
        this.keyConstructor = keyConstructor;
    }

    protected K newKey() {
        if (keyConstructor == null) {
            throw new IllegalStateException("Cannot create a new key for deserialization");
        }
        return keyConstructor.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        return classId;
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return classVersion;
    }

    /** {@inheritDoc} */
    @Override
    public int getSerializedSize() {
        return serializedKeySize;
    }

    /** {@inheritDoc} */
    @Override
    public long getCurrentDataVersion() {
        return serializedKeyVersion;
    }

    /** {@inheritDoc} */
    @Override
    public int serialize(final K data, final ByteBuffer buffer) throws IOException {
        data.serialize(buffer);
        return serializedKeySize;
    }

    /** {@inheritDoc} */
    @Override
    public int deserializeKeySize(final ByteBuffer buffer) {
        return serializedKeySize;
    }

    /** {@inheritDoc} */
    @Override
    public K deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
        if (dataVersion != serializedKeyVersion) {
            throw new IllegalArgumentException("Serialization version mismatch");
        }
        final K key = newKey();
        key.deserialize(buffer, (int) dataVersion);
        return key;
    }
}
