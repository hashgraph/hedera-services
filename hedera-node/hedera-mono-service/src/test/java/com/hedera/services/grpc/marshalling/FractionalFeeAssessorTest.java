/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.grpc.marshalling;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.fees.CustomFeePayerExemptions;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.NftTransfer;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FractionalFeeAssessorTest {
    private final List<FcAssessedCustomFee> accumulator = new ArrayList<>();

    @Mock private BalanceChangeManager changeManager;
    @Mock private FixedFeeAssessor fixedFeeAssessor;
    @Mock private CustomFeePayerExemptions customFeePayerExemptions;

    private FractionalFeeAssessor subject;

    @BeforeEach
    void setUp() {
        subject = new FractionalFeeAssessor(fixedFeeAssessor, customFeePayerExemptions);
    }

    @Test
    void appliesFeesAsExpected() {
        // setup:
        final var feeMeta =
                newCustomFeeMeta(
                        tokenWithFractionalFee,
                        List.of(
                                firstFractionalFee,
                                secondFractionalFee,
                                exemptFractionalFee,
                                skippedFixedFee,
                                fractionalFeeNetOfTransfers));
        final var firstCollectorChange =
                BalanceChange.tokenAdjust(
                        firstFractionalFeeCollector.asId(), tokenWithFractionalFee, 0L);
        final var secondCollectorChange =
                BalanceChange.tokenAdjust(
                        secondFractionalFeeCollector.asId(), tokenWithFractionalFee, 0L);
        final var credits = List.of(firstVanillaReclaim, secondVanillaReclaim);
        // and:
        final var firstExpectedFee =
                subject.amountOwedGiven(
                        vanillaTriggerAmount, firstFractionalFee.getFractionalFeeSpec());
        final var secondExpectedFee =
                subject.amountOwedGiven(
                        vanillaTriggerAmount, secondFractionalFee.getFractionalFeeSpec());
        final var netFee =
                subject.amountOwedGiven(
                        vanillaTriggerAmount, fractionalFeeNetOfTransfers.getFractionalFeeSpec());
        final var totalReclaimedFees = firstExpectedFee + secondExpectedFee;
        // and:
        final var expFirstAssess =
                new FcAssessedCustomFee(
                        firstFractionalFeeCollector,
                        tokenWithFractionalFee.asEntityId(),
                        firstExpectedFee,
                        effPayerAccountNums);
        final var expSecondAssess =
                new FcAssessedCustomFee(
                        secondFractionalFeeCollector,
                        tokenWithFractionalFee.asEntityId(),
                        secondExpectedFee,
                        effPayerAccountNums);

        given(changeManager.changeFor(firstFractionalFeeCollector.asId(), tokenWithFractionalFee))
                .willReturn(firstCollectorChange);
        given(changeManager.changeFor(secondFractionalFeeCollector.asId(), tokenWithFractionalFee))
                .willReturn(secondCollectorChange);
        given(changeManager.creditsInCurrentLevel(tokenWithFractionalFee)).willReturn(credits);

        // when:
        final var result =
                subject.assessAllFractional(vanillaTrigger, feeMeta, changeManager, accumulator);

        // then:
        assertEquals(OK, result);
        // and:
        assertEquals(
                firstCreditAmount - (totalReclaimedFees * 4 / 5),
                firstVanillaReclaim.getAggregatedUnits());
        assertEquals(
                secondCreditAmount - (totalReclaimedFees / 5),
                secondVanillaReclaim.getAggregatedUnits());
        // and:
        assertEquals(firstExpectedFee, firstCollectorChange.getAggregatedUnits());
        assertEquals(secondExpectedFee, secondCollectorChange.getAggregatedUnits());
        // and:
        assertEquals(2, accumulator.size());
        assertEquals(expFirstAssess, accumulator.get(0));
        assertEquals(expSecondAssess, accumulator.get(1));
        // and:
        verify(fixedFeeAssessor)
                .assess(
                        payer,
                        tokenWithFractionalMeta,
                        FcCustomFee.fixedFee(
                                netFee,
                                tokenWithFractionalFee.asEntityId(),
                                netOfTransfersFeeCollector,
                                true),
                        changeManager,
                        accumulator);
    }

    @Test
    void nonNetOfTransfersRespectExemptions() {
        // setup:
        final var feeMeta = newCustomFeeMeta(tokenWithFractionalFee, List.of(firstFractionalFee));
        final var collectorChange =
                BalanceChange.tokenAdjust(
                        firstFractionalFeeCollector.asId(), tokenWithFractionalFee, 0L);
        final var credits = List.of(firstVanillaReclaim, secondVanillaReclaim);
        // and:
        final var expectedReclaimed =
                subject.amountOwedGiven(
                        vanillaTriggerAmount, firstFractionalFee.getFractionalFeeSpec());
        final var expectedAssess =
                new FcAssessedCustomFee(
                        firstFractionalFeeCollector,
                        tokenWithFractionalFee.asEntityId(),
                        expectedReclaimed,
                        // The first candidate payer is going to be exempt
                        new long[] {secondVanillaReclaim.getAccount().num()});
        given(
                        customFeePayerExemptions.isPayerExempt(
                                feeMeta, firstFractionalFee, firstVanillaReclaim.getAccount()))
                .willReturn(true);
        given(
                        customFeePayerExemptions.isPayerExempt(
                                feeMeta, firstFractionalFee, secondVanillaReclaim.getAccount()))
                .willReturn(false);

        given(changeManager.changeFor(firstFractionalFeeCollector.asId(), tokenWithFractionalFee))
                .willReturn(collectorChange);
        given(changeManager.creditsInCurrentLevel(tokenWithFractionalFee)).willReturn(credits);

        // when:
        final var result =
                subject.assessAllFractional(vanillaTrigger, feeMeta, changeManager, accumulator);

        // then:
        assertEquals(OK, result);
        // and:
        assertEquals(firstCreditAmount, firstVanillaReclaim.getAggregatedUnits());
        assertEquals(
                secondCreditAmount - expectedReclaimed, secondVanillaReclaim.getAggregatedUnits());
        // and:
        assertEquals(expectedReclaimed, collectorChange.getAggregatedUnits());
        // and:
        assertEquals(1, accumulator.size());
        assertEquals(expectedAssess, accumulator.get(0));
    }

    @Test
    void ifNonNetOfTransfersFindsAllExemptThenNoFeesAssessed() {
        // setup:
        final var feeMeta = newCustomFeeMeta(tokenWithFractionalFee, List.of(firstFractionalFee));
        final var collectorChange =
                BalanceChange.tokenAdjust(
                        firstFractionalFeeCollector.asId(), tokenWithFractionalFee, 0L);
        final var credits = List.of(firstVanillaReclaim, secondVanillaReclaim);
        given(
                        customFeePayerExemptions.isPayerExempt(
                                feeMeta, firstFractionalFee, firstVanillaReclaim.getAccount()))
                .willReturn(true);
        given(
                        customFeePayerExemptions.isPayerExempt(
                                feeMeta, firstFractionalFee, secondVanillaReclaim.getAccount()))
                .willReturn(true);

        given(changeManager.creditsInCurrentLevel(tokenWithFractionalFee)).willReturn(credits);

        // when:
        final var result =
                subject.assessAllFractional(vanillaTrigger, feeMeta, changeManager, accumulator);

        // then:
        assertEquals(OK, result);
        assertEquals(0, accumulator.size());
    }

    @Test
    void abortsOnCreditOverflow() {
        // setup:
        final var feeMeta =
                newCustomFeeMeta(
                        tokenWithFractionalFee, List.of(firstFractionalFee, secondFractionalFee));
        final var credits = List.of(firstVanillaReclaim, secondVanillaReclaim);
        // and:
        firstVanillaReclaim.aggregateUnits(Long.MAX_VALUE / 2);
        secondVanillaReclaim.aggregateUnits(Long.MAX_VALUE / 2);

        given(changeManager.creditsInCurrentLevel(tokenWithFractionalFee)).willReturn(credits);

        // when:
        final var result =
                subject.assessAllFractional(vanillaTrigger, feeMeta, changeManager, accumulator);

        // then:
        assertEquals(CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE, result);
    }

    @Test
    void cannotOverflowWithCrazyFraction() {
        // setup:
        final var feeMeta =
                newCustomFeeMeta(tokenWithFractionalFee, List.of(nonsenseFee, secondFractionalFee));
        final var credits = List.of(firstVanillaReclaim, secondVanillaReclaim);
        given(changeManager.creditsInCurrentLevel(tokenWithFractionalFee)).willReturn(credits);

        // when:
        final var result =
                subject.assessAllFractional(vanillaTrigger, feeMeta, changeManager, accumulator);

        // then:
        assertEquals(CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE, result);
    }

    @Test
    void failsWithInsufficientBalanceWhenAppropos() {
        // setup:
        final var feeMeta =
                newCustomFeeMeta(
                        tokenWithFractionalFee, List.of(firstFractionalFee, secondFractionalFee));
        final var credits = List.of(someCredit);
        given(changeManager.creditsInCurrentLevel(tokenWithFractionalFee)).willReturn(credits);

        // when:
        final var result =
                subject.assessAllFractional(
                        wildlyInsufficientChange, feeMeta, changeManager, accumulator);

        // then:
        assertEquals(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE, result);
    }

    @Test
    void failsFastOnPositiveAdjustment() {
        // given:
        vanillaTrigger.aggregateUnits(Long.MAX_VALUE);
        final CustomFeeMeta feeMeta = newCustomFeeMeta(tokenWithFractionalFee, List.of());

        // expect:
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        subject.assessAllFractional(
                                vanillaTrigger, feeMeta, changeManager, accumulator));
    }

    @Test
    void failsFastOnNonFungibleChange() {
        // setup:
        final var feeMeta =
                newCustomFeeMeta(
                        tokenWithFractionalFee, List.of(firstFractionalFee, secondFractionalFee));

        // when:
        final var result =
                subject.assessAllFractional(nonFungibleChange, feeMeta, changeManager, accumulator);

        // then:
        assertEquals(INVALID_TOKEN_ID, result);
    }

    @Test
    void handlesEasyCase() {
        // given:
        long reasonable = 1_234_567L;
        long n = 10;
        long d = 9;
        // and:
        final var expected = reasonable * n / d;

        // expect:
        assertEquals(expected, AdjustmentUtils.safeFractionMultiply(n, d, reasonable));
    }

    @Test
    void fallsBackToArbitraryPrecisionIfNeeded() {
        // given:
        long huge = Long.MAX_VALUE / 2;
        long n = 10;
        long d = 9;
        // and:
        final var expected =
                BigInteger.valueOf(huge)
                        .multiply(BigInteger.valueOf(n))
                        .divide(BigInteger.valueOf(d))
                        .longValueExact();

        // expect:
        assertEquals(expected, AdjustmentUtils.safeFractionMultiply(n, d, huge));
    }

    @Test
    void propagatesArithmeticExceptionOnOverflow() {
        // given:
        long huge = Long.MAX_VALUE - 1;
        long n = 10;
        long d = 9;

        // expect:
        assertThrows(
                ArithmeticException.class, () -> AdjustmentUtils.safeFractionMultiply(n, d, huge));
    }

    @Test
    void computesVanillaFine() {
        // expect:
        assertEquals(
                vanillaTriggerAmount / firstDenominator,
                subject.amountOwedGiven(
                        vanillaTriggerAmount, firstFractionalFee.getFractionalFeeSpec()));
    }

    @Test
    void enforcesMax() {
        // expect:
        assertEquals(
                firstMaxAmountOfFractionalFee,
                subject.amountOwedGiven(
                        maxApplicableTriggerAmount, firstFractionalFee.getFractionalFeeSpec()));
    }

    @Test
    void enforcesMin() {
        // expect:
        assertEquals(
                firstMinAmountOfFractionalFee,
                subject.amountOwedGiven(
                        minApplicableTriggerAmount, firstFractionalFee.getFractionalFeeSpec()));
    }

    @Test
    void reclaimsAsExpected() {
        // setup:
        final long reclaimAmount = 1000L;
        final List<BalanceChange> credits = List.of(firstVanillaReclaim, secondVanillaReclaim);
        // and:
        final var expFromFirst = reclaimAmount * firstCreditAmount / vanillaTriggerAmount;
        final var expFromSecond = reclaimAmount * secondCreditAmount / vanillaTriggerAmount;

        // when:
        subject.reclaim(1000L, credits);

        // then:
        assertEquals(firstCreditAmount - expFromFirst, firstVanillaReclaim.getAggregatedUnits());
        assertEquals(secondCreditAmount - expFromSecond, secondVanillaReclaim.getAggregatedUnits());
    }

    @Test
    void reclaimsRemainderAsExpected() {
        // setup:
        final List<BalanceChange> credits = List.of(firstVanillaReclaim, secondVanillaReclaim);
        firstVanillaReclaim.aggregateUnits(12_345L);
        secondVanillaReclaim.aggregateUnits(2_345L);
        final var perturbedFirstCreditAmount = firstVanillaReclaim.getAggregatedUnits();
        final var perturbedSecondCreditAmount = secondVanillaReclaim.getAggregatedUnits();
        // and:
        final var expFromFirst = 831;
        final var expFromSecond = 169;

        // when:
        subject.reclaim(1000L, credits);

        // then:
        assertEquals(
                perturbedFirstCreditAmount - expFromFirst,
                firstVanillaReclaim.getAggregatedUnits());
        assertEquals(
                perturbedSecondCreditAmount - expFromSecond,
                secondVanillaReclaim.getAggregatedUnits());
    }

    private CustomFeeMeta newCustomFeeMeta(Id tokenId, List<FcCustomFee> customFees) {
        return new CustomFeeMeta(tokenId, treasury, customFees);
    }

    private final Id payer = new Id(0, 0, 2);
    private final Id firstReclaimedAcount = new Id(0, 0, 8);
    private final Id secondReclaimedAcount = new Id(0, 0, 9);
    private final long[] effPayerAccountNums = new long[] {8L, 9L};
    private final long vanillaTriggerAmount = 5000L;
    private final long firstCreditAmount = 4000L;
    private final long secondCreditAmount = 1000L;
    private final long minApplicableTriggerAmount = 50L;
    private final long maxApplicableTriggerAmount = 50_000L;
    private final long firstMinAmountOfFractionalFee = 2L;
    private final long firstMaxAmountOfFractionalFee = 100L;
    private final long firstNumerator = 1L;
    private final long firstDenominator = 100L;
    private final long netOfTransfersMinAmountOfFractionalFee = 3L;
    private final long netOfTransfersMaxAmountOfFractionalFee = 101L;
    private final long netOfTransfersNumerator = 2L;
    private final long netOfTransfersDenominator = 101L;
    private final long secondMinAmountOfFractionalFee = 10L;
    private final long secondMaxAmountOfFractionalFee = 1000L;
    private final long secondNumerator = 1L;
    private final long secondDenominator = 10L;
    private final long nonsenseNumerator = Long.MAX_VALUE;
    private final long nonsenseDenominator = 1L;
    private final boolean notNetOfTransfers = false;
    private final Id tokenWithFractionalFee = new Id(1, 2, 3);
    private final Id treasury = new Id(5, 6, 7777);
    private final EntityId firstFractionalFeeCollector = new EntityId(4, 5, 6);
    private final EntityId secondFractionalFeeCollector = new EntityId(5, 6, 7);
    private final EntityId netOfTransfersFeeCollector = new EntityId(6, 7, 8);
    private final FcCustomFee skippedFixedFee =
            FcCustomFee.fixedFee(
                    100L, EntityId.MISSING_ENTITY_ID, EntityId.MISSING_ENTITY_ID, false);
    private final FcCustomFee fractionalFeeNetOfTransfers =
            FcCustomFee.fractionalFee(
                    netOfTransfersNumerator,
                    netOfTransfersDenominator,
                    netOfTransfersMinAmountOfFractionalFee,
                    netOfTransfersMaxAmountOfFractionalFee,
                    !notNetOfTransfers,
                    netOfTransfersFeeCollector,
                    true);
    private final FcCustomFee firstFractionalFee =
            FcCustomFee.fractionalFee(
                    firstNumerator,
                    firstDenominator,
                    firstMinAmountOfFractionalFee,
                    firstMaxAmountOfFractionalFee,
                    notNetOfTransfers,
                    firstFractionalFeeCollector,
                    false);
    private final FcCustomFee secondFractionalFee =
            FcCustomFee.fractionalFee(
                    secondNumerator,
                    secondDenominator,
                    secondMinAmountOfFractionalFee,
                    secondMaxAmountOfFractionalFee,
                    notNetOfTransfers,
                    secondFractionalFeeCollector,
                    false);
    private final FcCustomFee exemptFractionalFee =
            FcCustomFee.fractionalFee(
                    firstNumerator,
                    secondDenominator,
                    firstMinAmountOfFractionalFee,
                    secondMaxAmountOfFractionalFee,
                    notNetOfTransfers,
                    payer.asEntityId(),
                    false);
    private final FcCustomFee nonsenseFee =
            FcCustomFee.fractionalFee(
                    nonsenseNumerator,
                    nonsenseDenominator,
                    1,
                    1,
                    notNetOfTransfers,
                    secondFractionalFeeCollector,
                    false);
    private final FcCustomFee exemptCustomFractionalFee =
            FcCustomFee.fractionalFee(
                    secondNumerator,
                    secondDenominator,
                    secondMinAmountOfFractionalFee,
                    secondMaxAmountOfFractionalFee,
                    true,
                    secondFractionalFeeCollector,
                    true);
    private final CustomFeeMeta tokenWithFractionalMeta =
            new CustomFeeMeta(
                    tokenWithFractionalFee,
                    treasury,
                    List.of(
                            firstFractionalFee,
                            secondFractionalFee,
                            exemptFractionalFee,
                            skippedFixedFee,
                            fractionalFeeNetOfTransfers));
    private final BalanceChange vanillaTrigger =
            BalanceChange.tokenAdjust(payer, tokenWithFractionalFee, -vanillaTriggerAmount);
    private final BalanceChange firstVanillaReclaim =
            BalanceChange.tokenAdjust(
                    firstReclaimedAcount, tokenWithFractionalFee, +firstCreditAmount);
    private final BalanceChange secondVanillaReclaim =
            BalanceChange.tokenAdjust(
                    secondReclaimedAcount, tokenWithFractionalFee, +secondCreditAmount);
    private final BalanceChange wildlyInsufficientChange =
            BalanceChange.tokenAdjust(payer, tokenWithFractionalFee, -1);
    private final BalanceChange someCredit =
            BalanceChange.tokenAdjust(firstReclaimedAcount, tokenWithFractionalFee, +1);

    private final NftTransfer ownershipChange =
            NftTransfer.newBuilder()
                    .setSenderAccountID(payer.asGrpcAccount())
                    .setReceiverAccountID(firstReclaimedAcount.asGrpcAccount())
                    .setSerialNumber(666)
                    .build();

    private final BalanceChange nonFungibleChange =
            BalanceChange.changingNftOwnership(
                    tokenWithFractionalFee,
                    tokenWithFractionalFee.asGrpcToken(),
                    ownershipChange,
                    payer.asGrpcAccount());
}
