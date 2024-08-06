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

package com.hedera.node.app.state.recordcache;

import static com.hedera.hapi.util.HapiUtils.TIMESTAMP_COMPARATOR;
import static com.hedera.hapi.util.HapiUtils.isBefore;
import static com.hedera.hapi.util.HapiUtils.minus;
import static com.hedera.node.app.state.recordcache.RecordCacheService.NAME;
import static com.hedera.node.app.state.recordcache.schemas.V0540RecordCacheSchema.TXN_RECEIPT_QUEUE;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.recordcache.TransactionReceiptEntries;
import com.hedera.hapi.node.state.recordcache.TransactionReceiptEntry;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.spi.validation.TruePredicate;
import com.hedera.node.app.state.DeduplicationCache;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.swirlds.state.State;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.WritableQueueState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of {@link HederaRecordCache}
 *
 * <p>This implementation stores all records in a time-ordered queue of {@link TransactionRecord}s, where records are
 * ordered by <strong>consensus timestamp</strong> and kept for {@code maxTxnDuration} seconds before expiry. These
 * records are stored in state because they must be deterministic across all nodes in the network, and therefore must
 * also be part of reconnect, and state saving and loading. A queue provides superior performance to a map in this
 * particular case because all items removed from the queue are always removed from the head, and all items added to
 * the queue are always added to the end.
 *
 * <p>However, storing them in a queue of this nature does not provide efficient access to the data itself. For this
 * reason, in-memory data structures are used to provide efficient access to the data. These data structures are rebuilt
 * after reconnect or restart, and kept in sync with the data in state.
 *
 * <p>Some transactions produce additional "child" transactions or "preceding" transactions. For example, when an
 * account is to be auto-created due to a crypto transfer to an unknown alias, we create a preceding transaction. Or,
 * a smart contract call may create child transactions when working with HTS. In all cases, each of these transactions
 * are treated as separate transactions, in that they are individually added to the queue, in the appropriate order, and
 * individually added to the history. However, child transactions are always included in the history of the user
 * transaction that triggered them, because they need to be available to the user when querying for all records for a
 * given transaction ID, or for a given payer, while preceding trnasactions are treated as their own top level
 * transactions.
 *
 * <p>Mutation methods must be called during startup, reconnect, or on the "handle" thread. Getters may be called from
 * any thread.
 */
@Singleton
public class RecordCacheImpl implements HederaRecordCache {
    private static final Logger logger = LogManager.getLogger(RecordCacheImpl.class);
    /**
     * Comparator for sorting {@link TransactionReceiptEntry} by the transaction valid start timestamp.
     */
    public static final Comparator<TransactionReceiptEntry> TRANSACTION_VALID_START_COMPARATOR = Comparator.comparing(
            e -> e.transactionIdOrElse(TransactionID.DEFAULT).transactionValidStart(), TIMESTAMP_COMPARATOR);

    /**
     * This empty History is returned whenever a transaction is known to the deduplication cache, but not yet
     * added to this cache.
     */
    private static final History EMPTY_HISTORY = new History();

    /** Gives access to the current working state. */
    private final WorkingStateAccessor workingStateAccessor;
    /** Used for looking up the max valid duration window for a transaction. This must be looked up dynamically. */
    private final ConfigProvider configProvider;
    /**
     * Every record added to the cache has a unique transaction ID. Each of these must be recorded in the dedupe cache
     * to help avoid duplicate transactions being ingested. And, when a history is looked up, if the transaction ID is
     * known to the deduplication cache, but unknown to this record cache, then we return an empty history rather than
     * null.
     */
    private final DeduplicationCache deduplicationCache;
    /**
     * A map of transaction IDs to the histories of all transactions that came to
     * consensus with that ID, or their child
     * transactions. This data structure is rebuilt during reconnect or restart. Using a non-deterministic, map is
     * perfectly acceptable, as the order of these histories is not important.
     */
    private final Map<TransactionID, History> histories;
    /**
     * A secondary index that maps from the AccountID of the payer account to a set of transaction IDs that were
     * submitted by this payer. This is only needed for answering queries. Ideally such queries would exist on the
     * mirror node instead. The answer to this query will include child records that were created as a consequence
     * of the original user transaction, but not any preceding records triggered by it.
     */
    private final Map<AccountID, Set<TransactionID>> payerToTransactionIndex = new ConcurrentHashMap<>();

    private final List<TransactionReceiptEntry> transactionReceipts = new ArrayList<>();

