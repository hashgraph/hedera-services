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
public class ScheduleCreateResourceUsageTest {
    ScheduleCreateResourceUsage subject;

    TransactionBody nonScheduleCreateTxn;
    TransactionBody scheduleCreateTxn;

    @BeforeEach
    private void setup() throws Throwable {
        scheduleCreateTxn = mock(TransactionBody.class);
        given(scheduleCreateTxn.hasScheduleCreate()).willReturn(true);

        nonScheduleCreateTxn = mock(TransactionBody.class);
        given(nonScheduleCreateTxn.hasScheduleCreate()).willReturn(false);

        subject = new ScheduleCreateResourceUsage();
    }

    @Test
    public void recognizesApplicableQuery() {
        // expect:
        assertTrue(subject.applicableTo(scheduleCreateTxn));
        assertFalse(subject.applicableTo(nonScheduleCreateTxn));
    }

    @Test
    public void usageGivenNotSupported() {
        assertThrows(UnsupportedOperationException.class, () -> subject.usageGiven(scheduleCreateTxn, null, null));
    }
}
