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

package com.hedera.services.bdd.suites.meta;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;

import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class VersionInfoSpec extends HapiSuite {
    private static final Logger log = LogManager.getLogger(VersionInfoSpec.class);
    private final Map<String, String> specConfig;

    public VersionInfoSpec(final Map<String, String> specConfig) {
        this.specConfig = specConfig;
    }

    public VersionInfoSpec() {
        specConfig = null;
    }

    public static void main(String... args) {
        new VersionInfoSpec().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(discoversExpectedVersions());
    }

    final Stream<DynamicTest> discoversExpectedVersions() {
        if (specConfig != null) {
            return customHapiSpec("getVersionInfo")
                    .withProperties(specConfig)
                    .given()
                    .when()
                    .then(getVersionInfo().withYahcliLogging().noLogging());
        } else {
            return hapiTest(getVersionInfo().logged().hasNoDegenerateSemvers());
        }
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
