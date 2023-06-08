/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.expiry;

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.records.TxnIdRecentHistory;
import com.hedera.node.app.service.mono.state.migration.RecordsStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.StorageStrategy;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.fcqueue.FCQueue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages the purging of expired records and their associated transaction histories from state and
 * auxiliary data structures. Behaves differently depending on the storage strategy in use.
 */
@Singleton
public class ExpiryManager {
    private static final Logger log = LogManager.getLogger(ExpiryManager.class);

    private final Map<TransactionID, TxnIdRecentHistory> txnHistories;
    private final Supplier<RecordsStorageAdapter> records;
    // Lazy-initialized after the storage strategy is known, post SwirldState.init()
    private StorageStrategy strategyInUse;
    // Needed if strategyInUse != IN_SINGLE_FCQ so we can do deterministic expiration
    private final MonotonicFullQueueExpiries<Long> payerRecordExpiries = new MonotonicFullQueueExpiries<>();

    @Inject
    public ExpiryManager(
            final Map<TransactionID, TxnIdRecentHistory> txnHistories, final Supplier<RecordsStorageAdapter> records) {
        this.records = records;
        this.txnHistories = txnHistories;
    }

    /**
     * Purges any references to expired entities (at this time, records or schedules).
     *
     * @param now the consensus second
     */
    public void purge(final long now) {
        if (storageStrategy() == StorageStrategy.IN_SINGLE_FCQ) {
            purgeExpiredRecordsFromConsolidatedStorageAt(now);
        } else {
            purgeExpiredRecordsFromPayerScopedStorageAt(now);
        }
    }

    /**
     * When payer records are in state (true by default), upon restart or reconnect the expiry
     * manager needs to rebuild its expiration queue so it can correctly purge these records as
     * their lifetimes (default 180s) expire.
     *
     * <p><b>IMPORTANT:</b> As a side-effect, this method re-stages the injected {@code
     * txnHistories} map with the recent histories of the {@link TransactionID}s from records in
     * state.
     */
    public void reviewExistingPayerRecords() {
        txnHistories.clear();
        payerRecordExpiries.reset();

        final var payerExpiries = new ArrayList<Map.Entry<Long, Long>>();
        final var savedRecords = records.get();
        if (storageStrategy() == StorageStrategy.IN_SINGLE_FCQ) {
            final var queryableRecords = requireNonNull(savedRecords.getQueryableRecords());
            queryableRecords.clear();
            requireNonNull(savedRecords.getRecords()).forEach(savedRecord -> {
                stage(savedRecord);
                // TODO - the effective payer for this record could have been different than the
                // account in the transaction id (in case of node diligence failure); to address,
                // we will need to add the effective payer number to the ExpirableTxnRecord type
                final var payerNum = savedRecord.getPayerNum();
                queryableRecords
                        .computeIfAbsent(payerNum, ignore -> new LinkedList<>())
                        .add(savedRecord);
            });
        } else {
            savedRecords.doForEach((payerNum, accountRecords) ->
                    stageExpiringRecords(payerNum.longValue(), accountRecords, payerExpiries));
            payerExpiries.sort(comparing(Map.Entry<Long, Long>::getValue).thenComparing(Map.Entry::getKey));
            payerExpiries.forEach(entry -> payerRecordExpiries.track(entry.getKey(), entry.getValue()));
        }
        txnHistories.values().forEach(TxnIdRecentHistory::observeStaged);
    }

    void trackRecordInState(final AccountID owner, final long expiry) {
        // We only need to use an auxiliary expiration queue if records are kept per-payer in state
        if (storageStrategy() != StorageStrategy.IN_SINGLE_FCQ) {
            payerRecordExpiries.track(owner.getAccountNum(), expiry);
        }
    }

    private void purgeExpiredRecordsFromConsolidatedStorageAt(final long now) {
        final var curRecords = requireNonNull(records.get().getRecords());
        final var curQueryableRecords = requireNonNull(records.get().getQueryableRecords());
        for (int i = 0, n = curRecords.size(); i < n; i++) {
            final var nextRecord = curRecords.peek();
            if (requireNonNull(nextRecord).getExpiry() > now) {
                break;
            }
            curRecords.poll();
            purgeHistoryFor(nextRecord, now);

            final var payerRecords = curQueryableRecords.get(nextRecord.getPayerNum());
            if (payerRecords != null && !payerRecords.isEmpty()) {
                final var nextPayerRecord = payerRecords.poll();
                if (!nextRecord.equals(nextPayerRecord)) {
                    log.error("Inconsistent queryable record {} for expired record {}", nextPayerRecord, nextRecord);
                }
                // No more records for this payer, so remove the queue
                if (payerRecords.isEmpty()) {
                    curQueryableRecords.remove(nextRecord.getPayerNum());
                }
            } else {
                log.error(
                        "No queryable records found for payer {} despite link to expiring record {}",
                        nextRecord.getPayerNum(),
                        nextRecord);
            }
        }
    }

    private void purgeExpiredRecordsFromPayerScopedStorageAt(final long now) {
        final var curPayerRecords = records.get();
        while (payerRecordExpiries.hasExpiringAt(now)) {
            final var key = EntityNum.fromLong(payerRecordExpiries.expireNextAt(now));
            final var mutableRecords = curPayerRecords.getMutablePayerRecords(key);
            purgeExpiredFrom(mutableRecords, now);
        }
    }

    private void purgeExpiredFrom(final FCQueue<ExpirableTxnRecord> records, final long now) {
        ExpirableTxnRecord nextRecord;
        while ((nextRecord = records.peek()) != null && nextRecord.getExpiry() <= now) {
            nextRecord = records.poll();
            purgeHistoryFor(requireNonNull(nextRecord), now);
        }
    }

    private void purgeHistoryFor(@NonNull final ExpirableTxnRecord expiredRecord, final long now) {
        final var txnId = expiredRecord.getTxnId().toGrpc();
        final var history = txnHistories.get(txnId);
        if (history != null) {
            history.forgetExpiredAt(now);
            if (history.isForgotten()) {
                txnHistories.remove(txnId);
            }
        }
    }

    private void stageExpiringRecords(
            final Long num, final Queue<ExpirableTxnRecord> records, final List<Map.Entry<Long, Long>> expiries) {
        long lastAdded = -1;
        for (final var expirableTxnRecord : records) {
            stage(expirableTxnRecord);
            final var expiry = expirableTxnRecord.getExpiry();
            if (expiry != lastAdded) {
                expiries.add(new AbstractMap.SimpleImmutableEntry<>(num, expiry));
                lastAdded = expiry;
            }
        }
    }

    private void stage(final ExpirableTxnRecord expirableTxnRecord) {
        final var txnId = expirableTxnRecord.getTxnId().toGrpc();
        txnHistories.computeIfAbsent(txnId, ignore -> new TxnIdRecentHistory()).stage(expirableTxnRecord);
    }

    private StorageStrategy storageStrategy() {
        if (strategyInUse == null) {
            strategyInUse = records.get().storageStrategy();
            log.info("Using {} strategy for record storage", strategyInUse);
        }
        return strategyInUse;
    }
}
