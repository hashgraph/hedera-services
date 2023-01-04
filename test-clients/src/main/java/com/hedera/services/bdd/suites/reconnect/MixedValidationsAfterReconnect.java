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

    public static void main(String... args) {
        new MixedValidationsAfterReconnect().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(getAccountBalanceFromAllNodes(), validateTopicInfo(), validateFileInfo());
    }

    private HapiSpec getAccountBalanceFromAllNodes() {
        String sender = "0.0.1002";
        String receiver = "0.0.1003";
        String lastlyCreatedAccount = "0.0.21063";
        return defaultHapiSpec("GetAccountBalanceFromAllNodes")
                .given()
                .when()
                .then(
                        balanceSnapshot("senderBalance", sender), // from default node 0.0.3
                        balanceSnapshot("receiverBalance", receiver), // from default node 0.0.3
                        balanceSnapshot(
                                "lastlyCreatedAccountBalance",
                                lastlyCreatedAccount), // from default node 0.0.3
                        getAccountBalance(sender)
                                .logged()
                                .setNode("0.0.4")
                                .hasTinyBars(changeFromSnapshot("senderBalance", 0)),
                        getAccountBalance(receiver)
                                .logged()
                                .setNode("0.0.4")
                                .hasTinyBars(changeFromSnapshot("receiverBalance", 0)),
                        getAccountBalance(sender)
                                .logged()
                                .setNode("0.0.5")
                                .hasTinyBars(changeFromSnapshot("senderBalance", 0)),
                        getAccountBalance(receiver)
                                .logged()
                                .setNode("0.0.5")
                                .hasTinyBars(changeFromSnapshot("receiverBalance", 0)),
                        getAccountBalance(sender)
                                .logged()
                                .setNode("0.0.8")
                                .hasTinyBars(changeFromSnapshot("senderBalance", 0)),
                        getAccountBalance(receiver)
                                .logged()
                                .setNode("0.0.8")
                                .hasTinyBars(changeFromSnapshot("receiverBalance", 0)),
                        getAccountBalance(lastlyCreatedAccount)
                                .logged()
                                .setNode("0.0.8")
                                .hasTinyBars(changeFromSnapshot("lastlyCreatedAccountBalance", 0)));
    }

    private HapiSpec validateTopicInfo() {
        String firstlyCreatedTopic = "0.0.21064";
        String lastlyCreatedTopic = "0.0.41063";
        String invalidTopicId = "0.0.41064";
        String topicIdWithMessagesSubmittedTo = "0.0.30050";
        byte[] emptyRunningHash = new byte[48];
        return defaultHapiSpec("ValidateTopicInfo")
                .given(getTopicInfo(topicIdWithMessagesSubmittedTo).logged().saveRunningHash())
                .when()
                .then(
                        getTopicInfo(firstlyCreatedTopic)
                                .logged()
                                .setNode("0.0.8")
                                .hasRunningHash(emptyRunningHash),
                        getTopicInfo(lastlyCreatedTopic)
                                .logged()
                                .setNode("0.0.8")
                                .hasRunningHash(emptyRunningHash),
                        getTopicInfo(invalidTopicId)
                                .hasCostAnswerPrecheck(ResponseCodeEnum.INVALID_TOPIC_ID),
                        getTopicInfo(topicIdWithMessagesSubmittedTo)
                                .logged()
                                .setNode("0.0.8")
                                .hasRunningHash(topicIdWithMessagesSubmittedTo));
    }

    private HapiSpec validateFileInfo() {
        String firstlyCreatedFile = "0.0.41064";
        String lastlyCreatedFile = "0.0.42063";
        String invalidFileId = "0.0.42064";
        return defaultHapiSpec("ValidateFileInfo")
                .given()
                .when()
                .then(
                        getFileInfo(firstlyCreatedFile).logged().setNode("0.0.8"),
                        getFileInfo(lastlyCreatedFile).logged().setNode("0.0.8"),
                        getFileInfo(invalidFileId)
                                .hasCostAnswerPrecheck(ResponseCodeEnum.INVALID_FILE_ID));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
