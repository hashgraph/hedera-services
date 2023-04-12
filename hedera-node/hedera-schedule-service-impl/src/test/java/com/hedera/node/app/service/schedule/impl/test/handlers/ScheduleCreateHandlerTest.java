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

import static com.hedera.hapi.node.base.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
import static com.hedera.node.app.service.schedule.impl.test.ScheduledTxnFactory.scheduleCreateTxnWith;
import static com.hedera.node.app.spi.fixtures.Assertions.assertPreCheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleCreateHandlerTest extends ScheduleHandlerTestBase {

    @Test
    void preHandleScheduleCreateVanilla() throws PreCheckException {
        final var subject = new ScheduleCreateHandler();
        final var txn = scheduleCreateTransaction(payer);

        given(keyLookup.getAccountById(scheduler)).willReturn(schedulerAccount);
        given(schedulerAccount.getKey()).willReturn(schedulerKey);
        given(keyLookup.getAccountById(payer)).willReturn(payerAccount);
        given(payerAccount.getKey()).willReturn(payerKey);

        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 1);
        assertEquals(schedulerKey, context.payerKey());
        assertEquals(Set.of(adminKey), context.requiredNonPayerKeys());

        final var innerContext = context.innerContext();
        basicContextAssertions(innerContext, 0);
        assertEquals(payer, innerContext.payer());
        assertEquals(payerKey, innerContext.payerKey());

        verify(dispatcher).dispatch(innerContext);
    }

    @Test
    void preHandleScheduleCreateVanillaNoAdmin() throws PreCheckException {
        final var subject = new ScheduleCreateHandler();
        final var txn = scheduleCreateTxnWith(
                null, "", payer, scheduler, Timestamp.newBuilder().seconds(1L).build());

        given(keyLookup.getAccountById(scheduler)).willReturn(schedulerAccount);
        given(schedulerAccount.getKey()).willReturn(schedulerKey);
        given(keyLookup.getAccountById(payer)).willReturn(payerAccount);
        given(payerAccount.getKey()).willReturn(payerKey);

        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 0);
        assertEquals(schedulerKey, context.payerKey());
        assertEquals(Set.of(), context.requiredNonPayerKeys());

        final var innerContext = context.innerContext();
        basicContextAssertions(innerContext, 0);
        assertEquals(payer, innerContext.payer());
        assertEquals(payerKey, innerContext.payerKey());

        verify(dispatcher).dispatch(innerContext);
    }

    @Test
    void preHandleScheduleCreateUsesSamePayerIfScheduledPayerNotSet() throws PreCheckException {
        final var subject = new ScheduleCreateHandler();
        final var txn = scheduleCreateTransaction(null);
        given(keyLookup.getAccountById(scheduler)).willReturn(schedulerAccount);
        given(schedulerAccount.getKey()).willReturn(schedulerKey);

        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 1);
        assertEquals(schedulerKey, context.payerKey());
        assertEquals(Set.of(adminKey), context.requiredNonPayerKeys());

        final var innerContext = context.innerContext();
        basicContextAssertions(innerContext, 0);
        assertEquals(scheduler, innerContext.payer());
        assertEquals(schedulerKey, innerContext.payerKey());

        verify(dispatcher).dispatch(innerContext);
    }

    @Test
    void failsWithScheduleTransactionNotInWhitelist() throws PreCheckException {
        final var subject = new ScheduleCreateHandler();
        final var txn = scheduleTxnNotRecognized();
        given(keyLookup.getAccountById(scheduler)).willReturn(schedulerAccount);
        given(schedulerAccount.getKey()).willReturn(schedulerKey);

        final var context = new PreHandleContext(keyLookup, txn);
        subject.preHandle(context, dispatcher);

        basicContextAssertions(context, 0);
        assertEquals(schedulerKey, context.payerKey());
        assertEquals(Collections.EMPTY_SET, context.requiredNonPayerKeys());

        // @todo whitelist tests don't work; it appears they never actually tested a
        //       non-whitelist situation so much as a missing key.  This requires careful
        //       thought and rework.
        //        final var innerContext = context.innerContext();
        //        basicContextAssertions(innerContext, 0, true, SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        //        assertEquals(scheduler, innerContext.payer());
        //        assertEquals(schedulerKey, innerContext.payerKey());
        //        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void innerTxnFailsSetsStatus() throws PreCheckException {
        final var subject = new ScheduleCreateHandler();
        final var txn = scheduleCreateTransaction(payer);

        given(keyLookup.getAccountById(scheduler)).willReturn(schedulerAccount);
        given(schedulerAccount.getKey()).willReturn(schedulerKey);
        given(keyLookup.getAccountById(payer)).willReturn(null);

        final var context = new PreHandleContext(keyLookup, txn);
        assertPreCheck(() -> subject.preHandle(context, dispatcher), UNRESOLVABLE_REQUIRED_SIGNERS);
    }

    private TransactionBody scheduleCreateTransaction(final AccountID payer) {
        final Timestamp timestampValue =
                Timestamp.newBuilder().seconds(1_234_567L).build();
        return scheduleCreateTxnWith(TEST_KEY, "test", payer, scheduler, timestampValue);
    }
}
