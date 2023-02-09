/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.perf.token;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenAssociationLoadTest extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TokenAssociationLoadTest.class);

    private AtomicInteger maxTokens = new AtomicInteger(500);

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    runTokenAssociationLoadTest(),
                });
    }

    private HapiSpec runTokenAssociationLoadTest() {
        return HapiSpec.defaultHapiSpec("RunTokenAssociationLoadTest")
                .given(
                        overridingTwo(
                                "tokens.maxPerAccount", "10",
                                "tokens.maxRelsPerInfoQuery", "10"))
                .when(
                        // Something seems to be missing here...
                        )
                .then(
                        //  Restore defaults
                        overridingTwo(
                                "tokens.maxPerAccount", "1000",
                                "tokens.maxRelsPerInfoQuery", "1000"));
    }
}
