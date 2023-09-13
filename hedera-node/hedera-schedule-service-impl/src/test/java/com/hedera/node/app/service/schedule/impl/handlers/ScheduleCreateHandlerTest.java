/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.handlers;

import static org.assertj.core.api.BDDAssertions.assertThat;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.impl.ScheduledTransactionFactory;
import com.hedera.node.app.spi.fixtures.Assertions;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

class ScheduleCreateHandlerTest extends ScheduleHandlerTestBase {
    private ScheduleCreateHandler subject;
    private PreHandleContext realPreContext;

    @BeforeEach
    void setUp() throws Exception {
        subject = new ScheduleCreateHandler();
        setUpBase();
    }

    @Test
    void preHandleVanilla() throws PreCheckException {
        realPreContext = new PreHandleContextImpl(
                mockStoreFactory, scheduleCreateTransaction(payer), testConfig, mockDispatcher);
        subject.preHandle(realPreContext);

        assertThat(realPreContext).isNotNull();
        assertThat(realPreContext.payerKey()).isNotNull().isEqualTo(schedulerKey);
        assertThat(realPreContext.requiredNonPayerKeys()).isNotNull().hasSize(1);
        assertThat(realPreContext.optionalNonPayerKeys()).isNotNull().hasSize(1);

        assertThat(realPreContext.requiredNonPayerKeys()).isEqualTo(Set.of(adminKey));
        assertThat(realPreContext.optionalNonPayerKeys()).isEqualTo(Set.of(payerKey));

        assertThat(mockContext).isNotNull();
    }

    @Test
    void preHandleVanillaNoAdmin() throws PreCheckException {
        final TransactionBody transactionToTest = ScheduledTransactionFactory.scheduleCreateTransactionWith(
                null, "", payer, scheduler, Timestamp.newBuilder().seconds(1L).build());
        realPreContext = new PreHandleContextImpl(mockStoreFactory, transactionToTest, testConfig, mockDispatcher);
        subject.preHandle(realPreContext);

        assertThat(realPreContext).isNotNull();
        assertThat(realPreContext.payerKey()).isNotNull().isEqualTo(schedulerKey);
        assertThat(realPreContext.requiredNonPayerKeys()).isNotNull().isEmpty();
        assertThat(realPreContext.optionalNonPayerKeys()).isNotNull().hasSize(1);

        assertThat(realPreContext.optionalNonPayerKeys()).isEqualTo(Set.of(payerKey));
    }

    @Test
    void preHandleUsesSamePayerIfScheduledPayerNotSet() throws PreCheckException {
        realPreContext =
                new PreHandleContextImpl(mockStoreFactory, scheduleCreateTransaction(null), testConfig, mockDispatcher);
        subject.preHandle(realPreContext);

        assertThat(realPreContext).isNotNull();
        assertThat(realPreContext.payerKey()).isNotNull().isEqualTo(schedulerKey);
        assertThat(realPreContext.requiredNonPayerKeys()).isNotNull().hasSize(1);
        assertThat(realPreContext.optionalNonPayerKeys()).isNotNull().isEmpty();

        assertThat(realPreContext.requiredNonPayerKeys()).isEqualTo(Set.of(adminKey));
    }

    @Test
    void preHandleMissingPayerSetsInvalidPayer() throws PreCheckException {
        BDDMockito.given(accountStore.getAccountById(payer)).willReturn(null);

        realPreContext = new PreHandleContextImpl(
                mockStoreFactory, scheduleCreateTransaction(payer), testConfig, mockDispatcher);
        Assertions.assertThrowsPreCheck(
                () -> subject.preHandle(realPreContext), ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID);
    }

    private TransactionBody scheduleCreateTransaction(final AccountID payer) {
        final Timestamp timestampValue =
                Timestamp.newBuilder().seconds(1_234_567L).build();
        return ScheduledTransactionFactory.scheduleCreateTransactionWith(
                adminKey, "test", payer, scheduler, timestampValue);
    }
}
