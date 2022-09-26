/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees.calculation;

import static com.hedera.services.fees.calculation.BasicFcfsUsagePrices.DEFAULT_RESOURCE_PRICES;
import static com.hedera.test.factories.txns.ContractCallFactory.newSignedContractCall;
import static com.hedera.test.factories.txns.ContractCreateFactory.newSignedContractCreate;
import static com.hedera.test.factories.txns.CryptoCreateFactory.newSignedCryptoCreate;
import static com.hedera.test.factories.txns.CryptoTransferFactory.newSignedCryptoTransfer;
import static com.hedera.test.factories.txns.FileCreateFactory.newSignedFileCreate;
import static com.hedera.test.factories.txns.TinyBarsFromTo.tinyBarsFromTo;
import static com.hedera.test.factories.txns.TokenBurnFactory.newSignedTokenBurn;
import static com.hedera.test.factories.txns.TokenMintFactory.newSignedTokenMint;
import static com.hedera.test.factories.txns.TokenWipeFactory.newSignedTokenWipe;
import static com.hedera.test.utils.IdUtils.asAccountString;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractAutoRenew;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAccountAutoRenew;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.fee.FeeBuilder.FEE_DIVISOR_FACTOR;
import static com.hederahashgraph.fee.FeeBuilder.HRS_DIVISOR;
import static com.hederahashgraph.fee.FeeBuilder.getFeeObject;
import static com.hederahashgraph.fee.FeeBuilder.getTinybarsFromTinyCents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.utils.PricedUsageCalculator;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.services.utils.accessors.TokenWipeAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.FeeBuilder;
import com.hederahashgraph.fee.FeeObject;
import com.hederahashgraph.fee.SigValueObj;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

