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

package com.hedera.services.bdd.suites.freeze;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;

import com.hedera.services.bdd.suites.HapiSuite;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class SimpleFreezeOnly extends HapiSuite {
    private static final Logger log = LogManager.getLogger(SimpleFreezeOnly.class);

    public static void main(String... args) {
        new SimpleFreezeOnly().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return allOf(positiveTests());
    }

    private List<Stream<DynamicTest>> positiveTests() {
        return Arrays.asList(simpleFreezeWithTimestamp());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    final Stream<DynamicTest> simpleFreezeWithTimestamp() {
        //        return defaultHapiSpec("SimpleFreezeWithTimeStamp")
        //                .given(freezeOnly().payingWith(GENESIS).startingAt(Instant.now().plusSeconds(10)))
        //                .when(sleepFor(40000))
        //                .then();
        return customHapiSpec("hmm")
                .withProperties(Map.of(
                        "nodes", "35.196.138.70",
                        "fees.useFixedOffer", "false",
                        "fees.fixedOffer", "10000000000"))
                .given()
                .when()
                .then(getFileContents(FEE_SCHEDULE).saveTo("weird.bin"));
    }
}
