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

package com.hedera.node.app.service.mono.state.migration;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerklePayerRecords;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.fcqueue.FCQueue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RecordConsolidation {
    private static final Comparator<ExpirableTxnRecord> BY_EXPIRY_THEN_CONSENSUS_TIME =
            Comparator.comparing(ExpirableTxnRecord::getExpiry).thenComparing(ExpirableTxnRecord::getConsensusTime);

    public static void toSingleFcq(@NonNull final ServicesState mutableState) {
        final var accounts = mutableState.accounts();
        if (accounts.areOnDisk()) {
            toSingleFcqFromDisk(mutableState);
        } else {
            toSingleFcqFromInMemory(mutableState, accounts);
        }
    }

    private static void toSingleFcqFromInMemory(
            @NonNull final ServicesState mutableState, @NonNull final AccountStorageAdapter accounts) {
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
        numsWithRecords.forEach(num -> {
            final var mutableAccount = (MerkleAccount) accounts.getForModify(num);
            mutableAccount.records().clear();
        });

        finishConsolidation(savedRecords, mutableState);
    }

    private static void toSingleFcqFromDisk(@NonNull final ServicesState mutableState) {
        final List<ExpirableTxnRecord> savedRecords = new ArrayList<>();

        // Traverse all payer records in state and accumulate
        final MerkleMapLike<EntityNum, MerklePayerRecords> payerRecords =
                MerkleMapLike.from(mutableState.getChild(StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ));
        payerRecords.forEach((num, recordsHere) -> {
            final var fcq = recordsHere.mutableQueue();
            if (fcq.isEmpty()) {
                return;
            }
            savedRecords.addAll(fcq);
        });

        // This overwrites the payer records in state, they will be garbage collected naturally
        finishConsolidation(savedRecords, mutableState);
    }

    private static void finishConsolidation(
            @NonNull final List<ExpirableTxnRecord> savedRecords, @NonNull final ServicesState mutableState) {
        // Sort and consolidate all records into a single FCQ
        savedRecords.sort(BY_EXPIRY_THEN_CONSENSUS_TIME);
        final FCQueue<ExpirableTxnRecord> consolidatedRecords = new FCQueue<>();
        consolidatedRecords.addAll(savedRecords);
        mutableState.setChild(StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ, consolidatedRecords);
    }

    private RecordConsolidation() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
