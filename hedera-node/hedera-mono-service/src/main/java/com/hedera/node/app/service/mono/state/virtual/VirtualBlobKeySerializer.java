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

package com.hedera.node.app.service.mono.state.virtual;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.serialize.KeySerializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class VirtualBlobKeySerializer implements KeySerializer<VirtualBlobKey> {

    static final long CLASS_ID = 0xb7b4f0d24bf1ebf3L;

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
    public int getSerializedSize() {
        return VirtualBlobKey.sizeInBytes();
    }

    @Override
    public void serialize(@NonNull final VirtualBlobKey key, @NonNull final WritableSequentialData out) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(out);
        key.serialize(out);
    }

    @Override
    @Deprecated
    public void serialize(final VirtualBlobKey key, final ByteBuffer buffer) throws IOException {
        Objects.requireNonNull(key);
        Objects.requireNonNull(buffer);
        key.serialize(buffer);
    }

    @Override
    public VirtualBlobKey deserialize(@NonNull final ReadableSequentialData out) {
        Objects.requireNonNull(out);
        final var key = new VirtualBlobKey();
        key.deserialize(out);
        return key;
    }

    @Override
    @Deprecated
    public VirtualBlobKey deserialize(final ByteBuffer buffer, final long version) throws IOException {
        Objects.requireNonNull(buffer);
        final var key = new VirtualBlobKey();
        key.deserialize(buffer);
        return key;
    }

    @Override
    public boolean equals(@NonNull final BufferedData buffer, @NonNull final VirtualBlobKey key) {
        Objects.requireNonNull(buffer);
        Objects.requireNonNull(key);
        return key.equalsTo(buffer);
    }

    @Override
    @Deprecated
    public boolean equals(final ByteBuffer buffer, final int version, final VirtualBlobKey key) throws IOException {
        Objects.requireNonNull(buffer);
        return key.equalsTo(buffer, version);
    }
}
