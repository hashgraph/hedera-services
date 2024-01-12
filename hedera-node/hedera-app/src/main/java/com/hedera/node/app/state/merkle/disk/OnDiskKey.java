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

import static com.hedera.node.app.state.merkle.StateUtils.readFromStream;
import static com.hedera.node.app.state.merkle.StateUtils.writeToStream;

import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.pbj.runtime.Codec;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;

/**
 * An implementation of {@link VirtualKey} for Hedera applications.
 *
 * <p>The {@link OnDiskKey} is actually a wrapper for the "real" key, which is some business logic
 * object of type {@code K}. For example, the "real" key may be {@code AccountID}, but it must be
 * wrapped by an {@link OnDiskKey} to adapt it for use by the {@link VirtualMap}.
 *
 * <p>The {@code AccountID} itself is not directly serializable, and therefore a {@link Codec} is
 * provided to handle all serialization needs for the "real" key. The {@link Codec} is used to
 * convert the "real" key into bytes for hashing, saving to disk via the {@link VirtualMap}, reading
 * from disk, reconnect, and for state saving.
 *
 * @param <K> The type of key
 */
public final class OnDiskKey<K> implements VirtualKey {

    @Deprecated(forRemoval = true)
    private static final long CLASS_ID = 0x2929238293892373L;

    static final int VERSION = 1;

    /** The metadata */
    private final StateMetadata<K, ?> md;

    /** The "real" key, such as AccountID. */
    private K key;

    // Default constructor provided for ConstructableRegistry, TO BE REMOVED ASAP
    @Deprecated(forRemoval = true)
    public OnDiskKey() {
        md = null;
    }

    /**
     * Creates a new OnDiskKey. Used by {@link OnDiskKeySerializer}.
     *
     * @param md The state metadata
     */
    public OnDiskKey(@NonNull final StateMetadata<K, ?> md) {
        this.md = Objects.requireNonNull(md);
    }

    /**
     * Creates a new OnDiskKey.
     *
     * @param md The state metadata
     * @param key The "real" key
     */
    public OnDiskKey(@NonNull final StateMetadata<K, ?> md, @NonNull final K key) {
        this(md);
        this.key = Objects.requireNonNull(key);
    }

    @NonNull
    public K getKey() {
        return key;
    }

    @Override
    public long getClassId() {
        // SHOULD NOT ALLOW md TO BE NULL, but ConstructableRegistry has foiled me.
        return md == null ? CLASS_ID : md.onDiskKeyClassId();
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    /** Writes the "real" key to the given stream. {@inheritDoc} */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        final Codec<K> codec;
        if ((md == null) || ((codec = md.stateDefinition().keyCodec()) == null)) {
            throw new IllegalStateException("Cannot serialize on-disk key, null metadata / codec");
        }
        writeToStream(out, codec, key);
    }

    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, int ignored) throws IOException {
        final Codec<K> codec;
        if ((md == null) || ((codec = md.stateDefinition().keyCodec()) == null)) {
            throw new IllegalStateException("Cannot deserialize on-disk key, null metadata / codec");
        }
        key = readFromStream(in, codec);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OnDiskKey<?> onDiskKey)) {
            return false;
        }
        return Objects.equals(key, onDiskKey.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "OnDiskKey{" + "key=" + key + '}';
    }
}
