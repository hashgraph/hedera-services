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
package com.hedera.services.state.virtual.schedule;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.hashmap.KeyIndexType;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ScheduleEqualityVirtualKeySerializer
        implements KeySerializer<ScheduleEqualityVirtualKey> {
    static final long CLASS_ID = 0xc7b4f042e2fe2417L;
    static final int CURRENT_VERSION = 1;
    static final long DATA_VERSION = 1;

    @Override
    public int deserializeKeySize(ByteBuffer byteBuffer) {
        return ScheduleEqualityVirtualKey.sizeInBytes();
    }

    @Override
    public int getSerializedSize() {
        return ScheduleEqualityVirtualKey.sizeInBytes();
    }

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    @Override
    public KeyIndexType getIndexType() {
        return KeyIndexType.GENERIC;
    }

    @Override
    public ScheduleEqualityVirtualKey deserialize(ByteBuffer byteBuffer, long version)
            throws IOException {
        final var key = new ScheduleEqualityVirtualKey();
        key.deserialize(byteBuffer, (int) version);
        return key;
    }

    @Override
    public boolean equals(ByteBuffer buffer, int version, ScheduleEqualityVirtualKey key)
            throws IOException {
        return key.equals(buffer, version);
    }

    @Override
    public int serialize(ScheduleEqualityVirtualKey key, SerializableDataOutputStream out)
            throws IOException {
        key.serialize(out);
        return ScheduleEqualityVirtualKey.sizeInBytes();
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        /* No-op */
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        /* No-op */
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }
}
