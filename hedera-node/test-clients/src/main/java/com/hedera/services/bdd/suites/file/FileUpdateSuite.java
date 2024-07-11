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

package com.hedera.services.bdd.suites.file;

import static com.hedera.services.bdd.junit.ContextRequirement.PERMISSION_OVERRIDES;
import static com.hedera.services.bdd.junit.ContextRequirement.PROPERTY_OVERRIDES;
import static com.hedera.services.bdd.junit.ContextRequirement.UPGRADE_FILE_CONTENT;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.including;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocalWithFunctionAbi;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.BYTES_4K;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doSeveralWithStartupConfig;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doWithStartupConfig;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.specOps;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateSpecialFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hedera.services.bdd.suites.HapiSuite.ADDRESS_BOOK_CONTROL;
import static com.hedera.services.bdd.suites.HapiSuite.API_PERMISSIONS;
import static com.hedera.services.bdd.suites.HapiSuite.APP_PROPERTIES;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.HapiSuite.TINY_PARTS_PER_WHOLE;
import static com.hedera.services.bdd.suites.HapiSuite.ZERO_BYTE_MEMO;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hedera.services.bdd.suites.utils.contracts.SimpleBytesResult.bigIntResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_STORAGE_RENT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;
import static java.lang.Long.parseLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.token.TokenAssociationSpecs;
import com.swirlds.common.utility.CommonUtils;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

@SuppressWarnings("java:S1192")
public class FileUpdateSuite {
    private static final Logger log = LogManager.getLogger(FileUpdateSuite.class);
    private static final String CONTRACT = "CreateTrivial";
    private static final String CREATE_TXN = "create";
    public static final String INSERT_ABI = "insert";
    private static final String INDIRECT_GET_ABI = "getIndirect";
    private static final String CHAIN_ID_GET_ABI = "getChainID";
    private static final String INVALID_ENTITY_ID = "1.2.3";
    public static final String CIVILIAN = "civilian";
    public static final String TEST_TOPIC = "testTopic";

