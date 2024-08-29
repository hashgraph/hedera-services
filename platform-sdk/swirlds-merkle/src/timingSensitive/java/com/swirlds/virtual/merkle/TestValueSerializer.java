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

package com.swirlds.virtual.merkle;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.virtualmap.serialize.ValueSerializer;

public class TestValueSerializer implements ValueSerializer<TestValue> {

    public TestValueSerializer() {
        // required for deserialization
    }

    @Override
    public long getClassId() {
        return 53543454;
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
    public int getSerializedSize(final TestValue data) {
        final byte[] bytes = CommonUtils.getNormalisedStringBytes(data.getValue());
        return Integer.BYTES + bytes.length;
    }

    @Override
    public int getTypicalSerializedSize() {
        return 20; // guesstimation
    }

    @Override
    public void serialize(final TestValue data, final WritableSequentialData out) {
        final byte[] bytes = CommonUtils.getNormalisedStringBytes(data.getValue());
        out.writeInt(bytes.length);
        out.writeBytes(bytes);
    }

    @Override
    public TestValue deserialize(final ReadableSequentialData in) {
        final int size = in.readInt();
        final byte[] bytes = new byte[size];
        in.readBytes(bytes);
        final String value = CommonUtils.getNormalisedStringFromBytes(bytes);
        return new TestValue(value);
    }
}
