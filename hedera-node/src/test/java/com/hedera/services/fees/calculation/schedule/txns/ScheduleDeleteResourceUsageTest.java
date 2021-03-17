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
import com.hedera.services.usage.schedule.ScheduleOpsUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class ScheduleDeleteResourceUsageTest {
    ScheduleDeleteResourceUsage subject;

    StateView view;
    ScheduleOpsUsage scheduleOpsUsage;

    long expiry = 1_234_567L;
    int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
    FeeData expected;

    TransactionBody nonScheduleDeleteTxn;
    TransactionBody scheduleDeleteTxn;

    @BeforeEach
    private void setup() {
        expected = mock(FeeData.class);
        view = mock(StateView.class);
        scheduleDeleteTxn = mock(TransactionBody.class);
        given(scheduleDeleteTxn.hasScheduleDelete()).willReturn(true);

        nonScheduleDeleteTxn = mock(TransactionBody.class);
        given(nonScheduleDeleteTxn.hasScheduleDelete()).willReturn(false);

        scheduleOpsUsage = mock(ScheduleOpsUsage.class);
        given(scheduleOpsUsage.scheduleDeleteUsage(scheduleDeleteTxn, sigUsage, expiry)).willReturn(expected);

        subject = new ScheduleDeleteResourceUsage(scheduleOpsUsage);
    }

    @Test
    public void recognizesApplicableQuery() {
        // expect:
        assertTrue(subject.applicableTo(scheduleDeleteTxn));
        assertFalse(subject.applicableTo(nonScheduleDeleteTxn));
    }

    @Test
    public void delegatesToCorrectEstimate() throws Exception {
        // expect:
        assertEquals(expected, subject.usageGiven(scheduleDeleteTxn, obj, view));
    }
}
