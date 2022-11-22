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
package com.hedera.node.app.state.impl;

import com.hedera.node.app.spi.state.WritableState;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
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
public final class InMemoryState<K, V extends MerkleNode & Keyed<K>>
        extends MutableStateBase<K, V> {
    /** The underlying merkle tree data structure with the data */
    private final MerkleMap<K, V> merkle;

    /**
     * Create a new instance.
     *
     * @param stateKey The state key to use. Cannot be null.
     * @param merkleMap The backing merkle map. Cannot be null.
     */
    public InMemoryState(@NonNull String stateKey, @NonNull MerkleMap<K, V> merkleMap) {
        super(stateKey);
        this.merkle = Objects.requireNonNull(merkleMap);
    }

    @Override
    @NonNull
    protected V readFromDataSource(@NonNull K key) {
        return merkle.get(key);
    }

    @Override
    @NonNull
    protected V getForModifyFromDataSource(@NonNull K key) {
        return merkle.getForModify(key);
    }

    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        merkle.put(key, value);
    }

    @Override
    protected void removeFromDataSource(@NonNull K key) {
        merkle.remove(key);
    }

    @NonNull
    @Override
    protected <T extends MerkleNode> T merkleNode() {
        return (T) merkle;
    }
}
