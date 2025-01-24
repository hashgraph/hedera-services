/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFeeScheduleUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHbarFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doSeveralWithStartupConfigNow;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doWithStartupConfigNow;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.specOps;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ADDRESS_BOOK_CONTROL;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.HapiSuite.ZERO_BYTE_MEMO;
import static com.hedera.services.bdd.suites.HapiSuite.salted;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_METADATA_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static java.lang.Long.parseLong;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import com.hederahashgraph.api.proto.java.TokenType;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(TOKEN)
public class TokenUpdateSpecs {
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_SYMBOL_LENGTH = 100;
    private static final String PAYER = "payer";
    private static final String TREASURY_UPDATE_TXN = "treasuryUpdateTxn";
    private static final String INVALID_TREASURY = "invalidTreasury";

    private static final String TOKEN_TREASURY = "treasury";

    @HapiTest
    final Stream<DynamicTest> canUpdateExpiryOnlyOpWithoutAdminKey() {
        String originalMemo = "First things first";
        String saltedName = salted("primary");
        final var civilian = "civilian";
        return hapiTest(
                cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                newKeyNamed("adminKey"),
                newKeyNamed("freezeKey"),
                newKeyNamed("newFreezeKey"),
                newKeyNamed("kycKey"),
                newKeyNamed("newKycKey"),
                newKeyNamed("supplyKey"),
                newKeyNamed("newSupplyKey"),
                newKeyNamed("wipeKey"),
                newKeyNamed("newWipeKey"),
                newKeyNamed("pauseKey"),
                newKeyNamed("newPauseKey"),
                tokenCreate("primary")
                        .name(saltedName)
                        .entityMemo(originalMemo)
                        .treasury(TOKEN_TREASURY)
                        .initialSupply(500)
                        .decimals(1)
                        .adminKey("adminKey")
                        .freezeKey("freezeKey")
                        .kycKey("kycKey")
                        .supplyKey("supplyKey")
                        .wipeKey("wipeKey")
                        .pauseKey("pauseKey")
                        .payingWith(civilian),
                doWithStartupConfigNow("entities.maxLifetime", (value, now) -> tokenUpdate("primary")
                        .expiry(parseLong(value) + now.getEpochSecond() - 12345)));
    }

    @HapiTest
    final Stream<DynamicTest> validatesNewExpiry() {
        return hapiTest(tokenCreate("tbu"), doSeveralWithStartupConfigNow("entities.maxLifetime", (value, now) -> {
            final var maxLifetime = Long.parseLong(value);
            final var okExpiry = now.getEpochSecond() + maxLifetime - 12_345L;
            final var excessiveExpiry = now.getEpochSecond() + maxLifetime + 12_345L;
            return specOps(
                    tokenUpdate("tbu").expiry(excessiveExpiry).hasKnownStatus(INVALID_EXPIRATION_TIME),
                    tokenUpdate("tbu").expiry(okExpiry));
        }));
    }

    @HapiTest
    final Stream<DynamicTest> validatesAlreadyDeletedToken() {
        return hapiTest(
                newKeyNamed("adminKey"),
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                tokenCreate("tbd").adminKey("adminKey").treasury(TOKEN_TREASURY),
                tokenDelete("tbd"),
                tokenUpdate("tbd").signedByPayerAnd("adminKey").hasKnownStatus(TOKEN_WAS_DELETED));
    }

    @HapiTest
    final Stream<DynamicTest> tokensCanBeMadeImmutableWithEmptyKeyList() {
        final var mutableForNow = "mutableForNow";
        return hapiTest(
                newKeyNamed("initialAdmin"),
                cryptoCreate("neverToBe").balance(0L),
                tokenCreate(mutableForNow).adminKey("initialAdmin"),
                tokenUpdate(mutableForNow)
                        .usingInvalidAdminKey()
                        .signedByPayerAnd("initialAdmin")
                        .hasPrecheck(INVALID_ADMIN_KEY),
                tokenUpdate(mutableForNow).properlyEmptyingAdminKey().signedByPayerAnd("initialAdmin"),
                getTokenInfo(mutableForNow),
                tokenUpdate(mutableForNow)
                        .treasury("neverToBe")
                        .signedBy(GENESIS, "initialAdmin", "neverToBe")
                        .hasKnownStatus(TOKEN_IS_IMMUTABLE));
    }

    @HapiTest
    final Stream<DynamicTest> standardImmutabilitySemanticsHold() {
        long then = Instant.now().getEpochSecond() + 1_234_567L;
        final var immutable = "immutable";
        return hapiTest(
                tokenCreate(immutable).expiry(then),
                tokenUpdate(immutable).treasury(ADDRESS_BOOK_CONTROL).hasKnownStatus(TOKEN_IS_IMMUTABLE),
                tokenUpdate(immutable).expiry(then - 1).hasKnownStatus(INVALID_EXPIRATION_TIME),
                tokenUpdate(immutable).expiry(then + 1),
                getTokenInfo(immutable).logged());
    }

