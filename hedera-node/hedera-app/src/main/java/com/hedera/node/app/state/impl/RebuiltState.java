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

import com.hedera.node.app.spi.state.ReadableState;
import com.hedera.node.app.state.merkle.HederaStateImpl;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.fchashmap.FCHashMap;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Map;
import java.util.Objects;

/**
 * An implementation of {@link ReadableState} backed by a {@link FCHashMap} for aliases, that needs
 * to be rebuilt from the {@link HederaStateImpl}.
 *
 * @param <K> The type of key for the state
 * @param <V> The type of value for the state
 */
public final class RebuiltState<K, V> extends MutableStateBase<K, V> {
    private Map<K, V> aliases;

    // TODO This is broken, it also needs a reference to the state with account Ids,
    //      otherwise changes to this or queries from this won't map to the same
    //      account Ids and lead to errors!
    public RebuiltState(@NonNull final String stateKey, @NonNull Map<K, V> aliases) {
        super(stateKey);
        this.aliases = Objects.requireNonNull(aliases);
    }

    @Override
    protected V getForModifyFromDataSource(@NonNull K key) {
        return aliases.get(key);
    }

    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        aliases.put(key, value);
    }

    @Override
    protected void removeFromDataSource(@NonNull K key) {
        aliases.remove(key);
    }

    @Override
    protected V readFromDataSource(@NonNull K key) {
        return aliases.get(key);
    }

    @NonNull
    @Override
    protected <T extends MerkleNode> T merkleNode() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
