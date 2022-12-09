/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.autorenew;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TopicAutoRenewalSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TopicAutoRenewalSuite.class);

    public static void main(String... args) {
        new TopicAutoRenewalSuite().runSuiteSync();
    }

    // TODO : just added empty shells for now.

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(topicAutoRemoval(), topicAutoRenewal());
    }

    private HapiSpec topicAutoRemoval() {
        return defaultHapiSpec("").given().when().then();
    }

    private HapiSpec topicAutoRenewal() {
        return defaultHapiSpec("").given().when().then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
