/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.ED25519_ON;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.keys.SigControl.SECP256K1_ON;
import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenPause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.maxCustomFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.maxHtsCustomFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createHollow;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_DENOMINATION_IN_MAX_CUSTOM_FEE_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CUSTOM_FEES_IS_NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CUSTOM_FEE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_VALID_MAX_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;

@HapiTestLifecycle
@DisplayName("Submit message")
public class TopicCustomFeeSubmitMessageTest extends TopicCustomFeeBase {

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
            final var fee = fixedConsensusHbarFee(ONE_HBAR, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(0L),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC).message("TEST").payingWith(SUBMITTER),
                    getAccountBalance(collector).hasTinyBars(ONE_HBAR));
        }

        @HapiTest
        @DisplayName("MessageSubmit to a public topic with a fee of 1 FT")
        // TOPIC_FEE_105
        final Stream<DynamicTest> messageSubmitToPublicTopicWithFee1token() {
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            return hapiTest(
                    cryptoCreate(collector),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
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
            final var fee = fixedConsensusHtsFee(1, token, topicFeeCollector);
            return hapiTest(flattened(
                    // create denomination token and transfer it to the submitter
                    createTokenWith2LayerFee(SUBMITTER, token, true),
                    // create topic with multilayer fee
                    cryptoCreate(topicFeeCollector).balance(0L),
                    tokenAssociate(topicFeeCollector, token),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    // submit message
                    submitMessageTo(TOPIC).message("TEST").payingWith(SUBMITTER),
                    // assert topic fee collector balance
                    getAccountBalance(topicFeeCollector).hasTokenBalance(token, 1),
                    // assert token fee collector balance
                    getAccountBalance(tokenFeeCollector)
                            .hasTokenBalance(denomToken, 1)
                            .hasTinyBars(ONE_HBAR)));
        }

        @HapiTest
        @DisplayName("MessageSubmit to a public topic with 10 different 3 layer fees")
        // TOPIC_FEE_108/180
        final Stream<DynamicTest> messageSubmitToPublicTopicWith10different2layerFees() {
            return hapiTest(flattened(
                    // create 9 denomination tokens and transfer them to the submitter
                    createMultipleTokensWith2LayerFees(SUBMITTER, 9),
                    // create 9 collectors and associate them with tokens
                    associateAllTokensToCollectors(),
                    // create topic with 10 multilayer fees - 9 HTS + 1 HBAR
                    createTopicWith10Different2layerFees(),
                    submitMessageTo(TOPIC).message("TEST").payingWith(SUBMITTER),
                    // assert topic fee collector balance
                    assertAllCollectorsBalances()));
        }

        // TOPIC_FEE_108
        private SpecOperation[] associateAllTokensToCollectors() {
            final var collectorName = "collector_";
            final var associateTokensToCollectors = new ArrayList<SpecOperation>();
            for (int i = 0; i < 9; i++) {
                associateTokensToCollectors.add(cryptoCreate(collectorName + i).balance(0L));
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
        @DisplayName("Validate the number of child records of the submit message transaction")
        final Stream<DynamicTest> validateNumberOfChildRecords() {
            return hapiTest(flattened(
                    // create 9 denomination tokens and transfer them to the submitter
                    createMultipleTokensWith2LayerFees(SUBMITTER, 9),
                    // create 9 collectors and associate them with tokens
                    associateAllTokensToCollectors(),
                    // create topic with 10 multilayer fees - 9 HTS + 1 HBAR
                    createTopicWith10Different2layerFees(),
                    submitMessageTo(TOPIC).message("TEST").payingWith(SUBMITTER).via("submit"),

                    // validate 0 child records
                    withOpContext((spec, log) -> {
                        final var submitTxnRecord = getTxnRecord("submit")
                                .andAllChildRecords()
                                // 3 layers * 9 HTS fees + 1 HBAR fee
                                .hasAssessedCustomFeesSize(28)
                                .logged();
                        allRunFor(spec, submitTxnRecord);
                        final var transactionRecordSize =
                                submitTxnRecord.getChildRecords().size();
                        assertEquals(0, transactionRecordSize);
                    }),
                    // assert topic fee collector balance
                    assertAllCollectorsBalances()));
        }

        @HapiTest
        @DisplayName("Treasury submit to a public topic with 3 layer fees")
        // TOPIC_FEE_109
        final Stream<DynamicTest> treasurySubmitToPublicTopicWith3layerFees() {
            final var topicFeeCollector = "collector";
            final var token = "token";
            final var denomToken = DENOM_TOKEN_PREFIX + token;
            final var tokenFeeCollector = COLLECTOR_PREFIX + token;
            final var fee = fixedConsensusHtsFee(1, token, topicFeeCollector);
            return hapiTest(flattened(
                    // create denomination token and transfer it to the submitter
                    createTokenWith2LayerFee(SUBMITTER, token, true),
                    // create topic with multilayer fee
                    cryptoCreate(topicFeeCollector).balance(0L),
                    tokenAssociate(topicFeeCollector, token),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    // submit message
                    submitMessageTo(TOPIC).message("TEST").payingWith(TOKEN_TREASURY),
                    // assert topic fee collector balance
                    getAccountBalance(topicFeeCollector).hasTokenBalance(token, 0),
                    // assert token fee collector balance
                    getAccountBalance(tokenFeeCollector)
                            .hasTokenBalance(denomToken, 0)
                            .hasTinyBars(0)));
        }

        @HapiTest
        @DisplayName("Treasury second layer submit to a public topic with 3 layer fees")
        // TOPIC_FEE_110
        final Stream<DynamicTest> treasuryOfSecondLayerSubmitToPublic() {
            final var token = "token";
            final var denomToken = DENOM_TOKEN_PREFIX + token;
            final var topicFeeCollector = "topicFeeCollector";
            final var fee = fixedConsensusHtsFee(1, token, topicFeeCollector);

            return hapiTest(flattened(
                    // create token and transfer it to the submitter
                    createTokenWith2LayerFee(SUBMITTER, token, true),

                    // give one token to denomToken treasury to be able to pay the fee
                    tokenAssociate(DENOM_TREASURY, token),
                    cryptoTransfer(moving(1, token).between(SUBMITTER, DENOM_TREASURY)),

                    // create topic
                    cryptoCreate(topicFeeCollector).balance(0L),
                    tokenAssociate(topicFeeCollector, token),
                    createTopic(TOPIC).withConsensusCustomFee(fee),

                    // submit
                    submitMessageTo(TOPIC).message("TEST").payingWith(DENOM_TREASURY),

                    // assert topic fee collector balance
                    getAccountBalance(topicFeeCollector).hasTokenBalance(token, 1),
                    // assert token fee collector balance
                    getAccountBalance(topicFeeCollector)
                            .hasTokenBalance(denomToken, 0)
                            .hasTinyBars(0)));
        }

        @HapiTest
        @DisplayName("Collector submit to a public topic with 3 layer fees")
        // TOPIC_FEE_111
        final Stream<DynamicTest> collectorSubmitToPublicTopicWith3layerFees() {
            final var token = "token";
            final var denomToken = DENOM_TOKEN_PREFIX + token;
            final var topicFeeCollector = "topicFeeCollector";
            final var fee = fixedConsensusHtsFee(1, token, topicFeeCollector);
            return hapiTest(flattened(
                    // create token and transfer it to the submitter
                    createTokenWith2LayerFee(SUBMITTER, token, true),

                    // transfer one token to the collector, to be able to pay the fee
                    cryptoCreate(topicFeeCollector).balance(ONE_HBAR),
                    tokenAssociate(topicFeeCollector, token),

                    // create topic
                    createTopic(TOPIC).withConsensusCustomFee(fee),

                    // submit
                    submitMessageTo(TOPIC).message("TEST").payingWith(topicFeeCollector),

                    // assert balances
                    getAccountBalance(topicFeeCollector).hasTokenBalance(token, 0),
                    getAccountBalance(COLLECTOR_PREFIX + token).hasTokenBalance(denomToken, 0)));
        }

        @HapiTest
        @DisplayName("Collector of second layer submit to a public topic with 3 layer fees")
        // TOPIC_FEE_112
        final Stream<DynamicTest> collectorOfSecondLayerSubmitToPublicTopicWith3layerFees() {
            final var token = "token";
            final var denomToken = DENOM_TOKEN_PREFIX + token;
            final var secondLayerFeeCollector = COLLECTOR_PREFIX + token;
            final var topicFeeCollector = "topicFeeCollector";
            final var fee = fixedConsensusHtsFee(1, token, topicFeeCollector);
            return hapiTest(flattened(
                    // create token and transfer it to the submitter
                    createTokenWith2LayerFee(SUBMITTER, token, true),

                    // give one token to denomToken treasury to be able to pay the fee
                    tokenAssociate(secondLayerFeeCollector, token),
                    cryptoTransfer(moving(1, token).between(SUBMITTER, secondLayerFeeCollector)),

                    // create topic
                    cryptoCreate(topicFeeCollector).balance(0L),
                    tokenAssociate(topicFeeCollector, token),
                    createTopic(TOPIC).withConsensusCustomFee(fee),

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
            final var fee = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            return hapiTest(flattened(
                    // create another token with fixed fee
                    createTokenWith2LayerFee(SUBMITTER, anotherToken, true),
                    tokenAssociate(anotherCollector, BASE_TOKEN),
                    cryptoTransfer(
                            moving(100, BASE_TOKEN).between(SUBMITTER, anotherCollector),
                            TokenMovement.movingHbar(ONE_HBAR).between(SUBMITTER, anotherCollector)),
                    // create topic
                    cryptoCreate(collector),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC).message("TEST").payingWith(anotherCollector),
                    // the fee was paid
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 1)));
        }

        @HapiTest
        @DisplayName("MessageSubmit to a topic with hollow account as fee collector")
        // TOPIC_FEE_116
        final Stream<DynamicTest> messageTopicSubmitToHollowAccountAsFeeCollector() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(ONE_HBAR, collector);
            return hapiTest(
                    // create hollow account with ONE_HUNDRED_HBARS
                    createHollow(1, i -> collector),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC).message("TEST").payingWith(SUBMITTER),

                    // collector should be still a hollow account
                    // and should have the initial balance + ONE_HBAR fee
                    getAccountInfo(collector).isHollow(),
                    getAccountBalance(collector).hasTinyBars(ONE_HUNDRED_HBARS + ONE_HBAR));
        }

        @HapiTest
        @DisplayName("MessageSubmit and signs with the topic’s feeScheduleKey which is listed in the FEKL list")
        // TOPIC_FEE_124
        final Stream<DynamicTest> accountMessageSubmitAndSignsWithFeeScheduleKey() {
            final var collector = "collector";
            final var feeScheduleKey = "feeScheduleKey";
            final var fee = fixedConsensusHbarFee(ONE_HBAR, collector);
            return hapiTest(
                    newKeyNamed(feeScheduleKey),
                    cryptoCreate(collector).balance(0L),
                    createTopic(TOPIC)
                            .feeScheduleKeyName(feeScheduleKey)
                            .feeExemptKeys(feeScheduleKey)
                            .withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .signedByPayerAnd(feeScheduleKey)
                            // any non payer key in FEKL, should sign with full prefixes keys
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(feeScheduleKey)),
                    getAccountBalance(collector).hasTinyBars(0L));
        }

        @HapiTest
        @DisplayName("Collector submits a message to a topic with fee of 1 FT.")
        // TOPIC_FEE_125
        final Stream<DynamicTest> collectorSubmitMessageToTopicWithFTFee() {
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC).message("TEST").payingWith(collector),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 0L));
        }

        @HapiTest
        @DisplayName("Collector submits a message to a topic with fee of 1 HBAR.")
        // TOPIC_FEE_126
        final Stream<DynamicTest> collectorSubmitMessageToTopicWithHbarFee() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(ONE_HBAR, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC).message("TEST").payingWith(collector).via("submit"),
                    // assert collector's tinyBars balance
                    withOpContext((spec, log) -> {
                        final var submitTxnRecord = getTxnRecord("submit");
                        allRunFor(spec, submitTxnRecord);
                        final var transactionTxnFee =
                                submitTxnRecord.getResponseRecord().getTransactionFee();
                        getAccountBalance(collector).hasTinyBars(ONE_HUNDRED_HBARS - transactionTxnFee);
                    }));
        }

        @DisplayName("Submit message to a topic after fee update")
        // TOPIC_FEE_126
        final Stream<DynamicTest> submitMessageAfterUpdate() {
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            final var feeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 1);
            final var updatedFee = fixedConsensusHtsFee(2, BASE_TOKEN, collector);
            final var updatedFeeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 2);
            return hapiTest(
                    newKeyNamed("adminKey"),
                    newKeyNamed("feeScheduleKey"),
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fee)
                            .adminKeyName("adminKey")
                            .feeScheduleKeyName("feeScheduleKey"),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .via("submit"),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 1),
                    updateTopic(TOPIC)
                            .withConsensusCustomFee(updatedFee)
                            .signedByPayerAnd("adminKey", "feeScheduleKey"),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(updatedFeeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .via("submit2"),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 3));
        }

        @HapiTest
        @DisplayName("Submit message to a topic after key is removed from FEKL")
        // TOPIC_FEE_129
        final Stream<DynamicTest> submitMessageAfterFEKLisRemoved() {
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            final var feeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 1);
            return hapiTest(
                    newKeyNamed("adminKey"),
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fee)
                            .adminKeyName("adminKey")
                            .feeExemptKeys(SUBMITTER),
                    submitMessageTo(TOPIC).message("TEST").payingWith(SUBMITTER).via("submit"),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 0),
                    updateTopic(TOPIC).feeExemptKeys().signedByPayerAnd("adminKey"),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .via("submit2"),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 1));
        }

        @HapiTest
        @DisplayName("Submit message to a topic after fee is added with update")
        // TOPIC_FEE_130
        final Stream<DynamicTest> submitMessageAfterFeeIsAdded() {
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            final var feeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 1);
            return hapiTest(
                    newKeyNamed("adminKey"),
                    newKeyNamed("feeScheduleKey"),
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).adminKeyName("adminKey").feeScheduleKeyName("feeScheduleKey"),
                    submitMessageTo(TOPIC).message("TEST").payingWith(SUBMITTER).via("submit"),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 0),
                    updateTopic(TOPIC).withConsensusCustomFee(fee).signedByPayerAnd("adminKey", "feeScheduleKey"),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .via("submit2"),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 1));
        }

        @HapiTest
        @DisplayName("Collector submits a message to a topic with 2 different FT fees.")
        final Stream<DynamicTest> collectorSubmitMessageToTopicWith2differentFees() {
            final var collector = "collector";
            final var secondCollector = "secondCollector";
            final var fee1 = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            final var fee1Limit = maxHtsCustomFee(collector, BASE_TOKEN, 1);
            final var fee2 = fixedConsensusHtsFee(1, SECOND_TOKEN, secondCollector);
            final var fee2Limit = maxHtsCustomFee(collector, SECOND_TOKEN, 1);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN, SECOND_TOKEN),
                    // create second collector and send second token
                    cryptoCreate(secondCollector).balance(ONE_HBAR),
                    tokenAssociate(secondCollector, SECOND_TOKEN),
                    cryptoTransfer(moving(1, SECOND_TOKEN).between(SUBMITTER, collector)),
                    // create topic with two fees
                    createTopic(TOPIC).withConsensusCustomFee(fee1).withConsensusCustomFee(fee2),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(fee1Limit)
                            .maxCustomFee(fee2Limit)
                            .message("TEST")
                            .payingWith(collector),
                    // only second fee should be paid
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 0L),
                    getAccountBalance(secondCollector).hasTokenBalance(SECOND_TOKEN, 1L));
        }

        @HapiTest
        @DisplayName("Account with key in FEKL submits a message to a topic.")
        // TOPIC_FEE_129/130/131
        final Stream<DynamicTest> accountSubmitMessageWithKeysExempt() {
            final var collector = "collector";
            final var ecdsaKey = "ecdsaKey";
            final var ed25519Key = "ed25519Key";
            final var threshKey = "threshKey";
            final var fee = fixedConsensusHbarFee(1, collector);
            final var feeLimitEcdsa = maxCustomFee("ecdsaAccount", 1);
            final var feeLimitEd25559 = maxCustomFee("ed25559Account", 1);
            final var feeLimitThresh = maxCustomFee("threshAccount", 1);
            return hapiTest(
                    newKeyNamed(ecdsaKey).shape(SECP256K1_ON),
                    newKeyNamed(ed25519Key).shape(ED25519_ON),
                    newKeyNamed(threshKey).shape(threshOf(1, SIMPLE, SIMPLE)),
                    cryptoCreate("ecdsaAccount").key(ecdsaKey),
                    cryptoCreate("ed25559Account").key(ed25519Key),
                    cryptoCreate("threshAccount").key(threshKey),
                    cryptoCreate(collector).balance(ONE_HBAR),
                    createTopic(TOPIC).withConsensusCustomFee(fee).feeExemptKeys(ecdsaKey, ed25519Key, threshKey),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimitEcdsa)
                            .message("TEST")
                            .payingWith("ecdsaAccount"),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimitEd25559)
                            .message("TEST")
                            .payingWith("ed25559Account"),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimitThresh)
                            .message("TEST")
                            .payingWith("threshAccount"),
                    getAccountBalance(collector).hasTinyBars(ONE_HBAR));
        }

        @HapiTest
        @DisplayName("Account with thresh key in FEKL submits a message to a topic.")
        // TOPIC_FEE_132
        final Stream<DynamicTest> accountSubmitMessageWithThreshKey() {
            final var collector = "collector";
            final var threshKey = "threshKey";
            final var threshShape = threshOf(1, SIMPLE, SIMPLE);
            final var oneSig = SigControl.threshSigs(1, OFF, ON);
            final var fee = fixedConsensusHbarFee(1, collector);
            final var feeLimit = maxCustomFee("payingAccount", 1);
            return hapiTest(
                    newKeyNamed(threshKey).shape(threshShape),
                    cryptoCreate(collector).balance(ONE_HBAR),
                    createTopic(TOPIC).withConsensusCustomFee(fee).feeExemptKeys(threshKey),
                    cryptoCreate("payingAccount").key(threshKey),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith("payingAccount")
                            .sigControl(forKey(threshKey, oneSig)),
                    getAccountBalance(collector).hasTinyBars(ONE_HBAR));
        }

        @HapiTest
        @DisplayName("Submits a message to a topic with fee schedule key.")
        // TOPIC_FEE_134
        final Stream<DynamicTest> submitWithFeeScheduleKey() {
            final var collector = "collector";
            final var feeScheduleKey = "feeScheduleKey";
            final var fee = fixedConsensusHbarFee(1, collector);
            final var feeLimit = maxCustomFee("payingAccount", 1);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    newKeyNamed(feeScheduleKey),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fee)
                            .feeScheduleKeyName(feeScheduleKey)
                            .feeExemptKeys(feeScheduleKey),
                    cryptoCreate("payingAccount").key(feeScheduleKey),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith("payingAccount"),
                    getAccountBalance(collector).hasTinyBars(ONE_HBAR));
        }

        @HapiTest
        @DisplayName("Submits a message to a topic with admin key.")
        // TOPIC_FEE_135
        final Stream<DynamicTest> submitWithAdminKey() {
            final var collector = "collector";
            final var adminKey = "adminKey";
            final var fee = fixedConsensusHbarFee(1, collector);
            final var feeLimit = maxCustomFee("payingAccount", 1);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    newKeyNamed(adminKey),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fee)
                            .adminKeyName(adminKey)
                            .feeExemptKeys(adminKey),
                    cryptoCreate("payingAccount").key(adminKey),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith("payingAccount"),
                    getAccountBalance(collector).hasTinyBars(ONE_HBAR));
        }

        @HapiTest
        @DisplayName("Submits a message to a topic without max custom fee")
        // TOPIC_FEE_136
        final Stream<DynamicTest> submitWithNoMaxCustomFee() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(1, collector);
            final var feeLimit = maxHtsCustomFee("payingAccount", BASE_TOKEN, 1);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    cryptoCreate("payingAccount"),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith("payingAccount")
                            .hasKnownStatus(NO_VALID_MAX_CUSTOM_FEE),
                    getAccountBalance(collector).hasTinyBars(ONE_HBAR));
        }

        @HapiTest
        @DisplayName("Submits a message to a topic with 2 FT fees available only 1 fee")
        // TOPIC_FEE_137/138
        final Stream<DynamicTest> submitToTopicWithTwoFeesOnlyOneAvailable() {
            final var collector = "collector";
            final var ftA = "Fungible_Token_A";
            final var ftB = "Fungible_Token_B";
            final var firstFee = fixedConsensusHtsFee(1, ftA, collector);
            final var firstFeeLimit = maxHtsCustomFee("treasuryA", ftA, 1);
            final var secondFee = fixedConsensusHtsFee(1, ftB, collector);
            final var secondFeeLimit = maxHtsCustomFee("treasuryA", ftB, 1);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    cryptoCreate("treasuryA").balance(ONE_HBAR),
                    cryptoCreate("treasuryB").balance(ONE_HBAR),
                    tokenCreate(ftA).initialSupply(2).treasury("treasuryA"),
                    tokenCreate(ftB).initialSupply(2).treasury("treasuryB"),
                    tokenAssociate(collector, ftA, ftB),
                    tokenAssociate("treasuryA", ftB),
                    createTopic(TOPIC).withConsensusCustomFee(firstFee).withConsensusCustomFee(secondFee),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(firstFeeLimit)
                            .maxCustomFee(secondFeeLimit)
                            .message("TEST")
                            .payingWith("treasuryA")
                            .hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE),
                    getAccountBalance(collector).hasTokenBalance(ftA, 0),
                    getAccountBalance(collector).hasTokenBalance(ftB, 0));
        }

        @HapiTest
        @DisplayName("Submits a message to a topic not enough max custom fee")
        // TOPIC_FEE_139
        final Stream<DynamicTest> submitWithNotEnoughMaxCustomFee() {
            final var collector = "collector";
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(fixedConsensusHtsFee(2, BASE_TOKEN, collector)),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 1))
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(MAX_CUSTOM_FEE_LIMIT_EXCEEDED),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 0));
        }

        @HapiTest
        @DisplayName("Submits a message to a topic not enough of a single FT in max custom fee")
        // TOPIC_FEE_140
        final Stream<DynamicTest> submitWithNotEnoughSingleFtInMaxCustomFee() {
            final var collector = "collector";
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN, SECOND_TOKEN),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fixedConsensusHtsFee(2, BASE_TOKEN, collector))
                            .withConsensusCustomFee(fixedConsensusHtsFee(2, SECOND_TOKEN, collector)),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 1))
                            .maxCustomFee(maxHtsCustomFee(SUBMITTER, SECOND_TOKEN, 2))
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(MAX_CUSTOM_FEE_LIMIT_EXCEEDED),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 0),
                    getAccountBalance(collector).hasTokenBalance(SECOND_TOKEN, 0));
        }

        @HapiTest
        @DisplayName("Submits a message to a topic with custom fee FT with 4 layer fees")
        // TOPIC_FEE_144
        final Stream<DynamicTest> submitToTopicWithFourLayersOfFees() {
            final var collector = "collector";
            return hapiTest(flattened(
                    createTokenWith4LayerFee(SUBMITTER, "fourLayerToken", true),
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, "fourLayerToken"),
                    createTopic(TOPIC).withConsensusCustomFee(fixedConsensusHtsFee(1, "fourLayerToken", collector)),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(maxHtsCustomFee(SUBMITTER, "fourLayerToken", 1))
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH),
                    getAccountBalance(collector).hasTokenBalance("fourLayerToken", 0)));
        }

        @HapiTest
        @DisplayName("Submits a message to a topic when fee collector is deleted")
        // TOPIC_FEE_145
        final Stream<DynamicTest> submitToTopicWithDeletedCollector() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(1, collector);
            final var feeLimit = maxCustomFee(SUBMITTER, 1);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    cryptoDelete(collector),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(ACCOUNT_DELETED));
        }

        @HapiTest
        @DisplayName("Submit to topic with max fee above topic fee")
        // TOPIC_FEE_179
        final Stream<DynamicTest> submitToTopicWithMaxFeeAboveTopicFee() {
            final var collector = "collector";
            final var tokenFee = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            final var hbarFee = fixedConsensusHbarFee(1, collector);
            final var tokenFeeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 2);
            final var hbarFeeLimit = maxCustomFee(SUBMITTER, 2);
            return hapiTest(
                    cryptoCreate(collector).balance(0L),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(tokenFee).withConsensusCustomFee(hbarFee),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(tokenFeeLimit)
                            .maxCustomFee(hbarFeeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 1),
                    getAccountBalance(collector).hasTinyBars(1));
        }

        @HapiTest
        @DisplayName("MessageSubmit to a public topic with one 3-layer custom fee")
        // TOPIC_FEE_181/182
        final Stream<DynamicTest> messageSubmitToPublicTopicWithOne3LayerCustomFee() {
            final var tokenA = "tokenA";
            final var collector = COLLECTOR_PREFIX + tokenA;
            final var tokenADenom = DENOM_TOKEN_PREFIX + tokenA;

            final var feeA = fixedConsensusHtsFee(1, tokenA, collector);
            final var feeAlimit = maxHtsCustomFee("submitter", tokenA, 2);
            final var feeLimitDenom = maxHtsCustomFee("submitter", tokenADenom, 1);
            final var hbarLimitDenom = maxCustomFee("submitter", 1);

            return hapiTest(flattened(
                    createTokenWith2LayerFee("submitter", tokenA, true, 2, 2L),
                    tokenAssociate(collector, tokenA),
                    createTopic(TOPIC).withConsensusCustomFee(feeA),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeAlimit)
                            .maxCustomFee(feeLimitDenom)
                            .maxCustomFee(hbarLimitDenom)
                            .message("TEST")
                            .payingWith("submitter"),
                    getAccountBalance(collector).hasTokenBalance(tokenA, 1),
                    getAccountBalance(collector).hasTokenBalance(tokenADenom, 2),
                    getAccountBalance(collector).hasTinyBars(2)));
        }

        @HapiTest
        @DisplayName("MessageSubmit limit above balance")
        // TOPIC_FEE_183/184
        final Stream<DynamicTest> messageSubmitLimitAboveBalance() {
            final var collector = "collector";
            final var tokenFee = fixedConsensusHtsFee(10, BASE_TOKEN, collector);
            final var tokenFeeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 20);
            final var hbarFee = fixedConsensusHbarFee(10, collector);
            final var hbarFeeLimit = maxCustomFee(SUBMITTER, 20);

            return hapiTest(
                    cryptoCreate(collector).balance(0L),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(tokenFee).withConsensusCustomFee(hbarFee),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(tokenFeeLimit)
                            .maxCustomFee(hbarFeeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 10),
                    getAccountBalance(collector).hasTinyBars(10));
        }

        @HapiTest
        @DisplayName(
                "MessageSubmit to a topic with no custom fees and provide a list of max_custom_fee for valid tokens")
        // TOPIC_FEE_185
        final Stream<DynamicTest> messageSubmitToTopicWithNoCustomFees() {
            return hapiTest(
                    tokenCreate("tokenA"),
                    cryptoCreate(COLLECTOR),
                    createTopic(TOPIC),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(maxHtsCustomFee(SUBMITTER, "tokenA", 1))
                            .message("TEST")
                            .payingWith(SUBMITTER),
                    getAccountBalance(COLLECTOR).hasTokenBalance("tokenA", 0));
        }

        @HapiTest
        @DisplayName("MessageSubmit to a topic with no custom fees and payer not associated")
        // TOPIC_FEE_186/187/189
        final Stream<DynamicTest> messageSubmitToTopicPayerNotAssociated() {
            return hapiTest(
                    tokenCreate("tokenA"),
                    tokenCreate("tokenB"),
                    cryptoCreate(COLLECTOR),
                    tokenAssociate(COLLECTOR, "tokenA"),
                    cryptoCreate("sender"),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fixedConsensusHtsFee(1, "tokenA", COLLECTOR))
                            .feeExemptKeys(SUBMITTER),
                    submitMessageTo(TOPIC).message("TEST").payingWith(SUBMITTER),
                    getAccountBalance(COLLECTOR).hasTokenBalance("tokenA", 0),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .maxCustomFee(maxHtsCustomFee("sender", "tokenB", 1))
                            .payingWith("sender")
                            .signedBy("sender", SUBMITTER)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SUBMITTER)),
                    getAccountBalance(COLLECTOR).hasTokenBalance("tokenA", 0));
        }

        @HapiTest
        @DisplayName("MessageSubmit to a topic with max custom fee invalid token")
        // TOPIC_FEE_190
        final Stream<DynamicTest> submitMessageWithMaxCustomFeeInvalidToken() {
            return hapiTest(
                    withOpContext((spec, opLog) -> {
                        spec.registry().saveTokenId("invalidToken", TokenID.getDefaultInstance());
                    }),
                    cryptoCreate(COLLECTOR),
                    createTopic(TOPIC).feeExemptKeys(SUBMITTER),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .maxCustomFee(maxHtsCustomFee(SUBMITTER, "invalidToken", 1))
                            .payingWith(SUBMITTER),
                    getAccountBalance(COLLECTOR).hasTokenBalance("invalidToken", 0));
        }

        @HapiTest
        @DisplayName("MessageSubmit to a topic with no custom fees and payer frozen and exempt")
        // TOPIC_FEE_191
        final Stream<DynamicTest> messageSubmitPayerFrozenAndExempt() {
            return hapiTest(
                    newKeyNamed("freezeKey"),
                    tokenCreate("tokenA").freezeKey("freezeKey"),
                    cryptoCreate(COLLECTOR),
                    tokenAssociate(COLLECTOR, "tokenA"),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fixedConsensusHtsFee(1, "tokenA", COLLECTOR))
                            .feeExemptKeys(SUBMITTER),
                    tokenAssociate(SUBMITTER, "tokenA"),
                    tokenFreeze("tokenA", SUBMITTER),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .maxCustomFee(maxHtsCustomFee(SUBMITTER, "tokenA", 2))
                            .payingWith(SUBMITTER),
                    getAccountBalance(COLLECTOR).hasTokenBalance("tokenA", 0));
        }

        @HapiTest
        @DisplayName("SubmitMessage to a topic with a custom fee of 1 FT A and 1 HBAR and accept_all_custom_fees=true")
        // TOPIC_FEE_192/193
        final Stream<DynamicTest> submitMessageToTopicWithCustomFeesAndAcceptAllCustomFees() {
            final var collector = "collector";
            final var tokenA = "tokenA";
            final var tokenFee = fixedConsensusHtsFee(1, tokenA, collector);
            final var hbarFee = fixedConsensusHbarFee(1, collector);

            return hapiTest(
                    cryptoCreate(collector).balance(0L),
                    tokenCreate(tokenA).treasury(TOKEN_TREASURY),
                    tokenAssociate(collector, tokenA),
                    createTopic(TOPIC).withConsensusCustomFee(tokenFee).withConsensusCustomFee(hbarFee),
                    createTopic("noFeesTopic"),
                    cryptoCreate("sender").balance(ONE_HBAR),
                    tokenAssociate("sender", tokenA),
                    cryptoTransfer(moving(10, tokenA).between(TOKEN_TREASURY, "sender")),
                    submitMessageTo(TOPIC).message("TEST").payingWith("sender"),
                    // Assert collector balances
                    getAccountBalance(collector).hasTokenBalance(tokenA, 1),
                    getAccountBalance(collector).hasTinyBars(1),
                    submitMessageTo("noFeesTopic").message("TEST").payingWith("sender"),
                    getAccountBalance(collector).hasTokenBalance(tokenA, 1),
                    getAccountBalance(collector).hasTinyBars(1));
        }

        @HapiTest
        @DisplayName("MessageSubmit to a topic from a fee collector")
        // TOPIC_FEE_199
        final Stream<DynamicTest> submitMessageFromFeeCollector() {
            final var fee = fixedConsensusHtsFee(5, BASE_TOKEN, COLLECTOR);
            return hapiTest(
                    withOpContext((spec, opLog) -> {
                        spec.registry().saveTokenId("invalidToken", TokenID.getDefaultInstance());
                    }),
                    tokenCreate("tokenA"),
                    cryptoCreate(COLLECTOR),
                    tokenAssociate(COLLECTOR, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .maxCustomFee(maxHtsCustomFee(COLLECTOR, "invalidToken", 1))
                            .maxCustomFee(maxHtsCustomFee(COLLECTOR, BASE_TOKEN, 1))
                            .maxCustomFee(maxHtsCustomFee(COLLECTOR, "tokenA", 1))
                            .payingWith(COLLECTOR)
                            .hasKnownStatus(SUCCESS));
        }

        @HapiTest
        @DisplayName("Test duplicate denominations in max custom fee list")
        final Stream<DynamicTest> testDuplicateDenomInMaxCustomFee() {
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(2, BASE_TOKEN, collector);
            final var feeLimit1 = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 2);
            final var feeLimit2 = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 1);
            final var hbarFee2 = fixedConsensusHbarFee(2, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(fee).withConsensusCustomFee(hbarFee2),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit1)
                            .maxCustomFee(feeLimit2)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasPrecheck(ResponseCodeEnum.DUPLICATE_DENOMINATION_IN_MAX_CUSTOM_FEE_LIST));
        }

        @HapiTest
        @DisplayName("Test multiple fees with same denomination")
        final Stream<DynamicTest> multipleFeesSameDenom() {
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(2, BASE_TOKEN, collector);
            final var fee1 = fixedConsensusHtsFee(1, BASE_TOKEN, collector);

            final var feeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 1);
            final var correctFeeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 3);

            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(fee).withConsensusCustomFee(fee1),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(ResponseCodeEnum.MAX_CUSTOM_FEE_LIMIT_EXCEEDED),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(correctFeeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER));
        }

        @HapiTest
        @DisplayName("Test multiple hbar fees with")
        final Stream<DynamicTest> multipleHbarFees() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(2, collector);
            final var fee1 = fixedConsensusHbarFee(1, collector);

            final var feeLimit = maxCustomFee(SUBMITTER, 2);
            final var correctFeeLimit = maxCustomFee(SUBMITTER, 3);

            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(fee).withConsensusCustomFee(fee1),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(ResponseCodeEnum.MAX_CUSTOM_FEE_LIMIT_EXCEEDED),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(correctFeeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER));
        }

        @HapiTest
        // TOPIC_FEE_177
        @DisplayName("Submits messages with account excluded and included from FEKL")
        final Stream<DynamicTest> submitWithoutAndWithFEKL() {
            final var alice = "alice";
            final var collector = "collector";
            final var topicAdmin = "topicAdmin";

            final var fee1 = fixedConsensusHtsFee(1, BASE_TOKEN, collector);

            return hapiTest(
                    cryptoCreate(alice),
                    cryptoCreate(collector),
                    newKeyNamed(topicAdmin),
                    tokenAssociate(alice, BASE_TOKEN),
                    tokenAssociate(collector, BASE_TOKEN),
                    cryptoTransfer(moving(1, BASE_TOKEN).between(SUBMITTER, alice))
                            .signedByPayerAnd(SUBMITTER),

                    // Create a topic without alice in the fee exempt key list and verify that she pays
                    createTopic(TOPIC).withConsensusCustomFee(fee1).adminKeyName(topicAdmin),
                    submitMessageTo(TOPIC).message("TEST").payingWith(alice),
                    getAccountBalance(alice).hasTokenBalance(BASE_TOKEN, 0),

                    // Add alice to the fee exempt key list and verify that she doesn't pay
                    updateTopic(TOPIC).feeExemptKeys(alice).signedByPayerAnd(topicAdmin),
                    // even though alice doesn't have any tokens, the transaction should still be successful
                    submitMessageTo(TOPIC).message("TEST").payingWith(alice),
                    getAccountBalance(alice).hasTokenBalance(BASE_TOKEN, 0));
        }

        @HapiTest
        // TOPIC_FEE_178
        @DisplayName("Submits messages with account included and excluded from FEKL")
        final Stream<DynamicTest> submitWithAndWithoutFEKL() {
            final var alice = "alice";
            final var collector = "collector";
            final var topicAdmin = "topicAdmin";

            final var fee1 = fixedConsensusHtsFee(1, BASE_TOKEN, collector);

            return hapiTest(
                    cryptoCreate(alice),
                    cryptoCreate(collector),
                    newKeyNamed(topicAdmin),
                    tokenAssociate(alice, BASE_TOKEN),
                    tokenAssociate(collector, BASE_TOKEN),
                    cryptoTransfer(moving(1, BASE_TOKEN).between(SUBMITTER, alice))
                            .signedByPayerAnd(SUBMITTER),

                    // Add alice to the fee exempt key list and verify that she doesn't pay
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fee1)
                            .feeExemptKeys(alice)
                            .adminKeyName(topicAdmin),
                    submitMessageTo(TOPIC).message("TEST").payingWith(alice),
                    getAccountBalance(alice).hasTokenBalance(BASE_TOKEN, 1),

                    // Remove alice from the fee exempt key list and verify that she pays
                    updateTopic(TOPIC).withEmptyFeeExemptKeyList().signedByPayerAnd(topicAdmin),
                    submitMessageTo(TOPIC).message("TEST").payingWith(alice),
                    getAccountBalance(alice).hasTokenBalance(BASE_TOKEN, 0));
        }

        @HapiTest
        // TOPIC_FEE_250
        @DisplayName("Submitter as collector pays fees with no max fee limit")
        final Stream<DynamicTest> multipleCustomFeesWithSenderAsCollectorWithAcceptAllCustomFees() {
            final var alice = "alice";
            final var bob = "bob";
            final var fee1 = fixedConsensusHtsFee(1, BASE_TOKEN, alice);
            final var fee2 = fixedConsensusHtsFee(2, BASE_TOKEN, bob);
            return hapiTest(
                    cryptoCreate(alice).balance(ONE_HBAR),
                    cryptoCreate(bob).balance(0L),
                    tokenAssociate(alice, BASE_TOKEN),
                    tokenAssociate(bob, BASE_TOKEN),
                    cryptoTransfer(moving(2, BASE_TOKEN).between(SUBMITTER, alice)),
                    createTopic(TOPIC).withConsensusCustomFee(fee1).withConsensusCustomFee(fee2),
                    submitMessageTo(TOPIC).message("TEST").payingWith(alice),
                    getAccountBalance(alice).hasTokenBalance(BASE_TOKEN, 0),

                    // Verifying that alice pays bob but not herself
                    getAccountBalance(alice).hasTokenBalance(BASE_TOKEN, 0L),
                    getAccountBalance(bob).hasTokenBalance(BASE_TOKEN, 2L));
        }

        @HapiTest
        // TOPIC_FEE_251
        @DisplayName("Submitter as collector pays fees with max fee limit")
        final Stream<DynamicTest> multipleCustomFeesWithSenderAsCollectorWithAcceptAllCustomFeesAndMaxFee() {
            final var alice = "alice";
            final var bob = "bob";
            final var fee1 = fixedConsensusHtsFee(1, BASE_TOKEN, alice);
            final var fee2 = fixedConsensusHtsFee(2, BASE_TOKEN, bob);
            final var feeLimit = maxHtsCustomFee(alice, BASE_TOKEN, 2);
            return hapiTest(
                    cryptoCreate(alice).balance(ONE_HBAR),
                    cryptoCreate(bob).balance(0L),
                    tokenAssociate(alice, BASE_TOKEN),
                    tokenAssociate(bob, BASE_TOKEN),
                    cryptoTransfer(moving(2, BASE_TOKEN).between(SUBMITTER, alice)),
                    createTopic(TOPIC).withConsensusCustomFee(fee1).withConsensusCustomFee(fee2),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith(alice),

                    // Verifying that alice pays bob but not herself
                    getAccountBalance(alice).hasTokenBalance(BASE_TOKEN, 0L),
                    getAccountBalance(bob).hasTokenBalance(BASE_TOKEN, 2L));
        }

        @HapiTest
        // TOPIC_FEE_253
        @DisplayName("Submitter as collector have enough balance to pay")
        final Stream<DynamicTest> submitHaveEnoughBalanceToPayWithSubmitterAsCollector() {
            final var alice = "alice";
            final var bob = "bob";
            final var dave = "dave";
            final var fee1 = fixedConsensusHtsFee(1, BASE_TOKEN, alice);
            final var fee2 = fixedConsensusHtsFee(2, BASE_TOKEN, bob);
            final var fee3 = fixedConsensusHtsFee(3, BASE_TOKEN, dave);
            return hapiTest(
                    cryptoCreate(alice).balance(ONE_HBAR),
                    cryptoCreate(bob).balance(0L),
                    cryptoCreate(dave).balance(0L),
                    tokenAssociate(alice, BASE_TOKEN),
                    tokenAssociate(bob, BASE_TOKEN),
                    tokenAssociate(dave, BASE_TOKEN),
                    cryptoTransfer(moving(5, BASE_TOKEN).between(SUBMITTER, alice)),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fee1)
                            .withConsensusCustomFee(fee2)
                            .withConsensusCustomFee(fee3),
                    submitMessageTo(TOPIC).message("TEST").payingWith(alice),
                    // Verifying that alice pays bob but not herself
                    getAccountBalance(alice).hasTokenBalance(BASE_TOKEN, 0L),
                    getAccountBalance(bob).hasTokenBalance(BASE_TOKEN, 2L),
                    getAccountBalance(dave).hasTokenBalance(BASE_TOKEN, 3L));
        }
    }

    @Nested
    @DisplayName("Negative scenarios")
    class SubmitMessagesNegativeScenarios {

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle lifecycle) {
            lifecycle.doAdhoc(associateFeeTokensAndSubmitter());
        }

        @HapiTest
        // TOPIC_FEE_179
        @DisplayName("Collector submits a message to a deleted topic.")
        final Stream<DynamicTest> submitMessageToDeletedTopic() {
            final var collector = "collector";
            final var admin = "admin";
            final var fee1 = fixedConsensusHtsFee(1, BASE_TOKEN, collector);

            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    newKeyNamed(admin),
                    createTopic(TOPIC).withConsensusCustomFee(fee1).adminKeyName(admin),
                    deleteTopic(TOPIC).signedByPayerAnd(admin),
                    submitMessageTo(TOPIC).message("TEST").hasKnownStatus(ResponseCodeEnum.INVALID_TOPIC_ID));
        }

        @HapiTest
        @DisplayName("Submitter has insufficient hbar balance")
        // TOPIC_FEE_158
        final Stream<DynamicTest> submitterHasInsufficientHbarBalance() {
            final var collector = "collector";
            final var submitterWithLowBalance = "submitterWithLowBalance";
            final var fee = fixedConsensusHbarFee(ONE_HUNDRED_HBARS, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(0L),
                    cryptoCreate(submitterWithLowBalance).balance(ONE_HBAR),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .payingWith(submitterWithLowBalance)
                            .hasKnownStatus(INSUFFICIENT_ACCOUNT_BALANCE));
        }

        @HapiTest
        @DisplayName("Submitter has insufficient token balance")
        // TOPIC_FEE_159
        final Stream<DynamicTest> submitterHasInsufficientTokenBalance() {
            final var collector = "collector";
            final var submitterWithLowBalance = "submitterWithLowBalance";
            final var fee = fixedConsensusHtsFee(20, BASE_TOKEN, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(0L),
                    tokenAssociate(collector, BASE_TOKEN),
                    // create submitter and transfer only one token
                    cryptoCreate(submitterWithLowBalance)
                            .maxAutomaticTokenAssociations(-1)
                            .balance(ONE_HBAR),
                    // create topic and submit
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .payingWith(submitterWithLowBalance)
                            .hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE));
        }

        @HapiTest
        @DisplayName("Submitter is not associated to the fee token")
        // TOPIC_FEE_160
        final Stream<DynamicTest> submitterIsNotAssociatedToFeeToken() {
            final var collector = "collector";
            final var submitterWithNoAssociation = "submitterWithNoAssociation";
            final var fee = fixedConsensusHtsFee(20, BASE_TOKEN, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(0L),
                    tokenAssociate(collector, BASE_TOKEN),
                    // create submitter and transfer only one token
                    cryptoCreate(submitterWithNoAssociation).balance(ONE_HBAR),
                    // create topic and submit
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .payingWith(submitterWithNoAssociation)
                            .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
        }

        @HapiTest
        @DisplayName("Message submit by collector with 0 Hbar balance")
        // TOPIC_FEE_161
        final Stream<DynamicTest> messageSubmitByCollectorWith0HbarBalance() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(ONE_HUNDRED_HBARS, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(0L),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .payingWith(collector)
                            .hasPrecheck(INSUFFICIENT_PAYER_BALANCE));
        }

        @HapiTest
        @DisplayName("Message submit by treasury with 0 Hbar balance")
        // TOPIC_FEE_162
        final Stream<DynamicTest> messageSubmitByTreasuryWith0HbarBalance() {
            final var collector = "collector";
            final var treasury = "treasury";
            final var fee = fixedConsensusHtsFee(1, "token", collector);
            return hapiTest(
                    // create treasury and token for the fee
                    cryptoCreate(treasury).balance(0L),
                    tokenCreate("token")
                            .tokenType(TokenType.FUNGIBLE_COMMON)
                            .treasury(treasury)
                            .initialSupply(50),
                    // create collector account and associate it to token
                    cryptoCreate(collector).balance(0L),
                    tokenAssociate(collector, "token"),
                    // create topic and submit a message
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .payingWith(treasury)
                            .hasPrecheck(INSUFFICIENT_PAYER_BALANCE));
        }

        @HapiTest
        @DisplayName("Multiple fees with same denomination")
        // TOPIC_FEE_175/177/200/202/204/206
        final Stream<DynamicTest> multipleFeesSameDenom() {
            final var collector = "collector";
            final var fee1 = fixedConsensusHtsFee(2, BASE_TOKEN, collector);
            final var fee2 = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            final var fee3 = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            final var feeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 2);
            final var correctFeeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 4);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fee1)
                            .withConsensusCustomFee(fee2)
                            .withConsensusCustomFee(fee3),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(MAX_CUSTOM_FEE_LIMIT_EXCEEDED),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(correctFeeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER));
        }

        @HapiTest
        @DisplayName("Submit message to a private topic no max custom fee")
        // TOPIC_FEE_170
        final Stream<DynamicTest> submitMessageToPrivateNoMaxCustomFee() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(ONE_HBAR, collector);
            final var feeLimit = maxHtsCustomFee("submitter", BASE_TOKEN, 1);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    newKeyNamed(SUBMIT_KEY),
                    createTopic(TOPIC).submitKeyName(SUBMIT_KEY).withConsensusCustomFee(fee),
                    cryptoCreate("submitter").balance(ONE_HBAR).key(SUBMIT_KEY),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith("submitter")
                            .hasKnownStatus(NO_VALID_MAX_CUSTOM_FEE));
        }

        @HapiTest
        @DisplayName("Submit message to a private topic not enough balance")
        // TOPIC_FEE_171
        final Stream<DynamicTest> submitMessageToPrivateNotEnoughBalance() {
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(5, BASE_TOKEN, collector);
            final var feeLimit = maxHtsCustomFee("submitter", BASE_TOKEN, 5);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    newKeyNamed(SUBMIT_KEY),
                    createTopic(TOPIC).submitKeyName(SUBMIT_KEY).withConsensusCustomFee(fee),
                    cryptoCreate("submitter").balance(ONE_HBAR).key(SUBMIT_KEY),
                    tokenAssociate("submitter", BASE_TOKEN),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith("submitter")
                            .hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE));
        }

        @HapiTest
        @DisplayName("Submit message to a private no submit key")
        // TOPIC_FEE_172/173
        final Stream<DynamicTest> submitMessageToPrivateNoSubmitKey() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(ONE_HBAR, collector);
            final var feeLimit = maxCustomFee("submitter", 1);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    newKeyNamed(SUBMIT_KEY),
                    newKeyNamed("key"),
                    cryptoCreate("submitter").balance(ONE_HUNDRED_HBARS).key("key"),
                    createTopic(TOPIC)
                            .feeExemptKeys("key")
                            .submitKeyName(SUBMIT_KEY)
                            .withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith("submitter")
                            .signedBy("submitter")
                            .hasKnownStatus(INVALID_SIGNATURE));
        }

        @HapiTest
        @DisplayName("Multiple fees with same denomination and different collectors")
        // TOPIC_FEE_176/178/201/203/205/207
        final Stream<DynamicTest> multipleFeesSameDenomDifferentCollectors() {
            final var collector1 = "collector1";
            final var collector2 = "collector2";
            final var collector3 = "collector3";
            final var fee1 = fixedConsensusHtsFee(2, BASE_TOKEN, collector1);
            final var fee2 = fixedConsensusHtsFee(1, BASE_TOKEN, collector2);
            final var fee3 = fixedConsensusHtsFee(2, BASE_TOKEN, collector3);
            final var feeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 2);
            final var correctFeeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 5);
            return hapiTest(
                    cryptoCreate(collector1).balance(ONE_HBAR),
                    tokenAssociate(collector1, BASE_TOKEN),
                    cryptoCreate(collector2).balance(ONE_HBAR),
                    tokenAssociate(collector2, BASE_TOKEN),
                    cryptoCreate(collector3).balance(ONE_HBAR),
                    tokenAssociate(collector3, BASE_TOKEN),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fee1)
                            .withConsensusCustomFee(fee2)
                            .withConsensusCustomFee(fee3),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(MAX_CUSTOM_FEE_LIMIT_EXCEEDED),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(correctFeeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER));
        }

        @HapiTest
        @DisplayName("Submit to a topic when max_custom_fee is not enough")
        // TOPIC_FEE_208/209
        final Stream<DynamicTest> submitToTopicMaxLimitNotEnough() {
            final var collector = "collector";
            final var tokenFee = fixedConsensusHtsFee(2, BASE_TOKEN, collector);
            final var hbarFee = fixedConsensusHbarFee(2, collector);
            final var tokenFeeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 1);
            final var hbarFeeLimit = maxCustomFee(SUBMITTER, 1);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic("tokenTopic").withConsensusCustomFee(tokenFee),
                    createTopic("hbarTopic").withConsensusCustomFee(hbarFee),
                    submitMessageTo("tokenTopic")
                            .maxCustomFee(tokenFeeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(MAX_CUSTOM_FEE_LIMIT_EXCEEDED),
                    submitMessageTo("hbarTopic")
                            .maxCustomFee(hbarFeeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(MAX_CUSTOM_FEE_LIMIT_EXCEEDED));
        }

        @HapiTest
        @DisplayName("Submit to a topic with multiple fees with not enough balance")
        // TOPIC_FEE_210/226/227
        final Stream<DynamicTest> submitToTopicMultipleFeesNotEnoughBalance() {
            final var collector = "collector";
            final var tokenFee = fixedConsensusHtsFee(2, BASE_TOKEN, collector);
            final var hbarFee = fixedConsensusHbarFee(2, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    cryptoCreate("sender").balance(ONE_HBAR),
                    tokenAssociate("sender", BASE_TOKEN),
                    cryptoCreate("poorSender").balance(0L),
                    tokenAssociate("poorSender", BASE_TOKEN),
                    cryptoTransfer(moving(5, BASE_TOKEN).between(TOKEN_TREASURY, "poorSender")),
                    createTopic(TOPIC).withConsensusCustomFee(hbarFee).withConsensusCustomFee(tokenFee),
                    createTopic("hbarTopic").withConsensusCustomFee(hbarFee),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .payingWith("sender")
                            .hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .payingWith("poorSender")
                            .hasPrecheck(INSUFFICIENT_PAYER_BALANCE),
                    submitMessageTo("hbarTopic")
                            .message("TEST")
                            .payingWith("poorSender")
                            .hasPrecheck(INSUFFICIENT_PAYER_BALANCE));
        }

        @HapiTest
        @DisplayName("MessageSubmit to a topic with a custom fee and not enough max_custom_fee")
        // TOPIC_FEE_213/214
        final Stream<DynamicTest> messageSubmitToTopicWithCustomFeesAndMaxCustomFee() {
            final var collector = "collector";
            final var tokenFee = fixedConsensusHtsFee(2, BASE_TOKEN, collector);
            final var hbarFee = fixedConsensusHbarFee(2, collector);
            final var tokenFeeLimit = maxHtsCustomFee("submitter", BASE_TOKEN, 1);
            final var tokenFeeLimitEnough = maxHtsCustomFee("submitter", BASE_TOKEN, 2);
            final var hbarFeeLimit = maxCustomFee("submitter", 1);
            final var hbarFeeLimitEnough = maxCustomFee("submitter", 2);

            return hapiTest(
                    cryptoCreate(collector).balance(0L),
                    cryptoCreate("submitter").balance(ONE_HBAR),
                    tokenAssociate("submitter", BASE_TOKEN),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(tokenFee).withConsensusCustomFee(hbarFee),
                    cryptoTransfer(moving(10, BASE_TOKEN).between(TOKEN_TREASURY, "submitter")),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(tokenFeeLimit)
                            .maxCustomFee(hbarFeeLimitEnough)
                            .message("TEST")
                            .payingWith("submitter")
                            .hasKnownStatus(MAX_CUSTOM_FEE_LIMIT_EXCEEDED),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(tokenFeeLimitEnough)
                            .maxCustomFee(hbarFeeLimit)
                            .message("TEST")
                            .payingWith("submitter")
                            .hasKnownStatus(MAX_CUSTOM_FEE_LIMIT_EXCEEDED),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(tokenFeeLimitEnough)
                            .message("TEST")
                            .payingWith("submitter")
                            .hasKnownStatus(NO_VALID_MAX_CUSTOM_FEE));
        }

        @HapiTest
        @DisplayName("MessageSubmit to a topic with no custom fees not associated")
        // TOPIC_FEE_216
        final Stream<DynamicTest> messageSubmitNotAssociated() {
            final var topic = "testTopic";
            final var tokenA = "tokenA";
            final var submitter = "submitter";
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(1, tokenA, collector);
            final var feeLimit = maxHtsCustomFee(submitter, tokenA, 1);

            return hapiTest(
                    cryptoCreate(collector),
                    tokenCreate(tokenA).treasury(collector).initialSupply(1000),
                    createTopic(topic).withConsensusCustomFee(fee),
                    cryptoCreate(submitter).balance(10 * ONE_HBAR),
                    submitMessageTo(topic)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith(submitter)
                            .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
        }

        @HapiTest
        @DisplayName("Test accept_all_custom_fees negative")
        // TOPIC_FEE_223/224
        final Stream<DynamicTest> negativeAcceptAllCustomFees() {
            final var collector = "collector";
            final var tokenFee = fixedConsensusHtsFee(2, BASE_TOKEN, collector);
            final var hbarFee = fixedConsensusHbarFee(2, collector);
            final var tokenFeeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 1);
            final var hbarFeeLimit = maxCustomFee(SUBMITTER, 1);
            return hapiTest(
                    cryptoCreate(collector).balance(0L),
                    tokenAssociate(collector, BASE_TOKEN),
                    cryptoCreate("sender").balance(1L),
                    tokenAssociate("sender", BASE_TOKEN),
                    cryptoTransfer(moving(1, BASE_TOKEN).between(TOKEN_TREASURY, "sender")),
                    createTopic(TOPIC).withConsensusCustomFee(tokenFee).withConsensusCustomFee(hbarFee),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(tokenFeeLimit)
                            .maxCustomFee(hbarFeeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(MAX_CUSTOM_FEE_LIMIT_EXCEEDED),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .payingWith("sender")
                            .hasPrecheck(INSUFFICIENT_PAYER_BALANCE));
        }

        @HapiTest
        @DisplayName("Submit to topic with frozen FT for fee")
        // TOPIC_FEE_230
        final Stream<DynamicTest> submitToTopicFrozenToken() {
            final var collector = "collector";
            final var frozenToken = "frozenToken";
            final var fee = fixedConsensusHtsFee(1, frozenToken, collector);
            return hapiTest(
                    newKeyNamed("frozenKey"),
                    tokenCreate(frozenToken).freezeKey("frozenKey"),
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, frozenToken),
                    tokenAssociate(SUBMITTER, frozenToken),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    tokenFreeze(frozenToken, collector),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN));
        }

        @HapiTest
        @DisplayName("Submit to topic with paused FT for fee")
        // TOPIC_FEE_231
        final Stream<DynamicTest> submitToTopicPausedToken() {
            final var collector = "collector";
            final var pausedToken = "pausedToken";
            final var fee = fixedConsensusHtsFee(1, pausedToken, collector);
            return hapiTest(
                    newKeyNamed("pausedKey"),
                    tokenCreate(pausedToken).pauseKey("pausedKey"),
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, pausedToken),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    tokenPause(pausedToken),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(INVALID_TOKEN_ID_IN_CUSTOM_FEES));
        }

        @HapiTest
        @DisplayName("Test multiple hbar fees with")
        final Stream<DynamicTest> multipleHbarFees() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(2, collector);
            final var fee1 = fixedConsensusHbarFee(1, collector);

            final var feeLimit = maxCustomFee(SUBMITTER, 2);
            final var correctFeeLimit = maxCustomFee(SUBMITTER, 3);

            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(fee).withConsensusCustomFee(fee1),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(feeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(MAX_CUSTOM_FEE_LIMIT_EXCEEDED),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(correctFeeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER));
        }

        @HapiTest
        // TOPIC_FEE_252
        @DisplayName("Submitter doesn't have enough balance to pay with submitter as collector")
        final Stream<DynamicTest> submitDoesNotHaveEnoughBalanceToPayWithSubmitterAsCollector() {
            final var alice = "alice";
            final var bob = "bob";
            final var dave = "dave";
            final var fee1 = fixedConsensusHtsFee(1, BASE_TOKEN, alice);
            final var fee2 = fixedConsensusHtsFee(2, BASE_TOKEN, bob);
            final var fee3 = fixedConsensusHtsFee(3, BASE_TOKEN, dave);
            return hapiTest(
                    cryptoCreate(alice).balance(ONE_HBAR),
                    cryptoCreate(bob).balance(0L),
                    cryptoCreate(dave).balance(0L),
                    tokenAssociate(alice, BASE_TOKEN),
                    tokenAssociate(bob, BASE_TOKEN),
                    tokenAssociate(dave, BASE_TOKEN),
                    cryptoTransfer(moving(3, BASE_TOKEN).between(SUBMITTER, alice)),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fee1)
                            .withConsensusCustomFee(fee2)
                            .withConsensusCustomFee(fee3),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .payingWith(alice)
                            .hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE));
        }

        @HapiTest
        @DisplayName("Max custom fee is supported only on consensus message submit")
        final Stream<DynamicTest> maxCustomFeesIsSupportedOnlyWithMsgSubmit() {
            final var sender = "sender";
            final var receiver = "receiver";
            final var feeLimit = maxCustomFee(sender, 2);
            return hapiTest(
                    cryptoCreate(sender).balance(ONE_HBAR),
                    cryptoCreate(receiver),
                    cryptoTransfer(TokenMovement.movingHbar(1).between(sender, receiver))
                            .maxCustomFee(feeLimit)
                            .hasPrecheck(MAX_CUSTOM_FEES_IS_NOT_SUPPORTED));
        }

        @HapiTest
        @DisplayName("Max custom fee contain duplicate denominations")
        final Stream<DynamicTest> maxCustomFeeContainsDuplicateDenominations() {
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(2, BASE_TOKEN, collector);
            final var feeLimit = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 2);
            final var feeLimit2 = maxHtsCustomFee(SUBMITTER, BASE_TOKEN, 10);
            return hapiTest(flattened(
                    associateFeeTokensAndSubmitter(),
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC)
                            // duplicate denominations in maxCustomFee
                            .maxCustomFee(feeLimit)
                            .maxCustomFee(feeLimit2)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasPrecheck(DUPLICATE_DENOMINATION_IN_MAX_CUSTOM_FEE_LIST)));
        }
    }

    @Nested
    @DisplayName("Positive scenarios with private topic")
    class SubmitMessagesToPrivateTopicPositiveScenarios {

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle lifecycle) {
            lifecycle.doAdhoc(associateFeeTokensAndSubmitter());
        }

        @HapiTest
        @DisplayName("Submitter key is in FEKL - hbar fee")
        // TOPIC_FEE_163
        final Stream<DynamicTest> submitterKeyIsInFEKLHbar() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(ONE_HUNDRED_HBARS, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(0L),
                    createTopic(TOPIC)
                            .feeExemptKeys(SUBMITTER)
                            .submitKeyName(SUBMITTER)
                            .withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC).message("TEST"),
                    getAccountBalance(collector).hasTinyBars(0));
        }

        @HapiTest
        @DisplayName("Submitter key is in FEKL - hts fee")
        // TOPIC_FEE_164
        final Stream<DynamicTest> submitterKeyIsInFEKLHts() {
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(0L),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC)
                            .feeExemptKeys(SUBMITTER)
                            .submitKeyName(SUBMITTER)
                            .withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC).message("TEST"),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 0L));
        }

        @HapiTest
        @DisplayName("FEKL is empty - hbar fee")
        // TOPIC_FEE_165
        final Stream<DynamicTest> FEKLIsEmptyHbar() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(ONE_HBAR, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(0L),
                    createTopic(TOPIC).submitKeyName(SUBMITTER).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC).message("TEST"),
                    getAccountBalance(collector).hasTinyBars(ONE_HBAR));
        }

        @HapiTest
        @DisplayName("Submit with treasury and FEKL is empty")
        // TOPIC_FEE_166
        final Stream<DynamicTest> SubmitWithTreasuryAndFEKLIsEmpty() {
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(0L),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).submitKeyName(SUBMITTER).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC).message("TEST").signedBy(SUBMITTER).payingWith(TOKEN_TREASURY),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 0L));
        }

        @HapiTest
        @DisplayName("Submit with collector and FEKL is empty")
        // TOPIC_FEE_167
        final Stream<DynamicTest> SubmitWithCollectorAndFEKLIsEmpty() {
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).submitKeyName(SUBMITTER).withConsensusCustomFee(fee),
                    submitMessageTo(TOPIC).message("TEST").signedBy(SUBMITTER).payingWith(collector),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 0L));
        }
    }
}