    @HapiTest
    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(fileCreate("file").contents("ABC"))
                .when()
                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> fileUpdate("file")
                        .contents("DEF")));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> associateHasExpectedSemantics() {
        return propertyPreservingHapiSpec("AssociateHasExpectedSemantics")
                .preserving("tokens.maxRelsPerInfoQuery")
                .given(flattened((Object[]) TokenAssociationSpecs.basicKeysAndTokens()))
                .when(
                        cryptoCreate("misc").balance(0L),
                        TxnVerbs.tokenAssociate("misc", TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT),
                        TxnVerbs.tokenAssociate("misc", TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT)
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
                        TxnVerbs.tokenAssociate("misc", TokenAssociationSpecs.FREEZABLE_TOKEN_OFF_BY_DEFAULT),
                        tokenAssociate(
                                "misc", TokenAssociationSpecs.KNOWABLE_TOKEN, TokenAssociationSpecs.VANILLA_TOKEN))
                .then(getAccountInfo("misc")
                        .hasToken(relationshipWith(TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT)
                                .kyc(KycNotApplicable)
                                .freeze(Frozen))
                        .hasToken(relationshipWith(TokenAssociationSpecs.FREEZABLE_TOKEN_OFF_BY_DEFAULT)
                                .kyc(KycNotApplicable)
                                .freeze(Unfrozen))
                        .hasToken(relationshipWith(TokenAssociationSpecs.KNOWABLE_TOKEN)
                                .kyc(Revoked)
                                .freeze(FreezeNotApplicable))
                        .hasToken(relationshipWith(TokenAssociationSpecs.VANILLA_TOKEN)
                                .kyc(KycNotApplicable)
                                .freeze(FreezeNotApplicable))
                        .logged());
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> notTooManyFeeScheduleCanBeCreated() {
        final var denom = "fungible";
        final var token = "token";
        return propertyPreservingHapiSpec("OnlyValidCustomFeeScheduleCanBeCreated")
                .preserving("tokens.maxCustomFeesAllowed")
                .given(overriding("tokens.maxCustomFeesAllowed", "1"))
                .when(
                        tokenCreate(denom),
                        tokenCreate(token)
                                .treasury(DEFAULT_PAYER)
                                .withCustom(fixedHbarFee(1, DEFAULT_PAYER))
                                .withCustom(fixedHtsFee(1, denom, DEFAULT_PAYER))
                                .hasKnownStatus(CUSTOM_FEES_LIST_TOO_LONG))
                .then();
    }

    @LeakyHapiTest(UPGRADE_FILE_CONTENT)
    final Stream<DynamicTest> optimisticSpecialFileUpdate() {
        final var appendsPerBurst = 128;
        final var specialFile = "0.0.159";
        final var contents = randomUtf8Bytes(64 * BYTES_4K);
        final var specialFileContents = ByteString.copyFrom(contents);
        final byte[] expectedHash;
        try {
            expectedHash = MessageDigest.getInstance("SHA-384").digest(contents);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return defaultHapiSpec("OptimisticSpecialFileUpdate")
                .given()
                .when(updateSpecialFile(GENESIS, specialFile, specialFileContents, BYTES_4K, appendsPerBurst))
                .then(getFileInfo(specialFile).hasMemo(CommonUtils.hex(expectedHash)));
    }

    @LeakyHapiTest(PERMISSION_OVERRIDES)
    final Stream<DynamicTest> apiPermissionsChangeDynamically() {
        final var civilian = CIVILIAN;
        return defaultHapiSpec("ApiPermissionsChangeDynamically")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1_000_000_000L)),
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        getFileContents(API_PERMISSIONS).logged(),
                        tokenCreate("poc").payingWith(civilian))
                .when(
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of("tokenCreate", "0-1")),
                        getFileContents(API_PERMISSIONS).logged())
                .then(
                        tokenCreate("poc")
                                .payingWith(civilian)
                                .hasPrecheckFrom(NOT_SUPPORTED, OK)
                                .hasKnownStatus(UNAUTHORIZED),
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of("tokenCreate", "0-*")),
                        tokenCreate("secondPoc").payingWith(civilian));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> updateFeesCompatibleWithCreates() {
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
                .then(UtilVerbs.withOpContext((spec, opLog) -> {
                    final var createOp = getTxnRecord(CREATE_TXN);
                    final var to4kOp = getTxnRecord("updateTo4");
                    final var to2kOp = getTxnRecord("updateTo2");
                    final var extensionOp = getTxnRecord("extend");
                    final var specialOp = getTxnRecord("special");
                    allRunFor(spec, createOp, to4kOp, to2kOp, extensionOp, specialOp);
                    final var createFee = createOp.getResponseRecord().getTransactionFee();
                    opLog.info("Creation : {} ", createFee);
                    opLog.info(
                            "New 4k   : {} ({})",
                            to4kOp.getResponseRecord().getTransactionFee(),
                            (to4kOp.getResponseRecord().getTransactionFee() - createFee));
                    opLog.info(
                            "New 2k   : {} ({})",
                            to2kOp.getResponseRecord().getTransactionFee(),
                            +(to2kOp.getResponseRecord().getTransactionFee() - createFee));
                    opLog.info(
                            "Extension: {} ({})",
                            extensionOp.getResponseRecord().getTransactionFee(),
                            (extensionOp.getResponseRecord().getTransactionFee() - createFee));
                    opLog.info("Special: {}", specialOp.getResponseRecord().getTransactionFee());
                }));
    }

    @HapiTest
    final Stream<DynamicTest> vanillaUpdateSucceeds() {
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

    @HapiTest
    final Stream<DynamicTest> cannotUpdateImmutableFile() {
        final String file1 = "FILE_1";
        final String file2 = "FILE_2";
        return defaultHapiSpec("CannotUpdateImmutableFile")
                .given(
                        fileCreate(file1).contents("Hello World").unmodifiable(),
                        fileCreate(file2).contents("Hello World").waclShape(SigControl.emptyList()))
                .when()
                .then(
                        fileUpdate(file1)
                                .contents("Goodbye World")
                                .signedBy(DEFAULT_PAYER)
                                .hasKnownStatus(UNAUTHORIZED),
                        fileUpdate(file2)
                                .contents("Goodbye World")
                                .signedBy(DEFAULT_PAYER)
                                .hasKnownStatus(UNAUTHORIZED));
    }

    @HapiTest
    final Stream<DynamicTest> cannotUpdateExpirationPastMaxLifetime() {
        return defaultHapiSpec("CannotUpdateExpirationPastMaxLifetime")
                .given(fileCreate("test"))
                .when()
                .then(doWithStartupConfig("entities.maxLifetime", maxLifetime -> fileUpdate("test")
                        .lifetime(parseLong(maxLifetime) + 12_345L)
                        .hasPrecheck(AUTORENEW_DURATION_NOT_IN_RANGE)));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> maxRefundIsEnforced() {
        return propertyPreservingHapiSpec("MaxRefundIsEnforced")
                .preserving("contracts.maxRefundPercentOfGasLimit")
                .given(
                        overriding("contracts.maxRefundPercentOfGasLimit", "5"),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .when(contractCall(CONTRACT, CREATE_TXN).gas(1000000L))
                .then(contractCallLocal(CONTRACT, INDIRECT_GET_ABI)
                        .gas(300000L)
                        .has(resultWith().gasUsed(285000L)));
    }

    // C.f. https://github.com/hashgraph/hedera-services/pull/8908
    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> allUnusedGasIsRefundedIfSoConfigured() {
        return propertyPreservingHapiSpec("AllUnusedGasIsRefundedIfSoConfigured")
                .preserving("contracts.maxRefundPercentOfGasLimit")
                .given(
                        overriding("contracts.maxRefundPercentOfGasLimit", "100"),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT).gas(100_000L))
                .when(contractCall(CONTRACT, CREATE_TXN).gas(1_000_000L))
                .then(contractCallLocal(CONTRACT, INDIRECT_GET_ABI)
                        .gas(300_000L)
                        .has(resultWith().gasUsed(26_515)));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> gasLimitOverMaxGasLimitFailsPrecheck() {
        return propertyPreservingHapiSpec("GasLimitOverMaxGasLimitFailsPrecheck")
                .preserving("contracts.maxGasPerSec")
                .given(
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT).gas(1_000_000L),
                        overriding("contracts.maxGasPerSec", "100"))
                .when()
                .then(contractCallLocal(CONTRACT, INDIRECT_GET_ABI)
                        .gas(101L)
                        // for some reason BUSY is returned in CI
                        .hasCostAnswerPrecheckFrom(MAX_GAS_LIMIT_EXCEEDED, BUSY));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> kvLimitsEnforced() {
        final var contract = "User";
        final var gasToOffer = 1_000_000;

        return propertyPreservingHapiSpec("KvLimitsEnforced")
                .preserving("contracts.maxKvPairs.individual", "contracts.maxKvPairs.aggregate")
                .given(
                        uploadInitCode(contract),
                        /* This contract has 0 key/value mappings at creation */
                        contractCreate(contract),
                        /* Now we update the per-contract limit to 10 mappings */
                        overriding("contracts.maxKvPairs.individual", "10"))
                .when(
                        /* The first call to insert adds 5 mappings */
                        contractCall(contract, INSERT_ABI, BigInteger.ONE, BigInteger.ONE)
                                .payingWith(GENESIS)
                                .gas(gasToOffer),
                        /* Each subsequent call to adds 3 mappings; so 8 total after this */
                        contractCall(contract, INSERT_ABI, BigInteger.TWO, BigInteger.valueOf(4))
                                .payingWith(GENESIS)
                                .gas(gasToOffer),
                        /* And this one fails because 8 + 3 = 11 > 10 */
                        contractCall(contract, INSERT_ABI, BigInteger.valueOf(3), BigInteger.valueOf(9))
                                .payingWith(GENESIS)
                                .hasKnownStatus(MAX_CONTRACT_STORAGE_EXCEEDED)
                                .gas(gasToOffer),
                        /* Confirm the storage size didn't change */
                        getContractInfo(contract).has(contractWith().numKvPairs(8)),
                        /* Now we update the per-contract limit to 1B mappings, but the aggregate limit to just 1 */
                        overridingTwo(
                                "contracts.maxKvPairs.individual", "1000000000",
                                "contracts.maxKvPairs.aggregate", "1"),
                        contractCall(contract, INSERT_ABI, BigInteger.valueOf(3), BigInteger.valueOf(9))
                                .payingWith(GENESIS)
                                .hasKnownStatus(MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED)
                                .gas(gasToOffer),
                        getContractInfo(contract).has(contractWith().numKvPairs(8)))
                .then(
                        /* Now raise the limits and confirm we can use more storage */
                        overridingTwo(
                                "contracts.maxKvPairs.individual", "1000000000",
                                "contracts.maxKvPairs.aggregate", "10000000000"),
                        contractCall(contract, INSERT_ABI, BigInteger.valueOf(3), BigInteger.valueOf(9))
                                .payingWith(GENESIS)
                                .gas(gasToOffer),
                        contractCall(contract, INSERT_ABI, BigInteger.valueOf(4), BigInteger.valueOf(16))
                                .payingWith(GENESIS)
                                .gas(gasToOffer),
                        getContractInfo(contract).has(contractWith().numKvPairs(14)));
    }

    @SuppressWarnings("java:S5960")
    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> serviceFeeRefundedIfConsGasExhausted() {
        final var contract = "User";
        final var gasToOffer = 15_000_000;
        final var civilian = "payer";
        final var unrefundedTxn = "unrefundedTxn";
        final var refundedTxn = "refundedTxn";

        return propertyPreservingHapiSpec("ServiceFeeRefundedIfConsGasExhausted")
                .preserving("contracts.maxGasPerSec")
                .given(
                        overriding("contracts.maxGasPerSec", gasToOffer + ""),
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(contract),
                        contractCreate(contract),
                        contractCall(contract, INSERT_ABI, BigInteger.ONE, BigInteger.valueOf(4))
                                .payingWith(civilian)
                                .gas(gasToOffer)
                                .via(unrefundedTxn))
                .when(
                        usableTxnIdNamed(refundedTxn).payerId(civilian),
                        contractCall(contract, INSERT_ABI, BigInteger.TWO, BigInteger.valueOf(4))
                                .payingWith(GENESIS)
                                .gas(gasToOffer)
                                .hasAnyStatusAtAll()
                                .deferStatusResolution(),
                        uncheckedSubmit(contractCall(contract, INSERT_ABI, BigInteger.valueOf(3), BigInteger.valueOf(4))
                                        .signedBy(civilian)
                                        .gas(gasToOffer)
                                        .txnId(refundedTxn))
                                .payingWith(GENESIS))
                .then(sleepFor(6_000L), withOpContext((spec, opLog) -> {
                    final var unrefundedOp = getTxnRecord(unrefundedTxn);
                    final var refundedOp = getTxnRecord(refundedTxn).assertingNothingAboutHashes();
                    allRunFor(spec, refundedOp, unrefundedOp);
                    final var status =
                            refundedOp.getResponseRecord().getReceipt().getStatus();
                    if (status == SUCCESS) {
                        log.info("Latency allowed gas throttle bucket to drain" + " completely");
                    } else {
                        assertEquals(CONSENSUS_GAS_EXHAUSTED, status);
                        final var origFee = unrefundedOp.getResponseRecord().getTransactionFee();
                        final var feeSansRefund = refundedOp.getResponseRecord().getTransactionFee();
                        assertTrue(
                                feeSansRefund < origFee,
                                "Expected service fee to be refunded, but sans fee "
                                        + feeSansRefund
                                        + " was not less than "
                                        + origFee);
                    }
                }));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> chainIdChangesDynamically() {
        final var chainIdUser = "ChainIdUser";
        final var otherChainId = 0xABCDL;
        final var firstCallTxn = "firstCallTxn";
        final var secondCallTxn = "secondCallTxn";
        return propertyPreservingHapiSpec("ChainIdChangesDynamically")
                .preserving("contracts.chainId")
                .given(
                        uploadInitCode(chainIdUser),
                        contractCreate(chainIdUser),
                        contractCall(chainIdUser, CHAIN_ID_GET_ABI).via(firstCallTxn),
                        doSeveralWithStartupConfig("contracts.chainId", chainId -> {
                            final var expectedChainId = bigIntResult(parseLong(chainId));
                            return specOps(
                                    contractCallLocal(chainIdUser, CHAIN_ID_GET_ABI)
                                            .has(resultWith().contractCallResult(expectedChainId)),
                                    getTxnRecord(firstCallTxn)
                                            .hasPriority(recordWith()
                                                    .contractCallResult(
                                                            resultWith().contractCallResult(expectedChainId))),
                                    contractCallLocal(chainIdUser, "getSavedChainID")
                                            .has(resultWith().contractCallResult(expectedChainId)));
                        }))
                .when(
                        overriding("contracts.chainId", "" + otherChainId),
                        contractCreate(chainIdUser),
                        contractCall(chainIdUser, CHAIN_ID_GET_ABI).via(secondCallTxn),
                        contractCallLocal(chainIdUser, CHAIN_ID_GET_ABI)
                                .has(resultWith().contractCallResult(bigIntResult(otherChainId))),
                        getTxnRecord(secondCallTxn)
                                .hasPriority(recordWith()
                                        .contractCallResult(
                                                resultWith().contractCallResult(bigIntResult(otherChainId)))),
                        contractCallLocal(chainIdUser, "getSavedChainID")
                                .has(resultWith().contractCallResult(bigIntResult(otherChainId))))
                .then();
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> entitiesNotCreatableAfterUsageLimitsReached() {
        final var notToBe = "ne'erToBe";
        return propertyPreservingHapiSpec("EntitiesNotCreatableAfterUsageLimitsReached")
                .preserving(
                        "accounts.maxNumber",
                        "contracts.maxNumber",
                        "files.maxNumber",
                        "scheduling.maxNumber",
                        "tokens.maxNumber",
                        "topics.maxNumber")
                .given(
                        uploadInitCode("Multipurpose"),
                        overridingAllOf(Map.of(
                                "accounts.maxNumber", "0",
                                "contracts.maxNumber", "0",
                                "files.maxNumber", "0",
                                "scheduling.maxNumber", "0",
                                "tokens.maxNumber", "0",
                                "topics.maxNumber", "0")))
                .when(
                        cryptoCreate(notToBe).hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED),
                        contractCreate("Multipurpose").hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED),
                        fileCreate(notToBe)
                                .contents("NOPE")
                                .hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED),
                        scheduleCreate(notToBe, cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1)))
                                .hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED),
                        tokenCreate(notToBe).hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED),
                        createTopic(notToBe).hasKnownStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED))
                .then();
    }

    // (FUTURE) Re-enable when contract rent is enabled
    final Stream<DynamicTest> rentItemizedAsExpectedWithOverridePriceTiers() {
        final var slotUser = "SlotUser";
        final var creation = "creation";
        final var aSet = "aSet";
        final var failedSet = "failedSet";
        final var bSet = "bSet";
        final var autoRenew = "autoRenew";
        final var oddGasAmount = 666_666L;
        final String datumAbi = "{\"inputs\":[],\"name\":\"datum\",\"outputs\":"
                + "[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}],"
                + "\"stateMutability\":\"view\",\"type\":\"function\"}";
        final AtomicLong expectedStorageFee = new AtomicLong();
        return propertyPreservingHapiSpec("RentItemizedAsExpectedWithOverridePriceTiers")
                .preserving(
                        "contracts.freeStorageTierLimit",
                        "contract.storageSlotPriceTiers",
                        "staking.fees.nodeRewardPercentage",
                        "staking.fees.stakingRewardPercentage")
                .given(
                        uploadInitCode(slotUser),
                        cryptoCreate(autoRenew).balance(0L),
                        contractCreate(slotUser)
                                .autoRenewAccountId(autoRenew)
                                .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                                .via(creation),
                        getTxnRecord(creation).hasNonStakingChildRecordCount(1))
                .when(
                        overridingThree(
                                "contract.storageSlotPriceTiers",
                                "10000til100M",
                                "staking.fees.nodeRewardPercentage",
                                "0",
                                "staking.fees.stakingRewardPercentage",
                                "0"),
                        // Validate free tier is respected
                        contractCall(slotUser, "consumeB", BigInteger.ONE).via(bSet),
                        getTxnRecord(bSet).hasNonStakingChildRecordCount(0),
                        contractCallLocal(slotUser, "slotB")
                                .exposingTypedResultsTo(results -> assertEquals(BigInteger.ONE, results[0])),
                        overriding("contracts.freeStorageTierLimit", "0"),
                        // And validate auto-renew account must be storage fees must be payable
                        contractCall(slotUser, "consumeA", BigInteger.TWO, BigInteger.valueOf(3))
                                .gas(oddGasAmount)
                                .via(failedSet)
                                .hasKnownStatus(INSUFFICIENT_BALANCES_FOR_STORAGE_RENT),
                        // All gas should be consumed
                        getTxnRecord(failedSet)
                                .logged()
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith().gasUsed(oddGasAmount))),
                        // And of course no state should actually change
                        contractCallLocal(slotUser, "slotA")
                                .exposingTypedResultsTo(results -> assertEquals(BigInteger.ZERO, results[0])),
                        contractCallLocalWithFunctionAbi(slotUser, datumAbi)
                                .exposingTypedResultsTo(results -> assertEquals(BigInteger.ZERO, results[0])),
                        // Now fund the contract's auto-renew account and confirm payment accepted
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, autoRenew, 5 * ONE_HBAR)),
                        contractCall(slotUser, "consumeA", BigInteger.TWO, BigInteger.ONE)
                                .via(aSet),
                        contractCallLocal(slotUser, "slotA")
                                .exposingTypedResultsTo(results -> assertEquals(BigInteger.TWO, results[0])),
                        contractCallLocalWithFunctionAbi(slotUser, datumAbi)
                                .exposingTypedResultsTo(results -> assertEquals(BigInteger.ONE, results[0])),
                        withOpContext((spec, opLog) -> {
                            final var expiryLookup = getContractInfo(slotUser);
                            final var callTimeLookup = getTxnRecord(aSet);
                            allRunFor(spec, expiryLookup, callTimeLookup);
                            final var lifetime = expiryLookup
                                            .getResponse()
                                            .getContractGetInfo()
                                            .getContractInfo()
                                            .getExpirationTime()
                                            .getSeconds()
                                    - callTimeLookup
                                            .getResponseRecord()
                                            .getConsensusTimestamp()
                                            .getSeconds();
                            final var tcFeePerSlot = 10 * TINY_PARTS_PER_WHOLE * lifetime / 31536000L;
                            final var tbFeePerSlot = spec.ratesProvider().toTbWithActiveRates(tcFeePerSlot);
                            expectedStorageFee.set(2 * tbFeePerSlot);
                        }),
                        sourcing(() -> getTxnRecord(aSet)
                                .logged()
                                .hasChildRecords(recordWith()
                                        .transfers(including(
                                                tinyBarsFromTo(autoRenew, FUNDING, expectedStorageFee.get()))))))
                .then();
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> messageSubmissionSizeChange() {
        final var defaultMaxBytesAllowed = 1024;
        final var longMessage = TxnUtils.randomUtf8Bytes(defaultMaxBytesAllowed);

        return propertyPreservingHapiSpec("messageSubmissionSizeChange")
                .preserving("consensus.message.maxBytesAllowed")
                .given(newKeyNamed("submitKey"), createTopic(TEST_TOPIC).submitKeyName("submitKey"))
                .when(
                        cryptoCreate(CIVILIAN),
                        submitMessageTo(TEST_TOPIC)
                                .message("testmessage")
                                .payingWith(CIVILIAN)
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(SUCCESS),
                        overriding("consensus.message.maxBytesAllowed", String.valueOf(defaultMaxBytesAllowed - 1)))
                .then(submitMessageTo(TEST_TOPIC)
                        .message(longMessage)
                        .payingWith(CIVILIAN)
                        .hasRetryPrecheckFrom(BUSY)
                        .hasKnownStatus(MESSAGE_SIZE_TOO_LARGE));
    }
}
