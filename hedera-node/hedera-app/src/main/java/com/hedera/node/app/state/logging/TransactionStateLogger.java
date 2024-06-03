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

package com.hedera.node.app.state.logging;

import static com.swirlds.platform.eventhandling.TransactionHandler.TRANSACTION_HANDLING_THREAD_NAME;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.platform.state.merkle.disk.OnDiskKey;
import com.swirlds.platform.state.merkle.disk.OnDiskValue;
import com.swirlds.platform.state.merkle.memory.InMemoryKey;
import com.swirlds.platform.state.merkle.memory.InMemoryValue;
import com.swirlds.platform.state.merkle.singleton.ValueLeaf;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.internal.merkle.VirtualLeafNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A logger for state changes. This records a separate log file for all input state read and output state changes from
 * each transaction. This makes it easy to trace what happened if an ISS happens.
 *
 * <p>All methods are called on the handle transaction thread or check they are on handle transaction thread before
 * doing any work. Therefore state does not need to be thread safe.</p>
 */
public final class TransactionStateLogger {
    /** The logger we are using for Transaction State log */
    private static final Logger logger = LogManager.getLogger(TransactionStateLogger.class);

    /**
     * Log the start of a round if it contains any non-system transactions.
     *
     * @param round The round to log
     */
    public static void logStartRound(final Round round) {
        if (logger.isDebugEnabled()) {
            AtomicBoolean isAllSystem = new AtomicBoolean(true);
            round.forEachEventTransaction((event, tx) -> {
                if (!tx.isSystem()) {
                    isAllSystem.set(false);
                }
            });
            if (!isAllSystem.get()) {
                logger.debug(
                        "Starting round {} of {} events at {}",
                        round.getRoundNum(),
                        round.getEventCount(),
                        round.getConsensusTimestamp());
            }
        }
    }

    /**
     * Log the start of an event if it contains any non-system transactions.
     *
     * @param event The event to log
     */
    public static void logStartEvent(final ConsensusEvent event, final NodeInfo creator) {
        if (logger.isDebugEnabled()) {
            AtomicBoolean isAllSystem = new AtomicBoolean(true);
            event.forEachTransaction(tx -> {
                if (!tx.isSystem()) {
                    isAllSystem.set(false);
                }
            });
            if (!isAllSystem.get()) {
                logger.debug(
                        "  Starting event {} at {} from node {}",
                        event.getConsensusOrder(),
                        event.getConsensusTimestamp(),
                        creator.nodeId());
            }
        }
    }

    /**
     * Log the start of a transaction.
     *
     * @param transaction The transaction to log
     * @param txBody The transaction body
     * @param payer The payer
     */
    public static void logStartUserTransaction(
            @NonNull final ConsensusTransaction transaction,
            @Nullable final TransactionBody txBody,
            @NonNull final AccountID payer) {
        if (logger.isDebugEnabled()) {
            logger.debug(
                    "    Starting user transaction {} at platform time {} from payer 0.0.{}",
                    txBody == null ? "null" : formatTransactionId(txBody.transactionID()),
                    transaction.getConsensusTimestamp(),
                    payer.accountNum());
        }
    }

    /**
     * Log the start of a user transaction pre-handle result.
     *
     * @param preHandleResult The pre-handle result
     */
    public static void logStartUserTransactionPreHandleResultP2(@NonNull PreHandleResult preHandleResult) {
        if (logger.isDebugEnabled()) {
            final var payer = preHandleResult.payer();
            final var payerKey = preHandleResult.payerKey();
            logger.debug(
                    "      with preHandleResult: payer 0.0.{} with key {} with status {} with response code {}",
                    payer == null ? "null" : payer.accountNum(),
                    payerKey == null ? "null" : payerKey.toString(),
                    preHandleResult.status(),
                    preHandleResult.responseCode());
        }
    }

