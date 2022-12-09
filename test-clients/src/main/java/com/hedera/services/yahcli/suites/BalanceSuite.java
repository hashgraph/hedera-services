/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BalanceSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(BalanceSuite.class);

    private final Map<String, String> specConfig;
    private final List<String> accounts;

    public BalanceSuite(final Map<String, String> specConfig, final String[] accounts) {
        this.specConfig = specConfig;
        this.accounts = rationalized(accounts);
    }

    private List<String> rationalized(final String[] accounts) {
        return Arrays.stream(accounts).map(Utils::extractAccount).collect(Collectors.toList());
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        List<HapiSpec> specToRun = new ArrayList<>();
        accounts.forEach(s -> specToRun.add(getBalance(s)));
        return specToRun;
    }

    private HapiSpec getBalance(String accountID) {
        return HapiSpec.customHapiSpec("getBalance")
                .withProperties(specConfig)
                .given()
                .when()
                .then(getAccountBalance(accountID).noLogging().withYahcliLogging());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
