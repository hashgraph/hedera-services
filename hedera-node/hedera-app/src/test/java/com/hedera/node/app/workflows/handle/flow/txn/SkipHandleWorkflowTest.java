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

package com.hedera.node.app.workflows.handle.flow.txn;

import static com.hedera.hapi.node.base.ResponseCodeEnum.BUSY;
import static com.hedera.node.app.spi.fixtures.Scenarios.ALICE;
import static com.hedera.node.app.workflows.handle.flow.DispatchHandleContextTest.DEFAULT_CONSENSUS_NOW;
import static com.hedera.node.app.workflows.handle.flow.dispatch.child.logic.ChildRecordBuilderFactoryTest.asTxn;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkipHandleWorkflowTest {
    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock
    private UserTransactionComponent userTxn;

    @Mock
    private ExchangeRateSet exchangeRateSet;

    @InjectMocks
    private SkipHandleWorkflow subject;

    private static final AccountID payerId = ALICE.accountID();
    private static final CryptoTransferTransactionBody transferBody = CryptoTransferTransactionBody.newBuilder()
            .tokenTransfers(TokenTransferList.newBuilder()
                    .token(TokenID.DEFAULT)
                    .nftTransfers(NftTransfer.newBuilder()
                            .receiverAccountID(AccountID.DEFAULT)
                            .senderAccountID(AccountID.DEFAULT)
                            .serialNumber(1)
                            .build())
                    .build())
            .build();
    private static final TransactionBody txBody = asTxn(transferBody, payerId, DEFAULT_CONSENSUS_NOW);
    private RecordListBuilder recordListBuilder = new RecordListBuilder(DEFAULT_CONSENSUS_NOW);

    private static final TransactionInfo txnInfo = new TransactionInfo(
            Transaction.newBuilder().body(txBody).build(),
            txBody,
            SignatureMap.DEFAULT,
            Bytes.EMPTY,
            HederaFunctionality.CRYPTO_TRANSFER);

    @BeforeEach
    public void setUp() {
        subject = new SkipHandleWorkflow(exchangeRateManager);
        when(exchangeRateManager.exchangeRates()).thenReturn(exchangeRateSet);
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testExecute() {
        // Setup mocks
        when(userTxn.txnInfo()).thenReturn(txnInfo);
        when(userTxn.recordListBuilder()).thenReturn(recordListBuilder);

        subject.execute(userTxn);

        Assertions.assertThat(recordListBuilder.userTransactionRecordBuilder().status())
                .isEqualTo(BUSY);
        Assertions.assertThat(recordListBuilder.userTransactionRecordBuilder().transaction())
                .isEqualTo(txnInfo.transaction());
        Assertions.assertThat(recordListBuilder.userTransactionRecordBuilder().transactionID())
                .isEqualTo(txnInfo.transactionID());
        Assertions.assertThat(recordListBuilder.userTransactionRecordBuilder().exchangeRate())
                .isNotNull();
    }
}