    /**
     * Called once during startup to create this singleton. Rebuilds the in-memory data structures based on the current
     * working state at the moment of startup. The size of these data structures is fixed based on the length of time
     * the node was configured to keep records for. In other words, the amount of time it takes to rebuild this
     * data structure is not dependent on the size of state, but rather, the number of transactions that had occurred
     * within a 3-minute window prior to the node stopping.
     *
     * @param deduplicationCache   A cache containing known {@link TransactionID}s, used for deduplication
     * @param workingStateAccessor Gives access to the current working state, needed at startup, but also any time
     *                             records must be saved in state or read from state.
     * @param configProvider       Used for looking up the max valid duration window for a transaction dynamically
     */
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
     * reconnect. The amount of time it takes to rebuild this data structure is not dependent on the size of state, but
     * rather, the number of transactions in the queue (which is capped by configuration at 3 minutes by default).
     */
    public void rebuild() {
        histories.clear();
        payerToTransactionIndex.clear();
        // FUTURE: It doesn't hurt to clear the dedupe cache here, but is also probably not the best place to do it. The
        // system should clear the dedupe cache directly and not indirectly through this call.
        deduplicationCache.clear();

        final var queue = getReadableQueue(workingStateAccessor);
        final var itr = queue.iterator();
        while (itr.hasNext()) {
            final var entry = itr.next();
            for (final var e : entry.entries()) {
                final var rec = asTxnRecord(e);
                addToInMemoryCache(e.nodeId(), e.transactionIdOrThrow().accountIDOrThrow(), rec);
                deduplicationCache.add(e.transactionIdOrThrow());
            }
        }
    }

    private static TransactionRecord asTxnRecord(final TransactionReceiptEntry receipt) {
        return TransactionRecord.newBuilder()
                .receipt(
                        TransactionReceipt.newBuilder().status(receipt.status()).build())
                .transactionID(receipt.transactionId())
                .build();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Implementation methods of HederaRecordCache
    // ---------------------------------------------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public void add(
            final long nodeId,
            @NonNull final AccountID payerAccountId,
            @NonNull final List<SingleTransactionRecord> transactionRecords) {
        requireNonNull(payerAccountId);
        requireNonNull(transactionRecords);

        // This really shouldn't ever happen. If it does, we'll log a warning and bail.
        if (transactionRecords.isEmpty()) {
            logger.warn("Received an empty list of transaction records. This should never happen");
            return;
        }

        // For each transaction, in order, add to the in-memory data structures.
        for (final var singleTransactionRecord : transactionRecords) {
            final var rec = singleTransactionRecord.transactionRecord();
            addToInMemoryCache(nodeId, payerAccountId, rec);
            transactionReceipts.add(new TransactionReceiptEntry(
                    nodeId,
                    rec.transactionIDOrThrow(),
                    requireNonNull(rec.receipt()).status()));
        }
    }

    @Override
    public void resetRoundReceipts() {
        transactionReceipts.clear();
    }

    @Override
    public void commitAndPurgeIfAny(final State state, final Timestamp blockTimestamp) {
        // Looks at all the receipts in the queue and if the youngest entry's transaction valid start is before 120secs
        // of current block time, purges those from the queue.
        final var states = state.getWritableStates(RecordCacheService.NAME);
        final var queue = states.<TransactionReceiptEntries>getQueue(TXN_RECEIPT_QUEUE);
        removeExpiredReceipts(queue, blockTimestamp);

        queue.add(new TransactionReceiptEntries(transactionReceipts));

        if (states instanceof CommittableWritableStates committable) {
            committable.commit();
        }
    }

    @NonNull
    @Override
    public DuplicateCheckResult hasDuplicate(@NonNull TransactionID transactionID, long nodeId) {
        final var history = histories.get(transactionID);
        // If there is no history for this transaction id; or all its history consists of
        // unclassifiable records, return that it is effectively a unique id
        if (history == null || history.nodeIds().isEmpty()) {
            return DuplicateCheckResult.NO_DUPLICATE;
        }
        return history.nodeIds().contains(nodeId) ? DuplicateCheckResult.SAME_NODE : DuplicateCheckResult.OTHER_NODE;
    }

    /**
     * Called during {@link #rebuild()} or {@link #add(long, AccountID, List)}, this method adds the given
     * {@link TransactionRecord} to the internal lookup data structures.
     *
     * @param nodeId The ID of the node that submitted the transaction.
     * @param payerAccountId The {@link AccountID} of the payer of the transaction, so we can look up transactions by
     *                      payer later, if needed.
     * @param transactionRecord The record to add.
     */
    private void addToInMemoryCache(
            final long nodeId,
            @NonNull final AccountID payerAccountId,
            @NonNull final TransactionRecord transactionRecord) {
        final var txId = transactionRecord.transactionIDOrThrow();
        // We need the hasParentConsensusTimestamp() check to detect triggered transactions that
        // children of a ScheduleSign or ScheduleCreate (nonces were introduced after scheduled
        // transactions, so these children still have nonce=0)
        final var isChildTx = transactionRecord.hasParentConsensusTimestamp() || txId.nonce() > 0;
        final var userTxId = isChildTx ? txId.copyBuilder().nonce(0).build() : txId;

        // Get or create the history for this transaction ID.
        // One interesting tidbit -- at genesis, the records will piggyback on the first transaction, so whatever node
        // sent the first transaction will get "credit" for all the genesis records. But it will be deterministic, and
        // doesn't actually matter.
        final var history = histories.computeIfAbsent(userTxId, ignored -> new History());
        final var status = transactionRecord.receiptOrThrow().status();
        // If the status indicates a due diligence failure, we don't use the result in duplicate classification
        if (!DUE_DILIGENCE_FAILURES.contains(status)) {
            history.nodeIds().add(nodeId);
        }

        // Either we add this tx to the main records list if it is a user/preceding transaction, or to the child
        // transactions list of its parent.  Note that scheduled transactions are always child transactions, but
        // never produce child *records*; instead, the scheduled transaction record is treated as
        // a user transaction record.  The map key remains the current user transaction ID, however.
        final var listToAddTo = (isChildTx && !txId.scheduled()) ? history.childRecords() : history.records();
        listToAddTo.add(transactionRecord);

        // Add to the payer-to-transaction index
        final var transactionIDs = payerToTransactionIndex.computeIfAbsent(payerAccountId, ignored -> new HashSet<>());
        transactionIDs.add(txId);
    }

