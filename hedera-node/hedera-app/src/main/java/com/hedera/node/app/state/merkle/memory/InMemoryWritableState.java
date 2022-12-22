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

import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.Objects;

/**
 * An implementation of {@link WritableKVState} backed by a {@link MerkleMap}, resulting in a state
 * that is stored in memory.
 *
 * @param <K> The type of key for the state
 * @param <V> The type of value for the state
 */
public final class InMemoryWritableState<K extends Comparable<K>, V>
        extends WritableKVStateBase<K, V> {
    /** The underlying merkle tree data structure with the data */
    private final MerkleMap<InMemoryKey<K>, InMemoryValue<K, V>> merkle;

    private final StateMetadata<K, V> md;

    /**
     * Create a new instance.
     *
     * @param md the state metadata
     * @param merkleMap The backing merkle map
     */
    public InMemoryWritableState(
            @NonNull final StateMetadata<K, V> md,
            @NonNull MerkleMap<InMemoryKey<K>, InMemoryValue<K, V>> merkleMap) {
        super(md.stateDefinition().stateKey());
        this.md = md;
        this.merkle = Objects.requireNonNull(merkleMap);
    }

    @Override
    protected V readFromDataSource(@NonNull K key) {
        final var k = new InMemoryKey<>(key);
        final var leaf = merkle.get(k);
        return leaf == null ? null : leaf.getValue();
    }

    @NonNull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        return merkle.keySet().stream().map(InMemoryKey::key).iterator();
    }

    @Override
    protected V getForModifyFromDataSource(@NonNull K key) {
        final var k = new InMemoryKey<>(key);
        final var leaf = merkle.getForModify(k);
        return leaf == null ? null : leaf.getValue();
    }

    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        final var k = new InMemoryKey<>(key);
        final var existing = merkle.getForModify(k);
        if (existing != null) {
            existing.setValue(value);
        } else {
            merkle.put(k, new InMemoryValue<>(md, k, value));
        }
    }

    @Override
    protected void removeFromDataSource(@NonNull K key) {
        final var k = new InMemoryKey<>(key);
        merkle.remove(k);
    }
}
