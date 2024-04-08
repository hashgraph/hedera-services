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

package com.hedera.node.app.state.merkle.disk;

import static com.hedera.node.app.state.logging.TransactionStateLogger.*;

import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.swirlds.metrics.api.Metrics;
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
public final class OnDiskWritableKVState<K, V> extends WritableKVStateBase<K, V> {
    /** The backing merkle data structure */
    private final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> virtualMap;

    private final StateMetadata<K, V> md;

    private OnDiskWritableKvStateMetrics kvStateMetrics;

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
        final var value = v == null ? null : v.getValue();
        // Log to transaction state log, what was read
        logMapGet(getStateKey(), key, value);
        return value;
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
        final var value = v == null ? null : v.getValue();
        // Log to transaction state log, what was read
        logMapGetForModify(getStateKey(), key, value);
        return value;
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
        // Log to transaction state log, what was put
        logMapPut(getStateKey(), key, value);
    }

    /** {@inheritDoc} */
    @Override
    protected void removeFromDataSource(@NonNull K key) {
        final var k = new OnDiskKey<>(md, key);
        final var removed = virtualMap.remove(k);
        // Log to transaction state log, what was removed
        logMapRemove(getStateKey(), key, removed);
    }

    /** {@inheritDoc} */
    @Override
    public long sizeOfDataSource() {
        final var size = virtualMap.size();
        // Log to transaction state log, size of map
        logMapGetSize(getStateKey(), size);
        return size;
    }

    @Override
    public void setupMetrics(@NonNull Metrics metrics, @NonNull String name, @NonNull String label, long maxCapacity) {
        kvStateMetrics = new OnDiskWritableKvStateMetrics(metrics, name, label, sizeOfDataSource(), maxCapacity);
    }

    @Override
    public void commit() {
        super.commit();

        if (kvStateMetrics != null) {
            kvStateMetrics.updateMetrics(sizeOfDataSource());
        }
    }
}
