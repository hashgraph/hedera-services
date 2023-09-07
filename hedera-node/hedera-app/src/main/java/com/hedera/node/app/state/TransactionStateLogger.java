package com.hedera.node.app.state;

import com.hedera.hapi.node.base.*;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.hedera.node.app.state.merkle.memory.InMemoryKey;
import com.hedera.node.app.state.merkle.memory.InMemoryValue;
import com.hedera.node.app.state.merkle.singleton.ValueLeaf;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.internal.merkle.VirtualLeafNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A logger for state changes. This records a separate log file for all input state read and output state changes from
 * each transaction. This makes it easy to trace what happened if an ISS happens.
 */
public final class TransactionStateLogger {
    private static final Logger logger = LogManager.getLogger(TransactionStateLogger.class);

    /**
     * Log the start of a round if it contains any non-system transactions.
     *
     * @param round The round to log
     */
    public static void logStartRound(final Round round) {
        if (logger.isInfoEnabled()) {
            AtomicBoolean isAllSystem = new AtomicBoolean(true);
            round.forEachEventTransaction((event, tx) -> {
                if (!tx.isSystem()) {
                    isAllSystem.set(false);
                }
            });
            if (!isAllSystem.get()) {
                logger.info("Starting round {} of {} events at {}", round.getRoundNum(), round.getEventCount(),
                        round.getConsensusTimestamp());
            }
        }
    }

    /**
     * Log the start of a event if it contains any non-system transactions.
     *
     * @param event The event to log
     */
    public static void logStartEvent(final ConsensusEvent event, final NodeInfo creator) {
        if (logger.isInfoEnabled()) {
            AtomicBoolean isAllSystem = new AtomicBoolean(true);
            event.forEachTransaction(tx -> {
                if (!tx.isSystem()) {
                    isAllSystem.set(false);
                }
            });
            if (!isAllSystem.get()) {
                logger.info("Starting event {} at {} from node {}", event.getConsensusOrder(), event.getConsensusTimestamp(),
                        creator.nodeId());
            }
        }
    }

    public static void logStartUserTransaction(@NonNull final ConsensusTransaction transaction,
                                               @Nullable final TransactionBody txBody,
                                               @NonNull final AccountID payer) {
        logger.info("Starting user transaction {} at platform time {} from payer 0.0.{}",
                txBody == null ? "null" : txBody.transactionID(),
                transaction.getConsensusTimestamp(),
                payer.accountNum());
    }

    public static void logStartUserTransactionPreHandleResultP2(@Nullable AccountID payer,
                                                                @Nullable Key payerKey,
                                                                @NonNull PreHandleResult.Status status,
                                                                @NonNull ResponseCodeEnum responseCode) {
        logger.debug("with preHandleResult: payer 0.0.{} with key {} with status {} with response code {}",
                payer == null ? "null" : payer.accountNum(),
                payerKey == null ? "null" : payerKey.toString(),
                status,
                responseCode);
    }
    public static void logStartUserTransactionPreHandleResultP3(@Nullable TransactionInfo txInfo,
                                                                @Nullable Set<Key> requiredKeys,
                                                                @Nullable Map<Key, SignatureVerificationFuture> verificationResults) {
        logger.trace("with preHandleResult: txInfo {} with requiredKeys {} with verificationResults {}",
                txInfo, requiredKeys, verificationResults);
    }

    /**
     * Record the end of a transaction, this can be a user, preceding or child transaction.
     *
     * @param txID The ID of the transaction
     * @param transactionRecord The record of the transaction execution
     */
    public static void logEndTransactionRecord(@NonNull final TransactionID txID,
                                             @NonNull final TransactionRecord transactionRecord) {
        logger.info("Ending transaction {} response code {} with record:\n{}",
                txID,
                transactionRecord.receipt() == null ? "null" : transactionRecord.receipt().status(),
                TransactionRecord.JSON.toJSON(transactionRecord));
    }

    // =========== Singleton Methods ==================================================================

    public static <T> void logSingletonRead(@NonNull final String label, @Nullable final ValueLeaf<T> value) {
        logger.debug("READ singleton {} value {}", label,
                value == null ? "null" : value.getValue());
    }
    public static void logSingletonWrite(@NonNull final String label, @Nullable final Object value) {
        logger.info("WRITTEN singleton {} value {}", label,
                value == null ? "null" : value.toString());
    }

