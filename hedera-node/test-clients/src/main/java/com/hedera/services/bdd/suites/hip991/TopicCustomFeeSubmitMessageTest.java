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

package com.hedera.services.bdd.suites.hip991;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenPause;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createHollow;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_DENOMINATION_IN_MAX_CUSTOM_FEE_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CUSTOM_FEE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_VALID_MAX_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
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
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith(SUBMITTER),
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
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith(SUBMITTER),
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
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith(SUBMITTER),
                    // assert topic fee collector balance
                    getAccountBalance(topicFeeCollector).hasTokenBalance(token, 1),
                    // assert token fee collector balance
                    getAccountBalance(tokenFeeCollector)
                            .hasTokenBalance(denomToken, 1)
                            .hasTinyBars(ONE_HBAR)));
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
                    submitMessageTo(TOPIC)
                            .acceptAllCustomFees(true)
                            .message("TEST")
                            .payingWith(SUBMITTER),
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
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith(TOKEN_TREASURY),
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
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith(DENOM_TREASURY),

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
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith(topicFeeCollector),

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
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith(secondLayerFeeCollector),

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
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith(anotherCollector),
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
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith(SUBMITTER),

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
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").signedByPayerAnd(feeScheduleKey),
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
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith(collector),
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
                    submitMessageTo(TOPIC)
                            .maxCustomFee(fee)
                            .message("TEST")
                            .payingWith(collector)
                            .via("submit"),
                    // assert collector's tinyBars balance
                    withOpContext((spec, log) -> {
                        final var submitTxnRecord = getTxnRecord("submit");
                        allRunFor(spec, submitTxnRecord);
                        final var transactionTxnFee =
                                submitTxnRecord.getResponseRecord().getTransactionFee();
                        getAccountBalance(collector).hasTinyBars(ONE_HUNDRED_HBARS - transactionTxnFee);
                    }));
        }

        @HapiTest
        @DisplayName("Submit message to a private topic no max custom fee")
        // TOPIC_FEE_158
        final Stream<DynamicTest> submitMessageToPrivateNoMaxCustomFee() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(ONE_HBAR, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    newKeyNamed(SUBMIT_KEY),
                    createTopic(TOPIC).submitKeyName(SUBMIT_KEY).withConsensusCustomFee(fee),
                    cryptoCreate("submitter").balance(ONE_HBAR).key(SUBMIT_KEY),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .payingWith("submitter")
                            .hasKnownStatus(NO_VALID_MAX_CUSTOM_FEE));
        }

        @HapiTest
        @DisplayName("Submit message to a private topic not enough balance")
        // TOPIC_FEE_159
        final Stream<DynamicTest> submitMessageToPrivateNotEnoughBalance() {
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(5, BASE_TOKEN, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    newKeyNamed(SUBMIT_KEY),
                    createTopic(TOPIC).submitKeyName(SUBMIT_KEY).withConsensusCustomFee(fee),
                    cryptoCreate("submitter").balance(ONE_HBAR).key(SUBMIT_KEY),
                    tokenAssociate("submitter", BASE_TOKEN),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(fee)
                            .message("TEST")
                            .payingWith("submitter")
                            .hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE));
        }

        @HapiTest
        @DisplayName("Submit message to a private no submit key")
        // TOPIC_FEE_160/161
        final Stream<DynamicTest> submitMessageToPrivateNoSubmitKey() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(ONE_HBAR, collector);
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
                            .maxCustomFee(fee)
                            .message("TEST")
                            .payingWith("submitter")
                            .signedBy("submitter")
                            .hasKnownStatus(INVALID_SIGNATURE));
        }

        @HapiTest
        @DisplayName("Multiple fees with same denomination")
        // TOPIC_FEE_191/193/195/197/199/163
        final Stream<DynamicTest> multipleFeesSameDenom() {
            final var collector = "collector";
            final var fee1 = fixedConsensusHtsFee(2, BASE_TOKEN, collector);
            final var fee2 = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            final var fee3 = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            final var correctFeeLimit = fixedConsensusHtsFee(4, BASE_TOKEN, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fee1)
                            .withConsensusCustomFee(fee2)
                            .withConsensusCustomFee(fee3),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(fee1)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(MAX_CUSTOM_FEE_LIMIT_EXCEEDED),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(correctFeeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER));
        }

        @HapiTest
        @DisplayName("Multiple fees with same denomination and different collectors")
        // TOPIC_FEE_192/194/196/198/200/164
        final Stream<DynamicTest> multipleFeesSameDenomDifferentCollectors() {
            final var collector1 = "collector1";
            final var collector2 = "collector2";
            final var collector3 = "collector3";
            final var fee1 = fixedConsensusHtsFee(2, BASE_TOKEN, collector1);
            final var fee2 = fixedConsensusHtsFee(1, BASE_TOKEN, collector2);
            final var fee3 = fixedConsensusHtsFee(2, BASE_TOKEN, collector3);
            final var correctFeeLimit = fixedConsensusHtsFee(5, BASE_TOKEN, collector1);
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
                            .maxCustomFee(fee1)
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
        // TOPIC_FEE_165/166
        final Stream<DynamicTest> submitToTopicMaxLimitNotEnough() {
            final var collector = "collector";
            final var tokenFee = fixedConsensusHtsFee(2, BASE_TOKEN, collector);
            final var hbarFee = fixedConsensusHbarFee(2, collector);
            final var tokenFeeLimit = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            final var hbarFeeLimit = fixedConsensusHbarFee(1, collector);
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
        // TOPIC_FEE_167/168/169
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
                            .acceptAllCustomFees(true)
                            .message("TEST")
                            .payingWith("sender")
                            .hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE),
                    submitMessageTo(TOPIC)
                            .acceptAllCustomFees(true)
                            .message("TEST")
                            .payingWith("poorSender")
                            .hasPrecheck(INSUFFICIENT_PAYER_BALANCE),
                    submitMessageTo("hbarTopic")
                            .acceptAllCustomFees(true)
                            .message("TEST")
                            .payingWith("poorSender")
                            .hasPrecheck(INSUFFICIENT_PAYER_BALANCE));
        }

        @HapiTest
        @DisplayName("Submit to topic with frozen FT for fee")
        // TOPIC_FEE_172
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
                            .acceptAllCustomFees(true)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN));
        }

        @HapiTest
        @DisplayName("Submit to topic with paused FT for fee")
        // TOPIC_FEE_173
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
                            .acceptAllCustomFees(true)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(TOKEN_IS_PAUSED));
        }

        @HapiTest
        @DisplayName("Collector submits a message to a topic with 2 different FT fees.")
        final Stream<DynamicTest> collectorSubmitMessageToTopicWith2differentFees() {
            final var collector = "collector";
            final var secondCollector = "secondCollector";
            final var fee1 = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            final var fee2 = fixedConsensusHtsFee(1, SECOND_TOKEN, secondCollector);
            return hapiTest(
                    // todo create and associate collector in beforeAll()
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN, SECOND_TOKEN),
                    // create second collector and send second token
                    cryptoCreate(secondCollector).balance(ONE_HBAR),
                    tokenAssociate(secondCollector, SECOND_TOKEN),
                    cryptoTransfer(moving(1, SECOND_TOKEN).between(SUBMITTER, collector)),
                    // create topic with two fees
                    createTopic(TOPIC).withConsensusCustomFee(fee1).withConsensusCustomFee(fee2),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(fee1)
                            .maxCustomFee(fee2)
                            .message("TEST")
                            .payingWith(collector),
                    // only second fee should be paid
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 0L),
                    getAccountBalance(secondCollector).hasTokenBalance(SECOND_TOKEN, 1L));
        }

        @HapiTest
        @DisplayName("Just some tests")
        final Stream<DynamicTest> test() {
            final var collector = "collector";
            final var fee = fixedConsensusHtsFee(2, BASE_TOKEN, collector);
            final var fee1 = fixedConsensusHtsFee(1, BASE_TOKEN, collector);
            final var hbarFee = fixedConsensusHbarFee(1, collector);
            final var hbarFee2 = fixedConsensusHbarFee(2, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(fee).withConsensusCustomFee(hbarFee2),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(fee)
                            .maxCustomFee(fee1)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(DUPLICATE_DENOMINATION_IN_MAX_CUSTOM_FEE_LIST));
        }

        @HapiTest
        @DisplayName("Test multiple hbar fees with")
        final Stream<DynamicTest> multipleHbarFees() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(2, collector);
            final var fee1 = fixedConsensusHbarFee(1, collector);
            final var correctFeeLimit = fixedConsensusHbarFee(3, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, BASE_TOKEN),
                    createTopic(TOPIC).withConsensusCustomFee(fee).withConsensusCustomFee(fee1),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(fee)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(MAX_CUSTOM_FEE_LIMIT_EXCEEDED),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(correctFeeLimit)
                            .message("TEST")
                            .payingWith(SUBMITTER));
        }

        // questions:
        // topic with 2 fees with same denomination!
        // topic with multiple denomination fees
    }
}
