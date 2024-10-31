/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.state.merkle.disk;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.virtualmap.serialize.ValueSerializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;

/**
 * An implementation of {@link ValueSerializer}, required by the {@link
 * com.swirlds.virtualmap.VirtualMap} for creating new {@link OnDiskValue}s.
 *
 * @param <V> The type of the value in the virtual map
 */
public final class OnDiskValueSerializer<V> implements ValueSerializer<OnDiskValue<V>> {

    private static final long CLASS_ID = 0x3992113882234886L;

    private static final int VERSION = 1;

    // guesstimate of the typical size of a serialized value
    private static final int TYPICAL_SIZE = 1024;

    private final long serializerClassId;
    private final long valueClassId;
    private final Codec<V> codec;

    // Default constructor provided for ConstructableRegistry, TO BE REMOVED ASAP
    @Deprecated(forRemoval = true)
    public OnDiskValueSerializer() {
        codec = null;
        serializerClassId = CLASS_ID;
        valueClassId = 0; // invalid class id
    }

    /**
     * Create a new instance. This is created at registration time, it doesn't need to serialize
     * anything to disk.
     */
    public OnDiskValueSerializer(final long serializerClassId, final long valueClassId, @NonNull final Codec<V> codec) {
        this.codec = Objects.requireNonNull(codec);
        this.serializerClassId = serializerClassId;
        this.valueClassId = valueClassId;
    }

    // Serializer info

    @Override
    public long getClassId() {
        return serializerClassId;
    }

    @Override
    public int getClassVersion() {
        return VERSION;
    }

    // Value info

    @Override
    public long getCurrentDataVersion() {
        return OnDiskValue.VERSION;
    }

    // Value serialization

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getSerializedSize(OnDiskValue<V> value) {
        assert codec != null;
        return codec.measureRecord(value.getValue());
    }

    @Override
    public int getTypicalSerializedSize() {
        return TYPICAL_SIZE;
    }

    @Override
    public void serialize(@NonNull final OnDiskValue<V> value, @NonNull final WritableSequentialData out) {
        assert codec != null;
        // Future work: https://github.com/hashgraph/pbj/issues/73
        try {
            codec.write(value.getValue(), out);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Value deserialization

    @Override
    public OnDiskValue<V> deserialize(@NonNull final ReadableSequentialData in) {
        assert codec != null;
        // Future work: https://github.com/hashgraph/pbj/issues/73
        try {
            final V value = codec.parse(in);
            return new OnDiskValue<>(valueClassId, codec, value);
        } catch (final ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
