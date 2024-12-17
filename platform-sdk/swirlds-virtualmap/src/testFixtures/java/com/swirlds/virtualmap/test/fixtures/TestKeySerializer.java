/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.test.fixtures;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.virtualmap.serialize.KeySerializer;
import edu.umd.cs.findbugs.annotations.NonNull;

public class TestKeySerializer implements KeySerializer<TestKey> {

    public static final TestKeySerializer INSTANCE = new TestKeySerializer();

    @Override
    public long getClassId() {
        return 0x592a33a2329ec4b9L;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getSerializedSize(@NonNull TestKey data) {
        return Long.BYTES;
    }

    @Override
    public int getTypicalSerializedSize() {
        return Long.BYTES;
    }

    @Override
    public void serialize(@NonNull final TestKey data, @NonNull final WritableSequentialData out) {
        out.writeLong(data.getKey());
    }

    @Override
    public TestKey deserialize(@NonNull final ReadableSequentialData in) {
        final long key = in.readLong();
        return new TestKey(key);
    }

    @Override
    public boolean equals(@NonNull final BufferedData buffer, @NonNull final TestKey keyToCompare) {
        return buffer.readLong() == keyToCompare.getKey();
    }
}
