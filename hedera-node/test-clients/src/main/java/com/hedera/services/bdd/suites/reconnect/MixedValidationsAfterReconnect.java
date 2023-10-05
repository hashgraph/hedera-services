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
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MixedValidationsAfterReconnect extends HapiSuite {
    private static final Logger log = LogManager.getLogger(MixedValidationsAfterReconnect.class);

    private static final String SENDER = "0.0.1301";
    private static final String RECEIVER = "0.0.1302";
    private static final String LAST_CREATED_ACCOUNT = "0.0.3500";
    private static final String FIRST_CREATED_TOPIC = "0.0.3600";
    private static final String LAST_CREATED_TOPIC = "0.0.5900";
    private static final String INVALID_TOPIC_ID = "0.0.41064";
    private static final String TOPIC_ID_WITH_MESSAGE_SUBMITTED_TO = "0.0.5900";

    private static final String FIRST_CREATED_FILE = "0.0.6100";
    private static final String LAST_CREATED_FILE = "0.0.6900";
    private static final String INVALID_FILE_ID = "0.0.7064";

    public static void main(String... args) {
        new MixedValidationsAfterReconnect().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(getAccountBalanceFromAllNodes(), validateTopicInfo(), validateFileInfo());
    }

    private HapiSpec getAccountBalanceFromAllNodes() {
        // Since https://github.com/hashgraph/hedera-services/pull/5799, the nodes will create
        // 299 "blocklist" accounts with EVM addresses commonly used in HardHat test environments,
        // to protect developers from accidentally sending hbar to those addresses
        // NOTE: blacklisted accounts are NOT created for use when blacklisted accounts are disabled
        return defaultHapiSpec("GetAccountBalanceFromAllNodes")
                .given()
                .when()
                .then(
                        balanceSnapshot("senderBalance", SENDER), // from default node 0.0.3
                        balanceSnapshot("receiverBalance", RECEIVER), // from default node 0.0.3
                        balanceSnapshot("lastlyCreatedAccountBalance", LAST_CREATED_ACCOUNT), // from default node 0.0.3
                        getAccountBalance(SENDER)
                                .logged()
                                .setNode("0.0.4")
                                .hasTinyBars(changeFromSnapshot("senderBalance", 0)),
                        getAccountBalance(RECEIVER)
                                .logged()
                                .setNode("0.0.4")
                                .hasTinyBars(changeFromSnapshot("receiverBalance", 0)),
                        getAccountBalance(SENDER)
                                .logged()
                                .setNode("0.0.5")
                                .hasTinyBars(changeFromSnapshot("senderBalance", 0)),
                        getAccountBalance(RECEIVER)
                                .logged()
                                .setNode("0.0.5")
                                .hasTinyBars(changeFromSnapshot("receiverBalance", 0)),
                        getAccountBalance(SENDER)
                                .logged()
                                .setNode("0.0.8")
                                .hasTinyBars(changeFromSnapshot("senderBalance", 0)),
                        getAccountBalance(RECEIVER)
                                .logged()
                                .setNode("0.0.8")
                                .hasTinyBars(changeFromSnapshot("receiverBalance", 0)),
                        getAccountBalance(LAST_CREATED_ACCOUNT)
                                .logged()
                                .setNode("0.0.8")
                                .hasTinyBars(changeFromSnapshot("lastlyCreatedAccountBalance", 0)));
    }

    private HapiSpec validateTopicInfo() {
        final byte[] emptyRunningHash = new byte[48];
        return defaultHapiSpec("ValidateTopicInfo")
                .given(getTopicInfo(TOPIC_ID_WITH_MESSAGE_SUBMITTED_TO).logged().saveRunningHash())
                .when()
                .then(
                        getTopicInfo(FIRST_CREATED_TOPIC)
                                .logged()
                                .setNode("0.0.8")
                                .hasRunningHash(emptyRunningHash),
                        getTopicInfo(LAST_CREATED_TOPIC)
                                .logged()
                                .setNode("0.0.8")
                                .hasRunningHash(emptyRunningHash),
                        getTopicInfo(INVALID_TOPIC_ID).hasCostAnswerPrecheck(ResponseCodeEnum.INVALID_TOPIC_ID),
                        getTopicInfo(TOPIC_ID_WITH_MESSAGE_SUBMITTED_TO)
                                .logged()
                                .setNode("0.0.8")
                                .hasRunningHash(TOPIC_ID_WITH_MESSAGE_SUBMITTED_TO));
    }

    private HapiSpec validateFileInfo() {
        return defaultHapiSpec("ValidateFileInfo")
                .given()
                .when()
                .then(
                        getFileInfo(FIRST_CREATED_FILE).logged().setNode("0.0.8"),
                        getFileInfo(LAST_CREATED_FILE).logged().setNode("0.0.8"),
                        getFileInfo(INVALID_FILE_ID).hasCostAnswerPrecheck(ResponseCodeEnum.INVALID_FILE_ID));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
