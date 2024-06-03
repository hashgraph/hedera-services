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

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTopicId;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.FULLY_NONDETERMINISTIC;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;

import com.hedera.services.bdd.junit.HapiTest;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class TopicDeleteSuite {
    @HapiTest
    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(newKeyNamed("adminKey"))
                .when(createTopic("topic").adminKeyName("adminKey"))
                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> deleteTopic("topic")));
    }

    @HapiTest
    final Stream<DynamicTest> pureCheckFails() {
        return defaultHapiSpec("CannotDeleteAccountAsTopic")
                .given(cryptoCreate("nonTopicId"))
                .when()
                .then(
                        deleteTopic(spec -> asTopicId(spec.registry().getAccountID("nonTopicId")))
                                .hasPrecheck(INVALID_TOPIC_ID),
                        deleteTopic((String) null).hasPrecheck(INVALID_TOPIC_ID));
    }

    @HapiTest
    final Stream<DynamicTest> cannotDeleteAccountAsTopic() {
        return defaultHapiSpec("CannotDeleteAccountAsTopic")
                .given(cryptoCreate("nonTopicId"))
                .when()
                .then(deleteTopic(spec -> asTopicId(spec.registry().getAccountID("nonTopicId")))
                        .hasKnownStatus(INVALID_TOPIC_ID));
    }

    @HapiTest
    final Stream<DynamicTest> topicIdIsValidated() {
        // Fully non-deterministic for fuzzy matching because the test uses an absolute entity number (i.e.
        // 100.232.4534)
        // but fuzzy matching compares relative entity numbers
        return defaultHapiSpec("topicIdIsValidated", FULLY_NONDETERMINISTIC)
                .given()
                .when()
                .then(
                        deleteTopic((String) null).hasKnownStatus(INVALID_TOPIC_ID),
                        deleteTopic("100.232.4534") // non-existent id
                                .hasKnownStatus(INVALID_TOPIC_ID));
    }

    @HapiTest
    final Stream<DynamicTest> noAdminKeyCannotDelete() {
        return defaultHapiSpec("noAdminKeyCannotDelete")
                .given(createTopic("testTopic"))
                .when(deleteTopic("testTopic").hasKnownStatus(UNAUTHORIZED))
                .then();
    }

    @HapiTest
    final Stream<DynamicTest> deleteWithAdminKey() {
        return defaultHapiSpec("deleteWithAdminKey")
                .given(newKeyNamed("adminKey"), createTopic("testTopic").adminKeyName("adminKey"))
                .when(deleteTopic("testTopic").hasPrecheck(ResponseCodeEnum.OK))
                .then(getTopicInfo("testTopic").hasCostAnswerPrecheck(INVALID_TOPIC_ID));
    }

    @HapiTest
    final Stream<DynamicTest> deleteFailedWithWrongKey() {
        long PAYER_BALANCE = 1_999_999_999L;
        return defaultHapiSpec("deleteFailedWithWrongKey")
                .given(
                        newKeyNamed("adminKey"),
                        newKeyNamed("wrongKey"),
                        cryptoCreate("payer").balance(PAYER_BALANCE),
                        createTopic("testTopic").adminKeyName("adminKey"))
                .when(deleteTopic("testTopic")
                        .payingWith("payer")
                        .signedBy("payer", "wrongKey")
                        .hasKnownStatus(ResponseCodeEnum.INVALID_SIGNATURE))
                .then();
    }

    @HapiTest
    final Stream<DynamicTest> feeAsExpected() {
        return defaultHapiSpec("feeAsExpected")
                .given(cryptoCreate("payer"), createTopic("testTopic").adminKeyName("payer"))
                .when(deleteTopic("testTopic").blankMemo().payingWith("payer").via("topicDelete"))
                .then(validateChargedUsd("topicDelete", 0.005));
    }
}
