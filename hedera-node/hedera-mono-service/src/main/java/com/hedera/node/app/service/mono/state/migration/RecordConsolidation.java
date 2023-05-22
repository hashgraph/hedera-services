package com.hedera.node.app.service.mono.state.migration;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.fcqueue.FCQueue;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RecordConsolidation {
    private static final Comparator<ExpirableTxnRecord> BY_EXPIRY_THEN_CONSENSUS_TIME =
            Comparator.comparing(ExpirableTxnRecord::getExpiry)
                    .thenComparing(ExpirableTxnRecord::getConsensusTime);

    public static void toSingleFcq(@NonNull final ServicesState mutableState) {
        final var accounts = mutableState.accounts();
        if (accounts.areOnDisk()) {
            throw new AssertionError("Not implemented");
        } else {
            toSingleFcqFromInMemory(mutableState, accounts);
        }
    }

    private static void toSingleFcqFromInMemory(
            @NonNull final ServicesState mutableState,
            @NonNull final AccountStorageAdapter accounts) {
        final List<ExpirableTxnRecord> savedRecords = new ArrayList<>();
        final List<EntityNum> numsWithRecords = new ArrayList<>();

        // Traverse all accounts in state and collect their records
        accounts.forEach((num, account) -> {
            final var merkleAccount = (MerkleAccount) account;
            if (merkleAccount.records().isEmpty()) {
                return;
            }
            savedRecords.addAll(merkleAccount.records());
            numsWithRecords.add(num);
        });

        // Remove all records from legacy accounts
        numsWithRecords.sort(Comparator.naturalOrder());
        numsWithRecords.forEach(accounts::remove);

        // Sort and consolidate all records into a single FCQ
        savedRecords.sort(BY_EXPIRY_THEN_CONSENSUS_TIME);
        final FCQueue<ExpirableTxnRecord> consolidatedRecords = new FCQueue<>();
        consolidatedRecords.addAll(savedRecords);
        mutableState.setChild(StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ, consolidatedRecords);
    }

    private static void toSingleFcqFromDisk(
            @NonNull final ServicesState mutableState,
            @NonNull final AccountStorageAdapter accounts) {
        throw new AssertionError("Not implemented");
    }

    private RecordConsolidation() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
