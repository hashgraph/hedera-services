/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.test.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.node.app.spi.meta.ErrorTransactionMetadata;
import com.hederahashgraph.api.proto.java.*;
import org.junit.jupiter.api.Test;

class ErrorTransactionMetadataTest {
    final ResponseCodeEnum responseCode = ResponseCodeEnum.INVALID_SIGNATURE;
    final Throwable throwable = new Throwable("Invalid signature");
    final TransactionBody txBody = createAccountTransaction();

    private ErrorTransactionMetadata subject =
            new ErrorTransactionMetadata(txBody, responseCode, throwable);

    @Test
    void testCause() {
        assertEquals("Invalid signature", subject.cause().getMessage());
    }

    @Test
    void testStatus() {
        assertEquals(ResponseCodeEnum.INVALID_SIGNATURE, subject.status());
    }

    @Test
    void testTxnBody() {
        assertEquals(txBody, subject.txnBody());
    }

    @Test
    void testRequiredNonPayerKeys() {
        assertEquals(0, subject.requiredNonPayerKeys().size());
    }

    @Test
    void testPayer() {
        assertNull(subject.payer());
    }

    @Test
    void testPayerKey() {
        assertNull(subject.payerKey());
    }

    private TransactionBody createAccountTransaction() {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(AccountID.newBuilder().setAccountNum(3L).build())
                        .setTransactionValidStart(Timestamp.newBuilder().build());
        final var createTxnBody =
                CryptoCreateTransactionBody.newBuilder()
                        .setReceiverSigRequired(true)
                        .setMemo("Create Account")
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoCreateAccount(createTxnBody)
                .build();
    }
}
