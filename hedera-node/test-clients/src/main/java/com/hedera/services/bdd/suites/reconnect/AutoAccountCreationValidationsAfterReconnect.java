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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.suites.reconnect.AutoAccountCreationsBeforeReconnect.TOTAL_ACCOUNTS;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AutoAccountCreationValidationsAfterReconnect extends HapiSuite {
    private static final Logger log =
            LogManager.getLogger(AutoAccountCreationValidationsAfterReconnect.class);

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(getAccountInfoOfAutomaticallyCreatedAccounts());
    }

    public static void main(String... args) {
        new AutoAccountCreationValidationsAfterReconnect().runSuiteSync();
    }
    /* These validations are assuming the state is from a 6N-1C test in which a client generates 10 autoAccounts in the
     * beginning of the test */
    private HapiSpec getAccountInfoOfAutomaticallyCreatedAccounts() {
        return defaultHapiSpec("GetAccountInfoOfAutomaticallyCreatedAccounts")
                .given()
                .when()
                .then(
                        inParallel(
                                asOpArray(
                                        TOTAL_ACCOUNTS,
                                        i ->
                                                getAccountInfo("0.0." + (i + 1004))
                                                        .has(
                                                                AccountInfoAsserts.accountWith()
                                                                        .hasAlias())
                                                        .setNode("0.0.8"))));
    }
}
