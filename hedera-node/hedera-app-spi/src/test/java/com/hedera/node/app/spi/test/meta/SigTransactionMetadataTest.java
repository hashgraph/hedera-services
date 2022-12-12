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
package com.hedera.node.app.spi.test.meta;

import static com.hedera.node.app.spi.test.meta.SigTransactionMetadataBuilderTest.A_COMPLEX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadata;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SigTransactionMetadataTest {
    private AccountID payer = AccountID.newBuilder().setAccountNum(3L).build();
    private Key key = A_COMPLEX_KEY;
    @Mock private HederaKey payerKey;
    @Mock private HederaKey otherKey;
    @Mock AccountKeyLookup lookup;
    private TransactionMetadata subject;

    @Test
    void gettersWork() {
        given(lookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));
        final var txn = createAccountTransaction();
        subject =
                new SigTransactionMetadataBuilder<>(lookup)
                        .payerKeyFor(payer)
                        .txnBody(txn)
                        .addToReqKeys(otherKey)
                        .build();

        assertFalse(subject.failed());
        assertEquals(txn, subject.txnBody());
        assertEquals(ResponseCodeEnum.OK, subject.status());
        assertEquals(List.of(payerKey, otherKey), subject.requiredKeys());
    }

    @Test
    void gettersWorkOnFailure() {
        given(lookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));
        final var txn = createAccountTransaction();
        subject = new SigTransactionMetadataBuilder<>(lookup)
                        .payerKeyFor(payer)
                        .status(INVALID_ACCOUNT_ID)
                        .txnBody(txn)
                        .addToReqKeys(otherKey)
                .build();

        assertTrue(subject.failed());
        assertEquals(txn, subject.txnBody());
        assertEquals(INVALID_ACCOUNT_ID, subject.status());
        assertEquals(
                List.of(payerKey),
                subject.requiredKeys()); // otherKey is not added as there is failure status set
    }

    private TransactionBody createAccountTransaction() {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payer)
                        .setTransactionValidStart(
                                Timestamp.newBuilder().setSeconds(123_456L).build());
        final var createTxnBody =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(key)
                        .setReceiverSigRequired(true)
                        .setMemo("Create Account")
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoCreateAccount(createTxnBody)
                .build();
    }
}
