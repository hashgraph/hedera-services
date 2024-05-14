/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.handlers.transfer.customfees;

import static com.hedera.hapi.node.base.TokenType.FUNGIBLE_COMMON;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.service.token.impl.test.handlers.transfer.AccountAmountUtils.asAccountWithAlias;
import static com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase.withFixedFee;
import static com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase.withFractionalFee;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TransferUtil.asAccountAmount;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TransferUtil.asNftTransferList;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TransferUtil.asTokenTransferList;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TransferUtil.asTransferList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.hapi.node.transaction.FractionalFee;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AssessmentResult;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeMeta;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFixedFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFractionalFeeAssessor;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CustomFractionalFeeAssessorTest {
    @Mock
    private CustomFixedFeeAssessor fixedFeeAssessor;

    private CustomFractionalFeeAssessor subject;
    private AssessmentResult result;

    private final long originalUnits = 100;
    private final AccountID payer = asAccount(4001);
    private final AccountID otherCollector = asAccount(1001);
    private final AccountID targetCollector = asAccount(2001);
    private final AccountID funding = asAccount(98);
    private final TokenID tokenWithFractionalFee = asToken(3000);
    private final AccountID minter = asAccount(6000);
    private final AccountID firstReclaimedAcount = asAccount(8000);
    private final AccountID secondReclaimedAcount = asAccount(9000);
    private final AccountID alias = asAccountWithAlias(Bytes.wrap("01234567890123456789012345678901"));
    private final TokenID nonFungibleTokenId = asToken(70000);

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
    private final TransferList hbarPayerTransferList = asTransferList(originalUnits, payer);
    private final TokenTransferList htsPayerTokenTransferList =
            asTokenTransferList(tokenWithFractionalFee, originalUnits, payer);
    private final TokenTransferList nftTransferList = asNftTransferList(nonFungibleTokenId, payer, funding, 1);
    private final AccountID firstFractionalFeeCollector = asAccount(6666L);
    ;
    private final AccountID secondFractionalFeeCollector = asAccount(7777L);
    ;
    private final AccountID netOfTransfersFeeCollector = asAccount(8888L);
    ;
    final FixedFee fixedFee = FixedFee.newBuilder()
            .denominatingTokenId(tokenWithFractionalFee)
            .amount(1)
            .build();
    final FractionalFee netOfTransferFractionalFee = FractionalFee.newBuilder()
            .maximumAmount(netOfTransfersMaxAmountOfFractionalFee)
            .minimumAmount(netOfTransfersMinAmountOfFractionalFee)
            .netOfTransfers(true)
            .fractionalAmount(Fraction.newBuilder()
                    .numerator(netOfTransfersNumerator)
                    .denominator(netOfTransfersDenominator)
                    .build())
            .build();
    final FractionalFee firstFractionalFeeWithoutNetOfTransfers = FractionalFee.newBuilder()
            .maximumAmount(firstMaxAmountOfFractionalFee)
            .minimumAmount(firstMinAmountOfFractionalFee)
            .netOfTransfers(false)
            .fractionalAmount(Fraction.newBuilder()
                    .numerator(firstNumerator)
                    .denominator(firstDenominator)
                    .build())
            .build();
    final FractionalFee secondFractionalFeeWithoutNetOfTransfers = FractionalFee.newBuilder()
            .maximumAmount(secondMaxAmountOfFractionalFee)
            .minimumAmount(secondMinAmountOfFractionalFee)
            .netOfTransfers(false)
            .fractionalAmount(Fraction.newBuilder()
                    .numerator(secondNumerator)
                    .denominator(secondDenominator)
                    .build())
            .build();

    final FractionalFee exemptFractionalFeeWithoutNetOfTransfers = FractionalFee.newBuilder()
            .maximumAmount(secondMaxAmountOfFractionalFee)
            .minimumAmount(firstMinAmountOfFractionalFee)
            .netOfTransfers(false)
            .fractionalAmount(Fraction.newBuilder()
                    .numerator(firstNumerator)
                    .denominator(firstDenominator)
                    .build())
            .build();

    final FractionalFee nonsenseFractionalFee = FractionalFee.newBuilder()
            .maximumAmount(1)
            .minimumAmount(1)
            .netOfTransfers(false)
            .fractionalAmount(Fraction.newBuilder()
                    .numerator(nonsenseNumerator)
                    .denominator(nonsenseDenominator)
                    .build())
            .build();

    private final CustomFee skippedFixedFee =
            withFixedFee(FixedFee.newBuilder().amount(100L).build(), null, notNetOfTransfers);
    private final CustomFee fractionalCustomFeeNetOfTransfers =
            withFractionalFee(netOfTransferFractionalFee, netOfTransfersFeeCollector, true);
    private final CustomFee firstFractionalCustomFee =
            withFractionalFee(firstFractionalFeeWithoutNetOfTransfers, firstFractionalFeeCollector, false);
    private final CustomFee secondFractionalCustomFee =
            withFractionalFee(secondFractionalFeeWithoutNetOfTransfers, secondFractionalFeeCollector, false);
    private final CustomFee exemptFractionalCustomFee =
            withFractionalFee(exemptFractionalFeeWithoutNetOfTransfers, payer, false);
    private final CustomFee nonsenseCustomFee =
            withFractionalFee(nonsenseFractionalFee, secondFractionalFeeCollector, false);
    private final CustomFeeMeta tokenWithFractionalMeta = new CustomFeeMeta(
            tokenWithFractionalFee,
            minter,
            List.of(
                    firstFractionalCustomFee,
                    secondFractionalCustomFee,
                    exemptFractionalCustomFee,
                    skippedFixedFee,
                    fractionalCustomFeeNetOfTransfers),
            TokenType.FUNGIBLE_COMMON);
    private final TokenTransferList triggerTransferList = asTokenTransferList(
            tokenWithFractionalFee,
            List.of(
                    asAccountAmount(payer, -vanillaTriggerAmount),
                    asAccountAmount(firstReclaimedAcount, +firstCreditAmount),
                    asAccountAmount(secondReclaimedAcount, +secondCreditAmount)));
    //    private final TokenTransferList firstVanillaReclaim =
    //            asTokenTransferList(tokenWithFractionalFee, +firstCreditAmount, firstReclaimedAcount);
    //    private final TokenTransferList secondVanillaReclaim =
    //            asTokenTransferList(tokenWithFractionalFee, +secondCreditAmount, secondReclaimedAcount);
    final AccountID aliasedAccountId =
            AccountID.newBuilder().alias(Bytes.wrap("alias")).build();
    private final TokenTransferList aliasedVanillaReclaim =
            asTokenTransferList(tokenWithFractionalFee, secondCreditAmount, aliasedAccountId);
    private final TokenTransferList wildlyInsufficientChange = asTokenTransferList(tokenWithFractionalFee, -1, payer);
    private final TokenTransferList someCredit = asTokenTransferList(tokenWithFractionalFee, +1, firstReclaimedAcount);
    private final AccountID[] effPayerAccountNums = new AccountID[] {firstReclaimedAcount, secondReclaimedAcount};

    @BeforeEach
    void setUp() {
        subject = new CustomFractionalFeeAssessor(fixedFeeAssessor);
    }

    @Test
    void appliesFeesAsExpected() {
        result = new AssessmentResult(List.of(triggerTransferList), List.of());

        final CustomFeeMeta feeMeta = withCustomFeeMeta(
                List.of(
                        firstFractionalCustomFee,
                        secondFractionalCustomFee,
                        exemptFractionalCustomFee,
                        skippedFixedFee,
                        fractionalCustomFeeNetOfTransfers),
                FUNGIBLE_COMMON);

        // firstExpectedFee = 5000 * 1 / 100 = 50
        final var firstExpectedFee = subject.amountOwed(vanillaTriggerAmount, firstFractionalCustomFee.fractionalFee());
        final var secondExpectedFee =
                subject.amountOwed(vanillaTriggerAmount, secondFractionalCustomFee.fractionalFee());

        final var netFee = subject.amountOwed(vanillaTriggerAmount, fractionalCustomFeeNetOfTransfers.fractionalFee());
        final var totalReclaimedFees = firstExpectedFee + secondExpectedFee;
        final var fixedFee = withFixedFee(
                FixedFee.newBuilder()
                        .denominatingTokenId(tokenWithFractionalFee)
                        .amount(netFee)
                        .build(),
                netOfTransfersFeeCollector,
                true);

        final var expectedAssessedFee1 = AssessedCustomFee.newBuilder()
                .feeCollectorAccountId(firstFractionalFeeCollector)
                .effectivePayerAccountId(effPayerAccountNums)
                .amount(firstExpectedFee)
                .tokenId(tokenWithFractionalFee)
                .build();
        final var expectedAssessedFee2 = AssessedCustomFee.newBuilder()
                .feeCollectorAccountId(secondFractionalFeeCollector)
                .effectivePayerAccountId(effPayerAccountNums)
                .amount(secondExpectedFee)
                .tokenId(tokenWithFractionalFee)
                .build();

        subject.assessFractionalFees(feeMeta, payer, result);

        verify(fixedFeeAssessor).assessFixedFee(feeMeta, payer, fixedFee, result);

        assertThat(result.getAssessedCustomFees()).isNotEmpty();
        assertThat(result.getAssessedCustomFees()).contains(expectedAssessedFee1);
        assertThat(result.getAssessedCustomFees()).contains(expectedAssessedFee2);

        assertThat(result.getRoyaltiesPaid()).isEmpty();
    }

    @Test
    void nonNetOfTransfersWithoutExemptions() {
        result = new AssessmentResult(List.of(triggerTransferList), List.of());

        final CustomFeeMeta feeMeta = withCustomFeeMeta(List.of(firstFractionalCustomFee), FUNGIBLE_COMMON);

        // firstExpectedFee = 5000 * 1 / 100 = 50
        final var firstExpectedFee = subject.amountOwed(vanillaTriggerAmount, firstFractionalCustomFee.fractionalFee());
        // secondExpectedFee = 5000 * 1 / 10 = 500
        final var secondExpectedFee =
                subject.amountOwed(vanillaTriggerAmount, secondFractionalCustomFee.fractionalFee());
        // netFee = 5000 * 2 / 101 = 99
        final var netFee = subject.amountOwed(vanillaTriggerAmount, fractionalCustomFeeNetOfTransfers.fractionalFee());
        final var totalReclaimedFees = firstExpectedFee + secondExpectedFee;
        final var fixedFee = withFixedFee(
                FixedFee.newBuilder()
                        .denominatingTokenId(tokenWithFractionalFee)
                        .amount(netFee)
                        .build(),
                netOfTransfersFeeCollector,
                true);

        // first assessed fee for fee collector is fraction of credit amounts 4000, 1000. So it is 50
        final var expectedAssessedFee1 = AssessedCustomFee.newBuilder()
                .feeCollectorAccountId(firstFractionalFeeCollector)
                .effectivePayerAccountId(effPayerAccountNums)
                .amount(firstExpectedFee)
                .tokenId(tokenWithFractionalFee)
                .build();

        subject.assessFractionalFees(feeMeta, payer, result);

        verify(fixedFeeAssessor, never()).assessFixedFee(feeMeta, payer, fixedFee, result);
        assertThat(result.getAssessedCustomFees()).hasSize(1);
        assertThat(result.getAssessedCustomFees()).contains(expectedAssessedFee1);

        assertThat(result.getRoyaltiesPaid()).isEmpty();
    }

    @Test
    void nonNetOfTransfersRespectExemptions() {
        result = new AssessmentResult(List.of(triggerTransferList), List.of());
        // two custom fees set. First sender is exempt from fees
        final CustomFeeMeta feeMeta = withCustomFeeMeta(
                List.of(
                        firstFractionalCustomFee
                                .copyBuilder()
                                .feeCollectorAccountId(payer)
                                .build(),
                        secondFractionalCustomFee),
                FUNGIBLE_COMMON);

        subject.assessFractionalFees(feeMeta, payer, result);

        // firstExpectedFee is ignored due to the fee collector being same as the payer
        // secondExpectedFee = 5000 * 1 / 10 = 500
        final var secondExpectedFee =
                subject.amountOwed(vanillaTriggerAmount, secondFractionalCustomFee.fractionalFee());

        // first assessed fee for fee collector is fraction of credit amounts 4000, 1000. So it is 50
        final var expectedAssessedFee1 = AssessedCustomFee.newBuilder()
                .feeCollectorAccountId(secondFractionalFeeCollector)
                .effectivePayerAccountId(effPayerAccountNums)
                .amount(secondExpectedFee)
                .tokenId(tokenWithFractionalFee)
                .build();

        // This is not Net of transfers fee, so it is not assessed
        verify(fixedFeeAssessor, never()).assessFixedFee(any(), any(), any(), any());
        assertThat(result.getAssessedCustomFees()).hasSize(1);
        assertThat(result.getAssessedCustomFees()).contains(expectedAssessedFee1);

        assertThat(result.getRoyaltiesPaid()).isEmpty();
    }

    @Test
    void doesNothingIfNoValueExchangedAndNoFallback() {
        final var transfersList =
                asTokenTransferList(tokenWithFractionalFee, List.of(asAccountAmount(payer, -vanillaTriggerAmount)));
        result = new AssessmentResult(List.of(transfersList), List.of());

        final CustomFeeMeta feeMeta = withCustomFeeMeta(
                List.of(
                        firstFractionalCustomFee,
                        secondFractionalCustomFee,
                        exemptFractionalCustomFee,
                        skippedFixedFee,
                        fractionalCustomFeeNetOfTransfers),
                FUNGIBLE_COMMON);

        subject.assessFractionalFees(feeMeta, payer, result);

        verify(fixedFeeAssessor, never()).assessFixedFee(any(), any(), any(), any());
        assertThat(result.getAssessedCustomFees()).isEmpty();
        assertThat(result.getRoyaltiesPaid()).isEmpty();
    }

    public CustomFeeMeta withCustomFeeMeta(List<CustomFee> customFees, TokenType tokenType) {
        return new CustomFeeMeta(tokenWithFractionalFee, minter, customFees, tokenType);
    }
}
