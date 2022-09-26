/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.queries.answering;

import static com.hedera.services.state.submerkle.ExpirableTxnRecordTestHelper.fromGprc;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.QueryUtils.payer;
import static com.hedera.test.utils.QueryUtils.txnRecordQuery;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.ExpirableTxnRecordTestHelper;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.merkle.map.MerkleMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnswerFunctionsTest {
    @Mock private StateView view;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private RecordCache recordCache;
    @Mock private MerkleAccount targetAccount;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;

    private List<ExpirableTxnRecord> targetRecords = new ArrayList<>();
    private AnswerFunctions subject;

    @BeforeEach
    void setUp() {
        subject = new AnswerFunctions(dynamicProperties);
    }

    @Test
    void returnsEmptyListForMissingAccount() {
        final var op =
                CryptoGetAccountRecordsQuery.newBuilder()
                        .setAccountID(targetId.toGrpcAccountId())
                        .build();
        setupAccountsView();

        assertSame(Collections.emptyList(), subject.mostRecentRecords(view, op));
    }

    @Test
    void returnsAllRecordsIfWithinMaxQueryable() {
        setupAccountsView();
        givenRecordCount(3);
        given(dynamicProperties.maxNumQueryableRecords()).willReturn(4);

        final var op =
                CryptoGetAccountRecordsQuery.newBuilder()
                        .setAccountID(targetId.toGrpcAccountId())
                        .build();
        final var actual = subject.mostRecentRecords(view, op);

        final var expected = ExpirableTxnRecord.allToGrpc(targetRecords);
        assertEquals(expected, actual);
    }

    @Test
    void returnsOnlyMostRecentRecordsIfTotalNotWithinMaxQueryable() {
        setupAccountsView();
        givenRecordCount(10);
        given(dynamicProperties.maxNumQueryableRecords()).willReturn(2);

        final var op =
                CryptoGetAccountRecordsQuery.newBuilder()
                        .setAccountID(targetId.toGrpcAccountId())
                        .build();
        final var actual = subject.mostRecentRecords(view, op);

        final var expected = ExpirableTxnRecord.allToGrpc(targetRecords.subList(8, 10));
        assertEquals(expected, actual);
    }

    @Test
    void returnsAsManyRecordsAsAvailableOnConcurrentModification() {
        setupAccountsView();
        givenRecordCount(10);
        targetRecords.remove(0);
        targetRecords.remove(1);
        given(dynamicProperties.maxNumQueryableRecords()).willReturn(2);

        final var op =
                CryptoGetAccountRecordsQuery.newBuilder()
                        .setAccountID(targetId.toGrpcAccountId())
                        .build();
        final var actual = subject.mostRecentRecords(view, op);

        assertEquals(Collections.emptyList(), actual);
    }

    @Test
    void returnsAsManyRecordsAsAvailableOnNoSuchElement() {
        setupAccountsView();
        given(accounts.get(targetId)).willReturn(targetAccount);
        given(targetAccount.numRecords()).willReturn(10);
        given(targetAccount.recordIterator()).willReturn(targetRecords.iterator());
        given(dynamicProperties.maxNumQueryableRecords()).willReturn(2);

        final var op =
                CryptoGetAccountRecordsQuery.newBuilder()
                        .setAccountID(targetId.toGrpcAccountId())
                        .build();
        final var actual = subject.mostRecentRecords(view, op);

        assertEquals(Collections.emptyList(), actual);
    }

    @Test
    void returnsEmptyOptionalWhenProblematic() {
        final var validQuery = txnRecordQuery(absentTxnId);
        given(recordCache.getPriorityRecord(absentTxnId)).willReturn(null);

        final var txnRecord = subject.txnRecord(recordCache, validQuery);

        assertFalse(txnRecord.isPresent());
    }

    @Test
    void usesCacheIfPresentThere() {
        final var validQuery = txnRecordQuery(targetTxnId);
        given(recordCache.getPriorityRecord(targetTxnId)).willReturn(cachedTargetRecord);

        final var txnRecord = subject.txnRecord(recordCache, validQuery);

        assertEquals(grpcRecord, txnRecord.get());
        verify(accounts, never()).get(any());
        verify(recordCache, never()).isReceiptPresent(any());
    }

    private void setupAccountsView() {
        final var children = new MutableStateChildren();
        children.setAccounts(accounts);
        view = new StateView(null, children, null);
    }

    private void givenRecordCount(final int n) {
        given(accounts.get(targetId)).willReturn(targetAccount);
        for (int i = 0; i < n; i++) {
            targetRecords.add(
                    ExpirableTxnRecordTestHelper.fromGprc(
                            grpcRecord.toBuilder()
                                    .setConsensusTimestamp(
                                            Timestamp.newBuilder().setSeconds(firstConsSecond + i))
                                    .build()));
        }
        given(targetAccount.numRecords()).willReturn(n);
        given(targetAccount.recordIterator()).willReturn(targetRecords.iterator());
    }

    private static final EntityNum targetId = EntityNum.fromLong(12345L);
    private static final TransactionID targetTxnId =
            TransactionID.newBuilder()
                    .setAccountID(asAccount(payer))
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234L))
                    .build();
    private static final TransactionID absentTxnId =
            TransactionID.newBuilder()
                    .setAccountID(asAccount("3.2.1"))
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(4_321L))
                    .build();
    private static final long firstConsSecond = 1_234_567L;
    private static final TransactionRecord grpcRecord =
            TransactionRecord.newBuilder()
                    .setReceipt(
                            TransactionReceipt.newBuilder()
                                    .setStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS))
                    .setTransactionID(targetTxnId)
                    .setMemo("Dim galleries, dusk winding stairs got past...")
                    .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(firstConsSecond))
                    .setTransactionFee(555L)
                    .setTransferList(
                            withAdjustments(
                                    asAccount("0.0.2"), -2L,
                                    asAccount("0.0.2"), -2L,
                                    asAccount("0.0.1001"), 2L,
                                    asAccount("0.0.1002"), 2L))
                    .build();
    private static final ExpirableTxnRecord targetRecord = fromGprc(grpcRecord);
    private static final ExpirableTxnRecord cachedTargetRecord = targetRecord;
}
