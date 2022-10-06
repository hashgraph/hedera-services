/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.files.store;

import static java.util.stream.Collectors.toSet;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class BytesStoreAdapter<K, V> extends AbstractMap<K, V> {
    private final Class<K> kType;
    private final Function<String, K> toK;
    private final Function<K, String> fromK;
    private final Function<byte[], V> toV;
    private final Function<V, byte[]> fromV;
    private final Map<String, byte[]> delegate;

    private Optional<Predicate<String>> delegateEntryFilter = Optional.empty();

    public BytesStoreAdapter(
            Class<K> kType,
            Function<byte[], V> toV,
            Function<V, byte[]> fromV,
            Function<String, K> toK,
            Function<K, String> fromK,
            Map<String, byte[]> delegate) {
        this.toK = toK;
        this.toV = toV;
        this.kType = kType;
        this.fromK = fromK;
        this.fromV = fromV;
        this.delegate = delegate;
    }

    public void setDelegateEntryFilter(Predicate<String> filter) {
        delegateEntryFilter = Optional.of(filter);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(fromK.apply(kType.cast(key)));
    }

    @Override
    public V get(Object key) {
        return toV.apply(delegate.get(fromK.apply(kType.cast(key))));
    }

    @Override
    public V put(K key, V value) {
        return toV.apply(delegate.put(fromK.apply(key), fromV.apply(value)));
    }

    @Override
    public V remove(Object key) {
        return toV.apply(delegate.remove(fromK.apply(kType.cast(key))));
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return delegate.entrySet().stream()
                .filter(entry -> delegateEntryFilter.orElse(ignore -> true).test(entry.getKey()))
                .map(
                        entry ->
                                new AbstractMap.SimpleEntry<>(
                                        toK.apply(entry.getKey()), toV.apply(entry.getValue())))
                .collect(toSet());
    }
}
