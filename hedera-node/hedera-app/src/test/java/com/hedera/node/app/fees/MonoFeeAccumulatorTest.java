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

import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_GET_TOPIC_INFO;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.hapi.node.base.FeeData;
import com.hedera.hapi.node.base.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoFeeAccumulatorTest {
    private final Query mockQuery = new Query.Builder().build();
    private final FeeData mockUsage = new FeeData.Builder().build();
    private final FeeData mockPrices = new FeeData.Builder().build();
    private final Timestamp mockTime = new Timestamp.Builder().seconds(100L).build();

    @Mock
    private UsageBasedFeeCalculator usageBasedFeeCalculator;

    @Mock
    private UsagePricesProvider usagePricesProvider;

    @Mock
    private StateView stateView;

    @Mock
    private MonoGetTopicInfoUsage getTopicInfoUsage;

    @Mock
    private ReadableStoreFactory readableStoreFactory;

    @Mock
    private ReadableTopicStore readableTopicStore;

    private MonoFeeAccumulator subject;

    @BeforeEach
    void setUp() {
        subject = new MonoFeeAccumulator(
                usageBasedFeeCalculator, getTopicInfoUsage, usagePricesProvider, () -> stateView);
    }

    @Test
    void usesMonoAdapterDirectlyForGetTopicInfo() {
        final var expectedFees = new FeeObject(100L, 0L, 100L);
        given(usagePricesProvider.defaultPricesGiven(fromPbj(CONSENSUS_GET_TOPIC_INFO), fromPbj(mockTime)))
                .willReturn(fromPbj(mockPrices));
        given(readableStoreFactory.createTopicStore()).willReturn(readableTopicStore);
        given(getTopicInfoUsage.computeUsage(mockQuery, readableTopicStore)).willReturn(mockUsage);
        given(usageBasedFeeCalculator.computeFromQueryResourceUsage(fromPbj(mockUsage), fromPbj(mockUsage), fromPbj(mockTime)))
                .willReturn(expectedFees);

        final var actualFees = subject.computePayment(readableStoreFactory, CONSENSUS_GET_TOPIC_INFO, mockQuery, mockTime);

        assertSame(expectedFees, actualFees);
    }

    @Test
    void delegatedComputePaymentForQuery() {
        final var queryFunction = CONSENSUS_GET_TOPIC_INFO;
        final var expectedFee = new FeeObject(100L, 0L, 100L);

        given(usagePricesProvider.defaultPricesGiven(fromPbj(queryFunction), fromPbj(mockTime))).willReturn(PbjConverter.fromPbj(mockPrices));
        given(readableStoreFactory.createTopicStore()).willReturn(readableTopicStore);
        given(getTopicInfoUsage.computeUsage(mockQuery, readableTopicStore)).willReturn(mockUsage);
        given(usageBasedFeeCalculator.computeFromQueryResourceUsage(fromPbj(mockUsage),
                fromPbj(mockPrices), fromPbj(mockTime)))
                .willReturn(expectedFee);

        final var fee = subject.computePayment(readableStoreFactory, queryFunction, mockQuery, mockTime);

        assertSame(expectedFee, fee);
    }
}
