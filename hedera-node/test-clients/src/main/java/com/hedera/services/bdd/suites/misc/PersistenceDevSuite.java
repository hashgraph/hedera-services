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
package com.hedera.services.bdd.suites.misc;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PersistenceDevSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(PersistenceDevSuite.class);

    public static void main(String... args) throws Exception {
        new PersistenceDevSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    testEntityLoading(),
                });
    }

    private HapiSpec testEntityLoading() {
        return customHapiSpec("TestEntityLoading")
                .withProperties(Map.of("persistentEntities.dir.path", "persistent-entities/"))
                .given(
                        getTokenInfo("knownToken").logged(),
                        getTopicInfo("knownTopic").logged(),
                        getAccountInfo("knownAccount").logged())
                .when()
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
