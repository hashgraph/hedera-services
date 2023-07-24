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

package com.hedera.node.app.state.recordcache;

import static com.hedera.hapi.node.base.ResponseCodeEnum.UNKNOWN;
import static com.hedera.node.app.spi.HapiUtils.asTimestamp;
import static com.hedera.node.app.spi.HapiUtils.isBefore;
import static com.hedera.node.app.spi.HapiUtils.minus;
import static com.hedera.node.app.state.recordcache.RecordCacheService.NAME;
import static com.hedera.node.app.state.recordcache.RecordCacheService.TXN_RECORD_QUEUE;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.spi.state.ReadableQueueState;
import com.hedera.node.app.spi.state.WritableQueueState;
import com.hedera.node.app.state.DeduplicationCache;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * An implementation of {@link HederaRecordCache}
 *
 * <p>This implementation stores all records in a time-ordered queue of {@link TransactionRecord}s, where records are
 * ordered by <strong>consensus timestamp</strong> and kept for {@code maxTxnDuration} seconds before expiry. These
 * records are stored in state because they must be deterministic across all nodes in the network, and therefore must
 * also be part of reconnect, and state saving and loading. Use a queue provides superior performance to a map in this
 * particular case because all items removed from the queue are always removed from the head, and all items added to
 * the queue are always added to the end.
 *
 * <p>However, storing them in a queue of this nature does not provide efficient access to the data itself. For this
 * reason, in-memory data structures are used to provide efficient access to the data. These data structures are rebuilt
 * after reconnect or restart, and kept in sync with the data in state.
 *
 * <p>Mutation methods must be called during startup, reconnect, or on the "handle" thread. Getters may be called from
 * any thread.
 */
@Singleton
public class RecordCacheImpl implements HederaRecordCache {
    /**
     * An item stored in the {@link #histories} cache. These histories are stored in a map keyed by transaction ID.
     *
     * @param nodeIds The IDs of every node that submitted a transaction with the txId that came to consensus and was
     *                handled.
     * @param records Every {@link TransactionRecord} handled for every transaction that came to consensus with the txId
     */
    private record History(@NonNull Set<Long> nodeIds, @NonNull List<TransactionRecord> records) {
        History() {
            this(new HashSet<>(), new ArrayList<>());
        }
    }

    /** Gives access to the current working state. */
    private final WorkingStateAccessor workingStateAccessor;
    /** Used for looking up the max transaction duration window. */
    private final ConfigProvider configProvider;
    /** Used for answering queries about receipts to include those that have not been handled but are known */
    private final DeduplicationCache deduplicationCache;
    /**
     * A map of transaction IDs to the histories of all transactions that came to consensus with that ID. This data
     * structure is rebuilt during reconnect or restart.
     */
    private final Map<TransactionID, History> histories;
    /**
     * A secondary index that maps from an AccountID of the payer account to a set of transaction IDs that were
     * submitted by this payer. This is only needed for answering such queries. Ideally such queries would exist on the
     * mirror node instead.
     */
    private final Map<AccountID, Set<TransactionID>> payerToTransactionIndex = new ConcurrentHashMap<>();

    @Inject
    public RecordCacheImpl(
            @NonNull final DeduplicationCache deduplicationCache,
            @NonNull final WorkingStateAccessor workingStateAccessor,
            @NonNull final ConfigProvider configProvider) {
        this.deduplicationCache = requireNonNull(deduplicationCache);
        this.workingStateAccessor = requireNonNull(workingStateAccessor);
        this.configProvider = requireNonNull(configProvider);
        this.histories = new ConcurrentHashMap<>();

        rebuild();
    }

