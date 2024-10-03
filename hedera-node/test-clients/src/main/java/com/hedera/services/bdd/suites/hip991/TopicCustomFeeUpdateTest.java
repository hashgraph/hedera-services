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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.expectedConsensusFixedHTSFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.expectedConsensusFixedHbarFee;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;

@HapiTestLifecycle
@DisplayName("Topic custom fees update")
public class TopicCustomFeeUpdateTest extends TopicCustomFeeBase {

    // TODO: separate into inner classes?

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle lifecycle) {
        lifecycle.doAdhoc(setupBaseForUpdate());
    }

    @HapiTest
    @DisplayName("the fee schedule key")
    // TOPIC_FEE_040
    final Stream<DynamicTest> updateFeeScheduleKey() {
        return hapiTest(flattened(
                // Create a topic and verify that the keys are correct
                createTopic(TOPIC).adminKeyName(ADMIN_KEY).feeScheduleKeyName(FEE_SCHEDULE_KEY),
                getTopicInfo(TOPIC).hasAdminKey(ADMIN_KEY).hasFeeScheduleKey(FEE_SCHEDULE_KEY),

                // Update the fee schedule and verify that it's updated
                updateTopic(TOPIC).feeScheduleKeyName(FEE_SCHEDULE_KEY2).signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY2),
                getTopicInfo(TOPIC).hasFeeScheduleKey(FEE_SCHEDULE_KEY2)));
    }

    @HapiTest
    @DisplayName("the custom fee from FT to HBAR")
    // TOPIC_FEE_041
    final Stream<DynamicTest> updateCustomFeeFromFTToHBAR() {
        return hapiTest(flattened(
                // Create a topic and verify custom fee is correct
                createTopic(TOPIC)
                        .adminKeyName(ADMIN_KEY)
                        .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                        .withConsensusCustomFee(fixedConsensusHtsFee(1, TOKEN, COLLECTOR)),
                getTopicInfo(TOPIC).hasCustomFee(expectedConsensusFixedHTSFee(1, TOKEN, COLLECTOR)),

                // Update the custom fee and verify that it's updated
                updateTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR))
                        .signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY),
                getTopicInfo(TOPIC).hasCustomFee(expectedConsensusFixedHbarFee(1, COLLECTOR))));
    }

    @HapiTest
    @DisplayName("the custom fee from HBAR to FT")
    // TOPIC_FEE_042
    final Stream<DynamicTest> updateCustomFeeFromHBARToFT() {
        return hapiTest(flattened(
                // Create a topic and verify custom fee is correct
                createTopic(TOPIC)
                        .adminKeyName(ADMIN_KEY)
                        .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR)),
                getTopicInfo(TOPIC).hasCustomFee(expectedConsensusFixedHbarFee(1, COLLECTOR)),

                // Update the custom fee and verify that it's updated
                updateTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHtsFee(1, TOKEN, COLLECTOR))
                        .signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY),
                getTopicInfo(TOPIC).hasCustomFee(expectedConsensusFixedHTSFee(1, TOKEN, COLLECTOR))));
    }

    @HapiTest
    @DisplayName("to remove the custom fees")
    // TOPIC_FEE_043
    final Stream<DynamicTest> updateToRemoveCustomFees() {
        return hapiTest(flattened(
                // Create a topic and verify custom fee is correct
                createTopic(TOPIC)
                        .adminKeyName(ADMIN_KEY)
                        .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR)),
                getTopicInfo(TOPIC).hasCustomFee(expectedConsensusFixedHbarFee(1, COLLECTOR)),

                // Update the topic removing the custom fee
                updateTopic(TOPIC).withEmptyCustomFee().signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY),
                getTopicInfo(TOPIC).hasNoCustomFee()));
    }

    @HapiTest
    @DisplayName("to remove one of the two custom fees")
    // TOPIC_FEE_044
    final Stream<DynamicTest> updateCustomFeesToRemoveOneOfTwo() {
        return hapiTest(flattened(
                // Create a topic with two custom fees
                createTopic(TOPIC)
                        .adminKeyName(ADMIN_KEY)
                        .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR))
                        .withConsensusCustomFee(fixedConsensusHtsFee(1, TOKEN, COLLECTOR)),
                getTopicInfo(TOPIC)
                        .hasCustomFee(expectedConsensusFixedHbarFee(1, COLLECTOR))
                        .hasCustomFee(expectedConsensusFixedHTSFee(1, TOKEN, COLLECTOR)),

                // Update to remove one of the fees
                updateTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR))
                        .signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY),
                getTopicInfo(TOPIC).hasCustomFeeSize(1).hasCustomFee(expectedConsensusFixedHbarFee(1, COLLECTOR))));
    }

    @HapiTest
    @DisplayName("the FeeExemptKeyList")
    // TOPIC_FEE_049
    final Stream<DynamicTest> updateFeeExemptKeyList() {
        return hapiTest(flattened(
                // Create a topic with no feeExemptKeyList
                createTopic(TOPIC).adminKeyName(ADMIN_KEY),

                // Create 10 keys
                newNamedKeysForFEKL(10),

                // Update the topic to add feeExemptKeyList keys
                updateTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR))
                        .feeExemptKeys(feeExemptKeyNames(10))
                        .signedByPayerAnd(ADMIN_KEY),
                getTopicInfo(TOPIC)
                        .hasCustomFee(expectedConsensusFixedHbarFee(1, COLLECTOR))
                        .hasFeeExemptKeys(List.of(feeExemptKeyNames(10)))));
    }

    @HapiTest
    @DisplayName("add one more fee exempt key")
    // TOPIC_FEE_050
    final Stream<DynamicTest> updateToAddOneMoreFeeExemptKey() {
        final var key = "key";
        final var key2 = "key2";
        return hapiTest(flattened(
                newKeyNamed(key),

                // Create a topic with a single key in feeExemptKeyList
                createTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR))
                        .feeExemptKeys(key)
                        .adminKeyName(ADMIN_KEY),
                getTopicInfo(TOPIC)
                        .hasCustomFee(expectedConsensusFixedHbarFee(1, COLLECTOR))
                        .hasFeeExemptKeys(List.of(key)),

                // Update the topic to add one more key
                newKeyNamed(key2),
                updateTopic(TOPIC).feeExemptKeys(key, key2).signedByPayerAnd(ADMIN_KEY),
                getTopicInfo(TOPIC)
                        .hasCustomFee(expectedConsensusFixedHbarFee(1, COLLECTOR))
                        .hasFeeExemptKeys(List.of(key, key2))));
    }

    @HapiTest
    @DisplayName("to remove one of the keys in fee except key list")
    // TOPIC_FEE_051
    final Stream<DynamicTest> updateToRemoveOneOfTheKeysFromFeeExemptKeysList() {
        final var key = "key";
        final var key2 = "key2";

        return hapiTest(flattened(
                newKeyNamed(key),
                newKeyNamed(key2),

                // Create a topic with two keys in feeExemptKeyList
                createTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR))
                        .feeExemptKeys(key, key2)
                        .adminKeyName(ADMIN_KEY),
                getTopicInfo(TOPIC).hasFeeExemptKeys(List.of(key, key2)),

                // Update the topic to remove one of the keys
                updateTopic(TOPIC).feeExemptKeys(key2).signedByPayerAnd(ADMIN_KEY),
                getTopicInfo(TOPIC).hasFeeExemptKeys(List.of(key2))));
    }

    @HapiTest
    @DisplayName("to remove all keys from fee exempt key list key")
    // TOPIC_FEE_052
    final Stream<DynamicTest> updateToRemoveAllFeeExemptKeyListKeys() {
        final var key = "key";
        final var key2 = "key2";

        return hapiTest(flattened(
                newKeyNamed(key),
                newKeyNamed(key2),

                // Create a topic with two keys in feeExemptKeyList
                createTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR))
                        .feeExemptKeys(key, key2)
                        .adminKeyName(ADMIN_KEY),
                getTopicInfo(TOPIC).hasFeeExemptKeys(List.of(key, key2)),

                // Update the topic to remove all the fee except keys
                updateTopic(TOPIC).withEmptyFeeExemptKeyList().signedByPayerAnd(ADMIN_KEY),
                getTopicInfo(TOPIC).hasEmptyFeeExemptKeyList()));
    }

    @HapiTest
    @DisplayName("fee schedule key and sign with the old one")
    // TOPIC_FEE_064
    final Stream<DynamicTest> updateFeeScheduleKeySignWithOld() {
        return hapiTest(flattened(
                // Create a topic and verify that the keys are correct
                createTopic(TOPIC).adminKeyName(ADMIN_KEY).feeScheduleKeyName(FEE_SCHEDULE_KEY),
                getTopicInfo(TOPIC).hasAdminKey(ADMIN_KEY).hasFeeScheduleKey(FEE_SCHEDULE_KEY),

                // Update the fee schedule and sign with the old key
                updateTopic(TOPIC)
                        .feeScheduleKeyName(FEE_SCHEDULE_KEY2)
                        .signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY)
                        .hasKnownStatus(ResponseCodeEnum.INVALID_SIGNATURE)));
    }
}
