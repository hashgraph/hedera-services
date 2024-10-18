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

package com.swirlds.merkledb.test.fixtures;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.virtualmap.serialize.ValueSerializer;

public final class ExampleFixedSizeVirtualValueSerializer implements ValueSerializer<ExampleFixedSizeVirtualValue> {

    public ExampleFixedSizeVirtualValueSerializer() {}

    // SelfSerializable

    private static final long CLASS_ID = 0x954027b17b5b54afL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getClassVersion() {
        return ClassVersion.ORIGINAL;
    }

    // ValueSerializer

    @Override
    public long getCurrentDataVersion() {
        return ExampleFixedSizeVirtualValue.SERIALIZATION_VERSION;
    }

    @Override
    public int getSerializedSize() {
        return Integer.BYTES + ExampleFixedSizeVirtualValue.RANDOM_BYTES;
    }

    @Override
    public void serialize(final ExampleFixedSizeVirtualValue data, final WritableSequentialData out) {
        out.writeInt(data.getId());
        out.writeBytes(data.getData());
    }

    @Override
    public ExampleFixedSizeVirtualValue deserialize(final ReadableSequentialData in) {
        final int id = in.readInt();
        final byte[] bytes = new byte[ExampleFixedSizeVirtualValue.RANDOM_BYTES];
        in.readBytes(bytes);
        return new ExampleFixedSizeVirtualValue(id, bytes);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        return obj instanceof ExampleFixedSizeVirtualValueSerializer;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return (int) CLASS_ID;
    }
}