    /**
     * Rebuild the internal data structures based on the current working state. Called during startup and during
     * reconnect.
     */
    public void rebuild() {
        histories.clear();
        payerToTransactionIndex.clear();
        deduplicationCache.clear();

        final var queue = getReadableQueue();
        final var itr = queue.iterator();
        while (itr.hasNext()) {
            final var entry = itr.next();
            addToInMemoryCache(entry.nodeId(), entry.payerAccountIdOrThrow(), entry.transactionRecordOrThrow());
            deduplicationCache.add(entry.transactionRecordOrThrow().transactionIDOrThrow());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Implementation methods of HederaRecordCache
    // ---------------------------------------------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public void add(
            final long nodeId,
            @NonNull final AccountID payerAccountId,
            @NonNull final TransactionRecord transactionRecord,
            @NonNull final Instant consensusTimestamp) {
        requireNonNull(payerAccountId);
        requireNonNull(transactionRecord);
        requireNonNull(consensusTimestamp);
        addToInMemoryCache(nodeId, payerAccountId, transactionRecord);

        final var queue = getQueue();
        removeExpiredTransactions(queue, consensusTimestamp);
        queue.add(new TransactionRecordEntry(nodeId, payerAccountId, transactionRecord));
    }

    /**
     * Called during {@link #rebuild()} or {@link #add(long, AccountID, TransactionRecord, Instant)}, this method adds
     * the given {@link TransactionRecord} to the internal lookup data structures.
     *
     * @param nodeId The ID of the node that submitted the transaction.
     * @param transactionRecord The record to add.
     */
    private void addToInMemoryCache(
            final long nodeId,
            @NonNull final AccountID payerAccountId,
            @NonNull final TransactionRecord transactionRecord) {
        // Add to the main histories cache
        final var txId = transactionRecord.transactionIDOrThrow();
        final var cacheItem = histories.computeIfAbsent(txId, ignored -> new History());
        cacheItem.nodeIds().add(nodeId);
        cacheItem.records().add(transactionRecord);
        // Add to the payer to transaction index
        final var transactionIDs = payerToTransactionIndex.computeIfAbsent(payerAccountId, ignored -> new HashSet<>());
        transactionIDs.add(txId);
    }

    /**
     * Removes all expired {@link TransactionID}s from the cache.
     */
    private void removeExpiredTransactions(
            @NonNull final WritableQueueState<TransactionRecordEntry> queue,
            @NonNull final Instant consensusTimestamp) {
        // Compute the earliest valid start timestamp that is still within the max transaction duration window.
        final var now = asTimestamp(consensusTimestamp);
        final var config = configProvider.getConfiguration().getConfigData(HederaConfig.class);
        final var earliestValidState = minus(now, config.transactionMaxValidDuration());

        // Loop in order and expunge every entry where the timestamp is before the current time. Also remove from the
        // in memory data structures.
        final var itr = queue.iterator();
        while (itr.hasNext()) {
            final var entry = itr.next();
            final var rec = entry.transactionRecordOrThrow();
            final var txId = rec.transactionIDOrThrow();
            // If the timestamp is before the current time, then it has expired
            if (isBefore(txId.transactionValidStartOrThrow(), earliestValidState)) {
                // Remove from the histories
                itr.remove();
                // Remove from the payer to transaction index
                final var payerAccountId = txId.accountIDOrThrow(); // NOTE: Not accurate if the payer was the node
                final var transactionIDs =
                        payerToTransactionIndex.computeIfAbsent(payerAccountId, ignored -> new HashSet<>());
                transactionIDs.remove(txId);
                if (transactionIDs.isEmpty()) {
                    payerToTransactionIndex.remove(payerAccountId);
                }
            } else {
                return;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Implementation methods of RecordCache
    // ---------------------------------------------------------------------------------------------------------------

    @NonNull
    @Override
    public List<TransactionRecord> getRecords(@NonNull final TransactionID transactionID) {
        final var history = histories.get(transactionID);
        return history == null ? emptyList() : history.records();
    }

    @NonNull
    @Override
    public List<TransactionRecord> getRecords(@NonNull final AccountID accountID) {
        final var transactionIDs = payerToTransactionIndex.get(accountID);
        if (transactionIDs == null) {
            return emptyList();
        }

        // Note that at **most** LedgerConfig#recordsMaxQueryableByAccount() records will be available, even if the
        // given account has paid for more than this number of transactions in the last 180 seconds.
        var maxRemaining = configProvider
                .getConfiguration()
                .getConfigData(LedgerConfig.class)
                .recordsMaxQueryableByAccount();

        // While we still need to gather more records, collect them from the different histories.
        final var records = new ArrayList<TransactionRecord>(maxRemaining);
        for (final var transactionID : transactionIDs) {
            final var history = histories.get(transactionID);
            if (history != null) {
                final var recs = history.records();
                records.addAll(recs.size() > maxRemaining ? recs.subList(0, maxRemaining) : recs);
                maxRemaining -= recs.size();
                if (maxRemaining <= 0) break;
            }
        }

        return records;
    }

    @NonNull
    @Override
    public List<TransactionReceipt> getReceipts(@NonNull final TransactionID transactionID) {
        final var records = getRecords(transactionID);
        return records.isEmpty() && deduplicationCache.contains(transactionID)
                ? List.of(TransactionReceipt.newBuilder().status(UNKNOWN).build())
                : records.stream().map(TransactionRecord::receipt).toList();
    }

    /** Utility method that get the writable queue from the working state */
    private WritableQueueState<TransactionRecordEntry> getQueue() {
        final var hederaState = workingStateAccessor.getHederaState();
        if (hederaState == null) {
            throw new RuntimeException("HederaState is null. This can only happen very early during bootstrapping");
        }
        final var states = hederaState.createWritableStates(NAME);
        return states.getQueue(TXN_RECORD_QUEUE);
    }

    /** Utility method that get the readable queue from the working state */
    private ReadableQueueState<TransactionRecordEntry> getReadableQueue() {
        final var hederaState = workingStateAccessor.getHederaState();
        if (hederaState == null) {
            throw new RuntimeException("HederaState is null. This can only happen very early during bootstrapping");
        }
        final var states = hederaState.createReadableStates(NAME);
        return states.getQueue(TXN_RECORD_QUEUE);
    }
}
