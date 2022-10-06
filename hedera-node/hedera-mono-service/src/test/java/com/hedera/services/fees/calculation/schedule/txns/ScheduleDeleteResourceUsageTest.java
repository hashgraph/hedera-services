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
package com.hedera.services.fees.calculation.schedule.txns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.schedule.ScheduleOpsUsage;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.ScheduleDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleInfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.SigValueObj;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleDeleteResourceUsageTest {
    TransactionID scheduledTxnId =
            TransactionID.newBuilder()
                    .setScheduled(true)
                    .setAccountID(IdUtils.asAccount("0.0.2"))
                    .build();

    ScheduleDeleteResourceUsage subject;

    StateView view;
    ScheduleOpsUsage scheduleOpsUsage;

    long expiry = 1_234_567L;
    int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    ScheduleID target = IdUtils.asSchedule("0.0.123");
    SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
    FeeData expected;

    TransactionBody nonScheduleDeleteTxn;
    TransactionBody scheduleDeleteTxn;

    ScheduleInfo info =
            ScheduleInfo.newBuilder()
                    .setScheduledTransactionID(scheduledTxnId)
                    .setExpirationTime(Timestamp.newBuilder().setSeconds(expiry))
                    .build();

    @BeforeEach
    void setup() {
        expected = mock(FeeData.class);
        view = mock(StateView.class);
        scheduleDeleteTxn = mock(TransactionBody.class);
        given(scheduleDeleteTxn.hasScheduleDelete()).willReturn(true);
        given(scheduleDeleteTxn.getScheduleDelete())
                .willReturn(
                        ScheduleDeleteTransactionBody.newBuilder().setScheduleID(target).build());

        nonScheduleDeleteTxn = mock(TransactionBody.class);
        given(nonScheduleDeleteTxn.hasScheduleDelete()).willReturn(false);

        scheduleOpsUsage = mock(ScheduleOpsUsage.class);
        given(scheduleOpsUsage.scheduleDeleteUsage(scheduleDeleteTxn, sigUsage, expiry))
                .willReturn(expected);

        given(view.infoForSchedule(target)).willReturn(Optional.of(info));

        subject = new ScheduleDeleteResourceUsage(scheduleOpsUsage, new MockGlobalDynamicProps());
    }

    @Test
    void recognizesApplicableQuery() {
        // expect:
        assertTrue(subject.applicableTo(scheduleDeleteTxn));
        assertFalse(subject.applicableTo(nonScheduleDeleteTxn));
    }

    @Test
    void delegatesToCorrectEstimate() throws Exception {
        // expect:
        assertEquals(expected, subject.usageGiven(scheduleDeleteTxn, obj, view));
    }

    @Test
    void returnsDefaultIfInfoMissing() throws Exception {
        // setup:
        long start = 1_234_567L;
        TransactionID txnId =
                TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(start))
                        .build();
        given(scheduleDeleteTxn.getTransactionID()).willReturn(txnId);
        given(view.infoForSchedule(target)).willReturn(Optional.empty());
        given(scheduleOpsUsage.scheduleDeleteUsage(scheduleDeleteTxn, sigUsage, start + 1800))
                .willReturn(expected);

        // expect:
        assertEquals(expected, subject.usageGiven(scheduleDeleteTxn, obj, view));
        // and:
        verify(view).infoForSchedule(target);
    }
}
