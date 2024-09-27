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

import static com.hedera.node.app.hapi.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedConsensusHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.expectedConsensusFixedHTSFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.expectedConsensusFixedHbarFee;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createHollow;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FEE_SCHEDULE_KEY_CANNOT_BE_UPDATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.utility.CommonUtils;
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
    // TODO: remove all TOPIC_FEE_0.. comments

    private static final long FIFTY_BILLION = 50_000_000_000L;

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle lifecycle) {
        lifecycle.doAdhoc(setupBaseForUpdate());
    }

    @HapiTest
    @DisplayName("the custom fee to 50 billion(max HBAR) + 1")
    // TOPIC_FEE_045
    final Stream<DynamicTest> updateTheCustomFeeTo50BillionPlusOne() {
        return hapiTest(flattened(
                // Create a topic and verify custom fee is correct
                createTopic(TOPIC)
                        .adminKeyName(ADMIN_KEY)
                        .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                        .withConsensusCustomFee(fixedConsensusHtsFee(1, TOKEN, COLLECTOR)),
                getTopicInfo(TOPIC).hasCustomFee(expectedConsensusFixedHTSFee(1, TOKEN, COLLECTOR)),

                // Update the custom fee to 50 billion(max HBAR) + 1
                updateTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHbarFee(FIFTY_BILLION + 1, COLLECTOR))
                        .signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY),
                getTopicInfo(TOPIC).hasCustomFee(expectedConsensusFixedHbarFee(FIFTY_BILLION + 1, COLLECTOR))));
    }

    @HapiTest
    @DisplayName("the fee schedule key")
    // TOPIC_FEE_046
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
    // TOPIC_FEE_047
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
    // TOPIC_FEE_048
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
    // TOPIC_FEE_049
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
    // TOPIC_FEE_050
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
    @DisplayName("to reach the limit of 10 custom fees")
    // TOPIC_FEE_051
    final Stream<DynamicTest> updateCustomFeesToReachTheLimit() {
        return hapiTest(
                // Create a topic with one custom fees
                createTopic(TOPIC)
                        .adminKeyName(ADMIN_KEY)
                        .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR)),
                getTopicInfo(TOPIC).hasCustomFee(expectedConsensusFixedHbarFee(1, COLLECTOR)),

                // Update the topic to add 9 more custom fees(reaching the limit of 10 custom fees)
                cryptoCreate("collector2"),
                cryptoCreate("collector3"),
                cryptoCreate("collector4"),
                cryptoCreate("collector5"),
                cryptoCreate("collector6"),
                cryptoCreate("collector7"),
                cryptoCreate("collector8"),
                cryptoCreate("collector9"),
                cryptoCreate("collector10"),
                updateTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector2"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector3"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector4"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector5"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector6"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector7"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector8"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector9"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector10"))
                        .signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY),

                // Verify that the topic consist of all the custom fees
                getTopicInfo(TOPIC)
                        .hasCustomFee(expectedConsensusFixedHbarFee(1, COLLECTOR))
                        .hasCustomFee(expectedConsensusFixedHbarFee(1, "collector2"))
                        .hasCustomFee(expectedConsensusFixedHbarFee(1, "collector3"))
                        .hasCustomFee(expectedConsensusFixedHbarFee(1, "collector4"))
                        .hasCustomFee(expectedConsensusFixedHbarFee(1, "collector5"))
                        .hasCustomFee(expectedConsensusFixedHbarFee(1, "collector6"))
                        .hasCustomFee(expectedConsensusFixedHbarFee(1, "collector7"))
                        .hasCustomFee(expectedConsensusFixedHbarFee(1, "collector8"))
                        .hasCustomFee(expectedConsensusFixedHbarFee(1, "collector9"))
                        .hasCustomFee(expectedConsensusFixedHbarFee(1, "collector10")));
    }

    @HapiTest
    @DisplayName("to add a custom fee with a hollow account as a collector")
    // TOPIC_FEE_052
    final Stream<DynamicTest> updateToAddCustomFeeWithHollowAsCollector() {
        final var hollowAccount = "hollowAccount";
        return hapiTest(
                // Create a topic without custom fees
                createTopic(TOPIC).adminKeyName(ADMIN_KEY).feeScheduleKeyName(FEE_SCHEDULE_KEY),
                createHollow(1, i -> hollowAccount),

                // Update the topic to have the hollow account as a collector
                updateTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, hollowAccount))
                        .signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY),
                getTopicInfo(TOPIC).hasCustomFee(expectedConsensusFixedHbarFee(1, hollowAccount)));
    }

    // TODO: talk with Ani - rename the test in notion from TOPIC_FEE_057 to TOPIC_FEE_047
    // TODO: INVALID_CUSTOM_FEE_COLLECTOR instead of success
    // TODO: move to the negative cases
    @HapiTest
    @DisplayName("to add a custom fee with a invalid collector")
    // TOPIC_FEE_053
    final Stream<DynamicTest> updateToAddCustomFeeWithInvalidCollector() {
        final byte[] publicKey = CommonUtils.unhex("0000000000000000000000000000000000000000000000000000000000000000");
        final ByteString evmAddress = ByteStringUtils.wrapUnsafely(recoverAddressFromPubKey(publicKey));
        final var accountId = HapiPropertySource.asAccount(evmAddress);

        return hapiTest(
                // Create a topic without custom fees
                createTopic(TOPIC).adminKeyName(ADMIN_KEY).feeScheduleKeyName(FEE_SCHEDULE_KEY),

                // Update the topic to have an empty address as a collector
                updateTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, accountId))
                        .signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY)
                        .hasKnownStatus(INVALID_CUSTOM_FEE_COLLECTOR));
    }

    @HapiTest
    @DisplayName("to add a custom fee with a contract as a collector")
    // TOPIC_FEE_054
    final Stream<DynamicTest> updateToAddCustomFeeWithContractAsCollector() {
        final var contract = "CallingContract";

        return hapiTest(
                // Create a topic without custom fees
                createTopic(TOPIC).adminKeyName(ADMIN_KEY).feeScheduleKeyName(FEE_SCHEDULE_KEY),
                uploadInitCode(contract),
                contractCreate(contract),

                // Update the topic to have contract as a collector
                updateTopic(TOPIC).withConsensusCustomFee(fixedConsensusHbarFee(1, contract)),
                getTopicInfo(TOPIC).hasCustomFee(expectedConsensusFixedHbarFee(1, contract)));
    }

    @HapiTest
    @DisplayName("to empty the custom fees with deleted collector")
    // TOPIC_FEE_055
    final Stream<DynamicTest> updateToEmptyCustomFeesWithDeletedCollector() {
        final var collector = "collector";
        return hapiTest(
                cryptoCreate(collector),

                // Create a topic with custom fees
                createTopic(TOPIC)
                        .adminKeyName(ADMIN_KEY)
                        .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, collector)),

                // Delete the collector account
                cryptoDelete(collector),

                // Delete the custom fee
                updateTopic(TOPIC).withEmptyCustomFee().signedByPayerAnd(ADMIN_KEY),
                getTopicInfo(TOPIC).hasNoCustomFee());
    }

    // TODO: when fee schedule was not set on create - we shouldn't be able to set it on update? Talk with Ani
    @HapiTest
    @DisplayName("the fee schedule key when it was empty on create")
    // TOPIC_FEE_056
    final Stream<DynamicTest> updateTheFeeScheduleKeyWhenWasEmpty() {
        return hapiTest(
                // Create a topic without custom fees
                createTopic(TOPIC).adminKeyName(ADMIN_KEY),
                updateTopic(TOPIC)
                        .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                        .signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY)
                        .hasKnownStatus(FEE_SCHEDULE_KEY_CANNOT_BE_UPDATED));
    }

    @HapiTest
    @DisplayName("the fee schedule key to empty")
    // TOPIC_FEE_057
    final Stream<DynamicTest> updateTheFeeScheduleKeyToEmpty() {
        return hapiTest(flattened(
                // Create a topic and verify that the keys are correct
                createTopic(TOPIC).adminKeyName(ADMIN_KEY).feeScheduleKeyName(FEE_SCHEDULE_KEY),
                getTopicInfo(TOPIC).hasAdminKey(ADMIN_KEY).hasFeeScheduleKey(FEE_SCHEDULE_KEY),

                // Update the fee schedule to remove the fee schedule key
                updateTopic(TOPIC).withEmptyFeeScheduleKey().signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY),
                getTopicInfo(TOPIC).hasNoFeeScheduleKey()));
    }

    @HapiTest
    @DisplayName("the FeeExemptKeyList")
    // TOPIC_FEE_058
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
    // TOPIC_FEE_059
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
    // TOPIC_FEE_060
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
    // TOPIC_FEE_061
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
    @DisplayName("to replace all fee exempt keys")
    // TOPIC_FEE_062
    final Stream<DynamicTest> updateToReplaceAllKeys() {
        final var key = "key";
        final var key2 = "key2";
        final var key3 = "key3";
        final var key4 = "key4";

        return hapiTest(flattened(
                newKeyNamed(key),
                newKeyNamed(key2),
                newKeyNamed(key3),
                newKeyNamed(key4),

                // Create a topic with two keys in feeExemptKeyList
                createTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR))
                        .feeExemptKeys(key, key2)
                        .adminKeyName(ADMIN_KEY),
                getTopicInfo(TOPIC).hasFeeExemptKeys(List.of(key, key2)),

                // Update the topic to remove all the fee except keys
                updateTopic(TOPIC).feeExemptKeys(key3, key4).signedByPayerAnd(ADMIN_KEY),
                getTopicInfo(TOPIC).hasFeeExemptKeys(List.of(key3, key4))));
    }

    @HapiTest
    @DisplayName("a topic that contains deleted key")
    // TOPIC_FEE_063
    final Stream<DynamicTest> updateTopicWhenKeyInFeeExemptKeyListIsDeleted() {
        final var alice = "alice";
        return hapiTest(
                cryptoCreate(alice),

                // Create a topic with fee exempt key alice
                createTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR))
                        .feeExemptKeys(alice)
                        .adminKeyName(ADMIN_KEY),

                // Delete alice
                cryptoDelete(alice),

                // Do a dummy update and verify that it updates successfully
                updateTopic(TOPIC).memo("dummy change").signedByPayerAnd(ADMIN_KEY));
    }

    @HapiTest
    @DisplayName("to update the fee schedule key")
    // TOPIC_FEE_064
    final Stream<DynamicTest> updateTheFeeScheduleKey() {
        return hapiTest(
                // Create a topic and verify that the keys are correct
                createTopic(TOPIC).adminKeyName(ADMIN_KEY).feeScheduleKeyName(FEE_SCHEDULE_KEY),
                getTopicInfo(TOPIC).hasAdminKey(ADMIN_KEY).hasFeeScheduleKey(FEE_SCHEDULE_KEY),

                // Update the fee schedule and sign with the new key
                updateTopic(TOPIC).feeScheduleKeyName(FEE_SCHEDULE_KEY2).signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY2),
                getTopicInfo(TOPIC).hasFeeScheduleKey(FEE_SCHEDULE_KEY2));
    }

    // TODO: talk with Ani - when there is a invalid key it cannot sign the transaction. Thus throwing INVALID_SIGNATURE
    @HapiTest
    @DisplayName("to update the fee schedule key with zero address key")
    // TOPIC_FEE_065
    final Stream<DynamicTest> updateFeeScheduleKeyWithZeroAddressKey() {
        final var zeroAddressKeyName = "zeroAddressKey";
        final var zeroAddressKey = Key.newBuilder()
                .setEd25519(ByteString.fromHex("0000000000000000000000000000000000000000000000000000000000000000"))
                .build();

        return hapiTest(
                // Create a topic with a fee schedule key
                createTopic(TOPIC).adminKeyName(ADMIN_KEY).feeScheduleKeyName(FEE_SCHEDULE_KEY),

                // Create a zero address key
                withOpContext((spec, opLog) -> spec.registry().saveKey(zeroAddressKeyName, zeroAddressKey)),

                // Update the topic to use the zero address key as fee schedule key
                updateTopic(TOPIC)
                        .feeScheduleKeyName(zeroAddressKeyName)
                        .signedByPayerAnd(ADMIN_KEY)
                        .hasKnownStatus(INVALID_SIGNATURE));
    }

    @HapiTest
    @DisplayName("to add a custom fee to a topic without")
    // TOPIC_FEE_066
    final Stream<DynamicTest> updateToAddACustomFee() {
        return hapiTest(
                // create a topic without a custom fee
                createTopic(TOPIC).adminKeyName(ADMIN_KEY),

                // Update the topic to add a custom fee
                updateTopic(TOPIC).withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR)),
                getTopicInfo(TOPIC).hasCustomFee(expectedConsensusFixedHbarFee(1, COLLECTOR)));
    }

    // TODO: do we need to explicitly delete the custom fees if we delete the feeScheduleKey?
    @HapiTest
    @DisplayName("to remove the fee schedule key and the custom fee should not be deleted")
    // TOPIC_FEE_068
    final Stream<DynamicTest> updateToRemoveTheFeeScheduleKeyCustomFeeShouldStay() {
        return hapiTest(
                // Create a topic with a fee schedule key and a custom fee
                createTopic(TOPIC)
                        .adminKeyName(ADMIN_KEY)
                        .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR)),

                // Update the fee schedule to remove the fee schedule key
                updateTopic(TOPIC).withEmptyFeeScheduleKey().signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY),
                getTopicInfo(TOPIC).hasNoFeeScheduleKey().hasCustomFee(expectedConsensusFixedHbarFee(1, COLLECTOR)));
    }

    // TODO: this should throw FEE_SCHEDULE_KEY_CANNOT_BE_UPDATED
    @HapiTest
    @DisplayName("to delete the fee schedule key and then setting the same key back")
    // TOPIC_FEE_069
    final Stream<DynamicTest> updateToDeleteTheFeeScheduleKeyAndSettingItBack() {
        return hapiTest(
                // Create a topic with fee schedule key
                createTopic(TOPIC).feeScheduleKeyName(FEE_SCHEDULE_KEY).adminKeyName(ADMIN_KEY),

                // Delete the fee schedule key
                updateTopic(TOPIC).withEmptyFeeScheduleKey().signedByPayerAnd(ADMIN_KEY),

                // Try to add the fee schedule key back
                updateTopic(TOPIC).feeScheduleKeyName(FEE_SCHEDULE_KEY).signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY));
    }

    @HapiTest
    @DisplayName("to update the fee schedule key with zero address key sign with the old key")
    // TOPIC_FEE_070
    final Stream<DynamicTest> updateTheFeeScheduleKeyWithZeroAddressSignWithOldKey() {
        final var zeroAddressKeyName = "zeroAddressKey";
        final var zeroAddressKey = Key.newBuilder()
                .setEd25519(ByteString.fromHex("0000000000000000000000000000000000000000000000000000000000000000"))
                .build();

        return hapiTest(
                // Create a topic with a fee schedule key
                createTopic(TOPIC).adminKeyName(ADMIN_KEY).feeScheduleKeyName(FEE_SCHEDULE_KEY),

                // Create a zero address key
                withOpContext((spec, opLog) -> spec.registry().saveKey(zeroAddressKeyName, zeroAddressKey)),

                // Update the topic to use the zero address key as fee schedule key
                updateTopic(TOPIC)
                        .feeScheduleKeyName(zeroAddressKeyName)
                        .signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY)
                        .hasKnownStatus(INVALID_SIGNATURE));
    }

    // TODO: talk with Ani - if we remove the fee schedule key - can only the admin sign?
    @HapiTest
    @DisplayName("to remove the fee schedule key without the fee schedule key to sign")
    // TOPIC_FEE_071
    final Stream<DynamicTest> updateToRemoveTheFeeScheduleKeyWithoutFeeScheduleToSign() {
        return hapiTest(
                // Create a topic with a fee schedule key and a custom fee
                createTopic(TOPIC).adminKeyName(ADMIN_KEY).feeScheduleKeyName(FEE_SCHEDULE_KEY),

                // Update the fee schedule to remove the fee schedule key
                updateTopic(TOPIC).withEmptyFeeScheduleKey().signedByPayerAnd(ADMIN_KEY),
                getTopicInfo(TOPIC).hasNoFeeScheduleKey());
    }

    @HapiTest
    @DisplayName("fee schedule key and sign with the old one")
    // TOPIC_FEE_073
    final Stream<DynamicTest> updateFeeScheduleKeySignWithOld() {
        return hapiTest(flattened(
                // Create a topic and verify that the keys are correct
                createTopic(TOPIC).adminKeyName(ADMIN_KEY).feeScheduleKeyName(FEE_SCHEDULE_KEY),
                getTopicInfo(TOPIC).hasAdminKey(ADMIN_KEY).hasFeeScheduleKey(FEE_SCHEDULE_KEY),

                // Update the fee schedule and sign with the old key
                updateTopic(TOPIC)
                        .feeScheduleKeyName(FEE_SCHEDULE_KEY2)
                        .signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY)
                        .hasKnownStatus(INVALID_SIGNATURE)));
    }

    @HapiTest
    @DisplayName("to remove the fee schedule key without the admin key to sign")
    // TOPIC_FEE_074
    final Stream<DynamicTest> updateToRemoveTheFeeScheduleKeyWithoutAdminToSign() {
        return hapiTest(
                // Create a topic with a fee schedule key and a custom fee
                createTopic(TOPIC).adminKeyName(ADMIN_KEY).feeScheduleKeyName(FEE_SCHEDULE_KEY),

                // Update the fee schedule to remove the fee schedule key
                updateTopic(TOPIC)
                        .withEmptyFeeScheduleKey()
                        .signedByPayerAnd(FEE_SCHEDULE_KEY)
                        .hasKnownStatus(INVALID_SIGNATURE));
    }

    @HapiTest
    @DisplayName("to reach the limit + 1 custom fees")
    // TOPIC_FEE_075
    final Stream<DynamicTest> updateCustomFeesToReachTheLimitPlusOne() {
        return hapiTest(
                // Create a topic with one custom fees
                createTopic(TOPIC)
                        .adminKeyName(ADMIN_KEY)
                        .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR)),
                getTopicInfo(TOPIC).hasCustomFee(expectedConsensusFixedHbarFee(1, COLLECTOR)),

                // Update the topic to add 10 more custom fees(reaching the limit + 1 custom fees)
                cryptoCreate("collector2"),
                cryptoCreate("collector3"),
                cryptoCreate("collector4"),
                cryptoCreate("collector5"),
                cryptoCreate("collector6"),
                cryptoCreate("collector7"),
                cryptoCreate("collector8"),
                cryptoCreate("collector9"),
                cryptoCreate("collector10"),
                cryptoCreate("collector11"),
                updateTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector2"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector3"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector4"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector5"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector6"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector7"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector8"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector9"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector10"))
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, "collector11"))
                        .signedByPayerAnd(ADMIN_KEY, FEE_SCHEDULE_KEY)
                        .hasKnownStatus(CUSTOM_FEES_LIST_TOO_LONG));
    }

    // TODO: see this is dept.
    /*
    Currently this is working and it shouldn't. I should have a check to see if the fee schedule key has signed
    Also check if we sign with different schedule key - will I still be able to update the custom fees
    Check that as well.
     */
    @HapiTest
    @DisplayName("the custom fee without having a custom fee and a schedule key")
    // TOPIC_FEE_076
    final Stream<DynamicTest> updateCustomFeeWithoutHavingCustomFeeAndScheduleKey() {
        return hapiTest(flattened(
                // Create a topic
                createTopic(TOPIC).adminKeyName(ADMIN_KEY),

                // Update the custom fee to add custom fee
                updateTopic(TOPIC)
                        .withConsensusCustomFee(fixedConsensusHbarFee(1, COLLECTOR))
                        .signedByPayerAnd(ADMIN_KEY)));
    }

    // TODO: talk with Ani - this will be success because we are not setting anything and we ignore the field
    @HapiTest
    @DisplayName("to remove the fee schedule key that doesn't exists")
    // TOPIC_FEE_078
    final Stream<DynamicTest> removeTheFeeScheduleKeyWhenItDoNotExists() {
        return hapiTest(flattened(
                // Create a topic
                createTopic(TOPIC).adminKeyName(ADMIN_KEY),

                // Update the custom fee to add custom fee
                updateTopic(TOPIC).withEmptyFeeScheduleKey().signedByPayerAnd(ADMIN_KEY)));
    }

    // TODO: same as 76
    @HapiTest
    @DisplayName("to remove the custom fees sign with admin key only")
    // TOPIC_FEE_079
    final Stream<DynamicTest> removeCustomFeeSignWithAdminOnly() {
        return hapiTest(flattened(
                // Create a topic and verify custom fee is correct
                createTopic(TOPIC)
                        .adminKeyName(ADMIN_KEY)
                        .feeScheduleKeyName(FEE_SCHEDULE_KEY)
                        .withConsensusCustomFee(fixedConsensusHtsFee(1, TOKEN, COLLECTOR)),

                // Update the custom fee and verify that it's updated
                updateTopic(TOPIC).withEmptyCustomFee().signedByPayerAnd(ADMIN_KEY)));
    }

    /*
    @HapiTest
    // TODO: fill the following
    @DisplayName("")
    // TOPIC_FEE_0
    final Stream<DynamicTest> test() {
        return hapiTest(

        );
    }
    */

}
