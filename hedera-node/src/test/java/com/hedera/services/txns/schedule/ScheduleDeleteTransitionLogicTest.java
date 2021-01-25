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
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ScheduleDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
public class ScheduleDeleteTransitionLogicTest {
    private OptionValidator validator;
    private ScheduleStore store;
    private HederaLedger ledger;
    private PlatformTxnAccessor accessor;
    private TransactionContext txnCtx;

    private ScheduleID schedule = IdUtils.asSchedule("1.2.3");

    private TransactionBody scheduleDeleteTxn;
    private ScheduleDeleteTransitionLogic subject;

    @BeforeEach
    private void setup() {
        validator = mock(OptionValidator.class);
        store = mock(ScheduleStore.class);
        ledger = mock(HederaLedger.class);
        accessor = mock(PlatformTxnAccessor.class);

        txnCtx = mock(TransactionContext.class);

        subject = new ScheduleDeleteTransitionLogic(validator, store, ledger, txnCtx);
    }

    @Test
    public void doStateTransitionIsUnsupported() {
        givenValidTxnCtx();

        // expect:
        assertThrows(UnsupportedOperationException.class, () -> subject.doStateTransition());
    }

    @Test
    public void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(scheduleDeleteTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    public void failsOnInvalidSchedule() {
        givenCtx(true);

        // expect:
        assertEquals(INVALID_SCHEDULE_ID, subject.validate(scheduleDeleteTxn));
    }

    @Test
    public void syntaxCheckWorks() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.syntaxCheck().apply(scheduleDeleteTxn));
    }

    private void givenValidTxnCtx() {
        givenCtx(false);
    }


    private void givenCtx(
            boolean invalidScheduleId
    ) {
        var builder = TransactionBody.newBuilder();
        var scheduleDelete = ScheduleDeleteTransactionBody.newBuilder()
                .setScheduleID(schedule);

        if (invalidScheduleId) {
            scheduleDelete.clearScheduleID();
        }

        builder.setScheduleDelete(scheduleDelete.build());

        scheduleDeleteTxn = builder.build();
        given(accessor.getTxn()).willReturn(scheduleDeleteTxn);
        given(txnCtx.accessor()).willReturn(accessor);
    }
}
