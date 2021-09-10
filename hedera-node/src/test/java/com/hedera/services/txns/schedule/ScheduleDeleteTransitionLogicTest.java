package com.hedera.services.txns.schedule;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.TransactionContext;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ScheduleDeleteTransitionLogicTest {
    private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);

    private ScheduleStore store;
    private PlatformTxnAccessor accessor;
    private TransactionContext txnCtx;
    private final ResponseCodeEnum NOT_OK = null;

    private final ScheduleID schedule = IdUtils.asSchedule("1.2.3");

    private TransactionBody scheduleDeleteTxn;
    private ScheduleDeleteTransitionLogic subject;

    @BeforeEach
    private void setup() {
        store = mock(ScheduleStore.class);
        accessor = mock(PlatformTxnAccessor.class);
        txnCtx = mock(TransactionContext.class);
        subject = new ScheduleDeleteTransitionLogic(store, txnCtx);
    }

    @Test
    void followsHappyPath() {
        givenValidTxnCtx();
        given(store.deleteAt(schedule, consensusNow)).willReturn(OK);

        subject.doStateTransition();

        verify(store).deleteAt(schedule, consensusNow);
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    void capturesInvalidSchedule() {
        givenValidTxnCtx();
        given(store.deleteAt(schedule, consensusNow)).willReturn(NOT_OK);

        subject.doStateTransition();

        verify(store).deleteAt(schedule, consensusNow);
        verify(txnCtx).setStatus(NOT_OK);
    }

    @Test
    void setsFailInvalidIfUnhandledException() {
        givenValidTxnCtx();
        given(store.deleteAt(schedule, consensusNow)).willThrow(IllegalArgumentException.class);

        subject.doStateTransition();

        verify(store).deleteAt(schedule, consensusNow);
        verify(txnCtx).setStatus(FAIL_INVALID);
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        assertTrue(subject.applicability().test(scheduleDeleteTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void failsOnInvalidSchedule() {
        givenCtx(true);

        assertEquals(INVALID_SCHEDULE_ID, subject.validate(scheduleDeleteTxn));
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        assertEquals(OK, subject.semanticCheck().apply(scheduleDeleteTxn));
    }

    @Test
    void rejectsInvalidScheduleId() {
        givenCtx(true);

        assertEquals(INVALID_SCHEDULE_ID, subject.semanticCheck().apply(scheduleDeleteTxn));
    }

    private void givenValidTxnCtx() {
        givenCtx(false);
    }

    private void givenCtx(
            boolean invalidScheduleId
    ) {
        final var builder = TransactionBody.newBuilder();
        final var scheduleDelete = ScheduleDeleteTransactionBody.newBuilder()
                .setScheduleID(schedule);

        if (invalidScheduleId) {
            scheduleDelete.clearScheduleID();
        }

        builder.setScheduleDelete(scheduleDelete);

        scheduleDeleteTxn = builder.build();
        given(accessor.getTxn()).willReturn(scheduleDeleteTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(consensusNow);
    }
}
