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

package com.hedera.node.app.workflows.handle.flow.dispatch.child.logic;

import static com.hedera.node.app.workflows.handle.flow.dispatch.child.logic.ChildRecordBuilderFactoryTest.asTxn;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.base.*;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChildTxnInfoFactoryTest {
    static final Instant consensusTime = Instant.ofEpochSecond(1_234_567L);
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

    private ChildTxnInfoFactory subject;

    @BeforeEach
    void setUp() {
        subject = new ChildTxnInfoFactory();
    }

    @Test
    void testGetTxnInfoFrom() {
        var txnInfo = assertDoesNotThrow(() -> subject.getTxnInfoFrom(txBody));

        assertNotNull(txnInfo);
        assertEquals(txBody, txnInfo.txBody());
        assertEquals(TransactionID.DEFAULT, txnInfo.transactionID());
        assertEquals(AccountID.DEFAULT, txnInfo.payerID());
        assertEquals(SignatureMap.DEFAULT, txnInfo.signatureMap());

        var bodyBytes = TransactionBody.PROTOBUF.toBytes(txBody);
        var signedTransaction =
                SignedTransaction.newBuilder().bodyBytes(bodyBytes).build();
        var signedTransactionBytes = SignedTransaction.PROTOBUF.toBytes(signedTransaction);
        assertArrayEquals(
                signedTransactionBytes.toByteArray(), txnInfo.signedBytes().toByteArray());

        var transaction = Transaction.newBuilder()
                .signedTransactionBytes(signedTransactionBytes)
                .build();
        assertEquals(transaction, txnInfo.transaction());
    }

    @Test
    void testFunctionOfTxnThrowsException() {
        var txBody = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().build())
                .memo("Test Memo")
                .build();
        Exception exception = assertThrows(IllegalArgumentException.class, () -> subject.getTxnInfoFrom(txBody));
        assertTrue(exception.getCause() instanceof UnknownHederaFunctionality);
        assertEquals("Unknown Hedera Functionality", exception.getMessage());
    }
}
