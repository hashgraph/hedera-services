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

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.schedule.GetScheduleInfoAnswer;
import com.hedera.services.usage.schedule.ScheduleGetInfoUsage;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.ScheduleGetInfoQuery;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleInfo;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class GetScheduleInfoResourceUsageTest {
    TransactionID scheduledTxnId = TransactionID.newBuilder()
            .setScheduled(true)
            .setAccountID(IdUtils.asAccount("0.0.2"))
            .build();
    ScheduleID target = IdUtils.asSchedule("0.0.123");

    Key randomKey = new KeyFactory().newEd25519();
    ScheduleInfo info = ScheduleInfo.newBuilder()
            .setTransactionBody(ByteString.copyFrom(new byte[]{0x01, 0x02, 0x03, 0x04}))
            .setMemo("some memo here")
            .setAdminKey(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey())
            .setPayerAccountID(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT)
            .setSignatories(KeyList.newBuilder().addKeys(randomKey))
			.setScheduledTransactionID(scheduledTxnId)
            .setExecuted(true)
            .setDeleted(false)
            .build();

    StateView view;
    GetScheduleInfoResourceUsage subject;
    ScheduleGetInfoUsage estimator;
    Function<Query, ScheduleGetInfoUsage> factory;
    FeeData expected;

    @BeforeEach
    private void setup() throws Throwable {
        view = mock(StateView.class);
        estimator = mock(ScheduleGetInfoUsage.class);
        factory = mock(Function.class);
        given(factory.apply(any())).willReturn(estimator);
        given(view.infoForSchedule(target)).willReturn(Optional.of(info));

        expected = mock(FeeData.class);

        given(estimator.givenScheduledTxn(any())).willReturn(estimator);
        given(estimator.givenMemo(info.getMemoBytes())).willReturn(estimator);
        given(estimator.givenCurrentAdminKey(any())).willReturn(estimator);
        given(estimator.givenSignatories(any())).willReturn(estimator);
        given(estimator.givenScheduledTxnId(any())).willReturn(estimator);
        given(estimator.givenDeleted(anyBoolean())).willReturn(estimator);
        given(estimator.givenExecuted(anyBoolean())).willReturn(estimator);
        given(estimator.get()).willReturn(expected);

        GetScheduleInfoResourceUsage.factory = factory;
        subject = new GetScheduleInfoResourceUsage();
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
        // when
        var usage = subject.usageGiven(scheduleInfoQuery(target), view);

        // then
        verify(view).infoForSchedule(target);
        verify(estimator).givenScheduledTxn(any());
        verify(estimator).givenCurrentAdminKey(Optional.of(info.getAdminKey()));
        verify(estimator).givenSignatories(Optional.of(info.getSignatories()));
        verify(estimator).givenScheduledTxnId(scheduledTxnId);
        verify(estimator).givenDeleted(false);
        verify(estimator).givenExecuted(true);
        assertSame(expected, usage);
    }

    @Test
    public void calculatesFeeDataScheduleNotPresent() {
        // given
        given(view.infoForSchedule(target)).willReturn(Optional.empty());
        // when
        var usage = subject.usageGiven(scheduleInfoQuery(target), view);

        // then
        verify(view).infoForSchedule(target);
        verify(estimator, never()).givenScheduledTxn(any());
        verify(estimator, never()).givenCurrentAdminKey(any());
        verify(estimator, never()).givenSignatories(any());
        assertSame(FeeData.getDefaultInstance(), usage);
    }

    @Test
    public void calculatesFeeDataWithType() {
        // when
        var usage = subject.usageGivenType(scheduleInfoQuery(target), view, ResponseType.ANSWER_STATE_PROOF);

        // then
        verify(view).infoForSchedule(target);
        verify(estimator).givenScheduledTxn(any());
        verify(estimator).givenCurrentAdminKey(Optional.of(info.getAdminKey()));
        verify(estimator).givenSignatories(Optional.of(info.getSignatories()));
        assertSame(expected, usage);
    }

    @Test
    public void calculatesFeeDataWithContext() {
        // setup:
        var queryCtx = new HashMap<String, Object>();

        // when
        var usage = subject.usageGiven(scheduleInfoQuery(target), view, queryCtx);

        // then
        assertSame(info, queryCtx.get(GetScheduleInfoAnswer.SCHEDULE_INFO_CTX_KEY));
        assertSame(expected, usage);
        verify(view).infoForSchedule(target);
        verify(estimator).givenScheduledTxn(any());
        verify(estimator).givenCurrentAdminKey(Optional.of(info.getAdminKey()));
        verify(estimator).givenSignatories(Optional.of(info.getSignatories()));
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
