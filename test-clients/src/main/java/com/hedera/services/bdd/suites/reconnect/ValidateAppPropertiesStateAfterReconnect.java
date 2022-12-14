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
package com.hedera.services.bdd.suites.reconnect;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiSuite;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ValidateAppPropertiesStateAfterReconnect extends HapiSuite {
    private static final Logger log =
            LogManager.getLogger(ValidateAppPropertiesStateAfterReconnect.class);

    final String PATH_TO_VALID_BYTECODE = HapiSpecSetup.getDefaultInstance().defaultContractPath();

    public static void main(String... args) {
        new ValidateAppPropertiesStateAfterReconnect().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(validateAppPropertiesStateAfterReconnect());
    }

    private HapiSpec validateAppPropertiesStateAfterReconnect() {
        return customHapiSpec("validateAppPropertiesStateAfterReconnect")
                .withProperties(Map.of("txn.start.offset.secs", "-5"))
                .given(
                        sleepFor(Duration.ofSeconds(25).toMillis()),
                        getAccountBalance(GENESIS).setNode("0.0.6").unavailableNode())
                .when(
                        getAccountBalance(GENESIS).setNode("0.0.6").unavailableNode(),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of("minimumAutoRenewDuration", "20")),
                        getAccountBalance(GENESIS).setNode("0.0.6").unavailableNode())
                .then(
                        withLiveNode("0.0.6")
                                .within(180, TimeUnit.SECONDS)
                                .loggingAvailabilityEvery(30)
                                .sleepingBetweenRetriesFor(10),
                        fileCreate("contractFile")
                                .setNode("0.0.6")
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .path(PATH_TO_VALID_BYTECODE),
                        contractCreate("testContract")
                                .bytecode("contractFile")
                                .autoRenewSecs(15)
                                .setNode("0.0.6")
                                .hasPrecheck(AUTORENEW_DURATION_NOT_IN_RANGE));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
