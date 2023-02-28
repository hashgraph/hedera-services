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

package com.hedera.services.yahcli.suites;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InfoSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(InfoSuite.class);

    private final Map<String, String> specConfig;
    private final List<String> accounts;

    public InfoSuite(final Map<String, String> specConfig, final String[] accounts) {
        this.specConfig = specConfig;
        this.accounts = rationalized(accounts);
    }

    private List<String> rationalized(final String[] accounts) {
        return Arrays.stream(accounts).map(Utils::extractAccount).toList();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        List<HapiSpec> specToRun = new ArrayList<>();
        accounts.forEach(s -> specToRun.add(getInfo(s)));
        return specToRun;
    }

    private HapiSpec getInfo(String accountID) {
        return HapiSpec.customHapiSpec("getInfo")
                .withProperties(specConfig)
                .given()
                .when()
                .then(getAccountInfo(accountID).logged().loggingHexedKeys());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
