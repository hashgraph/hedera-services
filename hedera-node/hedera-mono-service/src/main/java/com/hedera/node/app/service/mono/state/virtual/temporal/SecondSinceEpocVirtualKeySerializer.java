/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual.temporal;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.serialize.KeyIndexType;
import com.swirlds.merkledb.serialize.KeySerializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class SecondSinceEpocVirtualKeySerializer implements KeySerializer<SecondSinceEpocVirtualKey> {

    static final long CLASS_ID = 0xced4f0425c211ba2L;

    static final int CURRENT_VERSION = 1;

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
        return KeyIndexType.GENERIC;
    }

    @Override
    public int getSerializedSize() {
        return SecondSinceEpocVirtualKey.sizeInBytes();
    }

    @Override
    public void serialize(@NonNull final SecondSinceEpocVirtualKey key, @NonNull final WritableSequentialData out) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(out);
        key.serialize(out);
    }

    @Override
    @Deprecated
    public void serialize(final SecondSinceEpocVirtualKey key, final ByteBuffer buffer) throws IOException {
        key.serialize(buffer);
    }

    // Key deserialization

    @Override
    public SecondSinceEpocVirtualKey deserialize(@NonNull ReadableSequentialData in) {
        final var key = new SecondSinceEpocVirtualKey();
        key.deserialize(in);
        return key;
    }

    @Override
    @Deprecated
    public SecondSinceEpocVirtualKey deserialize(ByteBuffer buffer, long version) throws IOException {
        final var key = new SecondSinceEpocVirtualKey();
        key.deserialize(buffer);
        return key;
    }

    @Override
    public boolean equals(@NonNull BufferedData buffer, @NonNull SecondSinceEpocVirtualKey keyToCompare) {
        Objects.requireNonNull(buffer);
        Objects.requireNonNull(keyToCompare);
        return keyToCompare.equalsTo(buffer);
    }

    @Override
    @Deprecated
    public boolean equals(ByteBuffer buffer, int version, SecondSinceEpocVirtualKey key) throws IOException {
        return key.equalsTo(buffer, version);
    }
}
