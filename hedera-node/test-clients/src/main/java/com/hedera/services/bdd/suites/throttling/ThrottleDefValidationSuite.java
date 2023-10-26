/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.throttling;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OPERATION_REPEATED_IN_BUCKET_GROUPS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class ThrottleDefValidationSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(ThrottleDefValidationSuite.class);

    private static final String DEFAULT_CONGESTION_MULTIPLIERS =
            HapiSpecSetup.getDefaultNodeProps().get("fees.percentCongestionMultipliers");

    public static void main(String... args) {
        new ThrottleDefValidationSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            throttleDefsRejectUnauthorizedPayers(),
            throttleUpdateRejectsMultiGroupAssignment(),
            throttleUpdateWithZeroGroupOpsPerSecFails(),
            updateWithMissingTokenMintFails(),
            ensureDefaultsRestored()
        });
    }

    @HapiTest
    private HapiSpec updateWithMissingTokenMintFails() {
        var missingMintThrottles = protoDefsFromResource("testSystemFiles/throttles-sans-mint.json");

        return defaultHapiSpec("updateWithMissingTokenMintFails")
                .given()
                .when()
                .then(fileUpdate(THROTTLE_DEFS)
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .contents(missingMintThrottles.toByteArray())
                        .hasKnownStatusFrom(INVALID_TRANSACTION, SUCCESS_BUT_MISSING_EXPECTED_OPERATION));
    }

    @HapiTest
    private HapiSpec ensureDefaultsRestored() {
        var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");

        return defaultHapiSpec("EnsureDefaultsRestored")
                .given()
                .when()
                .then(
                        fileUpdate(THROTTLE_DEFS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(defaultThrottles.toByteArray()),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(
                                        Map.of("fees.percentCongestionMultipliers", DEFAULT_CONGESTION_MULTIPLIERS)));
    }

    @HapiTest
    private HapiSpec throttleUpdateWithZeroGroupOpsPerSecFails() {
        var zeroOpsPerSecThrottles = protoDefsFromResource("testSystemFiles/zero-ops-group.json");

        return defaultHapiSpec("ThrottleUpdateWithZeroGroupOpsPerSecFails")
                .given()
                .when()
                .then(fileUpdate(THROTTLE_DEFS)
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .contents(zeroOpsPerSecThrottles.toByteArray())
                        .hasKnownStatus(THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC));
    }

    @HapiTest
    private HapiSpec throttleUpdateRejectsMultiGroupAssignment() {
        var multiGroupThrottles = protoDefsFromResource("testSystemFiles/duplicated-operation.json");

        return defaultHapiSpec("ThrottleUpdateRejectsMultiGroupAssignment")
                .given()
                .when()
                .then(fileUpdate(THROTTLE_DEFS)
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .contents(multiGroupThrottles.toByteArray())
                        .hasKnownStatus(OPERATION_REPEATED_IN_BUCKET_GROUPS));
    }

    @HapiTest
    private HapiSpec throttleDefsRejectUnauthorizedPayers() {
        return defaultHapiSpec("ThrottleDefsRejectUnauthorizedPayers")
                .given(
                        cryptoCreate("civilian"),
                        cryptoTransfer(movingHbar(ONE_HUNDRED_HBARS).between(GENESIS, FEE_SCHEDULE_CONTROL)))
                .when()
                .then(
                        fileUpdate(THROTTLE_DEFS)
                                .contents("BOOM")
                                .payingWith("civilian")
                                .hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(THROTTLE_DEFS)
                                .contents("BOOM")
                                .payingWith(FEE_SCHEDULE_CONTROL)
                                .hasPrecheck(AUTHORIZATION_FAILED));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
