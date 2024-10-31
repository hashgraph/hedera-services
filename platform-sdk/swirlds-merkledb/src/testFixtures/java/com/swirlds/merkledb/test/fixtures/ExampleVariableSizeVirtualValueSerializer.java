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
import com.swirlds.virtualmap.serialize.BaseSerializer;
import com.swirlds.virtualmap.serialize.ValueSerializer;

public final class ExampleVariableSizeVirtualValueSerializer
        implements ValueSerializer<ExampleVariableSizeVirtualValue> {

    public ExampleVariableSizeVirtualValueSerializer() {}

    // SelfSerializable

    private static final long CLASS_ID = 0x3f501a6ed395e07eL;

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
        return ExampleVariableSizeVirtualValue.SERIALIZATION_VERSION;
    }

    @Override
    public int getSerializedSize() {
        return BaseSerializer.VARIABLE_DATA_SIZE;
    }

    @Override
    public int getSerializedSize(ExampleVariableSizeVirtualValue data) {
        return Integer.BYTES + Integer.BYTES + data.getData().length;
    }

    @Override
    public void serialize(final ExampleVariableSizeVirtualValue data, final WritableSequentialData out) {
        out.writeInt(data.getId());
        final int dataLength = data.getDataLength();
        out.writeInt(dataLength);
        out.writeBytes(data.getData());
    }

    @Override
    public ExampleVariableSizeVirtualValue deserialize(final ReadableSequentialData in) {
        final int id = in.readInt();
        final int dataLength = in.readInt();
        final byte[] bytes = new byte[dataLength];
        in.readBytes(bytes);
        return new ExampleVariableSizeVirtualValue(id, bytes);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        return obj instanceof ExampleVariableSizeVirtualValueSerializer;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return (int) CLASS_ID;
    }
}
