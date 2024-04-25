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

package com.hedera.services.bdd.suites.token.hip540;

import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_METADATA_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_METADATA_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(TOKEN)
public class Hip540Suite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(Hip540Suite.class);
    private static final String tokenName = "token";
    private static final String adminKey = "adminKey";
    private static final String wipeKey = "wipeKey";
    private static final String kycKey = "kycKey";
    private static final String freezeKey = "freezeKey";
    private static final String pauseKey = "pauseKey";
    private static final String supplyKey = "supplyKey";
    private static final String feeScheduleKey = "feeScheduleKey";
    private static final String metadataKey = "metadataKey";
    private static final String newAdminKey = "newAdminKey";
    private static final String newWipeKey = "newWipeKey";
    private static final String newKycKey = "newKycKey";
    private static final String newFreezeKey = "newFreezeKey";
    private static final String newPauseKey = "newPauseKey";
    private static final String newSupplyKey = "newSupplyKey";
    private static final String newFeeScheduleKey = "newFeeScheduleKey";
    private static final String newMetadataKey = "newMetadataKey";
    private static final String saltedName = salted(tokenName);
    private static final String civilian = "civilian";

    public static void main(String... args) {
        new Hip540Suite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveTests(), negativeTests());
    }

    private List<HapiSpec> positiveTests() {
        return List.of(
                tokenUpdateFreezeKeyNFT(),
                tokenUpdateFreezeKey(),
                tokenUpdateSupplyKey(),
                tokenUpdateFeeScheduleKey(),
                tokenUpdateKycKey(),
                tokenUpdateWipeKey(),
                tokenUpdatePauseKey(),
                tokenUpdateMetadataKey(),
                updateAllKeysToInvalidAdminKeySigns(),
                updateAllKeysToInvalidAllKeysSign(),
                updateAllLowPriorityKeysToInvalidAllOfThemSign(),
                updateAllKeysToValidAdminKeySigns(),
                updateAllKeysToValidAllKeysSign(),
                updateAllLowPriorityKeysToValidAllOfThemSign(),
                validateThatTheAdminKeyCanRemoveOtherKeys(),
                validateThatTheAdminKeyCanRemoveItself());
    }

    private List<HapiSpec> negativeTests() {
        return List.of(
                failUpdateAllKeysOnlyLowPriorityKeysSign(),
                failUpdateAllKeysOneLowPriorityKeyDoesNotSign(),
                failUpdateIfKeyIsInvalidAndWeValidateForKeys(),
                updateFailsIfTokenIsImmutable(),
                updateFailsIfKeyIsMissing(),
                keyRemovalFailsWhenAdminKeyDoesNotSign(),
                failUpdateTokenHasNoAdminKeyInitially(),
                failUpdateAllKeysToValidWhenKeysDoesNotExistInitially(),
                failUpdateAdminKeyAndLowPriorityWhenSomeSignatureIsMissing(),
                failUpdateTokenHasNoAdminKeyInitiallyAndTryToRemoveLowPriorityKey());
    }

    @HapiTest
    public final HapiSpec allScenariosAsExpected() {
        return defaultHapiSpec("allScenariosAsExpected")
                .given()
                .when()
                .then(TestScenarios.SCENARIOS.stream()
                        .map(Hip540TestScenario::asOperation)
                        .toArray(HapiSpecOperation[]::new));
    }

    @HapiTest
    public final HapiSpec tokenUpdateFreezeKeyNFT() {
        final var origHbarFee = 1_234L;
        final var HBAR_COLLECTOR = "hbarFee";
        final var admin = "admin";
        final var freeze = "freeze";
        final var freezeKey2 = "freezeKey2";

        return defaultHapiSpec("tokenUpdateFreezeKeyNFT")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(freezeKey2),
                        cryptoCreate(HBAR_COLLECTOR),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(freeze).key(freezeKey).balance(ONE_MILLION_HBARS),
                        tokenCreate(tokenName)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .adminKey(adminKey)
                                .supplyKey(supplyKey)
                                .freezeKey(freezeKey)
                                .withCustom(fixedHbarFee(origHbarFee, HBAR_COLLECTOR))
                                .payingWith(admin))
                .when(tokenUpdate(tokenName)
                        .freezeKey(freezeKey2)
                        .signedBy(freezeKey)
                        .payingWith(freeze))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasSupplyKey(supplyKey)
                        .hasFreezeKey(freezeKey2));
    }

    @HapiTest
    final HapiSpec tokenUpdateFreezeKey() {
        final var admin = "admin";
        final var freeze = "freeze";
        final var freezeKey2 = "freezeKey2";

        return defaultHapiSpec("tokenUpdateFreezeKey")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(freezeKey2),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(freeze).key(freezeKey).balance(ONE_MILLION_HBARS),
                        tokenCreate(tokenName)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(10)
                                .adminKey(adminKey)
                                .freezeKey(freezeKey)
                                .payingWith(admin))
                .when(tokenUpdate(tokenName)
                        .freezeKey(freezeKey2)
                        .signedBy(freezeKey)
                        .payingWith(freeze))
                .then(getTokenInfo(tokenName).searchKeysGlobally().hasFreezeKey(freezeKey2));
    }

    @HapiTest
    final HapiSpec tokenUpdateSupplyKey() {
        final var supply = "supply";
        final var supplyKey2 = "supplyKey2";

        return defaultHapiSpec("tokenUpdateSupplyKey")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(supplyKey2),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(supply).key(supplyKey).balance(ONE_MILLION_HBARS),
                        tokenCreate(tokenName)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(10)
                                .adminKey(adminKey)
                                .supplyKey(supplyKey)
                                .payingWith(supply))
                .when(tokenUpdate(tokenName)
                        .supplyKey(supplyKey2)
                        .signedBy(supplyKey)
                        .payingWith(supply))
                .then(getTokenInfo(tokenName).searchKeysGlobally().hasSupplyKey(supplyKey2));
    }

    @HapiTest
    final HapiSpec tokenUpdateFeeScheduleKey() {
        final var admin = "admin";
        final var feeSchedule = "feeSchedule";
        final var feeScheduleKey2 = "feeScheduleKey2";

        return defaultHapiSpec("tokenUpdateFeeScheduleKey")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(feeScheduleKey2),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(feeSchedule).key(feeScheduleKey).balance(ONE_MILLION_HBARS),
                        tokenCreate(tokenName)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(10)
                                .adminKey(adminKey)
                                .feeScheduleKey(feeScheduleKey)
                                .payingWith(admin))
                .when(tokenUpdate(tokenName)
                        .feeScheduleKey(feeScheduleKey2)
                        .signedBy(feeScheduleKey)
                        .payingWith(feeSchedule))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasFeeScheduleKey(feeScheduleKey2));
    }

    @HapiTest
    final HapiSpec tokenUpdateKycKey() {
        final var admin = "admin";
        final var kyc = "kyc";
        final var kycKey2 = "kycKey2";

        return defaultHapiSpec("tokenUpdateKycKey")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(kycKey2),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(kyc).key(kycKey).balance(ONE_MILLION_HBARS),
                        tokenCreate(tokenName)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(10)
                                .adminKey(adminKey)
                                .kycKey(kycKey)
                                .payingWith(admin))
                .when(tokenUpdate(tokenName).kycKey(kycKey2).signedBy(kycKey).payingWith(kyc))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasKycKey(kycKey2));
    }

    @HapiTest
    final HapiSpec tokenUpdateWipeKey() {
        final var admin = "admin";
        final var wipe = "wipe";
        final var wipeKey2 = "wipeKey2";

        return defaultHapiSpec("tokenUpdateWipeKey")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(wipeKey2),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(wipe).key(wipeKey).balance(ONE_MILLION_HBARS),
                        tokenCreate(tokenName)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(10)
                                .adminKey(adminKey)
                                .supplyKey(supplyKey)
                                .wipeKey(wipeKey)
                                .payingWith(admin))
                .when(tokenUpdate(tokenName).wipeKey(wipeKey2).signedBy(wipeKey).payingWith(wipe))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasSupplyKey(supplyKey)
                        .hasWipeKey(wipeKey2));
    }

    @HapiTest
    final HapiSpec tokenUpdatePauseKey() {
        final var admin = "admin";
        final var pause = "pause";
        final var pauseKey2 = "pauseKey2";

        return defaultHapiSpec("tokenUpdatePauseKey")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(pauseKey2),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(pause).key(pauseKey).balance(ONE_MILLION_HBARS),
                        tokenCreate(tokenName)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(10)
                                .adminKey(adminKey)
                                .supplyKey(supplyKey)
                                .pauseKey(pauseKey)
                                .payingWith(admin))
                .when(tokenUpdate(tokenName)
                        .pauseKey(pauseKey2)
                        .signedBy(pauseKey)
                        .payingWith(pause))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasSupplyKey(supplyKey)
                        .hasPauseKey(pauseKey2));
    }

    @HapiTest
    final HapiSpec tokenUpdateMetadataKey() {
        final var admin = "admin";
        final var metadata = "metadata";
        final var metadataKey2 = "metadataKey2";

        return defaultHapiSpec("tokenUpdateMetadataKey")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(metadataKey),
                        newKeyNamed(metadataKey2),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(metadata).key(metadataKey).balance(ONE_MILLION_HBARS),
                        tokenCreate(tokenName)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(10)
                                .adminKey(adminKey)
                                .supplyKey(supplyKey)
                                .metadataKey(metadataKey)
                                .payingWith(admin))
                .when(tokenUpdate(tokenName)
                        .metadataKey(metadataKey2)
                        .signedBy(metadataKey)
                        .payingWith(metadata))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasSupplyKey(supplyKey)
                        .hasMetadataKey(metadataKey2));
    }

    @HapiTest
    public HapiSpec updateAllKeysToInvalidAdminKeySigns() {
        return defaultHapiSpec("updateAllKeysToInvalidAdminKeySigns")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        tokenCreate(tokenName)
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey(adminKey)
                                .wipeKey(wipeKey)
                                .kycKey(kycKey)
                                .freezeKey(freezeKey)
                                .pauseKey(pauseKey)
                                .supplyKey(supplyKey)
                                .feeScheduleKey(feeScheduleKey)
                                .metadataKey(metadataKey)
                                .payingWith(civilian))
                .when(tokenUpdate(tokenName)
                        .applyNoValidationToKeys()
                        .usingInvalidAdminKey()
                        .usingInvalidWipeKey()
                        .usingInvalidKycKey()
                        .usingInvalidFreezeKey()
                        .usingInvalidPauseKey()
                        .usingInvalidSupplyKey()
                        .usingInvalidFeeScheduleKey()
                        .usingInvalidMetadataKey()
                        .signedBy(civilian, adminKey)
                        .payingWith(civilian))
                .then(getTokenInfo(tokenName)
                        .hasInvalidAdminKey()
                        .hasInvalidWipeKey()
                        .hasInvalidKycKey()
                        .hasInvalidFreezeKey()
                        .hasInvalidPauseKey()
                        .hasInvalidSupplyKey()
                        .hasInvalidFeeScheduleKey()
                        .hasInvalidMetadataKey()
                        .logged());
    }

    @HapiTest
    public HapiSpec updateAllKeysToInvalidAllKeysSign() {
        return defaultHapiSpec("updateAllKeysToInvalidAllKeysSign")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        tokenCreate(tokenName)
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey(adminKey)
                                .wipeKey(wipeKey)
                                .kycKey(kycKey)
                                .freezeKey(freezeKey)
                                .pauseKey(pauseKey)
                                .supplyKey(supplyKey)
                                .feeScheduleKey(feeScheduleKey)
                                .metadataKey(metadataKey)
                                .payingWith(civilian))
                .when(tokenUpdate(tokenName)
                        .applyNoValidationToKeys()
                        .usingInvalidAdminKey()
                        .usingInvalidWipeKey()
                        .usingInvalidKycKey()
                        .usingInvalidFreezeKey()
                        .usingInvalidPauseKey()
                        .usingInvalidSupplyKey()
                        .usingInvalidFeeScheduleKey()
                        .usingInvalidMetadataKey()
                        .signedBy(
                                civilian,
                                adminKey,
                                wipeKey,
                                kycKey,
                                freezeKey,
                                pauseKey,
                                supplyKey,
                                feeScheduleKey,
                                metadataKey)
                        .payingWith(civilian))
                .then(getTokenInfo(tokenName)
                        .hasInvalidAdminKey()
                        .hasInvalidWipeKey()
                        .hasInvalidKycKey()
                        .hasInvalidFreezeKey()
                        .hasInvalidPauseKey()
                        .hasInvalidSupplyKey()
                        .hasInvalidFeeScheduleKey()
                        .hasInvalidMetadataKey()
                        .logged());
    }

    @HapiTest
    public HapiSpec updateAllLowPriorityKeysToInvalidAllOfThemSign() {
        return defaultHapiSpec("updateAllLowPriorityKeysToInvalidAllOfThemSign")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        tokenCreate(tokenName)
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey(adminKey)
                                .wipeKey(wipeKey)
                                .kycKey(kycKey)
                                .freezeKey(freezeKey)
                                .pauseKey(pauseKey)
                                .supplyKey(supplyKey)
                                .feeScheduleKey(feeScheduleKey)
                                .metadataKey(metadataKey)
                                .payingWith(civilian))
                .when(tokenUpdate(tokenName)
                        .applyNoValidationToKeys()
                        .usingInvalidWipeKey()
                        .usingInvalidKycKey()
                        .usingInvalidFreezeKey()
                        .usingInvalidPauseKey()
                        .usingInvalidSupplyKey()
                        .usingInvalidFeeScheduleKey()
                        .usingInvalidMetadataKey()
                        .signedBy(
                                civilian, wipeKey, kycKey, freezeKey, pauseKey, supplyKey, feeScheduleKey, metadataKey)
                        .payingWith(civilian))
                .then(getTokenInfo(tokenName)
                        .hasInvalidWipeKey()
                        .hasInvalidKycKey()
                        .hasInvalidFreezeKey()
                        .hasInvalidPauseKey()
                        .hasInvalidSupplyKey()
                        .hasInvalidFeeScheduleKey()
                        .hasInvalidMetadataKey()
                        .logged());
    }

    @HapiTest
    public HapiSpec updateAllKeysToValidAdminKeySigns() {
        return defaultHapiSpec("updateAllKeysToValidAdminKeySigns")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        newKeyNamed(newAdminKey),
                        newKeyNamed(newWipeKey),
                        newKeyNamed(newKycKey),
                        newKeyNamed(newFreezeKey),
                        newKeyNamed(newPauseKey),
                        newKeyNamed(newSupplyKey),
                        newKeyNamed(newFeeScheduleKey),
                        newKeyNamed(newMetadataKey),
                        tokenCreate(tokenName)
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey(adminKey)
                                .wipeKey(wipeKey)
                                .kycKey(kycKey)
                                .freezeKey(freezeKey)
                                .pauseKey(pauseKey)
                                .supplyKey(supplyKey)
                                .feeScheduleKey(feeScheduleKey)
                                .metadataKey(metadataKey)
                                .payingWith(civilian))
                .when(tokenUpdate(tokenName)
                        .adminKey(newAdminKey)
                        .wipeKey(newWipeKey)
                        .kycKey(newKycKey)
                        .freezeKey(newFreezeKey)
                        .pauseKey(newPauseKey)
                        .supplyKey(newSupplyKey)
                        .feeScheduleKey(newFeeScheduleKey)
                        .metadataKey(newMetadataKey)
                        .signedBy(civilian, adminKey, newAdminKey)
                        .payingWith(civilian))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(newAdminKey)
                        .hasWipeKey(newWipeKey)
                        .hasKycKey(newKycKey)
                        .hasFreezeKey(newFreezeKey)
                        .hasPauseKey(newPauseKey)
                        .hasSupplyKey(newSupplyKey)
                        .hasFeeScheduleKey(newFeeScheduleKey)
                        .hasMetadataKey(newMetadataKey)
                        .logged());
    }

    @HapiTest
    public HapiSpec failUpdateAllKeysToValidWhenKeysDoesNotExistInitially() {
        return defaultHapiSpec("failUpdateAllKeysToValidWhenKeysDoesNotExistInitially")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        tokenCreate(tokenName)
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey(adminKey)
                                .payingWith(civilian))
                .when(
                        // try to remove non-existent low priority keys
                        tokenUpdate(tokenName)
                                .freezeKey(freezeKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_FREEZE_KEY),
                        tokenUpdate(tokenName)
                                .kycKey(kycKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_KYC_KEY),
                        tokenUpdate(tokenName)
                                .supplyKey(supplyKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_SUPPLY_KEY),
                        tokenUpdate(tokenName)
                                .wipeKey(wipeKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_WIPE_KEY),
                        tokenUpdate(tokenName)
                                .pauseKey(pauseKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_PAUSE_KEY),
                        tokenUpdate(tokenName)
                                .feeScheduleKey(feeScheduleKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_FEE_SCHEDULE_KEY),
                        tokenUpdate(tokenName)
                                .metadataKey(metadataKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_METADATA_KEY))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .logged());
    }

    @HapiTest
    public HapiSpec failUpdateAdminKeyAndLowPriorityWhenSomeSignatureIsMissing() {
        return defaultHapiSpec("failUpdateAdminKeyAndLowPriorityWhenSomeSignatureIsMissing")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        newKeyNamed(newAdminKey),
                        newKeyNamed(newWipeKey),
                        newKeyNamed(newKycKey),
                        newKeyNamed(newFreezeKey),
                        newKeyNamed(newPauseKey),
                        newKeyNamed(newSupplyKey),
                        newKeyNamed(newFeeScheduleKey),
                        newKeyNamed(newMetadataKey),
                        tokenCreate(tokenName)
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey(adminKey)
                                .wipeKey(wipeKey)
                                .kycKey(kycKey)
                                .freezeKey(freezeKey)
                                .pauseKey(pauseKey)
                                .supplyKey(supplyKey)
                                .feeScheduleKey(feeScheduleKey)
                                .metadataKey(metadataKey)
                                .payingWith(civilian))
                .when(
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .wipeKey(newWipeKey)
                                .signedBy(civilian, adminKey, newWipeKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .kycKey(newKycKey)
                                .signedBy(civilian, adminKey, newKycKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .freezeKey(newFreezeKey)
                                .signedBy(civilian, adminKey, newFreezeKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .pauseKey(newPauseKey)
                                .signedBy(civilian, adminKey, newPauseKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .supplyKey(newSupplyKey)
                                .signedBy(civilian, adminKey, newSupplyKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .feeScheduleKey(newFeeScheduleKey)
                                .signedBy(civilian, adminKey, newFeeScheduleKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .metadataKey(newMetadataKey)
                                .signedBy(civilian, adminKey, newMetadataKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .wipeKey(newWipeKey)
                                .signedBy(civilian, adminKey, wipeKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .kycKey(newKycKey)
                                .signedBy(civilian, adminKey, kycKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .freezeKey(newFreezeKey)
                                .signedBy(civilian, adminKey, freezeKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .pauseKey(newPauseKey)
                                .signedBy(civilian, adminKey, pauseKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .supplyKey(newSupplyKey)
                                .signedBy(civilian, adminKey, supplyKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .feeScheduleKey(newFeeScheduleKey)
                                .signedBy(civilian, adminKey, feeScheduleKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .metadataKey(newMetadataKey)
                                .signedBy(civilian, adminKey, metadataKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .wipeKey(newWipeKey)
                                .signedBy(civilian, newAdminKey, newWipeKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .kycKey(newKycKey)
                                .signedBy(civilian, newAdminKey, newKycKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .freezeKey(newFreezeKey)
                                .signedBy(civilian, newAdminKey, newFreezeKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .pauseKey(newPauseKey)
                                .signedBy(civilian, newAdminKey, newPauseKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .supplyKey(newSupplyKey)
                                .signedBy(civilian, newAdminKey, newSupplyKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .feeScheduleKey(newFeeScheduleKey)
                                .signedBy(civilian, newAdminKey, newFeeScheduleKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .metadataKey(newMetadataKey)
                                .signedBy(civilian, newAdminKey, newMetadataKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .wipeKey(newWipeKey)
                                .signedBy(civilian, newAdminKey, wipeKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .kycKey(newKycKey)
                                .signedBy(civilian, newAdminKey, kycKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .freezeKey(newFreezeKey)
                                .signedBy(civilian, newAdminKey, freezeKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .pauseKey(newPauseKey)
                                .signedBy(civilian, newAdminKey, pauseKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .supplyKey(newSupplyKey)
                                .signedBy(civilian, newAdminKey, supplyKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .feeScheduleKey(newFeeScheduleKey)
                                .signedBy(civilian, newAdminKey, feeScheduleKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .adminKey(newAdminKey)
                                .metadataKey(newMetadataKey)
                                .signedBy(civilian, newAdminKey, metadataKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasWipeKey(wipeKey)
                        .hasKycKey(kycKey)
                        .hasFreezeKey(freezeKey)
                        .hasPauseKey(pauseKey)
                        .hasSupplyKey(supplyKey)
                        .hasFeeScheduleKey(feeScheduleKey)
                        .hasMetadataKey(metadataKey)
                        .logged());
    }

    @HapiTest
    public HapiSpec updateAllKeysToValidAllKeysSign() {
        return defaultHapiSpec("updateAllKeysToValidAllKeysSign")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        newKeyNamed(newAdminKey),
                        newKeyNamed(newWipeKey),
                        newKeyNamed(newKycKey),
                        newKeyNamed(newFreezeKey),
                        newKeyNamed(newPauseKey),
                        newKeyNamed(newSupplyKey),
                        newKeyNamed(newFeeScheduleKey),
                        newKeyNamed(newMetadataKey),
                        tokenCreate(tokenName)
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey(adminKey)
                                .wipeKey(wipeKey)
                                .kycKey(kycKey)
                                .freezeKey(freezeKey)
                                .pauseKey(pauseKey)
                                .supplyKey(supplyKey)
                                .feeScheduleKey(feeScheduleKey)
                                .metadataKey(metadataKey)
                                .payingWith(civilian))
                .when(tokenUpdate(tokenName)
                        .adminKey(newAdminKey)
                        .wipeKey(newWipeKey)
                        .kycKey(newKycKey)
                        .freezeKey(newFreezeKey)
                        .pauseKey(newPauseKey)
                        .supplyKey(newSupplyKey)
                        .feeScheduleKey(newFeeScheduleKey)
                        .metadataKey(newMetadataKey)
                        .signedBy(
                                civilian,
                                adminKey,
                                newAdminKey,
                                wipeKey,
                                kycKey,
                                freezeKey,
                                pauseKey,
                                supplyKey,
                                feeScheduleKey,
                                metadataKey)
                        .payingWith(civilian))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(newAdminKey)
                        .hasWipeKey(newWipeKey)
                        .hasKycKey(newKycKey)
                        .hasFreezeKey(newFreezeKey)
                        .hasPauseKey(newPauseKey)
                        .hasSupplyKey(newSupplyKey)
                        .hasFeeScheduleKey(newFeeScheduleKey)
                        .hasMetadataKey(newMetadataKey)
                        .logged());
    }

    @HapiTest
    public HapiSpec updateAllLowPriorityKeysToValidAllOfThemSign() {
        return defaultHapiSpec("updateAllLowPriorityKeysToValidAllOfThemSign")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        newKeyNamed(newAdminKey),
                        newKeyNamed(newWipeKey),
                        newKeyNamed(newKycKey),
                        newKeyNamed(newFreezeKey),
                        newKeyNamed(newPauseKey),
                        newKeyNamed(newSupplyKey),
                        newKeyNamed(newFeeScheduleKey),
                        newKeyNamed(newMetadataKey),
                        tokenCreate(tokenName)
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey(adminKey)
                                .wipeKey(wipeKey)
                                .kycKey(kycKey)
                                .freezeKey(freezeKey)
                                .pauseKey(pauseKey)
                                .supplyKey(supplyKey)
                                .feeScheduleKey(feeScheduleKey)
                                .metadataKey(metadataKey)
                                .payingWith(civilian))
                .when(tokenUpdate(tokenName)
                        .wipeKey(newWipeKey)
                        .kycKey(newKycKey)
                        .freezeKey(newFreezeKey)
                        .pauseKey(newPauseKey)
                        .supplyKey(newSupplyKey)
                        .feeScheduleKey(newFeeScheduleKey)
                        .metadataKey(newMetadataKey)
                        .signedBy(
                                civilian, wipeKey, kycKey, freezeKey, pauseKey, supplyKey, feeScheduleKey, metadataKey)
                        .payingWith(civilian))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasWipeKey(newWipeKey)
                        .hasKycKey(newKycKey)
                        .hasFreezeKey(newFreezeKey)
                        .hasPauseKey(newPauseKey)
                        .hasSupplyKey(newSupplyKey)
                        .hasFeeScheduleKey(newFeeScheduleKey)
                        .hasMetadataKey(newMetadataKey)
                        .logged());
    }

    // here the admin key signature is missing when we try to update low priority keys plus the admin key
    // all low priority key signatures are present, but we should require old admin key signature as well
    @HapiTest
    public HapiSpec failUpdateAllKeysOnlyLowPriorityKeysSign() {
        return defaultHapiSpec("failUpdateAllKeysOnlyLowPriorityKeysSign")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        tokenCreate(tokenName)
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey(adminKey)
                                .wipeKey(wipeKey)
                                .kycKey(kycKey)
                                .freezeKey(freezeKey)
                                .pauseKey(pauseKey)
                                .supplyKey(supplyKey)
                                .feeScheduleKey(feeScheduleKey)
                                .metadataKey(metadataKey)
                                .payingWith(civilian))
                .when(tokenUpdate(tokenName)
                        .applyNoValidationToKeys()
                        .usingInvalidAdminKey()
                        .usingInvalidWipeKey()
                        .usingInvalidKycKey()
                        .usingInvalidFreezeKey()
                        .usingInvalidPauseKey()
                        .usingInvalidSupplyKey()
                        .usingInvalidFeeScheduleKey()
                        .usingInvalidMetadataKey()
                        .signedBy(
                                civilian, wipeKey, kycKey, freezeKey, pauseKey, supplyKey, feeScheduleKey, metadataKey)
                        .payingWith(civilian)
                        .hasKnownStatus(INVALID_SIGNATURE))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasWipeKey(wipeKey)
                        .hasKycKey(kycKey)
                        .hasFreezeKey(freezeKey)
                        .hasPauseKey(pauseKey)
                        .hasSupplyKey(supplyKey)
                        .hasFeeScheduleKey(feeScheduleKey)
                        .hasMetadataKey(metadataKey)
                        .logged());
    }

    @HapiTest
    public HapiSpec failUpdateTokenHasNoAdminKeyInitially() {
        final var newKycKey = "newKycKey";
        return defaultHapiSpec("failUpdateTokenHasNoAdminKeyInitially")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(newKycKey),
                        tokenCreate(tokenName)
                                .name(saltedName)
                                .initialSupply(500)
                                .kycKey(kycKey)
                                .freezeKey(freezeKey)
                                .payingWith(civilian))
                .when(
                        tokenUpdate(tokenName)
                                .adminKey(adminKey)
                                .kycKey(newKycKey)
                                .signedBy(civilian, adminKey, kycKey)
                                .payingWith(civilian)
                                // token has no admin key on init and we fail
                                // with `TOKEN_HAS_NO_ADMIN_KEY` if we want to update a non-existent admin key
                                .hasKnownStatus(TOKEN_HAS_NO_ADMIN_KEY),
                        tokenUpdate(tokenName)
                                .adminKey(adminKey)
                                .kycKey(newKycKey)
                                .properlyEmptyingFreezeKey()
                                .signedBy(civilian, kycKey, freezeKey)
                                .payingWith(civilian)
                                // token has no admin key on init and an admin key
                                // has to always sign the removal of any key
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .kycKey(newKycKey)
                                .properlyEmptyingFreezeKey()
                                .signedBy(civilian, kycKey, freezeKey)
                                .payingWith(civilian)
                                // token has no admin key on init and an admin key
                                // has to always sign the removal of any key
                                .hasKnownStatus(INVALID_SIGNATURE))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasKycKey(kycKey)
                        .hasFreezeKey(freezeKey)
                        .logged());
    }

    @HapiTest
    public HapiSpec failUpdateTokenHasNoAdminKeyInitiallyAndTryToRemoveLowPriorityKey() {
        return defaultHapiSpec("failUpdateTokenHasNoAdminKeyInitiallyAndTryToRemoveLowPriorityKey")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(wipeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        tokenCreate(tokenName)
                                .name(saltedName)
                                .initialSupply(500)
                                .wipeKey(wipeKey)
                                .kycKey(kycKey)
                                .freezeKey(freezeKey)
                                .pauseKey(pauseKey)
                                .supplyKey(supplyKey)
                                .feeScheduleKey(feeScheduleKey)
                                .metadataKey(metadataKey)
                                .payingWith(civilian))
                .when(
                        tokenUpdate(tokenName)
                                .properlyEmptyingWipeKey()
                                .wipeKey(wipeKey)
                                // for key removal here we require the signature of the admin key
                                .signedBy(civilian, wipeKey)
                                .hasPrecheck(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingKycKey()
                                .kycKey(kycKey)
                                // for key removal here we require the signature of the admin key
                                .signedBy(civilian, kycKey)
                                .hasPrecheck(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingFreezeKey()
                                .freezeKey(freezeKey)
                                // for key removal here we require the signature of the admin key
                                .signedBy(civilian, freezeKey)
                                .hasPrecheck(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingPauseKey()
                                .pauseKey(pauseKey)
                                // for key removal here we require the signature of the admin key
                                .signedBy(civilian, pauseKey)
                                .hasPrecheck(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingSupplyKey()
                                .supplyKey(supplyKey)
                                // for key removal here we require the signature of the admin key
                                .signedBy(civilian, supplyKey)
                                .hasPrecheck(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingFeeScheduleKey()
                                .feeScheduleKey(feeScheduleKey)
                                // for key removal here we require the signature of the admin key
                                .signedBy(civilian, feeScheduleKey)
                                .hasPrecheck(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingMetadataKey()
                                .metadataKey(metadataKey)
                                // for key removal here we require the signature of the admin key
                                .signedBy(civilian, metadataKey)
                                .hasPrecheck(INVALID_SIGNATURE))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasWipeKey(wipeKey)
                        .hasKycKey(kycKey)
                        .hasFreezeKey(freezeKey)
                        .hasPauseKey(pauseKey)
                        .hasSupplyKey(supplyKey)
                        .hasFeeScheduleKey(feeScheduleKey)
                        .hasMetadataKey(metadataKey)
                        .logged());
    }

    // we try to update all low priority keys but the supply key signature is missing
    @HapiTest
    public HapiSpec failUpdateAllKeysOneLowPriorityKeyDoesNotSign() {
        return defaultHapiSpec("failUpdateAllKeysOneLowPriorityKeyDoesNotSign")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        tokenCreate(tokenName)
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey(adminKey)
                                .wipeKey(wipeKey)
                                .kycKey(kycKey)
                                .freezeKey(freezeKey)
                                .pauseKey(pauseKey)
                                .supplyKey(supplyKey)
                                .feeScheduleKey(feeScheduleKey)
                                .metadataKey(metadataKey)
                                .payingWith(civilian))
                .when(tokenUpdate(tokenName)
                        .applyNoValidationToKeys()
                        .usingInvalidWipeKey()
                        .usingInvalidKycKey()
                        .usingInvalidFreezeKey()
                        .usingInvalidPauseKey()
                        .usingInvalidSupplyKey()
                        .usingInvalidFeeScheduleKey()
                        .usingInvalidMetadataKey()
                        // supplyKey does not sign the update
                        .signedBy(civilian, wipeKey, kycKey, freezeKey, pauseKey, feeScheduleKey, metadataKey)
                        .payingWith(civilian)
                        .hasKnownStatus(INVALID_SIGNATURE))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasWipeKey(wipeKey)
                        .hasKycKey(kycKey)
                        .hasFreezeKey(freezeKey)
                        .hasPauseKey(pauseKey)
                        .hasSupplyKey(supplyKey)
                        .hasFeeScheduleKey(feeScheduleKey)
                        .hasMetadataKey(metadataKey)
                        .logged());
    }

    @HapiTest
    public HapiSpec failUpdateIfKeyIsInvalidAndWeValidateForKeys() {
        return defaultHapiSpec("failUpdateIfKeyIsInvalidAndWeValidateForKeys")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        tokenCreate(tokenName)
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey(adminKey)
                                .wipeKey(wipeKey)
                                .kycKey(kycKey)
                                .freezeKey(freezeKey)
                                .pauseKey(pauseKey)
                                .supplyKey(supplyKey)
                                .feeScheduleKey(feeScheduleKey)
                                .metadataKey(metadataKey)
                                .payingWith(civilian))
                .when(
                        tokenUpdate(tokenName)
                                .usingInvalidAdminKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_ADMIN_KEY),
                        tokenUpdate(tokenName)
                                .usingInvalidWipeKey()
                                .signedBy(civilian, wipeKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_WIPE_KEY),
                        tokenUpdate(tokenName)
                                .usingInvalidKycKey()
                                .signedBy(civilian, kycKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_KYC_KEY),
                        tokenUpdate(tokenName)
                                .usingInvalidFreezeKey()
                                .signedBy(civilian, freezeKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_FREEZE_KEY),
                        tokenUpdate(tokenName)
                                .usingInvalidPauseKey()
                                .signedBy(civilian, pauseKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_PAUSE_KEY),
                        tokenUpdate(tokenName)
                                .usingInvalidSupplyKey()
                                .signedBy(civilian, supplyKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SUPPLY_KEY),
                        tokenUpdate(tokenName)
                                .usingInvalidFeeScheduleKey()
                                .signedBy(civilian, feeScheduleKey)
                                .payingWith(civilian)
                                .hasPrecheck(INVALID_CUSTOM_FEE_SCHEDULE_KEY),
                        tokenUpdate(tokenName)
                                .usingInvalidMetadataKey()
                                .signedBy(civilian, metadataKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_METADATA_KEY))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasWipeKey(wipeKey)
                        .hasKycKey(kycKey)
                        .hasFreezeKey(freezeKey)
                        .hasPauseKey(pauseKey)
                        .hasSupplyKey(supplyKey)
                        .hasFeeScheduleKey(feeScheduleKey)
                        .hasMetadataKey(metadataKey)
                        .logged());
    }

    @HapiTest
    public HapiSpec validateThatTheAdminKeyCanRemoveOtherKeys() {
        final var tokenName2 = "tokenName2";
        return defaultHapiSpec("validateThatTheAdminKeyCanRemoveOtherKeys")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        tokenCreate(tokenName)
                                .adminKey(adminKey)
                                .freezeKey(freezeKey)
                                .kycKey(kycKey)
                                .supplyKey(supplyKey)
                                .wipeKey(wipeKey)
                                .pauseKey(pauseKey)
                                .feeScheduleKey(feeScheduleKey)
                                .metadataKey(metadataKey)
                                .payingWith(civilian),
                        tokenCreate(tokenName2)
                                .adminKey(adminKey)
                                .freezeKey(freezeKey)
                                .kycKey(kycKey)
                                .supplyKey(supplyKey)
                                .wipeKey(wipeKey)
                                .pauseKey(pauseKey)
                                .feeScheduleKey(feeScheduleKey)
                                .metadataKey(metadataKey)
                                .payingWith(civilian))
                .when(
                        tokenUpdate(tokenName)
                                .properlyEmptyingWipeKey()
                                .properlyEmptyingKycKey()
                                .properlyEmptyingFreezeKey()
                                .properlyEmptyingPauseKey()
                                .properlyEmptyingSupplyKey()
                                .properlyEmptyingFeeScheduleKey()
                                .properlyEmptyingMetadataKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian),
                        tokenUpdate(tokenName2)
                                .properlyEmptyingWipeKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian),
                        tokenUpdate(tokenName2)
                                .properlyEmptyingKycKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian),
                        tokenUpdate(tokenName2)
                                .properlyEmptyingFreezeKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian),
                        tokenUpdate(tokenName2)
                                .properlyEmptyingPauseKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian),
                        tokenUpdate(tokenName2)
                                .properlyEmptyingSupplyKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian),
                        tokenUpdate(tokenName2)
                                .properlyEmptyingFeeScheduleKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian),
                        tokenUpdate(tokenName2)
                                .properlyEmptyingMetadataKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian))
                .then(
                        getTokenInfo(tokenName)
                                .logged()
                                .hasEmptyFreezeKey()
                                .hasEmptyKycKey()
                                .hasEmptySupplyKey()
                                .hasEmptyWipeKey()
                                .hasEmptyPauseKey()
                                .hasEmptyFeeScheduleKey()
                                .hasEmptyMetadataKey(),
                        getTokenInfo(tokenName2)
                                .logged()
                                .hasEmptyFreezeKey()
                                .hasEmptyKycKey()
                                .hasEmptySupplyKey()
                                .hasEmptyWipeKey()
                                .hasEmptyPauseKey()
                                .hasEmptyFeeScheduleKey()
                                .hasEmptyMetadataKey());
    }

    @HapiTest
    public HapiSpec validateThatTheAdminKeyCanRemoveItself() {
        return defaultHapiSpec("validateThatTheAdminKeyCanRemoveItself")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        tokenCreate(tokenName)
                                .adminKey(adminKey)
                                .wipeKey(wipeKey)
                                .payingWith(civilian))
                .when(
                        tokenUpdate(tokenName)
                                .properlyEmptyingAdminKey()
                                .signedBy(civilian, wipeKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingAdminKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian))
                .then(getTokenInfo(tokenName).logged().hasEmptyAdminKey());
    }

    @HapiTest
    public HapiSpec keyRemovalRequiresAdminSignatureEveryTime() {
        final var newWipeKey = "newWipeKey";
        return defaultHapiSpec("keyRemovalRequiresAdminSignatureEveryTime")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(newWipeKey),
                        newKeyNamed(kycKey),
                        tokenCreate(tokenName)
                                .adminKey(adminKey)
                                .wipeKey(wipeKey)
                                .kycKey(kycKey)
                                .payingWith(civilian))
                .when(
                        tokenUpdate(tokenName)
                                .properlyEmptyingKycKey()
                                .wipeKey(newWipeKey)
                                .signedBy(civilian, wipeKey)
                                .payingWith(civilian)
                                // wipe key fails to update itself because the TokenUpdateOp
                                // contains key removal as well -> admin key needs to sign as well
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingKycKey()
                                .signedBy(civilian, kycKey)
                                .payingWith(civilian)
                                // kyc key fails to update itself because the TokenUpdateOp
                                // contains key removal as well -> admin key needs to sign as well
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingKycKey()
                                .wipeKey(newWipeKey)
                                .signedBy(civilian, adminKey, wipeKey)
                                .payingWith(civilian))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasEmptyKycKey()
                        .hasWipeKey(newWipeKey)
                        .logged());
    }

    @HapiTest
    public HapiSpec tokenUpdateRequiresAdminKeySignatureIfOtherFieldsAreUpdated() {
        final var tokenSymbol = "CREATE";
        final var newTokenSymbol = "UPDATE";
        final var newWipeKey = "newWipeKey";
        return defaultHapiSpec("tokenUpdateRequiresAdminKeySignatureIfOtherFieldsAreUpdated")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(newWipeKey),
                        newKeyNamed(kycKey),
                        tokenCreate(tokenName)
                                .symbol(tokenSymbol)
                                .adminKey(adminKey)
                                .wipeKey(wipeKey)
                                .kycKey(kycKey)
                                .payingWith(civilian))
                .when(
                        tokenUpdate(tokenName)
                                .symbol(newTokenSymbol)
                                .wipeKey(newWipeKey)
                                .signedBy(civilian, wipeKey)
                                .payingWith(civilian)
                                // wipe key fails to update itself because TokenUpdate contains
                                // other field update -> requires admin key to sign as well
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .symbol(newTokenSymbol)
                                .wipeKey(newWipeKey)
                                .signedBy(civilian, adminKey, wipeKey)
                                .payingWith(civilian))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasSymbol(newTokenSymbol)
                        .hasWipeKey(newWipeKey)
                        .logged());
    }

    @HapiTest
    public HapiSpec keyRemovalFailsWhenAdminKeyDoesNotSign() {
        return defaultHapiSpec("keyRemovalFailsWhenAdminKeyDoesNotSign")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        tokenCreate(tokenName)
                                .adminKey(adminKey)
                                .wipeKey(wipeKey)
                                .kycKey(kycKey)
                                .freezeKey(freezeKey)
                                .pauseKey(pauseKey)
                                .supplyKey(supplyKey)
                                .feeScheduleKey(feeScheduleKey)
                                .metadataKey(metadataKey)
                                .payingWith(civilian))
                .when(
                        tokenUpdate(tokenName)
                                .properlyEmptyingAdminKey()
                                .signedBy(civilian)
                                .payingWith(civilian)
                                // because admin key does not sign
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingWipeKey()
                                .signedBy(civilian, wipeKey)
                                .payingWith(civilian)
                                // because admin key does not sign
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingKycKey()
                                .signedBy(civilian, kycKey)
                                .payingWith(civilian)
                                // because admin key does not sign
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingFreezeKey()
                                .signedBy(civilian, freezeKey)
                                .payingWith(civilian)
                                // because admin key does not sign
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingPauseKey()
                                .signedBy(civilian, pauseKey)
                                .payingWith(civilian)
                                // because admin key does not sign
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingSupplyKey()
                                .signedBy(civilian, supplyKey)
                                .payingWith(civilian)
                                // because admin key does not sign
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingFeeScheduleKey()
                                .signedBy(civilian, feeScheduleKey)
                                .payingWith(civilian)
                                // because admin key does not sign
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingMetadataKey()
                                .signedBy(civilian, metadataKey)
                                .payingWith(civilian)
                                // because admin key does not sign
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingWipeKey()
                                .properlyEmptyingKycKey()
                                .properlyEmptyingFreezeKey()
                                .properlyEmptyingPauseKey()
                                .properlyEmptyingSupplyKey()
                                .properlyEmptyingFeeScheduleKey()
                                .properlyEmptyingMetadataKey()
                                .signedBy(
                                        civilian,
                                        wipeKey,
                                        kycKey,
                                        freezeKey,
                                        pauseKey,
                                        supplyKey,
                                        feeScheduleKey,
                                        metadataKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenUpdate(tokenName)
                                .properlyEmptyingWipeKey()
                                .properlyEmptyingKycKey()
                                .properlyEmptyingFreezeKey()
                                .properlyEmptyingPauseKey()
                                .properlyEmptyingSupplyKey()
                                .properlyEmptyingFeeScheduleKey()
                                .properlyEmptyingMetadataKey()
                                .signedBy(
                                        civilian,
                                        adminKey,
                                        wipeKey,
                                        kycKey,
                                        freezeKey,
                                        pauseKey,
                                        supplyKey,
                                        feeScheduleKey,
                                        metadataKey)
                                .payingWith(civilian))
                .then();
    }

    @HapiTest
    public HapiSpec updateFailsIfTokenIsImmutable() {
        final var autoRenewAccount = "autoRenewAccount";
        final var newAutoRenewAccount = "newAutoRenewAccount";
        final var firstPeriod = THREE_MONTHS_IN_SECONDS;
        final var secondPeriod = THREE_MONTHS_IN_SECONDS + 1234;
        final var tokenSymbol = "TOKEN";
        final var newTokenSymbol = "NEWTOKEN";
        final var tokenName = "name";
        final var newTokenName = "newName";
        final var memo = "memo";
        final var newMemo = "newMemo";
        return defaultHapiSpec("updateFailsIfTokenIsImmutable")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(autoRenewAccount),
                        cryptoCreate(newAutoRenewAccount),
                        newKeyNamed(adminKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(metadataKey),
                        tokenCreate(tokenName)
                                .name(tokenName)
                                .symbol(tokenSymbol)
                                .memo(memo)
                                .autoRenewAccount(autoRenewAccount)
                                .autoRenewPeriod(firstPeriod)
                                .payingWith(civilian))
                .when()
                .then(
                        tokenUpdate(tokenName)
                                .adminKey(adminKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_IS_IMMUTABLE),
                        tokenUpdate(tokenName)
                                .wipeKey(wipeKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_IS_IMMUTABLE),
                        tokenUpdate(tokenName)
                                .kycKey(kycKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_IS_IMMUTABLE),
                        tokenUpdate(tokenName)
                                .freezeKey(freezeKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_IS_IMMUTABLE),
                        tokenUpdate(tokenName)
                                .pauseKey(pauseKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_IS_IMMUTABLE),
                        tokenUpdate(tokenName)
                                .supplyKey(supplyKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_IS_IMMUTABLE),
                        tokenUpdate(tokenName)
                                .feeScheduleKey(feeScheduleKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_IS_IMMUTABLE),
                        tokenUpdate(tokenName)
                                .metadataKey(metadataKey)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_IS_IMMUTABLE),
                        tokenUpdate(tokenName)
                                .symbol(newTokenSymbol)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_IS_IMMUTABLE),
                        tokenUpdate(tokenName)
                                .name(newTokenName)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_IS_IMMUTABLE),
                        tokenUpdate(tokenName)
                                .memo(newMemo)
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_IS_IMMUTABLE),
                        tokenUpdate(tokenName)
                                .autoRenewAccount(newAutoRenewAccount)
                                .autoRenewPeriod(secondPeriod)
                                .signedBy(civilian, adminKey, newAutoRenewAccount)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_IS_IMMUTABLE));
    }

    @HapiTest
    public HapiSpec updateFailsIfKeyIsMissing() {
        final var tokenName2 = "tokenName2";
        return defaultHapiSpec("updateFailsIfKeyIsMissing")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(adminKey),
                        newKeyNamed(kycKey),
                        tokenCreate(tokenName2)
                                .adminKey(adminKey)
                                .kycKey(kycKey)
                                .payingWith(civilian),
                        tokenCreate(tokenName).adminKey(adminKey).payingWith(civilian))
                .when()
                .then(
                        tokenUpdate(tokenName)
                                .properlyEmptyingFreezeKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_FREEZE_KEY),
                        tokenUpdate(tokenName)
                                .properlyEmptyingKycKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_KYC_KEY),
                        tokenUpdate(tokenName)
                                .properlyEmptyingSupplyKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_SUPPLY_KEY),
                        tokenUpdate(tokenName)
                                .properlyEmptyingWipeKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_WIPE_KEY),
                        tokenUpdate(tokenName)
                                .properlyEmptyingPauseKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_PAUSE_KEY),
                        tokenUpdate(tokenName)
                                .properlyEmptyingFeeScheduleKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_FEE_SCHEDULE_KEY),
                        tokenUpdate(tokenName)
                                .properlyEmptyingMetadataKey()
                                .signedBy(civilian, adminKey)
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_METADATA_KEY),
                        tokenUpdate(tokenName2)
                                .properlyEmptyingFreezeKey()
                                .signedBy(civilian, kycKey)
                                .payingWith(civilian)
                                .hasKnownStatus(INVALID_SIGNATURE));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
