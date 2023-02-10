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

import static com.hedera.node.app.spi.test.meta.PrehandleHandlerContextListUpdatesTest.A_COMPLEX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.PrehandleHandlerContext;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrehandleHandlerContextTest {
    private static final AccountID PAYER = AccountID.newBuilder().setAccountNum(3L).build();
    @Mock private HederaKey payerKey;
    @Mock private HederaKey otherKey;
    @Mock AccountKeyLookup lookup;
    private PrehandleHandlerContext subject;

    @Test
    void gettersWork() {
        given(lookup.getKey(PAYER)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));
        final var txn = createAccountTransaction();
        subject = new PrehandleHandlerContext(lookup, txn, PAYER).addToReqNonPayerKeys(otherKey);

        assertFalse(subject.failed());
        assertEquals(txn, subject.getTxn());
        assertEquals(ResponseCodeEnum.OK, subject.getStatus());
        assertEquals(payerKey, subject.getPayerKey());
        assertEquals(List.of(otherKey), subject.getRequiredNonPayerKeys());
    }

    @Test
    void gettersWorkOnFailure() {
        given(lookup.getKey(PAYER)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));
        final var txn = createAccountTransaction();
        subject =
                new PrehandleHandlerContext(lookup, txn, PAYER)
                        .status(INVALID_ACCOUNT_ID)
                        .addToReqNonPayerKeys(otherKey);

        assertTrue(subject.failed());
        assertEquals(txn, subject.getTxn());
        assertEquals(INVALID_ACCOUNT_ID, subject.getStatus());
        assertEquals(payerKey, subject.getPayerKey());
        assertEquals(
                List.of(),
                subject.getRequiredNonPayerKeys()); // otherKey is not added as there is failure
        // status set
    }

    private TransactionBody createAccountTransaction() {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(PAYER)
                        .setTransactionValidStart(
                                Timestamp.newBuilder().setSeconds(123_456L).build());
        final var createTxnBody =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(A_COMPLEX_KEY)
                        .setReceiverSigRequired(true)
                        .setMemo("Create Account")
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoCreateAccount(createTxnBody)
                .build();
    }
}
