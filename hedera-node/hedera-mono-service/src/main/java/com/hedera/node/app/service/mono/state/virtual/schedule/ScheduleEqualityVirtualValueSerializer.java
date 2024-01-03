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

package com.hedera.node.app.service.mono.state.virtual.schedule;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.merkledb.serialize.ValueSerializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import java.util.Objects;

public class ScheduleEqualityVirtualValueSerializer implements ValueSerializer<ScheduleEqualityVirtualValue> {

    static final long CLASS_ID = 0x218235815b54ea30L;

    static final int CURRENT_VERSION = 1;

    static final int DATA_VERSION = 1;

    // guesstimate of the typical size of a serialized value
    private static final int TYPICAL_SIZE = 32;

    // Serializer info

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    // Data version

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    // Value serialization

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getTypicalSerializedSize() {
        return TYPICAL_SIZE;
    }

    @Override
    public int getSerializedSize(@NonNull ScheduleEqualityVirtualValue value) {
        return value.serializedSizeInBytes();
    }

    @Override
    public void serialize(
            @NonNull final ScheduleEqualityVirtualValue value, @NonNull final WritableSequentialData out) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(out);
        value.serialize(out);
    }

    @Override
    @Deprecated
    public void serialize(final ScheduleEqualityVirtualValue value, final ByteBuffer buffer) {
        value.serialize(buffer);
    }

    // Value deserialization

    @Override
    public ScheduleEqualityVirtualValue deserialize(@NonNull final ReadableSequentialData in) {
        Objects.requireNonNull(in);
        final var value = new ScheduleEqualityVirtualValue();
        value.deserialize(in);
        return value;
    }

    @Override
    @Deprecated
    public ScheduleEqualityVirtualValue deserialize(ByteBuffer buffer, long dataVersion) {
        final var value = new ScheduleEqualityVirtualValue();
        value.deserialize(buffer, (int) dataVersion);
        return value;
    }
}
