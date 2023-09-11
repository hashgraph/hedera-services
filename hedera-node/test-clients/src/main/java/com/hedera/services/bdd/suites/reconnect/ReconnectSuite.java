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

package com.hedera.services.bdd.suites.reconnect;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.shutdownNode;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class ReconnectSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ReconnectSuite.class);

    public static void main(String... args) {
        new ReconnectSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(checkUnavailableNode());
    }

    /**
     * A simple sanity check to verify that
     * @return
     */
    @HapiTest
    private HapiSpec checkUnavailableNode() {
        return defaultHapiSpec("CheckUnavailableNode")
                .given()
                .when(shutdownNode("Bob"))
                .then(getAccountBalance(GENESIS).setNode("0.0.4").unavailableNode(), // Bob is NOT available
                        getAccountBalance(GENESIS).setNode("0.0.3"));                // Alice IS available
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
