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

package com.hedera.node.app.service.schedule.impl.test.handlers;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.ScheduleDeleteTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.spi.fixtures.Assertions;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import java.security.InvalidKeyException;
import java.util.Set;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

class ScheduleDeleteHandlerTest extends ScheduleHandlerTestBase {
    private final AccountID scheduleDeleter =
            AccountID.newBuilder().accountNum(3001L).build();

    private ScheduleDeleteHandler subject;
    private PreHandleContext realPreContext;

    @BeforeEach
    void setUp() throws Exception {
        setUpBase();
        subject = new ScheduleDeleteHandler();
        BDDMockito.given(accountStore.getAccountById(scheduleDeleter)).willReturn(payerAccount);
    }

    @Test
    void preHandleHappyPath() throws InvalidKeyException, PreCheckException {
        realPreContext =
                new PreHandleContextImpl(mockStoreFactory, scheduleDeleteTransaction(), testConfig, mockDispatcher);

        subject.preHandle(realPreContext);
        BDDAssertions.assertThat(scheduleDeleter).isEqualTo(realPreContext.payer());
        BDDAssertions.assertThat(Set.of()).isNotEqualTo(realPreContext.requiredNonPayerKeys());
    }

    @Test
    // when schedule id to delete is not found, fail with INVALID_SCHEDULE_ID
    void failsIfScheduleMissing() throws PreCheckException {
        final TransactionBody schedule = scheduleDeleteTransaction();
        realPreContext = new PreHandleContextImpl(mockStoreFactory, schedule, testConfig, mockDispatcher);
        BDDMockito.given(schedulesById.get(testScheduleID)).willReturn(null);

        Assertions.assertThrowsPreCheck(() -> subject.preHandle(realPreContext), ResponseCodeEnum.INVALID_SCHEDULE_ID);
    }

    @Test
    // when admin key not set in scheduled tx, fail with SCHEDULE_IS_IMMUTABLE
    void failsIfScheduleIsImmutable() throws PreCheckException {
        final TransactionBody schedule = scheduleDeleteTransaction();
        realPreContext = new PreHandleContextImpl(mockStoreFactory, schedule, testConfig, mockDispatcher);
        BDDMockito.given(scheduleInState.adminKey()).willReturn(null);

        Assertions.assertThrowsPreCheck(
                () -> subject.preHandle(realPreContext), ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE);
    }

    private TransactionBody scheduleDeleteTransaction() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduleDeleter))
                .scheduleDelete(ScheduleDeleteTransactionBody.newBuilder().scheduleID(testScheduleID))
                .build();
    }
}
