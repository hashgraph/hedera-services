/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.state.merkle.logging;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TopicID;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.state.merkle.disk.OnDiskKey;
import com.swirlds.state.merkle.disk.OnDiskValue;
import com.swirlds.state.merkle.memory.InMemoryKey;
import com.swirlds.state.merkle.memory.InMemoryValue;
import com.swirlds.state.merkle.singleton.ValueLeaf;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.internal.merkle.VirtualLeafNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This utility class provides convenient methods for logging state operations for different types of state types.
 */
public class StateLogger {
    /** The logger we are using for the State log */
    private static final Logger logger = LogManager.getLogger(StateLogger.class);

    /**
     * The name of the thread that handles transactions. For the sake of the app, to allow logging.
     */
    private static final String TRANSACTION_HANDLING_THREAD_NAME = "<scheduler TransactionHandler>";

    /**
     * Log the read of a singleton.
     *
     * @param label The label of the singleton
     * @param value The value of the singleton
     * @param <T> The type of the singleton
     */
    public static <T> void logSingletonRead(@NonNull final String label, @Nullable final ValueLeaf<T> value) {
        if (logger.isDebugEnabled() && Thread.currentThread().getName().equals(TRANSACTION_HANDLING_THREAD_NAME)) {
            logger.debug("      READ singleton {} value {}", label, value == null ? "null" : value.getValue());
        }
    }

    /**
     * Log when the value of a singleton is written.
     *
     * @param label The label of the singleton
     * @param value The value of the singleton
     */
    public static void logSingletonWrite(@NonNull final String label, @Nullable final Object value) {
        if (logger.isDebugEnabled() && Thread.currentThread().getName().equals(TRANSACTION_HANDLING_THREAD_NAME)) {
            logger.debug("      WRITTEN singleton {} value {}", label, value == null ? "null" : value.toString());
        }
    }

    /**
     * Log when a value is added to a queue.
     *
     * @param label The label of the queue
     * @param value The value added to the queue
     */
    public static void logQueueAdd(@NonNull final String label, @Nullable final Object value) {
        if (logger.isDebugEnabled() && Thread.currentThread().getName().equals(TRANSACTION_HANDLING_THREAD_NAME)) {
            logger.debug("      ADD to queue {} value {}", label, value == null ? "null" : value.toString());
        }
    }

    /**
     * Log when a value is removed from a queue.
     *
     * @param label The label of the queue
     * @param value The value removed from the queue
     */
    public static void logQueueRemove(@NonNull final String label, @Nullable final Object value) {
        if (logger.isDebugEnabled() && Thread.currentThread().getName().equals(TRANSACTION_HANDLING_THREAD_NAME)) {
            logger.debug("      REMOVE from queue {} value {}", label, value == null ? "null" : value.toString());
        }
    }

    /**
     * Log when queue value is peeked.
     *
     * @param label The label of the queue
     * @param value The value peeked from the queue
     */
    public static void logQueuePeek(@NonNull final String label, @Nullable final Object value) {
        if (logger.isDebugEnabled() && Thread.currentThread().getName().equals(TRANSACTION_HANDLING_THREAD_NAME)) {
            logger.debug("      PEEK on queue {} value {}", label, value == null ? "null" : value.toString());
        }
    }

    /**
     * Log the iteration over a queue.
     *
     * @param label The label of the queue
     * @param queue The queue that was iterated
     * @param <K> The type of the queue values
     */
    public static <K> void logQueueIterate(@NonNull final String label, @NonNull final FCQueue<ValueLeaf<K>> queue) {
        if (logger.isDebugEnabled() && Thread.currentThread().getName().equals(TRANSACTION_HANDLING_THREAD_NAME)) {
            if (queue.isEmpty()) {
                logger.debug("      ITERATE queue {} size 0 values:EMPTY", label);
            } else {
                logger.debug(
                        "      ITERATE queue {} size {} values:\n{}",
                        label,
                        queue.size(),
                        queue.stream()
                                .map(leaf -> leaf == null ? "null" : leaf.toString())
                                .collect(Collectors.joining(",\n")));
            }
        }
    }

    /**
     * Log the put of an entry in to a map.
     *
     * @param label The label of the map
     * @param key The key added to the map
     * @param value The value added to the map
     * @param <K> The type of the key
     * @param <V> The type of the value
     */
    public static <K, V> void logMapPut(@NonNull final String label, @NonNull final K key, @Nullable final V value) {
        if (logger.isDebugEnabled() && Thread.currentThread().getName().equals(TRANSACTION_HANDLING_THREAD_NAME)) {
            logger.debug(
                    "      PUT into map {} key {} value {}",
                    label,
                    formatKey(key),
                    value == null ? "null" : value.toString());
        }
    }

    /**
     * Log the removal of an entry from a map.
     *
     * @param label The label of the map
     * @param key The key removed to the map
     * @param value The value removed to the map
     * @param <K> The type of the key
     * @param <V> The type of the value
     */
    public static <K, V> void logMapRemove(
            @NonNull final String label, @NonNull final K key, @Nullable final InMemoryValue<K, V> value) {
        if (logger.isDebugEnabled() && Thread.currentThread().getName().equals(TRANSACTION_HANDLING_THREAD_NAME)) {
            logger.debug(
                    "      REMOVE from map {} key {} removed value {}",
                    label,
                    formatKey(key),
                    value == null ? "null" : value.toString());
        }
    }