    @HapiTest
    final Stream<DynamicTest> validatesMissingRef() {
        return hapiTest(
                cryptoCreate(PAYER),
                tokenUpdate("0.0.0")
                        .fee(ONE_HBAR)
                        .payingWith(PAYER)
                        .signedBy(PAYER)
                        .hasKnownStatus(INVALID_TOKEN_ID),
                tokenUpdate("1.2.3")
                        .fee(ONE_HBAR)
                        .payingWith(PAYER)
                        .signedBy(PAYER)
                        .hasKnownStatus(INVALID_TOKEN_ID));
    }

    @HapiTest
    final Stream<DynamicTest> validatesMissingAdminKey() {
        return hapiTest(
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                cryptoCreate(PAYER),
                tokenCreate("tbd").treasury(TOKEN_TREASURY),
                tokenUpdate("tbd")
                        .autoRenewAccount(GENESIS)
                        .payingWith(PAYER)
                        .signedBy(PAYER, GENESIS)
                        .hasKnownStatus(TOKEN_IS_IMMUTABLE));
    }

    @HapiTest
    final Stream<DynamicTest> keysChange() {
        return hapiTest(
                newKeyNamed("adminKey"),
                newKeyNamed("newAdminKey"),
                newKeyNamed("kycThenFreezeKey"),
                newKeyNamed("freezeThenKycKey"),
                newKeyNamed("wipeThenSupplyKey"),
                newKeyNamed("supplyThenWipeKey"),
                newKeyNamed("oldFeeScheduleKey"),
                newKeyNamed("newFeeScheduleKey"),
                cryptoCreate("misc").balance(0L),
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                tokenCreate("tbu")
                        .treasury(TOKEN_TREASURY)
                        .freezeDefault(true)
                        .initialSupply(10)
                        .adminKey("adminKey")
                        .kycKey("kycThenFreezeKey")
                        .freezeKey("freezeThenKycKey")
                        .supplyKey("supplyThenWipeKey")
                        .wipeKey("wipeThenSupplyKey")
                        .feeScheduleKey("oldFeeScheduleKey"),
                getTokenInfo("tbu").logged(),
                tokenUpdate("tbu")
                        .adminKey("newAdminKey")
                        .kycKey("freezeThenKycKey")
                        .freezeKey("kycThenFreezeKey")
                        .wipeKey("supplyThenWipeKey")
                        .supplyKey("wipeThenSupplyKey")
                        .feeScheduleKey("newFeeScheduleKey")
                        .signedByPayerAnd("adminKey", "newAdminKey"),
                tokenAssociate("misc", "tbu"),
                getTokenInfo("tbu").logged(),
                tokenUnfreeze("tbu", "misc").signedBy(GENESIS, "kycThenFreezeKey"),
                grantTokenKyc("tbu", "misc").signedBy(GENESIS, "freezeThenKycKey"),
                getAccountInfo("misc").logged(),
                cryptoTransfer(moving(5, "tbu").between(TOKEN_TREASURY, "misc")),
                mintToken("tbu", 10).signedBy(GENESIS, "wipeThenSupplyKey"),
                burnToken("tbu", 10).signedBy(GENESIS, "wipeThenSupplyKey"),
                wipeTokenAccount("tbu", "misc", 5).signedBy(GENESIS, "supplyThenWipeKey"),
                getAccountInfo(TOKEN_TREASURY).logged());
    }

    @HapiTest
    final Stream<DynamicTest> newTreasuryAutoAssociationWorks() {
        return hapiTest(
                newKeyNamed("adminKey"),
                cryptoCreate("oldTreasury").balance(0L),
                tokenCreate("tbu").adminKey("adminKey").treasury("oldTreasury"),
                cryptoCreate("newTreasuryWithoutRemainingAutoAssociations").balance(0L),
                cryptoCreate("newTreasuryWithRemainingAutoAssociations")
                        .balance(0L)
                        .maxAutomaticTokenAssociations(10),
                tokenUpdate("tbu")
                        .treasury("newTreasuryWithoutRemainingAutoAssociations")
                        .signedByPayerAnd("adminKey", "newTreasuryWithoutRemainingAutoAssociations")
                        .hasKnownStatus(NO_REMAINING_AUTOMATIC_ASSOCIATIONS),
                tokenUpdate("tbu")
                        .treasury("newTreasuryWithRemainingAutoAssociations")
                        .signedByPayerAnd("adminKey", "newTreasuryWithRemainingAutoAssociations"),
                getTokenInfo("tbu").hasTreasury("newTreasuryWithRemainingAutoAssociations"));
    }

