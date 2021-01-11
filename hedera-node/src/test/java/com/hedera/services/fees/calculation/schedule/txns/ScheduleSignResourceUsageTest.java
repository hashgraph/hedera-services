package com.hedera.services.fees.calculation.schedule.txns;

import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
public class ScheduleSignResourceUsageTest {
    ScheduleSignResourceUsage subject;

    TransactionBody nonScheduleSignTxn;
    TransactionBody scheduleSignTxn;

    @BeforeEach
    private void setup() throws Throwable {
        scheduleSignTxn = mock(TransactionBody.class);
        given(scheduleSignTxn.hasScheduleSign()).willReturn(true);

        nonScheduleSignTxn = mock(TransactionBody.class);
        given(nonScheduleSignTxn.hasScheduleSign()).willReturn(false);

        subject = new ScheduleSignResourceUsage();
    }

    @Test
    public void recognizesApplicableQuery() {
        // expect:
        assertTrue(subject.applicableTo(scheduleSignTxn));
        assertFalse(subject.applicableTo(nonScheduleSignTxn));
    }

    @Test
    public void usageGivenNotSupported() {
        assertThrows(UnsupportedOperationException.class, () -> subject.usageGiven(scheduleSignTxn, null, null));
    }
}
