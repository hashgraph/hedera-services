/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.opcodes;

import com.esaulpaugh.headlong.abi.Address;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.queries.contract.HapiContractCallLocal;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.utility.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.services.bdd.junit.ContextRequirement.NO_CONCURRENT_CREATIONS;
import static com.hedera.services.bdd.junit.ContextRequirement.PROPERTY_OVERRIDES;
import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.accountIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContractString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.contractIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.explicitBytesOf;
import static com.hedera.services.bdd.spec.HapiPropertySource.literalIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocalWithFunctionAbi;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedContractBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getLiteralAliasContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCryptoTransferToAddress;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.ifHapiTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.ifNotHapiTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.ALLOW_SKIPPED_ENTITY_IDS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.FULLY_NONDETERMINISTIC;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONSTRUCTOR_PARAMETERS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONTRACT_CALL_RESULTS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_ETHEREUM_DATA;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_LOG_DATA;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.RELAYER;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SOURCE_KEY;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_TREASURY;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.aaWith;
import static com.hedera.services.bdd.suites.contract.Utils.accountId;
import static com.hedera.services.bdd.suites.contract.Utils.aliasContractIdKey;
import static com.hedera.services.bdd.suites.contract.Utils.aliasDelegateContractKey;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.captureChildCreate2MetaFor;
import static com.hedera.services.bdd.suites.contract.Utils.captureOneChildCreate2MetaFor;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.ocWith;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.A_TOKEN;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_MEMO;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.NFT_CREATE;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.NFT_INFINITE_SUPPLY_TOKEN;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.PARTY;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.TOKEN_A_CREATE;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.TokenSupplyType.FINITE;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.swirlds.common.utility.CommonUtils.hex;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(SMART_CONTRACT)
public class Create2OperationSuite {

    public static final String GET_BYTECODE = "getBytecode";
    public static final String DEPLOY = "deploy";
    public static final String SALT = "aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011";
    public static final String RETURNER_REPORTED_LOG_MESSAGE = "Returner reported {} when called with mirror address";
    public static final String CONTRACT_REPORTED_LOG_MESSAGE = "Contract reported TestContract initcode is {} bytes";
    public static final String CONTRACT_REPORTED_ADDRESS_MESSAGE = "Contract reported address results {}";
    public static final String EXPECTED_CREATE2_ADDRESS_MESSAGE = "  --> Expected CREATE2 address is {}";
    public static final String GET_ADDRESS = "getAddress";
    private static final Logger LOG = LogManager.getLogger(Create2OperationSuite.class);
    private static final String CREATION = "creation";
    private static final String CREATE_2_TXN = "create2Txn";
    private static final String SWISS = "swiss";
    private static final String RETURNER = "Returner";
    private static final String CALL_RETURNER = "callReturner";
    private static final String ADMIN_KEY = "adminKey";
    private static final String ENTITY_MEMO = "JUST DO IT";
    private static final String DELETED_CREATE_2_LOG = "Deleted the deployed CREATE2 contract using HAPI";

