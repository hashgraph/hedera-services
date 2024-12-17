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

package com.swirlds.state.merkle.memory;

import static com.swirlds.state.merkle.logging.StateLogger.logMapGet;
import static com.swirlds.state.merkle.logging.StateLogger.logMapGetForModify;
import static com.swirlds.state.merkle.logging.StateLogger.logMapGetSize;
import static com.swirlds.state.merkle.logging.StateLogger.logMapIterate;
import static com.swirlds.state.merkle.logging.StateLogger.logMapPut;
import static com.swirlds.state.merkle.logging.StateLogger.logMapRemove;

import com.hedera.pbj.runtime.Codec;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableKVStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Iterator;
import java.util.Objects;

/**
 * An implementation of {@link WritableKVState} backed by a {@link MerkleMap}, resulting in a state
 * that is stored in memory.
 *
 * @param <K> The type of key for the state
 * @param <V> The type of value for the state
 */
@SuppressWarnings("DuplicatedCode")
public final class InMemoryWritableKVState<K, V> extends WritableKVStateBase<K, V> {
    /** The underlying merkle tree data structure with the data */
    private final MerkleMap<InMemoryKey<K>, InMemoryValue<K, V>> merkle;

    private final Codec<K> keyCodec;
    private final Codec<V> valueCodec;

    private final long inMemoryValueClassId;

    /**
     * Create a new instance.
     *
     * @param stateKey the state key
     * @param inMemoryValueClassId the class ID for the value
     * @param keyCodec the codec for the key
     * @param valueCodec the codec for the value
     * @param merkleMap The backing merkle map
     */
    public InMemoryWritableKVState(
            @NonNull final String stateKey,
            final long inMemoryValueClassId,
            @Nullable Codec<K> keyCodec,
            @NonNull Codec<V> valueCodec,
            @NonNull MerkleMap<InMemoryKey<K>, InMemoryValue<K, V>> merkleMap) {
        super(stateKey);
        this.keyCodec = keyCodec;
        this.valueCodec = valueCodec;
        this.inMemoryValueClassId = inMemoryValueClassId;
        this.merkle = Objects.requireNonNull(merkleMap);
    }

    @Override
    protected V readFromDataSource(@NonNull K key) {
        final var k = new InMemoryKey<>(key);
        final var leaf = merkle.get(k);
        final var value = leaf == null ? null : leaf.getValue();
        // Log to transaction state log, what was read
        logMapGet(getStateKey(), key, value);
        return value;
    }

    @NonNull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        final var keySet = merkle.keySet();
        // Log to transaction state log, what was iterated
        logMapIterate(getStateKey(), keySet);
        return keySet.stream().map(InMemoryKey::key).iterator();
    }

    @Override
    protected V getForModifyFromDataSource(@NonNull K key) {
        final var k = new InMemoryKey<>(key);
        final var leaf = merkle.getForModify(k);
        final var value = leaf == null ? null : leaf.getValue();
        // Log to transaction state log, what was read
        logMapGetForModify(getStateKey(), key, value);
        return value;
    }

    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        final var k = new InMemoryKey<>(key);
        final var existing = merkle.getForModify(k);
        if (existing != null) {
            existing.setValue(value);
        } else {
            merkle.put(k, new InMemoryValue<>(inMemoryValueClassId, keyCodec, valueCodec, k, value));
        }
        // Log to transaction state log, what was put
        logMapPut(getStateKey(), key, value);
    }

    @Override
    protected void removeFromDataSource(@NonNull K key) {
        final var k = new InMemoryKey<>(key);
        final var removed = merkle.remove(k);
        // Log to transaction state log, what was removed
        logMapRemove(getStateKey(), key, removed);
    }

    /** {@inheritDoc} */
    @Override
    public long sizeOfDataSource() {
        final var size = merkle.size();
        // Log to transaction state log, size of map
        logMapGetSize(getStateKey(), size);
        return size;
    }
}
