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

import static com.hedera.node.app.state.logging.TransactionStateLogger.logMapGet;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logMapGetSize;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logMapIterate;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.internal.merkle.VirtualLeafNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of {@link ReadableKVState} backed by a {@link VirtualMap}, resulting in a state
 * that is stored on disk.
 *
 * @param <K> The type of key for the state
 * @param <V> The type of value for the state
 */
public final class OnDiskReadableKVState<K, V> extends ReadableKVStateBase<K, V> {
    private static final Logger log = LogManager.getLogger(OnDiskReadableKVState.class);
    public static final AtomicBoolean LOG_READS = new AtomicBoolean(false);
    public static final AtomicBoolean LOG_CONSTRUCTIONS = new AtomicBoolean(false);
    public static final AtomicBoolean HAVE_LOGGED_MISSING_CONTENTS = new AtomicBoolean(false);

    private static final Consumer<Runnable> DEFAULT_RUNNER = Thread::startVirtualThread;

    /** The backing merkle data structure to use */
    private final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> virtualMap;

    private final StateMetadata<K, V> md;

    private final Consumer<Runnable> runner;

    /**
     * Create a new instance
     *
     * @param md the state metadata
     * @param virtualMap the backing merkle structure to use
     */
    public OnDiskReadableKVState(
            @NonNull final StateMetadata<K, V> md, @NonNull final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> virtualMap) {
        this(md, virtualMap, DEFAULT_RUNNER);
        if (LOG_CONSTRUCTIONS.get()) {
            log.info(
                    "In thread {}, constructing OnDiskReadableKVState for {} with virtualMap {} of size {}",
                    Thread.currentThread().getName(),
                    md,
                    virtualMap,
                    virtualMap.size());
            if (md.serviceName().equals(TokenService.NAME)
                    && md.stateDefinition().stateKey().equals(TokenServiceImpl.ACCOUNTS_KEY)) {
                final var onDiskKey = new OnDiskKey<>(
                        (StateMetadata<AccountID, ?>) md,
                        AccountID.newBuilder().accountNum(1L).build());
                final var value = virtualMap.get((OnDiskKey<K>) onDiskKey);
                log.info("OnDiskValue for account 1: {}", virtualMap.get((OnDiskKey<K>) onDiskKey));
                log.info("VERSUS containsKey()? {}", virtualMap.containsKey((OnDiskKey<K>) onDiskKey));
                if (false && value == null && !HAVE_LOGGED_MISSING_CONTENTS.get()) {
                    log.error(
                            "Missing key {} had hashCode={} and classId={}",
                            onDiskKey,
                            onDiskKey.hashCode(),
                            onDiskKey.getClassId());
                    try {
                        VirtualMapLike.from(requireNonNull(virtualMap))
                                .extractVirtualMapData(
                                        AdHocThreadManager.getStaticThreadManager(),
                                        entry -> {
                                            final var key = (OnDiskKey<AccountID>) entry.key();
                                            log.error(
                                                    "  {} (hashCode={}, classId={}) -> {}",
                                                    key,
                                                    key.hashCode(),
                                                    key.getClassId(),
                                                    entry.value());
                                            if (key.getKey().accountNumOrElse(0L) == 1L) {
                                                log.error("----------------");
                                                log.error(
                                                        "  Found missing key {}  (class {})"
                                                                + ", but equals lookup key (class {})?",
                                                        key,
                                                        key.getClass().getName(),
                                                        Objects.equals(onDiskKey, key),
                                                        onDiskKey.getClass().getName());
                                                log.error("----------------");
                                            }
                                        },
                                        1);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                    HAVE_LOGGED_MISSING_CONTENTS.set(true);
                }
            }
        }
    }

    @VisibleForTesting
    OnDiskReadableKVState(
            @NonNull final StateMetadata<K, V> md,
            @NonNull final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> virtualMap,
            @NonNull final Consumer<Runnable> runner) {
        super(md.stateDefinition().stateKey());
        this.md = md;
        this.virtualMap = Objects.requireNonNull(virtualMap);
        this.runner = runner;
    }

    /** {@inheritDoc} */
    @Override
    protected V readFromDataSource(@NonNull K key) {
        final var k = new OnDiskKey<>(md, key);
        final var v = virtualMap.get(k);
        final var value = v == null ? null : v.getValue();
        if (LOG_READS.get()) {
            log.info("Read {} from {} for key {}", value, getStateKey(), key);
        }
        // Log to transaction state log, what was read
        logMapGet(getStateKey(), key, value);
        return value;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        // Log to transaction state log, what was iterated
        logMapIterate(getStateKey(), virtualMap);

        final var itr = virtualMap.treeIterator();
        return new Iterator<>() {
            private K next = null;

            @Override
            public boolean hasNext() {
                if (next != null) return true;
                while (itr.hasNext()) {
                    final var merkleNode = itr.next();
                    if (merkleNode instanceof VirtualLeafNode<?, ?> leaf) {
                        final var k = leaf.getKey();
                        if (k instanceof OnDiskKey<?> onDiskKey) {
                            this.next = (K) onDiskKey.getKey();
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public K next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                final var k = next;
                next = null;
                return k;
            }
        };
    }

    /** {@inheritDoc} */
    @Override
    public long size() {
        final var size = virtualMap.size();
        // Log to transaction state log, size of map
        logMapGetSize(getStateKey(), size);
        return size;
    }

    @Override
    public void warm(@NonNull final K key) {
        final var k = new OnDiskKey<>(md, key);
        runner.accept(() -> virtualMap.warm(k));
    }
}
