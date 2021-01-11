package com.hedera.services.fees.calculation.schedule.queries;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.usage.schedule.ScheduleGetInfoUsage;
import com.hedera.services.usage.token.TokenGetInfoUsage;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.ScheduleGetInfoQuery;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class GetScheduleInfoResourceUsageTest {
    ScheduleID target = IdUtils.asSchedule("0.0.123");

    Key randomKey = new KeyFactory().newEd25519();
    ScheduleInfo info = ScheduleInfo.newBuilder()
            .setTransactionBody(ByteString.copyFrom(new byte[]{0x01, 0x02, 0x03, 0x04}))
            .setAdminKey(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey())
            .setPayerAccountID(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT)
            .setSigners(KeyList.newBuilder().addKeys(randomKey))
            .build();

    StateView view;
    GetScheduleInfoResourceUsage subject;
    ScheduleGetInfoUsage estimator;
    Function<Query, ScheduleGetInfoUsage> factory;

    @BeforeEach
    private void setup() throws Throwable {
        subject = new GetScheduleInfoResourceUsage();
        view = mock(StateView.class);
        estimator = mock(ScheduleGetInfoUsage.class);
        factory = mock(Function.class);
        given(factory.apply(any())).willReturn(estimator);
        given(view.infoForSchedule(target)).willReturn(Optional.of(info));
    }

    @Test
    public void recognizesApplicableQuery() {
        // given:
        var applicable = scheduleInfoQuery(target);
        var inapplicable = Query.getDefaultInstance();

        // expect:
        assertTrue(subject.applicableTo(applicable));
        assertFalse(subject.applicableTo(inapplicable));
    }

//    @Test
//    public void calculatesFeeData() {
//        // when
//        subject.usageGiven(scheduleInfoQuery(target), view);
//
//        // then
//        verify(view).infoForSchedule(target);
//        verify(estimator).givenTransaction(info.getTransactionBody().toByteArray());
//        verify(estimator).givenCurrentAdminKey(Optional.of(info.getAdminKey()));
//        verify(estimator).givenSigners(Optional.of(info.getSigners()));
//    }

    private Query scheduleInfoQuery(ScheduleID id) {
        return Query.newBuilder()
                .setScheduleGetInfo(ScheduleGetInfoQuery.newBuilder()
                        .setScheduleID(id))
                .build();
    }

    private Query scheduleInfoQueryWithType(ScheduleID id, ResponseType type) {
        ScheduleGetInfoQuery.Builder op = ScheduleGetInfoQuery.newBuilder()
                .setScheduleID(id)
                .setHeader(QueryHeader.newBuilder().setResponseType(type));
        return Query.newBuilder()
                .setScheduleGetInfo(op)
                .build();
    }
}
