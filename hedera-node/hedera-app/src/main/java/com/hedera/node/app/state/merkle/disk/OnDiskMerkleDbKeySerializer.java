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
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * An implementation of {@link KeySerializer}, responsible for converting an {@link OnDiskKey} into
 * bytes when hashing, and when saving to disk, and when converting from bytes back into an object.
 * This class is <strong>NOT</strong> used by the state saving system, which relies on {@link
 * OnDiskKey} being self serializable.
 *
 * <p>However, this class does, itself, need to be {@link SelfSerializable} because it is part of
 * the structure of a {@link VirtualMap} and needs to be restored when loaded from saved-state.
 *
 * @param <K>
 */
public final class OnDiskMerkleDbKeySerializer<K> implements KeySerializer<OnDiskKey<K>> {

    /** This is a hint for virtual maps, but isn't actually useful. We just pick some size. */
    private static final int TYPICAL_SIZE = 256;

    @Deprecated(forRemoval = true)
    private static final long CLASS_ID = 0x9992382838283412L;

    private final long classId;
    private final Codec<K> codec;
    private final StateMetadata<K, ?> md;

    // Default constructor provided for ConstructableRegistry, TO BE REMOVED ASAP
    @Deprecated(forRemoval = true)
    public OnDiskMerkleDbKeySerializer() {
        classId = CLASS_ID; // BAD!!
        codec = null;
        md = null;
    }

    public OnDiskMerkleDbKeySerializer(@NonNull final StateMetadata<K, ?> md) {
        this.classId = md.onDiskKeySerializerClassId();
        this.md = Objects.requireNonNull(md);
        this.codec = md.stateDefinition().keyCodec();
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

    // Key info

    /** {@inheritDoc} */
    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    // Key serialization

    /** {@inheritDoc} */
    @Override
    public int getSerializedSize() {
        // We're going to use variable size keys, always. MerkleDB was designed with
        // fast paths if you knew you were using a Long as the key -- but we really
        // cannot use that. The problem manifests itself with state proofs. We wanted
        // to be able to say that we only store a long for the account ID, and implicitly
        // know the shard and realm as they could be system properties when the JVM
        // is started up. The problem is that the shard and realm MUST be part of the
        // serialized bytes, because they MUST be part of the state proof. Otherwise, you
        // would only have a proof of the accountNum, but not the shard and realm (well,
        // you may be able to reconstruct the shard and realm via the signature on the
        // state proof, if each shard has its own public key / ledger id, but that is
        // very cumbersome!).
        //
        // With protobuf serialization, we can encode shard/realm/num as a single long
        // in many cases, since "0" is the default for the fields, and therefore can
        // be skipped entirely. And anyway they are varint encoded, so for low values
        // of shard and realm (likely to be true for a very long time), they will
        // at most consume 7 bits each of the value.
        //
        // While that may be true, the KeySerializer requires either fixed size or variable
        // size, and protobuf would either be fixed > 8 bytes, or variable sized, and being
        // fixed but greater than 8 bytes doesn't help us in performance, so we will
        // have to use variable size always.
        return VARIABLE_DATA_SIZE;
    }

    /** {@inheritDoc} */
    @Override
    public int getTypicalSerializedSize() {
        // Future work: check mainnet states about actual average virtual key size
        return TYPICAL_SIZE;
    }

    /** {@inheritDoc} */
    @Override
    public int serialize(@NonNull final OnDiskKey<K> key, @NonNull final ByteBuffer buffer) throws IOException {
        // This creates a very short-lived BufferedData object, which increases load on GC.
        // It will be streamlined in future MerkleDb versions
        final BufferedData out = BufferedData.wrap(buffer);
        final long originalPos = out.position();
        out.skip(Integer.BYTES); // will be used later to store the size
        codec.write(key.getKey(), out);
        final long finalPos = out.position();
        final int size = (int) (finalPos - originalPos);
        // It would be great to just call out.setInt(originalPos, size), but BufferedData doesn't provide
        // such API yet. See https://github.com/hashgraph/pbj/issues/79 for details. For now, assuming
        // buffer and out share positions, use ByteBuffer.putInt() instead
        buffer.putInt((int) originalPos, size); // now update the size
        return size;
    }

    // Key deserialization

    /** {@inheritDoc} */
    @Override
    public int deserializeKeySize(@NonNull final ByteBuffer buffer) {
        return buffer.getInt(buffer.position()); // don't mess up with the current position
    }

    /** {@inheritDoc} */
    @Override
    public OnDiskKey<K> deserialize(@NonNull final ByteBuffer buffer, final long version) throws IOException {
        // This creates a very short-lived BufferedData object, which increases load on GC.
        // It will be streamlined in future MerkleDb versions
        final BufferedData in = BufferedData.wrap(buffer);
        in.skip(Integer.BYTES); // skip the length we wrote
        final K k = codec.parse(in);
        Objects.requireNonNull(k);
        return new OnDiskKey<>(md, k);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(@NonNull final ByteBuffer buffer, final int version, @Nullable final OnDiskKey<K> key)
            throws IOException {
        // I really don't have a fast path for this. Which is very problematic for performance.
        // All we can do is serialize one or deserialize the other! It would be nice if PBJ
        // had a special method for this, but then we'd have to pipe it through all our APIs again
        // or create some kind of Codec object with all this stuff on it.
        final OnDiskKey<K> other = deserialize(buffer, version);
        return other.equals(key);
    }
}
