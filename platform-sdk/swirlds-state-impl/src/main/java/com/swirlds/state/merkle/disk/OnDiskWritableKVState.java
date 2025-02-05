/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableKVStateBase;
import com.swirlds.state.spi.metrics.StoreMetrics;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
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
            @NonNull final String serviceName,
            @NonNull final String stateKey,
            @NonNull final Codec<K> keyCodec,
            @NonNull final Codec<V> valueCodec,
            @NonNull final VirtualMap virtualMap) {
        super(serviceName, stateKey);
        this.keyCodec = keyCodec;
        this.valueCodec = valueCodec;
        this.virtualMap = requireNonNull(virtualMap);
    }

    /** {@inheritDoc} */
    @Override
    protected V readFromDataSource(@NonNull K key) {
        final var value = virtualMap.get(getVirtualMapKey(key), valueCodec);
        // Log to transaction state log, what was read
        logMapGet(getLabel(), key, value);
        return value;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        // Log to transaction state log, what was iterated
        logMapIterate(getLabel(), virtualMap, keyCodec);
        return new OnDiskIterator<>(virtualMap, keyCodec);
    }

    /** {@inheritDoc} */
    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        final Bytes kb = keyCodec.toBytes(key);
        assert kb != null;
        // If we expect a lot of empty values, Bytes.EMPTY optimization below may be helpful, but
        // for now it just adds a call to measureRecord(), but benefits are unclear
        // final Bytes v = valueCodec.measureRecord(value) == 0 ? Bytes.EMPTY : valueCodec.toBytes(value);
        virtualMap.put(getVirtualMapKey(key), value, valueCodec);
        // Log to transaction state log, what was put
        logMapPut(getLabel(), key, value);
    }

    /** {@inheritDoc} */
    @Override
    protected void removeFromDataSource(@NonNull K key) {
        final var removed = virtualMap.remove(getVirtualMapKey(key), valueCodec);
        // Log to transaction state log, what was removed
        logMapRemove(getLabel(), key, removed);
    }

    /** {@inheritDoc} */
    @Override
    public long sizeOfDataSource() {
        final var size = virtualMap.size();
        // Log to transaction state log, size of map
        logMapGetSize(getLabel(), size);
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

    // TODO: test this method
    // TODO: refactor? (it is duplicated in OnDiskReadableKVState)
    /**
     * Generates a key for identifying an entry in the Virtual Map.
     * <p>
     * The key consists of:
     * <ul>
     *   <li>The first 2 bytes: the state ID (unsigned 16-bit, big-endian)</li>
     *   <li>The remaining bytes: the serialized form of the provided key</li>
     * </ul>
     * The state ID must be within [0..65535].
     * </p>
     *
     * @param key the key to serialize and append to the state ID
     * @return a {@link Bytes} object containing the state ID followed by the serialized key
     * @throws IllegalArgumentException if the state ID is outside [0..65535]
     */
    private Bytes getVirtualMapKey(final K key) {
        final int stateId = getStateId();

        if (stateId < 0 || stateId > 65535) {
            throw new IllegalArgumentException("State ID " + stateId + " must fit in [0..65535]");
        }

        // Allocate a single byte array to hold the 2-byte state ID and the serialized key.
        final byte[] bytes = new byte[Short.BYTES + keyCodec.measureRecord(key)];
        final BufferedData writer = BufferedData.wrap(bytes);

        // Write the state ID in big-endian order by writing two bytes.
        writer.writeByte((byte) ((stateId >>> 8) & 0xFF)); // High-order byte
        writer.writeByte((byte) (stateId & 0xFF)); // Low-order byte

        try {
            keyCodec.write(key, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return Bytes.wrap(bytes);
    }
}
