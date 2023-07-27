/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.merkle.disk;

import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.merkledb.serialize.ValueSerializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;

/**
 * An implementation of {@link ValueSerializer}, required by the {@link
 * com.swirlds.virtualmap.VirtualMap} for creating new {@link OnDiskValue}s.
 *
 * @param <V> virtual value type
 */
public final class OnDiskValueSerializerMerkleDb<V> implements ValueSerializer<OnDiskValue<V>> {

    @Deprecated(forRemoval = true)
    private static final long CLASS_ID = 0x3992113882234886L;

    private final StateMetadata<?, V> md;

    private final Codec<V> codec;

    // Default constructor provided for ConstructableRegistry, TO BE REMOVED ASAP
    @Deprecated(forRemoval = true)
    public OnDiskValueSerializerMerkleDb() {
        md = null;
        codec = null;
    }

    /**
     * Create a new instance. This is created at registration time, it doesn't need to serialize
     * anything to disk.
     */
    public OnDiskValueSerializerMerkleDb(@NonNull final StateMetadata<?, V> md) {
        this.md = Objects.requireNonNull(md);
        this.codec = md.stateDefinition().valueCodec();
    }

    // Serializer info

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        // SHOULD NOT ALLOW md TO BE NULL, but ConstructableRegistry has foiled me.
        return md == null ? CLASS_ID : md.onDiskValueSerializerClassId();
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return 1;
    }

    // Value info

    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    // Value serialization

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getSerializedSize(OnDiskValue<V> value) {
        return codec.measureRecord(value.getValue());
    }

    @Override
    public int getTypicalSerializedSize() {
        // Future work: check mainnet states about an average virtual entity size
        return 256;
    }

    @Override
    public void serialize(@NonNull final OnDiskValue<V> value, @NonNull final WritableSequentialData out) {
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
        // Future work: https://github.com/hashgraph/pbj/issues/73
        try {
            final V value = codec.parse(in);
            return new OnDiskValue<>(md, value);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
