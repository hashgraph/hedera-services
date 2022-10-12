package com.hedera.services.state.migration;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerklePayerRecords;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.EntityNum;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

import static com.hedera.services.utils.MiscUtils.forEach;

public class RecordsStorageAdapter {
    private final boolean accountsOnDisk;
    private final @Nullable MerkleMap<EntityNum, MerkleAccount> legacyAccounts;
    private final @Nullable MerkleMap<EntityNum, MerklePayerRecords> payerRecords;

    public static RecordsStorageAdapter fromLegacy(final MerkleMap<EntityNum, MerkleAccount> accounts) {
        return new RecordsStorageAdapter(accounts, null);
    }

    public static RecordsStorageAdapter fromDedicated(final MerkleMap<EntityNum, MerklePayerRecords> payerRecords) {
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

    public void doForEach(final BiConsumer<EntityNum, FCQueue<ExpirableTxnRecord>> observer) {
        if (accountsOnDisk) {
            forEach(payerRecords, (payerNum, payerRecords) -> observer.accept(payerNum, payerRecords.readOnlyQueue()));
        } else {
            forEach(legacyAccounts, (payerNum, account) -> observer.accept(payerNum, account.records()));
        }
    }
}
