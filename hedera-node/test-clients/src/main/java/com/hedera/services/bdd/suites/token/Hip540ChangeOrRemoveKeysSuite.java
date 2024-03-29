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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;

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
        return List.of(validateThatTheAdminKeyCanRemoveOtherKeys(), validateThatTheAdminKeyCanRemoveItself());
    }

    private List<HapiSpec> negativeTests() {
        return List.of(updateFailsIfTokenIsImmutable(), updateFailsIfKeyIsMissing());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @HapiTest
    public HapiSpec validateThatTheAdminKeyCanRemoveOtherKeys() {
        final var civilian = "civilian";
        return defaultHapiSpec("validateThatTheAdminKeyCanRemoveOtherKeys")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed("adminKey"),
                        newKeyNamed("freezeKey"),
                        newKeyNamed("kycKey"),
                        newKeyNamed("supplyKey"),
                        newKeyNamed("wipeKey"),
                        newKeyNamed("pauseKey"),
                        tokenCreate("primary")
                                .adminKey("adminKey")
                                .freezeKey("freezeKey")
                                .kycKey("kycKey")
                                .supplyKey("supplyKey")
                                .wipeKey("wipeKey")
                                .pauseKey("pauseKey")
                                .payingWith(civilian))
                .when(tokenUpdate("primary")
                        .properlyEmptyingFreezeKey()
                        .properlyEmptyingKycKey()
                        .properlyEmptyingSupplyKey()
                        .properlyEmptyingWipeKey()
                        .properlyEmptyingPauseKey()
                        .signedBy(civilian, "adminKey")
                        .payingWith(civilian))
                .then(getTokenInfo("primary")
                        .logged()
                        .hasEmptyFreezeKey()
                        .hasEmptyKycKey()
                        .hasEmptySupplyKey()
                        .hasEmptyWipeKey()
                        .hasEmptyPauseKey());
    }

    @HapiTest
    public HapiSpec validateThatTheAdminKeyCanRemoveItself() {
        final var civilian = "civilian";
        return defaultHapiSpec("validateThatTheAdminKeyCanRemoveItself")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed("adminKey"),
                        tokenCreate("primary").adminKey("adminKey").payingWith(civilian))
                .when(tokenUpdate("primary")
                        .properlyEmptyingAdminKey()
                        .signedBy(civilian, "adminKey")
                        .payingWith(civilian))
                .then(getTokenInfo("primary").logged().hasEmptyAdminKey());
    }

    @HapiTest
    public HapiSpec updateFailsIfTokenIsImmutable() {
        final var civilian = "civilian";
        return defaultHapiSpec("updateFailsIfTokenIsImmutable")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed("adminKey"),
                        tokenCreate("primary").payingWith(civilian))
                .when()
                .then(tokenUpdate("primary")
                        .properlyEmptyingAdminKey()
                        .signedBy(civilian, "adminKey")
                        .payingWith(civilian)
                        .hasKnownStatus(TOKEN_IS_IMMUTABLE));
    }

    @HapiTest
    public HapiSpec updateFailsIfKeyIsMissing() {
        final var civilian = "civilian";
        return defaultHapiSpec("updateFailsIfKeyIsMissing")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed("adminKey"),
                        tokenCreate("primary").adminKey("adminKey").payingWith(civilian))
                .when()
                .then(
                        tokenUpdate("primary")
                                .properlyEmptyingFreezeKey()
                                .signedBy(civilian, "adminKey")
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_FREEZE_KEY),
                        tokenUpdate("primary")
                                .properlyEmptyingKycKey()
                                .signedBy(civilian, "adminKey")
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_KYC_KEY),
                        tokenUpdate("primary")
                                .properlyEmptyingSupplyKey()
                                .signedBy(civilian, "adminKey")
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_SUPPLY_KEY),
                        tokenUpdate("primary")
                                .properlyEmptyingWipeKey()
                                .signedBy(civilian, "adminKey")
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_WIPE_KEY),
                        tokenUpdate("primary")
                                .properlyEmptyingPauseKey()
                                .signedBy(civilian, "adminKey")
                                .payingWith(civilian)
                                .hasKnownStatus(TOKEN_HAS_NO_PAUSE_KEY));
    }
}
