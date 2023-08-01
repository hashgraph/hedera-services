/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.serialize.ValueSerializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class OnDiskMerkleDbValueSerializer<V> implements ValueSerializer<OnDiskValue<V>> {

    @Deprecated(forRemoval = true)
    private static final long CLASS_ID = 0x3992113882234886L;

    private final long classId;
    private final Codec<V> codec;
    private final StateMetadata<?, V> md;

    // Default constructor provided for ConstructableRegistry, TO BE REMOVED ASAP
    @Deprecated(forRemoval = true)
    public OnDiskMerkleDbValueSerializer() {
        classId = CLASS_ID; // BAD!!
        md = null;
        codec = null;
    }

    /**
     * Create a new instance. This is created at registration time, it doesn't need to serialize
     * anything to disk.
     */
    public OnDiskMerkleDbValueSerializer(@NonNull final StateMetadata<?, V> md) {
        this.classId = md.onDiskValueSerializerClassId();
        this.md = Objects.requireNonNull(md);
        this.codec = md.stateDefinition().valueCodec();
    }

    // Serializer info

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        return classId;
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return 1;
    }

    // Value info

    /** {@inheritDoc} */
    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    // Value serialization

    /** {@inheritDoc} */
    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    /** {@inheritDoc} */
    @Override
    public int getTypicalSerializedSize() {
        // Future work: check mainnet states about actual average virtual value size
        return 256;
    }

    /** {@inheritDoc} */
    @Override
    public int serialize(@NonNull final OnDiskValue<V> value, @NonNull final ByteBuffer buffer) throws IOException {
        // This creates a very short-lived BufferedData object, which increases load on GC.
        // It will be streamlined in future MerkleDb versions
        final BufferedData out = BufferedData.wrap(buffer);
        final long originalPos = out.position();
        // For on-disk values, there is no need to store the size, but let's be consistent with on-disk keys
        out.skip(Integer.BYTES); // will be used to store the size
        codec.write(value.getValue(), out);
        final long finalPos = out.position();
        final int size = (int) (finalPos - originalPos);
        // It would be great to just call out.setInt(originalPos, size), but BufferedData doesn't provide
        // such API yet. See https://github.com/hashgraph/pbj/issues/79 for details. For now, assuming
        // buffer and out share positions, use ByteBuffer.putInt() instead
        buffer.putInt((int) originalPos, size);
        return size;
    }

    // Value deserialization

    /** {@inheritDoc} */
    @Override
    public OnDiskValue<V> deserialize(@NonNull final ByteBuffer buffer, final long version) throws IOException {
        // This creates a very short-lived BufferedData object, which increases load on GC.
        // It will be streamlined in future MerkleDb versions
        final BufferedData in = BufferedData.wrap(buffer);
        in.skip(Integer.BYTES); // skip the stored size
        final V value = codec.parse(in);
        return new OnDiskValue<>(md, value);
    }
}
