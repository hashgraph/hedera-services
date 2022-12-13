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
package com.hedera.node.app.service.schedule.impl.test;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asOrdinary;
import static com.hedera.test.factories.txns.ScheduledTxnFactory.scheduleCreateTxnWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.service.schedule.SchedulePreTransactionHandler;
import com.hedera.node.app.service.schedule.impl.SchedulePreTransactionHandlerImpl;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.InvalidTransactionMetadata;
import com.hedera.node.app.spi.meta.SigTransactionMetadata;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulePreTransactionHandlerImplTest {
    private SchedulePreTransactionHandler subject;
    @Mock private AccountKeyLookup keyLookup;
    @Mock private HederaKey schedulerKey;
    private SigTransactionMetadata scheduledMeta;
    @Mock private PreHandleDispatcher dispatcher;

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
        subject = new SchedulePreTransactionHandlerImpl(keyLookup);
    }

    @Test
    void preHandleScheduleCreateVanilla() {
        final var txn = scheduleCreateTransaction(payer);
        final var scheduledTxn =
                asOrdinary(
                        txn.getScheduleCreate().getScheduledTransactionBody(),
                        txn.getTransactionID());
        scheduledMeta =
                new SigTransactionMetadata(
                        asOrdinary(
                                txn.getScheduleCreate().getScheduledTransactionBody(),
                                txn.getTransactionID()),
                        scheduler,
                        OK,
                        schedulerKey,
                        List.of());

        given(keyLookup.getKey(scheduler))
                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(dispatcher.dispatch(scheduledTxn, payer)).willReturn(scheduledMeta);

        final var meta = subject.preHandleCreateSchedule(txn, scheduler, dispatcher);

        basicMetaAssertions(meta, 1, false, OK);
        assertEquals(schedulerKey, meta.payerKey());
        assertEquals(List.of(adminKey), meta.requiredNonPayerKeys());
        assertEquals(scheduledMeta, meta.scheduledMeta());

        verify(dispatcher).dispatch(scheduledTxn, payer);
    }

    @Test
    void preHandleScheduleCreateVanillaNoAdmin() {
        final var txn =
                scheduleCreateTxnWith(
                        null,
                        "",
                        payer,
                        scheduler,
                        MiscUtils.asTimestamp(Instant.ofEpochSecond(1L)));
        final var scheduledTxn =
                asOrdinary(
                        txn.getScheduleCreate().getScheduledTransactionBody(),
                        txn.getTransactionID());
        scheduledMeta =
                new SigTransactionMetadata(
                        asOrdinary(
                                txn.getScheduleCreate().getScheduledTransactionBody(),
                                txn.getTransactionID()),
                        scheduler,
                        OK,
                        schedulerKey,
                        List.of());

        given(keyLookup.getKey(scheduler))
                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(dispatcher.dispatch(scheduledTxn, payer)).willReturn(scheduledMeta);

        final var meta = subject.preHandleCreateSchedule(txn, scheduler, dispatcher);

        basicMetaAssertions(meta, 0, false, OK);
        assertEquals(schedulerKey, meta.payerKey());
        assertEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(scheduledMeta, meta.scheduledMeta());

        verify(dispatcher).dispatch(scheduledTxn, payer);
    }

    @Test
    void preHandleScheduleCreateFailsOnMissingPayer() {
        givenSetup(payer);
        given(keyLookup.getKey(scheduler))
                .willReturn(KeyOrLookupFailureReason.withFailureReason(INVALID_PAYER_ACCOUNT_ID));
        scheduledMeta =
                new SigTransactionMetadata(
                        asOrdinary(
                                txn.getScheduleCreate().getScheduledTransactionBody(),
                                txn.getTransactionID()),
                        scheduler,
                        OK,
                        schedulerKey,
                        List.of());
        given(dispatcher.dispatch(scheduledTxn, payer)).willReturn(scheduledMeta);

        final var meta = subject.preHandleCreateSchedule(txn, scheduler, dispatcher);

        basicMetaAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
        assertEquals(scheduledMeta, meta.scheduledMeta());

        verify(dispatcher).dispatch(scheduledTxn, payer);
    }

    @Test
    void preHandleScheduleCreateUsesSamePayerIfScheduledPayerNotSet() {
        givenSetup(null);
        scheduledMeta =
                new SigTransactionMetadata(
                        asOrdinary(
                                txn.getScheduleCreate().getScheduledTransactionBody(),
                                txn.getTransactionID()),
                        scheduler,
                        OK,
                        schedulerKey,
                        List.of());
        given(dispatcher.dispatch(eq(scheduledTxn), any())).willReturn(scheduledMeta);
        given(keyLookup.getKey(scheduler))
                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));

        final var meta = subject.preHandleCreateSchedule(txn, scheduler, dispatcher);

        basicMetaAssertions(meta, 1, false, OK);
        assertEquals(schedulerKey, meta.payerKey());
        assertEquals(List.of(adminKey), meta.requiredNonPayerKeys());
        assertEquals(scheduledMeta, meta.scheduledMeta());

        verify(dispatcher).dispatch(scheduledTxn, scheduler);
    }

    @Test
    void failsWithScheduleTransactionsNotRecognized() {
        final var txn = scheduleTxnNotRecognized();
        final var scheduledTxn =
                asOrdinary(
                        txn.getScheduleCreate().getScheduledTransactionBody(),
                        txn.getTransactionID());
        scheduledMeta =
                new SigTransactionMetadata(
                        asOrdinary(
                                txn.getScheduleCreate().getScheduledTransactionBody(),
                                txn.getTransactionID()),
                        scheduler,
                        OK,
                        schedulerKey,
                        List.of());

        given(keyLookup.getKey(scheduler))
                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));

        final var meta = subject.preHandleCreateSchedule(txn, scheduler, dispatcher);

        basicMetaAssertions(meta, 1, false, OK);
        basicMetaAssertions(meta.scheduledMeta(), 1, true, SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        assertEquals(schedulerKey, meta.payerKey());
        assertEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(schedulerKey, meta.scheduledMeta().payerKey());
        assertEquals(List.of(), meta.scheduledMeta().requiredNonPayerKeys());

        verify(dispatcher, never()).dispatch(scheduledTxn, payer);
    }

    @Test
    void innerTxnFailsSetsStatus() {
        givenSetup(payer);
        scheduledMeta =
                new SigTransactionMetadata(
                        asOrdinary(
                                txn.getScheduleCreate().getScheduledTransactionBody(),
                                txn.getTransactionID()),
                        payer,
                        INVALID_ACCOUNT_ID,
                        schedulerKey,
                        List.of());
        given(dispatcher.dispatch(any(), any())).willReturn(scheduledMeta);

        final var meta = subject.preHandleCreateSchedule(txn, scheduler, dispatcher);

        basicMetaAssertions(meta, 2, false, OK);
        assertEquals(List.of(schedulerKey, adminKey), meta.requiredNonPayerKeys());

        verify(dispatcher).dispatch(scheduledTxn, payer);
        assertTrue(meta.scheduledMeta() instanceof InvalidTransactionMetadata);
        assertEquals(UNRESOLVABLE_REQUIRED_SIGNERS, meta.scheduledMeta().status());
        assertEquals(payer, meta.scheduledMeta().payer());
        assertEquals(List.of(), meta.scheduledMeta().requiredNonPayerKeys());
        assertEquals(
                asOrdinary(
                        txn.getScheduleCreate().getScheduledTransactionBody(),
                        txn.getTransactionID()),
                meta.scheduledMeta().txnBody());
    }

    private void givenSetup(final AccountID payer) {
        txn = scheduleCreateTransaction(payer);
        scheduledTxn =
                asOrdinary(
                        txn.getScheduleCreate().getScheduledTransactionBody(),
                        txn.getTransactionID());

        given(keyLookup.getKey(scheduler))
                .willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        lenient().when(dispatcher.dispatch(scheduledTxn, payer)).thenReturn(scheduledMeta);
    }

    private void basicMetaAssertions(
            final TransactionMetadata meta,
            final int keysSize,
            final boolean failed,
            final ResponseCodeEnum failureStatus) {
        assertEquals(keysSize, meta.requiredNonPayerKeys().size());
        assertTrue(failed ? meta.failed() : !meta.failed());
        assertEquals(failureStatus, meta.status());
    }

    private TransactionBody scheduleCreateTransaction(final AccountID payer) {
        return scheduleCreateTxnWith(
                key,
                "test",
                payer,
                scheduler,
                Timestamp.newBuilder().setSeconds(1_234_567L).build());
    }

    private TransactionBody scheduleTxnNotRecognized() {
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder().setAccountID(scheduler))
                .setScheduleCreate(
                        ScheduleCreateTransactionBody.newBuilder()
                                .setScheduledTransactionBody(
                                        SchedulableTransactionBody.newBuilder().build()))
                .build();
    }
}
