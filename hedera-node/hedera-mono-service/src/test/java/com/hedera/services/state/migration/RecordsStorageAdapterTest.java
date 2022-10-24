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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerklePayerRecords;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordsStorageAdapterTest {
    private static final EntityNum SOME_NUM = EntityNum.fromInt(1234);
    private static final EntityNum SOME_MISSING_NUM = EntityNum.fromInt(4321);

    @Mock private @Nullable MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private @Nullable MerkleMap<EntityNum, MerklePayerRecords> payerRecords;
    @Mock private MerkleAccount account;
    @Mock private MerklePayerRecords accountRecords;
    @Mock private BiConsumer<EntityNum, FCQueue<ExpirableTxnRecord>> observer;

    private RecordsStorageAdapter subject;

    @Test
    void removingLegacyPayerIsNoop() {
        withLegacySubject();
        subject.forgetPayer(SOME_NUM);
        verifyNoInteractions(accounts);
    }

    @Test
    void removingDedicatedPayerRequiresWork() {
        withDedicatedSubject();
        subject.forgetPayer(SOME_NUM);
        verify(payerRecords).remove(SOME_NUM);
    }

    @Test
    void creatingLegacyPayerIsNoop() {
        withLegacySubject();
        subject.prepForPayer(SOME_NUM);
        verifyNoInteractions(accounts);
    }

    @Test
    void creatingDedicatedPayerRequiresWork() {
        withDedicatedSubject();
        subject.prepForPayer(SOME_NUM);
        verify(payerRecords).put(eq(SOME_NUM), any());
    }

    @Test
    void addingWithDedicatedPayerWorks() {
        withDedicatedSubject();
        given(payerRecords.getForModify(SOME_NUM)).willReturn(accountRecords);

        final var aRecord = SeededPropertySource.forSerdeTest(11, 1).nextRecord();
        subject.addPayerRecord(SOME_NUM, aRecord);

        verify(accountRecords).offer(aRecord);
    }

    @Test
    void gettingMutableWithDedicatedPayerWorks() {
        withDedicatedSubject();
        final FCQueue<ExpirableTxnRecord> records = new FCQueue<>();
        given(payerRecords.getForModify(SOME_NUM)).willReturn(accountRecords);
        given(accountRecords.mutableQueue()).willReturn(records);

        assertSame(records, subject.getMutablePayerRecords(SOME_NUM));
    }

    @Test
    void gettingMutableWithLegacyPayerWorks() {
        withLegacySubject();
        final FCQueue<ExpirableTxnRecord> records = new FCQueue<>();
        given(accounts.getForModify(SOME_NUM)).willReturn(account);
        given(account.records()).willReturn(records);

        assertSame(records, subject.getMutablePayerRecords(SOME_NUM));
    }

    @Test
    void gettingReadOnlyWithDedicatedPayerWorks() {
        withDedicatedSubject();
        final var aRecord = SeededPropertySource.forSerdeTest(11, 1).nextRecord();
        final FCQueue<ExpirableTxnRecord> records = new FCQueue<>();
        records.add(aRecord);
        final var queryable = new QueryableRecords(1, records.iterator());
        given(payerRecords.get(SOME_NUM)).willReturn(accountRecords);
        given(accountRecords.asQueryableRecords()).willReturn(queryable);

        assertSame(queryable, subject.getReadOnlyPayerRecords(SOME_NUM));
        assertSame(
                QueryableRecords.NO_QUERYABLE_RECORDS,
                subject.getReadOnlyPayerRecords(SOME_MISSING_NUM));
    }

    @Test
    void gettingReadOnlyWithLegacyPayerWorks() {
        withLegacySubject();
        final var aRecord = SeededPropertySource.forSerdeTest(11, 1).nextRecord();
        final FCQueue<ExpirableTxnRecord> records = new FCQueue<>();
        records.add(aRecord);
        given(accounts.get(SOME_NUM)).willReturn(account);
        given(account.numRecords()).willReturn(1);

        final var queryable = subject.getReadOnlyPayerRecords(SOME_NUM);
        assertEquals(1, queryable.expectedSize());
        assertSame(
                QueryableRecords.NO_QUERYABLE_RECORDS,
                subject.getReadOnlyPayerRecords(SOME_MISSING_NUM));
    }

    @Test
    void addingWithLegacyPayerWorks() {
        withLegacySubject();
        final FCQueue<ExpirableTxnRecord> records = new FCQueue<>();
        given(accounts.getForModify(SOME_NUM)).willReturn(account);
        given(account.records()).willReturn(records);

        final var aRecord = SeededPropertySource.forSerdeTest(11, 1).nextRecord();
        subject.addPayerRecord(SOME_NUM, aRecord);

        final var added = records.poll();
        assertSame(aRecord, added);
    }

    @Test
    void canReviewDedicatedRecords() {
        payerRecords = new MerkleMap<>();
        withDedicatedSubject();
        accountRecords = new MerklePayerRecords();
        final var aRecord = SeededPropertySource.forSerdeTest(11, 1).nextRecord();
        accountRecords.offer(aRecord);
        payerRecords.put(SOME_NUM, accountRecords);

        subject.doForEach(observer);

        verify(observer).accept(eq(SOME_NUM), any());
    }

    @Test
    void canReviewLegacy() {
        accounts = new MerkleMap<>();
        withLegacySubject();
        account = new MerkleAccount();
        final var aRecord = SeededPropertySource.forSerdeTest(11, 1).nextRecord();
        account.records().offer(aRecord);
        accounts.put(SOME_NUM, account);

        subject.doForEach(observer);

        verify(observer).accept(eq(SOME_NUM), any());
    }

    private void withLegacySubject() {
        subject = RecordsStorageAdapter.fromLegacy(accounts);
    }

    private void withDedicatedSubject() {
        subject = RecordsStorageAdapter.fromDedicated(payerRecords);
    }
}
