/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.schedule.impl.test;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asOrdinary;
import static com.hedera.test.factories.txns.ScheduledTxnFactory.scheduleCreateTxnWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.schedule.SchedulePreTransactionHandler;
import com.hedera.node.app.service.schedule.impl.SchedulePreTransactionHandlerImpl;
import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.PreHandleTxnAccessor;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadata;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulePreTransactionHandlerImplTest {
    private SchedulePreTransactionHandler subject;
    @Mock private CryptoPreTransactionHandler scheduledTxnHandler;
    @Mock private SchedulePreTransactionHandler invalidScheduledPreTxnHandler;
    @Mock private AccountKeyLookup keyLookup;
    @Mock private PreHandleTxnAccessor accessor;
    @Mock private HederaKey payerKey;
    @Mock private SigTransactionMetadata scheduledMeta;

    private AccountID scheduler = AccountID.newBuilder().setAccountNum(1001L).build();
    private AccountID payer = AccountID.newBuilder().setAccountNum(2001L).build();
    private TransactionBody txn;
    private TransactionBody scheduledTxn;

    private Key key =
            Key.newBuilder()
                    .setEd25519(ByteString.copyFromUtf8("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                    .build();
    private HederaKey adminKey = asHederaKey(key).get();

    @BeforeEach
    void setUp() {
        given(accessor.getAccountKeyLookup()).willReturn(keyLookup);
        subject = new SchedulePreTransactionHandlerImpl(accessor);
    }

    @Test
    void preHandleDelegatesCorrectly() {
        final var txn = scheduleCreateTransaction(scheduler);
        final var subject = mock(SchedulePreTransactionHandlerImpl.class);

        willCallRealMethod().given(subject).preHandle(any(), any());

        subject.preHandle(txn, payer);
        verify(subject).preHandleCreateSchedule(txn, payer);
    }

    @Test
    void preHandleScheduleCreateVanilla() {
        final var txn = scheduleCreateTransaction(scheduler);
        final var scheduledTxn =
                asOrdinary(
                        txn.getScheduleCreate().getScheduledTransactionBody(),
                        txn.getTransactionID());

        given(keyLookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));
        given(accessor.getPreTxnHandler(HederaFunctionality.CryptoDelete))
                .willReturn(scheduledTxnHandler);
        given(scheduledTxnHandler.preHandle(scheduledTxn, scheduler)).willReturn(scheduledMeta);
        given(scheduledMeta.failed()).willReturn(false);

        final var meta = subject.preHandleCreateSchedule(txn, payer);

        basicMetaAssertions(meta, 2, false, OK);
        assertEquals(List.of(payerKey, adminKey), meta.getReqKeys());
        assertEquals(scheduledMeta, meta.getScheduledMeta());

        verify(scheduledTxnHandler).preHandle(scheduledTxn, scheduler);
    }

    @Test
    void preHandleScheduleCreateFailsOnMissingPayer() {
        givenSetup(scheduler);
        given(keyLookup.getKey(payer))
                .willReturn(KeyOrLookupFailureReason.withFailureReason(INVALID_PAYER_ACCOUNT_ID));
        given(scheduledMeta.failed()).willReturn(false);

        final var meta = subject.preHandleCreateSchedule(txn, payer);

        basicMetaAssertions(meta, 1, true, INVALID_PAYER_ACCOUNT_ID);
        assertEquals(List.of(adminKey), meta.getReqKeys());
        assertEquals(scheduledMeta, meta.getScheduledMeta());

        verify(scheduledTxnHandler).preHandle(scheduledTxn, scheduler);
    }

    @Test
    void preHandleScheduleCreateUsesSamePayerIfScheduledPayerNotSet() {
        givenSetup(null);
        given(scheduledTxnHandler.preHandle(scheduledTxn, payer)).willReturn(scheduledMeta);
        given(scheduledMeta.failed()).willReturn(false);

        final var meta = subject.preHandleCreateSchedule(txn, payer);

        basicMetaAssertions(meta, 2, false, OK);
        assertEquals(List.of(payerKey, adminKey), meta.getReqKeys());
        assertEquals(scheduledMeta, meta.getScheduledMeta());

        verify(scheduledTxnHandler).preHandle(scheduledTxn, payer);
    }

    @Test
    void failsWithScheduleTransactionsNotRecognized() {
        final var txn = scheduleTxnNotRecognized();
        final var scheduledTxn =
                asOrdinary(
                        txn.getScheduleCreate().getScheduledTransactionBody(),
                        txn.getTransactionID());

        given(keyLookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));

        final var meta = subject.preHandleCreateSchedule(txn, payer);

        basicMetaAssertions(meta, 1, false, OK);
        basicMetaAssertions(
                meta.getScheduledMeta(), 1, true, SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        assertEquals(List.of(payerKey), meta.getReqKeys());
        assertEquals(List.of(payerKey), meta.getScheduledMeta().getReqKeys());

        verify(scheduledTxnHandler, never()).preHandle(scheduledTxn, payer);
    }

    @Test
    void innerTxnFailsSetsStatus() {
        givenSetup(scheduler);
        given(scheduledMeta.failed()).willReturn(true);

        final var meta = subject.preHandleCreateSchedule(txn, payer);

        basicMetaAssertions(meta, 2, false, OK);
        assertEquals(List.of(payerKey, adminKey), meta.getReqKeys());

        verify(invalidScheduledPreTxnHandler, never()).preHandle(scheduledTxn, payer);
        verify(scheduledMeta).setStatus(UNRESOLVABLE_REQUIRED_SIGNERS);

        assertEquals(scheduledMeta, meta.getScheduledMeta());
    }

    private void givenSetup(final AccountID scheduler) {
        txn = scheduleCreateTransaction(scheduler);
        scheduledTxn =
                asOrdinary(
                        txn.getScheduleCreate().getScheduledTransactionBody(),
                        txn.getTransactionID());

        given(keyLookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));
        given(accessor.getPreTxnHandler(HederaFunctionality.CryptoDelete))
                .willReturn(scheduledTxnHandler);
        lenient()
                .when(scheduledTxnHandler.preHandle(scheduledTxn, scheduler))
                .thenReturn(scheduledMeta);
    }

    private void basicMetaAssertions(
            final TransactionMetadata meta,
            final int keysSize,
            final boolean failed,
            final ResponseCodeEnum failureStatus) {
        assertEquals(keysSize, meta.getReqKeys().size());
        assertTrue(failed ? meta.failed() : !meta.failed());
        assertEquals(failureStatus, meta.status());
    }

    private TransactionBody scheduleCreateTransaction(final AccountID scheduler) {
        return scheduleCreateTxnWith(
                key,
                "test",
                scheduler,
                payer,
                Timestamp.newBuilder().setSeconds(1_234_567L).build());
    }

    private TransactionBody scheduleTxnNotRecognized() {
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder().setAccountID(payer))
                .setScheduleCreate(
                        ScheduleCreateTransactionBody.newBuilder()
                                .setScheduledTransactionBody(
                                        SchedulableTransactionBody.newBuilder().build()))
                .build();
    }
}
