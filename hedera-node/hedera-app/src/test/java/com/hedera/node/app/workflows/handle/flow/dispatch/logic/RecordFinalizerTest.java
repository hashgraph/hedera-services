/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.dispatch.logic;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static com.hedera.node.app.workflows.handle.flow.dispatch.child.logic.ChildRecordBuilderFactoryTest.asTxn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.records.ChildRecordFinalizer;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.service.token.records.ParentRecordFinalizer;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.flow.dispatch.Dispatch;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RecordFinalizerTest {
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);

    @Mock
    private ParentRecordFinalizer parentRecordFinalizer;

    @Mock
    private ChildRecordFinalizer childRecordFinalizer;

    @Mock
    private Dispatch dispatch;

    @Mock
    private FinalizeContext finalizeContext;

    @Mock
    private HandleContext handleContext;

    private static final AccountID PAYER_ID =
            AccountID.newBuilder().accountNum(1_234L).build();
    private static final CryptoTransferTransactionBody TRANSFER_BODY = CryptoTransferTransactionBody.newBuilder()
            .transfers(TransferList.newBuilder()
                    .accountAmounts(
                            AccountAmount.newBuilder()
                                    .accountID(
                                            AccountID.newBuilder().accountNum(1).build())
                                    .amount(0)
                                    .build(),
                            AccountAmount.newBuilder()
                                    .accountID(
                                            AccountID.newBuilder().accountNum(2).build())
                                    .amount(10)
                                    .build()))
            .build();
    private static final TransactionBody TX_BODY = asTxn(TRANSFER_BODY, PAYER_ID, CONSENSUS_NOW);
    private static final TransactionInfo TXN_INFO = new TransactionInfo(
            Transaction.newBuilder().body(TX_BODY).build(),
            TX_BODY,
            SignatureMap.DEFAULT,
            Bytes.EMPTY,
            HederaFunctionality.CRYPTO_TRANSFER);

    private SingleTransactionRecordBuilderImpl recordBuilder = new SingleTransactionRecordBuilderImpl(CONSENSUS_NOW);
    private RecordFinalizer subject;

    @BeforeEach
    void setUp() {
        subject = new RecordFinalizer(parentRecordFinalizer, childRecordFinalizer);

        lenient().when(dispatch.txnInfo()).thenReturn(TXN_INFO);
        lenient().when(dispatch.recordBuilder()).thenReturn(recordBuilder);
        lenient().when(dispatch.finalizeContext()).thenReturn(finalizeContext);
        lenient().when(dispatch.handleContext()).thenReturn(handleContext);
    }

    @Test
    public void testFinalizeRecordUserTransaction() {
        when(dispatch.txnCategory()).thenReturn(USER);

        when(dispatch.handleContext().dispatchPaidRewards()).thenReturn(Map.of());

        subject.finalizeRecord(dispatch);

        verify(parentRecordFinalizer).finalizeParentRecord(any(), any(), any(), any());
        verify(childRecordFinalizer, never()).finalizeChildRecord(any(), any());
    }

    @Test
    public void testFinalizeRecordChildTransaction() {
        when(dispatch.txnCategory()).thenReturn(CHILD);

        subject.finalizeRecord(dispatch);

        verify(childRecordFinalizer, times(1)).finalizeChildRecord(any(), any());
        verify(parentRecordFinalizer, never()).finalizeParentRecord(any(), any(), any(), any());
    }

    @Test
    public void testExtraRewardReceiversCryptoTransfer() {
        recordBuilder.status(SUCCESS);
        Set<AccountID> extraRewardReceivers =
                subject.extraRewardReceivers(TX_BODY, HederaFunctionality.CRYPTO_TRANSFER, recordBuilder);

        assertEquals(1, extraRewardReceivers.size());
        assertTrue(extraRewardReceivers.contains(
                AccountID.newBuilder().accountNum(1).build()));
    }

    @Test
    public void testExtraRewardReceiversOtherFunctionality() {
        recordBuilder.status(SUCCESS);

        Set<AccountID> extraRewardReceivers =
                subject.extraRewardReceivers(TX_BODY, HederaFunctionality.CONTRACT_CALL, recordBuilder);

        assertTrue(extraRewardReceivers.isEmpty());
    }

    @Test
    public void testZeroAdjustIdsFrom() {
        List<AccountAmount> accountAmounts = List.of(
                AccountAmount.newBuilder()
                        .accountID(AccountID.newBuilder().accountNum(1).build())
                        .amount(0)
                        .build(),
                AccountAmount.newBuilder()
                        .accountID(AccountID.newBuilder().accountNum(2).build())
                        .amount(10)
                        .build());

        Set<AccountID> zeroAdjustIds = subject.zeroAdjustIdsFrom(accountAmounts);

        assertEquals(1, zeroAdjustIds.size());
        assertTrue(zeroAdjustIds.contains(AccountID.newBuilder().accountNum(1).build()));
    }
}
