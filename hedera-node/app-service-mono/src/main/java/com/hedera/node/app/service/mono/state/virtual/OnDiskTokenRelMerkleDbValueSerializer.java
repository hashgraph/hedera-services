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

import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.swirlds.merkledb.serialize.ValueSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class OnDiskTokenRelMerkleDbValueSerializer implements ValueSerializer<OnDiskTokenRel> {

    // Serializer class ID
    static final long CLASS_ID = 0x0e52cff909625f56L;

    // Serializer version
    static final int CURRENT_VERSION = 1;

    // Data version
    static final int DATA_VERSION = 1;

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

    @Override
    public int getSerializedSize() {
        return Byte.BYTES // flags;
                + Long.BYTES // prev
                + Long.BYTES // next
                + Long.BYTES // balance
                + Long.BYTES; // numbers
    }

    // Value serialization

    @Override
    public int serialize(final OnDiskTokenRel value, final ByteBuffer out) throws IOException {
        Objects.requireNonNull(value);
        Objects.requireNonNull(out);
        value.serialize(out);
        return getSerializedSize();
    }

    // Value deserialization

    @Override
    public OnDiskTokenRel deserialize(final ByteBuffer buffer, final long version) throws IOException {
        Objects.requireNonNull(buffer);
        final OnDiskTokenRel value = new OnDiskTokenRel();
        value.deserialize(buffer, (int) version);
        return value;
    }
}
