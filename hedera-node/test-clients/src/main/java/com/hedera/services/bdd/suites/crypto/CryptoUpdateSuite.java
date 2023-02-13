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
package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.onlyDefaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyLabel.complex;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.ANY;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.suites.contract.hapi.ContractUpdateSuite.ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.keys.KeyLabel;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoUpdateSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CryptoUpdateSuite.class);

    private static final long DEFAULT_MAX_LIFETIME =
            Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));
    public static final String REPEATING_KEY = "repeatingKey";
    public static final String TEST_ACCOUNT = "testAccount";
    public static final String ORIG_KEY = "origKey";
    public static final String UPD_KEY = "updKey";

    public static void main(String... args) {
        new CryptoUpdateSuite().runSuiteAsync();
    }

    private final SigControl twoLevelThresh =
            SigControl.threshSigs(
                    2,
                    SigControl.threshSigs(1, ANY, ANY, ANY, ANY, ANY, ANY, ANY),
                    SigControl.threshSigs(3, ANY, ANY, ANY, ANY, ANY, ANY, ANY));
    private final KeyLabel overlappingKeys =
            complex(
                    complex("A", "B", "C", "D", "E", "F", "G"),
                    complex("H", "I", "J", "K", "L", "M", "A"));

    private final SigControl ENOUGH_UNIQUE_SIGS =
            SigControl.threshSigs(
                    2,
                    SigControl.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, ON),
                    SigControl.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
    private final SigControl NOT_ENOUGH_UNIQUE_SIGS =
            SigControl.threshSigs(
                    2,
                    SigControl.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, OFF),
                    SigControl.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
    private final SigControl ENOUGH_OVERLAPPING_SIGS =
            SigControl.threshSigs(
                    2,
                    SigControl.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, OFF),
                    SigControl.threshSigs(3, ON, ON, OFF, OFF, OFF, OFF, ON));

    private final String TARGET_KEY = "twoLevelThreshWithOverlap";
    private final String TARGET_ACCOUNT = "complexKeyAccount";

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                updateWithUniqueSigs(),
                updateWithOverlappingSigs(),
                updateWithOneEffectiveSig(),
                canUpdateMemo(),
                updateFailsWithInsufficientSigs(),
                cannotSetThresholdNegative(),
                updateWithEmptyKeyFails(),
                updateFailsIfMissingSigs(),
                updateFailsWithContractKey(),
                updateFailsWithOverlyLongLifetime(),
                usdFeeAsExpected(),
                sysAccountKeyUpdateBySpecialWontNeedNewKeyTxnSign(),
                updateMaxAutoAssociationsWorks(),
                updateStakingFieldsWorks());
    }

    private HapiSpec updateStakingFieldsWorks() {
        return defaultHapiSpec("updateStakingFieldsWorks")
                .given(
                        newKeyNamed(ADMIN_KEY),
                        cryptoCreate("user")
                                .key(ADMIN_KEY)
                                .stakedAccountId("0.0.20")
                                .declinedReward(true),
                        getAccountInfo("user")
                                .has(
                                        AccountInfoAsserts.accountWith()
                                                .stakedAccountId("0.0.20")
                                                .noStakingNodeId()
                                                .isDeclinedReward(true)))
                .when(
                        cryptoUpdate("user").newStakedNodeId(0L).newDeclinedReward(false),
                        getAccountInfo("user")
                                .has(
                                        AccountInfoAsserts.accountWith()
                                                .noStakedAccountId()
                                                .stakedNodeId(0L)
                                                .isDeclinedReward(false)),
                        cryptoUpdate("user").newStakedNodeId(-1L),
                        getAccountInfo("user")
                                .has(
                                        AccountInfoAsserts.accountWith()
                                                .noStakedAccountId()
                                                .noStakingNodeId()
                                                .isDeclinedReward(false)))
                .then(
                        cryptoUpdate("user")
                                .key(ADMIN_KEY)
                                .newStakedAccountId("0.0.20")
                                .newDeclinedReward(true),
                        getAccountInfo("user")
                                .has(
                                        AccountInfoAsserts.accountWith()
                                                .stakedAccountId("0.0.20")
                                                .noStakingNodeId()
                                                .isDeclinedReward(true)),
                        cryptoUpdate("user").key(ADMIN_KEY).newStakedAccountId("0.0.0"),
                        getAccountInfo("user")
                                .has(
                                        AccountInfoAsserts.accountWith()
                                                .noStakedAccountId()
                                                .noStakingNodeId()
                                                .isDeclinedReward(true)));
    }

    private HapiSpec usdFeeAsExpected() {
        double autoAssocSlotPrice = 0.0018;
        double baseFee = 0.00022;
        double plusOneSlotFee = baseFee + autoAssocSlotPrice;
        double plusTenSlotsFee = baseFee + 10 * autoAssocSlotPrice;

        final var baseTxn = "baseTxn";
        final var plusOneTxn = "plusOneTxn";
        final var plusTenTxn = "plusTenTxn";

        AtomicLong expiration = new AtomicLong();
        return defaultHapiSpec("UsdFeeAsExpectedCryptoUpdate")
                .given(
                        newKeyNamed("key").shape(SIMPLE),
                        cryptoCreate("payer").key("key").balance(1_000 * ONE_HBAR),
                        cryptoCreate("canonicalAccount")
                                .key("key")
                                .balance(100 * ONE_HBAR)
                                .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                                .blankMemo()
                                .payingWith("payer"),
                        cryptoCreate("autoAssocTarget")
                                .key("key")
                                .balance(100 * ONE_HBAR)
                                .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                                .blankMemo()
                                .payingWith("payer"),
                        getAccountInfo("canonicalAccount").exposingExpiry(expiration::set))
                .when(
                        sourcing(
                                () ->
                                        cryptoUpdate("canonicalAccount")
                                                .payingWith("canonicalAccount")
                                                .expiring(
                                                        expiration.get() + THREE_MONTHS_IN_SECONDS)
                                                .blankMemo()
                                                .via(baseTxn)),
                        cryptoUpdate("autoAssocTarget")
                                .payingWith("autoAssocTarget")
                                .blankMemo()
                                .maxAutomaticAssociations(1)
                                .via(plusOneTxn),
                        cryptoUpdate("autoAssocTarget")
                                .payingWith("autoAssocTarget")
                                .blankMemo()
                                .maxAutomaticAssociations(11)
                                .via(plusTenTxn))
                .then(
                        validateChargedUsd(baseTxn, baseFee),
                        validateChargedUsd(plusOneTxn, plusOneSlotFee),
                        validateChargedUsd(plusTenTxn, plusTenSlotsFee));
    }

    private HapiSpec updateFailsWithOverlyLongLifetime() {
        final var smallBuffer = 12_345L;
        final var excessiveExpiry =
                DEFAULT_MAX_LIFETIME + Instant.now().getEpochSecond() + smallBuffer;
        return defaultHapiSpec("UpdateFailsWithOverlyLongLifetime")
                .given(cryptoCreate(TARGET_ACCOUNT))
                .when()
                .then(
                        cryptoUpdate(TARGET_ACCOUNT)
                                .expiring(excessiveExpiry)
                                .hasKnownStatus(INVALID_EXPIRATION_TIME));
    }

    private HapiSpec sysAccountKeyUpdateBySpecialWontNeedNewKeyTxnSign() {
        String sysAccount = "0.0.99";
        String randomAccount = "randomAccount";
        String firstKey = "firstKey";
        String secondKey = "secondKey";

        return defaultHapiSpec("sysAccountKeyUpdateBySpecialWontNeedNewKeyTxnSign")
                .given(newKeyNamed(firstKey).shape(SIMPLE), newKeyNamed(secondKey).shape(SIMPLE))
                .when(cryptoCreate(randomAccount).key(firstKey))
                .then(
                        cryptoUpdate(sysAccount)
                                .key(secondKey)
                                .signedBy(GENESIS)
                                .payingWith(GENESIS)
                                .hasKnownStatus(SUCCESS),
                        cryptoUpdate(randomAccount)
                                .key(secondKey)
                                .signedBy(firstKey)
                                .payingWith(GENESIS)
                                .hasPrecheck(INVALID_SIGNATURE));
    }

    private HapiSpec canUpdateMemo() {
        String firstMemo = "First";
        String secondMemo = "Second";
        return defaultHapiSpec("CanUpdateMemo")
                .given(cryptoCreate(TARGET_ACCOUNT).balance(0L).entityMemo(firstMemo))
                .when(
                        cryptoUpdate(TARGET_ACCOUNT)
                                .entityMemo(ZERO_BYTE_MEMO)
                                .hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        cryptoUpdate(TARGET_ACCOUNT).entityMemo(secondMemo))
                .then(
                        getAccountDetails(TARGET_ACCOUNT)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().memo(secondMemo)));
    }

    private HapiSpec updateWithUniqueSigs() {
        return defaultHapiSpec("UpdateWithUniqueSigs")
                .given(
                        newKeyNamed(TARGET_KEY).shape(twoLevelThresh).labels(overlappingKeys),
                        cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY))
                .when()
                .then(
                        cryptoUpdate(TARGET_ACCOUNT)
                                .sigControl(forKey(TARGET_KEY, ENOUGH_UNIQUE_SIGS))
                                .receiverSigRequired(true));
    }

    private HapiSpec updateWithOneEffectiveSig() {
        KeyLabel oneUniqueKey =
                complex(
                        complex("X", "X", "X", "X", "X", "X", "X"),
                        complex("X", "X", "X", "X", "X", "X", "X"));
        SigControl singleSig =
                SigControl.threshSigs(
                        2,
                        SigControl.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, OFF),
                        SigControl.threshSigs(3, OFF, OFF, OFF, ON, OFF, OFF, OFF));

        return defaultHapiSpec("UpdateWithOneEffectiveSig")
                .given(
                        newKeyNamed(REPEATING_KEY).shape(twoLevelThresh).labels(oneUniqueKey),
                        cryptoCreate(TARGET_ACCOUNT).key(REPEATING_KEY).balance(1_000_000_000L))
                .when()
                .then(
                        cryptoUpdate(TARGET_ACCOUNT)
                                .sigControl(forKey(REPEATING_KEY, singleSig))
                                .receiverSigRequired(true)
                                .hasKnownStatus(SUCCESS));
    }

    private HapiSpec updateWithOverlappingSigs() {
        return defaultHapiSpec("UpdateWithOverlappingSigs")
                .given(
                        newKeyNamed(TARGET_KEY).shape(twoLevelThresh).labels(overlappingKeys),
                        cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY))
                .when()
                .then(
                        cryptoUpdate(TARGET_ACCOUNT)
                                .sigControl(forKey(TARGET_KEY, ENOUGH_OVERLAPPING_SIGS))
                                .receiverSigRequired(true)
                                .hasKnownStatus(SUCCESS));
    }

    private HapiSpec updateFailsWithContractKey() {
        return defaultHapiSpec("UpdateFailsWithContractKey")
                .given(cryptoCreate(TARGET_ACCOUNT))
                .when()
                .then(
                        cryptoUpdate(TARGET_ACCOUNT)
                                .usingContractKey()
                                .hasKnownStatus(INVALID_SIGNATURE));
    }

    private HapiSpec updateFailsWithInsufficientSigs() {
        return defaultHapiSpec("UpdateFailsWithInsufficientSigs")
                .given(
                        newKeyNamed(TARGET_KEY).shape(twoLevelThresh).labels(overlappingKeys),
                        cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY))
                .when()
                .then(
                        cryptoUpdate(TARGET_ACCOUNT)
                                .sigControl(forKey(TARGET_KEY, NOT_ENOUGH_UNIQUE_SIGS))
                                .receiverSigRequired(true)
                                .hasKnownStatus(INVALID_SIGNATURE));
    }

    private HapiSpec cannotSetThresholdNegative() {
        return defaultHapiSpec("CannotSetThresholdNegative")
                .given(cryptoCreate(TEST_ACCOUNT))
                .when()
                .then(cryptoUpdate(TEST_ACCOUNT).sendThreshold(-1L));
    }

    private HapiSpec updateFailsIfMissingSigs() {
        SigControl origKeySigs =
                SigControl.threshSigs(3, ON, ON, SigControl.threshSigs(1, OFF, ON));
        SigControl updKeySigs =
                SigControl.listSigs(ON, OFF, SigControl.threshSigs(1, ON, OFF, OFF, OFF));

        return defaultHapiSpec("UpdateFailsIfMissingSigs")
                .given(
                        newKeyNamed(ORIG_KEY).shape(origKeySigs),
                        newKeyNamed(UPD_KEY).shape(updKeySigs))
                .when(
                        cryptoCreate(TEST_ACCOUNT)
                                .receiverSigRequired(true)
                                .key(ORIG_KEY)
                                .sigControl(forKey(ORIG_KEY, origKeySigs)))
                .then(
                        cryptoUpdate(TEST_ACCOUNT)
                                .key(UPD_KEY)
                                .sigControl(
                                        forKey(TEST_ACCOUNT, origKeySigs),
                                        forKey(UPD_KEY, updKeySigs))
                                .hasKnownStatus(INVALID_SIGNATURE));
    }

    private HapiSpec updateWithEmptyKeyFails() {
        SigControl updKeySigs = threshOf(0, 0);

        return defaultHapiSpec("UpdateWithEmptyKey")
                .given(
                        newKeyNamed(ORIG_KEY).shape(KeyShape.SIMPLE),
                        newKeyNamed(UPD_KEY).shape(updKeySigs))
                .when(cryptoCreate(TEST_ACCOUNT).key(ORIG_KEY))
                .then(cryptoUpdate(TEST_ACCOUNT).key(UPD_KEY).hasPrecheck(INVALID_ADMIN_KEY));
    }

    private HapiSpec updateMaxAutoAssociationsWorks() {
        final int maxAllowedAssociations = 5000;
        final int originalMax = 2;
        final int newBadMax = originalMax - 1;
        final int newGoodMax = originalMax + 1;
        final String tokenA = "tokenA";
        final String tokenB = "tokenB";

        final String treasury = "treasury";
        final String tokenACreate = "tokenACreate";
        final String tokenBCreate = "tokenBCreate";
        final String transferAToC = "transferAToC";
        final String transferBToC = "transferBToC";
        final String CONTRACT = "Multipurpose";
        final String ADMIN_KEY = "adminKey";

        return onlyDefaultHapiSpec("updateMaxAutoAssociationsWorks")
                .given(
                        cryptoCreate(treasury).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(ADMIN_KEY),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT)
                                .adminKey(ADMIN_KEY)
                                .maxAutomaticTokenAssociations(originalMax),
                        tokenCreate(tokenA)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(Long.MAX_VALUE)
                                .treasury(treasury)
                                .via(tokenACreate),
                        getTxnRecord(tokenACreate).hasNewTokenAssociation(tokenA, treasury),
                        tokenCreate(tokenB)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(Long.MAX_VALUE)
                                .treasury(treasury)
                                .via(tokenBCreate),
                        getTxnRecord(tokenBCreate).hasNewTokenAssociation(tokenB, treasury),
                        getContractInfo(CONTRACT)
                                .has(
                                        ContractInfoAsserts.contractWith()
                                                .maxAutoAssociations(originalMax)))
                .when(
                        cryptoTransfer(moving(1, tokenA).between(treasury, CONTRACT))
                                .via(transferAToC),
                        getTxnRecord(transferAToC).hasNewTokenAssociation(tokenA, CONTRACT),
                        cryptoTransfer(moving(1, tokenB).between(treasury, CONTRACT))
                                .via(transferBToC),
                        getTxnRecord(transferBToC).hasNewTokenAssociation(tokenB, CONTRACT))
                .then(
                        getContractInfo(CONTRACT)
                                .payingWith(GENESIS)
                                .has(
                                        contractWith()
                                                .hasAlreadyUsedAutomaticAssociations(originalMax)
                                                .maxAutoAssociations(originalMax)),
                        contractUpdate(CONTRACT)
                                .newMaxAutomaticAssociations(newBadMax)
                                .hasKnownStatus(EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT),
                        contractUpdate(CONTRACT).newMaxAutomaticAssociations(newGoodMax),
                        contractUpdate(CONTRACT)
                                .newMaxAutomaticAssociations(maxAllowedAssociations + 1)
                                .hasKnownStatus(
                                        REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
