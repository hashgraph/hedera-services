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

package com.hedera.node.app.service.mono.state.adapters;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Set;
import java.util.function.BiConsumer;

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

    boolean isEmpty();

    void forEachNode(BiConsumer<? super K, ? super V> action);

    static <K, V extends MerkleNode & Keyed<K>> MerkleMapLike<K, V> from(MerkleMap<K, V> real) {
        return new MerkleMapLike<>() {
            @Override
            public void forEachNode(final BiConsumer<? super K, ? super V> action) {
                real.forEachNode((final MerkleNode node) -> {
                    if (node instanceof Keyed) {
                        final V leaf = node.cast();
                        action.accept(leaf.getKey(), leaf);
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
