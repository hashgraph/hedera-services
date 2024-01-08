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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccountState;
import com.hedera.node.app.service.mono.state.merkle.MerklePayerRecords;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordConsolidationTest {
    @Mock
    private ServicesState mutableState;

    @Mock
    private AccountStorageAdapter accounts;

    @Test
    void canMigrateFromLegacyAccounts() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));

        final ArgumentCaptor<FCQueue<ExpirableTxnRecord>> recordsCaptor = forClass(FCQueue.class);

        final int numAccounts = 20;
        final int maxRecordsPerAccount = 5;
        final var accountsAndRecords = randomLegacyAccounts(numAccounts, maxRecordsPerAccount);

        given(mutableState.accounts())
                .willReturn(AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accountsAndRecords.getKey())));

        RecordConsolidation.toSingleFcq(mutableState);

        verify(mutableState).setChild(eq(StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ), recordsCaptor.capture());
        final var capturedRecords = recordsCaptor.getValue();
        assertEquals(accountsAndRecords.getValue(), new LinkedList<>(capturedRecords));

        assertEquals(numAccounts, accountsAndRecords.getKey().size());
        accountsAndRecords.getKey().forEach((num, account) -> {
            assertTrue(account.records().isEmpty());
        });
    }

    @Test
    void canMigrateFromOnDiskAccounts() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(MerklePayerRecords.class, MerklePayerRecords::new));

        final ArgumentCaptor<FCQueue<ExpirableTxnRecord>> recordsCaptor = forClass(FCQueue.class);

        final int numAccounts = 20;
        final int maxRecordsPerAccount = 5;
        final var payerAndAllRecords = randomPayerRecords(numAccounts, maxRecordsPerAccount);

        given(accounts.areOnDisk()).willReturn(true);
        given(mutableState.getChild(StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ))
                .willReturn(payerAndAllRecords.getKey());
        given(mutableState.accounts()).willReturn(accounts);

        RecordConsolidation.toSingleFcq(mutableState);

        verify(mutableState).setChild(eq(StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ), recordsCaptor.capture());
        final var capturedRecords = recordsCaptor.getValue();
        assertEquals(payerAndAllRecords.getValue(), new LinkedList<>(capturedRecords));
    }

    private Pair<MerkleMap<EntityNum, MerkleAccount>, Queue<ExpirableTxnRecord>> randomLegacyAccounts(
            final int numAccounts, final int maxRecordsPerAccount) {
        final MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
        final var propertySource = SeededPropertySource.forSerdeTest(14, 1);
        final List<ExpirableTxnRecord> allRecords = new ArrayList<>();
        for (int i = 0; i < numAccounts; i++) {
            final FCQueue<ExpirableTxnRecord> recordsHere = new FCQueue<>();
            for (int j = 0, n = propertySource.nextInt(maxRecordsPerAccount); j < n; j++) {
                final var nextRecord = propertySource.nextRecord();
                recordsHere.add(nextRecord);
                allRecords.add(nextRecord);
            }
            final var account = new MerkleAccount(List.of(new MerkleAccountState(), recordsHere));
            accounts.put(EntityNum.fromInt(i), account);
        }
        allRecords.sort(Comparator.comparing(ExpirableTxnRecord::getExpiry)
                .thenComparing(ExpirableTxnRecord::getConsensusTime));
        return Pair.of(accounts, new LinkedList<>(allRecords));
    }

    private Pair<MerkleMap<EntityNum, MerklePayerRecords>, Queue<ExpirableTxnRecord>> randomPayerRecords(
            final int numAccounts, final int maxRecordsPerAccount) {
        final MerkleMap<EntityNum, MerklePayerRecords> payerRecords = new MerkleMap<>();
        final var propertySource = SeededPropertySource.forSerdeTest(14, 1);
        final List<ExpirableTxnRecord> allRecords = new ArrayList<>();
        for (int i = 0; i < numAccounts; i++) {
            final FCQueue<ExpirableTxnRecord> recordsHere = new FCQueue<>();
            for (int j = 0, n = propertySource.nextInt(maxRecordsPerAccount); j < n; j++) {
                final var nextRecord = propertySource.nextRecord();
                recordsHere.add(nextRecord);
                allRecords.add(nextRecord);
            }
            final var thesePayerRecords = new MerklePayerRecords();
            thesePayerRecords.mutableQueue().addAll(recordsHere);
            payerRecords.put(EntityNum.fromInt(i), thesePayerRecords);
        }
        allRecords.sort(Comparator.comparing(ExpirableTxnRecord::getExpiry)
                .thenComparing(ExpirableTxnRecord::getConsensusTime));
        return Pair.of(payerRecords, new LinkedList<>(allRecords));
    }
}
