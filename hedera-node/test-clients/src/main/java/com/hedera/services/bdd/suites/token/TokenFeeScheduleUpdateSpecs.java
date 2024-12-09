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

package com.hedera.services.bdd.suites.token;

import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFeeScheduleUpdate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.incompleteCustomFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeNoFallback;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHbarFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHtsFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fractionalFeeInSchedule;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hedera.services.bdd.suites.HapiSuite.APP_PROPERTIES;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ROYALTY_FRACTION_CANNOT_EXCEED_ONE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;

import com.hedera.services.bdd.junit.HapiTest;
import com.hederahashgraph.api.proto.java.TokenType;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(TOKEN)
public class TokenFeeScheduleUpdateSpecs {
    @HapiTest
    final Stream<DynamicTest> baseOperationIsChargedExpectedFee() {
        final var htsAmount = 2_345L;
        final var targetToken = "immutableToken";
        final var feeDenom = "denom";
        final var htsCollector = "denomFee";
        final var feeScheduleKey = "feeSchedule";
        final var expectedBasePriceUsd = 0.001;

        return hapiTest(
                newKeyNamed(feeScheduleKey),
                cryptoCreate("civilian").key(feeScheduleKey),
                cryptoCreate(htsCollector),
                tokenCreate(feeDenom).treasury(htsCollector),
                tokenCreate(targetToken)
                        .expiry(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS)
                        .feeScheduleKey(feeScheduleKey),
                tokenFeeScheduleUpdate(targetToken)
                        .signedBy(feeScheduleKey)
                        .payingWith("civilian")
                        .blankMemo()
                        .withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
                        .via("baseFeeSchUpd"),
                validateChargedUsdWithin("baseFeeSchUpd", expectedBasePriceUsd, 1.0));
    }

