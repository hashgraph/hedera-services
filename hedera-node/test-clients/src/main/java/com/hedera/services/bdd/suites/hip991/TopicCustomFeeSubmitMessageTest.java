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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
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

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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
}