    @HapiTest
    final Stream<DynamicTest> newTreasuryMustSign() {
        return hapiTest(
                newKeyNamed("adminKey"),
                cryptoCreate("oldTreasury").balance(0L),
                cryptoCreate("newTreasury").balance(0L),
                tokenCreate("tbu").adminKey("adminKey").treasury("oldTreasury"),
                tokenAssociate("newTreasury", "tbu"),
                cryptoTransfer(moving(1, "tbu").between("oldTreasury", "newTreasury")),
                tokenUpdate("tbu")
                        .treasury("newTreasury")
                        .signedBy(GENESIS, "adminKey")
                        .hasKnownStatus(INVALID_SIGNATURE),
                tokenUpdate("tbu").treasury("newTreasury").signedByPayerAnd("adminKey", "newTreasury"));
    }

    @HapiTest
    final Stream<DynamicTest> treasuryEvolves() {
        return hapiTest(
                newKeyNamed("adminKey"),
                newKeyNamed("kycKey"),
                newKeyNamed("freezeKey"),
                cryptoCreate("oldTreasury").balance(0L),
                cryptoCreate("newTreasury").balance(0L),
                tokenCreate("tbu")
                        .adminKey("adminKey")
                        .freezeDefault(true)
                        .kycKey("kycKey")
                        .freezeKey("freezeKey")
                        .treasury("oldTreasury"),
                getAccountInfo("oldTreasury").logged(),
                getAccountInfo("newTreasury").logged(),
                tokenAssociate("newTreasury", "tbu"),
                tokenUpdate("tbu")
                        .treasury("newTreasury")
                        .via(TREASURY_UPDATE_TXN)
                        .signedByPayerAnd("adminKey", "newTreasury"),
                getAccountInfo("oldTreasury").logged(),
                getAccountInfo("newTreasury").logged(),
                getTxnRecord(TREASURY_UPDATE_TXN).logged());
    }

    @HapiTest
    final Stream<DynamicTest> validAutoRenewWorks() {
        final var firstPeriod = THREE_MONTHS_IN_SECONDS;
        final var secondPeriod = THREE_MONTHS_IN_SECONDS + 1234;
        return hapiTest(
                cryptoCreate("autoRenew").balance(0L),
                cryptoCreate("newAutoRenew").balance(0L),
                newKeyNamed("adminKey"),
                tokenCreate("tbu")
                        .adminKey("adminKey")
                        .autoRenewAccount("autoRenew")
                        .autoRenewPeriod(firstPeriod),
                tokenUpdate("tbu")
                        .signedBy(GENESIS, "adminKey")
                        .autoRenewAccount("newAutoRenew")
                        .autoRenewPeriod(secondPeriod)
                        .hasKnownStatus(INVALID_SIGNATURE),
                tokenUpdate("tbu")
                        .autoRenewAccount("newAutoRenew")
                        .autoRenewPeriod(secondPeriod)
                        .signedByPayerAnd("adminKey", "newAutoRenew"),
                getTokenInfo("tbu").logged());
    }

    @HapiTest
    final Stream<DynamicTest> symbolChanges() {
        var hopefullyUnique = "ORIGINAL" + TxnUtils.randomUppercase(5);

        return hapiTest(
                newKeyNamed("adminKey"),
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                tokenCreate("tbu").adminKey("adminKey").treasury(TOKEN_TREASURY),
                tokenUpdate("tbu").symbol(hopefullyUnique).signedByPayerAnd("adminKey"),
                getTokenInfo("tbu").hasSymbol(hopefullyUnique),
                tokenAssociate(GENESIS, "tbu"),
                cryptoTransfer(moving(1, "tbu").between(TOKEN_TREASURY, GENESIS)));
    }

