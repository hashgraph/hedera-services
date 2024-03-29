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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

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
        return List.of(updateAllKeysToInvalidAdminKeySigns(), updateAllKeysToInvalidAllKeysSign());
    }

    private List<HapiSpec> negativeTests() {
        return List.of(
                updateFailsAllKeysToInvalidOnlyLowPriorityKeysSign(),
                updateFailsAllKeysToInvalidOneLowPriorityKeyDoesNotSign(),
                updateFailsIfTokenIsInvalidAndWeValidateForKeys());
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

    // here the admin key signature is missing when we try to update low priority keys plus the admin key
    // all low priority key signatures are present, but we should require old admin key signature as well
    @HapiTest
    public HapiSpec updateFailsAllKeysToInvalidOnlyLowPriorityKeysSign() {
        String saltedName = salted("primary");
        final var civilian = "civilian";
        return defaultHapiSpec("updateFailsAllKeysToInvalidOnlyLowPriorityKeysSign")
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
    public HapiSpec updateFailsAllKeysToInvalidOneLowPriorityKeyDoesNotSign() {
        String saltedName = salted("primary");
        final var civilian = "civilian";
        return defaultHapiSpec("updateFailsAllKeysToInvalidOneLowPriorityKeyDoesNotSign")
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
    public HapiSpec updateFailsIfTokenIsInvalidAndWeValidateForKeys() {
        String saltedName = salted("primary");
        final var civilian = "civilian";
        return defaultHapiSpec("updateFailsIfTokenIsInvalidAndWeValidateForKeys")
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
