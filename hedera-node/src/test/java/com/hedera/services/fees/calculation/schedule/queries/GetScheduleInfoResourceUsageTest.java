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
package com.hedera.services.fees.calculation.schedule.queries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.schedule.GetScheduleInfoAnswer;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.usage.schedule.ExtantScheduleContext;
import com.hedera.services.usage.schedule.ScheduleOpsUsage;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ScheduleGetInfoQuery;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleInfo;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GetScheduleInfoResourceUsageTest {
    private static final TransactionID scheduledTxnId =
            TransactionID.newBuilder()
                    .setScheduled(true)
                    .setAccountID(IdUtils.asAccount("0.0.2"))
                    .build();
    private static final ScheduleID target = IdUtils.asSchedule("0.0.123");
    private static final Instant resolutionTime = Instant.ofEpochSecond(123L);
    private static final Key signersList = TxnHandlingScenario.MISC_FILE_WACL_KT.asKey();
    private static final String memo = "some memo here";
    private static final Key adminKey = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey();
    private static final ScheduleInfo info =
            ScheduleInfo.newBuilder()
                    .setMemo(memo)
                    .setAdminKey(adminKey)
                    .setPayerAccountID(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT)
                    .setSigners(signersList.getKeyList())
                    .setScheduledTransactionID(scheduledTxnId)
                    .setDeletionTime(RichInstant.fromJava(resolutionTime).toGrpc())
                    .build();
    private static final Query scheduleInfoQuery =
            Query.newBuilder()
                    .setScheduleGetInfo(ScheduleGetInfoQuery.newBuilder().setScheduleID(target))
                    .build();

    private StateView view;
    private ScheduleOpsUsage scheduleOpsUsage;
    private FeeData expected;

    private GetScheduleInfoResourceUsage subject;

    @BeforeEach
    void setup() {
        view = mock(StateView.class);
        given(view.infoForSchedule(target)).willReturn(Optional.of(info));

        scheduleOpsUsage = mock(ScheduleOpsUsage.class);
        expected = mock(FeeData.class);

        subject = new GetScheduleInfoResourceUsage(scheduleOpsUsage);
    }

    @Test
    void recognizesApplicableQuery() {
        final var applicable = scheduleInfoQuery;
        final var inapplicable = Query.getDefaultInstance();

        assertTrue(subject.applicableTo(applicable));
        assertFalse(subject.applicableTo(inapplicable));
    }

    @Test
    void calculatesFeeData() {
        final var captor = ArgumentCaptor.forClass(ExtantScheduleContext.class);
        given(scheduleOpsUsage.scheduleInfoUsage(eq(scheduleInfoQuery), captor.capture()))
                .willReturn(expected);

        final var usage = subject.usageGiven(scheduleInfoQuery, view);

        verify(view).infoForSchedule(target);
        assertSame(expected, usage);

        final var ctx = captor.getValue();
        assertEquals(adminKey, ctx.adminKey());
        assertEquals(info.getSigners().getKeysCount(), ctx.numSigners());
        assertTrue(ctx.isResolved());
        assertEquals(info.getScheduledTransactionBody(), ctx.scheduledTxn());
        assertEquals(info.getMemo(), ctx.memo());
    }

    @Test
    void calculatesFeeDataScheduleNotPresent() {
        given(view.infoForSchedule(target)).willReturn(Optional.empty());

        final var usage = subject.usageGiven(scheduleInfoQuery, view);

        verify(view).infoForSchedule(target);
        assertSame(FeeData.getDefaultInstance(), usage);
    }

    @Test
    void calculatesFeeDataWithContext() {
        final var queryCtx = new HashMap<String, Object>();
        given(scheduleOpsUsage.scheduleInfoUsage(any(), any())).willReturn(expected);

        final var usage = subject.usageGiven(scheduleInfoQuery, view, queryCtx);

        assertSame(info, queryCtx.get(GetScheduleInfoAnswer.SCHEDULE_INFO_CTX_KEY));
        assertSame(expected, usage);
        verify(view).infoForSchedule(target);
    }

    @Test
    void onlySetsScheduleInfoInQueryCxtIfFound() {
        final var queryCtx = new HashMap<String, Object>();
        given(view.infoForSchedule(target)).willReturn(Optional.empty());

        final var usage = subject.usageGiven(scheduleInfoQuery, view, queryCtx);

        assertFalse(queryCtx.containsKey(GetScheduleInfoAnswer.SCHEDULE_INFO_CTX_KEY));
        assertSame(FeeData.getDefaultInstance(), usage);
    }

    @Test
    void calculatesFeeDataForScheduleMissingAdminKeyAndUnresolved() {
        final var differentInfo = info.toBuilder().clearAdminKey().clearDeletionTime().build();
        given(view.infoForSchedule(target)).willReturn(Optional.of(differentInfo));
        final var captor = ArgumentCaptor.forClass(ExtantScheduleContext.class);
        given(scheduleOpsUsage.scheduleInfoUsage(eq(scheduleInfoQuery), captor.capture()))
                .willReturn(expected);

        final var usage = subject.usageGiven(scheduleInfoQuery, view);

        verify(view).infoForSchedule(target);
        assertSame(expected, usage);

        final var ctx = captor.getValue();
        assertNull(ctx.adminKey());
        assertFalse(ctx.isResolved());
    }
}
