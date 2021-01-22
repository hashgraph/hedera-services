package com.hedera.services.fees.calculation.schedule.txns;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.UsageEstimatorUtils;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.schedule.ScheduleDeleteUsage;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class ScheduleDeleteResourceUsageTest {
    ScheduleDeleteResourceUsage subject;

    StateView view;
    ScheduleDeleteUsage usage;
    BiFunction<TransactionBody, SigUsage, ScheduleDeleteUsage> factory;

    int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);

    TransactionBody nonScheduleDeleteTxn;
    TransactionBody scheduleDeleteTxn;

    @BeforeEach
    private void setup() {
        view = mock(StateView.class);
        scheduleDeleteTxn = mock(TransactionBody.class);
        given(scheduleDeleteTxn.hasScheduleDelete()).willReturn(true);

        nonScheduleDeleteTxn = mock(TransactionBody.class);
        given(nonScheduleDeleteTxn.hasScheduleDelete()).willReturn(false);

        usage = mock(ScheduleDeleteUsage.class);
        given(usage.get()).willReturn(MOCK_SCHEDULE_DELETE_USAGE);

        factory = (BiFunction<TransactionBody, SigUsage, ScheduleDeleteUsage>)mock(BiFunction.class);
        given(factory.apply(scheduleDeleteTxn, sigUsage)).willReturn(usage);

        ScheduleDeleteResourceUsage.factory = factory;
        subject = new ScheduleDeleteResourceUsage();
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
        assertEquals(MOCK_SCHEDULE_DELETE_USAGE, subject.usageGiven(scheduleDeleteTxn, obj, view));
    }

    private static final FeeData MOCK_SCHEDULE_DELETE_USAGE = UsageEstimatorUtils.defaultPartitioning(
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
