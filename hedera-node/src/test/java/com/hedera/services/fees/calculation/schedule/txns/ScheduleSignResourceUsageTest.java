package com.hedera.services.fees.calculation.schedule.txns;

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.UsageEstimatorUtils;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.schedule.ScheduleSignUsage;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
public class ScheduleSignResourceUsageTest {
    ScheduleSignResourceUsage subject;
    StateView view;
    ScheduleSignUsage usage;
    BiFunction<TransactionBody, SigUsage, ScheduleSignUsage> factory;
    TransactionBody nonScheduleSignTxn;
    TransactionBody scheduleSignTxn;

    int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
    GlobalDynamicProperties props = new MockGlobalDynamicProps();

    @BeforeEach
    private void setup() {
        view = mock(StateView.class);
        scheduleSignTxn = mock(TransactionBody.class);
        given(scheduleSignTxn.hasScheduleSign()).willReturn(true);

        nonScheduleSignTxn = mock(TransactionBody.class);
        given(nonScheduleSignTxn.hasScheduleSign()).willReturn(false);

        usage = mock(ScheduleSignUsage.class);
        given(usage.givenScheduledTxExpirationTimeSecs(anyInt())).willReturn(usage);
        given(usage.get()).willReturn(MOCK_SCHEDULE_SIGN_USAGE);

        factory = (BiFunction<TransactionBody, SigUsage, ScheduleSignUsage>)mock(BiFunction.class);
        given(factory.apply(scheduleSignTxn, sigUsage)).willReturn(usage);

        ScheduleSignResourceUsage.factory = factory;
        subject = new ScheduleSignResourceUsage(props);
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
        assertEquals(MOCK_SCHEDULE_SIGN_USAGE, subject.usageGiven(scheduleSignTxn, obj, view));
    }

    private static final FeeData MOCK_SCHEDULE_SIGN_USAGE = UsageEstimatorUtils.defaultPartitioning(
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
