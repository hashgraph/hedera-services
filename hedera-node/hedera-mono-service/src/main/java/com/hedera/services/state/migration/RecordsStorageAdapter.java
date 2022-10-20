/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.migration;

import static com.hedera.services.state.migration.QueryableRecords.NO_QUERYABLE_RECORDS;
import static com.hedera.services.utils.MiscUtils.forEach;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerklePayerRecords;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.EntityNum;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

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
    private final boolean accountsOnDisk;
    private final @Nullable MerkleMap<EntityNum, MerkleAccount> legacyAccounts;
    private final @Nullable MerkleMap<EntityNum, MerklePayerRecords> payerRecords;

    public static RecordsStorageAdapter fromLegacy(
            final MerkleMap<EntityNum, MerkleAccount> accounts) {
        return new RecordsStorageAdapter(accounts, null);
    }

    public static RecordsStorageAdapter fromDedicated(
            final MerkleMap<EntityNum, MerklePayerRecords> payerRecords) {
        return new RecordsStorageAdapter(null, payerRecords);
    }

    private RecordsStorageAdapter(
            @Nullable final MerkleMap<EntityNum, MerkleAccount> accounts,
            @Nullable final MerkleMap<EntityNum, MerklePayerRecords> payerRecords) {
        if (accounts != null) {
            this.accountsOnDisk = false;
            this.legacyAccounts = accounts;
            this.payerRecords = null;
        } else {
            this.accountsOnDisk = true;
            this.legacyAccounts = null;
            this.payerRecords = payerRecords;
        }
    }

    /**
     * Performs any work needed to track records for a given payer account.
     *
     * @param payerNum the new payer number
     */
    public void prepForPayer(final EntityNum payerNum) {
        // If accounts are in memory, the needed FCQ was created as a
        // side-effect of creating the account itself
        if (accountsOnDisk) {
            payerRecords.put(payerNum, new MerklePayerRecords());
        }
    }

    public void forgetPayer(final EntityNum payerNum) {
        // If accounts are in memory, the needed FCQ was removed as a
        // side-effect of removing the account itself
        if (accountsOnDisk) {
            payerRecords.remove(payerNum);
        }
    }

    public void addPayerRecord(final EntityNum payerNum, final ExpirableTxnRecord payerRecord) {
        if (accountsOnDisk) {
            final var mutableRecords = payerRecords.getForModify(payerNum);
            mutableRecords.offer(payerRecord);
        } else {
            final var mutableAccount = legacyAccounts.getForModify(payerNum);
            mutableAccount.records().offer(payerRecord);
        }
    }

    public FCQueue<ExpirableTxnRecord> getMutablePayerRecords(final EntityNum payerNum) {
        if (accountsOnDisk) {
            final var mutableRecords = payerRecords.getForModify(payerNum);
            return mutableRecords.mutableQueue();
        } else {
            final var mutableAccount = legacyAccounts.getForModify(payerNum);
            return mutableAccount.records();
        }
    }

    public QueryableRecords getReadOnlyPayerRecords(final EntityNum payerNum) {
        if (accountsOnDisk) {
            final var payerRecordsView = payerRecords.get(payerNum);
            return (payerRecordsView == null)
                    ? NO_QUERYABLE_RECORDS
                    : payerRecordsView.asQueryableRecords();
        } else {
            final var payerAccountView = legacyAccounts.get(payerNum);
            return (payerAccountView == null)
                    ? NO_QUERYABLE_RECORDS
                    : new QueryableRecords(
                            payerAccountView.numRecords(), payerAccountView.recordIterator());
        }
    }

    public void doForEach(final BiConsumer<EntityNum, FCQueue<ExpirableTxnRecord>> observer) {
        if (accountsOnDisk) {
            forEach(
                    payerRecords,
                    (payerNum, accountRecords) ->
                            observer.accept(payerNum, accountRecords.readOnlyQueue()));
        } else {
            forEach(
                    legacyAccounts,
                    (payerNum, account) -> observer.accept(payerNum, account.records()));
        }
    }
}
