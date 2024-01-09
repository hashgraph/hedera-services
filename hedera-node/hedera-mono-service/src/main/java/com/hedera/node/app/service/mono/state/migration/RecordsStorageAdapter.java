/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.migration;

import static com.hedera.node.app.service.mono.state.migration.QueryableRecords.NO_QUERYABLE_RECORDS;
import static com.hedera.node.app.service.mono.utils.MiscUtils.forEach;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerklePayerRecords;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.function.BiConsumer;

/**
 * Encapsulates storage of <i>payer records</i>, which summarize the results of a transaction and
 * are kept in state for 180 consensus seconds. They are called "payer" records because of the
 * {@code getAccountRecords} HAPI query, whose contract is to return the latest records in in state
 * whose fees were paid by a given {@link com.hederahashgraph.api.proto.java.AccountID}.
 *
 * <p>Without the {@code getAccountRecords} query, we could store all records in a single huge
 * {@link FCQueue} in state. But with the query, that would entail an auxiliary data structure; so
 * we use an in-state representation that explicitly maps from payer id to an {@link FCQueue} with
 * that payer's records.
 *
 * <ul>
 *   <li>When accounts are in memory, each account is an internal node of a {@link MerkleMap}; we
 *       can just use a {@link FCQueue} child for the records of each such internal node.
 *   <li>When accounts are on disk, each account's {@link FCQueue} is wrapped in a {@link
 *       MerklePayerRecords} leaf of a <b>record-specific</b> {@link MerkleMap}.
 * </ul>
 */
public class RecordsStorageAdapter {

    private final StorageStrategy storageStrategy;
    private final @Nullable FCQueue<ExpirableTxnRecord> records;
    private final @Nullable Map<EntityNum, Queue<ExpirableTxnRecord>> queryableRecords;
    private final @Nullable MerkleMapLike<EntityNum, MerkleAccount> legacyAccounts;
    private final @Nullable MerkleMapLike<EntityNum, MerklePayerRecords> payerRecords;

    public static RecordsStorageAdapter fromLegacy(final MerkleMapLike<EntityNum, MerkleAccount> accounts) {
        return new RecordsStorageAdapter(null, null, accounts, null);
    }

    public static RecordsStorageAdapter fromDedicated(final MerkleMapLike<EntityNum, MerklePayerRecords> payerRecords) {
        return new RecordsStorageAdapter(null, null, null, payerRecords);
    }

    public static RecordsStorageAdapter fromConsolidated(
            final @NonNull FCQueue<ExpirableTxnRecord> records,
            final @NonNull Map<EntityNum, Queue<ExpirableTxnRecord>> queryableRecords) {
        return new RecordsStorageAdapter(requireNonNull(records), requireNonNull(queryableRecords), null, null);
    }

    private RecordsStorageAdapter(
            @Nullable final FCQueue<ExpirableTxnRecord> records,
            @Nullable final Map<EntityNum, Queue<ExpirableTxnRecord>> queryableRecords,
            @Nullable final MerkleMapLike<EntityNum, MerkleAccount> accounts,
            @Nullable final MerkleMapLike<EntityNum, MerklePayerRecords> payerRecords) {
        if (records != null) {
            this.storageStrategy = StorageStrategy.IN_SINGLE_FCQ;
            this.records = records;
            this.queryableRecords = requireNonNull(queryableRecords);
            this.legacyAccounts = null;
            this.payerRecords = null;
        } else if (accounts != null) {
            this.storageStrategy = StorageStrategy.IN_ACCOUNT_CHILD_FCQ;
            this.legacyAccounts = accounts;
            this.payerRecords = null;
            this.records = null;
            this.queryableRecords = null;
        } else {
            this.storageStrategy = StorageStrategy.IN_PAYER_SCOPED_FCQ;
            this.legacyAccounts = null;
            this.payerRecords = payerRecords;
            this.records = null;
            this.queryableRecords = null;
        }
    }

    public StorageStrategy storageStrategy() {
        return storageStrategy;
    }

    @Nullable
    public Map<EntityNum, Queue<ExpirableTxnRecord>> getQueryableRecords() {
        return queryableRecords;
    }

    @Nullable
    public FCQueue<ExpirableTxnRecord> getRecords() {
        return records;
    }

