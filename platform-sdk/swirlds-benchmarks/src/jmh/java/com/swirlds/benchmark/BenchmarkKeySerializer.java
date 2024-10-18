/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.benchmark;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.virtualmap.serialize.KeySerializer;

public class BenchmarkKeySerializer implements KeySerializer<BenchmarkKey> {

    // Serializer class ID
    private static final long CLASS_ID = 0xbfadab77596df06L;

    // Serializer version
    private static final int VERSION = 1;

    // Key data version
    private static final int DATA_VERSION = 1;

    public BenchmarkKeySerializer() {
        // required for deserialization
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getClassVersion() {
        return VERSION;
    }

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    @Override
    public int getSerializedSize() {
        return BenchmarkKey.getKeySize();
    }

    @Override
    public void serialize(final BenchmarkKey data, final WritableSequentialData out) {
        data.serialize(out);
    }

    @Override
    public BenchmarkKey deserialize(final ReadableSequentialData in) {
        BenchmarkKey key = new BenchmarkKey();
        key.deserialize(in);
        return key;
    }

    @Override
    public boolean equals(final BufferedData buffer, final BenchmarkKey keyToCompare) {
        return keyToCompare.equals(buffer);
    }
}
