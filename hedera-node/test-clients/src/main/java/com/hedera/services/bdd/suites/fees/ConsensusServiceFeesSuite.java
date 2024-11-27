/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.fees;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;

import com.hedera.services.bdd.junit.HapiTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class ConsensusServiceFeesSuite {
    @HapiTest
    final Stream<DynamicTest> topicCreateFeeAsExpected() {
        return hapiTest(
                newKeyNamed("adminKey"),
                newKeyNamed("submitKey"),
                cryptoCreate("autoRenewAccount"),
                cryptoCreate("payer").balance(ONE_HUNDRED_HBARS),
                createTopic("testTopic")
                        .topicMemo("testmemo")
                        .adminKeyName("adminKey")
                        .submitKeyName("submitKey")
                        .autoRenewAccountId("autoRenewAccount")
                        .payingWith("payer")
                        .via("topicCreate"),
                validateChargedUsd("topicCreate", 0.0226));
        // TODO: Wrong value (0.226) used, should be 0.01, the test probably has to be updated
    }

    @HapiTest
    final Stream<DynamicTest> topicUpdateFeeAsExpected() {
        return hapiTest(
                cryptoCreate("autoRenewAccount"),
                cryptoCreate("payer"),
                createTopic("testTopic")
                        .autoRenewAccountId("autoRenewAccount")
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS - 1)
                        .adminKeyName("payer"),
                updateTopic("testTopic")
                        .payingWith("payer")
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                        .via("updateTopic"),
                validateChargedUsdWithin("updateTopic", 0.00022, 3.0));
    }

    @HapiTest
    final Stream<DynamicTest> topicDeleteFeeAsExpected() {
        return hapiTest(
                cryptoCreate("payer"),
                createTopic("testTopic").adminKeyName("payer"),
                deleteTopic("testTopic").blankMemo().payingWith("payer").via("topicDelete"),
                validateChargedUsd("topicDelete", 0.005));
    }

    @HapiTest
    final Stream<DynamicTest> getTopicFeeAsExpected() {
        return hapiTest(
                cryptoCreate("payer"),
                createTopic("testTopic").adminKeyName("payer"),
                getTopicInfo("testTopic").payingWith("payer").via("getTopic"),
                validateChargedUsd("getTopic", 0.0001));
    }
}
