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

import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.Objects;

/**
 * An implementation of {@link WritableKVState} backed by a {@link VirtualMap}, resulting in a state
 * that is stored on disk.
 *
 * @param <K> The type of key for the state
 * @param <V> The type of value for the state
 */
public final class OnDiskWritableKVState<K extends Comparable<K>, V> extends WritableKVStateBase<K, V> {
    /** The backing merkle data structure */
    private final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> virtualMap;

    private final StateMetadata<K, V> md;

    /**
     * Create a new instance
     *
     * @param md the state metadata
     * @param virtualMap the backing merkle data structure to use
     */
    public OnDiskWritableKVState(
            @NonNull final StateMetadata<K, V> md, @NonNull final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> virtualMap) {
        super(md.stateDefinition().stateKey());
        this.md = md;
        this.virtualMap = Objects.requireNonNull(virtualMap);
    }

    /** {@inheritDoc} */
    @Override
    protected V readFromDataSource(@NonNull K key) {
        final var k = new OnDiskKey<>(md, key);
        final var v = virtualMap.get(k);
        return v == null ? null : v.getValue();
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        throw new UnsupportedOperationException("You cannot iterate over a virtual map's keys!");
    }

    /** {@inheritDoc} */
    @Override
    protected V getForModifyFromDataSource(@NonNull K key) {
        final var k = new OnDiskKey<>(md, key);
        final var v = virtualMap.getForModify(k);
        return v == null ? null : v.getValue();
    }

    /** {@inheritDoc} */
    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        final var k = new OnDiskKey<>(md, key);
        final var existing = virtualMap.getForModify(k);
        if (existing != null) {
            existing.setValue(value);
        } else {
            virtualMap.put(k, new OnDiskValue<>(md, value));
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void removeFromDataSource(@NonNull K key) {
        final var k = new OnDiskKey<>(md, key);
        virtualMap.remove(k);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public long sizeOfDataSource() {
        return virtualMap.size();
    }
}