    /**
     * Removes all expired {@link TransactionID}s from the cache.
     */
    private void removeExpiredReceipts(
            @NonNull final WritableQueueState<TransactionReceiptEntries> queue,
            @NonNull final Timestamp consensusTimestamp) {
        // Compute the earliest valid start timestamp that is still within the max transaction duration window.
        final var config = configProvider.getConfiguration().getConfigData(HederaConfig.class);
        final var earliestValidStart = minus(consensusTimestamp, config.transactionMaxValidDuration());
        // Loop in order and expunge the entry if the youngest TransactionReceiptEntry is expired
        do {
            final var receiptsList = queue.peek();
            if (receiptsList != null) {
                if (receiptsList.entries().isEmpty()) {
                    continue;
                }
                final var youngestEntryStart = receiptsList.entries().stream()
                        .filter(TransactionReceiptEntry::hasTransactionId)
                        .max(TRANSACTION_VALID_START_COMPARATOR)
                        .get();
                final var txId = youngestEntryStart.transactionId();
                // If the valid start time is before the earliest valid start, then it has expired
                if (isBefore(txId.transactionValidStart(), earliestValidStart)) {
                    // Remove from the histories.  Note that all transactions are added to this map
                    // keyed to the "user transaction" ID, so removing the entry here removes both
                    // "parent" and "child" transaction records associated with that ID.
                    histories.remove(txId);
                    // remove from queue as well.  The queue only permits removing the current "HEAD",
                    // but that should always be correct here.
                    queue.removeIf(TruePredicate.INSTANCE);
                    // Remove from the payer to transaction index
                    final var payerAccountId = txId.accountIDOrThrow(); // NOTE: Not accurate if the payer was the node
                    final var transactionIDs =
                            payerToTransactionIndex.computeIfAbsent(payerAccountId, ignored -> new HashSet<>());
                    transactionIDs.remove(txId);
                    if (transactionIDs.isEmpty()) {
                        payerToTransactionIndex.remove(payerAccountId);
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
        } while (true);
    }
    // ---------------------------------------------------------------------------------------------------------------
    // Implementation methods of RecordCache
    // ---------------------------------------------------------------------------------------------------------------

    @Nullable
    @Override
    public History getHistory(@NonNull TransactionID transactionID) {
        final var history = histories.get(transactionID);
        return history != null ? history : (deduplicationCache.contains(transactionID) ? EMPTY_HISTORY : null);
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
        // Because the set of transaction IDs could be concurrently modified by
        // the handle thread, wrap this in a try-catch block to deal with a CME
        // and return whatever we are able to gather. (I.e. this is a best-effort
        // query, and not a critical path; unused in production environments)
        try {
            for (final var transactionID : transactionIDs) {
                final var history = histories.get(transactionID);
                if (history != null) {
                    final var recs = history.orderedRecords();
                    records.addAll(recs.size() > maxRemaining ? recs.subList(0, maxRemaining) : recs);
                    maxRemaining -= recs.size();
                    if (maxRemaining <= 0) break;
                }
            }
        } catch (ConcurrentModificationException ignore) {
            // Ignore the exception and return what we found; this query is unused in production environments
        }

        records.sort((a, b) -> TIMESTAMP_COMPARATOR.compare(
                a.consensusTimestampOrElse(Timestamp.DEFAULT), b.consensusTimestampOrElse(Timestamp.DEFAULT)));

        return records;
    }

    /** Utility method that get the readable queue from the working state */
    private ReadableQueueState<TransactionReceiptEntries> getReadableQueue(
            final WorkingStateAccessor workingStateAccessor) {
        final var states = requireNonNull(workingStateAccessor.getState()).getReadableStates(NAME);
        return states.getQueue(TXN_RECEIPT_QUEUE);
    }
}
