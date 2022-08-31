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
package com.hedera.services.bdd.suites.freeze;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SimpleFreezeOnly extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(SimpleFreezeOnly.class);

    public static void main(String... args) {
        new SimpleFreezeOnly().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return allOf(positiveTests());
    }

    private List<HapiApiSpec> positiveTests() {
        return Arrays.asList(simpleFreezeWithTimestamp());
    }

    private HapiApiSpec simpleFreezeWithTimestamp() {
        return defaultHapiSpec("SimpleFreezeWithTimeStamp")
                .given(
                        cryptoCreate("civilian"),
//                        freezeOnly().payingWith(GENESIS).startingAt(Instant.now().plusSeconds(10)))
                        freezeOnly().payingWith("civilian").startingAt(Instant.now().plusSeconds(10)))
                .when(sleepFor(11000))
                .then(
                        cryptoCreate("not_going_to_happen")
                                .hasPrecheck(ResponseCodeEnum.PLATFORM_NOT_ACTIVE));
    }
}
