package com.hedera.services.fees.calculation.schedule.queries;

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.ScheduleID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import proto.ScheduleGetInfo;

import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GetScheduleInfoResourceUsageTest {
    ScheduleID target = IdUtils.asSchedule("0.0.123");

    GetScheduleInfoResourceUsage subject;

    @BeforeEach
    private void setup() throws Throwable {
        subject = new GetScheduleInfoResourceUsage();
    }

    @Test
    public void recognizesApplicableQuery() {
        // given:
        var applicable = scheduleInfoQuery(target, COST_ANSWER);
        var inapplicable = Query.getDefaultInstance();

        // expect:
        assertTrue(subject.applicableTo(applicable));
        assertFalse(subject.applicableTo(inapplicable));
    }

    private Query scheduleInfoQuery(ScheduleID id, ResponseType type) {
        ScheduleGetInfo.ScheduleGetInfoQuery.Builder op = ScheduleGetInfo.ScheduleGetInfoQuery.newBuilder()
                .setSchedule(id)
                .setHeader(QueryHeader.newBuilder().setResponseType(type));
        return Query.newBuilder()
                .setScheduleGetInfo(op)
                .build();
    }
}
