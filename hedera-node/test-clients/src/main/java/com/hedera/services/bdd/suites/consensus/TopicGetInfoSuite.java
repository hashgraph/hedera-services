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

package com.hedera.services.bdd.suites.consensus;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TopicGetInfoSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TopicGetInfoSuite.class);
    public static final String TEST_TOPIC = "testTopic";
    public static final String TESTMEMO = "testmemo";

    public static void main(String... args) {
        new TopicGetInfoSuite().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(allFieldsSetHappyCase());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    private HapiSpec allFieldsSetHappyCase() {
        // sequenceNumber should be 0 and runningHash should be 48 bytes all 0s.
        return defaultHapiSpec("AllFieldsSetHappyCase")
                .given(
                        newKeyNamed("adminKey"),
                        newKeyNamed("submitKey"),
                        cryptoCreate("autoRenewAccount"),
                        cryptoCreate("payer"),
                        createTopic(TEST_TOPIC)
                                .topicMemo(TESTMEMO)
                                .adminKeyName("adminKey")
                                .submitKeyName("submitKey")
                                .autoRenewAccountId("autoRenewAccount")
                                .via("createTopic"))
                .when()
                .then(
                        getTopicInfo(TEST_TOPIC)
                                .hasExpectedLedgerId("0x03")
                                .hasMemo(TESTMEMO)
                                .hasAdminKey("adminKey")
                                .hasSubmitKey("submitKey")
                                .hasAutoRenewAccount("autoRenewAccount")
                                .hasSeqNo(0)
                                .hasRunningHash(new byte[48]),
                        getTxnRecord("createTopic").logged(),
                        submitMessageTo(TEST_TOPIC)
                                .blankMemo()
                                .payingWith("payer")
                                .message(new String("test".getBytes()))
                                .via("submitMessage"),
                        getTxnRecord("submitMessage").logged(),
                        getTopicInfo(TEST_TOPIC)
                                .hasExpectedLedgerId("0x03")
                                .hasMemo(TESTMEMO)
                                .hasAdminKey("adminKey")
                                .hasSubmitKey("submitKey")
                                .hasAutoRenewAccount("autoRenewAccount")
                                .hasSeqNo(1)
                                .logged(),
                        updateTopic(TEST_TOPIC)
                                .topicMemo("Don't worry about the vase")
                                .via("updateTopic"),
                        getTxnRecord("updateTopic").logged(),
                        getTopicInfo(TEST_TOPIC)
                                .hasExpectedLedgerId("0x03")
                                .hasMemo("Don't worry about the vase")
                                .hasAdminKey("adminKey")
                                .hasSubmitKey("submitKey")
                                .hasAutoRenewAccount("autoRenewAccount")
                                .hasSeqNo(1)
                                .logged(),
                        deleteTopic(TEST_TOPIC).via("deleteTopic"),
                        getTxnRecord("deleteTopic").logged(),
                        getTopicInfo(TEST_TOPIC)
                                .hasCostAnswerPrecheck(INVALID_TOPIC_ID)
                                .logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
