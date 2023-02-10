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

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ValidatePermissionStateAfterReconnect extends HapiSuite {
    private static final Logger log =
            LogManager.getLogger(ValidatePermissionStateAfterReconnect.class);
    public static final String NODE_ACCOUNT = "0.0.6";

    public static void main(String... args) {
        new ValidatePermissionStateAfterReconnect().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(validateApiPermissionStateAfterReconnect());
    }

    private HapiSpec validateApiPermissionStateAfterReconnect() {
        return customHapiSpec("validateApiPermissionStateAfterReconnect")
                .withProperties(Map.of("txn.start.offset.secs", "-5"))
                .given(
                        sleepFor(Duration.ofSeconds(25).toMillis()),
                        getAccountBalance(GENESIS).setNode(NODE_ACCOUNT).unavailableNode())
                .when(
                        getAccountBalance(GENESIS).setNode(NODE_ACCOUNT).unavailableNode(),
                        fileCreate("effectivelyImmutable").contents("Can't touch me!"),
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of("updateFile", "2-50")),
                        getAccountBalance(GENESIS).setNode(NODE_ACCOUNT).unavailableNode())
                .then(
                        withLiveNode(NODE_ACCOUNT)
                                .within(180, TimeUnit.SECONDS)
                                .loggingAvailabilityEvery(30)
                                .sleepingBetweenRetriesFor(10),
                        fileUpdate("effectivelyImmutable")
                                .setNode(NODE_ACCOUNT)
                                .hasPrecheck(NOT_SUPPORTED));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
