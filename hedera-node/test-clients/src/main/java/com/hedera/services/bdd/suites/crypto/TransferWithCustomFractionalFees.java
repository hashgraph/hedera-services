/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.*;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.*;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.OptionalLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(CRYPTO)
public class TransferWithCustomFractionalFees extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TransferWithCustomFractionalFees.class);
    private static final long tokenTotal = 1_000L;
    private static final long numerator = 1L;
    private static final long denominator = 10L;
    private static final long minHtsFee = 2L;
    private static final long maxHtsFee = 10L;

    private static final String token = "withCustomFees";
    private static final String token2 = "withCustomFees2";
    private static final String hbarCollector = "hbarFee";
    private static final String htsCollector = "denomFee";
    private static final String tokenReceiver = "receiver";
    private static final String tokenTreasury = "tokenTreasury";
    private static final String spender = "spender";
    private static final String tokenOwner = "tokenOwner";
    private static final String alice = "alice";
    private static final String bob = "bob";
    private static final String carol = "carol";
    private static final String ivan = "ivan";

    public static void main(String... args) {
        new TransferWithCustomFractionalFees().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveTests(), negativeTests());
    }

    private List<HapiSpec> positiveTests() {
        return List.of(
                transferWithFractionalCustomFee(),
                transferWithFractionalCustomFeeNumeratorBiggerThanDenominator(),
                transferWithFractionalCustomFeeBellowMinimumAmount(),
                transferWithFractionalCustomFeeAboveMaximumAmount(),
                transferWithFractionalCustomFeeNetOfTransfers(),
                transferWithFractionalCustomFeeNetOfTransfersBellowMinimumAmount(),
                transferWithFractionalCustomFeeNetOfTransfersAboveMaximumAmount(),
                transferWithFractionalCustomFeeAllowance(),
                transferWithFractionalCustomFeeAllowanceTokenOwnerIsCollector(),
                transferWithFractionalCustomFeesThreeCollectors(),
                transferWithFractionalCustomFeesAllCollectorsExempt(),
                transferWithFractionalCustomFeesDenominatorMin(),
                transferWithFractionalCustomFeesDenominatorMax(),
                transferWithFractionalCustomFeeEqToAmount(),
                transferWithFractionalCustomFeeGreaterThanAmount(),
                transferWithFractionalCustomFeeGreaterThanAmountNetOfTransfers(),
                transferWithFractionalCustomFeeMultipleRecipientsShouldRoundFee(),
                transferWithFractionalCustomFeeMultipleRecipientsShouldRoundFeeNetOfTransfers(),
                transferMultipleTimesWithFractionalFeeTakenFromSender(),
                transferMultipleTimesWithFractionalFeeTakenFromReceiver());
    }

    private List<HapiSpec> negativeTests() {
        return List.of(new HapiSpec[] {
            transferWithFractionalCustomFeeNegativeMoreThanTen(),
            transferWithFractionalCustomFeeZeroDenominator(),
            transferWithFractionalCustomFeeNegativeNotEnoughAllowance(),
            transferWithFractionalCustomFeeGreaterThanAmountNegative(),
            transferWithFractionalCustomFeeNotEnoughBalance(),
            transferWithFractionalCustomFeeMultipleRecipientsHasNotEnoughBalance(),
            transferWithFractionalCustomFeeMultipleRecipientsNotEnoughBalance()
        });
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeNegativeMoreThanTen() {
        return defaultHapiSpec("transferWithFractionalCustomFeeNegativeMoreThanTen")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS))
                .when(tokenCreate(token)
                        .treasury(tokenTreasury)
                        .initialSupply(tokenTotal)
                        .payingWith(htsCollector)
                        .withCustom(fractionalFee(
                                numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector))
                        .withCustom(fractionalFee(
                                numerator, denominator + 1, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector))
                        .withCustom(fractionalFee(
                                numerator, denominator + 2, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector))
                        .withCustom(fractionalFee(
                                numerator, denominator + 3, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector))
                        .withCustom(fractionalFee(
                                numerator, denominator + 4, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector))
                        .withCustom(fractionalFee(
                                numerator, denominator + 5, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector))
                        .withCustom(fractionalFee(
                                numerator, denominator + 6, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector))
                        .withCustom(fractionalFee(
                                numerator, denominator + 7, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector))
                        .withCustom(fractionalFee(
                                numerator, denominator + 8, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector))
                        .withCustom(fractionalFee(
                                numerator, denominator + 9, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector))
                        .withCustom(fractionalFee(
                                numerator, denominator + 10, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector))
                        .hasKnownStatus(ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG))
                .then();
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFee() {
        return defaultHapiSpec("transferWithFractionalCustomFee")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFee(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(100L, token).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 900L),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 90L),
                        getAccountBalance(htsCollector).hasTokenBalance(token, 10L));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeZeroDenominator() {
        return defaultHapiSpec("transferWithFractionalCustomFeeZeroDenominator")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFee(
                                        numerator, 0, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector))
                                .hasKnownStatus(ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO))
                .when()
                .then();
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeNumeratorBiggerThanDenominator() {
        return defaultHapiSpec("transferWithFractionalCustomFeeNumeratorBiggerThanDenominator")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFeeNetOfTransfers(
                                        3, 1, minHtsFee, OptionalLong.of(50), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(10L, token).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 960L),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 10L),
                        getAccountBalance(htsCollector).hasTokenBalance(token, 30L));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeNetOfTransfers() {
        return defaultHapiSpec("transferWithFractionalCustomFeeNetOfTransfers")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFeeNetOfTransfers(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(100L, token).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 890L),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 100L),
                        getAccountBalance(htsCollector).hasTokenBalance(token, 10L));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeBellowMinimumAmount() {
        return defaultHapiSpec("transferWithFractionalCustomFeeBellowMinimumAmount")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(
                                        fractionalFee(numerator, denominator, 20L, OptionalLong.of(30L), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(100L, token).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 900L),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 80L),
                        getAccountBalance(htsCollector).hasTokenBalance(token, 20L));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeAboveMaximumAmount() {
        return defaultHapiSpec("transferWithFractionalCustomFeeAboveMaximumAmount")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFee(
                                        numerator, denominator, minHtsFee, OptionalLong.of(9L), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(100L, token).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 900),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 91),
                        getAccountBalance(htsCollector).hasTokenBalance(token, 9L));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeNetOfTransfersBellowMinimumAmount() {
        return defaultHapiSpec("transferWithFractionalCustomFeeNetOfTransfersBellowMinimumAmount")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFeeNetOfTransfers(
                                        numerator, denominator, 20L, OptionalLong.of(30), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(100L, token).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 880L),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 100L),
                        getAccountBalance(htsCollector).hasTokenBalance(token, 20L));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeNetOfTransfersAboveMaximumAmount() {
        return defaultHapiSpec("transferWithFractionalCustomFeeNetOfTransfersAboveMaximumAmount")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFeeNetOfTransfers(
                                        numerator, denominator, minHtsFee, OptionalLong.of(9L), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(100L, token).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 891L),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 100L),
                        getAccountBalance(htsCollector).hasTokenBalance(token, 9L));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeAllowance() {
        return defaultHapiSpec("transferWithFractionalCustomFeeAllowance")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(spender).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFee(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        tokenAssociate(spender, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(tokenOwner)
                                .addTokenAllowance(tokenOwner, token, spender, 120L)
                                .signedBy(tokenOwner)
                                .fee(ONE_HUNDRED_HBARS),
                        getAccountDetails(tokenOwner)
                                .has(accountDetailsWith().tokenAllowancesContaining(token, spender, 120L)),
                        cryptoTransfer(movingWithAllowance(100L, token).between(tokenOwner, tokenReceiver))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(spender)
                                .signedBy(spender))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, tokenTotal - 100L),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 100L - 100L * numerator / denominator),
                        getAccountBalance(htsCollector).hasTokenBalance(token, 100L * numerator / denominator),
                        getAccountDetails(tokenOwner)
                                .has(accountDetailsWith().tokenAllowancesContaining(token, spender, 20L)));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeAllowanceNetOfTransfers() {
        return defaultHapiSpec("transferWithFractionalCustomFeeAllowanceNetOfTransfers")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(spender).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFeeNetOfTransfers(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        tokenAssociate(spender, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(tokenOwner)
                                .addTokenAllowance(tokenOwner, token, spender, 120L)
                                .signedBy(tokenOwner)
                                .fee(ONE_HUNDRED_HBARS),
                        getAccountDetails(tokenOwner)
                                .has(accountDetailsWith().tokenAllowancesContaining(token, spender, 120L)),
                        cryptoTransfer(movingWithAllowance(100L, token).between(tokenOwner, tokenReceiver))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(spender)
                                .signedBy(spender))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, tokenTotal - 110L),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 100L),
                        getAccountBalance(htsCollector).hasTokenBalance(token, 100L * numerator / denominator),
                        getAccountDetails(tokenOwner)
                                .has(accountDetailsWith().tokenAllowancesContaining(token, spender, 10L)));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeAllowanceTokenOwnerIsCollector() {
        return defaultHapiSpec("transferWithFractionalCustomFeeAllowanceTokenOwnerIsCollector")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(spender).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(tokenOwner)
                                .signedBy(tokenOwner, tokenTreasury)
                                .withCustom(fractionalFee(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), tokenOwner)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(spender, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(tokenOwner)
                                .addTokenAllowance(tokenOwner, token, spender, 120L)
                                .signedBy(tokenOwner)
                                .fee(ONE_HUNDRED_HBARS),
                        getAccountDetails(tokenOwner)
                                .has(accountDetailsWith().tokenAllowancesContaining(token, spender, 120L)),
                        cryptoTransfer(movingWithAllowance(100L, token).between(tokenOwner, tokenReceiver))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(spender)
                                .signedBy(spender))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, tokenTotal - 100L),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 100L),
                        getAccountDetails(tokenOwner)
                                .has(accountDetailsWith().tokenAllowancesContaining(token, spender, 20L)));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeNegativeNotEnoughAllowance() {
        return defaultHapiSpec("transferWithFractionalCustomFeeNegativeNotEnoughAllowance")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(spender).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFee(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        tokenAssociate(spender, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(tokenOwner)
                                .addTokenAllowance(tokenOwner, token, spender, 1)
                                .fee(ONE_HUNDRED_HBARS),
                        cryptoTransfer(moving(100L, token).between(spender, tokenReceiver))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(spender)
                                .hasKnownStatus(ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 1000L),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 0L),
                        getAccountBalance(htsCollector).hasTokenBalance(token, 0L));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeesThreeCollectors() {
        return defaultHapiSpec("transferWithFractionalCustomFeesThreeCollectors")
                .given(
                        cryptoCreate(alice),
                        cryptoCreate(bob),
                        cryptoCreate(carol),
                        cryptoCreate(tokenTreasury).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(tokenTreasury)
                                .signedBy(alice, bob, carol, tokenTreasury)
                                .withCustom(fractionalFee(numerator, 2L, minHtsFee, OptionalLong.of(10000), alice))
                                .withCustom(fractionalFee(numerator, 4L, minHtsFee, OptionalLong.of(10000), bob))
                                .withCustom(fractionalFee(numerator, 8L, minHtsFee, OptionalLong.of(10000), carol)))
                .when(
                        cryptoTransfer(moving(100, token).between(tokenTreasury, alice)),
                        cryptoTransfer(moving(100 / 2, token).between(alice, bob)),
                        cryptoTransfer(moving(100 / 4, token).between(bob, carol)))
                .then(
                        getAccountBalance(alice).hasTokenBalance(token, 100 / 2 + 100 / 8),
                        getAccountBalance(bob).hasTokenBalance(token, 19),
                        getAccountBalance(carol).hasTokenBalance(token, 19));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeesAllCollectorsExempt() {
        return defaultHapiSpec("transferWithFractionalCustomFeesAllCollectorsExempt")
                .given(
                        cryptoCreate(alice),
                        cryptoCreate(bob),
                        cryptoCreate(carol),
                        cryptoCreate(tokenTreasury).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(tokenTreasury)
                                .signedBy(carol, bob, tokenTreasury)
                                .withCustom(fractionalFee(
                                        numerator, 8L, minHtsFee, OptionalLong.of(maxHtsFee), carol, true))
                                .withCustom(fractionalFeeNetOfTransfers(
                                        numerator, 8L, minHtsFee, OptionalLong.of(maxHtsFee), bob, true)),
                        tokenAssociate(alice, token))
                .when(
                        cryptoTransfer(moving(100, token).between(tokenTreasury, carol)),
                        cryptoTransfer(moving(10, token).between(carol, alice)),
                        cryptoTransfer(moving(10, token).between(alice, bob)))
                .then(
                        getAccountBalance(carol).hasTokenBalance(token, 90),
                        getAccountBalance(bob).hasTokenBalance(token, 10));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeesDenominatorMin() {
        return defaultHapiSpec("transferWithFractionalCustomFeesDenominatorMin")
                .given(
                        cryptoCreate(alice),
                        cryptoCreate(bob),
                        cryptoCreate(carol),
                        cryptoCreate(ivan),
                        cryptoCreate(tokenTreasury).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(tokenTreasury)
                                .signedBy(alice, bob, tokenTreasury)
                                .withCustom(fractionalFee(
                                        numerator,
                                        Long.MAX_VALUE,
                                        1 / Long.MAX_VALUE,
                                        OptionalLong.of(maxHtsFee),
                                        alice))
                                .withCustom(fractionalFeeNetOfTransfers(
                                        numerator,
                                        Long.MAX_VALUE,
                                        1 / Long.MAX_VALUE,
                                        OptionalLong.of(maxHtsFee),
                                        bob)),
                        tokenAssociate(carol, token),
                        tokenAssociate(ivan, token))
                .when(
                        cryptoTransfer(moving(10, token).between(tokenTreasury, carol)),
                        cryptoTransfer(moving(10, token).between(tokenTreasury, ivan)),
                        cryptoTransfer(moving(1, token).between(carol, ivan)))
                .then(
                        getAccountBalance(alice).hasTokenBalance(token, 0),
                        getAccountBalance(bob).hasTokenBalance(token, 0));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeesDenominatorMax() {
        return defaultHapiSpec("transferWithFractionalCustomFeesDenominatorMax")
                .given(
                        cryptoCreate(alice),
                        cryptoCreate(bob),
                        cryptoCreate(carol),
                        cryptoCreate(tokenTreasury).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(tokenTreasury)
                                .signedBy(alice, bob, tokenTreasury)
                                .withCustom(fractionalFee(
                                        Long.MAX_VALUE - 1,
                                        Long.MAX_VALUE,
                                        1 / Long.MAX_VALUE,
                                        OptionalLong.of(maxHtsFee),
                                        alice))
                                .withCustom(fractionalFeeNetOfTransfers(
                                        Long.MAX_VALUE - 1,
                                        Long.MAX_VALUE,
                                        1 / Long.MAX_VALUE,
                                        OptionalLong.of(maxHtsFee),
                                        bob)),
                        tokenAssociate(carol, token))
                .when(
                        cryptoTransfer(moving(10, token).between(tokenTreasury, alice)),
                        cryptoTransfer(moving(10, token).between(alice, bob)),
                        cryptoTransfer(moving(1, token).between(bob, carol)))
                .then(getAccountBalance(alice).hasTokenBalance(token, 0));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeesMultipleReceivers() {
        return defaultHapiSpec("transferWithFractionalCustomFeesMultipleReceivers")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(alice).balance(ONE_MILLION_HBARS),
                        cryptoCreate(bob),
                        cryptoCreate(carol),
                        cryptoCreate(tokenTreasury).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(tokenTreasury)
                                .signedBy(tokenTreasury, htsCollector)
                                .withCustom(fractionalFeeNetOfTransfers(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenCreate(token2)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(tokenTreasury)
                                .signedBy(tokenTreasury, htsCollector)
                                .withCustom(fractionalFeeNetOfTransfers(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(alice, token),
                        tokenAssociate(carol, token),
                        tokenAssociate(bob, token),
                        tokenAssociate(alice, token2),
                        tokenAssociate(carol, token2),
                        tokenAssociate(bob, token2),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, alice)),
                        cryptoTransfer(moving(tokenTotal, token2).between(tokenTreasury, alice)))
                .when(
                        cryptoTransfer(
                                moving(2, token).between(alice, bob),
                                moving(3, token2).between(alice, bob)),
                        cryptoTransfer(
                                moving(4, token).between(alice, carol),
                                moving(6, token2).between(alice, carol)))
                .then(
                        getAccountBalance(bob).hasTokenBalance(token, 2),
                        getAccountBalance(bob).hasTokenBalance(token2, 3),
                        getAccountBalance(carol).hasTokenBalance(token, 4),
                        getAccountBalance(carol).hasTokenBalance(token2, 6),
                        getAccountBalance(htsCollector).hasTokenBalance(token, 4),
                        getAccountBalance(htsCollector).hasTokenBalance(token2, 4));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeEqToAmount() {
        return defaultHapiSpec("transferWithFractionalCustomFeeEqToAmount")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFee(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(2L, token).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, tokenTotal - 2),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 0L),
                        getAccountBalance(htsCollector).hasTokenBalance(token, 2L));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeGreaterThanAmountNegative() {
        return defaultHapiSpec("transferWithFractionalCustomFeeGreaterThanAmountNegative")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(alice),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFee(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        tokenAssociate(alice, token),
                        cryptoTransfer(moving(tokenTotal / 2, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(1L, token).between(tokenOwner, alice))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner)
                        .hasKnownStatus(ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE))
                .then(getAccountBalance(tokenOwner).hasTokenBalance(token, tokenTotal / 2));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeGreaterThanAmount() {
        return defaultHapiSpec("transferWithFractionalCustomFeeGreaterThanAmount")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFee(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal / 2, token).between(tokenTreasury, htsCollector)),
                        cryptoTransfer(moving(tokenTotal / 2, token).between(tokenTreasury, tokenReceiver)))
                .when(
                        getAccountBalance(htsCollector).hasTokenBalance(token, tokenTotal / 2),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, tokenTotal / 2),
                        cryptoTransfer(moving(1L, token).between(htsCollector, tokenReceiver))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(tokenOwner))
                .then(
                        getAccountBalance(htsCollector).hasTokenBalance(token, tokenTotal / 2 - 1),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, tokenTotal / 2 + 1));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeGreaterThanAmountNetOfTransfers() {
        return defaultHapiSpec("transferWithFractionalCustomFeeGreaterThanAmountNetOfTransfers")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFeeNetOfTransfers(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal / 2, token).between(tokenTreasury, htsCollector)),
                        cryptoTransfer(moving(tokenTotal / 2, token).between(tokenTreasury, tokenReceiver)))
                .when(
                        getAccountBalance(htsCollector).hasTokenBalance(token, tokenTotal / 2),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, tokenTotal / 2),
                        cryptoTransfer(moving(1L, token).between(htsCollector, tokenReceiver))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(tokenOwner))
                .then(
                        getAccountBalance(htsCollector).hasTokenBalance(token, tokenTotal / 2 - 1),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, tokenTotal / 2 + 1));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeMultipleRecipientsShouldRoundFee() {
        return defaultHapiSpec("transferWithFractionalCustomFeeMultipleRecipientsShouldRoundFee")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(carol),
                        cryptoCreate(alice),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFee(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(alice, token),
                        tokenAssociate(carol, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(
                                moving(5L, token).between(tokenOwner, alice),
                                moving(5L, token).between(tokenOwner, carol))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(htsCollector).hasTokenBalance(token, 2L),
                        getAccountBalance(alice).hasTokenBalance(token, 4L),
                        getAccountBalance(carol).hasTokenBalance(token, 4L),
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 990L));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeMultipleRecipientsHasNotEnoughBalance() {
        return defaultHapiSpec("transferWithFractionalCustomFeeMultipleRecipientsHasNotEnoughBalance")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(carol),
                        cryptoCreate(alice),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(
                                        fractionalFee(numerator, denominator, 10, OptionalLong.of(100), htsCollector)),
                        tokenAssociate(alice, token),
                        tokenAssociate(carol, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(
                                moving(5L, token).between(tokenOwner, alice),
                                moving(5L, token).between(tokenOwner, carol))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, tokenTotal - 10),
                        getAccountBalance(htsCollector).hasTokenBalance(token, 10L),
                        getAccountBalance(alice).hasTokenBalance(token, 0L),
                        getAccountBalance(carol).hasTokenBalance(token, 0L));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeMultipleRecipientsShouldRoundFeeNetOfTransfers() {
        return defaultHapiSpec("transferWithFractionalCustomFeeMultipleRecipientsShouldRoundFeeNetOfTransfers")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(carol),
                        cryptoCreate(alice),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFeeNetOfTransfers(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(alice, token),
                        tokenAssociate(carol, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(
                                moving(5L, token).between(tokenOwner, alice),
                                moving(5L, token).between(tokenOwner, carol))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(htsCollector).hasTokenBalance(token, 2L),
                        getAccountBalance(alice).hasTokenBalance(token, 5L),
                        getAccountBalance(carol).hasTokenBalance(token, 5L),
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 988L));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeNotEnoughBalance() {
        return defaultHapiSpec("transferWithFractionalCustomFeeNotEnoughBalance")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(carol),
                        cryptoCreate(alice),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFee(
                                        numerator, denominator, 10L, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(alice, token),
                        tokenAssociate(carol, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(5L, token).between(tokenOwner, alice))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner)
                        .hasKnownStatus(ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE))
                .then(getAccountBalance(htsCollector).hasTokenBalance(token, 0));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFeeMultipleRecipientsNotEnoughBalance() {
        return defaultHapiSpec("transferWithFractionalCustomFeeMultipleRecipientsNotEnoughBalance")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(carol),
                        cryptoCreate(alice),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFee(
                                        numerator, denominator, 10L, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(alice, token),
                        tokenAssociate(carol, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(
                                moving(5L, token).between(tokenOwner, alice),
                                moving(5L, token).between(tokenOwner, htsCollector))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner)
                        .hasKnownStatus(ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE))
                .then();
    }

    @HapiTest
    public HapiSpec transferMultipleTimesWithFractionalFeeTakenFromSender() {
        return defaultHapiSpec("transferMultipleTimesWithFractionalFeeTakenFromSender")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner),
                        cryptoCreate(alice).balance(ONE_MILLION_HBARS),
                        cryptoCreate(bob).balance(ONE_MILLION_HBARS),
                        cryptoCreate(carol).balance(ONE_MILLION_HBARS),
                        cryptoCreate(ivan),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFeeNetOfTransfers(
                                        numerator, denominator, minHtsFee, OptionalLong.of(9L), htsCollector)),
                        tokenAssociate(alice, token),
                        tokenAssociate(bob, token),
                        tokenAssociate(carol, token),
                        tokenAssociate(ivan, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(
                        cryptoTransfer(moving(100L, token).between(tokenOwner, alice))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(tokenOwner),
                        cryptoTransfer(moving(50L, token).between(alice, bob))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(alice),
                        cryptoTransfer(moving(30L, token).between(bob, carol))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(bob),
                        cryptoTransfer(moving(10L, token).between(carol, ivan))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(carol))
                .then(
                        getAccountBalance(htsCollector).hasTokenBalance(token, 19L),
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 891L),
                        getAccountBalance(alice).hasTokenBalance(token, 45L),
                        getAccountBalance(bob).hasTokenBalance(token, 17L),
                        getAccountBalance(carol).hasTokenBalance(token, 18L));
    }

    @HapiTest
    public HapiSpec transferMultipleTimesWithFractionalFeeTakenFromReceiver() {
        return defaultHapiSpec("transferMultipleTimesWithFractionalFeeTakenFromReceiver")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner),
                        cryptoCreate(alice).balance(ONE_MILLION_HBARS),
                        cryptoCreate(bob).balance(ONE_MILLION_HBARS),
                        cryptoCreate(carol).balance(ONE_MILLION_HBARS),
                        cryptoCreate(ivan).balance(ONE_MILLION_HBARS),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fractionalFee(
                                        numerator, denominator, minHtsFee, OptionalLong.of(9L), htsCollector)),
                        tokenAssociate(alice, token),
                        tokenAssociate(bob, token),
                        tokenAssociate(carol, token),
                        tokenAssociate(ivan, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(
                        cryptoTransfer(moving(100L, token).between(tokenOwner, alice))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(alice),
                        cryptoTransfer(moving(50L, token).between(alice, bob))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(bob),
                        cryptoTransfer(moving(30L, token).between(bob, carol))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(carol),
                        cryptoTransfer(moving(10L, token).between(carol, ivan))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(ivan))
                .then(
                        getAccountBalance(htsCollector).hasTokenBalance(token, 19L),
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 900L),
                        getAccountBalance(alice).hasTokenBalance(token, 41L),
                        getAccountBalance(bob).hasTokenBalance(token, 15L),
                        getAccountBalance(carol).hasTokenBalance(token, 17L));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
