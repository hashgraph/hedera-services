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

import com.hedera.node.app.spi.state.Serdes;
import com.hedera.node.app.spi.state.serdes.ByteBufferDataInput;
import com.hedera.node.app.spi.state.serdes.ByteBufferDataOutput;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * An implementation of {@link VirtualKey} for Hedera applications.
 *
 * <p>The {@link OnDiskKey} is actually a wrapper for the "real" key, which is some business logic
 * object of type {@code K}. For example, the "real" key may be {@code AccountID}, but it must be
 * wrapped by an {@link OnDiskKey} to adapt it for use by the {@link VirtualMap}.
 *
 * <p>The {@code AccountID} itself is not directly serializable, and therefore a {@link Serdes} is
 * provided to handle all serialization needs for the "real" key. The {@link Serdes} is used to
 * convert the "real" key into bytes for hashing, saving to disk via the {@link VirtualMap}, reading
 * from disk, reconnect, and for state saving.
 *
 * @param <K> The type of key
 */
public final class OnDiskKey<K extends Comparable<? super K>> implements VirtualKey<OnDiskKey<K>> {
    @Deprecated(forRemoval = true)
    private static final long CLASS_ID = 0x2929238293892373L;
    /** The metadata */
    private final StateMetadata<K, ?> md;
    /** The {@link Serdes} used for handling serialization for the "real" key. */
    private final Serdes<K> serdes;
    /** The "real" key, such as AccountID. */
    private K key;

    // Default constructor provided for ConstructableRegistry, TO BE REMOVED ASAP
    @Deprecated(forRemoval = true)
    public OnDiskKey() {
        md = null;
        serdes = null;
    }

    /**
     * Creates a new OnDiskKey. Used by {@link OnDiskKeySerializer}.
     *
     * @param md The state metadata
     */
    public OnDiskKey(final StateMetadata<K, ?> md) {
        this.md = md;
        this.serdes = md.stateDefinition().keySerdes();
    }

    /**
     * Creates a new OnDiskKey.
     *
     * @param md The state metadata
     * @param key The "real" key
     */
    public OnDiskKey(final StateMetadata<K, ?> md, @NonNull final K key) {
        this(md);
        this.key = Objects.requireNonNull(key);
    }

    @NonNull
    public K getKey() {
        return key;
    }

    /** Writes the "real" key to the given stream. {@inheritDoc} */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream serializableDataOutputStream) throws IOException {
        serdes.write(key, serializableDataOutputStream);
    }

    @Override
    public void serialize(@NonNull final ByteBuffer byteBuffer) throws IOException {
        serdes.write(key, new ByteBufferDataOutput(byteBuffer));
    }

    @Override
    public void deserialize(@NonNull final ByteBuffer byteBuffer, int ignored) throws IOException {
        key = serdes.parse(new ByteBufferDataInput(byteBuffer));
    }

    @Override
    public void deserialize(@NonNull final SerializableDataInputStream serializableDataInputStream, int ignored)
            throws IOException {
        key = serdes.parse(new DataInputStream(serializableDataInputStream));
    }

    @Override
    public long getClassId() {
        // SHOULD NOT ALLOW md TO BE NULL, but ConstructableRegistry has foiled me.
        return md == null ? CLASS_ID : md.onDiskKeyClassId();
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public int compareTo(@NonNull final OnDiskKey<K> o) {
        // By contract, throw NPE if o or o.key are null
        return key.compareTo(o.key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OnDiskKey<?> onDiskKey)) return false;
        return Objects.equals(key, onDiskKey.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
