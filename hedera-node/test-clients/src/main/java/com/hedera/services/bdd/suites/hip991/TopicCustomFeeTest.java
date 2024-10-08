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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
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
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.expectedConsensusFixedHTSFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.expectedConsensusFixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FEKL_CONTAINS_DUPLICATED_KEYS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
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

        //        @HapiTest
        //        @DisplayName("submit")
        //        final Stream<DynamicTest> submitMessage() {
        //            final var collector = "collector";
        //            final var payer = "submitter";
        //            final var treasury = "treasury";
        //            final var token = "testToken";
        //            final var secondToken = "secondToken";
        //            final var denomToken = "denomToken";
        //            final var simpleKey = "simpleKey";
        //            final var simpleKey2 = "simpleKey2";
        //            final var invalidKey = "invalidKey";
        //            final var threshKey = "threshKey";
        //
        //            return hapiTest(
        //                    // create keys
        //                    newKeyNamed(invalidKey),
        //                    newKeyNamed(simpleKey),
        //                    newKeyNamed(simpleKey2),
        //                    newKeyNamed(threshKey)
        //                            .shape(threshOf(1, PREDEFINED_SHAPE, PREDEFINED_SHAPE)
        //                                    .signedWith(sigs(simpleKey2, simpleKey))),
        //                    // create accounts and denomination token
        //                    cryptoCreate(collector).balance(0L),
        //                    cryptoCreate(payer).balance(ONE_HUNDRED_HBARS),
        //                    cryptoCreate(treasury),
        //                    tokenCreate(denomToken)
        //                            .treasury(treasury)
        //                            .tokenType(TokenType.FUNGIBLE_COMMON)
        //                            .initialSupply(500),
        //                    tokenAssociate(collector, denomToken),
        //                    tokenAssociate(payer, denomToken),
        //                    tokenCreate(token)
        //                            .treasury(treasury)
        //                            .tokenType(TokenType.FUNGIBLE_COMMON)
        //                            .withCustom(fixedHtsFee(1, denomToken, collector))
        //                            .initialSupply(500),
        //                    tokenCreate(secondToken)
        //                            .treasury(treasury)
        //                            .tokenType(TokenType.FUNGIBLE_COMMON)
        //                            .initialSupply(500),
        //                    tokenAssociate(collector, token, secondToken),
        //                    tokenAssociate(payer, token, secondToken),
        //                    cryptoTransfer(
        //                            moving(2, token).between(treasury, payer),
        //                            moving(1, secondToken).between(treasury, payer),
        //                            moving(1, denomToken).between(treasury, payer)),
        //
        //                    // create topic with custom fees
        //                    createTopic(TOPIC)
        //                            //                            .withConsensusCustomFee(fixedConsensusHtsFee(1,
        // token,
        //                            // collector))
        //                            //                            .withConsensusCustomFee(fixedConsensusHtsFee(1,
        // secondToken,
        //                            // collector))
        //                            .withConsensusCustomFee(fixedConsensusHbarFee(ONE_HBAR, collector))
        //                            .feeExemptKeys(threshKey)
        //                            .hasKnownStatus(SUCCESS),
        //
        //                    // add allowance
        //                    approveTopicAllowance()
        //                            .payingWith(payer)
        //                            .addCryptoAllowance(payer, TOPIC, ONE_HUNDRED_HBARS, ONE_HBAR),
        //
        //                    // submit message
        //                    submitMessageTo(TOPIC)
        //                            .message("TEST")
        //                            .signedBy(invalidKey, payer)
        //                            .payingWith(payer)
        //                            .via("submit"),
        //
        //                    // check records
        //                    getTxnRecord("submit").andAllChildRecords().logged(),
        //
        //                    // assert balances
        //                    getAccountBalance(collector).hasTinyBars(ONE_HBAR));
        //            //                            .hasTokenBalance(token, 2)
        //            //                            .hasTokenBalance(denomToken,1)
        //            //                            .hasTokenBalance(secondToken, 1),
        //            //                    getAccountBalance(payer)
        //            //                            .hasTokenBalance(token, 0)
        //            //                            .hasTokenBalance(secondToken, 0));
        //        }

        @Nested
        @DisplayName("Positive scenarios")
        class SubmitMessagesPositiveScenarios {

            @BeforeAll
            static void beforeAll(@NonNull final TestLifecycle lifecycle) {
                lifecycle.doAdhoc(associateFeeTokensAndSubmitter());
            }

            @HapiTest
            @DisplayName("MessageSubmit to a public topic with a fee of 1 HBAR")
            // TOPIC_FEE_104
            final Stream<DynamicTest> messageSubmitToPublicTopicWithFee1Hbar() {
                final var collector = "collector";
                final var submitter = "submitter";
                return hapiTest(
                        cryptoCreate(collector).balance(0L),
                        cryptoCreate(submitter).balance(ONE_HUNDRED_HBARS),
                        createTopic(TOPIC).withConsensusCustomFee(fixedConsensusHbarFee(ONE_HBAR, collector)),
                        approveTopicAllowance()
                                .addCryptoAllowance(submitter, TOPIC, ONE_HUNDRED_HBARS, ONE_HBAR)
                                .payingWith(submitter),
                        submitMessageTo(TOPIC).message("TEST").payingWith(submitter),
                        getAccountBalance(collector).hasTinyBars(ONE_HBAR));
            }

            @HapiTest
            @DisplayName("MessageSubmit to a public topic with a fee of 1 FT")
            // TOPIC_FEE_105
            final Stream<DynamicTest> messageSubmitToPublicTopicWithFee1token() {
                final var collector = "collector";
                return hapiTest(
                        cryptoCreate(collector),
                        tokenAssociate(collector, BASE_TOKEN),
                        createTopic(TOPIC).withConsensusCustomFee(fixedConsensusHtsFee(1, BASE_TOKEN, collector)),
                        approveTopicAllowance()
                                .addTokenAllowance(SUBMITTER, BASE_TOKEN, TOPIC, 100, 1)
                                .payingWith(SUBMITTER),
                        submitMessageTo(TOPIC).message("TEST").payingWith(SUBMITTER),
                        getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 1));
            }

            @HapiTest
            @DisplayName("MessageSubmit to a public topic with 3 layer fee")
            // TOPIC_FEE_106
            final Stream<DynamicTest> messageSubmitToPublicTopicWith3layerFee() {
                final var topicFeeCollector = "collector";
                final var token = "token";
                final var denomToken = DENOM_TOKEN_PREFIX + token;
                final var tokenFeeCollector = COLLECTOR_PREFIX + token;
                return hapiTest(flattened(
                        // create denomination token and transfer it to the submitter
                        createTokenWith2LayerFee(SUBMITTER, token, true),
                        // create topic with multilayer fee
                        cryptoCreate(topicFeeCollector).balance(0L),
                        tokenAssociate(topicFeeCollector, token),
                        createTopic(TOPIC).withConsensusCustomFee(fixedConsensusHtsFee(1, token, topicFeeCollector)),
                        // add allowance
                        approveTopicAllowance()
                                .addTokenAllowance(SUBMITTER, token, TOPIC, 100, 1)
                                .payingWith(SUBMITTER),
                        // submit message
                        submitMessageTo(TOPIC).message("TEST").payingWith(SUBMITTER),
                        // assert topic fee collector balance
                        getAccountBalance(topicFeeCollector).hasTokenBalance(token, 1),
                        // assert token fee collector balance
                        getAccountBalance(tokenFeeCollector).hasTokenBalance(denomToken, 1).hasTinyBars(ONE_HBAR)));
            }

            @HapiTest
            @DisplayName("MessageSubmit to a public topic with 10 different 3 layer fees")
            // TOPIC_FEE_108
            final Stream<DynamicTest> messageSubmitToPublicTopicWith10different2layerFees() {
                return hapiTest(flattened(
                        // create 9 denomination tokens and transfer them to the submitter
                        createMultipleTokensWith2LayerFees(SUBMITTER, 9),
                        // create 9 collectors and associate them with tokens
                        associateAllTokensToCollectors(),
                        // create topic with 10 multilayer fees - 9 HTS + 1 HBAR
                        createTopicWith10Different2layerFees(),
                        approveTopicAllowanceForAllFees(),
                        submitMessageTo(TOPIC).message("TEST").payingWith(SUBMITTER),
                        // assert topic fee collector balance
                        assertAllCollectorsBalances()));
            }

            // TOPIC_FEE_108
            private SpecOperation[] associateAllTokensToCollectors() {
                final var collectorName = "collector_";
                final var associateTokensToCollectors = new ArrayList<SpecOperation>();
                for (int i = 0; i < 9; i++) {
                    associateTokensToCollectors.add(
                            cryptoCreate(collectorName + i).balance(0L));
                    associateTokensToCollectors.add(tokenAssociate(collectorName + i, TOKEN_PREFIX + i));
                }
                return associateTokensToCollectors.toArray(SpecOperation[]::new);
            }
            // TOPIC_FEE_108
            private SpecOperation createTopicWith10Different2layerFees() {
                final var collectorName = "collector_";
                final var topicCreateOp = createTopic(TOPIC);
                for (int i = 0; i < 9; i++) {
                    topicCreateOp.withConsensusCustomFee(fixedConsensusHtsFee(1, TOKEN_PREFIX + i, collectorName + i));
                }
                // add one hbar custom fee
                topicCreateOp.withConsensusCustomFee(fixedConsensusHbarFee(ONE_HBAR, collectorName + 0));
                return topicCreateOp;
            }
            // TOPIC_FEE_108
            private SpecOperation approveTopicAllowanceForAllFees() {
                final var approveAllowance = approveTopicAllowance().payingWith(SUBMITTER);

                for (int i = 0; i < 9; i++) {
                    approveAllowance.addTokenAllowance(SUBMITTER, TOKEN_PREFIX + i, TOPIC, 100, 1);
                }
                approveAllowance.addCryptoAllowance(SUBMITTER, TOPIC, ONE_HUNDRED_HBARS, ONE_HBAR);
                return approveAllowance;
            }
            // TOPIC_FEE_108
            private SpecOperation[] assertAllCollectorsBalances() {
                final var collectorName = "collector_";
                final var assertBalances = new ArrayList<SpecOperation>();
                // assert token balances
                for (int i = 0; i < 9; i++) {
                    assertBalances.add(getAccountBalance(collectorName + i).hasTokenBalance(TOKEN_PREFIX + i, 1));
                }
                // add assert for hbar
                assertBalances.add(getAccountBalance(collectorName + 0).hasTinyBars(ONE_HBAR));
                return assertBalances.toArray(SpecOperation[]::new);
            }

            @HapiTest
            @DisplayName("Treasury submit to a public topic with 3 layer fees")
            // TOPIC_FEE_109
            final Stream<DynamicTest> treasurySubmitToPublicTopicWith3layerFees() {
                final var topicFeeCollector = "collector";
                final var token = "token";
                final var denomToken = DENOM_TOKEN_PREFIX + token;
                final var tokenFeeCollector = COLLECTOR_PREFIX + token;

                return hapiTest(flattened(
                        // create denomination token and transfer it to the submitter
                        createTokenWith2LayerFee(SUBMITTER, token, true),
                        // create topic with multilayer fee
                        cryptoCreate(topicFeeCollector).balance(0L),
                        tokenAssociate(topicFeeCollector, token),
                        createTopic(TOPIC).withConsensusCustomFee(fixedConsensusHtsFee(1, token, topicFeeCollector)),
                        // add allowance
                        approveTopicAllowance()
                                .addTokenAllowance(SUBMITTER, token, TOPIC, 100, 1)
                                .payingWith(SUBMITTER),
                        // submit message
                        submitMessageTo(TOPIC).message("TEST").payingWith(TOKEN_TREASURY),
                        // assert topic fee collector balance
                        getAccountBalance(topicFeeCollector).hasTokenBalance(token, 0),
                        // assert token fee collector balance
                        getAccountBalance(tokenFeeCollector).hasTokenBalance(denomToken, 0).hasTinyBars(0)));
            }


            @HapiTest
            @DisplayName("Treasury second layer submit to a public topic with 3 layer fees")
            // TOPIC_FEE_110
            final Stream<DynamicTest> treasuryOfSecondLayerSubmitToPublic() {
                final var token = "token";
                final var denomToken = DENOM_TOKEN_PREFIX + token;
                final var topicFeeCollector = "topicFeeCollector";

                return hapiTest(flattened(
                        // create token and transfer it to the submitter
                        createTokenWith2LayerFee(SUBMITTER, token, true),

                        // give one token to denomToken treasury to be able to pay the fee
                        tokenAssociate(DENOM_TREASURY, token),
                        cryptoTransfer(moving(1, token).between(SUBMITTER, DENOM_TREASURY)),

                        // create topic
                        cryptoCreate(topicFeeCollector).balance(0L),
                        tokenAssociate(topicFeeCollector, token),
                        createTopic(TOPIC)
                                .withConsensusCustomFee(fixedConsensusHtsFee(1, token, topicFeeCollector)),

                        // add allowance for denomination token treasury
                        approveTopicAllowance()
                                .addTokenAllowance(DENOM_TREASURY, token, TOPIC, 100, 1)
                                .payingWith(DENOM_TREASURY),

                        // submit
                        submitMessageTo(TOPIC).message("TEST").payingWith(DENOM_TREASURY),

                        // assert topic fee collector balance
                        getAccountBalance(topicFeeCollector).hasTokenBalance(token, 1),
                        // assert token fee collector balance
                        getAccountBalance(topicFeeCollector).hasTokenBalance(denomToken, 0).hasTinyBars(0)));
            }


            @HapiTest
            @DisplayName("Collector submit to a public topic with 3 layer fees")
            // TOPIC_FEE_111
            final Stream<DynamicTest> collectorSubmitToPublicTopicWith3layerFees() {
                final var token = "token";
                final var denomToken = DENOM_TOKEN_PREFIX + token;
                final var topicFeeCollector = "topicFeeCollector";

                return hapiTest(flattened(
                        // create token and transfer it to the submitter
                        createTokenWith2LayerFee(SUBMITTER, token, true),

                        // transfer one token to the collector, to be able to pay the fee
                        cryptoCreate(topicFeeCollector).balance(ONE_HBAR),
                        tokenAssociate(topicFeeCollector, token),

                        // create topic
                        createTopic(TOPIC)
                                .withConsensusCustomFee(fixedConsensusHtsFee(1, token, topicFeeCollector)),

                        // add allowance for denomination token treasury
                        approveTopicAllowance()
                                .addTokenAllowance(topicFeeCollector, token, TOPIC, 100, 1)
                                .payingWith(topicFeeCollector),

                        // submit
                        submitMessageTo(TOPIC).message("TEST").payingWith(topicFeeCollector),

                        // assert balances
                        getAccountBalance(topicFeeCollector).hasTokenBalance(token, 0),
                        getAccountBalance(COLLECTOR_PREFIX+token).hasTokenBalance(denomToken, 0)));
            }


            @HapiTest
            @DisplayName("Collector of second layer submit to a public topic with 3 layer fees")
            // TOPIC_FEE_112
            final Stream<DynamicTest> collectorOfSecondLayerSubmitToPublicTopicWith3layerFees() {
                final var token = "token";
                final var denomToken = DENOM_TOKEN_PREFIX + token;
                final var secondLayerFeeCollector = COLLECTOR_PREFIX + token;
                final var topicFeeCollector = "topicFeeCollector";

                return hapiTest(flattened(
                        // create token and transfer it to the submitter
                        createTokenWith2LayerFee(SUBMITTER, token, true),

                        // give one token to denomToken treasury to be able to pay the fee
                        tokenAssociate(secondLayerFeeCollector, token),
                        cryptoTransfer(moving(1, token).between(SUBMITTER, secondLayerFeeCollector)),

                        // create topic
                        cryptoCreate(topicFeeCollector).balance(0L),
                        tokenAssociate(topicFeeCollector, token),
                        createTopic(TOPIC)
                                .withConsensusCustomFee(fixedConsensusHtsFee(1, token, topicFeeCollector)),

                        // add allowance for denomination token treasury
                        approveTopicAllowance()
                                .addTokenAllowance(secondLayerFeeCollector, token, TOPIC, 100, 1)
                                .payingWith(secondLayerFeeCollector),

                        // submit
                        submitMessageTo(TOPIC).message("TEST").payingWith(secondLayerFeeCollector),

                        // assert topic fee collector balance - only first layer fee should be paid
                        getAccountBalance(topicFeeCollector).hasTokenBalance(token, 1),
                        // token fee collector should have 1 token from the first transfer and 0 from msg submit
                        getAccountBalance(secondLayerFeeCollector).hasTokenBalance(denomToken, 1)));
            }

            @HapiTest
            @DisplayName("Another collector submit message to a topic with a fee")
            // TOPIC_FEE_113
            final Stream<DynamicTest> anotherCollectorSubmitMessageToATopicWithAFee() {
                final var collector = "collector";
                final var anotherToken = "anotherToken";
                final var anotherCollector = COLLECTOR_PREFIX + anotherToken;
                return hapiTest(flattened(
                        // create another token with fixed fee
                        createTokenWith2LayerFee(SUBMITTER, anotherToken, true),
                        tokenAssociate(anotherCollector, BASE_TOKEN),
                        cryptoTransfer(
                                moving(100, BASE_TOKEN).between(SUBMITTER, anotherCollector),
                                TokenMovement.movingHbar(ONE_HBAR).between(SUBMITTER, anotherCollector)
                        ),
                        // create topic
                        cryptoCreate(collector),
                        tokenAssociate(collector, BASE_TOKEN),
                        createTopic(TOPIC).withConsensusCustomFee(fixedConsensusHtsFee(1, BASE_TOKEN, collector)),
                        // add allowance and submit with another collector
                        approveTopicAllowance()
                                .addTokenAllowance(anotherCollector, BASE_TOKEN, TOPIC, 100, 1)
                                .payingWith(anotherCollector),
                        submitMessageTo(TOPIC).message("TEST").payingWith(anotherCollector),
                        // the fee was paid
                        getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 1)
                ));
            }


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
