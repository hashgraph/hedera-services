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

import com.hedera.node.app.spi.state.ReadableState;
import com.hedera.node.app.spi.state.ReadableStateBase;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.Objects;

/**
 * An implementation of {@link ReadableState} backed by a {@link VirtualMap}, resulting in a state
 * that is stored on disk.
 *
 * @param <K> The type of key for the state
 * @param <V> The type of value for the state
 */
public final class OnDiskReadableState<K extends Comparable<K>, V> extends ReadableStateBase<K, V> {
    /** The backing merkle data structure to use */
    private final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> virtualMap;
    /** The metadata for this state */
    private final StateMetadata<K, V> md;

    /**
     * Create a new instance
     *
     * @param md The metadata
     * @param virtualMap the backing merkle structure to use
     */
    public OnDiskReadableState(
            @NonNull final StateMetadata<K, V> md,
            @NonNull final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> virtualMap) {
        super(md.stateKey());
        this.virtualMap = Objects.requireNonNull(virtualMap);
        this.md = Objects.requireNonNull(md);
    }

    /** {@inheritDoc} */
    @Override
    protected V readFromDataSource(@NonNull K key) {
        final var k = new OnDiskKey<>(key, md.keyParser(), md.keyWriter());
        final var v = virtualMap.get(k);
        return v == null ? null : v.getValue();
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        throw new UnsupportedOperationException("You cannot iterate over a virtual map's keys!");
    }
}
