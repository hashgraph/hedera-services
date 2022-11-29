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
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * An implementation of {@link WritableState} backed by a {@link VirtualMap}, resulting in a state
 * that is stored on disk.
 *
 * @param <K> The type of key for the state
 * @param <V> The type of value for the state
 */
public final class OnDiskState<K extends VirtualKey<? super K>, V extends VirtualValue>
        extends MutableStateBase<K, V> {
    private final VirtualMap<K, V> virtualMap;

    public OnDiskState(@NonNull final String stateKey, @NonNull final VirtualMap<K, V> virtualMap) {
        super(stateKey);
        this.virtualMap = Objects.requireNonNull(virtualMap);
    }

    @Override
    protected V readFromDataSource(@NonNull K key) {
        return virtualMap.get(key);
    }

    @Override
    protected V getForModifyFromDataSource(@NonNull K key) {
        return virtualMap.getForModify(key);
    }

    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        virtualMap.put(key, value);
    }

    @Override
    protected void removeFromDataSource(@NonNull K key) {
        virtualMap.remove(key);
    }

    @NonNull
    @Override
    protected <T extends MerkleNode> T merkleNode() {
        return (T) virtualMap;
    }
}
