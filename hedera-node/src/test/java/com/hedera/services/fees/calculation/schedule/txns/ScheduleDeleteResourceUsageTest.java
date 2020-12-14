package com.hedera.services.fees.calculation.schedule.txns;

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import proto.ScheduleDelete;
import proto.ScheduleGetInfo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
public class ScheduleDeleteResourceUsageTest {
    ScheduleDeleteResourceUsage subject;

    TransactionBody nonScheduleDeleteTxn;
    TransactionBody scheduleDeleteTxn;

    @BeforeEach
    private void setup() throws Throwable {
        scheduleDeleteTxn = mock(TransactionBody.class);
        given(scheduleDeleteTxn.hasScheduleDelete()).willReturn(true);

        nonScheduleDeleteTxn = mock(TransactionBody.class);
        given(nonScheduleDeleteTxn.hasScheduleDelete()).willReturn(false);

        subject = new ScheduleDeleteResourceUsage();
    }

    @Test
    public void recognizesApplicableQuery() {
        // expect:
        assertTrue(subject.applicableTo(scheduleDeleteTxn));
        assertFalse(subject.applicableTo(nonScheduleDeleteTxn));
    }
}
