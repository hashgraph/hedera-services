package com.hedera.services.fees.calculation.schedule.txns;

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
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.schedule.ScheduleSignUsage;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleInfo;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ScheduleSignResourceUsageTest {
    ScheduleSignResourceUsage subject;
    StateView view;
    ScheduleSignUsage usage;
    BiFunction<TransactionBody, SigUsage, ScheduleSignUsage> factory;
    TransactionBody nonScheduleSignTxn;
    TransactionBody scheduleSignTxn;
    long expiry = 2_345_678L;

    int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    ScheduleID target = IdUtils.asSchedule("0.0.123");
    SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
    FeeData expected;

    ScheduleInfo info = ScheduleInfo.newBuilder()
            .setExpirationTime(Timestamp.newBuilder().setSeconds(expiry))
            .build();

    @BeforeEach
    private void setup() {
        expected = mock(FeeData.class);
        view = mock(StateView.class);
        scheduleSignTxn = mock(TransactionBody.class);
        given(scheduleSignTxn.hasScheduleSign()).willReturn(true);
        given(scheduleSignTxn.getScheduleSign())
                .willReturn(ScheduleSignTransactionBody.newBuilder()
                        .setScheduleID(target)
                        .build());

        nonScheduleSignTxn = mock(TransactionBody.class);
        given(nonScheduleSignTxn.hasScheduleSign()).willReturn(false);

        usage = mock(ScheduleSignUsage.class);
        given(usage.givenExpiry(anyLong())).willReturn(usage);
        given(usage.get()).willReturn(expected);

        factory = (BiFunction<TransactionBody, SigUsage, ScheduleSignUsage>)mock(BiFunction.class);
        given(factory.apply(scheduleSignTxn, sigUsage)).willReturn(usage);

        given(view.infoForSchedule(target)).willReturn(Optional.of(info));

        ScheduleSignResourceUsage.factory = factory;
        subject = new ScheduleSignResourceUsage();
    }

    @Test
    public void recognizesApplicableQuery() {
        // expect:
        assertTrue(subject.applicableTo(scheduleSignTxn));
        assertFalse(subject.applicableTo(nonScheduleSignTxn));
    }

    @Test
    public void delegatesToCorrectEstimate() throws Exception {
        // expect:
        assertEquals(expected, subject.usageGiven(scheduleSignTxn, obj, view));
    }

    @Test
    public void returnsDefaultIfInfoMissing() throws Exception {
        given(view.infoForSchedule(target)).willReturn(Optional.empty());

        // expect:
        assertEquals(
                FeeData.getDefaultInstance(),
                subject.usageGiven(scheduleSignTxn, obj, view));
        // and:
        verify(factory).apply(scheduleSignTxn, sigUsage);
        verify(scheduleSignTxn).getScheduleSign();
        verify(view).infoForSchedule(target);
    }
}
