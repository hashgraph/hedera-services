/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.file;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.including;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocalWithFunctionAbi;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.BYTES_4K;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.resetToDefault;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateSpecialFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.utils.contracts.SimpleBytesResult.bigIntResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.token.TokenAssociationSpecs;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * NOTE: 1. This test suite covers the test08UpdateFile() test scenarios from the legacy
 * FileServiceIT test class after the FileServiceIT class is removed since all other test scenarios
 * in this class are already covered by test suites under
 * com.hedera.services.legacy.regression.suites.file and
 * com.hedera.services.legacy.regression.suites.crpto.
 *
 * <p>2. While this class now provides minimal coverage for proto's FileUpdate transaction, we shall
 * add more positive and negative test scenarios to cover FileUpdate, such as missing (partial) keys
 * for update, for update of expirationTime, for modifying keys field, etc.
 *
 * <p>We'll come back to add all missing test scenarios for this and other test suites once we are
 * done with cleaning up old test cases.
 */
public class FileUpdateSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(FileUpdateSuite.class);
    private static final String CONTRACT = "CreateTrivial";
    private static final String CREATE_TXN = "create";
    private static final String INSERT_ABI = "insert";
    private static final String INDIRECT_GET_ABI = "getIndirect";
    private static final String CHAIN_ID_GET_ABI = "getChainID";
    private static final String INVALID_ENTITY_ID = "1.2.3";

    private static final String INDIVIDUAL_KV_LIMIT_PROP = "contracts.maxKvPairs.individual";
    private static final String AGGREGATE_KV_LIMIT_PROP = "contracts.maxKvPairs.aggregate";
    private static final String USE_GAS_THROTTLE_PROP = "contracts.throttle.throttleByGas";
    private static final String MAX_CUSTOM_FEES_PROP = "tokens.maxCustomFeesAllowed";
    private static final String MAX_REFUND_GAS_PROP = "contracts.maxRefundPercentOfGasLimit";
    private static final String CONS_MAX_GAS_PROP = "contracts.maxGasPerSec";
    private static final String CHAIN_ID_PROP = "contracts.chainId";
    private static final String AUTO_CREATION_PROP = "autoCreation.enabled";
    private static final String LAZY_CREATION_PROP = "lazyCreation.enabled";

    private static final long DEFAULT_CHAIN_ID =
            Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get(CHAIN_ID_PROP));
    private static final long DEFAULT_MAX_LIFETIME =
            Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));
    private static final String DEFAULT_MAX_CUSTOM_FEES =
            HapiSpecSetup.getDefaultNodeProps().get(MAX_CUSTOM_FEES_PROP);
    private static final String DEFAULT_MAX_KV_PAIRS_PER_CONTRACT =
            HapiSpecSetup.getDefaultNodeProps().get(INDIVIDUAL_KV_LIMIT_PROP);
    private static final String DEFAULT_MAX_KV_PAIRS =
            HapiSpecSetup.getDefaultNodeProps().get(AGGREGATE_KV_LIMIT_PROP);
    private static final String DEFAULT_MAX_CONS_GAS =
            HapiSpecSetup.getDefaultNodeProps().get(CONS_MAX_GAS_PROP);

    private static final String STORAGE_PRICE_TIERS_PROP = "contract.storageSlotPriceTiers";
    private static final String FREE_PRICE_TIER_PROP = "contracts.freeStorageTierLimit";
    public static final String CIVILIAN = "civilian";
    public static final String TEST_TOPIC = "testTopic";

    public static void main(String... args) {
        new FileUpdateSuite().runSuiteSync();
    }

    @Override
    @SuppressWarnings("java:S3878")
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                new HapiApiSpec[] {
                    vanillaUpdateSucceeds(),
                    updateFeesCompatibleWithCreates(),
                    apiPermissionsChangeDynamically(),
                    cannotUpdateExpirationPastMaxLifetime(),
                    optimisticSpecialFileUpdate(),
                    associateHasExpectedSemantics(),
                    notTooManyFeeScheduleCanBeCreated(),
                    allUnusedGasIsRefundedIfSoConfigured(),
                    maxRefundIsEnforced(),
                    gasLimitOverMaxGasLimitFailsPrecheck(),
                    autoCreationIsDynamic(),
                    kvLimitsEnforced(),
                    serviceFeeRefundedIfConsGasExhausted(),
                    chainIdChangesDynamically(),
                    entitiesNotCreatableAfterUsageLimitsReached(),
                    rentItemizedAsExpectedWithOverridePriceTiers(),
                    messageSubmissionSizeChange()
                });
    }

    private HapiApiSpec associateHasExpectedSemantics() {
        return defaultHapiSpec("AssociateHasExpectedSemantics")
                .given(flattened(TokenAssociationSpecs.basicKeysAndTokens()))
                .when(
                        cryptoCreate("misc").balance(0L),
                        TxnVerbs.tokenAssociate(
                                "misc", TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT),
                        TxnVerbs.tokenAssociate(
                                        "misc", TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT)
                                .hasKnownStatus(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT),
                        tokenAssociate("misc", INVALID_ENTITY_ID).hasKnownStatus(INVALID_TOKEN_ID),
                        tokenAssociate("misc", INVALID_ENTITY_ID, INVALID_ENTITY_ID)
                                .hasPrecheck(TOKEN_ID_REPEATED_IN_TOKEN_LIST),
                        tokenDissociate("misc", INVALID_ENTITY_ID, INVALID_ENTITY_ID)
                                .hasPrecheck(TOKEN_ID_REPEATED_IN_TOKEN_LIST),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of("tokens.maxRelsPerInfoQuery", "" + 1)),
                        fileUpdate(APP_PROPERTIES)
                                .overridingProps(Map.of("tokens.maxRelsPerInfoQuery", "" + 1000))
                                .payingWith(ADDRESS_BOOK_CONTROL),
                        TxnVerbs.tokenAssociate(
                                "misc", TokenAssociationSpecs.FREEZABLE_TOKEN_OFF_BY_DEFAULT),
                        tokenAssociate(
                                "misc",
                                TokenAssociationSpecs.KNOWABLE_TOKEN,
                                TokenAssociationSpecs.VANILLA_TOKEN))
                .then(
                        getAccountInfo("misc")
                                .hasToken(
                                        relationshipWith(
                                                        TokenAssociationSpecs
                                                                .FREEZABLE_TOKEN_ON_BY_DEFAULT)
                                                .kyc(KycNotApplicable)
                                                .freeze(Frozen))
                                .hasToken(
                                        relationshipWith(
                                                        TokenAssociationSpecs
                                                                .FREEZABLE_TOKEN_OFF_BY_DEFAULT)
                                                .kyc(KycNotApplicable)
                                                .freeze(Unfrozen))
                                .hasToken(
                                        relationshipWith(TokenAssociationSpecs.KNOWABLE_TOKEN)
                                                .kyc(Revoked)
                                                .freeze(FreezeNotApplicable))
                                .hasToken(
                                        relationshipWith(TokenAssociationSpecs.VANILLA_TOKEN)
                                                .kyc(KycNotApplicable)
                                                .freeze(FreezeNotApplicable))
                                .logged());
    }

    public HapiApiSpec autoCreationIsDynamic() {
        final var aliasKey = "autoCreationKey";

        return defaultHapiSpec("AutoCreationIsDynamic")
                .given(
                        newKeyNamed(aliasKey),
                        overriding(AUTO_CREATION_PROP, "false"),
                        overriding(LAZY_CREATION_PROP, "false"))
                .when(
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, aliasKey, ONE_HBAR))
                                .hasKnownStatus(NOT_SUPPORTED))
                .then(
                        overriding(AUTO_CREATION_PROP, "true"),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, aliasKey, ONE_HBAR)),
                        getAliasedAccountInfo(aliasKey),
                        resetToDefault(AUTO_CREATION_PROP));
    }

    public HapiApiSpec notTooManyFeeScheduleCanBeCreated() {
        final var denom = "fungible";
        final var token = "token";
        return defaultHapiSpec("OnlyValidCustomFeeScheduleCanBeCreated")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(Map.of(MAX_CUSTOM_FEES_PROP, "1")))
                .when(
                        tokenCreate(denom),
                        tokenCreate(token)
                                .treasury(DEFAULT_PAYER)
                                .withCustom(fixedHbarFee(1, DEFAULT_PAYER))
                                .withCustom(fixedHtsFee(1, denom, DEFAULT_PAYER))
                                .hasKnownStatus(CUSTOM_FEES_LIST_TOO_LONG))
                .then(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(
                                        Map.of(MAX_CUSTOM_FEES_PROP, DEFAULT_MAX_CUSTOM_FEES)));
    }

    private HapiApiSpec optimisticSpecialFileUpdate() {
        final var appendsPerBurst = 128;
        final var specialFile = "0.0.159";
        final var specialFileContents = ByteString.copyFrom(randomUtf8Bytes(64 * BYTES_4K));
        return defaultHapiSpec("OptimisticSpecialFileUpdate")
                .given()
                .when(
                        updateSpecialFile(
                                GENESIS,
                                specialFile,
                                specialFileContents,
                                BYTES_4K,
                                appendsPerBurst))
                .then(
                        getFileContents(specialFile)
                                .hasContents(ignore -> specialFileContents.toByteArray()));
    }

    private HapiApiSpec apiPermissionsChangeDynamically() {
        final var civilian = CIVILIAN;
        return defaultHapiSpec("ApiPermissionsChangeDynamically")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        getFileContents(API_PERMISSIONS).logged(),
                        tokenCreate("poc").payingWith(civilian))
                .when(
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .erasingProps(Set.of("tokenCreate")),
                        getFileContents(API_PERMISSIONS).logged())
                .then(
                        tokenCreate("poc").payingWith(civilian).hasPrecheck(NOT_SUPPORTED),
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of("tokenCreate", "0-*")),
                        tokenCreate("secondPoc").payingWith(civilian));
    }

    private HapiApiSpec updateFeesCompatibleWithCreates() {
        final long origLifetime = 7_200_000L;
        final long extension = 700_000L;
        final byte[] old2k = randomUtf8Bytes(BYTES_4K / 2);
        final byte[] new4k = randomUtf8Bytes(BYTES_4K);
        final byte[] new2k = randomUtf8Bytes(BYTES_4K / 2);

        return defaultHapiSpec("UpdateFeesCompatibleWithCreates")
                .given(fileCreate("test").contents(old2k).lifetime(origLifetime).via(CREATE_TXN))
                .when(
                        fileUpdate("test").contents(new4k).extendingExpiryBy(0).via("updateTo4"),
                        fileUpdate("test").contents(new2k).extendingExpiryBy(0).via("updateTo2"),
                        fileUpdate("test").extendingExpiryBy(extension).via("extend"),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of("maxFileSize", "1025"))
                                .via("special"),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of("maxFileSize", "1024")))
                .then(
                        UtilVerbs.withOpContext(
                                (spec, opLog) -> {
                                    final var createOp = getTxnRecord(CREATE_TXN);
                                    final var to4kOp = getTxnRecord("updateTo4");
                                    final var to2kOp = getTxnRecord("updateTo2");
                                    final var extensionOp = getTxnRecord("extend");
                                    final var specialOp = getTxnRecord("special");
                                    allRunFor(
                                            spec, createOp, to4kOp, to2kOp, extensionOp, specialOp);
                                    final var createFee =
                                            createOp.getResponseRecord().getTransactionFee();
                                    opLog.info("Creation : {} ", createFee);
                                    opLog.info(
                                            "New 4k   : {} ({})",
                                            to4kOp.getResponseRecord().getTransactionFee(),
                                            (to4kOp.getResponseRecord().getTransactionFee()
                                                    - createFee));
                                    opLog.info(
                                            "New 2k   : {} ({})",
                                            to2kOp.getResponseRecord().getTransactionFee(),
                                            +(to2kOp.getResponseRecord().getTransactionFee()
                                                    - createFee));
                                    opLog.info(
                                            "Extension: {} ({})",
                                            extensionOp.getResponseRecord().getTransactionFee(),
                                            (extensionOp.getResponseRecord().getTransactionFee()
                                                    - createFee));
                                    opLog.info(
                                            "Special: {}",
                                            specialOp.getResponseRecord().getTransactionFee());
                                }));
    }

    private HapiApiSpec vanillaUpdateSucceeds() {
        final byte[] old4K = randomUtf8Bytes(BYTES_4K);
        final byte[] new4k = randomUtf8Bytes(BYTES_4K);
        final String firstMemo = "Originally";
        final String secondMemo = "Subsequently";

        return defaultHapiSpec("VanillaUpdateSucceeds")
                .given(fileCreate("test").entityMemo(firstMemo).contents(old4K))
                .when(
                        fileUpdate("test")
                                .entityMemo(ZERO_BYTE_MEMO)
                                .contents(new4k)
                                .hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        fileUpdate("test").entityMemo(secondMemo).contents(new4k))
                .then(
                        getFileContents("test").hasContents(ignore -> new4k),
                        getFileInfo("test").hasMemo(secondMemo));
    }

    private HapiApiSpec cannotUpdateExpirationPastMaxLifetime() {
        return defaultHapiSpec("CannotUpdateExpirationPastMaxLifetime")
                .given(fileCreate("test"))
                .when()
                .then(
                        fileUpdate("test")
                                .lifetime(DEFAULT_MAX_LIFETIME + 12_345L)
                                .hasPrecheck(AUTORENEW_DURATION_NOT_IN_RANGE));
    }

    private HapiApiSpec maxRefundIsEnforced() {
        return defaultHapiSpec("MaxRefundIsEnforced")
                .given(
                        overriding(MAX_REFUND_GAS_PROP, "5"),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .when(contractCall(CONTRACT, CREATE_TXN).gas(1_000_000L))
                .then(
                        contractCallLocal(CONTRACT, INDIRECT_GET_ABI)
                                .gas(300_000L)
                                .has(resultWith().gasUsed(285_000L)),
                        resetToDefault(MAX_REFUND_GAS_PROP));
    }

    private HapiApiSpec allUnusedGasIsRefundedIfSoConfigured() {
        return defaultHapiSpec("AllUnusedGasIsRefundedIfSoConfigured")
                .given(
                        overriding(MAX_REFUND_GAS_PROP, "100"),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT).gas(100_000L))
                .when(contractCall(CONTRACT, CREATE_TXN).gas(1_000_000L))
                .then(
                        contractCallLocal(CONTRACT, INDIRECT_GET_ABI)
                                .gas(300_000L)
                                .has(resultWith().gasUsed(26_451)),
                        resetToDefault(MAX_REFUND_GAS_PROP));
    }

    private HapiApiSpec gasLimitOverMaxGasLimitFailsPrecheck() {
        return defaultHapiSpec("GasLimitOverMaxGasLimitFailsPrecheck")
                .given(
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT).gas(1_000_000L),
                        overriding(CONS_MAX_GAS_PROP, "100"))
                .when()
                .then(
                        contractCallLocal(CONTRACT, INDIRECT_GET_ABI)
                                .gas(101L)
                                // for some reason BUSY is returned in CI
                                .hasCostAnswerPrecheckFrom(MAX_GAS_LIMIT_EXCEEDED, BUSY),
                        resetToDefault(CONS_MAX_GAS_PROP));
    }

    private HapiApiSpec kvLimitsEnforced() {
        final var contract = "User";
        final var gasToOffer = 1_000_000;

        return defaultHapiSpec("KvLimitsEnforced")
                .given(
                        uploadInitCode(contract),
                        /* This contract has 0 key/value mappings at creation */
                        contractCreate(contract),
                        /* Now we update the per-contract limit to 10 mappings */
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(
                                        Map.of(
                                                INDIVIDUAL_KV_LIMIT_PROP,
                                                "10",
                                                CONS_MAX_GAS_PROP,
                                                "100_000_000")))
                .when(
                        /* The first call to insert adds 5 mappings */
                        contractCall(contract, INSERT_ABI, 1, 1)
                                .payingWith(GENESIS)
                                .gas(gasToOffer),
                        /* Each subsequent call to adds 3 mappings; so 8 total after this */
                        contractCall(contract, INSERT_ABI, 2, 4)
                                .payingWith(GENESIS)
                                .gas(gasToOffer),
                        /* And this one fails because 8 + 3 = 11 > 10 */
                        contractCall(contract, INSERT_ABI, 3, 9)
                                .payingWith(GENESIS)
                                .hasKnownStatus(MAX_CONTRACT_STORAGE_EXCEEDED)
                                .gas(gasToOffer),
                        /* Confirm the storage size didn't change */
                        getContractInfo(contract).has(contractWith().numKvPairs(8)),
                        /* Now we update the per-contract limit to 1B mappings, but the aggregate limit to just 1 🤪 */
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(
                                        Map.of(
                                                INDIVIDUAL_KV_LIMIT_PROP, "1_000_000_000",
                                                AGGREGATE_KV_LIMIT_PROP, "1")),
                        contractCall(contract, INSERT_ABI, 3, 9)
                                .payingWith(GENESIS)
                                .hasKnownStatus(MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED)
                                .gas(gasToOffer),
                        getContractInfo(contract).has(contractWith().numKvPairs(8)))
                .then(
                        /* Now restore the defaults and confirm we can use more storage */
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(
                                        Map.of(
                                                INDIVIDUAL_KV_LIMIT_PROP,
                                                DEFAULT_MAX_KV_PAIRS_PER_CONTRACT,
                                                AGGREGATE_KV_LIMIT_PROP,
                                                DEFAULT_MAX_KV_PAIRS,
                                                CONS_MAX_GAS_PROP,
                                                DEFAULT_MAX_CONS_GAS)),
                        contractCall(contract, INSERT_ABI, 3, 9)
                                .payingWith(GENESIS)
                                .gas(gasToOffer),
                        contractCall(contract, INSERT_ABI, 4, 16)
                                .payingWith(GENESIS)
                                .gas(gasToOffer),
                        getContractInfo(contract).has(contractWith().numKvPairs(14)));
    }

    @SuppressWarnings("java:S5960")
    private HapiApiSpec serviceFeeRefundedIfConsGasExhausted() {
        final var contract = "User";
        final var gasToOffer = Long.parseLong(DEFAULT_MAX_CONS_GAS);
        final var civilian = "payer";
        final var unrefundedTxn = "unrefundedTxn";
        final var refundedTxn = "refundedTxn";

        return defaultHapiSpec("ServiceFeeRefundedIfConsGasExhausted")
                .given(
                        overridingTwo(
                                CONS_MAX_GAS_PROP,
                                DEFAULT_MAX_CONS_GAS,
                                USE_GAS_THROTTLE_PROP,
                                "true"),
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(contract),
                        contractCreate(contract),
                        contractCall(contract, INSERT_ABI, 1, 4)
                                .payingWith(civilian)
                                .gas(gasToOffer)
                                .via(unrefundedTxn))
                .when(
                        usableTxnIdNamed(refundedTxn).payerId(civilian),
                        contractCall(contract, INSERT_ABI, 2, 4)
                                .payingWith(GENESIS)
                                .gas(gasToOffer)
                                .hasAnyStatusAtAll()
                                .deferStatusResolution(),
                        uncheckedSubmit(
                                        contractCall(contract, INSERT_ABI, 3, 4)
                                                .signedBy(civilian)
                                                .gas(gasToOffer)
                                                .txnId(refundedTxn))
                                .payingWith(GENESIS))
                .then(
                        sleepFor(2_000L),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var unrefundedOp = getTxnRecord(unrefundedTxn);
                                    final var refundedOp =
                                            getTxnRecord(refundedTxn).assertingNothingAboutHashes();
                                    allRunFor(spec, refundedOp, unrefundedOp);
                                    final var status =
                                            refundedOp.getResponseRecord().getReceipt().getStatus();
                                    if (status == SUCCESS) {
                                        log.info(
                                                "Latency allowed gas throttle bucket to drain"
                                                        + " completely");
                                    } else {
                                        assertEquals(CONSENSUS_GAS_EXHAUSTED, status);
                                        final var origFee =
                                                unrefundedOp
                                                        .getResponseRecord()
                                                        .getTransactionFee();
                                        final var feeSansRefund =
                                                refundedOp.getResponseRecord().getTransactionFee();
                                        assertTrue(
                                                feeSansRefund < origFee,
                                                "Expected service fee to be refunded, but sans fee "
                                                        + feeSansRefund
                                                        + " was not less than "
                                                        + origFee);
                                    }
                                }));
    }

    private HapiApiSpec chainIdChangesDynamically() {
        final var chainIdUser = "ChainIdUser";
        final var otherChainId = 0xABCDL;
        final var firstCallTxn = "firstCallTxn";
        final var secondCallTxn = "secondCallTxn";
        return defaultHapiSpec("ChainIdChangesDynamically")
                .given(
                        resetToDefault(CHAIN_ID_PROP),
                        uploadInitCode(chainIdUser),
                        contractCreate(chainIdUser),
                        contractCall(chainIdUser, CHAIN_ID_GET_ABI).via(firstCallTxn),
                        contractCallLocal(chainIdUser, CHAIN_ID_GET_ABI)
                                .has(
                                        resultWith()
                                                .contractCallResult(
                                                        bigIntResult(DEFAULT_CHAIN_ID))),
                        getTxnRecord(firstCallTxn)
                                .hasPriority(
                                        recordWith()
                                                .contractCallResult(
                                                        resultWith()
                                                                .contractCallResult(
                                                                        bigIntResult(
                                                                                DEFAULT_CHAIN_ID)))),
                        contractCallLocal(chainIdUser, "getSavedChainID")
                                .has(
                                        resultWith()
                                                .contractCallResult(
                                                        bigIntResult(DEFAULT_CHAIN_ID))))
                .when(
                        overriding(CHAIN_ID_PROP, "" + otherChainId),
                        contractCreate(chainIdUser),
                        contractCall(chainIdUser, CHAIN_ID_GET_ABI).via(secondCallTxn),
                        contractCallLocal(chainIdUser, CHAIN_ID_GET_ABI)
                                .has(resultWith().contractCallResult(bigIntResult(otherChainId))),
                        getTxnRecord(secondCallTxn)
                                .hasPriority(
                                        recordWith()
                                                .contractCallResult(
                                                        resultWith()
                                                                .contractCallResult(
                                                                        bigIntResult(
                                                                                otherChainId)))),
                        contractCallLocal(chainIdUser, "getSavedChainID")
                                .has(resultWith().contractCallResult(bigIntResult(otherChainId))))
                .then(resetToDefault(CHAIN_ID_PROP));
    }

    private HapiApiSpec entitiesNotCreatableAfterUsageLimitsReached() {
        final var notToBe = "ne'erToBe";
        return defaultHapiSpec("EntitiesNotCreatableAfterUsageLimitsReached")
                .given(
                        uploadInitCode("Multipurpose"),
                        overridingAllOf(
                                Map.of(
                                        "accounts.maxNumber", "0",
                                        "contracts.maxNumber", "0",
                                        "files.maxNumber", "0",
                                        "scheduling.maxNumber", "0",
                                        "tokens.maxNumber", "0",
                                        "topics.maxNumber", "0")))
                .when(
                        cryptoCreate(notToBe)
                                .hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED),
                        contractCreate("Multipurpose")
                                .hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED),
                        fileCreate(notToBe)
                                .contents("NOPE")
                                .hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED),
                        scheduleCreate(
                                        notToBe,
                                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1)))
                                .hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED),
                        tokenCreate(notToBe)
                                .hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED),
                        createTopic(notToBe)
                                .hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED))
                .then(
                        resetToDefault(
                                "accounts.maxNumber",
                                "contracts.maxNumber",
                                "files.maxNumber",
                                "scheduling.maxNumber",
                                "tokens.maxNumber",
                                "topics.maxNumber"));
    }

    private HapiApiSpec rentItemizedAsExpectedWithOverridePriceTiers() {
        final var slotUser = "SlotUser";
        final var creation = "creation";
        final var aSet = "aSet";
        final var failedSet = "failedSet";
        final var bSet = "bSet";
        final var autoRenew = "autoRenew";
        final var oddGasAmount = 666_666L;
        final String datumAbi =
                "{\"inputs\":[],\"name\":\"datum\",\"outputs\":"
                        + "[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}],"
                        + "\"stateMutability\":\"view\",\"type\":\"function\"}";
        final AtomicLong expectedStorageFee = new AtomicLong();
        return defaultHapiSpec("RentItemizedAsExpectedWithOverridePriceTiers")
                .given(
                        resetToDefault(STORAGE_PRICE_TIERS_PROP, FREE_PRICE_TIER_PROP),
                        uploadInitCode(slotUser),
                        cryptoCreate(autoRenew).balance(0L),
                        contractCreate(slotUser)
                                .autoRenewAccountId(autoRenew)
                                .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                                .via(creation),
                        getTxnRecord(creation).hasNonStakingChildRecordCount(1))
                .when(
                        overridingThree(
                                STORAGE_PRICE_TIERS_PROP,
                                "10000til100M",
                                "staking.fees.nodeRewardPercentage",
                                "0",
                                "staking.fees.stakingRewardPercentage",
                                "0"),
                        // Validate free tier is respected
                        contractCall(slotUser, "consumeB", 1L).via(bSet),
                        getTxnRecord(bSet).hasNonStakingChildRecordCount(0),
                        contractCallLocal(slotUser, "slotB")
                                .exposingTypedResultsTo(
                                        results -> assertEquals(BigInteger.ONE, results[0])),
                        overriding(FREE_PRICE_TIER_PROP, "0"),
                        // And validate auto-renew account must be storage fees must be payable
                        contractCall(slotUser, "consumeA", 2L, 3L)
                                .gas(oddGasAmount)
                                .via(failedSet)
                                .hasKnownStatus(INSUFFICIENT_BALANCES_FOR_STORAGE_RENT),
                        // All gas should be consumed
                        getTxnRecord(failedSet)
                                .logged()
                                .hasPriority(
                                        recordWith()
                                                .contractCallResult(
                                                        resultWith().gasUsed(oddGasAmount))),
                        // And of course no state should actually change
                        contractCallLocal(slotUser, "slotA")
                                .exposingTypedResultsTo(
                                        results -> assertEquals(BigInteger.ZERO, results[0])),
                        contractCallLocalWithFunctionAbi(slotUser, datumAbi)
                                .exposingTypedResultsTo(
                                        results -> assertEquals(BigInteger.ZERO, results[0])),
                        // Now fund the contract's auto-renew account and confirm payment accepted
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, autoRenew, 5 * ONE_HBAR)),
                        contractCall(slotUser, "consumeA", 2L, 1L).via(aSet),
                        contractCallLocal(slotUser, "slotA")
                                .exposingTypedResultsTo(
                                        results -> assertEquals(BigInteger.TWO, results[0])),
                        contractCallLocalWithFunctionAbi(slotUser, datumAbi)
                                .exposingTypedResultsTo(
                                        results -> assertEquals(BigInteger.ONE, results[0])),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var expiryLookup = getContractInfo(slotUser);
                                    final var callTimeLookup = getTxnRecord(aSet);
                                    allRunFor(spec, expiryLookup, callTimeLookup);
                                    final var lifetime =
                                            expiryLookup
                                                            .getResponse()
                                                            .getContractGetInfo()
                                                            .getContractInfo()
                                                            .getExpirationTime()
                                                            .getSeconds()
                                                    - callTimeLookup
                                                            .getResponseRecord()
                                                            .getConsensusTimestamp()
                                                            .getSeconds();
                                    final var tcFeePerSlot =
                                            10 * TINY_PARTS_PER_WHOLE * lifetime / 31536000L;
                                    final var tbFeePerSlot =
                                            spec.ratesProvider().toTbWithActiveRates(tcFeePerSlot);
                                    expectedStorageFee.set(2 * tbFeePerSlot);
                                }),
                        sourcing(
                                () ->
                                        getTxnRecord(aSet)
                                                .logged()
                                                .hasChildRecords(
                                                        recordWith()
                                                                .transfers(
                                                                        including(
                                                                                tinyBarsFromTo(
                                                                                        autoRenew,
                                                                                        FUNDING,
                                                                                        expectedStorageFee
                                                                                                .get()))))))
                .then(
                        resetToDefault(STORAGE_PRICE_TIERS_PROP, FREE_PRICE_TIER_PROP),
                        overridingTwo(
                                "staking.fees.nodeRewardPercentage", "10",
                                "staking.fees.stakingRewardPercentage", "10"));
    }

    private HapiApiSpec messageSubmissionSizeChange() {
        final var defaultMaxBytesAllowed = 1024;
        final var longMessage = TxnUtils.randomUtf8Bytes(defaultMaxBytesAllowed);

        return defaultHapiSpec("messageSubmissionSizeChange")
                .given(newKeyNamed("submitKey"), createTopic(TEST_TOPIC).submitKeyName("submitKey"))
                .when(
                        cryptoCreate(CIVILIAN),
                        submitMessageTo(TEST_TOPIC)
                                .message("testmessage")
                                .payingWith(CIVILIAN)
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(SUCCESS),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(
                                        Map.of(
                                                "consensus.message.maxBytesAllowed",
                                                String.valueOf(defaultMaxBytesAllowed - 1))))
                .then(
                        submitMessageTo(TEST_TOPIC)
                                .message(longMessage)
                                .payingWith(CIVILIAN)
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(MESSAGE_SIZE_TOO_LARGE),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(
                                        Map.of(
                                                "consensus.message.maxBytesAllowed",
                                                String.valueOf(defaultMaxBytesAllowed))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
