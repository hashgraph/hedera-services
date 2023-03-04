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

package com.hedera.node.app.workflows.handle;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.fees.calculation.consensus.txns.UpdateTopicResourceUsage;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.common.utility.AutoCloseableWrapper;
import java.time.Instant;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdaptedMonoFeeCalculatorTest {
    private static final FeeObject MOCK_FEES = new FeeObject(1, 2, 3);

    @Mock
    private StateView view;

    @Mock
    private TxnAccessor accessor;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private UsagePricesProvider usagePrices;

    @Mock
    private UsageBasedFeeCalculator monoFeeCalculator;

    @Mock
    private WorkingStateAccessor workingStateAccessor;

    @Mock
    private UpdateTopicResourceUsage monoUpdateTopicUsage;

    @Mock
    private Supplier<AutoCloseableWrapper<HederaState>> stateAccessor;

    private AdaptedMonoFeeCalculator subject;

    @BeforeEach
    void setUp() {
        subject = new AdaptedMonoFeeCalculator(
                exchange, usagePrices, monoFeeCalculator, workingStateAccessor, monoUpdateTopicUsage, stateAccessor);
    }

    @Test
    void delegatesInit() {
        subject.init();

        verify(monoFeeCalculator).init();
    }

    @Test
    void delegatesEstimatedGasPrice() {
        final var function = ContractCall;
        final var at = Timestamp.getDefaultInstance();
        given(monoFeeCalculator.estimatedGasPriceInTinybars(ContractCall, at)).willReturn(1L);

        assertEquals(1L, subject.estimatedGasPriceInTinybars(function, at));
    }

    @Test
    void delegatesEstimatedNonFeeAdjust() {
        final var at = Timestamp.getDefaultInstance();
        given(monoFeeCalculator.estimatedNonFeePayerAdjustments(accessor, at)).willReturn(1L);

        assertEquals(1L, subject.estimatedNonFeePayerAdjustments(accessor, at));
    }

    @Test
    void delegatesNonTopicUpdateComputeFee() {
        final var payerKey = new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
        final var at = Instant.ofEpochSecond(1_234_567L);
        given(monoFeeCalculator.computeFee(accessor, payerKey, view, at)).willReturn(MOCK_FEES);

        assertSame(MOCK_FEES, subject.computeFee(accessor, payerKey, view, at));
    }
}
