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
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.schedule.ScheduleOpsUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class ScheduleCreateResourceUsageTest {

    ScheduleCreateResourceUsage subject;

    StateView view;
    ScheduleOpsUsage scheduleOpsUsage;
    TransactionBody nonScheduleCreateTxn;
    TransactionBody scheduleCreateTxn;

    int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
    GlobalDynamicProperties props = new MockGlobalDynamicProps();
    FeeData expected;

    @BeforeEach
    private void setup() {
        expected = mock(FeeData.class);
        view = mock(StateView.class);
        scheduleCreateTxn = mock(TransactionBody.class);
        scheduleOpsUsage = mock(ScheduleOpsUsage.class);
        given(scheduleCreateTxn.hasScheduleCreate()).willReturn(true);
        given(scheduleCreateTxn.getScheduleCreate())
                .willReturn(ScheduleCreateTransactionBody.getDefaultInstance());

        nonScheduleCreateTxn = mock(TransactionBody.class);

        given(scheduleOpsUsage.scheduleCreateUsage(scheduleCreateTxn, sigUsage, props.scheduledTxExpiryTimeSecs()))
                .willReturn(expected);

        subject = new ScheduleCreateResourceUsage(props, scheduleOpsUsage);
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
        assertEquals(expected, subject.usageGiven(scheduleCreateTxn, obj, view));
    }
}