    /**
     * Log the removal of an entry from a map.
     *
     * @param label The label of the map
     * @param key The key removed to the map
     * @param value The value removed to the map
     * @param <K> The type of the key
     * @param <V> The type of the value
     */
    public static <K, V> void logMapRemove(
            @NonNull final String label, @NonNull final K key, @Nullable final OnDiskValue<V> value) {
        if (logger.isDebugEnabled() && Thread.currentThread().getName().equals(TRANSACTION_HANDLING_THREAD_NAME)) {
            logger.debug(
                    "      REMOVE from map {} key {} removed value {}",
                    label,
                    formatKey(key),
                    value == null ? "null" : value.toString());
        }
    }

    /**
     * Log the fetching of the size of a map.
     *
     * @param label The label of the map
     * @param size The size of the map
     */
    public static void logMapGetSize(@NonNull final String label, final long size) {
        if (logger.isDebugEnabled() && Thread.currentThread().getName().equals(TRANSACTION_HANDLING_THREAD_NAME)) {
            logger.debug("      GET_SIZE on map {} size {}", label, size);
        }
    }

    /**
     * Log the get of an entry from a map.
     *
     * @param label The label of the map
     * @param key The key fetched to the map
     * @param value The value fetched to the map
     * @param <K> The type of the key
     * @param <V> The type of the value
     */
    public static <K, V> void logMapGet(@NonNull final String label, @NonNull final K key, @Nullable final V value) {
        if (logger.isDebugEnabled() && Thread.currentThread().getName().equals(TRANSACTION_HANDLING_THREAD_NAME)) {
            logger.debug(
                    "      GET on map {} key {} value {}",
                    label,
                    formatKey(key),
                    value == null ? "null" : value.toString());
        }
    }

    /**
     * Log the get of an entry from a map for modification.
     *
     * @param label The label of the map
     * @param key The key fetched to the map
     * @param value The value fetched to the map
     * @param <K> The type of the key
     * @param <V> The type of the value
     */
    public static <K, V> void logMapGetForModify(
            @NonNull final String label, @NonNull final K key, @Nullable final V value) {
        if (logger.isDebugEnabled() && Thread.currentThread().getName().equals(TRANSACTION_HANDLING_THREAD_NAME)) {
            logger.debug(
                    "      GET_FOR_MODIFY on map {} key {} value {}",
                    label,
                    formatKey(key),
                    value == null ? "null" : value.toString());
        }
    }

    /**
     * Log the iteration of keys of a map.
     *
     * @param label The label of the map
     * @param keySet The set of keys of the map
     * @param <K> The type of the key
     */
    public static <K> void logMapIterate(@NonNull final String label, @NonNull final Set<InMemoryKey<K>> keySet) {
        if (logger.isDebugEnabled() && Thread.currentThread().getName().equals(TRANSACTION_HANDLING_THREAD_NAME)) {
            final long size = keySet.size();
            if (size == 0) {
                logger.debug("      ITERATE map {} size 0 keys:EMPTY", label);
            } else {
                logger.debug(
                        "      ITERATE map {} size {} keys:\n{}",
                        label,
                        size,
                        keySet.stream()
                                .map(InMemoryKey::key)
                                .map(StateLogger::formatKey)
                                .collect(Collectors.joining(",\n")));
            }
        }
    }

    /**
     * Log the iteration of values of a map.
     *
     * @param label The label of the map
     * @param virtualMap The map that was iterated
     * @param <K> The type of the key
     * @param <V> The type of the value
     */
    public static <K, V> void logMapIterate(
            @NonNull final String label, @NonNull final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> virtualMap) {
        if (logger.isDebugEnabled()) {
            final var spliterator = Spliterators.spliterator(
                    virtualMap.treeIterator(), virtualMap.size(), Spliterator.SIZED & Spliterator.ORDERED);
            final long size = virtualMap.size();
            if (size == 0) {
                logger.debug("      ITERATE map {} size 0 keys:EMPTY", label);
            } else {
                logger.debug(
                        "      ITERATE map {} size {} keys:\n{}",
                        label,
                        size,
                        StreamSupport.stream(spliterator, false)
                                .map(merkleNode -> {
                                    if (merkleNode instanceof VirtualLeafNode<?, ?> leaf) {
                                        final var k = leaf.getKey();
                                        if (k instanceof OnDiskKey<?> onDiskKey) {
                                            return onDiskKey.getKey().toString();
                                        }
                                    }
                                    return "Unknown_Type";
                                })
                                .collect(Collectors.joining(",\n")));
            }
        }
    }

    /**
     * Format entity id keys in form 0.0.123 rather than default toString() long form.
     *
     * @param key The key to format
     * @return The formatted key
     * @param <K> The type of the key
     */
    public static <K> String formatKey(@Nullable final K key) {
        if (key == null) {
            return "null";
        } else if (key instanceof AccountID accountID) {
            return accountID.shardNum() + "." + accountID.realmNum() + '.' + accountID.accountNum();
        } else if (key instanceof FileID fileID) {
            return fileID.shardNum() + "." + fileID.realmNum() + '.' + fileID.fileNum();
        } else if (key instanceof TokenID tokenID) {
            return tokenID.shardNum() + "." + tokenID.realmNum() + '.' + tokenID.tokenNum();
        } else if (key instanceof TopicID topicID) {
            return topicID.shardNum() + "." + topicID.realmNum() + '.' + topicID.topicNum();
        } else if (key instanceof ScheduleID scheduleID) {
            return scheduleID.shardNum() + "." + scheduleID.realmNum() + '.' + scheduleID.scheduleNum();
        }
        return key.toString();
    }
}
