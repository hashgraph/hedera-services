package com.hedera.node.app.state.merkle.adapters;

import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
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

public class VirtualMapLikeAdapter {
    public static <K extends VirtualKey<? super K>, V extends VirtualValue> VirtualMapLike<K, V> unwrapping(
            final StateMetadata<K, V> md,
            final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> real) {
        return new VirtualMapLike<>() {
            @Override
            public boolean release() {
                return real.release();
            }

            @Override
            public Hash getHash() {
                return real.getHash();
            }

            @Override
            public VirtualDataSource<?, ?> getDataSource() {
                return real.getDataSource();
            }

            @Override
            public void extractVirtualMapData(
                    final ThreadManager threadManager,
                    final InterruptableConsumer<Pair<K, V>> handler,
                    final int threadCount) throws InterruptedException {
                final var unwrappingHandler = new InterruptableConsumer<Pair<OnDiskKey<K>, OnDiskValue<V>>>() {
                    @Override
                    public void accept(
                            final Pair<OnDiskKey<K>, OnDiskValue<V>> pair) throws InterruptedException {
                        handler.accept(Pair.of(pair.getKey().getKey(), pair.getValue().getValue()));
                    }
                };
                VirtualMapMigration.extractVirtualMapData(
                        threadManager, real, unwrappingHandler, threadCount);
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
                real.put(new OnDiskKey<>(md, key), new OnDiskValue<>(md, value));
            }

            @Override
            public V get(final K key) {
                final var found = real.get(new OnDiskKey<>(md, key));
                return found != null ? found.getValue() : null;
            }

            @Override
            public V getForModify(final K key) {
                final var mutable = real.getForModify(new OnDiskKey<>(md, key));
                return mutable != null ? mutable.getValue() : null;
            }

            @Override
            public boolean containsKey(final K key) {
                return real.containsKey(new OnDiskKey<>(md, key));
            }

            @Override
            public boolean isEmpty() {
                return real.isEmpty();
            }

            @Override
            public V remove(final K key) {
                final var removed = real.remove(new OnDiskKey<>(md, key));
                return removed != null ? removed.getValue() : null;
            }
        };
    }

    public static <K extends VirtualLongKey, V extends VirtualValue> VirtualMapLike<K, V> unwrappingLongKeyed(
            final StateMetadata<K, V> md,
            final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> real) {
        return new VirtualMapLike<>() {
            @Override
            public boolean release() {
                return real.release();
            }

            @Override
            public Hash getHash() {
                return real.getHash();
            }

            @Override
            public VirtualDataSource<?, ?> getDataSource() {
                return real.getDataSource();
            }

            @Override
            public void extractVirtualMapData(
                    final ThreadManager threadManager,
                    final InterruptableConsumer<Pair<K, V>> handler,
                    final int threadCount) throws InterruptedException {
                final var unwrappingHandler = new InterruptableConsumer<Pair<OnDiskKey<K>, OnDiskValue<V>>>() {
                    @Override
                    public void accept(
                            final Pair<OnDiskKey<K>, OnDiskValue<V>> pair) throws InterruptedException {
                        handler.accept(Pair.of(pair.getKey().getKey(), pair.getValue().getValue()));
                    }
                };
                VirtualMapMigration.extractVirtualMapData(
                        threadManager, real, unwrappingHandler, threadCount);
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
                real.put(new OnDiskKey<>(md, key), new OnDiskValue<>(md, value));
            }

            @Override
            public V get(final K key) {
                final var found = real.get(new OnDiskKey<>(md, key));
                return found != null ? found.getValue() : null;
            }

            @Override
            public V getForModify(final K key) {
                final var mutable = real.getForModify(new OnDiskKey<>(md, key));
                return mutable != null ? mutable.getValue() : null;
            }

            @Override
            public boolean containsKey(final K key) {
                return real.containsKey(new OnDiskKey<>(md, key));
            }

            @Override
            public boolean isEmpty() {
                return real.isEmpty();
            }

            @Override
            public V remove(final K key) {
                final var removed = real.remove(new OnDiskKey<>(md, key));
                return removed != null ? removed.getValue() : null;
            }
        };
    }
}
