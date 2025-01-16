/*
 * Copyright (C) 2020-2025 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenPause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.expectedConsensusFixedHTSFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.expectedConsensusFixedHbarFee;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_BILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.createHollowAccountFrom;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.updateSpecFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FEE_EXEMPT_KEY_LIST_CONTAINS_DUPLICATED_KEYS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KEY_IN_FEE_EXEMPT_KEY_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTRIES_FOR_FEE_EXEMPT_KEY_LIST_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hederahashgraph.api.proto.java.ConsensusCustomFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;

@HapiTestLifecycle
@DisplayName("Topic custom fees")
public class TopicCustomFeeCreateTest extends TopicCustomFeeBase {

    @Nested
    @DisplayName("Positive scenarios")
    class TopicCreatePositiveScenarios {
        static final String COLLECTOR = "collector";

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle lifecycle) {
            lifecycle.doAdhoc(setupBaseKeys());
        }

        @HapiTest
        @DisplayName("Validate fees for topic create")
        final Stream<DynamicTest> validateFeesForTopicCreate() {
            return hapiTest(
                    cryptoCreate("payer").balance(ONE_HUNDRED_HBARS),
                    cryptoCreate("collector"),
                    createTopic(TOPIC).via("topicNoCustomFee").payingWith("payer"),
                    createTopic(TOPIC)
                            .via("topicWithCustomFee")
                            .blankMemo()
                            .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector"))
                            .payingWith("payer"),
                    validateChargedUsd("topicNoCustomFee", 0.01, 2),
                    validateChargedUsd("topicWithCustomFee", 2.0));
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
        @DisplayName("Create topic with ECDSA feeScheduleKey")
        // TOPIC_FEE_005
        final Stream<DynamicTest> createTopicWithECDSAFeeScheduleKey() {
            return hapiTest(
                    createTopic(TOPIC).feeScheduleKeyName(FEE_SCHEDULE_KEY_ECDSA),
                    getTopicInfo(TOPIC).hasFeeScheduleKey(FEE_SCHEDULE_KEY_ECDSA));
        }

        @HapiTest
        @DisplayName("Create topic with threshold feeScheduleKey")
        // TOPIC_FEE_006
        final Stream<DynamicTest> createTopicWithThresholdFeeScheduleKey() {
            final var threshKey = "threshKey";
            return hapiTest(
                    newKeyNamed(threshKey).shape(KeyShape.threshOf(1, KeyShape.SIMPLE, KeyShape.SIMPLE)),
                    createTopic(TOPIC).feeScheduleKeyName(threshKey),
                    getTopicInfo(TOPIC).hasFeeScheduleKey(threshKey));
        }

        @HapiTest
        @DisplayName("Create topic with 1 Hbar fixed fee")
        // TOPIC_FEE_008
        final Stream<DynamicTest> createTopicWithOneHbarFixedFee() {
            return hapiTest(
                    cryptoCreate(COLLECTOR),
                    createTopic(TOPIC)
                            .adminKeyName(ADMIN_KEY)
                            .submitKeyName(SUBMIT_KEY)
                            .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                            .withConsensusCustomFee(fixedConsensusHbarFee(ONE_HBAR, COLLECTOR)),
                    getTopicInfo(TOPIC)
                            .hasAdminKey(ADMIN_KEY)
                            .hasSubmitKey(SUBMIT_KEY)
                            .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                            .hasCustomFee(expectedConsensusFixedHbarFee(ONE_HBAR, COLLECTOR)));
        }

        @HapiTest
        @DisplayName("Create topic with 1 HTS fixed fee")
        // TOPIC_FEE_009
        final Stream<DynamicTest> createTopicWithOneHTSFixedFee() {
            return hapiTest(
                    cryptoCreate(COLLECTOR),
                    tokenCreate("testToken")
                            .tokenType(TokenType.FUNGIBLE_COMMON)
                            .initialSupply(500),
                    tokenAssociate(COLLECTOR, "testToken"),
                    createTopic(TOPIC)
                            .adminKeyName(ADMIN_KEY)
                            .submitKeyName(SUBMIT_KEY)
                            .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                            .withConsensusCustomFee(fixedConsensusHtsFee(1, "testToken", COLLECTOR)),
                    getTopicInfo(TOPIC)
                            .hasAdminKey(ADMIN_KEY)
                            .hasSubmitKey(SUBMIT_KEY)
                            .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                            .hasCustomFee(expectedConsensusFixedHTSFee(1, "testToken", COLLECTOR)));
        }

        @HapiTest
        @DisplayName("Create topic with 1 HBAR fixed fee with max amount")
        // TOPIC_FEE_010
        final Stream<DynamicTest> createTopicMaxHbarAmount() {
            return hapiTest(
                    cryptoCreate(COLLECTOR),
                    createTopic(TOPIC)
                            .adminKeyName(ADMIN_KEY)
                            .submitKeyName(SUBMIT_KEY)
                            .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                            .withConsensusCustomFee(fixedConsensusHbarFee(50 * ONE_BILLION_HBARS, COLLECTOR)),
                    getTopicInfo(TOPIC)
                            .hasAdminKey(ADMIN_KEY)
                            .hasSubmitKey(SUBMIT_KEY)
                            .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                            .hasCustomFee(expectedConsensusFixedHbarFee(50 * ONE_BILLION_HBARS, COLLECTOR)));
        }

        @HapiTest
        @DisplayName("Create topic with 1 HTS fixed with max supply")
        // TOPIC_FEE_011
        final Stream<DynamicTest> createTopicMaxTokenSupplyFee() {
            var maxSupply = 10_000;
            return hapiTest(
                    cryptoCreate(COLLECTOR),
                    tokenCreate("finiteToken")
                            .supplyType(TokenSupplyType.FINITE)
                            .tokenType(TokenType.FUNGIBLE_COMMON)
                            .initialSupply(500)
                            .maxSupply(maxSupply),
                    tokenAssociate(COLLECTOR, "finiteToken"),
                    createTopic(TOPIC)
                            .adminKeyName(ADMIN_KEY)
                            .submitKeyName(SUBMIT_KEY)
                            .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                            .withConsensusCustomFee(fixedConsensusHtsFee(maxSupply, "finiteToken", COLLECTOR)),
                    getTopicInfo(TOPIC)
                            .hasAdminKey(ADMIN_KEY)
                            .hasSubmitKey(SUBMIT_KEY)
                            .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                            .hasCustomFee(expectedConsensusFixedHTSFee(maxSupply, "finiteToken", COLLECTOR)));
        }

        @HapiTest
        @DisplayName("Create topic with 1 HTS fixed with max supply")
        // TOPIC_FEE_012
        final Stream<DynamicTest> createTopicTokenFeeMaxLong() {
            return hapiTest(
                    cryptoCreate(COLLECTOR),
                    tokenCreate("finiteToken")
                            .tokenType(TokenType.FUNGIBLE_COMMON)
                            .initialSupply(500),
                    tokenAssociate(COLLECTOR, "finiteToken"),
                    createTopic(TOPIC)
                            .adminKeyName(ADMIN_KEY)
                            .submitKeyName(SUBMIT_KEY)
                            .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                            .withConsensusCustomFee(fixedConsensusHtsFee(Long.MAX_VALUE, "finiteToken", COLLECTOR)),
                    getTopicInfo(TOPIC)
                            .hasAdminKey(ADMIN_KEY)
                            .hasSubmitKey(SUBMIT_KEY)
                            .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                            .hasCustomFee(expectedConsensusFixedHTSFee(Long.MAX_VALUE, "finiteToken", COLLECTOR)));
        }

        @HapiTest
        @DisplayName("Create topic smallest fee")
        // TOPIC_FEE_015
        final Stream<DynamicTest> createTopicSmallestFee() {
            return hapiTest(
                    cryptoCreate(COLLECTOR),
                    createTopic(TOPIC)
                            .adminKeyName(ADMIN_KEY)
                            .submitKeyName(SUBMIT_KEY)
                            .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                            .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR)),
                    getTopicInfo(TOPIC)
                            .hasAdminKey(ADMIN_KEY)
                            .hasSubmitKey(SUBMIT_KEY)
                            .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                            .hasCustomFee(expectedConsensusFixedHbarFee(1, COLLECTOR)));
        }

        @HapiTest
        @DisplayName("Create topic with HBAR fee above supply")
        // TOPIC_FEE_016
        final Stream<DynamicTest> createTopicHbarFeeAboveSupply() {
            var aboveSupply = 50 * ONE_BILLION_HBARS + 1;
            return hapiTest(
                    cryptoCreate(COLLECTOR),
                    createTopic(TOPIC)
                            .adminKeyName(ADMIN_KEY)
                            .submitKeyName(SUBMIT_KEY)
                            .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                            .withConsensusCustomFee(fixedConsensusHbarFee(aboveSupply, COLLECTOR)),
                    getTopicInfo(TOPIC)
                            .hasAdminKey(ADMIN_KEY)
                            .hasSubmitKey(SUBMIT_KEY)
                            .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                            .hasCustomFee(expectedConsensusFixedHbarFee(aboveSupply, COLLECTOR)));
        }

        @HapiTest
        @DisplayName("topic create with hollow account for collector")
        final Stream<DynamicTest> topicWithHollowCollector() {
            // TOPIC_FEE_017
            final var validAlias = "validAlias";
            return hapiTest(flattened(
                    createHollowAccountOperations(validAlias),
                    createTopic(TOPIC)
                            .adminKeyName(ADMIN_KEY)
                            .submitKeyName(SUBMIT_KEY)
                            .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                            .withConsensusCustomFee(fixedConsensusHbarFee(1, validAlias)),
                    getTopicInfo(TOPIC)
                            .hasAdminKey(ADMIN_KEY)
                            .hasSubmitKey(SUBMIT_KEY)
                            .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                            .hasCustomFee(expectedConsensusFixedHbarFee(1, validAlias)),
                    // check if account is hollow (has empty key)
                    getAliasedAccountInfo(validAlias).isHollow()));
        }

        @HapiTest
        @DisplayName("topic create with contract collector")
        final Stream<DynamicTest> topicWithContractCollector() {
            // TOPIC_FEE_019
            var mutableContract = "PayReceivable";
            return hapiTest(flattened(
                    deployMutableContract(mutableContract),
                    createTopic(TOPIC)
                            .adminKeyName(ADMIN_KEY)
                            .submitKeyName(SUBMIT_KEY)
                            .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                            .withConsensusCustomFee(fixedConsensusHbarFee(1, mutableContract)),
                    getTopicInfo(TOPIC)
                            .hasAdminKey(ADMIN_KEY)
                            .hasSubmitKey(SUBMIT_KEY)
                            .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                            .hasCustomFee(expectedConsensusFixedHbarFee(1, mutableContract))));
        }

        @HapiTest
        @DisplayName("Create topic with keys")
        // TOPIC_FEE_020/21/23/24
        final Stream<DynamicTest> topicCreateWithFEKL() {
            final var collector = "collector";
            final var key = "key";
            return hapiTest(
                    // create topic with 1 FEKL and no custom fee
                    newKeyNamed(key),
                    createTopic(TOPIC).adminKeyName(ADMIN_KEY).feeExemptKeys(key),
                    getTopicInfo(TOPIC).hasAdminKey(ADMIN_KEY).hasFeeExemptKeys(List.of(key)),
                    // create topic with 1 FEKL and custom fee
                    cryptoCreate(collector),
                    createTopic(TOPIC + "2")
                            .withConsensusCustomFee(fixedConsensusHbarFee(1, collector))
                            .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                            .adminKeyName(ADMIN_KEY)
                            .feeExemptKeys(key),
                    getTopicInfo(TOPIC + "2")
                            .hasCustomFee(expectedConsensusFixedHbarFee(1, collector))
                            .hasFeeScheduleKey(FEE_SCHEDULE_KEY)
                            .hasAdminKey(ADMIN_KEY)
                            .hasFeeExemptKeys(List.of(key)));
        }

        @HapiTest
        @DisplayName("Create topic with 10 keys in FEKL")
        // TOPIC_FEE_022
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

        @HapiTest
        @DisplayName("Topic Create with different keys")
        // TOPIC_FEE_025
        final Stream<DynamicTest> topicCreateWithDifferentKeys() {
            final var ecdsaKey = "ecdsaKey";
            final var ed25519Key = "ed25519Key";
            final var threshKey = "threshKey";
            return hapiTest(
                    newKeyNamed(ecdsaKey).shape(SigControl.SECP256K1_ON),
                    newKeyNamed(ed25519Key).shape(SigControl.ED25519_ON),
                    newKeyNamed(threshKey).shape(threshOf(1, SIMPLE, SIMPLE)),
                    createTopic(TOPIC).feeExemptKeys(ecdsaKey, ed25519Key, threshKey),
                    getTopicInfo(TOPIC).hasFeeExemptKeys(List.of(ecdsaKey, ed25519Key, threshKey)));
        }

        @HapiTest
        @DisplayName("Create topic with keys")
        // TOPIC_FEE_026
        final Stream<DynamicTest> topicCreateWithFeklDeletedAccount() {
            final var key = "key";
            return hapiTest(
                    newKeyNamed(key),
                    cryptoCreate("deleted").key(key),
                    cryptoDelete("deleted"),
                    createTopic(TOPIC).feeExemptKeys(key),
                    getTopicInfo(TOPIC).hasFeeExemptKeys(List.of(key)));
        }

        @HapiTest
        @DisplayName("Delete custom fee collector")
        // TOPIC_FEE_028
        final Stream<DynamicTest> deleteCollector() {
            final var collector = "collector";
            return hapiTest(
                    cryptoCreate(collector),
                    createTopic(TOPIC).withConsensusCustomFee(fixedConsensusHbarFee(1, collector)),
                    cryptoDelete(collector).hasKnownStatus(SUCCESS));
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
        // TOPIC_FEE_029
        final Stream<DynamicTest> createTopicWithDuplicateSignatures() {
            final var testKey = "testKey";
            return hapiTest(flattened(
                    newKeyNamed(testKey),
                    createTopic(TOPIC)
                            .adminKeyName(ADMIN_KEY)
                            .submitKeyName(SUBMIT_KEY)
                            .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                            .feeExemptKeys(testKey, testKey)
                            .hasPrecheck(FEE_EXEMPT_KEY_LIST_CONTAINS_DUPLICATED_KEYS)));
        }

        @HapiTest
        @DisplayName("Create topic with 0 Hbar fixed fee")
        // TOPIC_FEE_030
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
        // TOPIC_FEE_031
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
        // TOPIC_FEE_032
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
        // TOPIC_FEE_033
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

        @HapiTest
        @DisplayName("Create topic with custom fee above max FT supply")
        // TOPIC_FEE_034
        final Stream<DynamicTest> createTopicCustomFeeAboveSupply() {
            final var collector = "collector";
            return hapiTest(
                    cryptoCreate(collector),
                    tokenCreate("fungibleToken")
                            .tokenType(TokenType.FUNGIBLE_COMMON)
                            .supplyType(TokenSupplyType.FINITE)
                            .initialSupply(500)
                            .maxSupply(1000),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fixedConsensusHtsFee(1001, "fungibleToken", "collector"))
                            .hasKnownStatus(AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY));
        }

        @HapiTest
        @DisplayName("Create topic with negative custom fee")
        // TOPIC_FEE_037
        final Stream<DynamicTest> createTopicNegativeFee() {
            return hapiTest(
                    cryptoCreate("collector"),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fixedConsensusHbarFee(-1, "collector"))
                            .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE));
        }

        @HapiTest
        @DisplayName("Create topic with not specified custom fee")
        // TOPIC_FEE_038
        final Stream<DynamicTest> createTopicNotSpecifiedFee() {
            final var collector = "collector";
            return hapiTest(
                    cryptoCreate(collector),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(spec -> ConsensusCustomFee.newBuilder()
                                    .setFeeCollectorAccountId(spec.registry().getAccountID(collector))
                                    .build())
                            .hasKnownStatus(CUSTOM_FEE_NOT_FULLY_SPECIFIED));
        }

        @HapiTest
        @DisplayName("Create topic with invalid FT for custom fee")
        // TOPIC_FEE_039/40/41
        final Stream<DynamicTest> createTopicInvalidToken() {
            return hapiTest(
                    cryptoCreate("collector"),
                    newKeyNamed("pauseKey"),
                    tokenCreate("pausedToken").pauseKey("pauseKey"),
                    tokenPause("pausedToken"),
                    tokenCreate("deletedToken").adminKey("adminKey"),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fixedConsensusHtsFee(1, "pausedToken", "collector"))
                            .hasKnownStatus(INVALID_TOKEN_ID_IN_CUSTOM_FEES),
                    tokenDelete("deletedToken").hasKnownStatus(SUCCESS),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fixedConsensusHtsFee(1, "deletedToken", "collector"))
                            .hasKnownStatus(INVALID_TOKEN_ID_IN_CUSTOM_FEES));
        }

        @HapiTest
        @DisplayName("Create topic with above FEKL limit")
        // TOPIC_FEE_042
        final Stream<DynamicTest> createTopicAboveKeyLimit() {
            return hapiTest(flattened(
                    newNamedKeysForFEKL(11),
                    createTopic(TOPIC)
                            .feeExemptKeys(feeExemptKeyNames(11))
                            .hasKnownStatus(MAX_ENTRIES_FOR_FEE_EXEMPT_KEY_LIST_EXCEEDED)));
        }

        @HapiTest
        @DisplayName("Create topic with invalid fee exempt key")
        // TOPIC_FEE_043
        final Stream<DynamicTest> createTopicInvalidFeeExemptKey() {
            var invalidEd25519Key = Key.newBuilder()
                    .setEd25519(ByteString.fromHex("0000000000000000000000000000000000000000"))
                    .build();
            var invalidEcdsaKey = Key.newBuilder()
                    .setECDSASecp256K1(ByteString.fromHex("0000000000000000000000000000000000000000"))
                    .build();
            return hapiTest(
                    createTopic(TOPIC)
                            .feeExemptKeys(invalidEd25519Key)
                            .hasKnownStatus(INVALID_KEY_IN_FEE_EXEMPT_KEY_LIST),
                    createTopic(TOPIC)
                            .feeExemptKeys(invalidEcdsaKey)
                            .hasKnownStatus(INVALID_KEY_IN_FEE_EXEMPT_KEY_LIST));
        }

        @HapiTest
        @DisplayName("Create topic with frozen token for collector")
        // TOPIC_FEE_044
        final Stream<DynamicTest> createTopicFrozenTokenForCollector() {
            return hapiTest(
                    newKeyNamed("freezeKey"),
                    cryptoCreate("collector"),
                    tokenCreate("frozenToken").freezeKey("freezeKey"),
                    tokenAssociate("collector", "frozenToken"),
                    tokenFreeze("frozenToken", "collector"),
                    createTopic(TOPIC)
                            .withConsensusCustomFee(fixedConsensusHtsFee(1, "frozenToken", "collector"))
                            .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN));
        }
    }

    private SpecOperation[] createHollowAccountOperations(String alias) {
        final var t = new ArrayList<SpecOperation>(List.of(newKeyNamed(alias).shape(SECP_256K1_SHAPE)));
        t.addAll(Arrays.stream(createHollowAccountFrom(alias)).toList());
        t.add(withOpContext((spec, opLog) -> updateSpecFor(spec, alias)));
        return t.toArray(new SpecOperation[0]);
    }

    protected SpecOperation[] deployMutableContract(String name) {
        var t = List.of(
                newKeyNamed(name),
                uploadInitCode(name),
                contractCreate(name)
                        .maxAutomaticTokenAssociations(0)
                        .adminKey(name)
                        .gas(500_000L));

        return t.toArray(new SpecOperation[0]);
    }
}
