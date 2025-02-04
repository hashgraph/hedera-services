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
import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableKVStateBase;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;

/**
 * An implementation of {@link ReadableKVState} backed by a {@link VirtualMap}, resulting in a state
 * that is stored on disk.
 *
 * @param <K> The type of key for the state
 * @param <V> The type of value for the state
 */
public final class OnDiskReadableKVState<K, V> extends ReadableKVStateBase<K, V> {

    /** The backing merkle data structure to use */
    @NonNull
    private final VirtualMap megaMap;

    @NonNull
    private final Codec<K> keyCodec;

    @NonNull
    private final Codec<V> valueCodec;

    /**
     * Create a new instance
     *
     * @param stateKey
     * @param keyCodec
     * @param valueCodec
     * @param megaMap the backing merkle structure to use
     */
    public OnDiskReadableKVState(
            @NonNull final String serviceName,
            @NonNull final String stateKey,
            @NonNull final Codec<K> keyCodec,
            @NonNull final Codec<V> valueCodec,
            @NonNull final VirtualMap megaMap) {
        super(serviceName, stateKey);
        this.keyCodec = requireNonNull(keyCodec);
        this.valueCodec = requireNonNull(valueCodec);
        this.megaMap = requireNonNull(megaMap);
    }

    /** {@inheritDoc} */
    @Override
    protected V readFromDataSource(@NonNull K key) {
        final var value = megaMap.get(getMegaMapKey(key), valueCodec);
        // Log to transaction state log, what was read
        logMapGet(getLabel(), key, value);
        return value;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        // Log to transaction state log, what was iterated
        logMapIterate(getLabel(), megaMap, keyCodec);
        return new OnDiskIterator<>(megaMap, keyCodec);
    }

    /** {@inheritDoc} */
    @Override
    public long size() {
        final var size = megaMap.size();
        // Log to transaction state log, size of map
        logMapGetSize(getLabel(), size);
        return size;
    }

    @Override
    public void warm(@NonNull final K key) {
        megaMap.warm(getMegaMapKey(key));
    }

    // TODO: test this method
    // TODO: refactor? (it is duplicated in OnDiskWritableKVState)
    /**
     * Generates a key for identifying an entry in the MegaMap data structure.
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
    private Bytes getMegaMapKey(final K key) {
        final int stateId = getStateId();

        if (stateId < 0 || stateId > 65535) {
            throw new IllegalArgumentException("State ID " + stateId + " must fit in [0..65535]");
        }

        final ByteBuffer buffer = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) stateId);
        final Bytes stateIdBytes = Bytes.wrap(buffer.array());

        final Bytes keyBytes = keyCodec.toBytes(key);

        return stateIdBytes.append(keyBytes);
    }
}
