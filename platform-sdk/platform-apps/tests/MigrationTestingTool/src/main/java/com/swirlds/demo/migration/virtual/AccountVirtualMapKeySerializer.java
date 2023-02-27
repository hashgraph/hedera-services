/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.migration.virtual;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This is the key serializer for the {@link AccountVirtualMapKey}.
 */
public class AccountVirtualMapKeySerializer implements KeySerializer<AccountVirtualMapKey> {

    private static final long CLASS_ID = 0x93efc6111338834dL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSerializedSize() {
        return AccountVirtualMapKey.getSizeInBytes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountVirtualMapKey deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
        final AccountVirtualMapKey key = new AccountVirtualMapKey();
        key.deserialize(buffer, (int) dataVersion);
        return key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int serialize(final AccountVirtualMapKey data, final SerializableDataOutputStream outputStream)
            throws IOException {
        outputStream.writeSerializable(data, false);
        return AccountVirtualMapKey.getSizeInBytes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int deserializeKeySize(final ByteBuffer buffer) {
        return AccountVirtualMapKey.getSizeInBytes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final ByteBuffer buffer, final int dataVersion, final AccountVirtualMapKey keyToCompare)
            throws IOException {
        return keyToCompare.equals(buffer, dataVersion);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        // nothing to serialize
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        // nothing to deserialize
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }
}
