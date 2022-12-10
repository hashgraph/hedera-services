/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
import com.hedera.node.app.state.merkle.StateUtils;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

/** */
public final class InMemoryValue<K, V> extends PartialMerkleLeaf
        implements MerkleNode, Keyed<InMemoryKey<K>>, SelfSerializable, MerkleLeaf {

    /** The key associated with this value. {@link MerkleMap} requires we do this. */
    private InMemoryKey<K> key;
    /** The actual value. For example, it could be an Account or SmartContract. */
    private V val;
    /** The metadata */
    @SuppressWarnings("FieldMayBeFinal")
    private StateMetadata<K, V> md;

    // Unfortunately, required by constructable registry
    @SuppressWarnings("unused")
    public InMemoryValue() {
        // These are ALL BOGUS
        this.md = null;
    }

    /**
     * Used by the deserialization system only
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
            @NonNull final StateMetadata<K, V> md,
            @NonNull final InMemoryKey<K> key,
            @NonNull final V value) {
        this(md);
        this.key = Objects.requireNonNull(key);
        this.val = Objects.requireNonNull(value);
    }

    @Override
    public InMemoryValue<K, V> copy() {
        throwIfImmutable();
        throwIfDestroyed();

        final var cp = new InMemoryValue<>(md, key, val);
        setImmutable(true);
        return cp;
    }

    @Override
    public long getClassId() {
        return StateUtils.computeValueClassId(md.serviceName(), md.stateKey());
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public InMemoryKey<K> getKey() {
        return key;
    }

    @Override
    public void setKey(@NonNull final InMemoryKey<K> inMemoryKey) {
        throwIfImmutable();
        this.key = Objects.requireNonNull(inMemoryKey);
    }

    public V getValue() {
        return val;
    }

    public void setValue(final V value) {
        throwIfImmutable();
        this.val = value;
    }

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int ignored)
            throws IOException {
        final var k = md.keyParser().parse(new DataInputStream(serializableDataInputStream));
        if (k == null) {
            throw new IllegalStateException("Deserialized a null key, which is not allowed!");
        }
        this.key = new InMemoryKey<>(k);
        this.val = md.valueParser().parse(new DataInputStream(serializableDataInputStream));
    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream)
            throws IOException {
        md.keyWriter().write(key.key(), serializableDataOutputStream);
        md.valueWriter().write(val, serializableDataOutputStream);
    }
}