class UsageBasedFeeCalculatorTest {
    private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);
    private final FeeComponents mockFees =
            FeeComponents.newBuilder()
                    .setMax(1_234_567L)
                    .setGas(5_000_000L)
                    .setBpr(1_000_000L)
                    .setBpt(2_000_000L)
                    .setRbh(3_000_000L)
                    .setSbh(4_000_000L)
                    .build();
    private final FeeData mockFeeData =
            FeeData.newBuilder()
                    .setNetworkdata(mockFees)
                    .setNodedata(mockFees)
                    .setServicedata(mockFees)
                    .setSubType(SubType.DEFAULT)
                    .build();
    private final Map<SubType, FeeData> currentPrices =
            Map.of(
                    SubType.DEFAULT, mockFeeData,
                    SubType.TOKEN_FUNGIBLE_COMMON, mockFeeData,
                    SubType.TOKEN_NON_FUNGIBLE_UNIQUE, mockFeeData);
    private final FeeData defaultCurrentPrices = mockFeeData;
    private final FeeData resourceUsage = mockFeeData;
    private final ExchangeRate currentRate =
            ExchangeRate.newBuilder().setCentEquiv(22).setHbarEquiv(1).build();
    private Query query;
    private StateView view;
    private final Timestamp at = Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private HbarCentExchange exchange;
    private UsagePricesProvider usagePrices;
    private TxnResourceUsageEstimator correctOpEstimator;
    private TxnResourceUsageEstimator incorrectOpEstimator;
    private QueryResourceUsageEstimator correctQueryEstimator;
    private QueryResourceUsageEstimator incorrectQueryEstimator;
    private Map<HederaFunctionality, List<TxnResourceUsageEstimator>> txnUsageEstimators;
    private final long balance = 1_234_567L;
    private final AccountID payer = IdUtils.asAccount("0.0.75231");
    private final AccountID receiver = IdUtils.asAccount("0.0.86342");

    private final TokenID tokenId = IdUtils.asToken("0.0.123456");

    /* Has nine simple keys. */
    private final KeyTree complexKey = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
    private JKey payerKey;
    private Transaction signedTxn;
    private SignedTxnAccessor accessor;
    private AutoRenewCalcs autoRenewCalcs;
    private PricedUsageCalculator pricedUsageCalculator;

    private final AtomicLong suggestedMultiplier = new AtomicLong(1L);

    private UsageBasedFeeCalculator subject;

    @BeforeEach
    void setup() throws Throwable {
        view = mock(StateView.class);
        query = mock(Query.class);
        payerKey = complexKey.asJKey();
        exchange = mock(HbarCentExchange.class);
        signedTxn =
                newSignedCryptoCreate()
                        .balance(balance)
                        .payerKt(complexKey)
                        .txnValidStart(at)
                        .get();
        accessor = SignedTxnAccessor.from(signedTxn.toByteArray(), signedTxn);
        usagePrices = mock(UsagePricesProvider.class);
        given(usagePrices.activePrices(accessor)).willReturn(currentPrices);
        correctOpEstimator = mock(TxnResourceUsageEstimator.class);
        incorrectOpEstimator = mock(TxnResourceUsageEstimator.class);
        correctQueryEstimator = mock(QueryResourceUsageEstimator.class);
        incorrectQueryEstimator = mock(QueryResourceUsageEstimator.class);
        autoRenewCalcs = mock(AutoRenewCalcs.class);
        pricedUsageCalculator = mock(PricedUsageCalculator.class);

        txnUsageEstimators =
                (Map<HederaFunctionality, List<TxnResourceUsageEstimator>>) mock(Map.class);

        subject =
                new UsageBasedFeeCalculator(
                        autoRenewCalcs,
                        exchange,
                        mock(AutoCreationLogic.class),
                        usagePrices,
                        new NestedMultiplierSource(),
                        pricedUsageCalculator,
                        Set.of(incorrectQueryEstimator, correctQueryEstimator),
                        txnUsageEstimators);
    }

    @Test
    void delegatesAutoRenewCalcs() {
        // setup:
        final var expected = new RenewAssessment(456L, 123L);

        given(autoRenewCalcs.assessCryptoRenewal(any(), anyLong(), any(), any(), any()))
                .willReturn(expected);

        // when:
        var actual =
                subject.assessCryptoAutoRenewal(
                        new MerkleAccount(), 1L, Instant.ofEpochSecond(2L), new MerkleAccount());

        // then:
        assertSame(expected, actual);
    }

    @Test
    void estimatesContractCallPayerBalanceChanges() throws Throwable {
        // setup:
        long gas = 1_234L, sent = 5_432L;
        signedTxn =
                newSignedContractCall()
                        .payer(asAccountString(payer))
                        .gas(gas)
                        .sending(sent)
                        .txnValidStart(at)
                        .get();
        accessor = SignedTxnAccessor.from(signedTxn.toByteArray());

        given(exchange.rate(at)).willReturn(currentRate);
        given(usagePrices.defaultPricesGiven(ContractCall, at)).willReturn(defaultCurrentPrices);
        // and:
        long expectedGasPrice =
                getTinybarsFromTinyCents(currentRate, mockFees.getGas() / FEE_DIVISOR_FACTOR);

        // expect:
        assertEquals(
                -(gas * expectedGasPrice + sent),
                subject.estimatedNonFeePayerAdjustments(accessor, at));
    }

    @Test
    void estimatesEthereumTransactionChanges() throws Throwable {
        // setup:
        long gas = 1_234L, sent = 5_432L;
        signedTxn =
                newSignedContractCall()
                        .payer(asAccountString(payer))
                        .gas(gas)
                        .sending(sent)
                        .txnValidStart(at)
                        .get();
        accessor = Mockito.spy(SignedTxnAccessor.from(signedTxn.toByteArray()));

        given(accessor.getFunction()).willReturn(EthereumTransaction);

        // expect:
        assertEquals(0, subject.estimatedNonFeePayerAdjustments(accessor, at));
    }

    @Test
    void estimatesCryptoCreatePayerBalanceChanges() {
        // expect:
        assertEquals(-balance, subject.estimatedNonFeePayerAdjustments(accessor, at));
    }

    @Test
    void estimatesContractCreatePayerBalanceChanges() throws Throwable {
        // setup:
        long gas = 1_234L, initialBalance = 5_432L;
        signedTxn =
                newSignedContractCreate()
                        .payer(asAccountString(payer))
                        .gas(gas)
                        .initialBalance(initialBalance)
                        .txnValidStart(at)
                        .get();
        accessor = SignedTxnAccessor.from(signedTxn.toByteArray());

        given(exchange.rate(at)).willReturn(currentRate);
        given(usagePrices.pricesGiven(ContractCreate, at)).willReturn(currentPrices);
        given(usagePrices.defaultPricesGiven(ContractCreate, at)).willReturn(defaultCurrentPrices);
        // and:
        long expectedGasPrice =
                getTinybarsFromTinyCents(currentRate, mockFees.getGas() / FEE_DIVISOR_FACTOR);

        // expect:
        assertEquals(
                -(gas * expectedGasPrice + initialBalance),
                subject.estimatedNonFeePayerAdjustments(accessor, at));
    }

    @Test
    void estimatesMiscNoNetChange() throws Throwable {
        // setup:
        signedTxn = newSignedFileCreate().payer(asAccountString(payer)).txnValidStart(at).get();
        accessor = SignedTxnAccessor.from(signedTxn.toByteArray());

        // expect:
        assertEquals(0L, subject.estimatedNonFeePayerAdjustments(accessor, at));
    }

    @Test
    void estimatesCryptoTransferPayerBalanceChanges() throws Throwable {
        // setup:
        long sent = 1_234L;
        signedTxn =
                newSignedCryptoTransfer()
                        .payer(asAccountString(payer))
                        .transfers(
                                tinyBarsFromTo(
                                        asAccountString(payer), asAccountString(receiver), sent))
                        .txnValidStart(at)
                        .get();
        accessor = SignedTxnAccessor.from(signedTxn.toByteArray());

        // expect:
        assertEquals(-sent, subject.estimatedNonFeePayerAdjustments(accessor, at));
    }

    @Test
    void estimatesFutureGasPriceInTinybars() {
        given(exchange.rate(at)).willReturn(currentRate);
        given(usagePrices.pricesGiven(CryptoCreate, at)).willReturn(currentPrices);
        given(usagePrices.defaultPricesGiven(CryptoCreate, at)).willReturn(defaultCurrentPrices);
        // and:
        long expected =
                getTinybarsFromTinyCents(currentRate, mockFees.getGas() / FEE_DIVISOR_FACTOR);

        // when:
        long actual = subject.estimatedGasPriceInTinybars(CryptoCreate, at);

        // then:
        assertEquals(expected, actual);
    }

    @Test
    void loadPriceSchedulesOnInit() {
        final var firstNow = Instant.ofEpochSecond(1_234_567L, 890);
        final var secondNow = Instant.ofEpochSecond(1_234_567L, 911);
        final var accountSeq =
                Triple.of(
                        Map.of(SubType.DEFAULT, FeeData.getDefaultInstance()),
                        firstNow,
                        Map.of(SubType.DEFAULT, FeeData.getDefaultInstance()));
        final var contractSeq =
                Triple.of(
                        Map.of(SubType.DEFAULT, FeeData.getDefaultInstance()),
                        secondNow,
                        Map.of(SubType.DEFAULT, FeeData.getDefaultInstance()));

        given(usagePrices.activePricingSequence(CryptoAccountAutoRenew)).willReturn(accountSeq);
        given(usagePrices.activePricingSequence(ContractAutoRenew)).willReturn(contractSeq);

        subject.init();

        verify(usagePrices).loadPriceSchedules();
        verify(autoRenewCalcs).setAccountRenewalPriceSeq(accountSeq);
        verify(autoRenewCalcs).setContractRenewalPriceSeq(contractSeq);
    }

    @Test
    void throwsIseOnBadScheduleInFcfs() {
        willThrow(IllegalStateException.class).given(usagePrices).loadPriceSchedules();

        // expect:
        assertThrows(IllegalStateException.class, () -> subject.init());
    }

    @Test
    void failsWithIseGivenApplicableButUnusableCalculator() throws InvalidTxBodyException {
        // setup:
        SigValueObj expectedSigUsage =
                new SigValueObj(
                        FeeBuilder.getSignatureCount(signedTxn),
                        9,
                        FeeBuilder.getSignatureSize(signedTxn));

        given(correctOpEstimator.applicableTo(accessor.getTxn())).willReturn(true);
        given(txnUsageEstimators.get(CryptoCreate)).willReturn(List.of(correctOpEstimator));
        given(
                        correctOpEstimator.usageGiven(
                                argThat(accessor.getTxn()::equals),
                                argThat(factory.apply(expectedSigUsage)),
                                argThat(view::equals)))
                .willThrow(InvalidTxBodyException.class);

        // when:
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.computeFee(accessor, payerKey, view, consensusNow));
    }

    @Test
    void failsWithNseeSansApplicableUsageCalculator() {
        // expect:
        assertThrows(
                NoSuchElementException.class,
                () -> subject.computeFee(accessor, payerKey, view, consensusNow));

        final Map<String, Object> emptyMap = Collections.emptyMap();
        final var prices = currentPrices.get(SubType.DEFAULT);
        assertThrows(
                NoSuchElementException.class,
                () -> subject.computePayment(query, prices, view, at, emptyMap));
    }

    @Test
    void invokesQueryDelegateAsExpected() {
        // setup:
        FeeObject expectedFees =
                getFeeObject(currentPrices.get(SubType.DEFAULT), resourceUsage, currentRate);

        given(correctQueryEstimator.applicableTo(query)).willReturn(true);
        given(incorrectQueryEstimator.applicableTo(query)).willReturn(false);
        given(
                        correctQueryEstimator.usageGiven(
                                argThat(query::equals), argThat(view::equals), any()))
                .willReturn(resourceUsage);
        given(incorrectQueryEstimator.usageGiven(any(), any())).willThrow(RuntimeException.class);
        given(exchange.rate(at)).willReturn(currentRate);

        // when:
        FeeObject fees =
                subject.computePayment(
                        query,
                        currentPrices.get(SubType.DEFAULT),
                        view,
                        at,
                        Collections.emptyMap());

        // then:
        assertEquals(fees.getNodeFee(), expectedFees.getNodeFee());
        assertEquals(fees.getNetworkFee(), expectedFees.getNetworkFee());
        assertEquals(fees.getServiceFee(), expectedFees.getServiceFee());
    }

    @Test
    void invokesQueryDelegateByTypeAsExpected() {
        // setup:
        FeeObject expectedFees =
                getFeeObject(currentPrices.get(SubType.DEFAULT), resourceUsage, currentRate);

        given(correctQueryEstimator.applicableTo(query)).willReturn(true);
        given(incorrectQueryEstimator.applicableTo(query)).willReturn(false);
        given(correctQueryEstimator.usageGivenType(query, view, ANSWER_ONLY))
                .willReturn(resourceUsage);
        given(incorrectQueryEstimator.usageGivenType(any(), any(), any()))
                .willThrow(RuntimeException.class);
        given(exchange.rate(at)).willReturn(currentRate);

        // when:
        FeeObject fees =
                subject.estimatePayment(
                        query, currentPrices.get(SubType.DEFAULT), view, at, ANSWER_ONLY);

        // then:
        assertEquals(fees.getNodeFee(), expectedFees.getNodeFee());
        assertEquals(fees.getNetworkFee(), expectedFees.getNetworkFee());
        assertEquals(fees.getServiceFee(), expectedFees.getServiceFee());
    }

    @Test
    void usesMultiplierAsExpected() throws Exception {
        // setup:
        long multiplier = 5L;
        SigValueObj expectedSigUsage =
                new SigValueObj(
                        FeeBuilder.getSignatureCount(signedTxn),
                        9,
                        FeeBuilder.getSignatureSize(signedTxn));
        FeeObject expectedFees =
                getFeeObject(
                        currentPrices.get(SubType.DEFAULT), resourceUsage, currentRate, multiplier);
        suggestedMultiplier.set(multiplier);

        given(correctOpEstimator.applicableTo(accessor.getTxn())).willReturn(true);
        given(txnUsageEstimators.get(CryptoCreate)).willReturn(List.of(correctOpEstimator));
        given(
                        correctOpEstimator.usageGiven(
                                argThat(accessor.getTxn()::equals),
                                argThat(factory.apply(expectedSigUsage)),
                                argThat(view::equals)))
                .willReturn(resourceUsage);
        given(exchange.activeRate(consensusNow)).willReturn(currentRate);

        // when:
        FeeObject fees = subject.computeFee(accessor, payerKey, view, consensusNow);

        // then:
        assertEquals(fees.getNodeFee(), expectedFees.getNodeFee());
        assertEquals(fees.getNetworkFee(), expectedFees.getNetworkFee());
        assertEquals(fees.getServiceFee(), expectedFees.getServiceFee());
    }

    @Test
    void invokesOpDelegateAsExpectedWithOneOption() throws Exception {
        // setup:
        SigValueObj expectedSigUsage =
                new SigValueObj(
                        FeeBuilder.getSignatureCount(signedTxn),
                        9,
                        FeeBuilder.getSignatureSize(signedTxn));
        FeeObject expectedFees =
                getFeeObject(currentPrices.get(SubType.DEFAULT), resourceUsage, currentRate);

        given(correctOpEstimator.applicableTo(accessor.getTxn())).willReturn(true);
        given(txnUsageEstimators.get(CryptoCreate)).willReturn(List.of(correctOpEstimator));
        given(
                        correctOpEstimator.usageGiven(
                                argThat(accessor.getTxn()::equals),
                                argThat(factory.apply(expectedSigUsage)),
                                argThat(view::equals)))
                .willReturn(resourceUsage);
        given(exchange.activeRate(consensusNow)).willReturn(currentRate);

        // when:
        FeeObject fees = subject.computeFee(accessor, payerKey, view, consensusNow);

        // then:
        assertEquals(fees.getNodeFee(), expectedFees.getNodeFee());
        assertEquals(fees.getNetworkFee(), expectedFees.getNetworkFee());
        assertEquals(fees.getServiceFee(), expectedFees.getServiceFee());
    }

    @Test
    void invokesAccessorBasedUsagesForCryptoTransferOutsideHandleWithNewAccumulator()
            throws Throwable {
        // setup:
        long sent = 1_234L;
        signedTxn =
                newSignedCryptoTransfer()
                        .payer(asAccountString(payer))
                        .transfers(
                                tinyBarsFromTo(
                                        asAccountString(payer), asAccountString(receiver), sent))
                        .txnValidStart(at)
                        .get();
        invokesAccessorBasedUsagesForTxnInHandle(
                signedTxn, CryptoTransfer, SubType.DEFAULT, TokenType.UNRECOGNIZED);
    }

    @Test
    void invokesAccessorBasedUsagesForCryptoTransferInHandleWithReusedAccumulator()
            throws Throwable {
        // setup:
        long sent = 1_234L;
        signedTxn =
                newSignedCryptoTransfer()
                        .payer(asAccountString(payer))
                        .transfers(
                                tinyBarsFromTo(
                                        asAccountString(payer), asAccountString(receiver), sent))
                        .txnValidStart(at)
                        .get();

        invokesAccessorBasedUsagesForTxnInHandle(
                signedTxn, CryptoTransfer, SubType.DEFAULT, TokenType.UNRECOGNIZED);
    }

    @Test
    void invokesAccessorBasedUsagesForTokenBurnInHandle() throws Throwable {
        // setup:
        signedTxn =
                newSignedTokenBurn()
                        .payer(asAccountString(payer))
                        .burning(tokenId)
                        .txnValidStart(at)
                        .get();
        invokesAccessorBasedUsagesForTxnInHandle(
                signedTxn,
                TokenBurn,
                SubType.TOKEN_NON_FUNGIBLE_UNIQUE,
                TokenType.NON_FUNGIBLE_UNIQUE);
    }

    @Test
    void invokesAccessorBasedUsagesForTokenWipeInHandle() throws Throwable {
        // setup:
        signedTxn =
                newSignedTokenWipe()
                        .payer(asAccountString(payer))
                        .wiping(tokenId, receiver)
                        .txnValidStart(at)
                        .get();
        final var dynamicProperties = mock(GlobalDynamicProperties.class);
        given(dynamicProperties.areNftsEnabled()).willReturn(true);
        given(dynamicProperties.maxBatchSizeWipe()).willReturn(10);
        accessor = new TokenWipeAccessor(signedTxn.toByteArray(), signedTxn, dynamicProperties);
        invokesAccessorBasedUsagesForTxnInHandle(
                signedTxn,
                TokenAccountWipe,
                SubType.TOKEN_NON_FUNGIBLE_UNIQUE,
                TokenType.NON_FUNGIBLE_UNIQUE,
                true);
    }

    @Test
    void invokesAccessorBasedUsagesForTokenMintInHandle() throws Throwable {
        // setup:
        signedTxn =
                newSignedTokenMint()
                        .payer(asAccountString(payer))
                        .minting(tokenId)
                        .txnValidStart(at)
                        .get();

        invokesAccessorBasedUsagesForTxnInHandle(
                signedTxn, TokenMint, SubType.TOKEN_FUNGIBLE_COMMON, TokenType.FUNGIBLE_COMMON);
    }

    @Test
    void invokesOpDelegateAsExpectedWithTwoOptions() throws Exception {
        // setup:
        SigValueObj expectedSigUsage =
                new SigValueObj(
                        FeeBuilder.getSignatureCount(signedTxn),
                        9,
                        FeeBuilder.getSignatureSize(signedTxn));
        FeeObject expectedFees =
                getFeeObject(currentPrices.get(SubType.DEFAULT), resourceUsage, currentRate);

        given(correctOpEstimator.applicableTo(accessor.getTxn())).willReturn(true);
        given(incorrectOpEstimator.applicableTo(accessor.getTxn())).willReturn(false);
        given(txnUsageEstimators.get(CryptoCreate))
                .willReturn(List.of(incorrectOpEstimator, correctOpEstimator));
        given(
                        correctOpEstimator.usageGiven(
                                argThat(accessor.getTxn()::equals),
                                argThat(factory.apply(expectedSigUsage)),
                                argThat(view::equals)))
                .willReturn(resourceUsage);
        given(incorrectOpEstimator.usageGiven(any(), any(), any()))
                .willThrow(RuntimeException.class);
        given(exchange.rate(at)).willReturn(currentRate);
        given(usagePrices.activePrices(accessor)).willThrow(RuntimeException.class);
        given(usagePrices.pricesGiven(CryptoCreate, at)).willReturn(currentPrices);

        // when:
        FeeObject fees = subject.estimateFee(accessor, payerKey, view, at);

        // then:
        assertEquals(fees.getNodeFee(), expectedFees.getNodeFee());
        assertEquals(fees.getNetworkFee(), expectedFees.getNetworkFee());
        assertEquals(fees.getServiceFee(), expectedFees.getServiceFee());
    }

    @Test
    void invokesOpDelegateAsExpectedForEstimateOfUnrecognizable() throws Exception {
        // setup:
        SigValueObj expectedSigUsage =
                new SigValueObj(
                        FeeBuilder.getSignatureCount(signedTxn),
                        9,
                        FeeBuilder.getSignatureSize(signedTxn));

        FeeObject expectedFees =
                getFeeObject(
                        DEFAULT_RESOURCE_PRICES.get(SubType.DEFAULT), resourceUsage, currentRate);

        given(txnUsageEstimators.get(CryptoCreate)).willReturn(List.of(correctOpEstimator));
        given(correctOpEstimator.applicableTo(accessor.getTxn())).willReturn(true);
        given(
                        correctOpEstimator.usageGiven(
                                argThat(accessor.getTxn()::equals),
                                argThat(factory.apply(expectedSigUsage)),
                                argThat(view::equals)))
                .willReturn(resourceUsage);
        given(exchange.rate(at)).willReturn(currentRate);
        given(usagePrices.pricesGiven(CryptoCreate, at)).willThrow(RuntimeException.class);

        // when:
        FeeObject fees = subject.estimateFee(accessor, payerKey, view, at);

        // then:
        assertEquals(fees.getNodeFee(), expectedFees.getNodeFee());
        assertEquals(fees.getNetworkFee(), expectedFees.getNetworkFee());
        assertEquals(fees.getServiceFee(), expectedFees.getServiceFee());
    }

    private final Function<SigValueObj, ArgumentMatcher<SigValueObj>> factory =
            expectedSigUsage ->
                    sigUsage ->
                            expectedSigUsage.getSignatureSize() == sigUsage.getSignatureSize()
                                    && expectedSigUsage.getPayerAcctSigCount()
                                            == sigUsage.getPayerAcctSigCount()
                                    && expectedSigUsage.getSignatureSize()
                                            == sigUsage.getSignatureSize();

    private class NestedMultiplierSource implements FeeMultiplierSource {
        @Override
        public long currentMultiplier(final TxnAccessor accessor) {
            return suggestedMultiplier.get();
        }

        @Override
        public void resetExpectations() {
            /* No-op */
        }

        @Override
        public void updateMultiplier(final TxnAccessor accessor, Instant consensusNow) {
            /* No-op. */
        }

        @Override
        public void resetCongestionLevelStarts(Instant[] savedStartTimes) {
            /* No-op. */
        }

        @Override
        public Instant[] congestionLevelStarts() {
            return new Instant[0];
        }
    }

    public static void copyData(FeeData feeData, UsageAccumulator into) {
        into.setNumPayerKeys(feeData.getNodedata().getVpt());
        into.addVpt(feeData.getNetworkdata().getVpt());
        into.addBpt(feeData.getNetworkdata().getBpt());
        into.addBpr(feeData.getNodedata().getBpr());
        into.addSbpr(feeData.getNodedata().getSbpr());
        into.addNetworkRbs(feeData.getNetworkdata().getRbh() * HRS_DIVISOR);
        into.addRbs(feeData.getServicedata().getRbh() * HRS_DIVISOR);
        into.addSbs(feeData.getServicedata().getSbh() * HRS_DIVISOR);
    }

    void invokesAccessorBasedUsagesForTxnInHandle(
            final Transaction signedTxn,
            final HederaFunctionality function,
            final SubType subType,
            final TokenType tokenType) {
        invokesAccessorBasedUsagesForTxnInHandle(signedTxn, function, subType, tokenType, false);
    }

    void invokesAccessorBasedUsagesForTxnInHandle(
            final Transaction signedTxn,
            final HederaFunctionality function,
            final SubType subType,
            final TokenType tokenType,
            final boolean isCustomAccessor) {
        if (!isCustomAccessor) {
            accessor = SignedTxnAccessor.uncheckedFrom(signedTxn);
        }
        // and:
        final var expectedFees =
                getFeeObject(currentPrices.get(subType), resourceUsage, currentRate);

        given(pricedUsageCalculator.supports(function)).willReturn(true);
        given(exchange.activeRate(consensusNow)).willReturn(currentRate);
        given(usagePrices.activePrices(accessor)).willReturn(currentPrices);
        given(
                        pricedUsageCalculator.inHandleFees(
                                accessor, currentPrices.get(subType), currentRate, payerKey))
                .willReturn(expectedFees);
        given(view.tokenType(tokenId)).willReturn(Optional.of(tokenType));

        // when:
        FeeObject fees = subject.computeFee(accessor, payerKey, view, consensusNow);

        // then:
        assertNotNull(fees);
        assertEquals(fees.getNodeFee(), expectedFees.getNodeFee());
        assertEquals(fees.getNetworkFee(), expectedFees.getNetworkFee());
        assertEquals(fees.getServiceFee(), expectedFees.getServiceFee());
    }
}
