/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.codec;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.serialize.KeySerializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VirtualBlobKeySerializer implements KeySerializer<VirtualBlobKey> {

    static final long CLASS_ID = 0x6459da78c643abd6L;

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

    // Key info

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
    public void serialize(@NonNull final VirtualBlobKey key, final WritableSequentialData out) {
        key.serialize(out);
    }

    @Override
    public void serialize(final VirtualBlobKey key, final ByteBuffer byteBuffer) throws IOException {
        key.serialize(byteBuffer);
    }

    // Key deserialization

    @Override
    public VirtualBlobKey deserialize(@NonNull final ReadableSequentialData in) {
        final var key = new VirtualBlobKey();
        key.deserialize(in);
        return key;
    }

    @Override
    public VirtualBlobKey deserialize(ByteBuffer byteBuffer, long version) throws IOException {
        final var key = new VirtualBlobKey();
        key.deserialize(byteBuffer);
        return key;
    }

    @Override
    public boolean equals(@NonNull final BufferedData buf, @NonNull final VirtualBlobKey keyToCompare) {
        return keyToCompare.equalsTo(buf);
    }

    @Override
    public boolean equals(final ByteBuffer buffer, final int version, final VirtualBlobKey key) throws IOException {
        return key.getType().ordinal() == (0xff & buffer.get()) && key.getEntityNumCode() == buffer.getInt();
    }
}