    /**
     * Log the start of a user transaction pre-handle result.
     *
     * @param preHandleResult The pre-handle result
     */
    public static void logStartUserTransactionPreHandleResultP3(@NonNull PreHandleResult preHandleResult) {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    "      with preHandleResult: txInfo {} with requiredKeys {} with verificationResults {}",
                    preHandleResult.txInfo(),
                    preHandleResult.requiredKeys(),
                    preHandleResult.verificationResults());
        }
    }

    /**
     * Record the end of a transaction, this can be a user, preceding or child transaction.
     *
     * @param txID The ID of the transaction
     * @param transactionRecord The record of the transaction execution
     */
    public static void logEndTransactionRecord(
            @NonNull final TransactionID txID, @NonNull final TransactionRecord transactionRecord) {
        if (logger.isDebugEnabled()) {
            final StringBuilder sb = new StringBuilder();
            sb.append("    Ending transaction ").append(formatTransactionId(txID));
            if (transactionRecord.receipt() != null) {
                sb.append("\n    responseCode: ")
                        .append(transactionRecord.receipt().status());
                sb.append("\n    ").append(transactionRecord.receipt());
                sb.append("\n    ").append(transactionRecord.transferList());
            }
            if (transactionRecord.transferList() != null) {
                sb.append("\n    ").append(transactionRecord.transferList());
            }
            if (transactionRecord.tokenTransferLists() != null
                    && !transactionRecord.tokenTransferLists().isEmpty()) {
                sb.append("\n    ").append(transactionRecord.tokenTransferLists());
            }
            if (transactionRecord.tokenTransferLists() != null
                    && !transactionRecord.tokenTransferLists().isEmpty()) {
                sb.append("\n    ").append(transactionRecord.tokenTransferLists());
            }
            if (transactionRecord.paidStakingRewards() != null
                    && !transactionRecord.paidStakingRewards().isEmpty()) {
                sb.append("\n    ").append(transactionRecord.paidStakingRewards());
            }
            if (transactionRecord.assessedCustomFees() != null
                    && !transactionRecord.assessedCustomFees().isEmpty()) {
                sb.append("\n    ").append(transactionRecord.assessedCustomFees());
            }
            sb.append("\n    transactionFee: ")
                    .append(transactionRecord.transactionFee())
                    .append(" transactionHash: ")
                    .append(transactionRecord.transactionHash() == null ? "null" : transactionRecord.transactionHash());
            sb.append("\n    scheduleRef: ")
                    .append(transactionRecord.scheduleRef() == null ? "null" : transactionRecord.scheduleRef())
                    .append(" parentConsensusTimestamp: ")
                    .append(
                            transactionRecord.parentConsensusTimestamp() == null
                                    ? "null"
                                    : transactionRecord.parentConsensusTimestamp());
            sb.append("\n    alias: ")
                    .append(transactionRecord.alias() == null ? "null" : transactionRecord.alias())
                    .append(" ethereumHash: ")
                    .append(transactionRecord.ethereumHash() == null ? "null" : transactionRecord.ethereumHash());
            sb.append("\n    evmAddress: ")
                    .append(transactionRecord.evmAddress() == null ? "null" : transactionRecord.evmAddress())
                    .append(" entropy: ")
                    .append(transactionRecord.entropy() == null ? "null" : transactionRecord.entropy());
            logger.debug(sb);
        }
    }

    // =========== Singleton Methods ==================================================================

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

    // =========== Queue Methods ======================================================================

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
            if (queue.size() == 0) {
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

    // =========== Map Methods =========================================================================

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
                                .map(TransactionStateLogger::formatKey)
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

    // =========== Formatting Methods =========================================================================

    /**
     * Format entity id keys in form 0.0.123 rather than default toString() long form.
     *
     * @param key The key to format
     * @return The formatted key
     * @param <K> The type of the key
     */
    private static <K> String formatKey(@Nullable final K key) {
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

    /**
     * Nicer formatting for transaction IDs.
     *
     * @param transactionID The transaction ID to format
     * @return The formatted transaction ID
     */
    private static String formatTransactionId(@Nullable final TransactionID transactionID) {
        if (transactionID == null || transactionID.transactionValidStart() == null) {
            return "null";
        }
        return "TransactionID[transactionValidStart="
                + Instant.ofEpochSecond(
                        transactionID.transactionValidStart().seconds(),
                        transactionID.transactionValidStart().nanos())
                + ", accountID="
                + formatKey(transactionID.accountID()) + ", scheduled="
                + transactionID.scheduled() + ", nonce=" + transactionID.nonce() + "]";
    }
}
