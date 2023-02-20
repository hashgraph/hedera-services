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
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualLongKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import org.apache.commons.lang3.tuple.Pair;

public interface VirtualMapLike<K extends VirtualKey<? super K>, V extends VirtualValue> {
    void registerMetrics(Metrics metrics);

    long size();

    void put(K key, V value);

    V get(K key);

    V getForModify(K key);

    boolean containsKey(K key);

    boolean isEmpty();

    V remove(K key);

    boolean release();

    Hash getHash();

    @SuppressWarnings("rawtypes")
    VirtualDataSource getDataSource();

    void extractVirtualMapData(ThreadManager threadManager, InterruptableConsumer<Pair<K, V>> handler, int threadCount)
            throws InterruptedException;

    static <K extends VirtualLongKey, V extends VirtualValue> VirtualMapLike<K, V> fromLongKeyed(
            final VirtualMap<K, V> real) {
        return new VirtualMapLike<>() {
            @Override
            public VirtualDataSource<K, V> getDataSource() {
                return real.getDataSource();
            }

            @Override
            public void extractVirtualMapData(
                    final ThreadManager threadManager,
                    final InterruptableConsumer<Pair<K, V>> handler,
                    final int threadCount)
                    throws InterruptedException {
                VirtualMapMigration.extractVirtualMapData(threadManager, real, handler, threadCount);
            }

            @Override
            public void registerMetrics(final Metrics metrics) {
                real.registerMetrics(metrics);
            }

            @Override
            public long size() {
                return real.size();
            }

            @Override
            public void put(final K key, final V value) {
                real.put(key, value);
            }

            @Override
            public V get(final K key) {
                return real.get(key);
            }

            @Override
            public boolean containsKey(final K key) {
                return real.containsKey(key);
            }

            @Override
            public boolean isEmpty() {
                return real.isEmpty();
            }

            @Override
            public V remove(final K key) {
                return real.remove(key);
            }

            @Override
            public V getForModify(final K key) {
                return real.getForModify(key);
            }

            @Override
            public boolean release() {
                return real.release();
            }

            @Override
            public Hash getHash() {
                return real.getHash();
            }
        };
    }

    static <K extends VirtualKey<K>, V extends VirtualValue> VirtualMapLike<K, V> from(final VirtualMap<K, V> real) {
        return new VirtualMapLike<>() {
            @Override
            public VirtualDataSource<K, V> getDataSource() {
                return real.getDataSource();
            }

            @Override
            public void extractVirtualMapData(
                    final ThreadManager threadManager,
                    final InterruptableConsumer<Pair<K, V>> handler,
                    final int threadCount)
                    throws InterruptedException {
                VirtualMapMigration.extractVirtualMapData(threadManager, real, handler, threadCount);
            }

            @Override
            public void registerMetrics(final Metrics metrics) {
                real.registerMetrics(metrics);
            }

            @Override
            public long size() {
                return real.size();
            }

            @Override
            public void put(final K key, final V value) {
                real.put(key, value);
            }

            @Override
            public V get(final K key) {
                return real.get(key);
            }

            @Override
            public boolean containsKey(final K key) {
                return real.containsKey(key);
            }

            @Override
            public boolean isEmpty() {
                return real.isEmpty();
            }

            @Override
            public V remove(final K key) {
                return real.remove(key);
            }

            @Override
            public V getForModify(final K key) {
                return real.getForModify(key);
            }

            @Override
            public boolean release() {
                return real.release();
            }

            @Override
            public Hash getHash() {
                return real.getHash();
            }
        };
    }
}
