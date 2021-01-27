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

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.UsageEstimatorUtils;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.schedule.ScheduleCreateUsage;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class ScheduleCreateResourceUsageTest {

    ScheduleCreateResourceUsage subject;

    StateView view;
    ScheduleCreateUsage usage;
    BiFunction<TransactionBody, SigUsage, ScheduleCreateUsage> factory;
    TransactionBody nonScheduleCreateTxn;
    TransactionBody scheduleCreateTxn;

    int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
    GlobalDynamicProperties props = new MockGlobalDynamicProps();

    @BeforeEach
    private void setup() {
        view = mock(StateView.class);
        scheduleCreateTxn = mock(TransactionBody.class);
        given(scheduleCreateTxn.hasScheduleCreate()).willReturn(true);

        nonScheduleCreateTxn = mock(TransactionBody.class);
        given(nonScheduleCreateTxn.hasScheduleCreate()).willReturn(false);

        usage = mock(ScheduleCreateUsage.class);
        given(usage.givenScheduledTxExpirationTimeSecs(anyInt())).willReturn(usage);
        given(usage.get()).willReturn(MOCK_SCHEDULE_CREATE_USAGE);

        factory = (BiFunction<TransactionBody, SigUsage, ScheduleCreateUsage>)mock(BiFunction.class);
        given(factory.apply(scheduleCreateTxn, sigUsage)).willReturn(usage);

        ScheduleCreateResourceUsage.factory = factory;
        subject = new ScheduleCreateResourceUsage(props);
    }

    @Test
    public void recognizesApplicableQuery() {
        // expect:
        assertTrue(subject.applicableTo(scheduleCreateTxn));
        assertFalse(subject.applicableTo(nonScheduleCreateTxn));
    }

    @Test
    public void delegatesToCorrectEstimate() throws Exception {
        // expect:
        assertEquals(MOCK_SCHEDULE_CREATE_USAGE, subject.usageGiven(scheduleCreateTxn, obj, view));
    }

    private static final FeeData MOCK_SCHEDULE_CREATE_USAGE = UsageEstimatorUtils.defaultPartitioning(
            FeeComponents.newBuilder()
                    .setMin(1)
                    .setMax(1_000_000)
                    .setConstant(1)
                    .setBpt(1)
                    .setVpt(1)
                    .setRbh(1)
                    .setGas(1)
                    .setTv(1)
                    .setBpr(1)
                    .setSbpr(1)
                    .build(), 1);
}
