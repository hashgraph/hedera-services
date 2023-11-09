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

package com.hedera.services.bdd.suites.meta;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.BddTestNameDoesNotMatchMethodName;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
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
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(discoversExpectedVersions());
    }

    @BddTestNameDoesNotMatchMethodName
    @HapiTest
    private HapiSpec discoversExpectedVersions() {
        if (specConfig != null) {
            return customHapiSpec("getVersionInfo")
                    .withProperties(specConfig)
                    .given()
                    .when()
                    .then(getVersionInfo().withYahcliLogging().noLogging());
        } else {
            return defaultHapiSpec("discoversExpectedVersions")
                    .given()
                    .when()
                    .then(getVersionInfo().logged().hasNoDegenerateSemvers());
        }
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
