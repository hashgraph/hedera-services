package com.hedera.node.app.fees;

import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class MonoFeeAccumulatorTest {
    @Mock
    private UsageBasedFeeCalculator usageBasedFeeCalculator;
    @Mock
    private UsagePricesProvider usagePricesProvider;
    @Mock
    private StateView stateView;

    private MonoFeeAccumulator subject;

    @BeforeEach
    void setUp() {
        subject = new MonoFeeAccumulator(usageBasedFeeCalculator, usagePricesProvider, () ->stateView);
    }

    @Test
    void delegatedComputePaymentForQuery() {
        final var mockQuery = Query.getDefaultInstance();
        final var queryFunction = HederaFunctionality.ConsensusGetTopicInfo;
        final var usagePrices = FeeData.getDefaultInstance();
        final var time = Timestamp.newBuilder().setSeconds(100L).build();
        final var feeObject = new FeeObject(100L, 0L, 100L);

        given(usagePricesProvider.defaultPricesGiven(queryFunction, time))
                .willReturn(usagePrices);
        given(usageBasedFeeCalculator.computePayment(mockQuery, usagePrices, stateView, time, new HashMap<>()))
                .willReturn(feeObject);

        final var fee = subject.computePayment(queryFunction, mockQuery, Timestamp.newBuilder().setSeconds(100L).build());

        assertEquals(feeObject, fee);
        verify(usagePricesProvider).defaultPricesGiven(queryFunction, time);
        verify(usageBasedFeeCalculator).computePayment(mockQuery, usagePrices, stateView, time, new HashMap<>());
    }
}
