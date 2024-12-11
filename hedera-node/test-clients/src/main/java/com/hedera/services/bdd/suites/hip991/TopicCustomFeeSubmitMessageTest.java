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
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.ED25519_ON;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.keys.SigControl.SECP256K1_ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.keys.SigControl;
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
        @DisplayName("Account with key in FEKL submits a message to a topic.")
        // TOPIC_FEE_129/130/131
        final Stream<DynamicTest> accountSubmitMessageWithKeysExempt() {
            final var collector = "collector";
            final var ecdsaKey = "ecdsaKey";
            final var ed25519Key = "ed25519Key";
            final var threshKey = "threshKey";
            final var fee = fixedConsensusHbarFee(1, collector);
            return hapiTest(
                    newKeyNamed(ecdsaKey).shape(SECP256K1_ON),
                    newKeyNamed(ed25519Key).shape(ED25519_ON),
                    newKeyNamed(threshKey).shape(threshOf(1, SIMPLE, SIMPLE)),
                    cryptoCreate("ecdsaAccount").key(ecdsaKey),
                    cryptoCreate("ed25559Account").key(ed25519Key),
                    cryptoCreate("threshAccount").key(threshKey),
                    cryptoCreate(collector).balance(ONE_HBAR),
                    createTopic(TOPIC).withConsensusCustomFee(fee).feeExemptKeys(ecdsaKey, ed25519Key, threshKey),
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith("ecdsaAccount"),
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith("ed25559Account"),
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith("threshAccount"),
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
            return hapiTest(
                    newKeyNamed(threshKey).shape(threshShape),
                    cryptoCreate(collector).balance(ONE_HBAR),
                    createTopic(TOPIC).withConsensusCustomFee(fee).feeExemptKeys(threshKey),
                    cryptoCreate("payingAccount").key(threshKey),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(fee)
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
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    newKeyNamed(feeScheduleKey),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fee)
                            .feeScheduleKeyName(feeScheduleKey)
                            .feeExemptKeys(feeScheduleKey),
                    cryptoCreate("payingAccount").key(feeScheduleKey),
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith("payingAccount"),
                    getAccountBalance(collector).hasTinyBars(ONE_HBAR));
        }

        @HapiTest
        @DisplayName("Submits a message to a topic with admin key.")
        // TOPIC_FEE_135
        final Stream<DynamicTest> submitWithAdminKey() {
            final var collector = "collector";
            final var adminKey = "adminKey";
            final var fee = fixedConsensusHbarFee(1, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    newKeyNamed(adminKey),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fee)
                            .adminKeyName(adminKey)
                            .feeExemptKeys(adminKey),
                    cryptoCreate("payingAccount").key(adminKey),
                    submitMessageTo(TOPIC).maxCustomFee(fee).message("TEST").payingWith("payingAccount"),
                    getAccountBalance(collector).hasTinyBars(ONE_HBAR));
        }

        @HapiTest
        @DisplayName("Submits a message to a topic without max custom fee")
        // TOPIC_FEE_136
        final Stream<DynamicTest> submitWithNoMaxCustomFee() {
            final var collector = "collector";
            final var fee = fixedConsensusHbarFee(1, collector);
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    cryptoCreate("payingAccount"),
                    submitMessageTo(TOPIC)
                            .message("TEST")
                            .payingWith("payingAccount")
                            .hasKnownStatus(CUSTOM_FEES_LIMIT_EXCEEDED),
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
            final var secondFee = fixedConsensusHtsFee(1, ftB, collector);
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
                            .maxCustomFee(firstFee)
                            .maxCustomFee(secondFee)
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
                            .maxCustomFee(fixedConsensusHtsFee(1, BASE_TOKEN, collector))
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(CUSTOM_FEES_LIMIT_EXCEEDED),
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
                            .maxCustomFee(fixedConsensusHtsFee(1, BASE_TOKEN, collector))
                            .maxCustomFee(fixedConsensusHtsFee(2, SECOND_TOKEN, collector))
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(CUSTOM_FEES_LIMIT_EXCEEDED),
                    getAccountBalance(collector).hasTokenBalance(BASE_TOKEN, 0),
                    getAccountBalance(collector).hasTokenBalance(SECOND_TOKEN, 0));
        }

        @HapiTest
        @DisplayName("Submits a message to a topic with custom fee FT with 4 layer fees")
        // TOPIC_FEE_144
        final Stream<DynamicTest> submitToTopicWithThreeLayersOfFees() {
            final var collector = "collector";
            return hapiTest(flattened(
                    createTokenWith4LayerFee(SUBMITTER, "fourLayerToken", true),
                    cryptoCreate(collector).balance(ONE_HBAR),
                    tokenAssociate(collector, "fourLayerToken"),
                    createTopic(TOPIC).withConsensusCustomFee(fixedConsensusHtsFee(1, "fourLayerToken", collector)),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(fixedConsensusHtsFee(1, "fourLayerToken", collector))
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
            return hapiTest(
                    cryptoCreate(collector).balance(ONE_HBAR),
                    createTopic(TOPIC).withConsensusCustomFee(fee),
                    cryptoDelete(collector),
                    submitMessageTo(TOPIC)
                            .maxCustomFee(fee)
                            .message("TEST")
                            .payingWith(SUBMITTER)
                            .hasKnownStatus(ACCOUNT_DELETED));
        }
    }
}
