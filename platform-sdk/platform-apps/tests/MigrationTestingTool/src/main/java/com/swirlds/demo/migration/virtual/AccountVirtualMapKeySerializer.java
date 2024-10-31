/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.virtualmap.serialize.KeySerializer;

/**
 * This is the key serializer for the {@link AccountVirtualMapKey}.
 */
public class AccountVirtualMapKeySerializer implements KeySerializer<AccountVirtualMapKey> {

    private static final long CLASS_ID = 0x93efc6111338834eL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
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
    public int getClassVersion() {
        return ClassVersion.ORIGINAL;
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
    public void serialize(final AccountVirtualMapKey key, final WritableSequentialData out) {
        key.serialize(out);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountVirtualMapKey deserialize(final ReadableSequentialData in) {
        final AccountVirtualMapKey key = new AccountVirtualMapKey();
        key.deserialize(in);
        return key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final BufferedData buffer, final AccountVirtualMapKey keyToCompare) {
        return keyToCompare.equals(buffer);
    }
}
