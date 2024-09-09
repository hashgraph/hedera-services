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

package com.hedera.services.bdd.suites.hip991;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedTopicHbarFee;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

import com.hedera.services.bdd.junit.HapiTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;

public class TopicCustomFeeTest {

    @HapiTest
    @DisplayName("Create topic with all keys")
    final Stream<DynamicTest> createTopicWithAllKeys() {
        final var adminKey = "adminKey";
        final var submitKey = "submitKey";
        final var feeScheduleKey = "feeScheduleKey";
        return hapiTest(
                newKeyNamed(adminKey),
                newKeyNamed(submitKey),
                newKeyNamed(feeScheduleKey),
                newKeyNamed("firstFMK"),
                newKeyNamed("secondFMK"),
                newKeyNamed("thirdFMK"),
                newKeyNamed("fourthFMK"),
                cryptoCreate("collector"),
                createTopic("testTopic")
                        .adminKeyName(adminKey)
                        .submitKeyName(submitKey)
                        .feeScheduleKeyName(feeScheduleKey)
                        .freeMessagesKeys("firstFMK", "secondFMK", "thirdFMK"),
                getTopicInfo("testTopic")
                        .hasAdminKey(adminKey)
                        .hasSubmitKey(submitKey)
                        .hasFeeScheduleKey(feeScheduleKey)
                        .hasFreeMessagesKeys(List.of("firstFMK", "secondFMK", "thirdFMK")));
    }

    @HapiTest
    @DisplayName("Create topic with submitKey and feeScheduleKey")
    final Stream<DynamicTest> createTopicWithSubmitKeyAndFeeScheduleKey() {
        final var submitKey = "submitKey";
        final var feeScheduleKey = "feeScheduleKey";
        return hapiTest(
                newKeyNamed(submitKey),
                newKeyNamed(feeScheduleKey),
                createTopic("testTopic").submitKeyName(submitKey).feeScheduleKeyName(feeScheduleKey),
                getTopicInfo("testTopic").hasSubmitKey(submitKey).hasFeeScheduleKey(feeScheduleKey));
    }

    @HapiTest
    @DisplayName("Create topic with only feeScheduleKey")
    final Stream<DynamicTest> createTopicWithOnlyFeeScheduleKey() {
        final var feeScheduleKey = "feeScheduleKey";
        return hapiTest(
                newKeyNamed(feeScheduleKey),
                createTopic("testTopic").feeScheduleKeyName(feeScheduleKey),
                getTopicInfo("testTopic").hasFeeScheduleKey(feeScheduleKey));
    }

    @HapiTest
    @DisplayName("Create topic with 1 Hbar fixed fee")
    final Stream<DynamicTest> createTopicWithOneHbarFixedFee() {
        final var adminKey = "adminKey";
        final var submitKey = "submitKey";
        final var feeScheduleKey = "feeScheduleKey";
        final var collector = "collector";
        return hapiTest(
                newKeyNamed(adminKey),
                newKeyNamed(submitKey),
                newKeyNamed(feeScheduleKey),
                cryptoCreate(collector),
                createTopic("testTopic")
                        .adminKeyName(adminKey)
                        .submitKeyName(submitKey)
                        .feeScheduleKeyName(feeScheduleKey)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, collector)),
                // todo check if we need to sign with feeScheduleKey on create?
                getTopicInfo("testTopic")
                        .hasAdminKey(adminKey)
                        .hasSubmitKey(submitKey)
                        .hasFeeScheduleKey(feeScheduleKey)
                        .hasCustom(fixedTopicHbarFee(1, collector)));
    }

    // todo add test get info of deleted or expired-?
}
