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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.expectedConsensusFixedHTSFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.expectedConsensusFixedHbarFee;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hederahashgraph.api.proto.java.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;

@HapiTestLifecycle
@DisplayName("Topic custom fees")
public class TopicCustomFeeTest extends TopicCustomFeeBase {

    @Nested
    @DisplayName("Topic create")
    class TopicCreate {

        @Nested
        @DisplayName("Positive scenarios")
        class TopicCreatePositiveScenarios {

            @BeforeAll
            static void beforeAll(@NonNull final TestLifecycle lifecycle) {
                lifecycle.doAdhoc(setupBaseKeys());
            }

            @HapiTest
            @DisplayName("Create topic with all keys")
            final Stream<DynamicTest> createTopicWithAllKeys() {
                return hapiTest(flattened(
                        newNamedKeysForFMKL(5),
                        cryptoCreate("collector"),
                        createTopic(TOPIC)
                                .adminKeyName(ADMIN_KEY)
                                .submitKeyName(SUBMIT_KEY)
                                .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                                .freeMessagesKeys(freeMsgKeyNames(5)),
                        getTopicInfo(TOPIC)
                                .hasAdminKey(ADMIN_KEY)
                                .hasSubmitKey(SUBMIT_KEY)
                                .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                                .hasFreeMessagesKeys(List.of(freeMsgKeyNames(5)))));
            }

            @HapiTest
            @DisplayName("Create topic with submitKey and feeScheduleKey")
            final Stream<DynamicTest> createTopicWithSubmitKeyAndFeeScheduleKey() {
                return hapiTest(
                        createTopic(TOPIC).submitKeyName(SUBMIT_KEY).feeScheduleKeyName(FEE_SCHEDULE_KEY),
                        getTopicInfo(TOPIC).hasSubmitKey(SUBMIT_KEY).hasFeeScheduleKey(FEE_SCHEDULE_KEY));
            }

            @HapiTest
            @DisplayName("Create topic with only feeScheduleKey")
            final Stream<DynamicTest> createTopicWithOnlyFeeScheduleKey() {
                return hapiTest(
                        createTopic(TOPIC).feeScheduleKeyName(FEE_SCHEDULE_KEY),
                        getTopicInfo(TOPIC).hasFeeScheduleKey(FEE_SCHEDULE_KEY));
            }

            @HapiTest
            @DisplayName("Create topic with 1 Hbar fixed fee")
            final Stream<DynamicTest> createTopicWithOneHbarFixedFee() {
                final var collector = "collector";
                return hapiTest(
                        cryptoCreate(collector),
                        createTopic(TOPIC)
                                .adminKeyName(ADMIN_KEY)
                                .submitKeyName(SUBMIT_KEY)
                                .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                                .withConsensusCustomFee(fixedConsensusHbarFee(1, collector)),
                        // todo check if we need to sign with feeScheduleKey on create?
                        getTopicInfo(TOPIC)
                                .hasAdminKey(ADMIN_KEY)
                                .hasSubmitKey(SUBMIT_KEY)
                                .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                                .hasCustom(expectedConsensusFixedHbarFee(1, collector)));
            }

            @HapiTest
            @DisplayName("Create topic with 1 HTS fixed fee")
            final Stream<DynamicTest> createTopicWithOneHTSFixedFee() {
                final var collector = "collector";
                return hapiTest(
                        cryptoCreate(collector),
                        tokenCreate("testToken")
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(500),
                        tokenAssociate(collector, "testToken"),
                        createTopic(TOPIC)
                                .adminKeyName(ADMIN_KEY)
                                .submitKeyName(SUBMIT_KEY)
                                .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                                .withConsensusCustomFee(fixedConsensusHtsFee(1, "testToken", collector)),
                        getTopicInfo(TOPIC)
                                .hasAdminKey(ADMIN_KEY)
                                .hasSubmitKey(SUBMIT_KEY)
                                .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                                .hasCustom(expectedConsensusFixedHTSFee(1, "testToken", collector)));
            }
        }
    }
}
