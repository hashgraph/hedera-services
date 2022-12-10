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
package com.hedera.node.app.state.merkle.disk;

import com.hedera.node.app.spi.state.WritableState;
import com.hedera.node.app.spi.state.WritableStateBase;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.Objects;

/**
 * An implementation of {@link WritableState} backed by a {@link VirtualMap}, resulting in a state
 * that is stored on disk.
 *
 * @param <K> The type of key for the state
 * @param <V> The type of value for the state
 */
public final class OnDiskWritableState<K extends Comparable<K>, V> extends WritableStateBase<K, V> {
    private final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> virtualMap;

    private final StateMetadata<K, V> md;

    public OnDiskWritableState(
            @NonNull final StateMetadata<K, V> md,
            @NonNull final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> virtualMap) {
        super(md.stateKey());
        this.virtualMap = Objects.requireNonNull(virtualMap);
        this.md = Objects.requireNonNull(md);
    }

    @Override
    protected V readFromDataSource(@NonNull K key) {
        final var k = new OnDiskKey<>(key, md.keyParser(), md.keyWriter());
        final var v = virtualMap.get(k);
        return v == null ? null : v.getValue();
    }

    @NonNull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        throw new UnsupportedOperationException("You cannot iterate over a virtual map's keys!");
    }

    @Override
    protected V getForModifyFromDataSource(@NonNull K key) {
        final var k = new OnDiskKey<>(key, md.keyParser(), md.keyWriter());
        final var v = virtualMap.getForModify(k);
        return v == null ? null : v.getValue();
    }

    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        final var k = new OnDiskKey<>(key, md.keyParser(), md.keyWriter());
        final var existing = virtualMap.getForModify(k);
        if (existing != null) {
            existing.setValue(value);
        } else {
            virtualMap.put(k, new OnDiskValue<>(value, md.valueParser(), md.valueWriter()));
        }
    }

    @Override
    protected void removeFromDataSource(@NonNull K key) {
        final var k = new OnDiskKey<>(key, md.keyParser(), md.keyWriter());
        virtualMap.remove(k);
    }
}
