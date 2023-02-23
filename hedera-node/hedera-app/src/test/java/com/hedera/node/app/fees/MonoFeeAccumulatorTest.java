/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.fees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.FeeResourcesLoaderImpl;
import com.hedera.node.app.service.mono.fees.calculation.UsageBasedFeeCalculator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoFeeAccumulatorTest {
    @Mock
    private UsageBasedFeeCalculator usageBasedFeeCalculator;

    @Mock
    private PricesAndFeesProvider pricesAndFeesProvider;

    @Mock
    private FeeResourcesLoaderImpl feeResourcesLoader;

    @Mock
    private StateView stateView;

    private MonoFeeAccumulator subject;

    @BeforeEach
    void setUp() {
        subject = new MonoFeeAccumulator(usageBasedFeeCalculator, feeResourcesLoader, () -> stateView);
    }

    @Test
    void delegatedComputePaymentForQuery() {
        final var mockQuery = Query.getDefaultInstance();
        final var queryFunction = HederaFunctionality.ConsensusGetTopicInfo;
        final var usagePrices = FeeData.getDefaultInstance();
        final var time = Timestamp.newBuilder().setSeconds(100L).build();
        final var feeObject = new FeeObject(100L, 0L, 100L);

        given(usageBasedFeeCalculator.computePayment(mockQuery, usagePrices, stateView, time, new HashMap<>()))
                .willReturn(feeObject);

        final var fee = subject.computePayment(
                queryFunction,
                mockQuery,
                Timestamp.newBuilder().setSeconds(100L).build());

        assertEquals(feeObject, fee);
        verify(usageBasedFeeCalculator).computePayment(mockQuery, usagePrices, stateView, time, new HashMap<>());
    }
}
