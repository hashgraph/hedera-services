/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
    public UniqueTokenKey deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        Objects.requireNonNull(buffer);
        UniqueTokenKey tokenKey = new UniqueTokenKey();
        tokenKey.deserialize(buffer, (int) dataVersion);
        return tokenKey;
    }

    @Override
    public int serialize(UniqueTokenKey tokenKey, SerializableDataOutputStream outputStream)
            throws IOException {
        Objects.requireNonNull(tokenKey);
        Objects.requireNonNull(outputStream);
        return tokenKey.serializeTo(outputStream::write);
    }

    @Override
    public int deserializeKeySize(ByteBuffer byteBuffer) {
        return UniqueTokenKey.deserializeKeySize(byteBuffer);
    }

    @Override
    public boolean equals(ByteBuffer byteBuffer, int dataVersion, UniqueTokenKey uniqueTokenKey)
            throws IOException {
        final var key = new UniqueTokenKey();
        key.deserialize(byteBuffer, dataVersion);
        return key.equals(uniqueTokenKey);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int i)
            throws IOException {
        /* no state to load, so no-op */
    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream)
            throws IOException {
        /* no state to save, so no-op */
    }

    @Override
    public int getVersion() {
        return UniqueTokenKey.CURRENT_VERSION;
    }
}