    @SuppressWarnings("java:S5669")
    @LeakyHapiTest(NO_CONCURRENT_CREATIONS)
    final Stream<DynamicTest> allLogOpcodesResolveExpectedContractId() {
        final var contract = "OuterCreator";

        final AtomicLong outerCreatorNum = new AtomicLong();
        final var msg = new byte[] {(byte) 0xAB};
        final var noisyTxn = "noisyTxn";

        return defaultHapiSpec("AllLogOpcodesResolveExpectedContractId", FULLY_NONDETERMINISTIC)
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .payingWith(GENESIS)
                                .via(CREATION)
                                .exposingNumTo(outerCreatorNum::set))
                .when(contractCall(contract, "startChain", msg).gas(4_000_000).via(noisyTxn))
                .then(sourcing(() -> {
                    final var idOfFirstThreeLogs = "0.0." + (outerCreatorNum.get() + 1);
                    final var idOfLastTwoLogs = "0.0." + (outerCreatorNum.get() + 2);
                    return getTxnRecord(noisyTxn)
                            .andAllChildRecords()
                            .hasPriority(recordWith()
                                    .contractCallResult(resultWith()
                                            .logs(inOrder(
                                                    logWith().contract(idOfFirstThreeLogs),
                                                    logWith().contract(idOfFirstThreeLogs),
                                                    logWith().contract(idOfFirstThreeLogs),
                                                    logWith().contract(idOfLastTwoLogs),
                                                    logWith().contract(idOfLastTwoLogs)))))
                            .logged();
                }));
    }

    // https://github.com/hashgraph/hedera-services/issues/2868
    @LeakyHapiTest(NO_CONCURRENT_CREATIONS)
    final Stream<DynamicTest> inlineCreate2CanFailSafely() {
        final var tcValue = 1_234L;
        final var contract = "RevertingCreateFactory";
        final var foo = BigInteger.valueOf(22);
        final var salt = BigInteger.valueOf(23);
        final var timesToFail = 7;
        final AtomicLong factoryEntityNum = new AtomicLong();
        final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
        final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();

        return defaultHapiSpec(
                        "InlineCreate2CanFailSafely", NONDETERMINISTIC_FUNCTION_PARAMETERS, ALLOW_SKIPPED_ENTITY_IDS)
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .payingWith(GENESIS)
                                .via(CREATION)
                                .exposingNumTo(num -> {
                                    factoryEntityNum.set(num);
                                    factoryEvmAddress.set(asHexedSolidityAddress(0, 0, num));
                                }))
                .when(sourcing(
                        () -> contractCallLocal(contract, GET_BYTECODE, asHeadlongAddress(factoryEvmAddress.get()), foo)
                                .exposingTypedResultsTo(results -> {
                                    final var tcInitcode = (byte[]) results[0];
                                    testContractInitcode.set(tcInitcode);
                                    LOG.info(CONTRACT_REPORTED_LOG_MESSAGE, tcInitcode.length);
                                })
                                .payingWith(GENESIS)
                                .nodePayment(ONE_HBAR)))
                .then(
                        inParallel(IntStream.range(0, timesToFail)
                                .mapToObj(i ->
                                        sourcing(() -> contractCall(contract, DEPLOY, testContractInitcode.get(), salt)
                                                .payingWith(GENESIS)
                                                .gas(4_000_000L)
                                                .sending(tcValue)
                                                .via(CREATION)))
                                .toArray(HapiSpecOperation[]::new)),
                        sourcing(() -> cryptoCreate("nextUp")
                                .exposingCreatedIdTo(id -> LOG.info(
                                        "Next entity num was {}" + " instead of expected" + " {}",
                                        id.getAccountNum(),
                                        factoryEntityNum.get() + 1))));
    }

    @SuppressWarnings("java:S5669")
    @LeakyHapiTest(NO_CONCURRENT_CREATIONS)
    final Stream<DynamicTest> inlineCreateCanFailSafely() {
        final var tcValue = 1_234L;
        final var creation = CREATION;
        final var contract = "RevertingCreateFactory";

        final var foo = BigInteger.valueOf(22);
        final var timesToFail = 7;
        final AtomicLong factoryEntityNum = new AtomicLong();
        final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
        final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();

        return defaultHapiSpec(
                        "InlineCreateCanFailSafely", NONDETERMINISTIC_FUNCTION_PARAMETERS, ALLOW_SKIPPED_ENTITY_IDS)
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .payingWith(GENESIS)
                                .via(creation)
                                .exposingNumTo(num -> {
                                    factoryEntityNum.set(num);
                                    factoryEvmAddress.set(asHexedSolidityAddress(0, 0, num));
                                }))
                .when(sourcing(
                        () -> contractCallLocal(contract, GET_BYTECODE, asHeadlongAddress(factoryEvmAddress.get()), foo)
                                .exposingTypedResultsTo(results -> {
                                    final var tcInitcode = (byte[]) results[0];
                                    testContractInitcode.set(tcInitcode);
                                    LOG.info(CONTRACT_REPORTED_LOG_MESSAGE, tcInitcode.length);
                                })
                                .payingWith(GENESIS)
                                .nodePayment(ONE_HBAR)))
                .then(
                        inParallel(IntStream.range(0, timesToFail)
                                .mapToObj(i -> sourcing(
                                        () -> contractCall(contract, DEPLOY, testContractInitcode.get(), BigInteger.ONE)
                                                .payingWith(GENESIS)
                                                .gas(4_000_000L)
                                                .sending(tcValue)
                                                .via(creation)))
                                .toArray(HapiSpecOperation[]::new)),
                        sourcing(() -> cryptoCreate("nextUp")
                                .exposingCreatedIdTo(id -> LOG.info(
                                        "Next entity num was {}" + " instead of expected" + " {}",
                                        id.getAccountNum(),
                                        factoryEntityNum.get() + 1))));
    }

    @HapiTest
    final Stream<DynamicTest> canAssociateInConstructor() {
        final var token = "token";
        final var contract = "SelfAssociating";
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec(
                        "CanAssociateInConstructor",
                        NONDETERMINISTIC_CONSTRUCTOR_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
                        uploadInitCode(contract),
                        tokenCreate(token)
                                .exposingCreatedIdTo(
                                        id -> tokenMirrorAddr.set(hex(asAddress(HapiPropertySource.asToken(id))))))
                .when(sourcing(() -> contractCreate(contract, asHeadlongAddress(tokenMirrorAddr.get()))
                        .payingWith(GENESIS)
                        .omitAdminKey()
                        .gas(4_000_000)
                        .via(CREATION)))
                .then(
                        //						tokenDissociate(contract, token)
                        getContractInfo(contract).logged());
    }

    @HapiTest
    final Stream<DynamicTest> payableCreate2WorksAsExpected() {
        final var contract = "PayableCreate2Deploy";
        AtomicReference<String> tcMirrorAddr2 = new AtomicReference<>();
        AtomicReference<String> tcAliasAddr2 = new AtomicReference<>();

        return defaultHapiSpec("PayableCreate2WorksAsExpected", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).payingWith(GENESIS).gas(1_000_000))
                .when(
                        contractCall(contract, "testPayableCreate")
                                .sending(100L)
                                .via("testCreate2"),
                        captureOneChildCreate2MetaFor(
                                "Test contract create2", "testCreate2", tcMirrorAddr2, tcAliasAddr2))
                .then(sourcing(() ->
                        getContractInfo(tcMirrorAddr2.get()).has(contractWith().balance(100))));
    }

    // https://github.com/hashgraph/hedera-services/issues/2867
    // https://github.com/hashgraph/hedera-services/issues/2868
    @SuppressWarnings("java:S5960")
    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> create2FactoryWorksAsExpected() {
        final var tcValue = 1_234L;
        final var contract = "Create2Factory";
        final var testContract = "TestContract";
        final var salt = BigInteger.valueOf(42);
        final var adminKey = ADMIN_KEY;
        final var replAdminKey = "replAdminKey";
        final var customAutoRenew = 7776001L;
        final var autoRenewAccountID = "autoRenewAccount";
        final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
        final AtomicReference<String> expectedCreate2Address = new AtomicReference<>();
        final AtomicReference<String> expectedMirrorAddress = new AtomicReference<>();
        final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();
        final AtomicReference<byte[]> bytecodeFromMirror = new AtomicReference<>();
        final AtomicReference<byte[]> bytecodeFromAlias = new AtomicReference<>();
        final AtomicReference<String> mirrorLiteralId = new AtomicReference<>();

        return propertyPreservingHapiSpec(
                        "Create2FactoryWorksAsExpected",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_LOG_DATA)
                .preserving("contracts.evm.version")
                .given(
                        overriding("contracts.evm.version", "v0.46"),
                        newKeyNamed(adminKey),
                        newKeyNamed(replAdminKey),
                        uploadInitCode(contract),
                        cryptoCreate(autoRenewAccountID).balance(ONE_HUNDRED_HBARS),
                        contractCreate(contract)
                                .payingWith(GENESIS)
                                .adminKey(adminKey)
                                .entityMemo(ENTITY_MEMO)
                                .autoRenewSecs(customAutoRenew)
                                .autoRenewAccountId(autoRenewAccountID)
                                .via(CREATE_2_TXN)
                                .exposingNumTo(num -> factoryEvmAddress.set(asHexedSolidityAddress(0, 0, num))),
                        getContractInfo(contract)
                                .has(contractWith().autoRenewAccountId(autoRenewAccountID))
                                .logged())
                .when(
                        sourcing(() -> contractCallLocal(
                                        contract, GET_BYTECODE, asHeadlongAddress(factoryEvmAddress.get()), salt)
                                .exposingTypedResultsTo(results -> {
                                    final var tcInitcode = (byte[]) results[0];
                                    testContractInitcode.set(tcInitcode);
                                    LOG.info(CONTRACT_REPORTED_LOG_MESSAGE, tcInitcode.length);
                                })
                                .payingWith(GENESIS)
                                .nodePayment(ONE_HBAR)),
                        sourcing(() -> setExpectedCreate2Address(
                                contract, salt, expectedCreate2Address, testContractInitcode)),
                        // https://github.com/hashgraph/hedera-services/issues/2867 - cannot
                        // re-create same address
                        sourcing(() -> contractCall(contract, "wronglyDeployTwice", testContractInitcode.get(), salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .sending(tcValue)
                                .hasKnownStatusFrom(INVALID_SOLIDITY_ADDRESS, CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> getContractInfo(expectedCreate2Address.get())
                                .hasCostAnswerPrecheck(INVALID_CONTRACT_ID)),
                        sourcing(() -> contractCall(contract, DEPLOY, testContractInitcode.get(), salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .sending(tcValue)),
                        sourcing(() ->
                                contractDelete(expectedCreate2Address.get()).signedBy(DEFAULT_PAYER, adminKey)),
                        logIt(DELETED_CREATE_2_LOG),
                        sourcing(() -> contractCall(contract, DEPLOY, testContractInitcode.get(), salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .sending(tcValue)
                                .via(CREATE_2_TXN)),
                        logIt("Re-deployed the CREATE2 contract"),
                        sourcing(() -> childRecordsCheck(
                                CREATE_2_TXN,
                                SUCCESS,
                                recordWith()
                                        .contractCreateResult(
                                                resultWith().hexedEvmAddress(expectedCreate2Address.get()))
                                        .status(SUCCESS))),
                        withOpContext((spec, opLog) -> {
                            final var parentId = spec.registry().getContractId(contract);
                            final var childId = ContractID.newBuilder()
                                    .setContractNum(parentId.getContractNum() + 2L)
                                    .build();
                            mirrorLiteralId.set("0.0." + childId.getContractNum());
                            expectedMirrorAddress.set(hex(asSolidityAddress(childId)));
                        }),
                        sourcing(() ->
                                getContractBytecode(mirrorLiteralId.get()).exposingBytecodeTo(bytecodeFromMirror::set)),
                        // https://github.com/hashgraph/hedera-services/issues/2874
                        sourcing(() -> getContractBytecode(expectedCreate2Address.get())
                                .exposingBytecodeTo(bytecodeFromAlias::set)),
                        withOpContext((spec, opLog) -> assertArrayEquals(
                                bytecodeFromAlias.get(),
                                bytecodeFromMirror.get(),
                                "Bytecode should be get-able using alias")),
                        sourcing(() -> contractUpdate(expectedCreate2Address.get())
                                .signedBy(DEFAULT_PAYER, adminKey, replAdminKey)
                                .newKey(replAdminKey)))
                .then(
                        sourcing(() -> contractCall(contract, DEPLOY, testContractInitcode.get(), salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                /* Cannot repeat CREATE2 with same args without destroying the existing contract */
                                .hasKnownStatusFrom(INVALID_SOLIDITY_ADDRESS, CONTRACT_REVERT_EXECUTED)),
                        // https://github.com/hashgraph/hedera-services/issues/2874
                        // autoRenewAccountID is inherited from the sender
                        sourcing(() -> getContractInfo(expectedCreate2Address.get())
                                .has(contractWith()
                                        .addressOrAlias(expectedCreate2Address.get())
                                        .autoRenewAccountId(autoRenewAccountID))
                                .logged()),
                        sourcing(() -> contractCallLocalWithFunctionAbi(
                                        expectedCreate2Address.get(), getABIFor(FUNCTION, "getBalance", testContract))
                                .payingWith(GENESIS)
                                .has(resultWith()
                                        .resultThruAbi(
                                                getABIFor(FUNCTION, "getBalance", testContract),
                                                isLiteralResult(new Object[] {BigInteger.valueOf(tcValue)})))),
                        // autoRenewAccountID is inherited from the sender
                        sourcing(() -> getContractInfo(expectedMirrorAddress.get())
                                .has(contractWith()
                                        .adminKey(replAdminKey)
                                        .addressOrAlias(expectedCreate2Address.get())
                                        .autoRenewAccountId(autoRenewAccountID))
                                .logged()),
                        sourcing(() -> contractCallWithFunctionAbi(
                                        expectedCreate2Address.get(),
                                        getABIFor(FUNCTION, "vacateAddress", testContract))
                                .payingWith(GENESIS)),
                        sourcing(() -> getContractInfo(expectedCreate2Address.get())
                                .hasCostAnswerPrecheck(INVALID_CONTRACT_ID)));
    }

    @SuppressWarnings("java:S5960")
    @HapiTest
    final Stream<DynamicTest> canMergeCreate2ChildWithHollowAccount() {
        final var tcValue = 1_234L;
        final var contract = "Create2Factory";
        final var creation = CREATION;
        final var salt = BigInteger.valueOf(42);
        final var adminKey = ADMIN_KEY;
        final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
        final AtomicReference<String> expectedCreate2Address = new AtomicReference<>();
        final AtomicReference<String> hollowCreationAddress = new AtomicReference<>();
        final AtomicReference<String> mergedAliasAddr = new AtomicReference<>();
        final AtomicReference<String> mergedMirrorAddr = new AtomicReference<>();
        final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();

        final var initialTokenSupply = 1000;
        final AtomicReference<TokenID> ftId = new AtomicReference<>();
        final AtomicReference<TokenID> nftId = new AtomicReference<>();
        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<ByteString> partyAlias = new AtomicReference<>();

        return defaultHapiSpec(
                        "CanMergeCreate2ChildWithHollowAccount",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_LOG_DATA)
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(MULTI_KEY),
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .payingWith(GENESIS)
                                .adminKey(adminKey)
                                .entityMemo(ENTITY_MEMO)
                                .via(CREATE_2_TXN)
                                .exposingNumTo(num -> factoryEvmAddress.set(asHexedSolidityAddress(0, 0, num))),
                        cryptoCreate(PARTY).maxAutomaticTokenAssociations(2),
                        tokenCreate(A_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .supplyType(FINITE)
                                .initialSupply(initialTokenSupply)
                                .maxSupply(10L * initialTokenSupply)
                                .treasury(PARTY)
                                .via(TOKEN_A_CREATE),
                        tokenCreate(NFT_INFINITE_SUPPLY_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(PARTY)
                                .via(NFT_CREATE),
                        mintToken(
                                NFT_INFINITE_SUPPLY_TOKEN,
                                List.of(ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b"))),
                        setIdentifiers(ftId, nftId, partyId, partyAlias))
                .when(
                        sourcing(() -> contractCallLocal(
                                        contract, GET_BYTECODE, asHeadlongAddress(factoryEvmAddress.get()), salt)
                                .exposingTypedResultsTo(results -> {
                                    final var tcInitcode = (byte[]) results[0];
                                    testContractInitcode.set(tcInitcode);
                                    LOG.info(CONTRACT_REPORTED_LOG_MESSAGE, tcInitcode.length);
                                })
                                .payingWith(GENESIS)
                                .nodePayment(ONE_HBAR)),
                        sourcing(() -> setExpectedCreate2Address(
                                contract, salt, expectedCreate2Address, testContractInitcode)),
                        // Now create a hollow account at the desired address
                        lazyCreateAccount(creation, expectedCreate2Address, ftId, nftId, partyAlias),
                        getTxnRecord(creation)
                                .andAllChildRecords()
                                .logged()
                                .exposingCreationsTo(l -> hollowCreationAddress.set(l.get(0))),
                        sourcing(() ->
                                getAccountInfo(hollowCreationAddress.get()).logged()))
                .then(
                        sourcing(() -> contractCall(contract, "wronglyDeployTwice", testContractInitcode.get(), salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .sending(tcValue)
                                .hasKnownStatusFrom(INVALID_SOLIDITY_ADDRESS, CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> contractCall(contract, DEPLOY, testContractInitcode.get(), salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .sending(tcValue)
                                .via("TEST2")),
                        getTxnRecord("TEST2").andAllChildRecords().logged(),
                        captureOneChildCreate2MetaFor(
                                "Merged deployed contract with hollow account",
                                "TEST2",
                                mergedMirrorAddr,
                                mergedAliasAddr),
                        sourcing(() -> contractCall(contract, DEPLOY, testContractInitcode.get(), salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                /* Cannot repeat CREATE2
                                with same args without destroying the existing contract */

                                .hasKnownStatusFrom(INVALID_SOLIDITY_ADDRESS, CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> getContractInfo(mergedAliasAddr.get())
                                .has(contractWith()
                                        .numKvPairs(2)
                                        .hasStandinContractKey()
                                        .maxAutoAssociations(2)
                                        .hasAlreadyUsedAutomaticAssociations(2)
                                        .memo(LAZY_MEMO)
                                        .balance(ONE_HBAR + tcValue))
                                .hasToken(relationshipWith(A_TOKEN).balance(500))
                                .hasToken(relationshipWith(NFT_INFINITE_SUPPLY_TOKEN)
                                        .balance(1))
                                .logged()),
                        sourcing(
                                () -> getContractBytecode(mergedAliasAddr.get()).isNonEmpty()),
                        sourcing(() ->
                                assertCreate2Address(contract, salt, expectedCreate2Address, testContractInitcode)));
    }

    @SuppressWarnings("java:S5960")
    @HapiTest
    final Stream<DynamicTest> canMergeCreate2MultipleCreatesWithHollowAccount() {
        final var tcValue = 1_234L;
        final var contract = "Create2MultipleCreates";
        final var creation = CREATION;
        final var salt = BigInteger.valueOf(42);
        final var adminKey = ADMIN_KEY;
        final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
        final AtomicReference<String> expectedCreate2Address = new AtomicReference<>();
        final AtomicReference<String> hollowCreationAddress = new AtomicReference<>();
        final AtomicReference<String> mergedAliasAddr = new AtomicReference<>();
        final AtomicReference<String> mergedMirrorAddr = new AtomicReference<>();
        final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();

        final var initialTokenSupply = 1000;
        final AtomicReference<TokenID> ftId = new AtomicReference<>();
        final AtomicReference<TokenID> nftId = new AtomicReference<>();
        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<ByteString> partyAlias = new AtomicReference<>();

        return defaultHapiSpec("CanMergeCreate2MultipleCreatesWithHollowAccount", FULLY_NONDETERMINISTIC)
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(MULTI_KEY),
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .payingWith(GENESIS)
                                .adminKey(adminKey)
                                .entityMemo(ENTITY_MEMO)
                                .gas(10_000_000L)
                                .via(CREATE_2_TXN)
                                .exposingNumTo(num -> factoryEvmAddress.set(asHexedSolidityAddress(0, 0, num))),
                        cryptoCreate(PARTY).maxAutomaticTokenAssociations(2),
                        tokenCreate(A_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .supplyType(FINITE)
                                .initialSupply(initialTokenSupply)
                                .maxSupply(10L * initialTokenSupply)
                                .treasury(PARTY)
                                .via(TOKEN_A_CREATE),
                        tokenCreate(NFT_INFINITE_SUPPLY_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(PARTY)
                                .via(NFT_CREATE),
                        mintToken(
                                NFT_INFINITE_SUPPLY_TOKEN,
                                List.of(ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b"))),
                        setIdentifiers(ftId, nftId, partyId, partyAlias))
                .when(
                        sourcing(() -> contractCallLocal(
                                        contract, GET_BYTECODE, asHeadlongAddress(factoryEvmAddress.get()), salt)
                                .exposingTypedResultsTo(results -> {
                                    final var tcInitcode = (byte[]) results[0];
                                    testContractInitcode.set(tcInitcode);
                                    LOG.info(CONTRACT_REPORTED_LOG_MESSAGE, tcInitcode.length);
                                })
                                .payingWith(GENESIS)
                                .nodePayment(ONE_HBAR)),
                        sourcing(() -> setExpectedCreate2Address(
                                contract, salt, expectedCreate2Address, testContractInitcode)),
                        // Now create a hollow account at the desired address
                        lazyCreateAccount(creation, expectedCreate2Address, ftId, nftId, partyAlias),
                        getTxnRecord(creation)
                                .andAllChildRecords()
                                .exposingCreationsTo(l -> hollowCreationAddress.set(l.get(0))),
                        sourcing(() ->
                                getAccountInfo(hollowCreationAddress.get()).logged()))
                .then(
                        sourcing(() -> contractCall(contract, DEPLOY, testContractInitcode.get(), salt)
                                .payingWith(GENESIS)
                                .gas(10_000_000L)
                                .sending(tcValue)
                                .via(CREATE_2_TXN)),
                        // mod-service externalizes internal creations in order of their initiation,
                        // while mono-service externalizes them in order of their completion
                        ifHapiTest(captureChildCreate2MetaFor(
                                3,
                                0,
                                "Merged deployed contract with hollow account",
                                CREATE_2_TXN,
                                mergedMirrorAddr,
                                mergedAliasAddr)),
                        ifNotHapiTest(captureChildCreate2MetaFor(
                                3,
                                2,
                                "Merged deployed contract with hollow account",
                                CREATE_2_TXN,
                                mergedMirrorAddr,
                                mergedAliasAddr)),
                        withOpContext((spec, opLog) -> {
                            final var opExpectedMergedNonce = getTxnRecord(CREATE_2_TXN)
                                    .andAllChildRecords()
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith()
                                                    .contractWithNonce(
                                                            contractIdFromHexedMirrorAddress(mergedMirrorAddr.get()),
                                                            3L)));
                            allRunFor(spec, opExpectedMergedNonce);
                        }),
                        sourcing(() -> getContractInfo(mergedAliasAddr.get())
                                .has(contractWith()
                                        .numKvPairs(4)
                                        .hasStandinContractKey()
                                        .maxAutoAssociations(2)
                                        .hasAlreadyUsedAutomaticAssociations(2)
                                        .memo(LAZY_MEMO)
                                        .balance(ONE_HBAR + tcValue))
                                .hasToken(relationshipWith(A_TOKEN).balance(500))
                                .hasToken(relationshipWith(NFT_INFINITE_SUPPLY_TOKEN)
                                        .balance(1))
                                .logged()),
                        sourcing(
                                () -> getContractBytecode(mergedAliasAddr.get()).isNonEmpty()),
                        sourcing(() ->
                                assertCreate2Address(contract, salt, expectedCreate2Address, testContractInitcode)),
                        cryptoCreate("confirmingNoEntityIdCollision"));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> canCallFinalizedContractViaHapi() {
        final var contract = "FinalizedDestructible";
        final var salt = BigInteger.valueOf(1_234_567_890L);
        final AtomicReference<Address> childAddress = new AtomicReference<>();
        final AtomicReference<ContractID> childId = new AtomicReference<>();
        final var vacateAddressAbi =
                "{\"inputs\":[],\"name\":\"vacateAddress\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

        return propertyPreservingHapiSpec(
                        "CanCallFinalizedContractViaHapi",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .preserving("contracts.evm.version")
                .given(
                        overriding("contracts.evm.version", "v0.46"),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)),
                        uploadInitCode(contract),
                        contractCreate(contract).payingWith(GENESIS).gas(500_000L),
                        contractCallLocal(contract, "computeChildAddress", salt)
                                .exposingTypedResultsTo(results -> childAddress.set((Address) results[0])),
                        sourcing(() -> ethereumCryptoTransferToAddress(childAddress.get(), ONE_HBAR)
                                .gasLimit(2_000_000)))
                .when(
                        sourcing(() -> getAliasedAccountInfo(ByteString.copyFrom(explicitBytesOf(childAddress.get())))
                                .has(accountWith().balance(ONE_HBAR))),
                        contractCall(contract, "deployDeterministicChild", salt)
                                .sending(ONE_HBAR)
                                .gas(2_000_000),
                        sourcing(() -> getLiteralAliasContractInfo(asLiteralHexed(childAddress.get()))
                                .exposingContractId(childId::set)
                                .has(contractWith().balance(2 * ONE_HBAR))),
                        sourcing(() ->
                                contractCallWithFunctionAbi(asLiteralHexed(childAddress.get()), vacateAddressAbi)))
                .then(sourcing(() -> getContractInfo("0.0." + childId.get().getContractNum())
                        .has(contractWith().isDeleted())));
    }

    @SuppressWarnings("java:S5669")
    @HapiTest
    final Stream<DynamicTest> eip1014AliasIsPriorityInErcOwnerPrecompile() {
        final var ercContract = "ERC721Contract";
        final var pc2User = "Create2PrecompileUser";
        final var nft = "nonFungibleToken";
        final var lookup = "ownerOfPrecompile";

        final AtomicReference<String> userAliasAddr = new AtomicReference<>();
        final AtomicReference<String> userMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> userLiteralId = new AtomicReference<>();
        final AtomicReference<byte[]> nftAddress = new AtomicReference<>();

        final byte[] salt = unhex(SALT);

        return defaultHapiSpec(
                        "Eip1014AliasIsPriorityInErcOwnerPrecompile",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
                        newKeyNamed(SWISS),
                        cryptoCreate(TOKEN_TREASURY),
                        uploadInitCode(ercContract, pc2User),
                        contractCreate(ercContract).omitAdminKey(),
                        contractCreate(pc2User).adminKey(SWISS).payingWith(GENESIS),
                        contractCall(pc2User, "createUser", salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .via(CREATE_2_TXN),
                        captureOneChildCreate2MetaFor("Precompile user", CREATE_2_TXN, userMirrorAddr, userAliasAddr),
                        sourcing(() -> getAliasedContractBalance(userAliasAddr.get())
                                .hasId(accountIdFromHexedMirrorAddress(userMirrorAddr.get()))),
                        withOpContext((spec, opLog) -> userLiteralId.set(
                                asContractString(contractIdFromHexedMirrorAddress(userMirrorAddr.get())))),
                        sourcing(() -> tokenCreate(nft)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(userLiteralId.get())
                                .initialSupply(0L)
                                .supplyKey(SWISS)
                                .fee(ONE_HUNDRED_HBARS)
                                .signedBy(DEFAULT_PAYER, SWISS)),
                        mintToken(nft, List.of(ByteString.copyFromUtf8("PRICELESS"))))
                .when(
                        withOpContext((spec, opLog) -> {
                            final var nftType = spec.registry().getTokenID(nft);
                            nftAddress.set(asSolidityAddress(nftType));
                        }),
                        sourcing(() -> getContractInfo(userLiteralId.get()).logged()),
                        sourcing(() -> contractCall(
                                        ercContract,
                                        "ownerOf",
                                        HapiParserUtil.asHeadlongAddress(nftAddress.get()),
                                        BigInteger.valueOf(1))
                                .via(lookup)
                                .gas(4_000_000)))
                .then(sourcing(() -> childRecordsCheck(
                        lookup,
                        SUCCESS,
                        recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .contractCallResult(htsPrecompileResult()
                                                .forFunction(FunctionType.ERC_OWNER)
                                                .withOwner(unhex(userAliasAddr.get())))))));
    }

    @SuppressWarnings("java:S5669")
    @HapiTest
    final Stream<DynamicTest> canUseAliasesInPrecompilesAndContractKeys() {
        final var creation2 = CREATE_2_TXN;
        final var contract = "Create2PrecompileUser";
        final var userContract = "Create2User";
        final var ft = "fungibleToken";
        final var nft = "nonFungibleToken";
        final var multiKey = SWISS;
        final var ftFail = "ofInterest";
        final var nftFail = "alsoOfInterest";
        final var helperMintFail = "alsoOfExtremeInterest";
        final var helperMintSuccess = "quotidian";

        final AtomicReference<String> userAliasAddr = new AtomicReference<>();
        final AtomicReference<String> userMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> userLiteralId = new AtomicReference<>();
        final AtomicReference<String> hexedNftType = new AtomicReference<>();

        final var salt = unhex(SALT);

        return defaultHapiSpec(
                        "canUseAliasesInPrecompilesAndContractKeys",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE)
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(TOKEN_TREASURY),
                        uploadInitCode(contract),
                        contractCreate(contract).omitAdminKey().payingWith(GENESIS),
                        contractCall(contract, "createUser", salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .via(creation2),
                        captureOneChildCreate2MetaFor("Precompile user", creation2, userMirrorAddr, userAliasAddr),
                        withOpContext((spec, opLog) -> userLiteralId.set(
                                asContractString(contractIdFromHexedMirrorAddress(userMirrorAddr.get())))),
                        tokenCreate(ft)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1_000),
                        tokenCreate(nft)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(multiKey)
                                .initialSupply(0L)
                                .supplyKey(multiKey),
                        mintToken(nft, List.of(ByteString.copyFromUtf8("PRICELESS"))),
                        tokenUpdate(nft)
                                .supplyKey(() -> aliasContractIdKey(userAliasAddr.get()))
                                .signedByPayerAnd(multiKey))
                .when(withOpContext((spec, opLog) -> {
                    final var registry = spec.registry();
                    final var ftType = registry.getTokenID(ft);
                    final var nftType = registry.getTokenID(nft);

                    final var ftAssoc = contractCall(
                                    contract, "associateBothTo", asHeadlongAddress(hex(asSolidityAddress(ftType))))
                            .gas(4_000_000L);
                    final var nftAssoc = contractCall(
                                    contract, "associateBothTo", asHeadlongAddress(hex(asSolidityAddress(nftType))))
                            .gas(4_000_000L);

                    final var fundingXfer = cryptoTransfer(
                            moving(100, ft).between(TOKEN_TREASURY, contract),
                            movingUnique(nft, 1L).between(TOKEN_TREASURY, contract));

                    // https://github.com/hashgraph/hedera-services/issues/2874
                    // (alias in transfer precompile)
                    final var sendFt = contractCall(
                                    contract, "sendFtToUser", asHeadlongAddress(hex(asSolidityAddress(ftType))), 100L)
                            .gas(4_000_000L);
                    final var sendNft = contractCall(
                                    contract, "sendNftToUser", asHeadlongAddress(hex(asSolidityAddress(nftType))), 1L)
                            .via(ftFail)
                            .gas(4_000_000L);
                    final var failFtDissoc = contractCall(
                                    contract, "dissociateBothFrom", asHeadlongAddress(hex(asSolidityAddress(ftType))))
                            .via(ftFail)
                            .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                            .gas(4_000_000L);
                    final var failNftDissoc = contractCall(
                                    contract, "dissociateBothFrom", asHeadlongAddress(hex(asSolidityAddress(nftType))))
                            .via(nftFail)
                            .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                            .gas(4_000_000L);
                    // https://github.com/hashgraph/hedera-services/issues/2876
                    // (mint via ContractID key)
                    final var mint = contractCallWithFunctionAbi(
                                    userAliasAddr.get(),
                                    getABIFor(FUNCTION, "mintNft", userContract),
                                    asHeadlongAddress(hex(asSolidityAddress(nftType))),
                                    new byte[][] {"WoRtHlEsS".getBytes()})
                            .gas(4_000_000L);
                    /* Can't succeed yet because supply key isn't delegatable */
                    hexedNftType.set(hex(asSolidityAddress(nftType)));
                    final var helperMint = contractCallWithFunctionAbi(
                                    userAliasAddr.get(),
                                    getABIFor(FUNCTION, "mintNftViaDelegate", userContract),
                                    asHeadlongAddress(hexedNftType.get()),
                                    new byte[][] {"WoRtHlEsS".getBytes()})
                            .via(helperMintFail)
                            .gas(4_000_000L);

                    allRunFor(
                            spec,
                            ftAssoc,
                            nftAssoc,
                            fundingXfer,
                            sendFt,
                            sendNft,
                            failFtDissoc,
                            failNftDissoc,
                            mint,
                            helperMint);
                }))
                .then(
                        childRecordsCheck(
                                helperMintFail,
                                SUCCESS,
                                /* First record is of helper creation */
                                recordWith().status(SUCCESS),
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_MINT)
                                                        .withTotalSupply(0)
                                                        .withSerialNumbers()
                                                        .withStatus(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        childRecordsCheck(
                                ftFail,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(REVERTED_SUCCESS),
                                recordWith()
                                        .status(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)))),
                        childRecordsCheck(
                                nftFail,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(REVERTED_SUCCESS),
                                recordWith()
                                        .status(ACCOUNT_STILL_OWNS_NFTS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(ACCOUNT_STILL_OWNS_NFTS)))),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(nft, 1),

                        // https://github.com/hashgraph/hedera-services/issues/2876 (mint via
                        // delegatable_contract_id)
                        tokenUpdate(nft)
                                .supplyKey(() -> aliasDelegateContractKey(userAliasAddr.get()))
                                .signedByPayerAnd(multiKey),
                        sourcing(() -> contractCallWithFunctionAbi(
                                        userAliasAddr.get(),
                                        getABIFor(FUNCTION, "mintNftViaDelegate", userContract),
                                        asHeadlongAddress(hexedNftType.get()),
                                        new byte[][] {"WoRtHlEsS...NOT".getBytes()})
                                .via(helperMintSuccess)
                                .gas(4_000_000L)),
                        getTxnRecord(helperMintSuccess).andAllChildRecords().logged(),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(nft, 2),
                        cryptoTransfer((spec, b) -> {
                                    final var registry = spec.registry();
                                    final var tt = registry.getAccountID(TOKEN_TREASURY);
                                    final var ftId = registry.getTokenID(ft);
                                    final var nftId = registry.getTokenID(nft);
                                    b.setTransfers(TransferList.newBuilder()
                                            .addAccountAmounts(aaWith(tt, -666))
                                            .addAccountAmounts(aaWith(userMirrorAddr.get(), +666)));
                                    b.addTokenTransfers(TokenTransferList.newBuilder()
                                                    .setToken(ftId)
                                                    .addTransfers(aaWith(tt, -6))
                                                    .addTransfers(aaWith(userMirrorAddr.get(), +6)))
                                            .addTokenTransfers(TokenTransferList.newBuilder()
                                                    .setToken(nftId)
                                                    .addNftTransfers(NftTransfer.newBuilder()
                                                            .setSerialNumber(2L)
                                                            .setSenderAccountID(tt)
                                                            .setReceiverAccountID(accountId(userMirrorAddr.get()))));
                                })
                                .signedBy(DEFAULT_PAYER, TOKEN_TREASURY),
                        sourcing(() -> getContractInfo(userLiteralId.get()).logged()));
    }

    // https://github.com/hashgraph/hedera-services/issues/2874
    // https://github.com/hashgraph/hedera-services/issues/2925
    @SuppressWarnings("java:S5669")
    @HapiTest
    final Stream<DynamicTest> cannotSelfDestructToMirrorAddress() {
        final var creation2 = CREATE_2_TXN;
        final var messyCreation2 = "messyCreate2Txn";
        final var contract = "CreateDonor";
        final var donorContract = "Donor";

        final AtomicReference<String> donorAliasAddr = new AtomicReference<>();
        final AtomicReference<String> donorMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> mDonorAliasAddr = new AtomicReference<>();
        final AtomicReference<String> mDonorMirrorAddr = new AtomicReference<>();

        final var salt = unhex(SALT);
        final var otherSalt = unhex("aabbccddee880011aabbccddee880011aabbccddee880011aabbccddee880011");

        return defaultHapiSpec(
                        "CannotSelfDestructToMirrorAddress",
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS)
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).payingWith(GENESIS),
                        contractCall(contract, "buildDonor", salt)
                                .sending(1_000)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .via(creation2),
                        captureOneChildCreate2MetaFor("donor", creation2, donorMirrorAddr, donorAliasAddr))
                .when(
                        sourcing(() -> contractCallWithFunctionAbi(
                                        donorAliasAddr.get(),
                                        getABIFor(FUNCTION, "relinquishFundsTo", donorContract),
                                        asHeadlongAddress(donorAliasAddr.get()))
                                .hasKnownStatus(OBTAINER_SAME_CONTRACT_ID)),
                        sourcing(() -> contractCallWithFunctionAbi(
                                        donorAliasAddr.get(),
                                        getABIFor(FUNCTION, "relinquishFundsTo", donorContract),
                                        asHeadlongAddress(donorMirrorAddr.get()))
                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS)))
                .then(
                        contractCall(contract, "buildThenRevertThenBuild", otherSalt)
                                .sending(1_000)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .via(messyCreation2),
                        captureOneChildCreate2MetaFor(
                                "questionableDonor", messyCreation2, mDonorMirrorAddr, mDonorAliasAddr),
                        sourcing(() -> getContractInfo(mDonorAliasAddr.get())
                                .has(contractWith().balance(100))
                                .logged()));
    }

    // https://github.com/hashgraph/hedera-services/issues/2874
    @SuppressWarnings("java:S5669")
    @HapiTest
    final Stream<DynamicTest> canDeleteViaAlias() {
        final var adminKey = ADMIN_KEY;
        final var creation2 = CREATE_2_TXN;
        final var deletion = "deletion";
        final var contract = "SaltingCreatorFactory";
        final var saltingCreator = "SaltingCreator";

        final AtomicReference<String> saltingCreatorAliasAddr = new AtomicReference<>();
        final AtomicReference<String> saltingCreatorMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> saltingCreatorLiteralId = new AtomicReference<>();

        final var salt = unhex(SALT);
        final var otherSalt = unhex("aabbccddee330011aabbccddee330011aabbccddee330011aabbccddee330011");

        return defaultHapiSpec("CanDeleteViaAlias")
                .given(
                        newKeyNamed(adminKey),
                        uploadInitCode(contract),
                        contractCreate(contract).adminKey(adminKey).payingWith(GENESIS),
                        contractCall(contract, "buildCreator", salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .via(creation2),
                        captureOneChildCreate2MetaFor(
                                "Salting creator", creation2, saltingCreatorMirrorAddr, saltingCreatorAliasAddr),
                        withOpContext((spec, opLog) -> saltingCreatorLiteralId.set(
                                asContractString(contractIdFromHexedMirrorAddress(saltingCreatorMirrorAddr.get())))),
                        // https://github.com/hashgraph/hedera-services/issues/2867 (can't
                        // re-create2 after selfdestruct)
                        sourcing(() -> contractCallWithFunctionAbi(
                                        saltingCreatorAliasAddr.get(),
                                        getABIFor(FUNCTION, "createAndRecreateTest", saltingCreator),
                                        otherSalt)
                                .payingWith(GENESIS)
                                .gas(2_000_000L)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .when(
                        sourcing(() -> contractUpdate(saltingCreatorAliasAddr.get())
                                .signedBy(DEFAULT_PAYER, adminKey)
                                .memo("That's why you always leave a note")),
                        sourcing(() -> contractCallLocalWithFunctionAbi(
                                        saltingCreatorAliasAddr.get(),
                                        getABIFor(FUNCTION, "whatTheFoo", saltingCreator))
                                .has(resultWith()
                                        .resultThruAbi(
                                                getABIFor(FUNCTION, "whatTheFoo", saltingCreator),
                                                isLiteralResult(new Object[] {BigInteger.valueOf(42)})))),
                        sourcing(() -> contractDelete(saltingCreatorAliasAddr.get())
                                .signedBy(DEFAULT_PAYER, adminKey)
                                .transferContract(saltingCreatorMirrorAddr.get())
                                .hasKnownStatus(OBTAINER_SAME_CONTRACT_ID)),
                        sourcing(() -> contractDelete(saltingCreatorMirrorAddr.get())
                                .signedBy(DEFAULT_PAYER, adminKey)
                                .transferContract(saltingCreatorAliasAddr.get())
                                .hasKnownStatus(OBTAINER_SAME_CONTRACT_ID)))
                .then(
                        sourcing(() -> getContractInfo(saltingCreatorMirrorAddr.get())
                                .has(contractWith().addressOrAlias(saltingCreatorAliasAddr.get()))),
                        sourcing(() -> contractDelete(saltingCreatorAliasAddr.get())
                                .signedBy(DEFAULT_PAYER, adminKey)
                                .transferAccount(FUNDING)
                                .via(deletion)),
                        sourcing(() -> getTxnRecord(deletion)
                                .hasPriority(recordWith().targetedContractId(saltingCreatorLiteralId.get()))),
                        sourcing(() -> contractDelete(saltingCreatorMirrorAddr.get())
                                .signedBy(DEFAULT_PAYER, adminKey)
                                .transferAccount(FUNDING)
                                .hasPrecheck(CONTRACT_DELETED)),
                        sourcing(() -> getContractInfo(saltingCreatorMirrorAddr.get())
                                .has(contractWith().addressOrAlias(saltingCreatorMirrorAddr.get()))));
    }

    @SuppressWarnings("java:S5669")
    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> create2InputAddressIsStableWithTopLevelCallWhetherMirrorOrAliasIsUsed() {
        final var creation2 = CREATE_2_TXN;
        final var innerCreation2 = "innerCreate2Txn";
        final var delegateCreation2 = "delegateCreate2Txn";
        final var contract = "SaltingCreatorFactory";
        final var saltingCreator = "SaltingCreator";

        final AtomicReference<String> saltingCreatorAliasAddr = new AtomicReference<>();
        final AtomicReference<String> saltingCreatorMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> tcAliasAddr1 = new AtomicReference<>();
        final AtomicReference<String> tcMirrorAddr1 = new AtomicReference<>();
        final AtomicReference<String> tcAliasAddr2 = new AtomicReference<>();
        final AtomicReference<String> tcMirrorAddr2 = new AtomicReference<>();

        final var salt = unhex(SALT);

        return propertyPreservingHapiSpec(
                        "Create2InputAddressIsStableWithTopLevelCallWhetherMirrorOrAliasIsUsed",
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS)
                .preserving("contracts.evm.version")
                .given(
                        overriding("contracts.evm.version", "v0.46"),
                        uploadInitCode(contract),
                        contractCreate(contract).payingWith(GENESIS),
                        contractCall(contract, "buildCreator", salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .via(creation2),
                        captureOneChildCreate2MetaFor(
                                "Salting creator", creation2, saltingCreatorMirrorAddr, saltingCreatorAliasAddr))
                .when(
                        sourcing(() -> contractCallWithFunctionAbi(
                                        saltingCreatorAliasAddr.get(),
                                        getABIFor(FUNCTION, "createSaltedTestContract", saltingCreator),
                                        salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .via(innerCreation2)),
                        sourcing(() -> {
                            final var emitterId = literalIdFromHexedMirrorAddress(saltingCreatorMirrorAddr.get());
                            return getTxnRecord(innerCreation2)
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith()
                                                    .contract(emitterId)
                                                    .logs(inOrder(logWith().contract(emitterId)))))
                                    .andAllChildRecords()
                                    .logged();
                        }),
                        captureOneChildCreate2MetaFor(
                                "Test contract create2'd via mirror address",
                                innerCreation2,
                                tcMirrorAddr1,
                                tcAliasAddr1),
                        sourcing(() -> contractCallWithFunctionAbi(
                                        tcAliasAddr1.get(), getABIFor(FUNCTION, "vacateAddress", "TestContract"))
                                .payingWith(GENESIS)),
                        sourcing(() -> getContractInfo(tcMirrorAddr1.get())
                                .has(contractWith().isDeleted())))
                .then(
                        sourcing(() -> contractCall(
                                        contract, "callCreator", asHeadlongAddress(saltingCreatorAliasAddr.get()), salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .via(delegateCreation2)),
                        captureOneChildCreate2MetaFor(
                                "Test contract create2'd via alias address",
                                delegateCreation2,
                                tcMirrorAddr2,
                                tcAliasAddr2),
                        withOpContext((spec, opLog) -> {
                            assertNotEquals(
                                    tcMirrorAddr1.get(), tcMirrorAddr2.get(), "Mirror addresses must be different");
                            assertEquals(tcAliasAddr1.get(), tcAliasAddr2.get(), "Alias addresses must be stable");
                        }));
    }

    @SuppressWarnings("java:S5669")
    @HapiTest
    final Stream<DynamicTest> priorityAddressIsCreate2ForStaticHapiCalls() {
        final var contract = "AddressValueRet";

        final AtomicReference<String> aliasAddr = new AtomicReference<>();
        final AtomicReference<String> mirrorAddr = new AtomicReference<>();
        final AtomicReference<BigInteger> staticCallAliasAns = new AtomicReference<>();
        final AtomicReference<BigInteger> staticCallMirrorAns = new AtomicReference<>();

        final var salt = unhex(SALT);

        return defaultHapiSpec("PriorityAddressIsCreate2ForStaticHapiCalls")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).payingWith(GENESIS),
                        contractCall(contract, "createReturner", salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .via(CREATE_2_TXN),
                        captureOneChildCreate2MetaFor(RETURNER, CREATE_2_TXN, mirrorAddr, aliasAddr))
                .when(
                        sourcing(() -> contractCallLocalWithFunctionAbi(
                                        mirrorAddr.get(), getABIFor(FUNCTION, "returnThis", RETURNER))
                                .payingWith(GENESIS)
                                .exposingTypedResultsTo(results -> {
                                    LOG.info(RETURNER_REPORTED_LOG_MESSAGE, results);
                                    staticCallMirrorAns.set((BigInteger) results[0]);
                                })),
                        sourcing(() -> contractCallLocalWithFunctionAbi(
                                        aliasAddr.get(), getABIFor(FUNCTION, "returnThis", RETURNER))
                                .payingWith(GENESIS)
                                .exposingTypedResultsTo(results -> {
                                    LOG.info("Returner reported {} when" + " called with alias" + " address", results);
                                    staticCallAliasAns.set((BigInteger) results[0]);
                                })))
                .then(
                        withOpContext((spec, opLog) -> {
                            assertEquals(
                                    staticCallAliasAns.get(),
                                    staticCallMirrorAns.get(),
                                    "Static call with mirror address should be same as call" + " with alias");
                            assertTrue(
                                    aliasAddr
                                            .get()
                                            .endsWith(staticCallAliasAns.get().toString(16)),
                                    "Alias should get priority over mirror address");
                        }),
                        sourcing(() -> contractCallWithFunctionAbi(
                                        aliasAddr.get(), getABIFor(FUNCTION, "createPlaceholder", RETURNER))
                                .gas(4_000_000L)
                                .payingWith(GENESIS)
                                .via(CREATION)));
    }

//    @HapiTest
//    final Stream<DynamicTest> createHollowAccountWithoutAssociatonsAndDeployContractWithCreate2() {
//        final String HOLLOW_ACCOUNT_FACTORY = "HollowAccountFactory";
//        final String HOLLOW_ACCOUNT_ALIAS = "hollowAccountAlias";
//        //final String SALT = "0x12345678";
//        final String CONTRACT_BYTECODE_PATH = "contract/contracts/HollowAccountFactory/HollowAccountFactory.bin";
//
//        final var salt = new byte[32];
//        new Random().nextBytes(salt);
//
//        return defaultHapiSpec("CreateHollowAccountAndDeployContractWithCreate2")
//                .given(
//                        // Deploy the HollowAccountFactory contract
//                        uploadInitCode(HOLLOW_ACCOUNT_FACTORY),
//                        contractCreate(HOLLOW_ACCOUNT_FACTORY)
//                )
//                .when(
//                        // Create a hollow account using CREATE2
//                        contractCall(
//                                HOLLOW_ACCOUNT_FACTORY,
//                                "createHollowAccount",
//                                salt
//                        ).payingWith(DEFAULT_PAYER).via("createHollowAccountTx")
//
//                        // Get the address of the hollow account
////                        contractCallLocal(
////                                HOLLOW_ACCOUNT_FACTORY,
////                                "getHollowAccountAddress",
////                                salt
////                        ).saveResultTo("hollowAccountAddressResult")
//
//                        // Transfer HBAR to the hollow account address
////                        cryptoTransfer(
////                                moving(1_000_000L, DEFAULT_PAYER).to("hollowAccountAddressResult")
////                        ).via("hbarTransferTx")
//                )
//                .then(
//                        // Verify the hollow account creation transaction
////                        getTxnRecord("createHollowAccountTx").logged(),
////
////                        // Verify the HBAR transfer is successful
////                        getTxnRecord("hbarTransferTx").logged(),
////
////                        // Verify the properties of the hollow account
////                        getAccountInfo("hollowAccountAddressResult")
////                                .logged()
////                                        .hasMaxAutomaticAssociations(-1),
////
////                        // Deploy a contract to the hollow account's address using CREATE2
////                        contractCall(
////                                HOLLOW_ACCOUNT_FACTORY,
////                                "deployContract",
////                                salt,
////                                CONTRACT_BYTECODE_PATH
////                        ).payingWith(DEFAULT_PAYER).via("deployContractTx"),
////
////                        // Verify the contract deployment is successful
////                        getTxnRecord("deployContractTx").logged()
//                );
//    }

    //NEW
//@SuppressWarnings("java:S5669")
//@HapiTest
//final Stream<DynamicTest> createHollowAccountWithoutAssociatonsAndDeployContractWithCreate2() {
//    final var contract = "HollowAccountFactory";
//    final String HOLLOW_ACCOUNT_ADDRESS_RESULT = "hollowAccountAddressResult";
//    final var adminKey = ADMIN_KEY;
//
//    final var salt = new byte[32];
//    new Random().nextBytes(salt);
//
//    return defaultHapiSpec("")
//            .given(newKeyNamed(adminKey).shape(KeyShape.ED25519),
//                    cryptoCreate(GENESIS)
//                            .balance(10_000_000_000L)
//                            .key(adminKey),
//                    cryptoCreate(DEFAULT_PAYER).key(adminKey).balance(10_000_000_000L),
//                    // Upload and deploy the HollowAccountFactory contract
//                    uploadInitCode(contract),
//                    contractCreate(contract)
//                            .payingWith(GENESIS)
//            )
//            .when(
//                    // Create a hollow account using CREATE2
//                    contractCall(contract, "createHollowAccount", salt)
//                            .payingWith(GENESIS)
//                            .gas(4_000_000L)
//                            .via(CREATE_2_TXN),
//                    // Get the address of the hollow account
//                    sourcing(() -> contractCallLocal(contract, "getHollowAccountAddress", salt)
//                            .payingWith(GENESIS)
//                            .saveResultTo(HOLLOW_ACCOUNT_ADDRESS_RESULT)),
//
//            ).then(
//                    // Transfer HBAR to the hollow account address
//                    cryptoTransfer(moving(1_000_000L, DEFAULT_PAYER).to(HOLLOW_ACCOUNT_ADDRESS_RESULT))
//                            .signedBy(adminKey)
//                            .via("hbarTransferTx")
//            );
//}

//    @SuppressWarnings("java:S5669")
//    @HapiTest
//    final Stream<DynamicTest> createHollowAccountWithoutAssociatonsAndDeployContractWithCreate2() {
//        final var contract = "Create2HollowAccountFactory";
//        final String DEPLOYED_CONTRACT_ADDRESS = "deployedContractAddress";
//        final String CONTRACT_BYTECODE = "contracts/Create2HollowAccountFactory/Create2HollowAccountFactory.bin";
//        final var adminKey = ADMIN_KEY;
//
//        final var salt = new byte[32];
//        new Random().nextBytes(salt);
//
//        return defaultHapiSpec("")
//                .given(
////                        cryptoCreate(PARTY)
////                                .balance(10_000_000_000L)
////                                .keyShape(KeyShape.ED25519),
//                        // Create the hollow account with maxAutoAssociations = -1
//                        cryptoCreate(RELAYER).balance(100_000_000L),
//                        cryptoCreate("hollowAccount")
//                                .maxAutomaticTokenAssociations(-1)
//                                .balance(0L).receiverSigRequired(true),
//
//                        // Upload and deploy the HollowAccountFactory contract
//                        uploadInitCode(contract),
//                        contractCreate(contract)
//                                .payingWith(GENESIS)
//
//                )
//                .when(
//                        // Transfer HBAR to the hollow account address to create it
//                        cryptoTransfer(
//                                movingHbar(10_000l)
//                                        .between(RELAYER, "hollowAccount")
//                        ).via("hbarTransferTx")
//                        // Get the address for the contract deployment using CREATE2
////                        sourcing(() -> contractCallLocal(
////                                contract,
////                                "getDeploymentAddress",
////                                salt,
////                                CONTRACT_BYTECODE)
////                                .saveResultTo(DEPLOYED_CONTRACT_ADDRESS))
//
//                ).then(
//                        // Verify the HBAR transfer is successful
//                        getTxnRecord("hbarTransferTx")
//                                .hasPriority(
//                                        TransactionRecordAsserts.recordWith().status(SUCCESS)
//                                ),
//                        // Verify the properties of the hollow account
//                        getAccountInfo("hollowAccount")
//                                .logged()
//                                .has(accountWith()
//                                        .maxAutoAssociations(-1)),
//// Deploy a contract to the hollow account's address using CREATE2
//                        withOpContext((spec, log) -> {
//                            var hollowAccountAddress = spec.registry().getAccountID("hollowAccount");
//                            var bytecode = spec.registry().getBytes("6080604052348015600e575f80fd5b506105cf8061001c5f395ff3fe608060405234801561000f575f80fd5b5060043610610034575f3560e01c806309b1eca614610038578063498eacbe14610068575b5f80fd5b610052600480360381019061004d919061034a565b610098565b60405161005f91906103e3565b60405180910390f35b610082600480360381019061007d919061034a565b6100e0565b60405161008f91906103e3565b60405180910390f35b5f8060ff60f81b308585805190602001206040516020016100bc94939291906104ac565b604051602081830303815290604052805190602001209050805f1c91505092915050565b5f80838351602085015ff590505f73ffffffffffffffffffffffffffffffffffffffff168173ffffffffffffffffffffffffffffffffffffffff160361015b576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161015290610553565b60405180910390fd5b8073ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff167f41a1a1e5033fb48fa68ae6842ead9050d540ddf76974d1cee54faf540d1f49e6866040516101b89190610580565b60405180910390a38091505092915050565b5f604051905090565b5f80fd5b5f80fd5b5f819050919050565b6101ed816101db565b81146101f7575f80fd5b50565b5f81359050610208816101e4565b92915050565b5f80fd5b5f80fd5b5f601f19601f8301169050919050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52604160045260245ffd5b61025c82610216565b810181811067ffffffffffffffff8211171561027b5761027a610226565b5b80604052505050565b5f61028d6101ca565b90506102998282610253565b919050565b5f67ffffffffffffffff8211156102b8576102b7610226565b5b6102c182610216565b9050602081019050919050565b828183375f83830152505050565b5f6102ee6102e98461029e565b610284565b90508281526020810184848401111561030a57610309610212565b5b6103158482856102ce565b509392505050565b5f82601f8301126103315761033061020e565b5b81356103418482602086016102dc565b91505092915050565b5f80604083850312156103605761035f6101d3565b5b5f61036d858286016101fa565b925050602083013567ffffffffffffffff81111561038e5761038d6101d7565b5b61039a8582860161031d565b9150509250929050565b5f73ffffffffffffffffffffffffffffffffffffffff82169050919050565b5f6103cd826103a4565b9050919050565b6103dd816103c3565b82525050565b5f6020820190506103f65f8301846103d4565b92915050565b5f7fff0000000000000000000000000000000000000000000000000000000000000082169050919050565b5f819050919050565b61044161043c826103fc565b610427565b82525050565b5f8160601b9050919050565b5f61045d82610447565b9050919050565b5f61046e82610453565b9050919050565b610486610481826103c3565b610464565b82525050565b5f819050919050565b6104a66104a1826101db565b61048c565b82525050565b5f6104b78287610430565b6001820191506104c78286610475565b6014820191506104d78285610495565b6020820191506104e78284610495565b60208201915081905095945050505050565b5f82825260208201905092915050565b7f435245415445323a204661696c6564206f6e206465706c6f79000000000000005f82015250565b5f61053d6019836104f9565b915061054882610509565b602082019050919050565b5f6020820190508181035f83015261056a81610531565b9050919050565b61057a816101db565b82525050565b5f6020820190506105935f830184610571565b9291505056fea264697066735822122070daf70ac50f5180a8778dcf7bfb9b0738483344baa8247ea29477cd8ae6f92064736f6c63430008190033");
//
//                            spec.registry().saveBytes("CONTRACT_BYTECODE", ByteString.copyFrom(bytecode));
//                            spec.registry().saveBytes("SALT", ByteString.copyFrom(salt));
//
//                            allRunFor(
//                                    spec,
//                                    contractCall(
//                                            contract,
//                                            "deployContract",
//                                            spec.registry().getBytes("SALT"),
//                                            spec.registry().getBytes("CONTRACT_BYTECODE")
//                                    ).payingWith(DEFAULT_PAYER).via("deployContractTx")
//                            );
//                        })
////                        // Deploy a contract to the hollow account's address using CREATE2
////                        contractCall(
////                                contract,
////                                "deployContract",
////                                SALT,
////                                CONTRACT_BYTECODE
////                        ).payingWith(DEFAULT_PAYER).via("deployContractTx"),
////
////                        // Verify the contract deployment is successful
////                        getTxnRecord("deployContractTx")
////                                .hasPriority(
////                                        TransactionRecordAsserts.recordWith().status(SUCCESS)
////                                ),
////                        // Verify the properties of the deployed contract
////                        getAccountInfo(DEPLOYED_CONTRACT_ADDRESS)
////                                .logged()
////                                .has(accountWith()
////                                        .maxAutoAssociations(0))
//                );
//}

    @SuppressWarnings("java:S5669")
    @HapiTest
    final Stream<DynamicTest> createHollowAccountWithoutAssociatonsAndDeployContractWithCreate2() {
        final var contract = "Create2Factory";
        final var creation = CREATION;
        final var salt = BigInteger.valueOf(42);
        final var adminKey = ADMIN_KEY;
        final String HOLLOW_ACCOUNT = "hollowAccount";

        final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
        final AtomicReference<String> expectedCreate2Address = new AtomicReference<>();
        final AtomicReference<String> hollowCreationAddress = new AtomicReference<>();
        final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();
        final AtomicReference<TokenID> ftId = new AtomicReference<>();
        final AtomicReference<TokenID> nftId = new AtomicReference<>();
        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<ByteString> partyAlias = new AtomicReference<>();

        return defaultHapiSpec("CreateHollowAccountWithoutAssociatonsAndDeployContractWithCreate2",
                NONDETERMINISTIC_FUNCTION_PARAMETERS,
                NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                NONDETERMINISTIC_TRANSACTION_FEES,
                NONDETERMINISTIC_LOG_DATA)
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(MULTI_KEY),
                        // Create the hollow account with maxAutoAssociations = -1
                        cryptoCreate(HOLLOW_ACCOUNT)
                                .maxAutomaticTokenAssociations(-1)
                                .balance(0L).receiverSigRequired(true),
                        setIdentifiers(ftId, nftId, partyId, partyAlias),
                        // Upload and deploy the contract
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .payingWith(GENESIS)
                                .adminKey(adminKey)
                                .entityMemo(ENTITY_MEMO)
                                .via(CREATE_2_TXN)
                                .exposingNumTo(num ->
                                        factoryEvmAddress.set(asHexedSolidityAddress(0, 0, num))),

                        cryptoCreate(RELAYER).balance(100_000_000_000L),



                )
                .when(
                        sourcing(() -> contractCallLocal(
                                contract, GET_BYTECODE, asHeadlongAddress(factoryEvmAddress.get()), salt)
                                .exposingTypedResultsTo(results -> {
                                    final var tcInitcode = (byte[]) results[0];
                                    testContractInitcode.set(tcInitcode);
                                    LOG.info(CONTRACT_REPORTED_LOG_MESSAGE, tcInitcode.length);
                                })
                                .payingWith(GENESIS)
                                .nodePayment(ONE_HBAR)),
                        sourcing(() -> setExpectedCreate2Address(
                                contract, salt, expectedCreate2Address, testContractInitcode)),
                        // Now create a hollow account at the desired address
                        lazyCreateAccount(creation, expectedCreate2Address, ftId, nftId, partyAlias),
                        getTxnRecord(creation)
                                .andAllChildRecords()
                                .logged()
                                .exposingCreationsTo(l -> hollowCreationAddress.set(l.get(0))),

                        // Transfer HBAR to the hollow account address to create it
                        cryptoTransfer(
                                movingHbar(ONE_HBAR)
                                        .between(RELAYER, hollowCreationAddress.get())
                        ).via("hbarTransferTx")

                ).then(
                        // Verify the HBAR transfer is successful
                        getTxnRecord("hbarTransferTx")
                                .hasPriority(
                                        TransactionRecordAsserts.recordWith().status(SUCCESS)
                                ),
                        // Verify the properties of the hollow account
                        getAccountInfo("hollowAccount")
                                .logged()
                                .has(accountWith()
                                        .maxAutoAssociations(-1))

                );
    }


    @SuppressWarnings("java:S5669")
    public static HapiContractCallLocal setExpectedCreate2Address(
            String contract,
            BigInteger salt,
            AtomicReference<String> expectedCreate2Address,
            AtomicReference<byte[]> testContractInitcode) {
        return contractCallLocal(contract, GET_ADDRESS, testContractInitcode.get(), salt)
                .exposingTypedResultsTo(results -> {
                    LOG.info(CONTRACT_REPORTED_ADDRESS_MESSAGE, results);
                    final var expectedAddrBytes = (Address) results[0];
                    final var hexedAddress = hex(
                            Bytes.fromHexString(expectedAddrBytes.toString()).toArray());
                    LOG.info(EXPECTED_CREATE2_ADDRESS_MESSAGE, hexedAddress);
                    expectedCreate2Address.set(hexedAddress);
                })
                .payingWith(GENESIS);
    }

    private HapiCryptoTransfer lazyCreateAccount(
            String creation,
            AtomicReference<String> expectedCreate2Address,
            AtomicReference<TokenID> ftId,
            AtomicReference<TokenID> nftId,
            AtomicReference<ByteString> partyAlias) {
        return cryptoTransfer((spec, b) -> {
                    final var defaultPayerId = spec.registry().getAccountID(DEFAULT_PAYER);
                    b.setTransfers(TransferList.newBuilder()
                                    .addAccountAmounts(aaWith(
                                            ByteString.copyFrom(CommonUtils.unhex(expectedCreate2Address.get())),
                                            +ONE_HBAR))
                                    .addAccountAmounts(aaWith(defaultPayerId, -ONE_HBAR)))
                            .addTokenTransfers(TokenTransferList.newBuilder()
                                    .setToken(ftId.get())
                                    .addTransfers(aaWith(partyAlias.get(), -500))
                                    .addTransfers(aaWith(
                                            ByteString.copyFrom(CommonUtils.unhex(expectedCreate2Address.get())),
                                            +500)))
                            .addTokenTransfers(TokenTransferList.newBuilder()
                                    .setToken(nftId.get())
                                    .addNftTransfers(ocWith(
                                            accountId(partyAlias.get()),
                                            accountId(ByteString.copyFrom(
                                                    CommonUtils.unhex(expectedCreate2Address.get()))),
                                            1L)));
                })
                .signedBy(DEFAULT_PAYER, PARTY)
                .fee(ONE_HBAR)
                .via(creation);
    }

    private HapiContractCallLocal assertCreate2Address(
            String contract,
            BigInteger salt,
            AtomicReference<String> expectedCreate2Address,
            AtomicReference<byte[]> testContractInitcode) {
        return contractCallLocal(contract, GET_ADDRESS, testContractInitcode.get(), salt)
                .exposingTypedResultsTo(results -> {
                    LOG.info(CONTRACT_REPORTED_ADDRESS_MESSAGE, results);
                    final var addrBytes = (Address) results[0];
                    final var hexedAddress =
                            hex(Bytes.fromHexString(addrBytes.toString()).toArray());
                    LOG.info(EXPECTED_CREATE2_ADDRESS_MESSAGE, hexedAddress);

                    assertEquals(expectedCreate2Address.get(), hexedAddress);
                })
                .payingWith(GENESIS);
    }

    private CustomSpecAssert setIdentifiers(
            AtomicReference<TokenID> ftId,
            AtomicReference<TokenID> nftId,
            AtomicReference<AccountID> partyId,
            AtomicReference<ByteString> partyAlias) {
        return withOpContext((spec, opLog) -> {
            final var registry = spec.registry();
            ftId.set(registry.getTokenID(A_TOKEN));
            nftId.set(registry.getTokenID(NFT_INFINITE_SUPPLY_TOKEN));
            partyId.set(registry.getAccountID(PARTY));
            partyAlias.set(ByteString.copyFrom(asSolidityAddress(partyId.get())));
        });
    }

    private String asLiteralHexed(final Address address) {
        return address.toString().substring(2);
    }
}
