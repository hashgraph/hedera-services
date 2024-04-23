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

package com.hedera.services.bdd.suites.consensus;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicUpdate;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class TopicUpdateSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(TopicUpdateSuite.class);

    private static final long validAutoRenewPeriod = 7_000_000L;
    private static final long defaultMaxLifetime =
            Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));

    public static final Key IMMUTABILITY_SENTINEL_KEY =
            Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();

    public static void main(String... args) {
        new TopicUpdateSuite().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                pureCheckFails(),
                validateMultipleFields(),
                topicUpdateSigReqsEnforcedAtConsensus(),
                updateSubmitKeyToDiffKey(),
                updateAdminKeyToDiffKey(),
                updateAdminKeyToEmpty(),
                updateMultipleFields(),
                expirationTimestampIsValidated(),
                updateSubmitKeyOnTopicWithNoAdminKeyFails(),
                clearingAdminKeyWhenAutoRenewAccountPresent(),
                feeAsExpected(),
                updateExpiryOnTopicWithNoAdminKey(),
                updateToMissingTopicFails(),
                canRemoveSubmitKeyDuringUpdate(),
                updateAdminKeyToHollowAccountAlias(),
                updateSubmitKeyToHollowAccountAlias(),
                updateRemovedSubmitKeyToHollowAccountAlias());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @HapiTest
    final HapiSpec pureCheckFails() {
        return defaultHapiSpec("testTopic")
                .given()
                .when()
                .then(updateTopic("0.0.1").hasPrecheck(INVALID_TOPIC_ID));
    }

    @HapiTest
    final HapiSpec updateToMissingTopicFails() {
        return defaultHapiSpec("updateToMissingTopicFails")
                .given()
                .when()
                .then(updateTopic("1.2.3").hasKnownStatus(INVALID_TOPIC_ID));
    }

    @HapiTest
    public HapiSpec idVariantsTreatedAsExpected() {
        final var autoRenewAccount = "autoRenewAccount";
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(cryptoCreate(autoRenewAccount), cryptoCreate("replacementAccount"), newKeyNamed("adminKey"))
                .when(createTopic("topic").adminKeyName("adminKey").autoRenewAccountId(autoRenewAccount))
                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> updateTopic("topic")
                        .autoRenewAccountId("replacementAccount")));
    }

    @HapiTest
    final HapiSpec validateMultipleFields() {
        byte[] longBytes = new byte[1000];
        Arrays.fill(longBytes, (byte) 33);
        String longMemo = new String(longBytes, StandardCharsets.UTF_8);
        return defaultHapiSpec("validateMultipleFields")
                .given(newKeyNamed("adminKey"), createTopic("testTopic").adminKeyName("adminKey"))
                .when()
                .then(
                        updateTopic("testTopic")
                                .adminKey(NONSENSE_KEY)
                                .hasPrecheckFrom(BAD_ENCODING, OK)
                                .hasKnownStatus(BAD_ENCODING),
                        updateTopic("testTopic").submitKey(NONSENSE_KEY).hasKnownStatus(BAD_ENCODING),
                        updateTopic("testTopic").topicMemo(longMemo).hasKnownStatus(MEMO_TOO_LONG),
                        updateTopic("testTopic").topicMemo(ZERO_BYTE_MEMO).hasKnownStatus(INVALID_ZERO_BYTE_IN_STRING),
                        updateTopic("testTopic").autoRenewPeriod(0).hasKnownStatus(AUTORENEW_DURATION_NOT_IN_RANGE),
                        updateTopic("testTopic")
                                .autoRenewPeriod(Long.MAX_VALUE)
                                .hasKnownStatus(AUTORENEW_DURATION_NOT_IN_RANGE));
    }

    @HapiTest
    final HapiSpec topicUpdateSigReqsEnforcedAtConsensus() {
        long PAYER_BALANCE = 199_999_999_999L;
        Function<String[], HapiTopicUpdate> updateTopicSignedBy = (signers) -> updateTopic("testTopic")
                .payingWith("payer")
                .adminKey("newAdminKey")
                .autoRenewAccountId("newAutoRenewAccount")
                .signedBy(signers);

        return defaultHapiSpec("topicUpdateSigReqsEnforcedAtConsensus")
                .given(
                        newKeyNamed("oldAdminKey"),
                        cryptoCreate("oldAutoRenewAccount"),
                        newKeyNamed("newAdminKey"),
                        cryptoCreate("newAutoRenewAccount"),
                        cryptoCreate("payer").balance(PAYER_BALANCE),
                        createTopic("testTopic").adminKeyName("oldAdminKey").autoRenewAccountId("oldAutoRenewAccount"))
                .when(
                        updateTopicSignedBy
                                .apply(new String[] {"payer", "oldAdminKey"})
                                .hasKnownStatus(INVALID_SIGNATURE),
                        updateTopicSignedBy
                                .apply(new String[] {"payer", "oldAdminKey", "newAdminKey"})
                                .hasKnownStatus(INVALID_SIGNATURE),
                        updateTopicSignedBy
                                .apply(new String[] {"payer", "oldAdminKey", "newAutoRenewAccount"})
                                .hasKnownStatus(INVALID_SIGNATURE),
                        updateTopicSignedBy
                                .apply(new String[] {"payer", "newAdminKey", "newAutoRenewAccount"})
                                .hasKnownStatus(INVALID_SIGNATURE),
                        updateTopicSignedBy
                                .apply(new String[] {"payer", "oldAdminKey", "newAdminKey", "newAutoRenewAccount"})
                                .hasKnownStatus(SUCCESS))
                .then(getTopicInfo("testTopic")
                        .logged()
                        .hasAdminKey("newAdminKey")
                        .hasAutoRenewAccount("newAutoRenewAccount"));
    }

    @HapiTest
    final HapiSpec updateSubmitKeyToDiffKey() {
        return defaultHapiSpec("updateSubmitKeyToDiffKey")
                .given(
                        newKeyNamed("adminKey"),
                        newKeyNamed("submitKey"),
                        createTopic("testTopic").adminKeyName("adminKey"))
                .when(updateTopic("testTopic").submitKey("submitKey"))
                .then(getTopicInfo("testTopic")
                        .hasSubmitKey("submitKey")
                        .hasAdminKey("adminKey")
                        .logged());
    }

    @HapiTest
    final HapiSpec canRemoveSubmitKeyDuringUpdate() {
        return defaultHapiSpec("updateSubmitKeyToDiffKey")
                .given(
                        newKeyNamed("adminKey"),
                        newKeyNamed("submitKey"),
                        createTopic("testTopic").adminKeyName("adminKey").submitKeyName("submitKey"))
                .when(
                        submitMessageTo("testTopic").message("message"),
                        updateTopic("testTopic").submitKey(EMPTY_KEY))
                .then(
                        getTopicInfo("testTopic").hasNoSubmitKey().hasAdminKey("adminKey"),
                        submitMessageTo("testTopic").message("message").logged());
    }

    @HapiTest
    final HapiSpec updateAdminKeyToDiffKey() {
        return defaultHapiSpec("updateAdminKeyToDiffKey")
                .given(
                        newKeyNamed("adminKey"),
                        newKeyNamed("updateAdminKey"),
                        createTopic("testTopic").adminKeyName("adminKey"))
                .when(updateTopic("testTopic").adminKey("updateAdminKey"))
                .then(getTopicInfo("testTopic").hasAdminKey("updateAdminKey").logged());
    }

    @HapiTest
    final HapiSpec updateAdminKeyToEmpty() {
        return defaultHapiSpec("updateAdminKeyToEmpty")
                .given(newKeyNamed("adminKey"), createTopic("testTopic").adminKeyName("adminKey"))
                /* if adminKey is empty list should clear adminKey */
                .when(updateTopic("testTopic").adminKey(EMPTY_KEY))
                .then(getTopicInfo("testTopic").hasNoAdminKey().logged());
    }

    @HapiTest
    final HapiSpec updateMultipleFields() {
        long expirationTimestamp = Instant.now().getEpochSecond() + 10000000; // more than default.autorenew
        // .secs=7000000
        return defaultHapiSpec("updateMultipleFields")
                .given(
                        newKeyNamed("adminKey"),
                        newKeyNamed("adminKey2"),
                        newKeyNamed("submitKey"),
                        cryptoCreate("autoRenewAccount"),
                        cryptoCreate("nextAutoRenewAccount"),
                        createTopic("testTopic")
                                .topicMemo("initialmemo")
                                .adminKeyName("adminKey")
                                .autoRenewPeriod(validAutoRenewPeriod)
                                .autoRenewAccountId("autoRenewAccount"))
                .when(updateTopic("testTopic")
                        .topicMemo("updatedmemo")
                        .submitKey("submitKey")
                        .adminKey("adminKey2")
                        .expiry(expirationTimestamp)
                        .autoRenewPeriod(validAutoRenewPeriod + 5_000L)
                        .autoRenewAccountId("nextAutoRenewAccount")
                        .hasKnownStatus(SUCCESS))
                .then(getTopicInfo("testTopic")
                        .hasMemo("updatedmemo")
                        .hasSubmitKey("submitKey")
                        .hasAdminKey("adminKey2")
                        .hasExpiry(expirationTimestamp)
                        .hasAutoRenewPeriod(validAutoRenewPeriod + 5_000L)
                        .hasAutoRenewAccount("nextAutoRenewAccount")
                        .logged());
    }

    @HapiTest
    final HapiSpec expirationTimestampIsValidated() {
        long now = Instant.now().getEpochSecond();
        return defaultHapiSpec("expirationTimestampIsValidated")
                .given(createTopic("testTopic").autoRenewPeriod(validAutoRenewPeriod))
                .when()
                .then(
                        updateTopic("testTopic")
                                .expiry(now - 1) // less than consensus time
                                .hasKnownStatusFrom(INVALID_EXPIRATION_TIME, EXPIRATION_REDUCTION_NOT_ALLOWED),
                        updateTopic("testTopic")
                                .expiry(now + 1000) // 1000 < autoRenewPeriod
                                .hasKnownStatus(EXPIRATION_REDUCTION_NOT_ALLOWED));
    }

    /* If admin key is not set, only expiration timestamp updates are allowed */
    @HapiTest
    final HapiSpec updateExpiryOnTopicWithNoAdminKey() {
        long overlyDistantNewExpiry = Instant.now().getEpochSecond() + defaultMaxLifetime + 12_345L;
        long reasonableNewExpiry = Instant.now().getEpochSecond() + defaultMaxLifetime - 12_345L;
        return defaultHapiSpec("updateExpiryOnTopicWithNoAdminKey")
                .given(createTopic("testTopic"))
                .when(
                        updateTopic("testTopic").expiry(overlyDistantNewExpiry).hasKnownStatus(INVALID_EXPIRATION_TIME),
                        updateTopic("testTopic").expiry(reasonableNewExpiry))
                .then(getTopicInfo("testTopic").hasExpiry(reasonableNewExpiry));
    }

    @HapiTest
    final HapiSpec clearingAdminKeyWhenAutoRenewAccountPresent() {
        return defaultHapiSpec("clearingAdminKeyWhenAutoRenewAccountPresent")
                .given(
                        newKeyNamed("adminKey"),
                        cryptoCreate("autoRenewAccount"),
                        createTopic("testTopic").adminKeyName("adminKey").autoRenewAccountId("autoRenewAccount"))
                .when(
                        updateTopic("testTopic").adminKey(EMPTY_KEY).hasKnownStatus(AUTORENEW_ACCOUNT_NOT_ALLOWED),
                        updateTopic("testTopic").adminKey(EMPTY_KEY).autoRenewAccountId("0.0.0"))
                .then(getTopicInfo("testTopic").hasNoAdminKey());
    }

    @HapiTest
    final HapiSpec updateSubmitKeyOnTopicWithNoAdminKeyFails() {
        return defaultHapiSpec("updateSubmitKeyOnTopicWithNoAdminKeyFails")
                .given(newKeyNamed("submitKey"), createTopic("testTopic"))
                .when(updateTopic("testTopic").submitKey("submitKey").hasKnownStatus(UNAUTHORIZED))
                .then();
    }

    @HapiTest
    final HapiSpec feeAsExpected() {
        return defaultHapiSpec("feeAsExpected")
                .given(
                        cryptoCreate("autoRenewAccount"),
                        cryptoCreate("payer"),
                        createTopic("testTopic")
                                .autoRenewAccountId("autoRenewAccount")
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS - 1)
                                .adminKeyName("payer"))
                .when(updateTopic("testTopic")
                        .payingWith("payer")
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                        .via("updateTopic"))
                .then(validateChargedUsdWithin("updateTopic", 0.00022, 3.0));
    }

    @HapiTest
    final HapiSpec updateAdminKeyToHollowAccountAlias() {
        return defaultHapiSpec("updateAdminKeyToHollowAccountAlias")
                .given(
                        // Create an ECDSA key
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed("initialKey"),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_MILLION_HBARS),
                        // Create the topic
                        createTopic("testTopic").adminKeyName("initialKey"))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    spec.registry()
                            .saveAccountAlias(
                                    SECP_256K1_SOURCE_KEY,
                                    AccountID.newBuilder().setAlias(evmAddress).build());

                    allRunFor(
                            spec,
                            // Transfer money to the alias --> creates HOLLOW ACCOUNT
                            cryptoTransfer(
                                    movingHbar(ONE_HUNDRED_HBARS).distributing(TOKEN_TREASURY, SECP_256K1_SOURCE_KEY)),
                            // Verify that the account is created and is hollow
                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                    .has(accountWith().hasEmptyKey()),
                            // Update the topic with the ECDSA key as admin key
                            updateTopic("testTopic").adminKey(SECP_256K1_SOURCE_KEY));
                }))
                .then(getTopicInfo("testTopic").hasAdminKey(SECP_256K1_SOURCE_KEY));
    }

    @HapiTest
    final HapiSpec updateSubmitKeyToHollowAccountAlias() {
        return defaultHapiSpec("updateSubmitKeyToHollowAccountAlias")
                .given(
                        // Create an ECDSA key
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed("adminKey"),
                        newKeyNamed("submitKey"),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_MILLION_HBARS),
                        // Create the topic
                        createTopic("testTopic").adminKeyName("adminKey").submitKeyName("submitKey"))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    spec.registry()
                            .saveAccountAlias(
                                    SECP_256K1_SOURCE_KEY,
                                    AccountID.newBuilder().setAlias(evmAddress).build());

                    allRunFor(
                            spec,
                            // Transfer money to the alias --> creates HOLLOW ACCOUNT
                            cryptoTransfer(
                                    movingHbar(ONE_HUNDRED_HBARS).distributing(TOKEN_TREASURY, SECP_256K1_SOURCE_KEY)),
                            // Verify that the account is created and is hollow
                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                    .has(accountWith().hasEmptyKey()),
                            // Update the topic with the ECDSA key as admin key
                            updateTopic("testTopic").submitKey(SECP_256K1_SOURCE_KEY));
                }))
                .then(getTopicInfo("testTopic").hasSubmitKey(SECP_256K1_SOURCE_KEY));
    }

    @HapiTest
    final HapiSpec updateSubmitKeyWithNoAdminKeyToHollowAccountAliasShouldFail() {
        return defaultHapiSpec("updateSubmitKeyWithNoAdminKeyToHollowAccountAliasShouldFail")
                .given(
                        // Create an ECDSA key
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_MILLION_HBARS),
                        // Create the topic with no admin key
                        createTopic("testTopic"))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    spec.registry()
                            .saveAccountAlias(
                                    SECP_256K1_SOURCE_KEY,
                                    AccountID.newBuilder().setAlias(evmAddress).build());

                    allRunFor(
                            spec,
                            // Transfer money to the alias --> creates HOLLOW ACCOUNT
                            cryptoTransfer(
                                    movingHbar(ONE_HUNDRED_HBARS).distributing(TOKEN_TREASURY, SECP_256K1_SOURCE_KEY)),
                            // Verify that the account is created and is hollow
                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                    .has(accountWith().hasEmptyKey()),
                            // Update the topic with the ECDSA key as submit key but there is no admin key, so it should
                            // fail
                            updateTopic("testTopic")
                                    .submitKey(SECP_256K1_SOURCE_KEY)
                                    .hasKnownStatus(UNAUTHORIZED));
                }))
                .then();
    }

    @HapiTest
    final HapiSpec updateRemovedSubmitKeyToHollowAccountAlias() {
        return defaultHapiSpec("updateRemovedSubmitKeyToHollowAccountAlias")
                .given(
                        // Create an ECDSA key
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed("adminKey"),
                        newKeyNamed("submitKey"),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_MILLION_HBARS),
                        // Create the topic
                        createTopic("testTopic").adminKeyName("adminKey").submitKeyName("submitKey"))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    spec.registry()
                            .saveAccountAlias(
                                    SECP_256K1_SOURCE_KEY,
                                    AccountID.newBuilder().setAlias(evmAddress).build());

                    allRunFor(
                            spec,
                            // Transfer money to the alias --> creates HOLLOW ACCOUNT
                            cryptoTransfer(
                                    movingHbar(ONE_HUNDRED_HBARS).distributing(TOKEN_TREASURY, SECP_256K1_SOURCE_KEY)),
                            // Verify that the account is created and is hollow
                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                    .has(accountWith().hasEmptyKey()),
                            // Remove the submit key
                            updateTopic("testTopic").submitKey(EMPTY_KEY),
                            // Verify the submit key is removed
                            getTopicInfo("testTopic").hasNoSubmitKey(),
                            // Update the topic with the ECDSA key as submit key
                            updateTopic("testTopic").submitKey(SECP_256K1_SOURCE_KEY));
                }))
                .then(getTopicInfo("testTopic").hasSubmitKey(SECP_256K1_SOURCE_KEY));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
