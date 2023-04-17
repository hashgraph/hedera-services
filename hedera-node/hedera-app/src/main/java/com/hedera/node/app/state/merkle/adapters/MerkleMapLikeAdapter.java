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

package com.hedera.node.app.state.merkle.adapters;

import com.hedera.node.app.service.mono.context.StateChildrenProvider;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.node.app.state.merkle.memory.InMemoryKey;
import com.hedera.node.app.state.merkle.memory.InMemoryValue;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Adapts a {@link MerkleMap} constructed by {@code MerkleHederaState#MerkleStates} by "unwrapping"
 * its {@link InMemoryKey} and {@link InMemoryValue} containers, so that a
 * {@code MerkleMap<InMemoryKey<K>, InMemoryValue<K, V>>} appears as a {@code MerkleMapLike<K, V>}.
 *
 * <p>This allows us to use a {@link MerkleHederaState} as a {@link StateChildrenProvider} binding
 * within a {@link com.hedera.node.app.HederaApp} instance, which is important while we are relying
 * heavily on adapters around {@code mono-service} components.
 */
public final class MerkleMapLikeAdapter {
    private MerkleMapLikeAdapter() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static <K extends Comparable<? super K>, V extends MerkleNode & Keyed<K>> MerkleMapLike<K, V> unwrapping(
            final StateMetadata<K, V> md, final MerkleMap<InMemoryKey<K>, InMemoryValue<K, V>> real) {
        return new MerkleMapLike<>() {
            @Override
            public void forEachNode(final BiConsumer<? super K, ? super V> action) {
                real.forEachNode((final MerkleNode node) -> {
                    if (node instanceof Keyed) {
                        final InMemoryValue<K, V> leaf = node.cast();
                        action.accept(leaf.getKey().key(), leaf.getValue());
                    }
                });
            }

            @Override
            public boolean isEmpty() {
                return real.isEmpty();
            }

            @Override
            public Hash getHash() {
                return real.getHash();
            }

            @Override
            @SuppressWarnings("unchecked")
            public V remove(final Object key) {
                final var removed = real.remove(new InMemoryKey<>((K) key));
                return removed != null ? removed.getValue() : null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public V get(final Object key) {
                return withKeyIfPresent((K) key, real.get(new InMemoryKey<>((K) key)));
            }

            @Override
            public V getForModify(final K key) {
                return withKeyIfPresent(key, real.getForModify(new InMemoryKey<>(key)));
            }

            @Override
            public V put(final K key, final V value) {
                final var wrappedKey = new InMemoryKey<>(key);
                final var replaced = real.put(wrappedKey, new InMemoryValue<>(md, wrappedKey, value));
                return replaced != null ? replaced.getValue() : null;
            }

            @Override
            public int size() {
                return real.size();
            }

            @Override
            public Set<K> keySet() {
                return real.keySet().stream().map(InMemoryKey::key).collect(Collectors.toSet());
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean containsKey(final Object key) {
                return real.containsKey(new InMemoryKey<>((K) key));
            }

            @Override
            @SuppressWarnings("unchecked")
            public V getOrDefault(final Object key, final V defaultValue) {
                final var wrappedKey = new InMemoryKey<>((K) key);
                final var wrappedDefaultValue = new InMemoryValue<>(md, wrappedKey, defaultValue);
                return real.getOrDefault(wrappedKey, wrappedDefaultValue).getValue();
            }

            @Override
            public void forEach(final BiConsumer<? super K, ? super V> action) {
                real.forEach((k, v) -> action.accept(k.key(), v.getValue()));
            }

            @Nullable
            private V withKeyIfPresent(final @NonNull K key, final @Nullable InMemoryValue<K, V> present) {
                if (present != null) {
                    final var answer = present.getValue();
                    Objects.requireNonNull(answer).setKey(key);
                    return answer;
                } else {
                    return null;
                }
            }
        };
    }
}
