/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTopicId;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;

import com.hedera.services.bdd.junit.HapiTest;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class TopicDeleteSuite {
    @HapiTest
    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
        return hapiTest(
                newKeyNamed("adminKey"),
                createTopic("topic").adminKeyName("adminKey"),
                submitModified(withSuccessivelyVariedBodyIds(), () -> deleteTopic("topic")));
    }

    @HapiTest
    final Stream<DynamicTest> pureCheckFails() {
        return hapiTest(
                cryptoCreate("nonTopicId"),
                deleteTopic(spec -> asTopicId(spec.registry().getAccountID("nonTopicId")))
                        .hasPrecheck(INVALID_TOPIC_ID),
                deleteTopic((String) null).hasPrecheck(INVALID_TOPIC_ID));
    }

    @HapiTest
    final Stream<DynamicTest> cannotDeleteAccountAsTopic() {
        return hapiTest(
                cryptoCreate("nonTopicId"),
                deleteTopic(spec -> asTopicId(spec.registry().getAccountID("nonTopicId")))
                        .hasKnownStatus(INVALID_TOPIC_ID));
    }

    @HapiTest
    final Stream<DynamicTest> topicIdIsValidated() {
        return hapiTest(
                deleteTopic((String) null).hasKnownStatus(INVALID_TOPIC_ID),
                deleteTopic("100.232.4534") // non-existent id
                        .hasKnownStatus(INVALID_TOPIC_ID));
    }

    @HapiTest
    final Stream<DynamicTest> noAdminKeyCannotDelete() {
        return hapiTest(createTopic("testTopic"), deleteTopic("testTopic").hasKnownStatus(UNAUTHORIZED));
    }

    @HapiTest
    final Stream<DynamicTest> deleteWithAdminKey() {
        return hapiTest(
                newKeyNamed("adminKey"),
                createTopic("testTopic").adminKeyName("adminKey"),
                deleteTopic("testTopic").hasPrecheck(ResponseCodeEnum.OK),
                getTopicInfo("testTopic").hasCostAnswerPrecheck(INVALID_TOPIC_ID));
    }

    @HapiTest
    final Stream<DynamicTest> deleteFailedWithWrongKey() {
        long PAYER_BALANCE = 1_999_999_999L;
        return hapiTest(
                newKeyNamed("adminKey"),
                newKeyNamed("wrongKey"),
                cryptoCreate("payer").balance(PAYER_BALANCE),
                createTopic("testTopic").adminKeyName("adminKey"),
                deleteTopic("testTopic")
                        .payingWith("payer")
                        .signedBy("payer", "wrongKey")
                        .hasKnownStatus(ResponseCodeEnum.INVALID_SIGNATURE));
    }

    @HapiTest
    final Stream<DynamicTest> feeAsExpected() {
        return hapiTest(
                cryptoCreate("payer"),
                createTopic("testTopic").adminKeyName("payer"),
                deleteTopic("testTopic").blankMemo().payingWith("payer").via("topicDelete"),
                validateChargedUsd("topicDelete", 0.005));
    }
}
