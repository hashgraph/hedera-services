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
import java.util.Objects;

public class UniqueTokenKeySerializer implements KeySerializer<UniqueTokenKey> {

    static final long CLASS_ID = 0xb3c94b6cf62aa6c5L;

    // Serializer version
    static final int CURRENT_VERSION = 1;

    // Key data version
    static final long DATA_VERSION = UniqueTokenKey.CURRENT_VERSION;

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
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getSerializedSize(@NonNull final UniqueTokenKey key) {
        return key.getSerializedSizeInBytes();
    }

    @Override
    public int getTypicalSerializedSize() {
        return UniqueTokenKey.ESTIMATED_SIZE_BYTES;
    }

    @Override
    public void serialize(@NonNull final UniqueTokenKey key, @NonNull final WritableSequentialData out) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(out);
        key.serializeTo(out::writeByte);
    }

    @Override
    public UniqueTokenKey deserialize(@NonNull final ReadableSequentialData in) {
        Objects.requireNonNull(in);
        final UniqueTokenKey tokenKey = new UniqueTokenKey();
        tokenKey.deserializeFrom(in::readByte);
        return tokenKey;
    }

    @Override
    public boolean equals(@NonNull final BufferedData buffer, @NonNull final UniqueTokenKey key) {
        Objects.requireNonNull(buffer);
        Objects.requireNonNull(key);
        return key.equalsTo(buffer::readByte);
    }
}
