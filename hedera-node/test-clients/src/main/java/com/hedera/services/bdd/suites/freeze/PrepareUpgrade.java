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

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.initializeSettings;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.upgradeFileHash;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.upgradeFileId;

import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public final class PrepareUpgrade extends HapiSuite {
    private static final Logger log = LogManager.getLogger(PrepareUpgrade.class);

    public static void main(String... args) {
        new PrepareUpgrade().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(prepareUpgrade());
    }

    Stream<DynamicTest> prepareUpgrade() {
        return hapiTest(flattened(initializeSettings(), sourcing(() -> UtilVerbs.prepareUpgrade()
                .withUpdateFile(upgradeFileId())
                .signedBy(GENESIS)
                .payingWith(GENESIS)
                .havingHash(upgradeFileHash()))));
    }
}
