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

package com.swirlds.demo.virtualmerkle.map.account;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.serialize.KeySerializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This is the key serializer for the {@link AccountVirtualMapKey}.
 */
public class AccountVirtualMapKeySerializer implements KeySerializer<AccountVirtualMapKey> {

    private static final long CLASS_ID = 0x93efc6111338834eL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    @Override
    public int getSerializedSize() {
        return AccountVirtualMapKey.getSizeInBytes();
    }

    @Override
    public void serialize(@NonNull final AccountVirtualMapKey key, @NonNull final WritableSequentialData out) {
        key.serialize(out);
    }

    @Override
    public void serialize(final AccountVirtualMapKey key, final ByteBuffer buffer) throws IOException {
        key.serialize(buffer);
    }

    @Override
    public AccountVirtualMapKey deserialize(@NonNull ReadableSequentialData in) {
        final AccountVirtualMapKey key = new AccountVirtualMapKey();
        key.deserialize(in);
        return key;
    }

    @Override
    public AccountVirtualMapKey deserialize(final ByteBuffer buffer, final long dataVersion) {
        final AccountVirtualMapKey key = new AccountVirtualMapKey();
        key.deserialize(buffer);
        return key;
    }

    @Override
    public boolean equals(@NonNull BufferedData buffer, @NonNull AccountVirtualMapKey keyToCompare) {
        return keyToCompare.equals(buffer);
    }

    @Override
    public boolean equals(final ByteBuffer buffer, final int dataVersion, final AccountVirtualMapKey keyToCompare) {
        return keyToCompare.equals(buffer, dataVersion);
    }
}
