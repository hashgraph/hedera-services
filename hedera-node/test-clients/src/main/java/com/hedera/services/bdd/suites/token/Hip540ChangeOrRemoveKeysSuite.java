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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(TOKEN)
public class Hip540ChangeOrRemoveKeysSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(Hip540ChangeOrRemoveKeysSuite.class);

    public static void main(String... args) {
        new Hip540ChangeOrRemoveKeysSuite().runSuiteSync();
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
                updateAllKeysToInvalidAdminKeySigns(),
                updateAllKeysToInvalidAllKeysSign(),
                updateAllLowPriorityKeysToInvalidAllOfThemSign());
    }

    private List<HapiSpec> negativeTests() {
        return List.of(
                failUpdateAllKeysOnlyLowPriorityKeysSign(),
                failUpdateAllKeysOneLowPriorityKeyDoesNotSign(),
                failUpdateIfKeyIsInvalidAndWeValidateForKeys());
    }

    @HapiTest
    public final HapiSpec tokenUpdateFreezeKeyNFT() {
        final var origHbarFee = 1_234L;

        final var tokenName = "token";
        final var HBAR_COLLECTOR = "hbarFee";

        final var admin = "admin";
        final var adminKey = "adminKey";
        final var freeze = "freeze";
        final var freeze2 = "freeze2";
        final var freezeKey = "freezeKey";
        final var supplyKey = "supplyKey";
        final var freezeKey2 = "freezeKey2";
        final var feeScheduleKey = "feeSchedule";
        final var newFeeScheduleKey = "feeScheduleRedux";

        return defaultHapiSpec("tokenUpdateFreezeKeyNFT")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(freezeKey2),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(newFeeScheduleKey),
                        cryptoCreate(HBAR_COLLECTOR),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(freeze).key(freezeKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(freeze2).key(freezeKey2).balance(ONE_MILLION_HBARS),
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
                        .signedBy(freezeKey, freezeKey2)
                        .payingWith(freeze))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasSupplyKey(supplyKey)
                        .hasFreezeKey(freezeKey2));
    }

    @HapiTest
    final HapiSpec tokenUpdateFreezeKey() {
        final var tokenName = "token";
        final var HBAR_COLLECTOR = "hbarFee";

        final var admin = "admin";
        final var adminKey = "adminKey";
        final var freeze = "freeze";
        final var freeze2 = "freeze2";
        final var freezeKey = "freezeKey";
        final var supplyKey = "supplyKey";
        final var freezeKey2 = "freezeKey2";
        final var feeScheduleKey = "feeSchedule";
        final var newFeeScheduleKey = "feeScheduleRedux";

        return defaultHapiSpec("tokenUpdateFreezeKey")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(freezeKey2),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(newFeeScheduleKey),
                        cryptoCreate(HBAR_COLLECTOR),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(freeze).key(freezeKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(freeze2).key(freezeKey2).balance(ONE_MILLION_HBARS),
                        tokenCreate(tokenName)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(10)
                                .adminKey(adminKey)
                                .supplyKey(supplyKey)
                                .freezeKey(freezeKey)
                                .payingWith(admin))
                .when(tokenUpdate(tokenName)
                        .freezeKey(freezeKey2)
                        .signedBy(freezeKey2, freezeKey)
                        .payingWith(freeze))
                .then(getTokenInfo(tokenName).searchKeysGlobally().hasFreezeKey(freezeKey2));
    }

    @HapiTest
    final HapiSpec tokenUpdateSupplyKey() {
        final var tokenName = "token";
        final var HBAR_COLLECTOR = "hbarFee";

        final var adminKey = "adminKey";
        final var supply2 = "supply2";
        final var freezeKey = "freezeKey";
        final var supplyKey = "supplyKey";
        final var supply = "supply";
        final var supplyKey2 = "supplyKey2";
        final var feeScheduleKey = "feeSchedule";
        final var newFeeScheduleKey = "feeScheduleRedux";

        return defaultHapiSpec("tokenUpdateFreezeKey")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(supplyKey2),
                        newKeyNamed(supplyKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(newFeeScheduleKey),
                        cryptoCreate(HBAR_COLLECTOR),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(supply).key(supplyKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(supply2).key(supplyKey2).balance(ONE_MILLION_HBARS),
                        tokenCreate(tokenName)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(10)
                                .adminKey(adminKey)
                                .supplyKey(supplyKey)
                                .freezeKey(freezeKey)
                                .payingWith(supply))
                .when(tokenUpdate(tokenName)
                        .supplyKey(supplyKey2)
                        .signedBy(supplyKey2, supplyKey)
                        .payingWith(supply))
                .then(getTokenInfo(tokenName).searchKeysGlobally().hasSupplyKey(supplyKey2));
    }

    @HapiTest
    final HapiSpec tokenUpdateFeeScheduleKey() {
        final var tokenName = "token";
        final var HBAR_COLLECTOR = "hbarFee";

        final var admin = "admin";
        final var adminKey = "adminKey";
        final var feeSchedule = "feeSchedule";
        final var feeSchedule2 = "feeSchedule2";
        final var feeScheduleKey = "feeScheduleKey";
        final var feeScheduleKey2 = "feeScheduleKey2";
        final var freezeKey = "freezeKey";
        final var supplyKey = "supplyKey";
        final var newFeeScheduleKey = "feeScheduleRedux";

        return defaultHapiSpec("tokenUpdateFreezeKey")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(feeScheduleKey),
                        newKeyNamed(feeScheduleKey2),
                        newKeyNamed(newFeeScheduleKey),
                        cryptoCreate(HBAR_COLLECTOR),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(feeSchedule).key(feeScheduleKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(feeSchedule2).key(feeScheduleKey2).balance(ONE_MILLION_HBARS),
                        tokenCreate(tokenName)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(10)
                                .adminKey(adminKey)
                                .supplyKey(supplyKey)
                                .feeScheduleKey(feeScheduleKey)
                                .payingWith(admin))
                .when(tokenUpdate(tokenName)
                        .feeScheduleKey(feeScheduleKey2)
                        .signedBy(feeScheduleKey2, feeScheduleKey)
                        .payingWith(feeSchedule))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasSupplyKey(supplyKey)
                        .hasFeeScheduleKey(feeScheduleKey2));
    }

    @HapiTest
    final HapiSpec tokenUpdateKycKey() {
        final var tokenName = "token";
        final var HBAR_COLLECTOR = "hbarFee";

        final var admin = "admin";
        final var adminKey = "adminKey";
        final var kyc = "kyc";
        final var kyc2 = "kyc2";
        final var kycKey = "kycKey";
        final var kycKey2 = "kycKey2";
        final var freezeKey = "freezeKey";
        final var supplyKey = "supplyKey";
        final var newFeeScheduleKey = "feeScheduleRedux";

        return defaultHapiSpec("tokenUpdateFreezeKey")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(kycKey2),
                        newKeyNamed(newFeeScheduleKey),
                        cryptoCreate(HBAR_COLLECTOR),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(admin).key(adminKey).balance(ONE_MILLION_HBARS),
                        cryptoCreate(kyc).key(kycKey).balance(ONE_MILLION_HBARS),
                        tokenCreate(tokenName)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(10)
                                .adminKey(adminKey)
                                .supplyKey(supplyKey)
                                .kycKey(kycKey)
                                .payingWith(admin))
                .when(tokenUpdate(tokenName)
                        .kycKey(kycKey2)
                        .signedBy(kycKey2, kycKey)
                        .payingWith(kyc))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasSupplyKey(supplyKey)
                        .hasKycKey(kycKey2));
    }

    @HapiTest
    final HapiSpec tokenUpdateWipeKey() {
        final var tokenName = "token";
        final var HBAR_COLLECTOR = "hbarFee";

        final var admin = "admin";
        final var adminKey = "adminKey";
        final var wipe = "wipe";
        final var wipeKey = "wipeKey";
        final var wipeKey2 = "wipeKey2";
        final var supplyKey = "supplyKey";
        final var newFeeScheduleKey = "feeScheduleRedux";

        return defaultHapiSpec("tokenUpdateFreezeKey")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(wipeKey),
                        newKeyNamed(wipeKey2),
                        newKeyNamed(newFeeScheduleKey),
                        cryptoCreate(HBAR_COLLECTOR),
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
                .when(tokenUpdate(tokenName)
                        .wipeKey(wipeKey2)
                        .signedBy(wipeKey2, wipeKey)
                        .payingWith(wipe))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasSupplyKey(supplyKey)
                        .hasWipeKey(wipeKey2));
    }

    @HapiTest
    final HapiSpec tokenUpdatePauseKey() {
        final var tokenName = "token";
        final var HBAR_COLLECTOR = "hbarFee";

        final var admin = "admin";
        final var adminKey = "adminKey";
        final var pause = "pause";
        final var pauseKey = "pauseKey";
        final var pauseKey2 = "pauseKey2";
        final var supplyKey = "supplyKey";
        final var newFeeScheduleKey = "feeScheduleRedux";

        return defaultHapiSpec("tokenUpdateFreezeKey")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(supplyKey),
                        newKeyNamed(pauseKey),
                        newKeyNamed(pauseKey2),
                        newKeyNamed(newFeeScheduleKey),
                        cryptoCreate(HBAR_COLLECTOR),
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
                        .signedBy(pauseKey2, pauseKey)
                        .payingWith(pause))
                .then(getTokenInfo(tokenName)
                        .searchKeysGlobally()
                        .hasAdminKey(adminKey)
                        .hasSupplyKey(supplyKey)
                        .hasPauseKey(pauseKey2));
    }

    @HapiTest
    public HapiSpec updateAllKeysToInvalidAdminKeySigns() {
        String saltedName = salted("primary");
        final var civilian = "civilian";
        return defaultHapiSpec("updateAllKeysToInvalidAdminKeySigns")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed("adminKey"),
                        newKeyNamed("wipeKey"),
                        newKeyNamed("kycKey"),
                        newKeyNamed("freezeKey"),
                        newKeyNamed("pauseKey"),
                        newKeyNamed("supplyKey"),
                        newKeyNamed("feeScheduleKey"),
                        tokenCreate("primary")
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey("adminKey")
                                .wipeKey("wipeKey")
                                .kycKey("kycKey")
                                .freezeKey("freezeKey")
                                .pauseKey("pauseKey")
                                .supplyKey("supplyKey")
                                .feeScheduleKey("feeScheduleKey")
                                .payingWith(civilian))
                .when(tokenUpdate("primary")
                        .applyNoValidationToKeys()
                        .usingInvalidAdminKey()
                        .usingInvalidWipeKey()
                        .usingInvalidKycKey()
                        .usingInvalidFreezeKey()
                        .usingInvalidPauseKey()
                        .usingInvalidSupplyKey()
                        .usingInvalidFeeScheduleKey()
                        .signedBy(civilian, "adminKey")
                        .payingWith(civilian))
                .then(getTokenInfo("primary")
                        .hasInvalidAdminKey()
                        .hasInvalidWipeKey()
                        .hasInvalidKycKey()
                        .hasInvalidFreezeKey()
                        .hasInvalidPauseKey()
                        .hasInvalidSupplyKey()
                        .hasInvalidFeeScheduleKey()
                        .logged());
    }

    @HapiTest
    public HapiSpec updateAllKeysToInvalidAllKeysSign() {
        String saltedName = salted("primary");
        final var civilian = "civilian";
        return defaultHapiSpec("updateAllKeysToInvalidAllKeysSign")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed("adminKey"),
                        newKeyNamed("wipeKey"),
                        newKeyNamed("kycKey"),
                        newKeyNamed("freezeKey"),
                        newKeyNamed("pauseKey"),
                        newKeyNamed("supplyKey"),
                        newKeyNamed("feeScheduleKey"),
                        tokenCreate("primary")
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey("adminKey")
                                .wipeKey("wipeKey")
                                .kycKey("kycKey")
                                .freezeKey("freezeKey")
                                .pauseKey("pauseKey")
                                .supplyKey("supplyKey")
                                .feeScheduleKey("feeScheduleKey")
                                .payingWith(civilian))
                .when(tokenUpdate("primary")
                        .applyNoValidationToKeys()
                        .usingInvalidAdminKey()
                        .usingInvalidWipeKey()
                        .usingInvalidKycKey()
                        .usingInvalidFreezeKey()
                        .usingInvalidPauseKey()
                        .usingInvalidSupplyKey()
                        .usingInvalidFeeScheduleKey()
                        .signedBy(
                                civilian,
                                "adminKey",
                                "wipeKey",
                                "kycKey",
                                "freezeKey",
                                "pauseKey",
                                "supplyKey",
                                "feeScheduleKey")
                        .payingWith(civilian))
                .then(getTokenInfo("primary")
                        .hasInvalidAdminKey()
                        .hasInvalidWipeKey()
                        .hasInvalidKycKey()
                        .hasInvalidFreezeKey()
                        .hasInvalidPauseKey()
                        .hasInvalidSupplyKey()
                        .hasInvalidFeeScheduleKey()
                        .logged());
    }

    @HapiTest
    public HapiSpec updateAllLowPriorityKeysToInvalidAllOfThemSign() {
        String saltedName = salted("primary");
        final var civilian = "civilian";
        return defaultHapiSpec("updateAllLowPriorityKeysToInvalidAllOfThemSign")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed("adminKey"),
                        newKeyNamed("wipeKey"),
                        newKeyNamed("kycKey"),
                        newKeyNamed("freezeKey"),
                        newKeyNamed("pauseKey"),
                        newKeyNamed("supplyKey"),
                        newKeyNamed("feeScheduleKey"),
                        tokenCreate("primary")
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey("adminKey")
                                .wipeKey("wipeKey")
                                .kycKey("kycKey")
                                .freezeKey("freezeKey")
                                .pauseKey("pauseKey")
                                .supplyKey("supplyKey")
                                .feeScheduleKey("feeScheduleKey")
                                .payingWith(civilian))
                .when(tokenUpdate("primary")
                        .applyNoValidationToKeys()
                        .usingInvalidWipeKey()
                        .usingInvalidKycKey()
                        .usingInvalidFreezeKey()
                        .usingInvalidPauseKey()
                        .usingInvalidSupplyKey()
                        .usingInvalidFeeScheduleKey()
                        .signedBy(civilian, "wipeKey", "kycKey", "freezeKey", "pauseKey", "supplyKey", "feeScheduleKey")
                        .payingWith(civilian))
                .then(getTokenInfo("primary")
                        .hasInvalidWipeKey()
                        .hasInvalidKycKey()
                        .hasInvalidFreezeKey()
                        .hasInvalidPauseKey()
                        .hasInvalidSupplyKey()
                        .hasInvalidFeeScheduleKey()
                        .logged());
    }

    // here the admin key signature is missing when we try to update low priority keys plus the admin key
    // all low priority key signatures are present, but we should require old admin key signature as well
    @HapiTest
    public HapiSpec failUpdateAllKeysOnlyLowPriorityKeysSign() {
        String saltedName = salted("primary");
        final var civilian = "civilian";
        return defaultHapiSpec("failUpdateAllKeysOnlyLowPriorityKeysSign")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed("adminKey"),
                        newKeyNamed("wipeKey"),
                        newKeyNamed("kycKey"),
                        newKeyNamed("freezeKey"),
                        newKeyNamed("pauseKey"),
                        newKeyNamed("supplyKey"),
                        newKeyNamed("feeScheduleKey"),
                        tokenCreate("primary")
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey("adminKey")
                                .wipeKey("wipeKey")
                                .kycKey("kycKey")
                                .freezeKey("freezeKey")
                                .pauseKey("pauseKey")
                                .supplyKey("supplyKey")
                                .feeScheduleKey("feeScheduleKey")
                                .payingWith(civilian))
                .when(tokenUpdate("primary")
                        .applyNoValidationToKeys()
                        .usingInvalidAdminKey()
                        .usingInvalidWipeKey()
                        .usingInvalidKycKey()
                        .usingInvalidFreezeKey()
                        .usingInvalidPauseKey()
                        .usingInvalidSupplyKey()
                        .usingInvalidFeeScheduleKey()
                        .signedBy(civilian, "wipeKey", "kycKey", "freezeKey", "pauseKey", "supplyKey", "feeScheduleKey")
                        .payingWith(civilian)
                        .hasKnownStatus(INVALID_SIGNATURE))
                .then(getTokenInfo("primary").logged());
    }

    // we try to update all low priority keys but the supply key signature is missing
    @HapiTest
    public HapiSpec failUpdateAllKeysOneLowPriorityKeyDoesNotSign() {
        String saltedName = salted("primary");
        final var civilian = "civilian";
        return defaultHapiSpec("failUpdateAllKeysOneLowPriorityKeyDoesNotSign")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed("adminKey"),
                        newKeyNamed("wipeKey"),
                        newKeyNamed("kycKey"),
                        newKeyNamed("freezeKey"),
                        newKeyNamed("pauseKey"),
                        newKeyNamed("supplyKey"),
                        newKeyNamed("feeScheduleKey"),
                        tokenCreate("primary")
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey("adminKey")
                                .wipeKey("wipeKey")
                                .kycKey("kycKey")
                                .freezeKey("freezeKey")
                                .pauseKey("pauseKey")
                                .supplyKey("supplyKey")
                                .feeScheduleKey("feeScheduleKey")
                                .payingWith(civilian))
                .when(tokenUpdate("primary")
                        .applyNoValidationToKeys()
                        .usingInvalidWipeKey()
                        .usingInvalidKycKey()
                        .usingInvalidFreezeKey()
                        .usingInvalidPauseKey()
                        .usingInvalidSupplyKey()
                        .usingInvalidFeeScheduleKey()
                        .signedBy(civilian, "wipeKey", "kycKey", "freezeKey", "pauseKey", "feeScheduleKey")
                        .payingWith(civilian)
                        .hasKnownStatus(INVALID_SIGNATURE))
                .then(getTokenInfo("primary").logged());
    }

    @HapiTest
    public HapiSpec failUpdateIfKeyIsInvalidAndWeValidateForKeys() {
        String saltedName = salted("primary");
        final var civilian = "civilian";
        return defaultHapiSpec("failUpdateIfKeyIsInvalidAndWeValidateForKeys")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed("adminKey"),
                        newKeyNamed("wipeKey"),
                        newKeyNamed("kycKey"),
                        newKeyNamed("freezeKey"),
                        newKeyNamed("pauseKey"),
                        newKeyNamed("supplyKey"),
                        newKeyNamed("feeScheduleKey"),
                        tokenCreate("primary")
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey("adminKey")
                                .payingWith(civilian))
                .when(tokenUpdate("primary")
                        .usingInvalidAdminKey()
                        .signedBy(civilian, "adminKey")
                        .payingWith(civilian)
                        .hasKnownStatus(INVALID_ADMIN_KEY))
                .then(getTokenInfo("primary").logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
