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

import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hedera.test.utils.KeyUtils.B_COMPLEX_KEY;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hashgraph.pbj.runtime.io.Bytes;
import com.hedera.node.app.fees.MonoGetTopicInfoUsage;
import com.hedera.node.app.hapi.utils.exception.InvalidTxBodyException;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.consensus.impl.handlers.PbjKeyConverter;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.RenewAssessment;
import com.hedera.node.app.service.mono.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.fees.calculation.consensus.txns.UpdateTopicResourceUsage;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.utility.AutoCloseableWrapper;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdaptedMonoFeeCalculatorTest {
    private static final JKey PAYER_KEY = new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
    private static final Query MOCK_QUERY = Query.getDefaultInstance();
    private static final Instant NOW = Instant.ofEpochSecond(1_234_567L);
    private static final Timestamp AT =
            Timestamp.newBuilder().setSeconds(1_234_567).build();
    private static final FeeData MOCK_USAGE = FeeData.getDefaultInstance();
    private static final FeeData MOCK_PRICES = FeeData.getDefaultInstance();
    private static final FeeObject MOCK_FEES = new FeeObject(1, 2, 3);
    private static final SigValueObj MOCK_SIG_USAGE = new SigValueObj(1, 2, 3);
    private static final ExchangeRate MOCK_RATE = ExchangeRate.getDefaultInstance();
    private static final Map<SubType, FeeData> TYPED_PRICES = Map.of(SubType.DEFAULT, FeeData.getDefaultInstance());

    private static final Topic MOCK_TOPIC = new Topic(
            1L,
            2L,
            3L,
            4L,
            5L,
            true,
            Bytes.wrap("MOCK_RUNNING_HASH".getBytes()),
            "MOCK_MEMO",
            PbjKeyConverter.fromGrpcKey(A_COMPLEX_KEY),
            PbjKeyConverter.fromGrpcKey(B_COMPLEX_KEY));

    private static final TransactionBody MOCK_TXN = TransactionBody.newBuilder()
            .setConsensusUpdateTopic(ConsensusUpdateTopicTransactionBody.newBuilder()
                    .setTopicID(TopicID.newBuilder()
                            .setTopicNum(MOCK_TOPIC.topicNumber())
                            .build()))
            .build();

    @Mock
    private HederaState state;

    @Mock
    private HederaAccount account;

    @Mock
    private ReadableStates readableStates;

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

    @Mock
    private AutoCloseableWrapper<HederaState> wrapper;

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

    @Test
    void computesUpdateTopicFeeViaWorkingStates() throws InvalidTxBodyException {
        given(accessor.getTxn()).willReturn(MOCK_TXN);
        given(accessor.getFunction()).willReturn(ConsensusUpdateTopic);
        given(accessor.getSubType()).willReturn(SubType.DEFAULT);

        given(workingStateAccessor.getHederaState()).willReturn(state);
        given(state.createReadableStates(ConsensusService.NAME)).willReturn(readableStates);
        given(readableStates.<EntityNum, Topic>get(ConsensusServiceImpl.TOPICS_KEY))
                .willReturn(wellKnownTopicsKVS());

        final var mappedTopic = MonoGetTopicInfoUsage.monoTopicFrom(MOCK_TOPIC);
        given(monoFeeCalculator.getSigUsage(accessor, PAYER_KEY)).willReturn(MOCK_SIG_USAGE);
        given(monoUpdateTopicUsage.usageGivenExplicit(MOCK_TXN, MOCK_SIG_USAGE, mappedTopic))
                .willReturn(MOCK_USAGE);

        given(exchange.activeRate(NOW)).willReturn(MOCK_RATE);
        given(usagePrices.activePrices(accessor)).willReturn(TYPED_PRICES);
        given(monoFeeCalculator.feesIncludingCongestion(MOCK_USAGE, MOCK_PRICES, accessor, MOCK_RATE))
                .willReturn(MOCK_FEES);

        final var updateTopicFees = subject.computeFee(accessor, PAYER_KEY, view, NOW);

        assertSame(MOCK_FEES, updateTopicFees);
    }

    @Test
    void estimatesUpdateTopicFeeViaAccessibleStates() throws InvalidTxBodyException {
        given(accessor.getTxn()).willReturn(MOCK_TXN);
        given(accessor.getFunction()).willReturn(ConsensusUpdateTopic);
        given(accessor.getSubType()).willReturn(SubType.DEFAULT);

        given(stateAccessor.get()).willReturn(wrapper);
        given(wrapper.get()).willReturn(state);
        given(state.createReadableStates(ConsensusService.NAME)).willReturn(readableStates);
        given(readableStates.<EntityNum, Topic>get(ConsensusServiceImpl.TOPICS_KEY))
                .willReturn(wellKnownTopicsKVS());

        final var mappedTopic = MonoGetTopicInfoUsage.monoTopicFrom(MOCK_TOPIC);
        given(monoFeeCalculator.getSigUsage(accessor, PAYER_KEY)).willReturn(MOCK_SIG_USAGE);
        given(monoUpdateTopicUsage.usageGivenExplicit(MOCK_TXN, MOCK_SIG_USAGE, mappedTopic))
                .willReturn(MOCK_USAGE);

        given(exchange.rate(AT)).willReturn(MOCK_RATE);
        given(monoFeeCalculator.uncheckedPricesGiven(accessor, AT)).willReturn(TYPED_PRICES);
        given(monoFeeCalculator.feesIncludingCongestion(MOCK_USAGE, MOCK_PRICES, accessor, MOCK_RATE))
                .willReturn(MOCK_FEES);

        final var updateTopicFees = subject.estimateFee(accessor, PAYER_KEY, view, AT);

        assertSame(MOCK_FEES, updateTopicFees);
    }

    @Test
    void delegatesEstimatePayment() {
        given(monoFeeCalculator.estimatePayment(MOCK_QUERY, MOCK_PRICES, view, AT, ANSWER_ONLY))
                .willReturn(MOCK_FEES);

        final var estimatePayment = subject.estimatePayment(MOCK_QUERY, MOCK_PRICES, view, AT, ANSWER_ONLY);

        assertSame(MOCK_FEES, estimatePayment);
    }

    @Test
    void delegatesComputePayment() {
        given(monoFeeCalculator.computePayment(MOCK_QUERY, MOCK_PRICES, view, AT, Collections.emptyMap()))
                .willReturn(MOCK_FEES);

        final var computedPayment = subject.computePayment(MOCK_QUERY, MOCK_PRICES, view, AT, Collections.emptyMap());

        assertSame(MOCK_FEES, computedPayment);
    }

    @Test
    void delegatesAutoRenewalAssessment() {
        final var expected = new RenewAssessment(1L, 2L);
        given(monoFeeCalculator.assessCryptoAutoRenewal(account, 3L, NOW, account))
                .willReturn(expected);

        final var assessment = subject.assessCryptoAutoRenewal(account, 3L, NOW, account);

        assertSame(expected, assessment);
    }

    private MapReadableKVState<EntityNum, Topic> wellKnownTopicsKVS() {
        return MapReadableKVState.<EntityNum, Topic>builder(ConsensusServiceImpl.TOPICS_KEY)
                .value(EntityNum.fromLong(MOCK_TOPIC.topicNumber()), MOCK_TOPIC)
                .build();
    }
}
