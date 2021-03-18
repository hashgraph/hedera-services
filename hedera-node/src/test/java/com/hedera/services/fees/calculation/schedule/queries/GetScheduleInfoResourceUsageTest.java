package com.hedera.services.fees.calculation.schedule.queries;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class GetScheduleInfoResourceUsageTest {
    TransactionID scheduledTxnId = TransactionID.newBuilder()
            .setScheduled(true)
            .setAccountID(IdUtils.asAccount("0.0.2"))
            .build();
    ScheduleID target = IdUtils.asSchedule("0.0.123");

    Instant resolutionTime = Instant.ofEpochSecond(123L);
    Key signersList = TxnHandlingScenario.MISC_FILE_WACL_KT.asKey();
    String memo = "some memo here";
    Key adminKey = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey();
    ScheduleInfo info = ScheduleInfo.newBuilder()
            .setMemo(memo)
            .setAdminKey(adminKey)
            .setPayerAccountID(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT)
            .setSigners(signersList.getKeyList())
			.setScheduledTransactionID(scheduledTxnId)
            .setDeletionTime(RichInstant.fromJava(resolutionTime).toGrpc())
            .build();

    StateView view;
    GetScheduleInfoResourceUsage subject;
    ScheduleOpsUsage scheduleOpsUsage;
    FeeData expected;

    @BeforeEach
    private void setup() throws Throwable {
        view = mock(StateView.class);
        given(view.infoForSchedule(target)).willReturn(Optional.of(info));

        scheduleOpsUsage = mock(ScheduleOpsUsage.class);

        expected = mock(FeeData.class);

        subject = new GetScheduleInfoResourceUsage(scheduleOpsUsage);
    }

    @Test
    public void recognizesApplicableQuery() {
        // given:
        var applicable = scheduleInfoQuery(target);
        var inapplicable = Query.getDefaultInstance();

        // expect:
        assertTrue(subject.applicableTo(applicable));
        assertFalse(subject.applicableTo(inapplicable));
    }

    @Test
    public void calculatesFeeData() {
        ArgumentCaptor<ExtantScheduleContext> captor = ArgumentCaptor.forClass(ExtantScheduleContext.class);

        // setup:
        var query = scheduleInfoQuery(target);

        given(scheduleOpsUsage.scheduleInfoUsage(eq(query), captor.capture())).willReturn(expected);

        // when:
        var usage = subject.usageGiven(query, view);

        // then:
        verify(view).infoForSchedule(target);
        assertSame(expected, usage);
        // and:
        var ctx = captor.getValue();
        assertEquals(adminKey, ctx.adminKey());
        assertEquals(info.getSigners().getKeysCount(), ctx.numSigners());
        assertTrue(ctx.isResolved());
        assertEquals(info.getScheduledTransactionBody(), ctx.scheduledTxn());
        assertEquals(info.getMemo(), ctx.memo());
    }

    @Test
    public void calculatesFeeDataScheduleNotPresent() {
        // given:
        given(view.infoForSchedule(target)).willReturn(Optional.empty());
        // when:
        var usage = subject.usageGiven(scheduleInfoQuery(target), view);

        // then:
        verify(view).infoForSchedule(target);
        assertSame(FeeData.getDefaultInstance(), usage);
    }

    @Test
    public void calculatesFeeDataWithContext() {
        // setup:
        var queryCtx = new HashMap<String, Object>();

        given(scheduleOpsUsage.scheduleInfoUsage(any(), any())).willReturn(expected);

        // when
        var usage = subject.usageGiven(scheduleInfoQuery(target), view, queryCtx);

        // then
        assertSame(info, queryCtx.get(GetScheduleInfoAnswer.SCHEDULE_INFO_CTX_KEY));
        assertSame(expected, usage);
        verify(view).infoForSchedule(target);
    }

    @Test
    public void onlySetsScheduleInfoInQueryCxtIfFound() {
        // setup:
        var queryCtx = new HashMap<String, Object>();

        given(view.infoForSchedule(target)).willReturn(Optional.empty());

        // when:
        var usage = subject.usageGiven(scheduleInfoQuery(target), view, queryCtx);

        // then:
        assertFalse(queryCtx.containsKey(GetScheduleInfoAnswer.SCHEDULE_INFO_CTX_KEY));
        // and:
        assertSame(FeeData.getDefaultInstance(), usage);
    }

    private Query scheduleInfoQuery(ScheduleID id) {
        return Query.newBuilder()
                .setScheduleGetInfo(ScheduleGetInfoQuery.newBuilder()
                        .setScheduleID(id))
                .build();
    }
}