    /**
     * Performs any work needed to track records for a given payer account.
     *
     * @param payerNum the new payer number
     */
    public void prepForPayer(final EntityNum payerNum) {
        switch (storageStrategy) {
            case IN_ACCOUNT_CHILD_FCQ -> {
                // In-memory pattern with MerkleInternal accounts each w/ child records FCQ; nothing to do here
            }
            case IN_PAYER_SCOPED_FCQ -> // On-disk pattern with per-payer FCQ created as needed
            requireNonNull(payerRecords).put(payerNum, new MerklePayerRecords());
            case IN_SINGLE_FCQ -> // New on-disk pattern with a single FCQ and a rebuilt per-payer queue for queries
            requireNonNull(queryableRecords).put(payerNum, new LinkedList<>());
        }
    }

    public void forgetPayer(final EntityNum payerNum) {
        switch (storageStrategy) {
            case IN_ACCOUNT_CHILD_FCQ -> {
                // In-memory pattern with MerkleInternal accounts each w/ child records FCQ; nothing to do here
            }
            case IN_PAYER_SCOPED_FCQ -> // On-disk pattern with per-payer FCQ created as needed
            requireNonNull(payerRecords).remove(payerNum);
            case IN_SINGLE_FCQ -> // New on-disk pattern with a single FCQ and a rebuilt per-payer queue for queries
            requireNonNull(queryableRecords).remove(payerNum);
        }
    }

    public void addPayerRecord(final EntityNum payerNum, final ExpirableTxnRecord payerRecord) {
        switch (storageStrategy) {
            case IN_ACCOUNT_CHILD_FCQ -> {
                final var mutableAccount = requireNonNull(legacyAccounts).getForModify(payerNum);
                mutableAccount.records().offer(payerRecord);
            }
            case IN_PAYER_SCOPED_FCQ -> {
                final var mutableRecords = requireNonNull(payerRecords).getForModify(payerNum);
                mutableRecords.offer(payerRecord);
            }
            case IN_SINGLE_FCQ -> {
                requireNonNull(records).offer(payerRecord);
                requireNonNull(queryableRecords)
                        .computeIfAbsent(payerNum, ignore -> new LinkedList<>())
                        .offer(payerRecord);
            }
        }
    }

    public FCQueue<ExpirableTxnRecord> getMutablePayerRecords(final EntityNum payerNum) {
        return switch (storageStrategy) {
            case IN_ACCOUNT_CHILD_FCQ -> {
                final var mutableAccount = requireNonNull(legacyAccounts).getForModify(payerNum);
                yield mutableAccount.records();
            }
            case IN_PAYER_SCOPED_FCQ -> {
                final var mutableRecords = requireNonNull(payerRecords).getForModify(payerNum);
                yield mutableRecords.mutableQueue();
            }
            case IN_SINGLE_FCQ -> records;
        };
    }

    public QueryableRecords getReadOnlyPayerRecords(final EntityNum payerNum) {
        return switch (storageStrategy) {
            case IN_ACCOUNT_CHILD_FCQ -> {
                final var payerAccountView = requireNonNull(legacyAccounts).get(payerNum);
                yield (payerAccountView == null)
                        ? NO_QUERYABLE_RECORDS
                        : new QueryableRecords(payerAccountView.numRecords(), payerAccountView.recordIterator());
            }
            case IN_PAYER_SCOPED_FCQ -> {
                final var payerRecordsView = requireNonNull(payerRecords).get(payerNum);
                yield (payerRecordsView == null) ? NO_QUERYABLE_RECORDS : payerRecordsView.asQueryableRecords();
            }
            case IN_SINGLE_FCQ -> {
                final var accountRecords = requireNonNull(queryableRecords).get(payerNum);
                yield (accountRecords == null)
                        ? NO_QUERYABLE_RECORDS
                        : new QueryableRecords(accountRecords.size(), accountRecords.iterator());
            }
        };
    }

    public void doForEach(final BiConsumer<EntityNum, Queue<ExpirableTxnRecord>> observer) {
        switch (storageStrategy) {
            case IN_ACCOUNT_CHILD_FCQ -> forEach(
                    requireNonNull(legacyAccounts),
                    (payerNum, account) -> observer.accept(payerNum, account.records()));
            case IN_PAYER_SCOPED_FCQ -> forEach(
                    requireNonNull(payerRecords),
                    (payerNum, accountRecords) -> observer.accept(payerNum, accountRecords.readOnlyQueue()));
            case IN_SINGLE_FCQ -> requireNonNull(queryableRecords).forEach(observer);
        }
    }
}
