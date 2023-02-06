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

import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withFailureReason;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.ScheduleSigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.ScheduleTransactionMetadata;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleSigTransactionMetadataBuilderTest {
    private ScheduleSigTransactionMetadataBuilder subject;
    @Mock TransactionMetadata scheduledMetadata;
    @Mock private AccountKeyLookup keyLookup;
    @Mock private HederaKey payerKey;
    @Mock private HederaKey schedulePayerKey;
    private AccountID payer = AccountID.newBuilder().setAccountNum(3L).build();
    private AccountID schedulePayer = AccountID.newBuilder().setAccountNum(4L).build();
    private ScheduleTransactionMetadata meta;

    @Test
    void gettersWorkAsExpectedWhenPayerKeyExist() {
        final var txn = createScheduleTransaction();
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));

        subject =
                new ScheduleSigTransactionMetadataBuilder(keyLookup)
                        .txnBody(txn)
                        .payerKeyFor(payer)
                        .scheduledMeta(scheduledMetadata);
        meta = subject.build();

        assertFalse(meta.failed());
        assertEquals(txn, meta.txnBody());
        assertEquals(payerKey, meta.payerKey());
        assertEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(scheduledMetadata, meta.scheduledMeta());
    }

    @Test
    void nullInputToBuilderArgumentsThrows() {
        final var subject = new ScheduleSigTransactionMetadataBuilder(keyLookup);
        assertThrows(
                NullPointerException.class, () -> new ScheduleSigTransactionMetadataBuilder(null));
        assertThrows(NullPointerException.class, () -> subject.txnBody(null));
        assertThrows(NullPointerException.class, () -> subject.payerKeyFor(null));
        assertThrows(NullPointerException.class, () -> subject.status(null));
        assertThrows(NullPointerException.class, () -> subject.addNonPayerKey((AccountID) null));
        assertThrows(
                NullPointerException.class,
                () -> subject.addNonPayerKeyIfReceiverSigRequired(null, null));
        assertDoesNotThrow(() -> subject.addNonPayerKey(payer, null));
        assertThrows(NullPointerException.class, () -> subject.scheduledMeta(null));
        assertDoesNotThrow(() -> subject.addNonPayerKeyIfReceiverSigRequired(payer, null));
    }

    @Test
    void gettersWorkAsExpectedWhenOtherKeysSet() {
        final var txn = createScheduleTransaction();
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));
        subject =
                new ScheduleSigTransactionMetadataBuilder(keyLookup)
                        .txnBody(txn)
                        .payerKeyFor(payer)
                        .addAllReqKeys(List.of(schedulePayerKey))
                        .scheduledMeta(scheduledMetadata);
        meta = subject.build();

        assertFalse(meta.failed());
        assertEquals(txn, meta.txnBody());
        assertEquals(payerKey, meta.payerKey());
        assertEquals(List.of(schedulePayerKey), meta.requiredNonPayerKeys());
        assertEquals(payer, meta.payer());
    }

    @Test
    void failsWhenPayerKeyDoesntExist() {
        final var txn = createScheduleTransaction();
        given(keyLookup.getKey(payer)).willReturn(withFailureReason(INVALID_PAYER_ACCOUNT_ID));

        subject =
                new ScheduleSigTransactionMetadataBuilder(keyLookup)
                        .txnBody(txn)
                        .payerKeyFor(payer)
                        .addToReqNonPayerKeys(schedulePayerKey)
                        .scheduledMeta(scheduledMetadata);
        meta = subject.build();

        assertTrue(meta.failed());
        assertNull(meta.payerKey());
        assertEquals(INVALID_PAYER_ACCOUNT_ID, meta.status());

        assertEquals(txn, meta.txnBody());
        assertEquals(
                List.of(),
                meta.requiredNonPayerKeys()); // No other keys are added when payerKey is not added
    }

    @Test
    void doesntAddToReqKeysIfStatus() {
        given(keyLookup.getKey(payer)).willReturn(withFailureReason(INVALID_PAYER_ACCOUNT_ID));

        subject =
                new ScheduleSigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createScheduleTransaction())
                        .payerKeyFor(payer)
                        .addToReqNonPayerKeys(schedulePayerKey)
                        .scheduledMeta(scheduledMetadata);
        subject.addToReqNonPayerKeys(payerKey);

        assertEquals(0, subject.build().requiredNonPayerKeys().size());
        assertNull(subject.build().payerKey());
        assertFalse(subject.build().requiredNonPayerKeys().contains(payerKey));
    }

    @Test
    void addsToReqKeysCorrectly() {
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));

        subject =
                new ScheduleSigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createScheduleTransaction())
                        .payerKeyFor(payer)
                        .scheduledMeta(scheduledMetadata);

        assertEquals(0, subject.build().requiredNonPayerKeys().size());
        assertEquals(payerKey, subject.build().payerKey());

        subject.addToReqNonPayerKeys(schedulePayerKey);
        assertEquals(1, subject.build().requiredNonPayerKeys().size());
        assertEquals(payerKey, subject.build().payerKey());
        assertTrue(subject.build().requiredNonPayerKeys().contains(schedulePayerKey));
    }

    @Test
    void settersWorkCorrectly() {
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));
        final var txn = createScheduleTransaction();

        subject =
                new ScheduleSigTransactionMetadataBuilder(keyLookup)
                        .txnBody(txn)
                        .payerKeyFor(payer)
                        .scheduledMeta(scheduledMetadata)
                        .addToReqNonPayerKeys(schedulePayerKey);
        assertEquals(OK, subject.build().status());
        assertEquals(scheduledMetadata, subject.build().scheduledMeta());
        assertEquals(txn, subject.build().txnBody());
        assertEquals(payerKey, subject.build().payerKey());
        assertEquals(payer, subject.build().payer());
        assertIterableEquals(List.of(schedulePayerKey), subject.build().requiredNonPayerKeys());
    }

    @Test
    void failsIfRequiredFieldsAreNull() {
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));

        subject = new ScheduleSigTransactionMetadataBuilder(keyLookup);
        assertThrows(NullPointerException.class, subject::build);

        subject.txnBody(createScheduleTransaction());
        assertThrows(NullPointerException.class, subject::build);

        subject.payerKeyFor(payer);
        assertThrows(NullPointerException.class, subject::build);

        subject.scheduledMeta(scheduledMetadata);
        assertDoesNotThrow(subject::build);
    }

    private TransactionBody createScheduleTransaction() {
        final var transactionID = TransactionID.newBuilder().setAccountID(payer);
        final var createTxnBody =
                ScheduleCreateTransactionBody.newBuilder()
                        .setScheduledTransactionBody(
                                SchedulableTransactionBody.newBuilder()
                                        .setMemo("test")
                                        .setTransactionFee(1_000_000L))
                        .setPayerAccountID(schedulePayer)
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setScheduleCreate(createTxnBody)
                .build();
    }
}
