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
import static com.hedera.services.bdd.spec.keys.KeyShape.PREDEFINED_SHAPE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.approveTopicAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.expectedConsensusFixedHTSFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.expectedConsensusFixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FEKL_CONTAINS_DUPLICATED_KEYS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

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
            // TOPIC_FEE_001
            final Stream<DynamicTest> createTopicWithAllKeys() {
                return hapiTest(flattened(
                        createTopic(TOPIC)
                                .adminKeyName(ADMIN_KEY)
                                .submitKeyName(SUBMIT_KEY)
                                .feeScheduleKeyName(FEE_SCHEDULE_KEY),
                        getTopicInfo(TOPIC)
                                .hasAdminKey(ADMIN_KEY)
                                .hasSubmitKey(SUBMIT_KEY)
                                .hasFeeScheduleKey(FEE_SCHEDULE_KEY)));
            }

            @HapiTest
            @DisplayName("Create topic with submitKey and feeScheduleKey")
            // TOPIC_FEE_002
            final Stream<DynamicTest> createTopicWithSubmitKeyAndFeeScheduleKey() {
                return hapiTest(
                        createTopic(TOPIC).submitKeyName(SUBMIT_KEY).feeScheduleKeyName(FEE_SCHEDULE_KEY),
                        getTopicInfo(TOPIC).hasSubmitKey(SUBMIT_KEY).hasFeeScheduleKey(FEE_SCHEDULE_KEY));
            }

            @HapiTest
            @DisplayName("Create topic with only feeScheduleKey")
            // TOPIC_FEE_003
            final Stream<DynamicTest> createTopicWithOnlyFeeScheduleKey() {
                return hapiTest(
                        createTopic(TOPIC).feeScheduleKeyName(FEE_SCHEDULE_KEY),
                        getTopicInfo(TOPIC).hasFeeScheduleKey(FEE_SCHEDULE_KEY));
            }

            @HapiTest
            @DisplayName("Create topic with 1 Hbar fixed fee")
            // TOPIC_FEE_004
            final Stream<DynamicTest> createTopicWithOneHbarFixedFee() {
                final var collector = "collector";
                return hapiTest(
                        cryptoCreate(collector),
                        createTopic(TOPIC)
                                .adminKeyName(ADMIN_KEY)
                                .submitKeyName(SUBMIT_KEY)
                                .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                                .withConsensusCustomFee(fixedConsensusHbarFee(ONE_HBAR, collector)),
                        getTopicInfo(TOPIC)
                                .hasAdminKey(ADMIN_KEY)
                                .hasSubmitKey(SUBMIT_KEY)
                                .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                                .hasCustom(expectedConsensusFixedHbarFee(ONE_HBAR, collector)));
            }

            @HapiTest
            @DisplayName("Create topic with 1 HTS fixed fee")
            // TOPIC_FEE_005
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

            @HapiTest
            @DisplayName("Create topic with 10 keys in FEKL")
            // TOPIC_FEE_020
            final Stream<DynamicTest> createTopicWithFEKL() {
                final var collector = "collector";
                return hapiTest(flattened(
                        // create 10 keys
                        newNamedKeysForFEKL(10),
                        cryptoCreate(collector),
                        createTopic(TOPIC)
                                .adminKeyName(ADMIN_KEY)
                                .submitKeyName(SUBMIT_KEY)
                                .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                                .withConsensusCustomFee(fixedConsensusHbarFee(5, collector))
                                // set list of 10 keys
                                .feeExemptKeys(feeExemptKeyNames(10)),
                        getTopicInfo(TOPIC)
                                .hasAdminKey(ADMIN_KEY)
                                .hasSubmitKey(SUBMIT_KEY)
                                .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                                // assert the list
                                .hasFeeExemptKeys(List.of(feeExemptKeyNames(10)))));
            }
        }

        @Nested
        @DisplayName("Negative scenarios")
        class TopicCreateNegativeScenarios {

            @BeforeAll
            static void beforeAll(@NonNull final TestLifecycle lifecycle) {
                lifecycle.doAdhoc(setupBaseKeys());
            }

            @HapiTest
            @DisplayName("Create topic with duplicated signatures in FEKL")
            // TOPIC_FEE_023
            final Stream<DynamicTest> createTopicWithDuplicateSignatures() {
                final var testKey = "testKey";
                return hapiTest(flattened(
                        newKeyNamed(testKey),
                        createTopic(TOPIC)
                                .adminKeyName(ADMIN_KEY)
                                .submitKeyName(SUBMIT_KEY)
                                .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                                .feeExemptKeys(testKey, testKey)
                                .hasPrecheck(FEKL_CONTAINS_DUPLICATED_KEYS)));
            }

            @HapiTest
            @DisplayName("Create topic with 0 Hbar fixed fee")
            // TOPIC_FEE_024
            final Stream<DynamicTest> createTopicWithZeroHbarFixedFee() {
                final var collector = "collector";
                return hapiTest(
                        cryptoCreate(collector),
                        createTopic(TOPIC)
                                .adminKeyName(ADMIN_KEY)
                                .submitKeyName(SUBMIT_KEY)
                                .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                                .withConsensusCustomFee(fixedConsensusHbarFee(0, collector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE));
            }

            @HapiTest
            @DisplayName("Create topic with 0 HTS fixed fee")
            // TOPIC_FEE_025
            final Stream<DynamicTest> createTopicWithZeroHTSFixedFee() {
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
                                .withConsensusCustomFee(fixedConsensusHtsFee(0, "testToken", collector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE));
            }

            @HapiTest
            @DisplayName("Create topic with invalid fee schedule key")
            // TOPIC_FEE_026
            final Stream<DynamicTest> createTopicWithInvalidFeeScheduleKey() {
                final var invalidKey = "invalidKey";
                return hapiTest(
                        withOpContext((spec, opLog) -> spec.registry().saveKey(invalidKey, STRUCTURALLY_INVALID_KEY)),
                        createTopic(TOPIC)
                                .adminKeyName(ADMIN_KEY)
                                .submitKeyName(SUBMIT_KEY)
                                .feeScheduleKeyName(invalidKey)
                                .hasKnownStatus(INVALID_CUSTOM_FEE_SCHEDULE_KEY));
            }

            @HapiTest
            @DisplayName("Create topic with custom fee and deleted collector")
            // TOPIC_FEE_028
            final Stream<DynamicTest> createTopicWithCustomFeeAndDeletedCollector() {
                final var collector = "collector";
                return hapiTest(
                        cryptoCreate(collector),
                        cryptoDelete(collector),
                        createTopic(TOPIC)
                                .adminKeyName(ADMIN_KEY)
                                .submitKeyName(SUBMIT_KEY)
                                .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                                .withConsensusCustomFee(fixedConsensusHbarFee(ONE_HBAR, collector))
                                .hasKnownStatus(ACCOUNT_DELETED));
            }
        }
    }

    @Nested
    @DisplayName("Submit message")
    class SubmitMessage {

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle lifecycle) {
            lifecycle.doAdhoc(setupBaseKeys());
        }

        @HapiTest
        @DisplayName("submit")
        final Stream<DynamicTest> submitMessage() {
            final var collector = "collector";
            final var payer = "submitter";
            final var treasury = "treasury";
            final var token = "testToken";
            final var secondToken = "secondToken";
            final var denomToken = "denomToken";
            final var simpleKey = "simpleKey";
            final var simpleKey2 = "simpleKey2";
            final var invalidKey = "invalidKey";
            final var threshKey = "threshKey";

            return hapiTest(
                    // create keys
                    newKeyNamed(invalidKey),
                    newKeyNamed(simpleKey),
                    newKeyNamed(simpleKey2),
                    newKeyNamed(threshKey)
                            .shape(threshOf(1, PREDEFINED_SHAPE, PREDEFINED_SHAPE)
                                    .signedWith(sigs(simpleKey2, simpleKey))),
                    // create accounts and denomination token
                    cryptoCreate(collector).balance(0L),
                    cryptoCreate(payer).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(treasury),
                    tokenCreate(denomToken)
                            .treasury(treasury)
                            .tokenType(TokenType.FUNGIBLE_COMMON)
                            .initialSupply(500),
                    tokenAssociate(collector, denomToken),
                    tokenAssociate(payer, denomToken),
                    tokenCreate(token)
                            .treasury(treasury)
                            .tokenType(TokenType.FUNGIBLE_COMMON)
                            .withCustom(fixedHtsFee(1, denomToken, collector))
                            .initialSupply(500),
                    tokenCreate(secondToken)
                            .treasury(treasury)
                            .tokenType(TokenType.FUNGIBLE_COMMON)
                            .initialSupply(500),
                    tokenAssociate(collector, token, secondToken),
                    tokenAssociate(payer, token, secondToken),
                    cryptoTransfer(
                            moving(2, token).between(treasury, payer),
                            moving(1, secondToken).between(treasury, payer),
                            moving(1, denomToken).between(treasury, payer)),

                    // create topic with custom fees
                    createTopic(TOPIC)
                            //                            .withConsensusCustomFee(fixedConsensusHtsFee(1, token,
                            // collector))
                            //                            .withConsensusCustomFee(fixedConsensusHtsFee(1, secondToken,
                            // collector))
                            .withConsensusCustomFee(fixedConsensusHbarFee(ONE_HBAR, collector))
                            .feeExemptKeys(threshKey)
                            .hasKnownStatus(SUCCESS),

                    // add allowance
                    approveTopicAllowance()
                            .payingWith(payer)
                            .addCryptoAllowance(payer, TOPIC, ONE_HUNDRED_HBARS, ONE_HBAR),

                    // submit message
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .signedBy(invalidKey, payer)
                            .payingWith(payer)
                            .via("submit"),

                    // check records
                    getTxnRecord("submit").andAllChildRecords().logged(),

                    // assert balances
                    getAccountBalance(collector).hasTinyBars(ONE_HBAR));
            //                            .hasTokenBalance(token, 2)
            //                            .hasTokenBalance(denomToken,1)
            //                            .hasTokenBalance(secondToken, 1),
            //                    getAccountBalance(payer)
            //                            .hasTokenBalance(token, 0)
            //                            .hasTokenBalance(secondToken, 0));
        }
    }

    @Nested
    @DisplayName("Topic approve allowance")
    class TopicApproveAllowance {

        @Nested
        @DisplayName("Positive scenarios")
        class ApproveAllowancePositiveScenarios {

            @BeforeAll
            static void beforeAll(@NonNull final TestLifecycle lifecycle) {
                lifecycle.doAdhoc(setupBaseKeys());
            }

            @HapiTest
            @DisplayName("Approve crypto allowance for topic")
            final Stream<DynamicTest> createTopicWithAllKeys() {
                return hapiTest(
                        cryptoCreate(OWNER),
                        createTopic(TOPIC)
                                .adminKeyName(ADMIN_KEY)
                                .submitKeyName(SUBMIT_KEY)
                                .feeScheduleKeyName(FEE_SCHEDULE_KEY),
                        approveTopicAllowance().payingWith(OWNER).addCryptoAllowance(OWNER, TOPIC, 100, 10));
            }
        }
    }
}
