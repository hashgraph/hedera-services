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

package com.hedera.node.app.workflows.handle.flow.dispatch.user.helpers;

import static com.hedera.node.app.workflows.handle.flow.dispatch.child.helpers.ChildRecordBuilderFactoryTest.asTxn;
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
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserRecordInitializer;
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
    private static final Instant CONSENSUS_TIME = Instant.ofEpochSecond(1_234_567L);
    private static final AccountID PAYER_ID =
            AccountID.newBuilder().accountNum(1_234L).build();
    private static final CryptoTransferTransactionBody TRANSFER_BODY = CryptoTransferTransactionBody.newBuilder()
            .tokenTransfers(TokenTransferList.newBuilder()
                    .token(TokenID.DEFAULT)
                    .nftTransfers(NftTransfer.newBuilder()
                            .receiverAccountID(AccountID.DEFAULT)
                            .senderAccountID(AccountID.DEFAULT)
                            .serialNumber(1)
                            .build())
                    .build())
            .build();
    private static final TransactionBody TX_BODY = asTxn(TRANSFER_BODY, PAYER_ID, CONSENSUS_TIME);
    private static final SignedTransaction SIGNED_TXN = SignedTransaction.newBuilder()
            .bodyBytes(TransactionBody.PROTOBUF.toBytes(TX_BODY))
            .build();
    private static final Bytes TRANSACTION_BYTES = SignedTransaction.PROTOBUF.toBytes(SIGNED_TXN);

    private final SingleTransactionRecordBuilderImpl recordBuilder = new SingleTransactionRecordBuilderImpl(
            CONSENSUS_TIME,
            SingleTransactionRecordBuilderImpl.ReversingBehavior.IRREVERSIBLE,
            ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER);

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
                Transaction.newBuilder()
                        .signedTransactionBytes(TRANSACTION_BYTES)
                        .build(),
                TX_BODY,
                SignatureMap.DEFAULT,
                TRANSACTION_BYTES,
                HederaFunctionality.CRYPTO_TRANSFER);

        assertDoesNotThrow(() -> subject.initializeUserRecord(recordBuilder, txnInfo));

        assertEquals(
                Transaction.newBuilder()
                        .signedTransactionBytes(TRANSACTION_BYTES)
                        .build(),
                recordBuilder.transaction());
        assertEquals(
                TransactionID.newBuilder()
                        .accountID(PAYER_ID)
                        .transactionValidStart(Timestamp.newBuilder()
                                .seconds(CONSENSUS_TIME.getEpochSecond())
                                .build())
                        .build(),
                recordBuilder.transactionID());
        assertEquals(exchangeRateManager.exchangeRates(), recordBuilder.exchangeRate());
    }

    @Test
    void initializeUserRecordWithoutSignedTransactionBytes() {
        final TransactionInfo txnInfo = new TransactionInfo(
                Transaction.newBuilder().body(TX_BODY).build(),
                TX_BODY,
                SignatureMap.DEFAULT,
                Bytes.EMPTY,
                HederaFunctionality.CRYPTO_TRANSFER);

        assertDoesNotThrow(() -> subject.initializeUserRecord(recordBuilder, txnInfo));

        assertEquals(Transaction.newBuilder().body(TX_BODY).build(), recordBuilder.transaction());
        assertEquals(
                TransactionID.newBuilder()
                        .accountID(PAYER_ID)
                        .transactionValidStart(Timestamp.newBuilder()
                                .seconds(CONSENSUS_TIME.getEpochSecond())
                                .build())
                        .build(),
                recordBuilder.transactionID());
        assertEquals(exchangeRateManager.exchangeRates(), recordBuilder.exchangeRate());
    }

    @Test
    void initializeUserRecordWithoutTransaction() {
        final TransactionInfo txnInfo = new TransactionInfo(
                Transaction.DEFAULT, TX_BODY, SignatureMap.DEFAULT, Bytes.EMPTY, HederaFunctionality.CRYPTO_TRANSFER);

        assertDoesNotThrow(() -> subject.initializeUserRecord(recordBuilder, txnInfo));

        assertEquals(Transaction.DEFAULT, recordBuilder.transaction());
        assertEquals(
                TransactionID.newBuilder()
                        .accountID(PAYER_ID)
                        .transactionValidStart(Timestamp.newBuilder()
                                .seconds(CONSENSUS_TIME.getEpochSecond())
                                .build())
                        .build(),
                recordBuilder.transactionID());
        assertEquals(exchangeRateManager.exchangeRates(), recordBuilder.exchangeRate());
    }
}
