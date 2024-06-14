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

package com.hedera.node.app.workflows.handle.flow.dispatch.user.logic;

import static com.hedera.node.app.workflows.handle.flow.dispatch.child.logic.ChildRecordBuilderFactoryTest.asTxn;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserRecordInitializerTest {
    private static final Instant consensusTime = Instant.ofEpochSecond(1_234_567L);

    private SingleTransactionRecordBuilderImpl recordBuilder = new SingleTransactionRecordBuilderImpl(
            consensusTime,
            SingleTransactionRecordBuilderImpl.ReversingBehavior.IRREVERSIBLE,
            ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER);

    private static final AccountID payerId =
            AccountID.newBuilder().accountNum(1_234L).build();
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
    private static final TransactionBody txBody = asTxn(transferBody, payerId, consensusTime);
    private static final SignedTransaction transaction = SignedTransaction.newBuilder()
            .bodyBytes(TransactionBody.PROTOBUF.toBytes(txBody))
            .build();
    private static final Bytes transactionBytes = SignedTransaction.PROTOBUF.toBytes(transaction);

    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock
    private ExchangeRateSet exchangeRateSet;

    @InjectMocks
    private UserRecordInitializer subject;

    @BeforeEach
    void setUp() {
        subject = new UserRecordInitializer(exchangeRateManager);
        given(exchangeRateManager.exchangeRates()).willReturn(exchangeRateSet);
    }

    @Test
    void initializeUserRecordWithSignedTransactionBytes() {
        final TransactionInfo txnInfo = new TransactionInfo(
                Transaction.newBuilder().body(txBody).build(),
                txBody,
                SignatureMap.DEFAULT,
                transactionBytes,
                HederaFunctionality.CRYPTO_TRANSFER);

        assertDoesNotThrow(() -> subject.initializeUserRecord(recordBuilder, txnInfo));

        assertEquals(Transaction.newBuilder().body(txBody).build(), recordBuilder.transaction());
        assertEquals(
                TransactionID.newBuilder()
                        .accountID(payerId)
                        .transactionValidStart(Timestamp.newBuilder()
                                .seconds(consensusTime.getEpochSecond())
                                .build())
                        .build(),
                recordBuilder.transactionID());
        assertEquals(exchangeRateManager.exchangeRates(), recordBuilder.exchangeRate());
    }

    @Test
    void initializeUserRecordWithoutSignedTransactionBytes() {
        final TransactionInfo txnInfo = new TransactionInfo(
                Transaction.newBuilder().body(txBody).build(),
                txBody,
                SignatureMap.DEFAULT,
                Bytes.EMPTY,
                HederaFunctionality.CRYPTO_TRANSFER);

        assertDoesNotThrow(() -> subject.initializeUserRecord(recordBuilder, txnInfo));

        assertEquals(Transaction.newBuilder().body(txBody).build(), recordBuilder.transaction());
        assertEquals(
                TransactionID.newBuilder()
                        .accountID(payerId)
                        .transactionValidStart(Timestamp.newBuilder()
                                .seconds(consensusTime.getEpochSecond())
                                .build())
                        .build(),
                recordBuilder.transactionID());
        assertEquals(exchangeRateManager.exchangeRates(), recordBuilder.exchangeRate());
    }

    @Test
    void initializeUserRecordWithoutTransaction() {
        final TransactionInfo txnInfo = new TransactionInfo(
                Transaction.DEFAULT, txBody, SignatureMap.DEFAULT, Bytes.EMPTY, HederaFunctionality.CRYPTO_TRANSFER);

        assertDoesNotThrow(() -> subject.initializeUserRecord(recordBuilder, txnInfo));

        assertEquals(Transaction.DEFAULT, recordBuilder.transaction());
        assertEquals(
                TransactionID.newBuilder()
                        .accountID(payerId)
                        .transactionValidStart(Timestamp.newBuilder()
                                .seconds(consensusTime.getEpochSecond())
                                .build())
                        .build(),
                recordBuilder.transactionID());
        assertEquals(exchangeRateManager.exchangeRates(), recordBuilder.exchangeRate());
    }
}
