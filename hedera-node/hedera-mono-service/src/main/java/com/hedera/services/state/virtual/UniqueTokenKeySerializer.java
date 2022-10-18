/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.virtual;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class UniqueTokenKeySerializer implements KeySerializer<UniqueTokenKey> {
    static final long CLASS_ID = 0xb3c94b6cf62aa6c4L;

    @Override
    public boolean isVariableSize() {
        return true;
    }

    @Override
    public int getTypicalSerializedSize() {
        return UniqueTokenKey.ESTIMATED_SIZE_BYTES;
    }

    @Override
    public int getSerializedSize() {
        return DataFileCommon.VARIABLE_DATA_SIZE;
    }

    @Override
    public long getCurrentDataVersion() {
        return UniqueTokenKey.CURRENT_VERSION;
    }

    @Override
    public UniqueTokenKey deserialize(final ByteBuffer buffer, final long dataVersion)
            throws IOException {
        Objects.requireNonNull(buffer);
        final UniqueTokenKey tokenKey = new UniqueTokenKey();
        tokenKey.deserialize(buffer, (int) dataVersion);
        return tokenKey;
    }

    @Override
    public int serialize(
            final UniqueTokenKey tokenKey, final SerializableDataOutputStream outputStream)
            throws IOException {
        Objects.requireNonNull(tokenKey);
        Objects.requireNonNull(outputStream);
        return tokenKey.serializeTo(outputStream::write);
    }

    @Override
    public int deserializeKeySize(final ByteBuffer byteBuffer) {
        return UniqueTokenKey.deserializeKeySize(byteBuffer);
    }

    @Override
    public boolean equals(
            final ByteBuffer byteBuffer, final int dataVersion, final UniqueTokenKey uniqueTokenKey)
            throws IOException {
        return uniqueTokenKey.equalsTo(byteBuffer);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public void deserialize(
            final SerializableDataInputStream serializableDataInputStream, final int i)
            throws IOException {
        /* no state to load, so no-op */
    }

    @Override
    public void serialize(final SerializableDataOutputStream serializableDataOutputStream)
            throws IOException {
        /* no state to save, so no-op */
    }

    @Override
    public int getVersion() {
        return UniqueTokenKey.CURRENT_VERSION;
    }
}
