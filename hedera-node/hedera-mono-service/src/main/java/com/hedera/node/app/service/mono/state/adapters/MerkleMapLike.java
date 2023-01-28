package com.hedera.node.app.service.mono.state.adapters;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.merkle.map.MerkleMap;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface MerkleMapLike<K, V extends MerkleNode & Keyed<K>> {
    V remove(Object key);

    V get(Object key);

    V getForModify(K key);

    V put(K key, V value);

    int size();

    Set<K> keySet();

    boolean containsKey(Object key);

    V getOrDefault(Object key, V defaultValue);

    void forEach(BiConsumer<? super K, ? super V> action);

    Hash getHash();

    void archive();

    boolean isArchived();
    void forEachNode(Consumer<MerkleNode> operation);

    static <K, V extends MerkleNode & Keyed<K>> MerkleMapLike<K, V> from(MerkleMap<K, V> real) {
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
            public void forEach(final BiConsumer<? super K, ? super V> action) {
                real.forEach(action);
            }

            @Override
            public V getOrDefault(final Object key, final V defaultValue) {
                return real.getOrDefault(key, defaultValue);
            }

            @Override
            public boolean containsKey(final Object key) {
                return real.containsKey(key);
            }

            @Override
            public int size() {
                return real.size();
            }

            @Override
            public Set<K> keySet() {
                return real.keySet();
            }

            @Override
            public V remove(final Object key) {
                return real.remove(key);
            }

            @Override
            public V get(final Object key) {
                return real.get(key);
            }

            @Override
            public V getForModify(final K key) {
                return real.getForModify(key);
            }

            @Override
            public V put(final K key, final V value) {
                return real.put(key, value);
            }
        };
    }
}
