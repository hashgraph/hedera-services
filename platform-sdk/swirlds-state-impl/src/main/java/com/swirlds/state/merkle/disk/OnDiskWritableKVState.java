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

package com.swirlds.state.merkle.disk;

import static com.swirlds.state.merkle.logging.StateLogger.logMapGet;
import static com.swirlds.state.merkle.logging.StateLogger.logMapGetSize;
import static com.swirlds.state.merkle.logging.StateLogger.logMapIterate;
import static com.swirlds.state.merkle.logging.StateLogger.logMapPut;
import static com.swirlds.state.merkle.logging.StateLogger.logMapRemove;
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableKVStateBase;
import com.swirlds.state.spi.metrics.StoreMetrics;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;

/**
 * An implementation of {@link WritableKVState} backed by a {@link VirtualMap}, resulting in a state
 * that is stored on disk.
 *
 * @param <K> The type of key for the state
 * @param <V> The type of value for the state
 */
public final class OnDiskWritableKVState<K, V> extends WritableKVStateBase<K, V> {

    /** The backing merkle data structure */
    private final VirtualMap virtualMap;

    @NonNull
    private final Codec<K> keyCodec;

    @NonNull
    private final Codec<V> valueCodec;

    private StoreMetrics storeMetrics;

    /**
     * Create a new instance
     *
     * @param stateKey     the state key
     * @param keyCodec     the codec for the key
     * @param valueCodec   the codec for the value
     * @param virtualMap   the backing merkle data structure to use
     */
    public OnDiskWritableKVState(
            String stateKey,
            @NonNull final Codec<K> keyCodec,
            @NonNull final Codec<V> valueCodec,
            @NonNull final VirtualMap virtualMap) {
        super(stateKey);
        this.keyCodec = keyCodec;
        this.valueCodec = valueCodec;
        this.virtualMap = requireNonNull(virtualMap);
    }

    /** {@inheritDoc} */
    @Override
    protected V readFromDataSource(@NonNull K key) {
        final var k = keyCodec.toBytes(key);
        final var v = virtualMap.get(k);
        try {
            final var value = v == null ? null : valueCodec.parse(v);
            // Log to transaction state log, what was read
            logMapGet(getStateKey(), key, value);
            return value;
        } catch (final ParseException e) {
            throw new RuntimeException("Failed to parse value from the data store (type mismatch?)", e);
        }
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        // Log to transaction state log, what was iterated
        logMapIterate(getStateKey(), virtualMap, keyCodec);
        return new OnDiskIterator<>(virtualMap, keyCodec);
    }

    /** {@inheritDoc} */
    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        final Bytes k = keyCodec.toBytes(key);
        assert k != null;
        final Bytes v = valueCodec.toBytes(value);
        // If we expect a lot of empty values, Bytes.EMPTY optimization below may be helpful, but
        // for now it just adds a call to measureRecord(), but benefits are unclear
        // final Bytes v = valueCodec.measureRecord(value) == 0 ? Bytes.EMPTY : valueCodec.toBytes(value);
        virtualMap.put(k, v);
        // Log to transaction state log, what was put
        logMapPut(getStateKey(), key, value);
    }

    /** {@inheritDoc} */
    @Override
    protected void removeFromDataSource(@NonNull K key) {
        final var k = keyCodec.toBytes(key);
        final var removed = virtualMap.remove(k);
        // Log to transaction state log, what was removed
        logMapRemove(getStateKey(), key, removed, valueCodec);
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
    public void setMetrics(@NonNull StoreMetrics storeMetrics) {
        this.storeMetrics = requireNonNull(storeMetrics);
    }

    @Override
    public void commit() {
        super.commit();

        if (storeMetrics != null) {
            storeMetrics.updateCount(sizeOfDataSource());
        }
    }
}