    @HapiTest
    final Stream<DynamicTest> changeAutoRenewAccount() {
        var account = "autoRenewAccount";

        return hapiTest(
                newKeyNamed("adminKey"),
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                cryptoCreate(account).balance(0L),
                tokenCreate("tbu").adminKey("adminKey").treasury(TOKEN_TREASURY),
                tokenUpdate("tbu")
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS + 1)
                        .autoRenewAccount(account)
                        .signedByPayerAnd("adminKey", account),
                getTokenInfo("tbu").hasAutoRenewAccount(account));
    }

    @HapiTest
    final Stream<DynamicTest> nameChanges() {
        var hopefullyUnique = "ORIGINAL" + TxnUtils.randomUppercase(5);

        return hapiTest(
                newKeyNamed("adminKey"),
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                tokenCreate("tbu").adminKey("adminKey").treasury(TOKEN_TREASURY),
                tokenUpdate("tbu").name(hopefullyUnique).signedByPayerAnd("adminKey"),
                getTokenInfo("tbu").hasName(hopefullyUnique));
    }

    @HapiTest
    final Stream<DynamicTest> tooLongNameCheckHolds() {
        var tooLongName = "ORIGINAL" + TxnUtils.randomUppercase(MAX_NAME_LENGTH + 1);

        return hapiTest(
                newKeyNamed("adminKey"),
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                tokenCreate("tbu").adminKey("adminKey").treasury(TOKEN_TREASURY),
                tokenUpdate("tbu")
                        .name(tooLongName)
                        .signedByPayerAnd("adminKey")
                        .hasPrecheck(TOKEN_NAME_TOO_LONG));
    }

    @HapiTest
    final Stream<DynamicTest> tooLongSymbolCheckHolds() {
        var tooLongSymbol = TxnUtils.randomUppercase(MAX_SYMBOL_LENGTH + 1);

        return hapiTest(
                newKeyNamed("adminKey"),
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                tokenCreate("tbu").adminKey("adminKey").treasury(TOKEN_TREASURY),
                tokenUpdate("tbu")
                        .symbol(tooLongSymbol)
                        .signedByPayerAnd("adminKey")
                        .hasPrecheck(TOKEN_SYMBOL_TOO_LONG));
    }

    @HapiTest
    final Stream<DynamicTest> deletedAutoRenewAccountCheckHolds() {
        return hapiTest(
                newKeyNamed("adminKey"),
                cryptoCreate("autoRenewAccount").balance(0L),
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                cryptoDelete("autoRenewAccount"),
                tokenCreate("tbu").adminKey("adminKey").treasury(TOKEN_TREASURY),
                tokenUpdate("tbu")
                        .autoRenewAccount("autoRenewAccount")
                        .signedByPayerAnd("adminKey", "autoRenewAccount")
                        .hasKnownStatus(INVALID_AUTORENEW_ACCOUNT));
    }

    @HapiTest
    final Stream<DynamicTest> renewalPeriodCheckHolds() {
        return hapiTest(
                newKeyNamed("adminKey"),
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                cryptoCreate("autoRenewAccount").balance(0L),
                tokenCreate("tbu").adminKey("adminKey").treasury(TOKEN_TREASURY),
                tokenCreate("withAutoRenewAcc")
                        .adminKey("adminKey")
                        .autoRenewAccount("autoRenewAccount")
                        .treasury(TOKEN_TREASURY),
                tokenUpdate("tbu")
                        .autoRenewAccount("autoRenewAccount")
                        .autoRenewPeriod(-1123)
                        .signedByPayerAnd("adminKey", "autoRenewAccount")
                        .hasKnownStatus(INVALID_RENEWAL_PERIOD),
                tokenUpdate("tbu")
                        .autoRenewAccount("autoRenewAccount")
                        .autoRenewPeriod(0)
                        .signedByPayerAnd("adminKey", "autoRenewAccount")
                        .hasKnownStatus(INVALID_RENEWAL_PERIOD),
                tokenUpdate("withAutoRenewAcc")
                        .autoRenewPeriod(-1)
                        .signedByPayerAnd("adminKey")
                        .hasKnownStatus(INVALID_RENEWAL_PERIOD),
                tokenUpdate("withAutoRenewAcc")
                        .autoRenewPeriod(100000000000L)
                        .signedByPayerAnd("adminKey")
                        .hasKnownStatus(INVALID_RENEWAL_PERIOD));
    }

    @HapiTest
    final Stream<DynamicTest> invalidTreasuryCheckHolds() {
        return hapiTest(
                newKeyNamed("adminKey"),
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                cryptoCreate(INVALID_TREASURY).balance(0L),
                cryptoDelete(INVALID_TREASURY),
                tokenCreate("tbu")
                        .adminKey("adminKey")
                        .treasury(TOKEN_TREASURY)
                        .treasury(INVALID_TREASURY)
                        .signedByPayerAnd("adminKey", INVALID_TREASURY)
                        .hasKnownStatus(ACCOUNT_DELETED));
    }

    @HapiTest
    final Stream<DynamicTest> updateHappyPath() {
        String originalMemo = "First things first";
        String updatedMemo = "Nothing left to do";
        String saltedName = salted("primary");
        String newSaltedName = salted("primary");
        final var civilian = "civilian";
        return hapiTest(
                cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                cryptoCreate("newTokenTreasury").balance(0L),
                cryptoCreate("autoRenewAccount").balance(0L),
                cryptoCreate("newAutoRenewAccount").balance(0L),
                newKeyNamed("adminKey"),
                newKeyNamed("freezeKey"),
                newKeyNamed("newFreezeKey"),
                newKeyNamed("kycKey"),
                newKeyNamed("newKycKey"),
                newKeyNamed("supplyKey"),
                newKeyNamed("newSupplyKey"),
                newKeyNamed("wipeKey"),
                newKeyNamed("newWipeKey"),
                newKeyNamed("pauseKey"),
                newKeyNamed("newPauseKey"),
                tokenCreate("primary")
                        .name(saltedName)
                        .entityMemo(originalMemo)
                        .treasury(TOKEN_TREASURY)
                        .autoRenewAccount("autoRenewAccount")
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                        .initialSupply(500)
                        .decimals(1)
                        .adminKey("adminKey")
                        .freezeKey("freezeKey")
                        .kycKey("kycKey")
                        .supplyKey("supplyKey")
                        .wipeKey("wipeKey")
                        .pauseKey("pauseKey")
                        .payingWith(civilian),
                tokenAssociate("newTokenTreasury", "primary"),
                tokenUpdate("primary")
                        .entityMemo(ZERO_BYTE_MEMO)
                        .signedByPayerAnd("adminKey")
                        .hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                tokenUpdate("primary")
                        .name(newSaltedName)
                        .entityMemo(updatedMemo)
                        .treasury("newTokenTreasury")
                        .autoRenewAccount("newAutoRenewAccount")
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS + 1)
                        .freezeKey("newFreezeKey")
                        .kycKey("newKycKey")
                        .supplyKey("newSupplyKey")
                        .wipeKey("newWipeKey")
                        .pauseKey("newPauseKey")
                        .signedByPayerAnd("adminKey", "newTokenTreasury", "newAutoRenewAccount", civilian)
                        .payingWith(civilian),
                getAccountBalance(TOKEN_TREASURY).hasTokenBalance("primary", 0),
                getAccountBalance("newTokenTreasury").hasTokenBalance("primary", 500),
                getAccountInfo(TOKEN_TREASURY)
                        .hasToken(ExpectedTokenRel.relationshipWith("primary").balance(0)),
                getAccountInfo("newTokenTreasury")
                        .hasToken(ExpectedTokenRel.relationshipWith("primary")
                                .freeze(TokenFreezeStatus.Unfrozen)
                                .kyc(TokenKycStatus.Granted)
                                .balance(500)),
                getTokenInfo("primary")
                        .logged()
                        .hasEntityMemo(updatedMemo)
                        .hasRegisteredId("primary")
                        .hasName(newSaltedName)
                        .hasTreasury("newTokenTreasury")
                        .hasFreezeKey("primary")
                        .hasKycKey("primary")
                        .hasSupplyKey("primary")
                        .hasWipeKey("primary")
                        .hasPauseKey("primary")
                        .hasTotalSupply(500)
                        .hasAutoRenewAccount("newAutoRenewAccount")
                        .hasPauseStatus(TokenPauseStatus.Unpaused)
                        .hasAutoRenewPeriod(THREE_MONTHS_IN_SECONDS + 1));
    }

    @HapiTest
    final Stream<DynamicTest> updateTokenTreasuryRequiresZeroTokenBalance() {
        return hapiTest(
                cryptoCreate("oldTreasury"),
                cryptoCreate("newTreasury"),
                newKeyNamed("adminKey"),
                newKeyNamed("supplyKey"),
                tokenCreate("non-fungible")
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0)
                        .adminKey("adminKey")
                        .supplyKey("supplyKey")
                        .treasury("oldTreasury"),
                mintToken("non-fungible", List.of(ByteString.copyFromUtf8("memo"), ByteString.copyFromUtf8("memo1"))),
                tokenAssociate("newTreasury", "non-fungible"),
                cryptoTransfer(movingUnique("non-fungible", 1).between("oldTreasury", "newTreasury")),
                tokenUpdate("non-fungible")
                        .treasury("newTreasury")
                        .signedByPayerAnd("adminKey", "newTreasury")
                        .hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES));
    }

    @HapiTest
    final Stream<DynamicTest> tokenUpdateCanClearMemo() {
        final var token = "token";
        final var multiKey = "multiKey";
        final var memoToBeErased = "memoToBeErased";
        return hapiTest(
                newKeyNamed(multiKey),
                tokenCreate(token).entityMemo(memoToBeErased).adminKey(multiKey),
                getTokenInfo(token).hasEntityMemo(memoToBeErased),
                tokenUpdate(token).entityMemo("").signedByPayerAnd(multiKey),
                getTokenInfo(token).logged().hasEntityMemo(""));
    }

    @HapiTest
    final Stream<DynamicTest> updateNftTreasuryHappyPath() {
        return hapiTest(
                cryptoCreate(TOKEN_TREASURY),
                cryptoCreate("newTokenTreasury"),
                newKeyNamed("adminKeyA"),
                newKeyNamed("supplyKeyA"),
                newKeyNamed("pauseKeyA"),
                tokenCreate("primary")
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .treasury(TOKEN_TREASURY)
                        .initialSupply(0)
                        .adminKey("adminKeyA")
                        .supplyKey("supplyKeyA")
                        .pauseKey("pauseKeyA"),
                mintToken("primary", List.of(ByteString.copyFromUtf8("memo1"))),
                tokenAssociate("newTokenTreasury", "primary"),
                tokenUpdate("primary")
                        .treasury("newTokenTreasury")
                        .via("tokenUpdateTxn")
                        .signedByPayerAnd("adminKeyA", "newTokenTreasury"),
                getAccountBalance(TOKEN_TREASURY).hasTokenBalance("primary", 0),
                getAccountBalance("newTokenTreasury").hasTokenBalance("primary", 1),
                getTokenInfo("primary")
                        .hasTreasury("newTokenTreasury")
                        .hasPauseKey("primary")
                        .hasPauseStatus(TokenPauseStatus.Unpaused)
                        .logged(),
                getTokenNftInfo("primary", 1).hasAccountID("newTokenTreasury").logged());
    }

    @HapiTest
    final Stream<DynamicTest> safeToUpdateCustomFeesWithNewFallbackWhileTransferring() {
        final var uniqueTokenFeeKey = "uniqueTokenFeeKey";
        final var hbarCollector = "hbarFee";
        final var beneficiary = "luckyOne";
        final var multiKey = "allSeasons";
        final var sender = "sender";

        return hapiTest(
                newKeyNamed(multiKey),
                cryptoCreate(hbarCollector),
                cryptoCreate(TOKEN_TREASURY),
                cryptoCreate(sender).maxAutomaticTokenAssociations(100),
                cryptoCreate(beneficiary).maxAutomaticTokenAssociations(100),
                withOpContext((spec, opLog) -> {
                    for (int i = 0; i < 3; i++) {
                        final var name = uniqueTokenFeeKey + i;
                        final var creation = tokenCreate(name)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(multiKey)
                                .feeScheduleKey(multiKey)
                                .initialSupply(0);
                        final var mint = mintToken(name, List.of(ByteString.copyFromUtf8("SOLO")));
                        final var normalXfer = cryptoTransfer(
                                        movingUnique(name, 1L).between(TOKEN_TREASURY, sender))
                                .fee(ONE_HBAR);
                        final var update = tokenFeeScheduleUpdate(name)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 10, fixedHbarFeeInheritingRoyaltyCollector(1), hbarCollector))
                                .deferStatusResolution();
                        final var raceXfer = cryptoTransfer(
                                        movingUnique(name, 1L).between(sender, beneficiary))
                                .signedBy(DEFAULT_PAYER, sender)
                                .fee(ONE_HBAR)
                                /* The beneficiary needs to sign now b/c of the fallback fee (and the
                                 * lack of any fungible value going back to the treasury for this NFT). */
                                .hasKnownStatus(INVALID_SIGNATURE);
                        allRunFor(spec, creation, mint, normalXfer, update, raceXfer);
                    }
                }));
    }

    @HapiTest
    final Stream<DynamicTest> customFeesOnlyUpdatableWithKey() {
        final var origHbarFee = 1_234L;
        final var newHbarFee = 4_321L;

        final var tokenNoFeeKey = "justSchedule";
        final var uniqueTokenFeeKey = "uniqueTokenFeeKey";
        final var tokenWithFeeKey = "bothScheduleAndKey";
        final var hbarCollector = "hbarFee";

        final var adminKey = "admin";
        final var feeScheduleKey = "feeSchedule";
        final var newFeeScheduleKey = "feeScheduleRedux";

        return hapiTest(
                newKeyNamed(adminKey),
                newKeyNamed(feeScheduleKey),
                newKeyNamed(newFeeScheduleKey),
                cryptoCreate(hbarCollector),
                tokenCreate(tokenNoFeeKey).adminKey(adminKey).withCustom(fixedHbarFee(origHbarFee, hbarCollector)),
                tokenCreate(uniqueTokenFeeKey)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .supplyKey(adminKey)
                        .feeScheduleKey(feeScheduleKey)
                        .initialSupply(0)
                        .adminKey(adminKey)
                        .withCustom(fixedHbarFee(origHbarFee, hbarCollector)),
                tokenCreate(tokenWithFeeKey)
                        .adminKey(adminKey)
                        .feeScheduleKey(feeScheduleKey)
                        .withCustom(fixedHbarFee(origHbarFee, hbarCollector)),
                tokenUpdate(tokenNoFeeKey)
                        .feeScheduleKey(newFeeScheduleKey)
                        .signedByPayerAnd(adminKey)
                        .hasKnownStatus(TOKEN_HAS_NO_FEE_SCHEDULE_KEY),
                tokenUpdate(tokenWithFeeKey)
                        .usingInvalidFeeScheduleKey()
                        .feeScheduleKey(newFeeScheduleKey)
                        .signedByPayerAnd(adminKey)
                        .hasPrecheck(INVALID_CUSTOM_FEE_SCHEDULE_KEY),
                tokenUpdate(tokenWithFeeKey).feeScheduleKey(newFeeScheduleKey).signedByPayerAnd(adminKey),
                tokenFeeScheduleUpdate(tokenWithFeeKey).withCustom(fixedHbarFee(newHbarFee, hbarCollector)),
                tokenFeeScheduleUpdate(uniqueTokenFeeKey)
                        .withCustom(royaltyFeeWithFallback(
                                1, 3, fixedHbarFeeInheritingRoyaltyCollector(1_000), hbarCollector)),
                getTokenInfo(tokenWithFeeKey)
                        .hasCustom(fixedHbarFeeInSchedule(newHbarFee, hbarCollector))
                        .hasFeeScheduleKey(tokenWithFeeKey));
    }

    @HapiTest
    final Stream<DynamicTest> tokenUpdateTokenHasNoFreezeKey() {
        final var tokenName = "token";
        final var HBAR_COLLECTOR = "hbarFee";

        final var admin = "admin";
        final var adminKey = "adminKey";
        final var freezeKey = "freezeKey";
        final var freeze = "freeze";
        final var freezeKey2 = "freezeKey2";

        return hapiTest(
                newKeyNamed(adminKey),
                newKeyNamed(freezeKey),
                newKeyNamed(freezeKey2),
                cryptoCreate(HBAR_COLLECTOR),
                cryptoCreate(TOKEN_TREASURY),
                cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                cryptoCreate(freeze).key(freezeKey).balance(ONE_MILLION_HBARS),
                tokenCreate(tokenName)
                        .treasury(TOKEN_TREASURY)
                        .initialSupply(10)
                        .adminKey(adminKey)
                        .payingWith(admin),
                tokenUpdate(tokenName)
                        .freezeKey(freezeKey2)
                        .signedBy(adminKey, freezeKey)
                        .payingWith(freeze)
                        .hasKnownStatus(TOKEN_HAS_NO_FREEZE_KEY));
    }

    @HapiTest
    final Stream<DynamicTest> tokenUpdateTokenHasNoSupplyKey() {
        final var tokenName = "token";
        final var HBAR_COLLECTOR = "hbarFee";

        final var admin = "admin";
        final var adminKey = "adminKey";
        final var supplyKey = "supplyKey";
        final var supply = "supply";
        final var supplyKey2 = "supplyKey2";

        return hapiTest(
                newKeyNamed(adminKey),
                newKeyNamed(supplyKey),
                newKeyNamed(supplyKey2),
                cryptoCreate(HBAR_COLLECTOR),
                cryptoCreate(TOKEN_TREASURY),
                cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                cryptoCreate(supply).key(supplyKey).balance(ONE_MILLION_HBARS),
                tokenCreate(tokenName)
                        .treasury(TOKEN_TREASURY)
                        .initialSupply(10)
                        .adminKey(adminKey)
                        .payingWith(admin),
                tokenUpdate(tokenName)
                        .supplyKey(supplyKey2)
                        .signedBy(adminKey, supplyKey)
                        .payingWith(supply)
                        .hasKnownStatus(TOKEN_HAS_NO_SUPPLY_KEY));
    }

    @HapiTest
    final Stream<DynamicTest> tokenUpdateTokenHasNoKycKey() {
        final var tokenName = "token";
        final var HBAR_COLLECTOR = "hbarFee";

        final var admin = "admin";
        final var adminKey = "adminKey";
        final var kycKey = "kycKey";
        final var kyc = "kyc";
        final var kycKey2 = "kycKey2";

        return hapiTest(
                newKeyNamed(adminKey),
                newKeyNamed(kycKey),
                newKeyNamed(kycKey2),
                cryptoCreate(HBAR_COLLECTOR),
                cryptoCreate(TOKEN_TREASURY),
                cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                cryptoCreate(kyc).key(kycKey).balance(ONE_MILLION_HBARS),
                tokenCreate(tokenName)
                        .treasury(TOKEN_TREASURY)
                        .initialSupply(10)
                        .adminKey(adminKey)
                        .payingWith(admin),
                tokenUpdate(tokenName)
                        .kycKey(kycKey2)
                        .signedBy(adminKey, kycKey)
                        .payingWith(kyc)
                        .hasKnownStatus(TOKEN_HAS_NO_KYC_KEY));
    }

    @HapiTest
    final Stream<DynamicTest> tokenUpdateTokenHasNoWipeKey() {
        final var tokenName = "token";
        final var HBAR_COLLECTOR = "hbarFee";

        final var admin = "admin";
        final var adminKey = "adminKey";
        final var wipeKey = "wipeKey";
        final var wipe = "wipe";
        final var wipeKey2 = "wipeKey2";

        return hapiTest(
                newKeyNamed(adminKey),
                newKeyNamed(wipeKey),
                newKeyNamed(wipeKey2),
                cryptoCreate(HBAR_COLLECTOR),
                cryptoCreate(TOKEN_TREASURY),
                cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                cryptoCreate(wipe).key(wipeKey).balance(ONE_MILLION_HBARS),
                tokenCreate(tokenName)
                        .treasury(TOKEN_TREASURY)
                        .initialSupply(10)
                        .adminKey(adminKey)
                        .payingWith(admin),
                tokenUpdate(tokenName)
                        .wipeKey(wipeKey2)
                        .signedBy(adminKey, wipeKey)
                        .payingWith(wipe)
                        .hasKnownStatus(TOKEN_HAS_NO_WIPE_KEY));
    }

    @HapiTest
    final Stream<DynamicTest> tokenUpdateTokenHasNoPauseKey() {
        final var tokenName = "token";
        final var HBAR_COLLECTOR = "hbarFee";

        final var admin = "admin";
        final var adminKey = "adminKey";
        final var pauseKey = "pauseKey";
        final var pause = "pause";
        final var pauseKey2 = "pauseKey2";

        return hapiTest(
                newKeyNamed(adminKey),
                newKeyNamed(pauseKey),
                newKeyNamed(pauseKey2),
                cryptoCreate(HBAR_COLLECTOR),
                cryptoCreate(TOKEN_TREASURY),
                cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                cryptoCreate(pause).key(pauseKey).balance(ONE_MILLION_HBARS),
                tokenCreate(tokenName)
                        .treasury(TOKEN_TREASURY)
                        .initialSupply(10)
                        .adminKey(adminKey)
                        .payingWith(admin),
                tokenUpdate(tokenName)
                        .pauseKey(pauseKey2)
                        .signedBy(adminKey, pauseKey)
                        .payingWith(pause)
                        .hasKnownStatus(TOKEN_HAS_NO_PAUSE_KEY));
    }

    @HapiTest
    final Stream<DynamicTest> tokenUpdateTokenHasNoMetadataKey() {
        final var tokenName = "token";
        final var HBAR_COLLECTOR = "hbarFee";

        final var admin = "admin";
        final var adminKey = "adminKey";
        final var metadataKey = "metadataKey";
        final var metadata = "metadata";
        final var metadataKey2 = "metadataKey2";

        return hapiTest(
                newKeyNamed(adminKey),
                newKeyNamed(metadataKey),
                newKeyNamed(metadataKey2),
                cryptoCreate(HBAR_COLLECTOR),
                cryptoCreate(TOKEN_TREASURY),
                cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                cryptoCreate(metadata).key(metadataKey).balance(ONE_MILLION_HBARS),
                tokenCreate(tokenName)
                        .treasury(TOKEN_TREASURY)
                        .initialSupply(10)
                        .adminKey(adminKey)
                        .payingWith(admin),
                tokenUpdate(tokenName)
                        .metadataKey(metadataKey2)
                        .signedBy(adminKey, metadataKey)
                        .payingWith(metadata)
                        .hasKnownStatus(TOKEN_HAS_NO_METADATA_KEY));
    }

    @HapiTest
    final Stream<DynamicTest> updateUniqueTreasuryWithNfts() {
        final var specialKey = "special";

        return hapiTest(
                newKeyNamed(specialKey),
                cryptoCreate("oldTreasury").balance(0L),
                cryptoCreate("newTreasury").balance(0L),
                tokenCreate("tbu")
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0L)
                        .adminKey(specialKey)
                        .supplyKey(specialKey)
                        .treasury("oldTreasury"),
                mintToken("tbu", List.of(ByteString.copyFromUtf8("BLAMMO"))),
                getAccountInfo("oldTreasury").logged(),
                getAccountInfo("newTreasury").logged(),
                tokenAssociate("newTreasury", "tbu"),
                tokenUpdate("tbu").memo("newMemo").signedByPayerAnd(specialKey),
                tokenUpdate("tbu").treasury("newTreasury").signedByPayerAnd(specialKey, "newTreasury"),
                burnToken("tbu", List.of(1L)),
                getTokenInfo("tbu").hasTreasury("newTreasury"),
                tokenUpdate("tbu")
                        .treasury("newTreasury")
                        .via(TREASURY_UPDATE_TXN)
                        .signedByPayerAnd(specialKey, "newTreasury"),
                getAccountInfo("oldTreasury").logged(),
                getAccountInfo("newTreasury").logged(),
                getTokenInfo("tbu").hasTreasury("newTreasury"));
    }

    @LeakyHapiTest(overrides = {"tokens.nfts.useTreasuryWildcards"})
    final Stream<DynamicTest> tokenFrozenOnTreasuryCannotBeUpdated() {
        final var accountToFreeze = "account";
        final var adminKey = "adminKey";
        final var tokenToFreeze = "token";
        return hapiTest(
                overriding("tokens.nfts.useTreasuryWildcards", "false"),
                newKeyNamed(adminKey),
                cryptoCreate(accountToFreeze),
                tokenCreate(tokenToFreeze)
                        .treasury(accountToFreeze)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0)
                        .supplyKey(adminKey)
                        .freezeKey(adminKey)
                        .adminKey(adminKey)
                        .hasKnownStatus(SUCCESS),
                tokenFreeze(tokenToFreeze, accountToFreeze),
                tokenAssociate(DEFAULT_PAYER, tokenToFreeze),
                tokenUpdate(tokenToFreeze)
                        .treasury(DEFAULT_PAYER)
                        .signedBy(DEFAULT_PAYER, adminKey)
                        .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN));
    }
}
