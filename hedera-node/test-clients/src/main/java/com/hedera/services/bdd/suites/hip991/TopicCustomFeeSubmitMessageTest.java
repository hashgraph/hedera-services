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
import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_DENOMINATION_IN_MAX_CUSTOM_FEE_LIST;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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
        // TOPIC_FEE_108
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
                    cryptoTransfer(moving(1, BASE_TOKEN).between(TOKEN_TREASURY, submitterWithLowBalance)),
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
                        .hasPrecheck(ResponseCodeEnum.MAX_CUSTOM_FEES_IS_NOT_SUPPORTED));
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
