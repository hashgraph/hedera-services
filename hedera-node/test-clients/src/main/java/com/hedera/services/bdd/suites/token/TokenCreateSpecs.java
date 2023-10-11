/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpecOperation.UnknownFieldLocation.*;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AutoAssocAsserts.accountTokenPairs;
import static com.hedera.services.bdd.spec.assertions.AutoAssocAsserts.accountTokenPairsInAnyOrder;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.incompleteCustomFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeNoFallback;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHbarFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHtsFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fractionalFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordSystemProperty;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validates the {@code TokenCreate} transaction, including its:
 * <ul>
 *     <li>Auto-association behavior.</li>
 *     <li>Default values.</li>
 * </ul>
 */
@HapiTestSuite
public class TokenCreateSpecs extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TokenCreateSpecs.class);
    private static final String NON_FUNGIBLE_UNIQUE_FINITE = "non-fungible-unique-finite";
    private static final String PRIMARY = "primary";
    private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";
    private static final String ADMIN_KEY = "adminKey";
    private static final String SUPPLY_KEY = "supplyKey";
    private static final String AUTO_RENEW = "autoRenew";
    private static final String NAME = "012345678912";
    private static final String CREATE_TXN = "createTxn";
    private static final String PAYER = "payer";

    private static String TOKEN_TREASURY = "treasury";

    private static final String A_TOKEN = "TokenA";
    private static final String B_TOKEN = "TokenB";
    private static final String FIRST_USER = "Client1";
    private static final String SENTINEL_VALUE = "0.0.0";

    private static final long defaultMaxLifetime =
            Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));

    public static void main(String... args) {
        new TokenCreateSpecs().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                creationValidatesNonFungiblePrechecks(),
                creationValidatesMaxSupply(),
                creationValidatesMemo(),
                creationValidatesName(),
                creationValidatesSymbol(),
                treasuryHasCorrectBalance(),
                creationRequiresAppropriateSigs(),
                creationRequiresAppropriateSigsHappyPath(),
                initialSupplyMustBeSane(),
                creationYieldsExpectedToken(),
                creationSetsExpectedName(),
                creationValidatesTreasuryAccount(),
                autoRenewValidationWorks(),
                creationWithoutKYCSetsCorrectStatus(),
                creationValidatesExpiry(),
                creationValidatesFreezeDefaultWithNoFreezeKey(),
                creationSetsCorrectExpiry(),
                creationHappyPath(),
                worksAsExpectedWithDefaultTokenId(),
                cannotCreateWithExcessiveLifetime(),
                prechecksWork(),
                /* HIP-18 */
                onlyValidCustomFeeScheduleCanBeCreated(),
                feeCollectorSigningReqsWorkForTokenCreate(),
                createsFungibleInfiniteByDefault(),
                baseCreationsHaveExpectedPrices(),
                /* HIP-23 */
                validateNewTokenAssociations());
    }

    /**
     * Validates that a {@code TokenCreate} auto-associates the following types of
     * accounts:
     * <ul>
     *     <li>Its treasury.</li>
     *     <li>Any fractional fee collector.</li>
     *     <li>Any self-denominated fixed fee collector.</li>
     * </ul>
     * It also verifies that these auto-associations don't "count" against the max
     * automatic associations limit defined by https://hips.hedera.com/hip/hip-23.
     */
    @HapiTest
    private HapiSpec validateNewTokenAssociations() {
        final String notToBeToken = "notToBeToken";
        final String hbarCollector = "hbarCollector";
        final String fractionalCollector = "fractionalCollector";
        final String selfDenominatedFixedCollector = "selfDenominatedFixedCollector";
        final String otherSelfDenominatedFixedCollector = "otherSelfDenominatedFixedCollector";
        final String treasury = "treasury";
        final String tbd = "toBeDeletd";
        final String creationTxn = "creationTxn";
        final String failedCreationTxn = "failedCreationTxn";

        return defaultHapiSpec("ValidateNewTokenAssociations")
                .given(
                        cryptoCreate(tbd),
                        cryptoDelete(tbd),
                        cryptoCreate(hbarCollector),
                        cryptoCreate(fractionalCollector),
                        cryptoCreate(selfDenominatedFixedCollector),
                        cryptoCreate(otherSelfDenominatedFixedCollector),
                        cryptoCreate(treasury).maxAutomaticTokenAssociations(10).balance(ONE_HUNDRED_HBARS))
                .when(
                        getAccountInfo(treasury).savingSnapshot(treasury),
                        getAccountInfo(hbarCollector).savingSnapshot(hbarCollector),
                        getAccountInfo(fractionalCollector).savingSnapshot(fractionalCollector),
                        getAccountInfo(selfDenominatedFixedCollector).savingSnapshot(selfDenominatedFixedCollector),
                        getAccountInfo(otherSelfDenominatedFixedCollector)
                                .savingSnapshot(otherSelfDenominatedFixedCollector),
                        tokenCreate(A_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(Long.MAX_VALUE)
                                .treasury(treasury)
                                .withCustom(fixedHbarFee(20L, hbarCollector))
                                .withCustom(fractionalFee(1L, 100L, 1L, OptionalLong.of(5L), fractionalCollector))
                                .withCustom(fixedHtsFee(2L, SENTINEL_VALUE, selfDenominatedFixedCollector))
                                .withCustom(fixedHtsFee(3L, SENTINEL_VALUE, otherSelfDenominatedFixedCollector))
                                .signedBy(
                                        DEFAULT_PAYER,
                                        treasury,
                                        fractionalCollector,
                                        selfDenominatedFixedCollector,
                                        otherSelfDenominatedFixedCollector)
                                .via(creationTxn),
                        tokenCreate(notToBeToken)
                                .treasury(tbd)
                                .hasKnownStatus(INVALID_TREASURY_ACCOUNT_FOR_TOKEN)
                                .via(failedCreationTxn))
                .then(
                        /* Validate records */
                        getTxnRecord(creationTxn)
                                .hasPriority(recordWith()
                                        .autoAssociated(accountTokenPairs(List.of(
                                                Pair.of(treasury, A_TOKEN),
                                                Pair.of(fractionalCollector, A_TOKEN),
                                                Pair.of(selfDenominatedFixedCollector, A_TOKEN),
                                                Pair.of(otherSelfDenominatedFixedCollector, A_TOKEN))))),
                        getTxnRecord(failedCreationTxn)
                                .hasPriority(recordWith().autoAssociated(accountTokenPairs(List.of()))),
                        /* Validate state */
                        getAccountInfo(hbarCollector).has(accountWith().noChangesFromSnapshot(hbarCollector)),
                        getAccountInfo(treasury)
                                .hasMaxAutomaticAssociations(10)
                                /* TokenCreate auto-associations aren't part of the HIP-23 paradigm */
                                .hasAlreadyUsedAutomaticAssociations(0)
                                .has(accountWith()
                                        .newAssociationsFromSnapshot(treasury, List.of(relationshipWith(A_TOKEN)))),
                        getAccountInfo(fractionalCollector)
                                .has(accountWith()
                                        .newAssociationsFromSnapshot(
                                                fractionalCollector, List.of(relationshipWith(A_TOKEN)))),
                        getAccountInfo(selfDenominatedFixedCollector)
                                .has(accountWith()
                                        .newAssociationsFromSnapshot(
                                                selfDenominatedFixedCollector, List.of(relationshipWith(A_TOKEN)))),
                        getAccountInfo(otherSelfDenominatedFixedCollector)
                                .has(accountWith()
                                        .newAssociationsFromSnapshot(
                                                otherSelfDenominatedFixedCollector,
                                                List.of(relationshipWith(A_TOKEN)))));
    }

    /**
     * Validates the default values for a {@code TokenCreate}'s token type (fungible) and supply type (infinite).
     */
    @HapiTest
    private HapiSpec createsFungibleInfiniteByDefault() {
        return defaultHapiSpec("CreatesFungibleInfiniteByDefault")
                .given()
                .when(tokenCreate("DefaultFungible"))
                .then(getTokenInfo("DefaultFungible")
                        .hasTokenType(TokenType.FUNGIBLE_COMMON)
                        .hasSupplyType(TokenSupplyType.INFINITE));
    }

    @HapiTest
    private HapiSpec worksAsExpectedWithDefaultTokenId() {
        return defaultHapiSpec("WorksAsExpectedWithDefaultTokenId")
                .given()
                .when()
                .then(getTokenInfo(SENTINEL_VALUE).hasCostAnswerPrecheck(INVALID_TOKEN_ID));
    }

    @HapiTest
    public HapiSpec cannotCreateWithExcessiveLifetime() {
        final var smallBuffer = 12_345L;
        final var okExpiry = defaultMaxLifetime + Instant.now().getEpochSecond() - smallBuffer;
        final var excessiveExpiry = defaultMaxLifetime + Instant.now().getEpochSecond() + smallBuffer;
        return defaultHapiSpec("CannotCreateWithExcessiveLifetime")
                .given()
                .when()
                .then(
                        tokenCreate("neverToBe").expiry(excessiveExpiry).hasKnownStatus(INVALID_EXPIRATION_TIME),
                        tokenCreate("neverToBe").expiry(okExpiry));
    }

    @HapiTest
    public HapiSpec autoRenewValidationWorks() {
        final var deletingAccount = "deletingAccount";
        return defaultHapiSpec("AutoRenewValidationWorks")
                .given(
                        cryptoCreate(AUTO_RENEW).balance(0L),
                        cryptoCreate(deletingAccount).balance(0L))
                .when(
                        cryptoDelete(deletingAccount),
                        tokenCreate(PRIMARY)
                                .autoRenewAccount(deletingAccount)
                                .hasKnownStatus(INVALID_AUTORENEW_ACCOUNT),
                        tokenCreate(PRIMARY)
                                .signedBy(GENESIS)
                                .autoRenewAccount("1.2.3")
                                .hasKnownStatus(INVALID_AUTORENEW_ACCOUNT),
                        tokenCreate(PRIMARY)
                                .autoRenewAccount(AUTO_RENEW)
                                .autoRenewPeriod(Long.MAX_VALUE)
                                .hasPrecheck(INVALID_RENEWAL_PERIOD),
                        tokenCreate(PRIMARY)
                                .signedBy(GENESIS)
                                .autoRenewAccount(AUTO_RENEW)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenCreate(PRIMARY).autoRenewAccount(AUTO_RENEW))
                .then(getTokenInfo(PRIMARY).logged());
    }

    @HapiTest
    public HapiSpec creationYieldsExpectedToken() {
        return defaultHapiSpec("CreationYieldsExpectedToken")
                .given(cryptoCreate(TOKEN_TREASURY).balance(0L), newKeyNamed("freeze"))
                .when(tokenCreate(PRIMARY)
                        .initialSupply(123)
                        .decimals(4)
                        .freezeDefault(true)
                        .freezeKey("freeze")
                        .treasury(TOKEN_TREASURY))
                .then(getTokenInfo(PRIMARY).logged().hasRegisteredId(PRIMARY));
    }

    @HapiTest
    public HapiSpec creationSetsExpectedName() {
        String saltedName = salted(PRIMARY);
        return defaultHapiSpec("CreationSetsExpectedName")
                .given(cryptoCreate(TOKEN_TREASURY).balance(0L))
                .when(tokenCreate(PRIMARY).name(saltedName).treasury(TOKEN_TREASURY))
                .then(getTokenInfo(PRIMARY).logged().hasRegisteredId(PRIMARY).hasName(saltedName));
    }

    @HapiTest
    public HapiSpec creationWithoutKYCSetsCorrectStatus() {
        String saltedName = salted(PRIMARY);
        return defaultHapiSpec("CreationWithoutKYCSetsCorrectStatus")
                .given(cryptoCreate(TOKEN_TREASURY).balance(0L))
                .when(tokenCreate(PRIMARY).name(saltedName).treasury(TOKEN_TREASURY))
                .then(getAccountInfo(TOKEN_TREASURY)
                        .hasToken(relationshipWith(PRIMARY).kyc(TokenKycStatus.KycNotApplicable)));
    }

    @HapiTest
    public HapiSpec baseCreationsHaveExpectedPrices() {
        final var civilian = "NonExemptPayer";

        final var expectedCommonNoCustomFeesPriceUsd = 1.00;
        final var expectedUniqueNoCustomFeesPriceUsd = 1.00;
        final var expectedCommonWithCustomFeesPriceUsd = 2.00;
        final var expectedUniqueWithCustomFeesPriceUsd = 2.00;

        final var commonNoFees = "commonNoFees";
        final var commonWithFees = "commonWithFees";
        final var uniqueNoFees = "uniqueNoFees";
        final var uniqueWithFees = "uniqueWithFees";

        final var customFeeKey = "customFeeKey";

        return defaultHapiSpec("BaseCreationsHaveExpectedPrices")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(customFeeKey))
                .when(
                        tokenCreate(commonNoFees)
                                .blankMemo()
                                .name(NAME)
                                .symbol("ABCD")
                                .payingWith(civilian)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .adminKey(ADMIN_KEY)
                                .via(txnFor(commonNoFees)),
                        tokenCreate(commonWithFees)
                                .blankMemo()
                                .name(NAME)
                                .symbol("ABCD")
                                .payingWith(civilian)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .adminKey(ADMIN_KEY)
                                .withCustom(fixedHbarFee(ONE_HBAR, TOKEN_TREASURY))
                                .feeScheduleKey(customFeeKey)
                                .via(txnFor(commonWithFees)),
                        tokenCreate(uniqueNoFees)
                                .payingWith(civilian)
                                .blankMemo()
                                .name(NAME)
                                .symbol("ABCD")
                                .initialSupply(0L)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .adminKey(ADMIN_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .via(txnFor(uniqueNoFees)),
                        tokenCreate(uniqueWithFees)
                                .payingWith(civilian)
                                .blankMemo()
                                .name(NAME)
                                .symbol("ABCD")
                                .initialSupply(0L)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .adminKey(ADMIN_KEY)
                                .withCustom(fixedHbarFee(ONE_HBAR, TOKEN_TREASURY))
                                .supplyKey(SUPPLY_KEY)
                                .feeScheduleKey(customFeeKey)
                                .via(txnFor(uniqueWithFees)))
                .then(
                        validateChargedUsdWithin(txnFor(commonNoFees), expectedCommonNoCustomFeesPriceUsd, 0.01),
                        validateChargedUsdWithin(txnFor(commonWithFees), expectedCommonWithCustomFeesPriceUsd, 0.01),
                        validateChargedUsdWithin(txnFor(uniqueNoFees), expectedUniqueNoCustomFeesPriceUsd, 0.01),
                        validateChargedUsdWithin(txnFor(uniqueWithFees), expectedUniqueWithCustomFeesPriceUsd, 0.01));
    }

    private String txnFor(String tokenSubType) {
        return tokenSubType + "Txn";
    }

    @HapiTest
    public HapiSpec creationHappyPath() {
        String memo = "JUMP";
        String saltedName = salted(PRIMARY);
        final var secondCreation = "secondCreation";
        final var pauseKey = "pauseKey";
        return defaultHapiSpec("CreationHappyPath")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed("freezeKey"),
                        newKeyNamed("kycKey"),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed("wipeKey"),
                        newKeyNamed("feeScheduleKey"),
                        newKeyNamed(pauseKey))
                .when(
                        tokenCreate(PRIMARY)
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(memo)
                                .name(saltedName)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(1000)
                                .initialSupply(500)
                                .decimals(1)
                                .adminKey(ADMIN_KEY)
                                .freezeKey("freezeKey")
                                .kycKey("kycKey")
                                .supplyKey(SUPPLY_KEY)
                                .wipeKey("wipeKey")
                                .feeScheduleKey("feeScheduleKey")
                                .pauseKey(pauseKey)
                                .via(CREATE_TXN),
                        tokenCreate(NON_FUNGIBLE_UNIQUE_FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.FINITE)
                                .pauseKey(pauseKey)
                                .initialSupply(0)
                                .maxSupply(100)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(GENESIS)
                                .via(secondCreation),
                        getTxnRecord(secondCreation)
                                .logged()
                                .hasPriority(recordWith()
                                        .autoAssociated(accountTokenPairsInAnyOrder(
                                                List.of(Pair.of(TOKEN_TREASURY, NON_FUNGIBLE_UNIQUE_FINITE))))))
                .then(
                        withOpContext((spec, opLog) -> {
                            var createTxn = getTxnRecord(CREATE_TXN);
                            allRunFor(spec, createTxn);
                            var timestamp = createTxn
                                    .getResponseRecord()
                                    .getConsensusTimestamp()
                                    .getSeconds();
                            spec.registry().saveExpiry(PRIMARY, timestamp + THREE_MONTHS_IN_SECONDS);
                        }),
                        getTokenInfo(PRIMARY)
                                .logged()
                                .hasRegisteredId(PRIMARY)
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSupplyType(TokenSupplyType.FINITE)
                                .hasEntityMemo(memo)
                                .hasName(saltedName)
                                .hasTreasury(TOKEN_TREASURY)
                                .hasAutoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .hasValidExpiry()
                                .hasDecimals(1)
                                .hasAdminKey(PRIMARY)
                                .hasFreezeKey(PRIMARY)
                                .hasKycKey(PRIMARY)
                                .hasSupplyKey(PRIMARY)
                                .hasWipeKey(PRIMARY)
                                .hasFeeScheduleKey(PRIMARY)
                                .hasPauseKey(PRIMARY)
                                .hasPauseStatus(TokenPauseStatus.Unpaused)
                                .hasMaxSupply(1000)
                                .hasTotalSupply(500)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT),
                        getTokenInfo(NON_FUNGIBLE_UNIQUE_FINITE)
                                .logged()
                                .hasRegisteredId(NON_FUNGIBLE_UNIQUE_FINITE)
                                .hasTokenType(NON_FUNGIBLE_UNIQUE)
                                .hasSupplyType(TokenSupplyType.FINITE)
                                .hasPauseKey(PRIMARY)
                                .hasPauseStatus(TokenPauseStatus.Unpaused)
                                .hasTotalSupply(0)
                                .hasMaxSupply(100),
                        getAccountInfo(TOKEN_TREASURY)
                                .hasToken(relationshipWith(PRIMARY)
                                        .balance(500)
                                        .kyc(TokenKycStatus.Granted)
                                        .freeze(TokenFreezeStatus.Unfrozen))
                                .hasToken(relationshipWith(NON_FUNGIBLE_UNIQUE_FINITE)
                                        .balance(0)
                                        .kyc(TokenKycStatus.KycNotApplicable)
                                        .freeze(TokenFreezeStatus.FreezeNotApplicable)));
    }

    @HapiTest
    public HapiSpec creationSetsCorrectExpiry() {
        return defaultHapiSpec("CreationSetsCorrectExpiry")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW).balance(0L))
                .when(tokenCreate(PRIMARY)
                        .autoRenewAccount(AUTO_RENEW)
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                        .treasury(TOKEN_TREASURY)
                        .via(CREATE_TXN))
                .then(
                        withOpContext((spec, opLog) -> {
                            var createTxn = getTxnRecord(CREATE_TXN);
                            allRunFor(spec, createTxn);
                            var timestamp = createTxn
                                    .getResponseRecord()
                                    .getConsensusTimestamp()
                                    .getSeconds();
                            spec.registry().saveExpiry(PRIMARY, timestamp + THREE_MONTHS_IN_SECONDS);
                        }),
                        getTokenInfo(PRIMARY).logged().hasRegisteredId(PRIMARY).hasValidExpiry());
    }

    @HapiTest
    public HapiSpec creationValidatesExpiry() {
        return defaultHapiSpec("CreationValidatesExpiry")
                .given()
                .when()
                .then(tokenCreate(PRIMARY).expiry(1000).hasPrecheck(INVALID_EXPIRATION_TIME));
    }

    @HapiTest
    public HapiSpec creationValidatesFreezeDefaultWithNoFreezeKey() {
        return defaultHapiSpec("CreationValidatesFreezeDefaultWithNoFreezeKey")
                .given()
                .when()
                .then(tokenCreate(PRIMARY).freezeDefault(true).hasPrecheck(TOKEN_HAS_NO_FREEZE_KEY));
    }

    @HapiTest
    public HapiSpec creationValidatesMemo() {
        return defaultHapiSpec("CreationValidatesMemo")
                .given()
                .when()
                .then(tokenCreate(PRIMARY).entityMemo("N\u0000!!!").hasPrecheck(INVALID_ZERO_BYTE_IN_STRING));
    }

    @HapiTest
    public HapiSpec creationValidatesNonFungiblePrechecks() {
        return defaultHapiSpec("CreationValidatesNonFungiblePrechecks")
                .given()
                .when()
                .then(
                        tokenCreate(PRIMARY)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .decimals(0)
                                .hasPrecheck(TOKEN_HAS_NO_SUPPLY_KEY),
                        tokenCreate(PRIMARY)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(1)
                                .decimals(0)
                                .hasPrecheck(INVALID_TOKEN_INITIAL_SUPPLY),
                        tokenCreate(PRIMARY)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .decimals(1)
                                .hasPrecheck(INVALID_TOKEN_DECIMALS));
    }

    @HapiTest
    public HapiSpec creationValidatesMaxSupply() {
        return defaultHapiSpec("CreationValidatesMaxSupply")
                .given()
                .when()
                .then(
                        tokenCreate(PRIMARY).maxSupply(-1).hasPrecheck(INVALID_TOKEN_MAX_SUPPLY),
                        tokenCreate(PRIMARY).maxSupply(1).hasPrecheck(INVALID_TOKEN_MAX_SUPPLY),
                        tokenCreate(PRIMARY)
                                .supplyType(TokenSupplyType.FINITE)
                                .maxSupply(0)
                                .hasPrecheck(INVALID_TOKEN_MAX_SUPPLY),
                        tokenCreate(PRIMARY)
                                .supplyType(TokenSupplyType.FINITE)
                                .maxSupply(-1)
                                .hasPrecheck(INVALID_TOKEN_MAX_SUPPLY),
                        tokenCreate(PRIMARY)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(2)
                                .maxSupply(1)
                                .hasPrecheck(INVALID_TOKEN_INITIAL_SUPPLY));
    }

    @HapiTest
    public HapiSpec onlyValidCustomFeeScheduleCanBeCreated() {
        return defaultHapiSpec("OnlyValidCustomFeeScheduleCanBeCreated")
                .given(
                        newKeyNamed(customFeesKey),
                        cryptoCreate(htsCollector),
                        cryptoCreate(hbarCollector),
                        cryptoCreate(tokenCollector),
                        tokenCreate(feeDenom).treasury(htsCollector))
                .when(
                        tokenCreate(token)
                                .treasury(tokenCollector)
                                .withCustom(fractionalFee(
                                        numerator,
                                        0,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(FRACTION_DIVIDES_BY_ZERO),
                        tokenCreate(token)
                                .treasury(tokenCollector)
                                .withCustom(fixedHbarFee(hbarAmount, invalidEntityId))
                                .hasKnownStatus(INVALID_CUSTOM_FEE_COLLECTOR),
                        tokenCreate(token)
                                .treasury(tokenCollector)
                                .withCustom(fixedHtsFee(htsAmount, invalidEntityId, htsCollector))
                                .hasKnownStatus(INVALID_TOKEN_ID_IN_CUSTOM_FEES),
                        tokenCreate(token)
                                .treasury(tokenCollector)
                                .withCustom(fixedHtsFee(htsAmount, feeDenom, hbarCollector))
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR),
                        tokenCreate(token)
                                .treasury(tokenCollector)
                                .withCustom(incompleteCustomFee(hbarCollector))
                                .signedBy(DEFAULT_PAYER, tokenCollector, hbarCollector)
                                .hasKnownStatus(CUSTOM_FEE_NOT_FULLY_SPECIFIED),
                        tokenCreate(token)
                                .treasury(tokenCollector)
                                .withCustom(fixedHtsFee(negativeHtsFee, feeDenom, hbarCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenCreate(token)
                                .treasury(tokenCollector)
                                .withCustom(fractionalFee(
                                        numerator,
                                        -denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenCreate(token)
                                .treasury(tokenCollector)
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        -minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenCreate(token)
                                .treasury(tokenCollector)
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(-maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenCreate(token)
                                .treasury(tokenCollector)
                                .withCustom(fractionalFee(
                                        -numerator,
                                        -denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenCreate(token)
                                .treasury(tokenCollector)
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(minimumToCollect - 1),
                                        tokenCollector))
                                .hasKnownStatus(FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT),
                        tokenCreate(token)
                                .treasury(tokenCollector)
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(minimumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(SUCCESS),
                        tokenCreate(token)
                                .treasury(tokenCollector)
                                .withCustom(royaltyFeeNoFallback(1, 2, tokenCollector))
                                .hasKnownStatus(CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE),
                        tokenCreate(token)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(GENESIS)
                                .initialSupply(0L)
                                .treasury(tokenCollector)
                                .withCustom(royaltyFeeNoFallback(-1, 2, tokenCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenCreate(token)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(GENESIS)
                                .initialSupply(0L)
                                .treasury(tokenCollector)
                                .withCustom(royaltyFeeNoFallback(1, -2, tokenCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenCreate(token)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(GENESIS)
                                .initialSupply(0L)
                                .treasury(tokenCollector)
                                .withCustom(royaltyFeeNoFallback(1, 0, tokenCollector))
                                .hasKnownStatus(FRACTION_DIVIDES_BY_ZERO),
                        tokenCreate(token)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(GENESIS)
                                .initialSupply(0L)
                                .treasury(tokenCollector)
                                .withCustom(royaltyFeeNoFallback(2, 1, tokenCollector))
                                .hasKnownStatus(ROYALTY_FRACTION_CANNOT_EXCEED_ONE),
                        tokenCreate(token)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(GENESIS)
                                .initialSupply(0L)
                                .treasury(tokenCollector)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHbarFeeInheritingRoyaltyCollector(-100), tokenCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenCreate(token)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(GENESIS)
                                .initialSupply(0L)
                                .treasury(tokenCollector)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(100, "1.2.3"), tokenCollector))
                                .hasKnownStatus(INVALID_TOKEN_ID_IN_CUSTOM_FEES),
                        tokenCreate(token)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(GENESIS)
                                .initialSupply(0L)
                                .treasury(htsCollector)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(100, feeDenom), htsCollector)),
                        tokenCreate(token)
                                .treasury(tokenCollector)
                                .withCustom(fixedHbarFee(hbarAmount, hbarCollector))
                                .withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
                                .withCustom(fixedHtsFee(htsAmount, SENTINEL_VALUE, htsCollector))
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .signedBy(DEFAULT_PAYER, tokenCollector, htsCollector))
                .then(getTokenInfo(token)
                        .hasCustom(fixedHbarFeeInSchedule(hbarAmount, hbarCollector))
                        .hasCustom(fixedHtsFeeInSchedule(htsAmount, feeDenom, htsCollector))
                        .hasCustom(fixedHtsFeeInSchedule(htsAmount, token, htsCollector))
                        .hasCustom(fractionalFeeInSchedule(
                                numerator,
                                denominator,
                                minimumToCollect,
                                OptionalLong.of(maximumToCollect),
                                false,
                                tokenCollector)));
    }

    @HapiTest
    private HapiSpec feeCollectorSigningReqsWorkForTokenCreate() {
        return defaultHapiSpec("feeCollectorSigningReqsWorkForTokenCreate")
                .given(
                        newKeyNamed(customFeesKey),
                        cryptoCreate(htsCollector).receiverSigRequired(true),
                        cryptoCreate(hbarCollector),
                        cryptoCreate(tokenCollector),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(feeDenom).treasury(htsCollector))
                .when(
                        tokenCreate(token)
                                .treasury(TOKEN_TREASURY)
                                .withCustom(fractionalFee(
                                        numerator,
                                        0,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenCreate(token)
                                .treasury(TOKEN_TREASURY)
                                .withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenCreate(token)
                                .treasury(TOKEN_TREASURY)
                                .withCustom(fractionalFee(
                                        numerator,
                                        -denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenCreate(token)
                                .treasury(TOKEN_TREASURY)
                                .withCustom(fixedHbarFee(hbarAmount, hbarCollector))
                                .withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .signedBy(DEFAULT_PAYER, TOKEN_TREASURY, htsCollector, tokenCollector))
                .then(
                        getTokenInfo(token)
                                .hasCustom(fixedHbarFeeInSchedule(hbarAmount, hbarCollector))
                                .hasCustom(fixedHtsFeeInSchedule(htsAmount, feeDenom, htsCollector))
                                .hasCustom(fractionalFeeInSchedule(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        false,
                                        tokenCollector)),
                        getAccountInfo(tokenCollector).hasToken(relationshipWith(token)),
                        getAccountInfo(hbarCollector).hasNoTokenRelationship(token),
                        getAccountInfo(htsCollector).hasNoTokenRelationship(token));
    }

    @HapiTest
    public HapiSpec creationValidatesName() {
        AtomicInteger maxUtf8Bytes = new AtomicInteger();

        return defaultHapiSpec("CreationValidatesName")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        recordSystemProperty("tokens.maxTokenNameUtf8Bytes", Integer::parseInt, maxUtf8Bytes::set))
                .when()
                .then(
                        tokenCreate(PRIMARY).name("").logged().hasPrecheck(MISSING_TOKEN_NAME),
                        tokenCreate(PRIMARY).name("T\u0000ken").logged().hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        sourcing(() -> tokenCreate("tooLong")
                                .name(TxnUtils.nAscii(maxUtf8Bytes.get() + 1))
                                .hasPrecheck(TOKEN_NAME_TOO_LONG)),
                        sourcing(() -> tokenCreate("tooLongAgain")
                                .name(nCurrencySymbols(maxUtf8Bytes.get() / 3 + 1))
                                .hasPrecheck(TOKEN_NAME_TOO_LONG)));
    }

    @HapiTest
    public HapiSpec creationValidatesSymbol() {
        AtomicInteger maxUtf8Bytes = new AtomicInteger();

        return defaultHapiSpec("CreationValidatesSymbol")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        recordSystemProperty("tokens.maxSymbolUtf8Bytes", Integer::parseInt, maxUtf8Bytes::set))
                .when()
                .then(
                        tokenCreate("missingSymbol").symbol("").hasPrecheck(MISSING_TOKEN_SYMBOL),
                        tokenCreate(PRIMARY).name("T\u0000ken").logged().hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        sourcing(() -> tokenCreate("tooLong")
                                .symbol(TxnUtils.nAscii(maxUtf8Bytes.get() + 1))
                                .hasPrecheck(TOKEN_SYMBOL_TOO_LONG)),
                        sourcing(() -> tokenCreate("tooLongAgain")
                                .symbol(nCurrencySymbols(maxUtf8Bytes.get() / 3 + 1))
                                .hasPrecheck(TOKEN_SYMBOL_TOO_LONG)));
    }

    private String nCurrencySymbols(int n) {
        return IntStream.range(0, n).mapToObj(ignore -> "€").collect(Collectors.joining());
    }

    @HapiTest
    public HapiSpec creationRequiresAppropriateSigs() {
        return defaultHapiSpec("CreationRequiresAppropriateSigs")
                .given(
                        cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        newKeyNamed(ADMIN_KEY))
                .when()
                .then(
                        tokenCreate("shouldntWork")
                                .treasury(TOKEN_TREASURY)
                                .payingWith(PAYER)
                                .adminKey(ADMIN_KEY)
                                .signedBy(PAYER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        /* treasury must sign */
                        tokenCreate("shouldntWorkEither")
                                .treasury(TOKEN_TREASURY)
                                .payingWith(PAYER)
                                .adminKey(ADMIN_KEY)
                                .signedBy(PAYER, ADMIN_KEY)
                                .hasKnownStatus(INVALID_SIGNATURE));
    }

    @HapiTest
    public HapiSpec creationRequiresAppropriateSigsHappyPath() {
        return defaultHapiSpec("CreationRequiresAppropriateSigsHappyPath")
                .given(cryptoCreate(PAYER), cryptoCreate(TOKEN_TREASURY).balance(0L), newKeyNamed(ADMIN_KEY))
                .when()
                .then(tokenCreate("shouldWork")
                        .treasury(TOKEN_TREASURY)
                        .payingWith(PAYER)
                        .adminKey(ADMIN_KEY)
                        .signedBy(TOKEN_TREASURY, PAYER, ADMIN_KEY)
                        .hasKnownStatus(SUCCESS));
    }

    @HapiTest
    public HapiSpec creationValidatesTreasuryAccount() {
        return defaultHapiSpec("CreationValidatesTreasuryAccount")
                .given(cryptoCreate(TOKEN_TREASURY).balance(0L))
                .when(cryptoDelete(TOKEN_TREASURY))
                .then(tokenCreate("shouldntWork")
                        .treasury(TOKEN_TREASURY)
                        .hasKnownStatus(INVALID_TREASURY_ACCOUNT_FOR_TOKEN));
    }

    @HapiTest
    public HapiSpec initialSupplyMustBeSane() {
        return defaultHapiSpec("InitialSupplyMustBeSane")
                .given()
                .when()
                .then(
                        tokenCreate("sinking").initialSupply(-1L).hasPrecheck(INVALID_TOKEN_INITIAL_SUPPLY),
                        tokenCreate("bad decimals").decimals(-1).hasPrecheck(INVALID_TOKEN_DECIMALS),
                        tokenCreate("bad decimals").decimals(1 << 31).hasPrecheck(INVALID_TOKEN_DECIMALS),
                        tokenCreate("bad initial supply")
                                .initialSupply(1L << 63)
                                .hasPrecheck(INVALID_TOKEN_INITIAL_SUPPLY));
    }

    @HapiTest
    public HapiSpec treasuryHasCorrectBalance() {
        String token = salted("myToken");

        int decimals = 1;
        long initialSupply = 100_000;

        return defaultHapiSpec("TreasuryHasCorrectBalance")
                .given(cryptoCreate(TOKEN_TREASURY).balance(1L))
                .when(tokenCreate(token)
                        .treasury(TOKEN_TREASURY)
                        .decimals(decimals)
                        .initialSupply(initialSupply))
                .then(getAccountBalance(TOKEN_TREASURY).hasTinyBars(1L).hasTokenBalance(token, initialSupply));
    }

    @HapiTest
    private HapiSpec prechecksWork() {
        return defaultHapiSpec("PrechecksWork")
                .given(
                        cryptoCreate(TOKEN_TREASURY)
                                .withUnknownFieldIn(TRANSACTION)
                                .hasPrecheck(TRANSACTION_HAS_UNKNOWN_FIELDS),
                        cryptoCreate(TOKEN_TREASURY)
                                .withUnknownFieldIn(TRANSACTION_BODY)
                                .withProtoStructure(HapiSpecSetup.TxnProtoStructure.NEW)
                                .hasPrecheck(TRANSACTION_HAS_UNKNOWN_FIELDS),
                        cryptoCreate(TOKEN_TREASURY)
                                .withUnknownFieldIn(SIGNED_TRANSACTION)
                                .withProtoStructure(HapiSpecSetup.TxnProtoStructure.NEW)
                                .hasPrecheck(TRANSACTION_HAS_UNKNOWN_FIELDS),
                        cryptoCreate(TOKEN_TREASURY)
                                .withUnknownFieldIn(OP_BODY)
                                .hasPrecheck(TRANSACTION_HAS_UNKNOWN_FIELDS),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(FIRST_USER).balance(0L))
                .when(
                        tokenCreate(A_TOKEN).initialSupply(100).treasury(TOKEN_TREASURY),
                        tokenCreate(B_TOKEN).initialSupply(100).treasury(TOKEN_TREASURY))
                .then(
                        cryptoTransfer(
                                        moving(1, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER),
                                        moving(1, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER))
                                .dontFullyAggregateTokenTransfers()
                                .hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        cryptoTransfer(
                                        movingHbar(1).between(TOKEN_TREASURY, FIRST_USER),
                                        movingHbar(1).between(TOKEN_TREASURY, FIRST_USER))
                                .dontFullyAggregateTokenTransfers()
                                .hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        cryptoTransfer(
                                        moving(1, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER),
                                        moving(1, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER))
                                .dontFullyAggregateTokenTransfers()
                                .hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        tokenAssociate(FIRST_USER, A_TOKEN),
                        cryptoTransfer(moving(0, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER))
                                .hasPrecheck(OK),
                        cryptoTransfer(moving(10, A_TOKEN).from(TOKEN_TREASURY))
                                .hasPrecheck(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN),
                        cryptoTransfer(moving(10, A_TOKEN).empty()).hasPrecheck(EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    private final long hbarAmount = 1_234L;
    private final long htsAmount = 2_345L;
    private final long numerator = 1;
    private final long denominator = 10;
    private final long minimumToCollect = 5;
    private final long maximumToCollect = 50;
    private final String token = "withCustomSchedules";
    private final String feeDenom = "denom";
    private final String hbarCollector = "hbarFee";
    private final String htsCollector = "denomFee";
    private final String tokenCollector = "fractionalFee";
    private final String invalidEntityId = "1.2.786";
    private final long negativeHtsFee = -100L;
    private final String customFeesKey = "antique";
}
