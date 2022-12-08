/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.PropertySource.asAccount;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ResetThrottleSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ResetThrottleSuite.class);

    public static void main(String... args) {
        new ResetThrottleSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(resetThrottle());
    }

    private HapiSpec resetThrottle() {
        final var devThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
        return defaultHapiSpec("ResetThrottle")
                .given()
                .when(
                        // only allow the first client to update throttle file with the first node
                        withOpContext(
                                (spec, opLog) -> {
                                    HapiSpecOperation subOp;
                                    if (spec.setup().defaultNode().equals(asAccount("0.0.3"))) {
                                        subOp =
                                                fileUpdate(THROTTLE_DEFS)
                                                        .payingWith(GENESIS)
                                                        .contents(devThrottles.toByteArray());
                                    } else {
                                        subOp = sleepFor(20000);
                                    }
                                    allRunFor(spec, subOp);
                                }))
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
