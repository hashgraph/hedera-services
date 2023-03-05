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

package com.hedera.node.app.state.merkle.memory;

import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.pbj.runtime.io.DataInputStream;
import com.hedera.pbj.runtime.io.DataOutputStream;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Objects;

/** The value stored in a {@link MerkleMap} for in memory states */
public final class InMemoryValue<K extends Comparable<? super K>, V> extends PartialMerkleLeaf
        implements MerkleNode, Keyed<InMemoryKey<K>>, SelfSerializable, MerkleLeaf {

    @Deprecated(forRemoval = true)
    private static final long CLASS_ID = 0x657483284563728L;

    /** The key associated with this value. {@link MerkleMap} requires we do this. */
    private InMemoryKey<K> key;

    private final StateMetadata<K, V> md;
    /** The actual value. For example, it could be an Account or SmartContract. */
    private V val;

    // Default constructor provided for ConstructableRegistry, TO BE REMOVED ASAP
    @Deprecated(forRemoval = true)
    public InMemoryValue() {
        md = null;
    }

    /**
     * Used by the deserialization system to create an {@link InMemoryValue} that does not yet have
     * a value. Normally this should not be used.
     *
     * @param md The state metadata
     */
    public InMemoryValue(@NonNull final StateMetadata<K, V> md) {
        this.md = Objects.requireNonNull(md);
    }

    /**
     * Create a new instance with the given value.
     *
     * @param md The state metadata
     * @param key The associated key.
     * @param value The value.
     */
    public InMemoryValue(
            @NonNull final StateMetadata<K, V> md, @NonNull final InMemoryKey<K> key, @NonNull final V value) {
        this(md);
        this.key = Objects.requireNonNull(key);
        this.val = Objects.requireNonNull(value);
    }

    /** {@inheritDoc} */
    @Override
    public InMemoryValue<K, V> copy() {
        throwIfImmutable();
        throwIfDestroyed();

        final var cp = new InMemoryValue<>(md, key, val);
        setImmutable(true);
        return cp;
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        // Null `md` for ConstructableRegistry, TO BE REMOVED ASAP
        return md == null ? CLASS_ID : md.inMemoryValueClassId();
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return 1;
    }

    /** {@inheritDoc} */
    @Override
    public InMemoryKey<K> getKey() {
        return key;
    }

    /** {@inheritDoc} */
    @Override
    public void setKey(@NonNull final InMemoryKey<K> inMemoryKey) {
        throwIfImmutable();
        this.key = Objects.requireNonNull(inMemoryKey);
    }

    /**
     * Gets the value.
     *
     * @return The value.
     */
    @Nullable
    public V getValue() {
        return val;
    }

    /**
     * Sets the value. Cannot be called if the leaf is immutable.
     *
     * @param value the new value
     */
    public void setValue(@Nullable final V value) {
        throwIfImmutable();
        this.val = value;
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int ignored) throws IOException {
        final var keySerdes = md.stateDefinition().keyCodec();
        final var valueSerdes = md.stateDefinition().valueCodec();
        final var k = keySerdes.parse(new DataInputStream(serializableDataInputStream));
        this.key = new InMemoryKey<>(k);
        this.val = valueSerdes.parse(new DataInputStream(serializableDataInputStream));
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
        final var keySerdes = md.stateDefinition().keyCodec();
        final var valueSerdes = md.stateDefinition().valueCodec();
        keySerdes.write(key.key(), new DataOutputStream(serializableDataOutputStream));
        valueSerdes.write(val, new DataOutputStream(serializableDataOutputStream));
    }
}
