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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.ScheduleSigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleSigTransactionMetadataTest {
    private static final TransactionBody DEFAULT_TX_BODY = new TransactionBody.Builder().build();
    @Mock private AccountKeyLookup keyLookup;
    @Mock private HederaKey payerKey;
    private AccountID payer = new AccountID.Builder().accountNum(3L).build();
    private AccountID schedulePayer = new AccountID.Builder().accountNum(4L).build();

    @Test
    void getsInnerMetadata() {
        final var txn = createScheduleTransaction();
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));
        given(keyLookup.getKey(schedulePayer)).willReturn(withKey(payerKey));

        final var innerMeta =
                new SigTransactionMetadataBuilder(keyLookup)
                        .payerKeyFor(schedulePayer)
                        .txnBody(DEFAULT_TX_BODY)
                        .build();
        final var subject =
                new ScheduleSigTransactionMetadataBuilder(keyLookup)
                        .txnBody(txn)
                        .payerKeyFor(payer)
                        .scheduledMeta(innerMeta)
                        .build();
        assertEquals(innerMeta, subject.scheduledMeta());
    }

    @Test
    void gettersWorkOnFailure() {
        final var txn = createScheduleTransaction();
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));
        given(keyLookup.getKey(schedulePayer)).willReturn(withKey(payerKey));

        final var innerMeta =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(DEFAULT_TX_BODY)
                        .payerKeyFor(schedulePayer)
                        .build();
        final var subject =
                new ScheduleSigTransactionMetadataBuilder(keyLookup)
                        .txnBody(txn)
                        .payerKeyFor(payer)
                        .scheduledMeta(innerMeta)
                        .status(INVALID_PAYER_ACCOUNT_ID)
                        .build();
        assertEquals(innerMeta, subject.scheduledMeta());
        assertEquals(INVALID_PAYER_ACCOUNT_ID, subject.status());
        assertTrue(subject.failed());
    }

    @Test
    void gettersWorkOnInnerMetaFailure() {
        final var txn = createScheduleTransaction();
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));
        given(keyLookup.getKey(schedulePayer)).willReturn(withKey(payerKey));

        final var innerMeta =
                new SigTransactionMetadataBuilder(keyLookup)
                        .status(INVALID_PAYER_ACCOUNT_ID)
                        .txnBody(DEFAULT_TX_BODY)
                        .payerKeyFor(schedulePayer)
                        .build();
        final var subject =
                new ScheduleSigTransactionMetadataBuilder(keyLookup)
                        .txnBody(txn)
                        .payerKeyFor(payer)
                        .scheduledMeta(innerMeta)
                        .build();
        assertEquals(innerMeta, subject.scheduledMeta());
        assertEquals(OK, subject.status());
        assertEquals(INVALID_PAYER_ACCOUNT_ID, subject.scheduledMeta().status());
        assertTrue(subject.scheduledMeta().failed());
    }

    private TransactionBody createScheduleTransaction() {
        final var transactionID = new TransactionID.Builder().accountID(payer).build();
        final var createTxnBody =
                new ScheduleCreateTransactionBody.Builder()
                        .scheduledTransactionBody(
                                new SchedulableTransactionBody.Builder()
                                        .memo("test")
                                        .transactionFee(1_000_000L)
                                        .build())
                        .payerAccountID(schedulePayer)
                        .build();
        return new TransactionBody.Builder()
                .transactionID(transactionID)
                .scheduleCreate(createTxnBody)
                .build();
    }
}
