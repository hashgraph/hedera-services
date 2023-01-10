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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UpdatePermissionsDuringReconnect extends HapiSuite {
    private static final Logger log = LogManager.getLogger(UpdatePermissionsDuringReconnect.class);
    public static final String NODE_ACCOUNT = "0.0.6";

    public static void main(String... args) {
        new UpdatePermissionsDuringReconnect().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(updateApiPermissionsDuringReconnect());
    }

    private HapiSpec updateApiPermissionsDuringReconnect() {
        final String fileInfoRegistry = "apiPermissionsReconnect";
        return defaultHapiSpec("updateApiPermissionsDuringReconnect")
                .given(
                        sleepFor(Duration.ofSeconds(25).toMillis()),
                        getAccountBalance(GENESIS).setNode(NODE_ACCOUNT).unavailableNode())
                .when(
                        fileUpdate(API_PERMISSIONS)
                                .overridingProps(Map.of("updateFile", "1-1011"))
                                .payingWith(SYSTEM_ADMIN)
                                .logged(),
                        getAccountBalance(GENESIS).setNode(NODE_ACCOUNT).unavailableNode())
                .then(
                        withLiveNode(NODE_ACCOUNT)
                                .within(5 * 60, TimeUnit.SECONDS)
                                .loggingAvailabilityEvery(30)
                                .sleepingBetweenRetriesFor(10),
                        UtilVerbs.sleepFor(30 * 1000L),
                        withLiveNode(NODE_ACCOUNT)
                                .within(5 * 60, TimeUnit.SECONDS)
                                .loggingAvailabilityEvery(30)
                                .sleepingBetweenRetriesFor(10),
                        getFileContents(API_PERMISSIONS)
                                .logged()
                                .setNode("0.0.3")
                                .payingWith(SYSTEM_ADMIN)
                                .saveToRegistry(fileInfoRegistry),
                        getFileContents(API_PERMISSIONS)
                                .logged()
                                .setNode(NODE_ACCOUNT)
                                .payingWith(SYSTEM_ADMIN)
                                .hasContents(fileInfoRegistry));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