    // =========== Queue Methods ======================================================================

    public static void logQueueAdd(@NonNull final String label, @Nullable final Object value) {
        logger.info("ADD to queue {} value {}", label,
                value == null ? "null" : value.toString());
    }

    public static void logQueueRemove(@NonNull final String label, @Nullable final Object value) {
        logger.info("REMOVE from queue {} value {}", label,
                value == null ? "null" : value.toString());
    }

    public static void logQueuePeek(@NonNull final String label, @Nullable final Object value) {
        logger.debug("PEEK on queue {} value {}", label,
                value == null ? "null" : value.toString());
    }

    public static <K> void logQueueIterate(@NonNull final String label, @NonNull final FCQueue<ValueLeaf<K>> queue) {

        if (logger.isDebugEnabled()) {
            if (queue.size() == 0) {
                logger.debug("ITERATE queue {} size 0 values:EMPTY", label);
                return;
            } else {
                logger.debug("ITERATE queue {} size {} values:\n{}", label, queue.size(),
                        queue.stream()
                                .map(leaf -> leaf == null ? "null" : leaf.toString())
                                .collect(Collectors.joining(",\n"))
                );
            }
        }
    }

    // =========== Map Methods =========================================================================

    public static <K, V> void logMapPut(@NonNull final String label, @NonNull final K key,
                                        @Nullable final V value) {
        logger.info("PUT into map {} key {} value {}", label, formatKey(key),
                value == null ? "null" : value.toString());
    }

    public static <K, V> void logMapRemove(@NonNull final String label, @NonNull final K key,
                                    @Nullable final InMemoryValue<K, V> value) {
        logger.info("REMOVE from map {} key {} removed value {}", label, formatKey(key),
                value == null ? "null" : value.toString());
    }

    public static <K, V> void logMapRemove(@NonNull final String label, @NonNull final K key,
                                    @Nullable final OnDiskValue<V> value) {
        logger.info("REMOVE from map {} key {} removed value {}", label, formatKey(key),
                value == null ? "null" : value.toString());
    }

    public static <K, V> void logMapGetSize(@NonNull final String label, final long size) {
        logger.debug("GET_SIZE on map {} size {}", label, size);
    }

    public static <K, V> void logMapGet(@NonNull final String label, @NonNull final K key,
                                        @Nullable final V value) {
        logger.debug("GET on map {} key {} value {}", label, formatKey(key),
                value == null ? "null" : value.toString());
    }

    public static <K, V> void logMapGetForModify(@NonNull final String label, @NonNull final K key,
                                        @Nullable final V value) {
        logger.debug("GET_FOR_MODIFY on map {} key {} value {}", label, formatKey(key),
                value == null ? "null" : value.toString());
    }

    public static <K> void logMapIterate(@NonNull final String label, @NonNull final Set<InMemoryKey<K>> keySet) {
        if (logger.isDebugEnabled()) {
            final long size = keySet.size();
            if (size == 0) {
                logger.debug("ITERATE map {} size 0 keys:EMPTY", label);
                return;
            } else {
                logger.debug("ITERATE map {} size {} keys:\n{}", label, size,
                        keySet.stream()
                                .map(InMemoryKey::key)
                                .map(TransactionStateLogger::formatKey)
                                .collect(Collectors.joining(",\n"))
                );
            }
        }
    }

    public static <K, V> void logMapIterate(@NonNull final String label, @NonNull final VirtualMap<OnDiskKey<K>, OnDiskValue<V>> virtualMap) {
        if (logger.isDebugEnabled()) {
            final var spliterator = Spliterators.spliterator(virtualMap.treeIterator(), virtualMap.size(),
                    Spliterator.SIZED & Spliterator.ORDERED);
            final long size = virtualMap.size();
            if (size == 0) {
                logger.debug("ITERATE map {} size 0 keys:EMPTY", label);
                return;
            } else {
                logger.debug("ITERATE map {} size {} keys:\n{}", label, size,
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
                                .collect(Collectors.joining(",\n"))
                );
            }
        }
    }

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
