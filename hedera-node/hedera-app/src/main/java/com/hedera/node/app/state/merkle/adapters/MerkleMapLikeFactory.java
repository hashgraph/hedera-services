package com.hedera.node.app.state.merkle.adapters;

import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.state.merkle.memory.InMemoryKey;
import com.hedera.node.app.state.merkle.memory.InMemoryValue;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.merkle.map.MerkleMap;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MerkleMapLikeFactory {
    public static <K extends Comparable<K>, V extends MerkleNode & Keyed<K>> MerkleMapLike<K, V> unwrapping(
            final MerkleMap<InMemoryKey<K>, InMemoryValue<K, V>> real) {
        return new MerkleMapLike<>() {
            @Override
            public void forEachNode(final Consumer<MerkleNode> operation) {
                real.forEachNode(operation);
            }

            @Override
            public void archive() {
                real.archive();
            }

            @Override
            public boolean isArchived() {
                return real.isArchived();
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
            public V get(final Object key) {
                final var present = real.get(new InMemoryKey<>((K) key));
                return present != null ? present.getValue() : null;
            }

            @Override
            public V getForModify(final K key) {
                final var modifiable = real.getForModify(new InMemoryKey<>(key));
                return modifiable != null ? modifiable.getValue() : null;
            }

            @Override
            public V put(final K key, final V value) {
                return real.put(new InMemoryKey<>(key), new InMemoryValue<>(key, value)).get();
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
            public V getOrDefault(final Object key, final V defaultValue) {
                return real.getOrDefault(
                        new InMemoryKey<>((K) key),
                        new InMemoryValue<>((K) key, defaultValue)).get();
            }

            @Override
            public void forEach(final BiConsumer<? super K, ? super V> action) {
                real.forEach((k, v) -> action.accept(k.key(), v.getValue()));
            }
        };
    }
}
