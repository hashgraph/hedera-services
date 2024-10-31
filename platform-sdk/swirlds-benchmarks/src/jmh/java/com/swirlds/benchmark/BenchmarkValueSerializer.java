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

package com.swirlds.benchmark;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.virtualmap.serialize.ValueSerializer;

public class BenchmarkValueSerializer implements ValueSerializer<BenchmarkValue> {

    // Serializer class ID
    private static final long CLASS_ID = 0xbae262725c67b901L;

    // Serializer version
    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    // Value data version
    private static final int DATA_VERSION = 1;

    public BenchmarkValueSerializer() {
        // required for deserialization
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getClassVersion() {
        return ClassVersion.ORIGINAL;
    }

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    @Override
    public int getSerializedSize() {
        return Integer.BYTES + BenchmarkValue.getValueSize();
    }

    @Override
    public void serialize(final BenchmarkValue data, final WritableSequentialData out) {
        data.serialize(out);
    }

    @Override
    public BenchmarkValue deserialize(final ReadableSequentialData in) {
        final BenchmarkValue value = new BenchmarkValue();
        value.deserialize(in);
        return value;
    }
}
