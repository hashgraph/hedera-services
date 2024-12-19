/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.jrs;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ED_25519_KEY;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.initializeSettings;

import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class NodeOpsForUpgrade extends HapiSuite {
    private static final Logger log = LogManager.getLogger(NodeOpsForUpgrade.class);

    public static void main(String... args) {
        new NodeOpsForUpgrade().runSuiteSync();
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(doDelete());
    }

    final Stream<DynamicTest> doDelete() {
        return defaultHapiSpec("NodeOpsForUpgrade")
                .given(initializeSettings())
                .when(
                        overridingTwo("nodes.enableDAB", "true", "nodes.updateAccountIdAllowed", "true"),
                        newKeyNamed(ED_25519_KEY).shape(KeyShape.ED25519),
                        nodeDelete("3").payingWith(GENESIS).signedBy(GENESIS),
                        nodeUpdate("2")
                                .description("UpdatedNode0")
                                .accountId("0.0.100")
                                .payingWith(GENESIS)
                                .signedBy(GENESIS))
                .then(overridingTwo("nodes.enableDAB", "true", "nodes.updateAccountIdAllowed", "false"));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
