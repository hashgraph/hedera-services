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

package com.hedera.node.app.service.schedule.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
import static com.hedera.node.app.service.schedule.impl.test.ScheduledTxnFactory.scheduleCreateTxnWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleCreateHandlerTest extends ScheduleHandlerTestBase {

    @Test
    void preHandleScheduleCreateVanilla() {
        final var subject = new ScheduleCreateHandler();
        final var txn = scheduleCreateTransaction(payer);

        given(keyLookup.getKey(scheduler)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(keyLookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));

        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 1, false, OK);
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(List.of(adminKey), context.getRequiredNonPayerKeys());

        final var innerContext = context.getInnerContext();
        basicContextAssertions(innerContext, 0, false, OK);
        assertEquals(payer, innerContext.getPayer());
        assertEquals(payerKey, innerContext.getPayerKey());

        verify(dispatcher).dispatch(innerContext);
    }

    @Test
    void preHandleScheduleCreateVanillaNoAdmin() {
        final var subject = new ScheduleCreateHandler();
        final var txn = scheduleCreateTxnWith(
                null, "", payer, scheduler, Timestamp.newBuilder().seconds(1L).build());

        given(keyLookup.getKey(scheduler)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(keyLookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));

        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 0, false, OK);
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(List.of(), context.getRequiredNonPayerKeys());

        final var innerContext = context.getInnerContext();
        basicContextAssertions(innerContext, 0, false, OK);
        assertEquals(payer, innerContext.getPayer());
        assertEquals(payerKey, innerContext.getPayerKey());

        verify(dispatcher).dispatch(innerContext);
    }

    @Test
    void preHandleScheduleCreateFailsOnMissingPayer() {
        final var subject = new ScheduleCreateHandler();
        final var txn = scheduleCreateTransaction(payer);

        given(keyLookup.getKey(scheduler))
                .willReturn(KeyOrLookupFailureReason.withFailureReason(INVALID_PAYER_ACCOUNT_ID));
        given(keyLookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));

        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 0, true, INVALID_PAYER_ACCOUNT_ID);

        final var innerContext = context.getInnerContext();
        basicContextAssertions(innerContext, 0, false, OK);
        assertEquals(payer, innerContext.getPayer());
        assertEquals(payerKey, innerContext.getPayerKey());

        verify(dispatcher).dispatch(innerContext);
    }

    @Test
    void preHandleScheduleCreateUsesSamePayerIfScheduledPayerNotSet() {
        final var subject = new ScheduleCreateHandler();
        final var txn = scheduleCreateTransaction(null);
        given(keyLookup.getKey(scheduler)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));

        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 1, false, OK);
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(List.of(adminKey), context.getRequiredNonPayerKeys());

        final var innerContext = context.getInnerContext();
        basicContextAssertions(innerContext, 0, false, OK);
        assertEquals(scheduler, innerContext.getPayer());
        assertEquals(schedulerKey, innerContext.getPayerKey());

        verify(dispatcher).dispatch(innerContext);
    }

    @Test
    void failsWithScheduleTransactionsNotRecognized() {
        final var subject = new ScheduleCreateHandler();
        final var txn = scheduleTxnNotRecognized();

        given(keyLookup.getKey(scheduler)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));

        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 0, false, OK);
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(List.of(), context.getRequiredNonPayerKeys());

        final var innerContext = context.getInnerContext();
        basicContextAssertions(innerContext, 0, true, SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        assertEquals(scheduler, innerContext.getPayer());
        assertEquals(schedulerKey, innerContext.getPayerKey());

        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void innerTxnFailsSetsStatus() {
        final var subject = new ScheduleCreateHandler();
        final var txn = scheduleCreateTransaction(payer);

        given(keyLookup.getKey(scheduler)).willReturn(KeyOrLookupFailureReason.withKey(schedulerKey));
        given(keyLookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withFailureReason(INVALID_ACCOUNT_ID));

        final var context = new PreHandleContext(keyLookup, txn, scheduler);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 1, false, OK);
        assertEquals(schedulerKey, context.getPayerKey());
        assertEquals(List.of(adminKey), context.getRequiredNonPayerKeys());

        final var innerContext = context.getInnerContext();
        basicContextAssertions(innerContext, 0, true, UNRESOLVABLE_REQUIRED_SIGNERS);
        assertEquals(payer, innerContext.getPayer());
        assertNull(innerContext.getPayerKey());

        verify(dispatcher).dispatch(innerContext);
    }

    private TransactionBody scheduleCreateTransaction(final AccountID payer) {
        return scheduleCreateTxnWith(
                key,
                "test",
                payer,
                scheduler,
                Timestamp.newBuilder().seconds(1_234_567L).build());
    }
}
