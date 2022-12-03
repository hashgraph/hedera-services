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
package com.hedera.node.app.state.merkle;

import com.hedera.node.app.spi.state.Parser;
import com.hedera.node.app.spi.state.WritableState;
import com.hedera.node.app.spi.state.Writer;
import com.hedera.node.app.state.MutableStateBase;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * An implementation of {@link WritableState} backed by a {@link MerkleMap}, resulting in a state
 * that is stored in memory.
 *
 * @param <K> The type of key for the state
 * @param <V> The type of value for the state
 */
public final class InMemoryState<K, V> extends MutableStateBase<K, V> {
    /** The underlying merkle tree data structure with the data */
    private final MerkleMap<InMemoryKey<K>, InMemoryValue<K, V>> merkle;

    private final long valueClassId;
    private final Writer<K> keyWriter;
    private final Writer<V> valueWriter;
    private final Parser<K> keyParser;
    private final Parser<V> valueParser;

    /**
     * Create a new instance.
     *
     * @param stateKey The state key to use. Cannot be null.
     * @param merkleMap The backing merkle map. Cannot be null.
     */
    public InMemoryState(
            @NonNull String stateKey,
            @NonNull MerkleMap<InMemoryKey<K>, InMemoryValue<K, V>> merkleMap,
            final long valueClassId,
            @NonNull Parser<K> keyParser,
            @NonNull Parser<V> valueParser,
            @NonNull Writer<K> keyWriter,
            @NonNull Writer<V> valueWriter) {
        super(stateKey);
        this.valueClassId = valueClassId;
        this.merkle = Objects.requireNonNull(merkleMap);
        this.keyWriter = Objects.requireNonNull(keyWriter);
        this.valueWriter = Objects.requireNonNull(valueWriter);
        this.keyParser = Objects.requireNonNull(keyParser);
        this.valueParser = Objects.requireNonNull(valueParser);
    }

    @Override
    protected V readFromDataSource(@NonNull K key) {
        final var k = new InMemoryKey<>(key);
        final var leaf = merkle.get(k);
        return leaf == null ? null : leaf.getValue();
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
            merkle.put(
                    k,
                    new InMemoryValue<>(
                            valueClassId,
                            k,
                            value,
                            keyParser,
                            valueParser,
                            keyWriter,
                            valueWriter));
        }
    }

    @Override
    protected void removeFromDataSource(@NonNull K key) {
        final var k = new InMemoryKey<>(key);
        merkle.remove(k);
    }
}