    @HapiTest
    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(
                        newKeyNamed("feeScheduleKey"),
                        cryptoCreate("feeCollector"),
                        tokenCreate("t").feeScheduleKey("feeScheduleKey"),
                        tokenAssociate("feeCollector", "t"))
                .when()
                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> tokenFeeScheduleUpdate("t")
                        .withCustom(fixedHbarFee(1, "feeCollector"))
                        .fee(ONE_HBAR)));
    }

    @HapiTest
    final Stream<DynamicTest> failsUpdatingToEmptyFees() {
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(
                        newKeyNamed("feeScheduleKey"),
                        cryptoCreate("feeCollector"),
                        tokenCreate("t").feeScheduleKey("feeScheduleKey"),
                        tokenAssociate("feeCollector", "t"))
                .when()
                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> tokenFeeScheduleUpdate("t")
                        .hasKnownStatus(CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES)
                        .fee(ONE_HBAR)));
    }

    @HapiTest
    final Stream<DynamicTest> validatesRoyaltyFee() {
        return defaultHapiSpec("validatesRoyaltyFee")
                .given(
                        newKeyNamed("feeScheduleKey"),
                        newKeyNamed("supplyKey"),
                        cryptoCreate("feeCollector"),
                        tokenCreate("t")
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .feeScheduleKey("feeScheduleKey")
                                .initialSupply(0)
                                .supplyKey("supplyKey"),
                        tokenAssociate("feeCollector", "t"))
                .when(
                        tokenFeeScheduleUpdate("t")
                                .withCustom(royaltyFeeNoFallback(1, 0, "feeCollector"))
                                .hasKnownStatus(FRACTION_DIVIDES_BY_ZERO)
                                .fee(ONE_HBAR),
                        tokenFeeScheduleUpdate("t")
                                .withCustom(royaltyFeeNoFallback(2, 1, "feeCollector"))
                                .hasKnownStatus(ROYALTY_FRACTION_CANNOT_EXCEED_ONE)
                                .fee(ONE_HBAR),
                        tokenFeeScheduleUpdate("t")
                                .withCustom(royaltyFeeNoFallback(0, 1, "feeCollector"))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE)
                                .fee(ONE_HBAR))
                .then(
                        tokenFeeScheduleUpdate("t")
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHbarFeeInheritingRoyaltyCollector(0), "feeCollector"))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE)
                                .fee(ONE_HBAR),
                        tokenFeeScheduleUpdate("t")
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHbarFeeInheritingRoyaltyCollector(-1), "feeCollector"))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE)
                                .fee(ONE_HBAR));
    }

    @HapiTest
    final Stream<DynamicTest> onlyValidCustomFeeScheduleCanBeUpdated() {
        final var hbarAmount = 1_234L;
        final var htsAmount = 2_345L;
        final var numerator = 1;
        final var denominator = 10;
        final var minimumToCollect = 5;
        final var maximumToCollect = 50;

        final var token = "withCustomSchedules";
        final var immutableTokenWithFeeScheduleKey = "immutableToken";
        final var noFeeScheduleKeyToken = "tokenWithoutFeeScheduleKey";
        final var feeDenom = "denom";
        final var hbarCollector = "hbarFee";
        final var htsCollector = "denomFee";
        final var tokenCollector = "fractionalFee";
        final var invalidEntityId = "1.2.786";

        final var adminKey = "admin";
        final var feeScheduleKey = "feeSchedule";

        final var newHbarAmount = 17_234L;
        final var newHtsAmount = 27_345L;
        final var newNumerator = 17;
        final var newDenominator = 107;
        final var newMinimumToCollect = 57;
        final var newMaximumToCollect = 507;

        final var newFeeDenom = "newDenom";
        final var newHbarCollector = "newHbarFee";
        final var newHtsCollector = "newDenomFee";
        final var newTokenCollector = "newFractionalFee";

        return defaultHapiSpec("OnlyValidCustomFeeScheduleCanBeUpdated")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(Map.of("tokens.maxCustomFeesAllowed", "10")),
                        newKeyNamed(adminKey),
                        newKeyNamed(feeScheduleKey),
                        cryptoCreate(htsCollector),
                        cryptoCreate(newHtsCollector),
                        cryptoCreate(hbarCollector),
                        cryptoCreate(newHbarCollector),
                        cryptoCreate(tokenCollector),
                        cryptoCreate(newTokenCollector),
                        tokenCreate(feeDenom).treasury(htsCollector),
                        tokenCreate(newFeeDenom).treasury(newHtsCollector),
                        tokenCreate(token)
                                .adminKey(adminKey)
                                .feeScheduleKey(feeScheduleKey)
                                .treasury(tokenCollector)
                                .withCustom(fixedHbarFee(hbarAmount, hbarCollector))
                                .withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector)),
                        tokenCreate(immutableTokenWithFeeScheduleKey)
                                .feeScheduleKey(feeScheduleKey)
                                .treasury(tokenCollector)
                                .withCustom(fixedHbarFee(hbarAmount, hbarCollector))
                                .withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector)),
                        tokenCreate(noFeeScheduleKeyToken)
                                .adminKey(adminKey)
                                .treasury(tokenCollector)
                                .withCustom(fixedHbarFee(hbarAmount, hbarCollector))
                                .withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector)),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(Map.of("tokens.maxCustomFeesAllowed", "1")))
                .when(
                        tokenFeeScheduleUpdate(immutableTokenWithFeeScheduleKey)
                                .withCustom(fractionalFee(
                                        numerator,
                                        0,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(FRACTION_DIVIDES_BY_ZERO),
                        tokenFeeScheduleUpdate(noFeeScheduleKeyToken)
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(TOKEN_HAS_NO_FEE_SCHEDULE_KEY),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fractionalFee(
                                        numerator,
                                        0,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(FRACTION_DIVIDES_BY_ZERO),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fractionalFee(
                                        -numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        -minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(-maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fixedHbarFee(hbarAmount, hbarCollector))
                                .withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
                                .hasKnownStatus(CUSTOM_FEES_LIST_TOO_LONG),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fixedHbarFee(hbarAmount, invalidEntityId))
                                .hasKnownStatus(INVALID_CUSTOM_FEE_COLLECTOR),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fixedHtsFee(htsAmount, invalidEntityId, htsCollector))
                                .hasKnownStatus(INVALID_TOKEN_ID_IN_CUSTOM_FEES),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fixedHtsFee(htsAmount, feeDenom, hbarCollector))
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fixedHtsFee(-htsAmount, feeDenom, htsCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(incompleteCustomFee(hbarCollector))
                                .hasKnownStatus(CUSTOM_FEE_NOT_FULLY_SPECIFIED),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(Map.of("tokens.maxCustomFeesAllowed", "10")),
                        tokenAssociate(newTokenCollector, token),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fixedHbarFee(newHbarAmount, newHbarCollector))
                                .withCustom(fixedHtsFee(newHtsAmount, newFeeDenom, newHtsCollector))
                                .withCustom(fractionalFee(
                                        newNumerator,
                                        newDenominator,
                                        newMinimumToCollect,
                                        OptionalLong.of(newMaximumToCollect),
                                        newTokenCollector)))
                .then(getTokenInfo(token)
                        .hasCustom(fixedHbarFeeInSchedule(newHbarAmount, newHbarCollector))
                        .hasCustom(fixedHtsFeeInSchedule(newHtsAmount, newFeeDenom, newHtsCollector))
                        .hasCustom(fractionalFeeInSchedule(
                                newNumerator,
                                newDenominator,
                                newMinimumToCollect,
                                OptionalLong.of(newMaximumToCollect),
                                false,
                                newTokenCollector)));
    }
}
