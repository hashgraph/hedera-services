/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.accountIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContractString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.contractIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.literalIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocalWithFunctionAbi;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedContractBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.*;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.swirlds.common.utility.CommonUtils.hex;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.contracts.ParsingConstants.FunctionType;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Create2OperationSuite extends HapiApiSuite {

    private static final Logger LOG = LogManager.getLogger(Create2OperationSuite.class);
    private static final String CREATION = "creation";
    private static final String CONTRACTS_THROTTLE_THROTTLE_BY_GAS =
            "contracts.throttle.throttleByGas";
    private static final String FALSE = "false";
    private static final String GET_BYTECODE = "getBytecode";
    private static final String DEPLOY = "deploy";
    private static final String CREATE_2_TXN = "create2Txn";
    private static final String SWISS = "swiss";
    private static final String SALT =
            "aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011";
    private static final String RETURNER = "Returner";
    private static final String CALL_RETURNER = "callReturner";
    private static final String RETURNER_REPORTED_LOG_MESSAGE =
            "Returner reported {} when called with mirror address";
    private static final String CONTRACT_REPORTED_LOG_MESSAGE =
            "Contract reported TestContract initcode is {} bytes";

    public static void main(String... args) {
        new Create2OperationSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                create2FactoryWorksAsExpected(),
                canDeleteViaAlias(),
                cannotSelfDestructToMirrorAddress(),
                priorityAddressIsCreate2ForStaticHapiCalls(),
                canInternallyCallAliasedAddressesOnlyViaCreate2Address(),
                create2InputAddressIsStableWithTopLevelCallWhetherMirrorOrAliasIsUsed(),
                canUseAliasesInPrecompilesAndContractKeys(),
                inlineCreateCanFailSafely(),
                inlineCreate2CanFailSafely(),
                allLogOpcodesResolveExpectedContractId(),
                eip1014AliasIsPriorityInErcOwnerPrecompile(),
                canAssociateInConstructor(),
                childInheritanceOfAdminKeyAuthorizesParentAssociationInConstructor());
    }

    @SuppressWarnings("java:S5669")
    private HapiApiSpec allLogOpcodesResolveExpectedContractId() {
        final var contract = "OuterCreator";

        final AtomicLong outerCreatorNum = new AtomicLong();
        final var msg = new byte[] {(byte) 0xAB};
        final var noisyTxn = "noisyTxn";

        return defaultHapiSpec("AllLogOpcodesResolveExpectedContractId")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .payingWith(GENESIS)
                                .via(CREATION)
                                .exposingNumTo(outerCreatorNum::set))
                .when(contractCall(contract, "startChain", msg).gas(4_000_000).via(noisyTxn))
                .then(
                        sourcing(
                                () -> {
                                    final var idOfFirstThreeLogs =
                                            "0.0." + (outerCreatorNum.get() + 1);
                                    final var idOfLastTwoLogs =
                                            "0.0." + (outerCreatorNum.get() + 2);
                                    return getTxnRecord(noisyTxn)
                                            .andAllChildRecords()
                                            .hasPriority(
                                                    recordWith()
                                                            .contractCallResult(
                                                                    resultWith()
                                                                            .logs(
                                                                                    inOrder(
                                                                                            logWith()
                                                                                                    .contract(
                                                                                                            idOfFirstThreeLogs),
                                                                                            logWith()
                                                                                                    .contract(
                                                                                                            idOfFirstThreeLogs),
                                                                                            logWith()
                                                                                                    .contract(
                                                                                                            idOfFirstThreeLogs),
                                                                                            logWith()
                                                                                                    .contract(
                                                                                                            idOfLastTwoLogs),
                                                                                            logWith()
                                                                                                    .contract(
                                                                                                            idOfLastTwoLogs)))))
                                            .logged();
                                }));
    }

    // https://github.com/hashgraph/hedera-services/issues/2868
    private HapiApiSpec inlineCreate2CanFailSafely() {
        final var tcValue = 1_234L;
        final var contract = "RevertingCreateFactory";
        final var foo = 22;
        final var salt = 23;
        final var timesToFail = 7;
        final AtomicLong factoryEntityNum = new AtomicLong();
        final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
        final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();

        return defaultHapiSpec("InlineCreate2CanFailSafely")
                .given(
                        overriding(CONTRACTS_THROTTLE_THROTTLE_BY_GAS, FALSE),
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .payingWith(GENESIS)
                                .via(CREATION)
                                .exposingNumTo(
                                        num -> {
                                            factoryEntityNum.set(num);
                                            factoryEvmAddress.set(
                                                    asHexedSolidityAddress(0, 0, num));
                                        }))
                .when(
                        sourcing(
                                () ->
                                        contractCallLocal(
                                                        contract,
                                                        GET_BYTECODE,
                                                        factoryEvmAddress.get(),
                                                        foo)
                                                .exposingTypedResultsTo(
                                                        results -> {
                                                            final var tcInitcode =
                                                                    (byte[]) results[0];
                                                            testContractInitcode.set(tcInitcode);
                                                            LOG.info(
                                                                    CONTRACT_REPORTED_LOG_MESSAGE,
                                                                    tcInitcode.length);
                                                        })
                                                .payingWith(GENESIS)
                                                .nodePayment(ONE_HBAR)))
                .then(
                        inParallel(
                                IntStream.range(0, timesToFail)
                                        .mapToObj(
                                                i ->
                                                        sourcing(
                                                                () ->
                                                                        contractCall(
                                                                                        contract,
                                                                                        DEPLOY,
                                                                                        testContractInitcode
                                                                                                .get(),
                                                                                        salt)
                                                                                .payingWith(GENESIS)
                                                                                .gas(4_000_000L)
                                                                                .sending(tcValue)
                                                                                .via(CREATION)))
                                        .toArray(HapiSpecOperation[]::new)),
                        sourcing(
                                () ->
                                        cryptoCreate("nextUp")
                                                .exposingCreatedIdTo(
                                                        id ->
                                                                LOG.info(
                                                                        "Next entity num was {}"
                                                                            + " instead of expected"
                                                                            + " {}",
                                                                        id.getAccountNum(),
                                                                        factoryEntityNum.get()
                                                                                + 1))));
    }

    @SuppressWarnings("java:S5669")
    private HapiApiSpec inlineCreateCanFailSafely() {
        final var tcValue = 1_234L;
        final var creation = CREATION;
        final var contract = "RevertingCreateFactory";

        final var foo = 22;
        final var timesToFail = 7;
        final AtomicLong factoryEntityNum = new AtomicLong();
        final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
        final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();

        return defaultHapiSpec("InlineCreateCanFailSafely")
                .given(
                        overriding(CONTRACTS_THROTTLE_THROTTLE_BY_GAS, FALSE),
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .payingWith(GENESIS)
                                .via(creation)
                                .exposingNumTo(
                                        num -> {
                                            factoryEntityNum.set(num);
                                            factoryEvmAddress.set(
                                                    asHexedSolidityAddress(0, 0, num));
                                        }))
                .when(
                        sourcing(
                                () ->
                                        contractCallLocal(
                                                        contract,
                                                        GET_BYTECODE,
                                                        factoryEvmAddress.get(),
                                                        foo)
                                                .exposingTypedResultsTo(
                                                        results -> {
                                                            final var tcInitcode =
                                                                    (byte[]) results[0];
                                                            testContractInitcode.set(tcInitcode);
                                                            LOG.info(
                                                                    "Contract reported TestContract"
                                                                        + " initcode is {} bytes",
                                                                    tcInitcode.length);
                                                        })
                                                .payingWith(GENESIS)
                                                .nodePayment(ONE_HBAR)))
                .then(
                        inParallel(
                                IntStream.range(0, timesToFail)
                                        .mapToObj(
                                                i ->
                                                        sourcing(
                                                                () ->
                                                                        contractCall(
                                                                                        contract,
                                                                                        DEPLOY,
                                                                                        testContractInitcode
                                                                                                .get())
                                                                                .payingWith(GENESIS)
                                                                                .gas(4_000_000L)
                                                                                .sending(tcValue)
                                                                                .via(creation)))
                                        .toArray(HapiSpecOperation[]::new)),
                        sourcing(
                                () ->
                                        cryptoCreate("nextUp")
                                                .exposingCreatedIdTo(
                                                        id ->
                                                                LOG.info(
                                                                        "Next entity num was {}"
                                                                            + " instead of expected"
                                                                            + " {}",
                                                                        id.getAccountNum(),
                                                                        factoryEntityNum.get()
                                                                                + 1))));
    }

    private HapiApiSpec canAssociateInConstructor() {
        final var token = "token";
        final var contract = "SelfAssociating";
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("CanAssociateInConstructor")
                .given(
                        uploadInitCode(contract),
                        tokenCreate(token)
                                .exposingCreatedIdTo(
                                        id ->
                                                tokenMirrorAddr.set(
                                                        hex(
                                                                asAddress(
                                                                        HapiPropertySource.asToken(
                                                                                id))))))
                .when(
                        sourcing(
                                () ->
                                        contractCreate(contract, tokenMirrorAddr.get())
                                                .payingWith(GENESIS)
                                                .omitAdminKey()
                                                .gas(4_000_000)
                                                .via(CREATION)))
                .then(
                        //						tokenDissociate(contract, token)
                        getContractInfo(contract).logged());
    }

    // https://github.com/hashgraph/hedera-services/issues/2867
    // https://github.com/hashgraph/hedera-services/issues/2868
    @SuppressWarnings("java:S5960")
    private HapiApiSpec create2FactoryWorksAsExpected() {
        final var tcValue = 1_234L;
        final var contract = "Create2Factory";
        final var testContract = "TestContract";
        final var salt = 42;
        final var adminKey = "adminKey";
        final var replAdminKey = "replAdminKey";
        final var entityMemo = "JUST DO IT";
        final var customAutoRenew = 7776001L;
        final var autoRenewAccountID = "autoRenewAccount";
        final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
        final AtomicReference<String> expectedCreate2Address = new AtomicReference<>();
        final AtomicReference<String> expectedMirrorAddress = new AtomicReference<>();
        final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();
        final AtomicReference<byte[]> bytecodeFromMirror = new AtomicReference<>();
        final AtomicReference<byte[]> bytecodeFromAlias = new AtomicReference<>();
        final AtomicReference<String> mirrorLiteralId = new AtomicReference<>();

        return defaultHapiSpec("Create2FactoryWorksAsExpected")
                .given(
                        overridingAllOf(
                                Map.of(
                                        "staking.fees.nodeRewardPercentage", "10",
                                        "staking.fees.stakingRewardPercentage", "10",
                                        "staking.isEnabled", "true",
                                        "staking.maxDailyStakeRewardThPerH", "100",
                                        "staking.rewardRate", "100_000_000_000",
                                        "staking.startThreshold", "100_000_000")),
                        newKeyNamed(adminKey),
                        newKeyNamed(replAdminKey),
                        overriding(CONTRACTS_THROTTLE_THROTTLE_BY_GAS, FALSE),
                        uploadInitCode(contract),
                        cryptoCreate(autoRenewAccountID).balance(ONE_HUNDRED_HBARS),
                        contractCreate(contract)
                                .payingWith(GENESIS)
                                .adminKey(adminKey)
                                .entityMemo(entityMemo)
                                .autoRenewSecs(customAutoRenew)
                                .autoRenewAccountId(autoRenewAccountID)
                                .via(CREATE_2_TXN)
                                .exposingNumTo(
                                        num ->
                                                factoryEvmAddress.set(
                                                        asHexedSolidityAddress(0, 0, num))),
                        getContractInfo(contract)
                                .has(contractWith().autoRenewAccountId(autoRenewAccountID))
                                .logged())
                .when(
                        sourcing(
                                () ->
                                        contractCallLocal(
                                                        contract,
                                                        GET_BYTECODE,
                                                        factoryEvmAddress.get(),
                                                        salt)
                                                .exposingTypedResultsTo(
                                                        results -> {
                                                            final var tcInitcode =
                                                                    (byte[]) results[0];
                                                            testContractInitcode.set(tcInitcode);
                                                            LOG.info(
                                                                    "Contract reported TestContract"
                                                                        + " initcode is {} bytes",
                                                                    tcInitcode.length);
                                                        })
                                                .payingWith(GENESIS)
                                                .nodePayment(ONE_HBAR)),
                        sourcing(
                                () ->
                                        contractCallLocal(
                                                        contract,
                                                        "getAddress",
                                                        testContractInitcode.get(),
                                                        salt)
                                                .exposingTypedResultsTo(
                                                        results -> {
                                                            LOG.info(
                                                                    "Contract reported address"
                                                                            + " results {}",
                                                                    results);
                                                            final var expectedAddrBytes =
                                                                    (byte[]) results[0];
                                                            final var hexedAddress =
                                                                    hex(expectedAddrBytes);
                                                            LOG.info(
                                                                    "  --> Expected CREATE2 address"
                                                                            + " is {}",
                                                                    hexedAddress);
                                                            expectedCreate2Address.set(
                                                                    hexedAddress);
                                                        })
                                                .payingWith(GENESIS)),
                        // First check the feature toggle
                        overriding("contracts.allowCreate2", FALSE),
                        sourcing(
                                () ->
                                        contractCall(
                                                        contract,
                                                        DEPLOY,
                                                        testContractInitcode.get(),
                                                        salt)
                                                .payingWith(GENESIS)
                                                .gas(4_000_000L)
                                                .sending(tcValue)
                                                .via(CREATE_2_TXN)
                                                .hasKnownStatus(CONTRACT_EXECUTION_EXCEPTION)),
                        // Now re-enable CREATE2 and proceed
                        overriding("contracts.allowCreate2", "true"),
                        // https://github.com/hashgraph/hedera-services/issues/2867 - cannot
                        // re-create same address
                        sourcing(
                                () ->
                                        contractCall(
                                                        contract,
                                                        "wronglyDeployTwice",
                                                        testContractInitcode.get(),
                                                        salt)
                                                .payingWith(GENESIS)
                                                .gas(4_000_000L)
                                                .sending(tcValue)
                                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS)),
                        sourcing(
                                () ->
                                        getContractInfo(expectedCreate2Address.get())
                                                .hasCostAnswerPrecheck(INVALID_CONTRACT_ID)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        contract,
                                                        DEPLOY,
                                                        testContractInitcode.get(),
                                                        salt)
                                                .payingWith(GENESIS)
                                                .gas(4_000_000L)
                                                .sending(tcValue)),
                        sourcing(
                                () ->
                                        contractDelete(expectedCreate2Address.get())
                                                .signedBy(DEFAULT_PAYER, adminKey)),
                        logIt("Deleted the deployed CREATE2 contract using HAPI"),
                        sourcing(
                                () ->
                                        contractCall(
                                                        contract,
                                                        DEPLOY,
                                                        testContractInitcode.get(),
                                                        salt)
                                                .payingWith(GENESIS)
                                                .gas(4_000_000L)
                                                .sending(tcValue)
                                                .via(CREATE_2_TXN)),
                        logIt("Re-deployed the CREATE2 contract"),
                        sourcing(
                                () ->
                                        childRecordsCheck(
                                                CREATE_2_TXN,
                                                SUCCESS,
                                                recordWith()
                                                        .contractCreateResult(
                                                                resultWith()
                                                                        .hexedEvmAddress(
                                                                                expectedCreate2Address
                                                                                        .get()))
                                                        .status(SUCCESS))),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var parentId = spec.registry().getContractId(contract);
                                    final var childId =
                                            ContractID.newBuilder()
                                                    .setContractNum(parentId.getContractNum() + 2L)
                                                    .build();
                                    mirrorLiteralId.set("0.0." + childId.getContractNum());
                                    expectedMirrorAddress.set(hex(asSolidityAddress(childId)));
                                }),
                        sourcing(
                                () ->
                                        getContractBytecode(mirrorLiteralId.get())
                                                .exposingBytecodeTo(bytecodeFromMirror::set)),
                        // https://github.com/hashgraph/hedera-services/issues/2874
                        sourcing(
                                () ->
                                        getContractBytecode(expectedCreate2Address.get())
                                                .exposingBytecodeTo(bytecodeFromAlias::set)),
                        withOpContext(
                                (spec, opLog) ->
                                        assertArrayEquals(
                                                bytecodeFromAlias.get(),
                                                bytecodeFromMirror.get(),
                                                "Bytecode should be get-able using alias")),
                        sourcing(
                                () ->
                                        contractUpdate(expectedCreate2Address.get())
                                                .signedBy(DEFAULT_PAYER, adminKey, replAdminKey)
                                                .newKey(replAdminKey)))
                .then(
                        sourcing(
                                () ->
                                        contractCall(
                                                        contract,
                                                        DEPLOY,
                                                        testContractInitcode.get(),
                                                        salt)
                                                .payingWith(GENESIS)
                                                .gas(4_000_000L)
                                                /* Cannot repeat CREATE2 with same args without destroying the existing contract */
                                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS)),
                        // https://github.com/hashgraph/hedera-services/issues/2874
                        // autoRenewAccountID is inherited from the sender
                        sourcing(
                                () ->
                                        getContractInfo(expectedCreate2Address.get())
                                                .has(
                                                        contractWith()
                                                                .addressOrAlias(
                                                                        expectedCreate2Address
                                                                                .get())
                                                                .autoRenewAccountId(
                                                                        autoRenewAccountID))
                                                .logged()),
                        sourcing(
                                () ->
                                        contractCallLocalWithFunctionAbi(
                                                        expectedCreate2Address.get(),
                                                        getABIFor(
                                                                FUNCTION,
                                                                "getBalance",
                                                                testContract))
                                                .payingWith(GENESIS)
                                                .has(
                                                        resultWith()
                                                                .resultThruAbi(
                                                                        getABIFor(
                                                                                FUNCTION,
                                                                                "getBalance",
                                                                                testContract),
                                                                        isLiteralResult(
                                                                                new Object[] {
                                                                                    BigInteger
                                                                                            .valueOf(
                                                                                                    tcValue)
                                                                                })))),
                        // autoRenewAccountID is inherited from the sender
                        sourcing(
                                () ->
                                        getContractInfo(expectedMirrorAddress.get())
                                                .has(
                                                        contractWith()
                                                                .adminKey(replAdminKey)
                                                                .addressOrAlias(
                                                                        expectedCreate2Address
                                                                                .get())
                                                                .autoRenewAccountId(
                                                                        autoRenewAccountID))
                                                .logged()),
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        expectedCreate2Address.get(),
                                                        getABIFor(
                                                                FUNCTION,
                                                                "vacateAddress",
                                                                testContract))
                                                .payingWith(GENESIS)),
                        sourcing(
                                () ->
                                        getContractInfo(expectedCreate2Address.get())
                                                .hasCostAnswerPrecheck(INVALID_CONTRACT_ID)));
    }

    @SuppressWarnings("java:S5669")
    private HapiApiSpec eip1014AliasIsPriorityInErcOwnerPrecompile() {
        final var ercContract = "ERC721Contract";
        final var pc2User = "Create2PrecompileUser";
        final var nft = "nonFungibleToken";
        final var lookup = "ownerOfPrecompile";

        final AtomicReference<String> userAliasAddr = new AtomicReference<>();
        final AtomicReference<String> userMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> userLiteralId = new AtomicReference<>();
        final AtomicReference<byte[]> nftAddress = new AtomicReference<>();

        final byte[] salt = unhex(SALT);

        return defaultHapiSpec("Eip1014AliasIsPriorityInErcOwnerPrecompile")
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
                        captureOneChildCreate2MetaFor(
                                "Precompile user", CREATE_2_TXN, userMirrorAddr, userAliasAddr),
                        sourcing(
                                () ->
                                        getAliasedContractBalance(userAliasAddr.get())
                                                .hasId(
                                                        accountIdFromHexedMirrorAddress(
                                                                userMirrorAddr.get()))),
                        withOpContext(
                                (spec, opLog) ->
                                        userLiteralId.set(
                                                asContractString(
                                                        contractIdFromHexedMirrorAddress(
                                                                userMirrorAddr.get())))),
                        sourcing(
                                () ->
                                        tokenCreate(nft)
                                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                                .treasury(userLiteralId.get())
                                                .initialSupply(0L)
                                                .supplyKey(SWISS)
                                                .fee(ONE_HUNDRED_HBARS)
                                                .signedBy(DEFAULT_PAYER, SWISS)),
                        mintToken(nft, List.of(ByteString.copyFromUtf8("PRICELESS"))))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var nftType = spec.registry().getTokenID(nft);
                                    nftAddress.set(asSolidityAddress(nftType));
                                }),
                        sourcing(() -> getContractInfo(userLiteralId.get()).logged()),
                        sourcing(
                                () ->
                                        contractCall(ercContract, "ownerOf", nftAddress.get(), 1)
                                                .via(lookup)
                                                .gas(4_000_000)))
                .then(
                        sourcing(
                                () ->
                                        childRecordsCheck(
                                                lookup,
                                                SUCCESS,
                                                recordWith()
                                                        .status(SUCCESS)
                                                        .contractCallResult(
                                                                resultWith()
                                                                        .contractCallResult(
                                                                                htsPrecompileResult()
                                                                                        .forFunction(
                                                                                                FunctionType
                                                                                                        .ERC_OWNER)
                                                                                        .withOwner(
                                                                                                unhex(
                                                                                                        userAliasAddr
                                                                                                                .get())))))));
    }

    private HapiApiSpec childInheritanceOfAdminKeyAuthorizesParentAssociationInConstructor() {
        final var ft = "fungibleToken";
        final var multiKey = SWISS;
        final var creationAndAssociation = "creationAndAssociation";
        final var immediateChildAssoc = "ImmediateChildAssociation";

        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> childMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("childInheritanceOfAdminKeyAuthorizesParentAssociationInConstructor")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(ft)
                                .exposingCreatedIdTo(
                                        id ->
                                                tokenMirrorAddr.set(
                                                        hex(
                                                                asSolidityAddress(
                                                                        HapiPropertySource.asToken(
                                                                                id))))))
                .when(
                        uploadInitCode(immediateChildAssoc),
                        sourcing(
                                () ->
                                        contractCreate(immediateChildAssoc, tokenMirrorAddr.get())
                                                .gas(2_000_000)
                                                .adminKey(multiKey)
                                                .payingWith(GENESIS)
                                                .exposingNumTo(
                                                        n -> childMirrorAddr.set("0.0." + (n + 1)))
                                                .via(creationAndAssociation)))
                .then(sourcing(() -> getContractInfo(childMirrorAddr.get()).logged()));
    }

    @SuppressWarnings("java:S5669")
    private HapiApiSpec canUseAliasesInPrecompilesAndContractKeys() {
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

        return defaultHapiSpec("CanUseAliasesInPrecompiles")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(TOKEN_TREASURY),
                        uploadInitCode(contract),
                        contractCreate(contract).omitAdminKey().payingWith(GENESIS),
                        contractCall(contract, "createUser", salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .via(creation2),
                        captureOneChildCreate2MetaFor(
                                "Precompile user", creation2, userMirrorAddr, userAliasAddr),
                        withOpContext(
                                (spec, opLog) ->
                                        userLiteralId.set(
                                                asContractString(
                                                        contractIdFromHexedMirrorAddress(
                                                                userMirrorAddr.get())))),
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
                        tokenUpdate(nft).supplyKey(() -> aliasContractIdKey(userAliasAddr.get())))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var registry = spec.registry();
                                    final var ftType = registry.getTokenID(ft);
                                    final var nftType = registry.getTokenID(nft);

                                    final var ftAssoc =
                                            contractCall(
                                                            contract,
                                                            "associateBothTo",
                                                            hex(asSolidityAddress(ftType)))
                                                    .gas(4_000_000L);
                                    final var nftAssoc =
                                            contractCall(
                                                            contract,
                                                            "associateBothTo",
                                                            hex(asSolidityAddress(nftType)))
                                                    .gas(4_000_000L);

                                    final var fundingXfer =
                                            cryptoTransfer(
                                                    moving(100, ft)
                                                            .between(TOKEN_TREASURY, contract),
                                                    movingUnique(nft, 1L)
                                                            .between(TOKEN_TREASURY, contract));

                                    // https://github.com/hashgraph/hedera-services/issues/2874
                                    // (alias in transfer precompile)
                                    final var sendFt =
                                            contractCall(
                                                            contract,
                                                            "sendFtToUser",
                                                            hex(asSolidityAddress(ftType)),
                                                            100)
                                                    .gas(4_000_000L);
                                    final var sendNft =
                                            contractCall(
                                                            contract,
                                                            "sendNftToUser",
                                                            hex(asSolidityAddress(nftType)),
                                                            1)
                                                    .via(ftFail)
                                                    .gas(4_000_000L);
                                    final var failFtDissoc =
                                            contractCall(
                                                            contract,
                                                            "dissociateBothFrom",
                                                            hex(asSolidityAddress(ftType)))
                                                    .via(ftFail)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                    .gas(4_000_000L);
                                    final var failNftDissoc =
                                            contractCall(
                                                            contract,
                                                            "dissociateBothFrom",
                                                            hex(asSolidityAddress(nftType)))
                                                    .via(nftFail)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                    .gas(4_000_000L);
                                    // https://github.com/hashgraph/hedera-services/issues/2876
                                    // (mint via ContractID key)
                                    final var mint =
                                            contractCallWithFunctionAbi(
                                                            userAliasAddr.get(),
                                                            getABIFor(
                                                                    FUNCTION,
                                                                    "mintNft",
                                                                    userContract),
                                                            hex(asSolidityAddress(nftType)),
                                                            List.of("WoRtHlEsS"))
                                                    .gas(4_000_000L);
                                    /* Can't succeed yet because supply key isn't delegatable */
                                    hexedNftType.set(hex(asSolidityAddress(nftType)));
                                    final var helperMint =
                                            contractCallWithFunctionAbi(
                                                            userAliasAddr.get(),
                                                            getABIFor(
                                                                    FUNCTION,
                                                                    "mintNftViaDelegate",
                                                                    userContract),
                                                            hexedNftType.get(),
                                                            List.of("WoRtHlEsS"))
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
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .HAPI_MINT)
                                                                        .withTotalSupply(0)
                                                                        .withSerialNumbers()
                                                                        .withStatus(
                                                                                INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        childRecordsCheck(
                                ftFail,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(REVERTED_SUCCESS),
                                recordWith()
                                        .status(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)))),
                        childRecordsCheck(
                                nftFail,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(REVERTED_SUCCESS),
                                recordWith()
                                        .status(ACCOUNT_STILL_OWNS_NFTS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                ACCOUNT_STILL_OWNS_NFTS)))),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(nft, 1),

                        // https://github.com/hashgraph/hedera-services/issues/2876 (mint via
                        // delegatable_contract_id)
                        tokenUpdate(nft)
                                .supplyKey(() -> aliasDelegateContractKey(userAliasAddr.get())),
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        userAliasAddr.get(),
                                                        getABIFor(
                                                                FUNCTION,
                                                                "mintNftViaDelegate",
                                                                userContract),
                                                        hexedNftType.get(),
                                                        List.of("WoRtHlEsS...NOT"))
                                                .via(helperMintSuccess)
                                                .gas(4_000_000L)),
                        getTxnRecord(helperMintSuccess).andAllChildRecords().logged(),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(nft, 2),
                        cryptoTransfer(
                                        (spec, b) -> {
                                            final var registry = spec.registry();
                                            final var tt = registry.getAccountID(TOKEN_TREASURY);
                                            final var ftId = registry.getTokenID(ft);
                                            final var nftId = registry.getTokenID(nft);
                                            b.setTransfers(
                                                    TransferList.newBuilder()
                                                            .addAccountAmounts(aaWith(tt, -666))
                                                            .addAccountAmounts(
                                                                    aaWith(
                                                                            userMirrorAddr.get(),
                                                                            +666)));
                                            b.addTokenTransfers(
                                                            TokenTransferList.newBuilder()
                                                                    .setToken(ftId)
                                                                    .addTransfers(aaWith(tt, -6))
                                                                    .addTransfers(
                                                                            aaWith(
                                                                                    userMirrorAddr
                                                                                            .get(),
                                                                                    +6)))
                                                    .addTokenTransfers(
                                                            TokenTransferList.newBuilder()
                                                                    .setToken(nftId)
                                                                    .addNftTransfers(
                                                                            NftTransfer.newBuilder()
                                                                                    .setSerialNumber(
                                                                                            2L)
                                                                                    .setSenderAccountID(
                                                                                            tt)
                                                                                    .setReceiverAccountID(
                                                                                            accountId(
                                                                                                    userMirrorAddr
                                                                                                            .get()))));
                                        })
                                .signedBy(DEFAULT_PAYER, TOKEN_TREASURY),
                        sourcing(() -> getContractInfo(userLiteralId.get()).logged()));
    }

    // https://github.com/hashgraph/hedera-services/issues/2874
    // https://github.com/hashgraph/hedera-services/issues/2925
    @SuppressWarnings("java:S5669")
    private HapiApiSpec cannotSelfDestructToMirrorAddress() {
        final var creation2 = CREATE_2_TXN;
        final var messyCreation2 = "messyCreate2Txn";
        final var contract = "CreateDonor";
        final var donorContract = "Donor";

        final AtomicReference<String> donorAliasAddr = new AtomicReference<>();
        final AtomicReference<String> donorMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> mDonorAliasAddr = new AtomicReference<>();
        final AtomicReference<String> mDonorMirrorAddr = new AtomicReference<>();

        final var salt = unhex(SALT);
        final var otherSalt =
                unhex("aabbccddee880011aabbccddee880011aabbccddee880011aabbccddee880011");

        return defaultHapiSpec("CannotSelfDestructToMirrorAddress")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).payingWith(GENESIS),
                        contractCall(contract, "buildDonor", salt)
                                .sending(1_000)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .via(creation2),
                        captureOneChildCreate2MetaFor(
                                "donor", creation2, donorMirrorAddr, donorAliasAddr))
                .when(
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        donorAliasAddr.get(),
                                                        getABIFor(
                                                                FUNCTION,
                                                                "relinquishFundsTo",
                                                                donorContract),
                                                        donorAliasAddr.get())
                                                .hasKnownStatus(OBTAINER_SAME_CONTRACT_ID)),
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        donorAliasAddr.get(),
                                                        getABIFor(
                                                                FUNCTION,
                                                                "relinquishFundsTo",
                                                                donorContract),
                                                        donorMirrorAddr.get())
                                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS)))
                .then(
                        contractCall(contract, "buildThenRevertThenBuild", otherSalt)
                                .sending(1_000)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .via(messyCreation2),
                        captureOneChildCreate2MetaFor(
                                "questionableDonor",
                                messyCreation2,
                                mDonorMirrorAddr,
                                mDonorAliasAddr),
                        sourcing(
                                () ->
                                        getContractInfo(mDonorAliasAddr.get())
                                                .has(contractWith().balance(100))
                                                .logged()));
    }

    // https://github.com/hashgraph/hedera-services/issues/2874
    @SuppressWarnings("java:S5669")
    private HapiApiSpec canDeleteViaAlias() {
        final var adminKey = "adminKey";
        final var creation2 = CREATE_2_TXN;
        final var deletion = "deletion";
        final var contract = "SaltingCreatorFactory";
        final var saltingCreator = "SaltingCreator";

        final AtomicReference<String> saltingCreatorAliasAddr = new AtomicReference<>();
        final AtomicReference<String> saltingCreatorMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> saltingCreatorLiteralId = new AtomicReference<>();

        final var salt = unhex(SALT);
        final var otherSalt =
                unhex("aabbccddee330011aabbccddee330011aabbccddee330011aabbccddee330011");

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
                                "Salting creator",
                                creation2,
                                saltingCreatorMirrorAddr,
                                saltingCreatorAliasAddr),
                        withOpContext(
                                (spec, opLog) ->
                                        saltingCreatorLiteralId.set(
                                                asContractString(
                                                        contractIdFromHexedMirrorAddress(
                                                                saltingCreatorMirrorAddr.get())))),
                        // https://github.com/hashgraph/hedera-services/issues/2867 (can't
                        // re-create2 after selfdestruct)
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        saltingCreatorAliasAddr.get(),
                                                        getABIFor(
                                                                FUNCTION,
                                                                "createAndRecreateTest",
                                                                saltingCreator),
                                                        otherSalt)
                                                .payingWith(GENESIS)
                                                .gas(2_000_000L)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .when(
                        sourcing(
                                () ->
                                        contractUpdate(saltingCreatorAliasAddr.get())
                                                .signedBy(DEFAULT_PAYER, adminKey)
                                                .memo("That's why you always leave a note")),
                        sourcing(
                                () ->
                                        contractCallLocalWithFunctionAbi(
                                                        saltingCreatorAliasAddr.get(),
                                                        getABIFor(
                                                                FUNCTION,
                                                                "whatTheFoo",
                                                                saltingCreator))
                                                .has(
                                                        resultWith()
                                                                .resultThruAbi(
                                                                        getABIFor(
                                                                                FUNCTION,
                                                                                "whatTheFoo",
                                                                                saltingCreator),
                                                                        isLiteralResult(
                                                                                new Object[] {
                                                                                    BigInteger
                                                                                            .valueOf(
                                                                                                    42)
                                                                                })))),
                        sourcing(
                                () ->
                                        contractDelete(saltingCreatorAliasAddr.get())
                                                .signedBy(DEFAULT_PAYER, adminKey)
                                                .transferContract(saltingCreatorMirrorAddr.get())
                                                .hasKnownStatus(OBTAINER_SAME_CONTRACT_ID)),
                        sourcing(
                                () ->
                                        contractDelete(saltingCreatorMirrorAddr.get())
                                                .signedBy(DEFAULT_PAYER, adminKey)
                                                .transferContract(saltingCreatorAliasAddr.get())
                                                .hasKnownStatus(OBTAINER_SAME_CONTRACT_ID)))
                .then(
                        sourcing(
                                () ->
                                        getContractInfo(saltingCreatorMirrorAddr.get())
                                                .has(
                                                        contractWith()
                                                                .addressOrAlias(
                                                                        saltingCreatorAliasAddr
                                                                                .get()))),
                        sourcing(
                                () ->
                                        contractDelete(saltingCreatorAliasAddr.get())
                                                .signedBy(DEFAULT_PAYER, adminKey)
                                                .transferAccount(FUNDING)
                                                .via(deletion)),
                        sourcing(
                                () ->
                                        getTxnRecord(deletion)
                                                .hasPriority(
                                                        recordWith()
                                                                .targetedContractId(
                                                                        saltingCreatorLiteralId
                                                                                .get()))),
                        sourcing(
                                () ->
                                        contractDelete(saltingCreatorMirrorAddr.get())
                                                .signedBy(DEFAULT_PAYER, adminKey)
                                                .transferAccount(FUNDING)
                                                .hasPrecheck(CONTRACT_DELETED)),
                        sourcing(
                                () ->
                                        getContractInfo(saltingCreatorMirrorAddr.get())
                                                .has(
                                                        contractWith()
                                                                .addressOrAlias(
                                                                        saltingCreatorMirrorAddr
                                                                                .get()))));
    }

    @SuppressWarnings("java:S5669")
    private HapiApiSpec create2InputAddressIsStableWithTopLevelCallWhetherMirrorOrAliasIsUsed() {
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

        return defaultHapiSpec(
                        "Create2InputAddressIsStableWithTopLevelCallWhetherMirrorOrAliasIsUsed")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).payingWith(GENESIS),
                        contractCall(contract, "buildCreator", salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .via(creation2),
                        captureOneChildCreate2MetaFor(
                                "Salting creator",
                                creation2,
                                saltingCreatorMirrorAddr,
                                saltingCreatorAliasAddr))
                .when(
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        saltingCreatorAliasAddr.get(),
                                                        getABIFor(
                                                                FUNCTION,
                                                                "createSaltedTestContract",
                                                                saltingCreator),
                                                        salt)
                                                .payingWith(GENESIS)
                                                .gas(4_000_000L)
                                                .via(innerCreation2)),
                        sourcing(
                                () -> {
                                    final var emitterId =
                                            literalIdFromHexedMirrorAddress(
                                                    saltingCreatorMirrorAddr.get());
                                    return getTxnRecord(innerCreation2)
                                            .hasPriority(
                                                    recordWith()
                                                            .contractCallResult(
                                                                    resultWith()
                                                                            .contract(emitterId)
                                                                            .logs(
                                                                                    inOrder(
                                                                                            logWith()
                                                                                                    .contract(
                                                                                                            emitterId)))))
                                            .andAllChildRecords()
                                            .logged();
                                }),
                        captureOneChildCreate2MetaFor(
                                "Test contract create2'd via mirror address",
                                innerCreation2,
                                tcMirrorAddr1,
                                tcAliasAddr1),
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        tcAliasAddr1.get(),
                                                        getABIFor(
                                                                FUNCTION,
                                                                "vacateAddress",
                                                                "TestContract"))
                                                .payingWith(GENESIS)),
                        sourcing(
                                () ->
                                        getContractInfo(tcMirrorAddr1.get())
                                                .has(contractWith().isDeleted())))
                .then(
                        sourcing(
                                () ->
                                        contractCall(
                                                        contract,
                                                        "callCreator",
                                                        saltingCreatorAliasAddr.get(),
                                                        salt)
                                                .payingWith(GENESIS)
                                                .gas(4_000_000L)
                                                .via(delegateCreation2)),
                        captureOneChildCreate2MetaFor(
                                "Test contract create2'd via alias address",
                                delegateCreation2,
                                tcMirrorAddr2,
                                tcAliasAddr2),
                        withOpContext(
                                (spec, opLog) -> {
                                    assertNotEquals(
                                            tcMirrorAddr1.get(),
                                            tcMirrorAddr2.get(),
                                            "Mirror addresses must be different");
                                    assertEquals(
                                            tcAliasAddr1.get(),
                                            tcAliasAddr2.get(),
                                            "Alias addresses must be stable");
                                }));
    }

    @SuppressWarnings("java:S5669")
    private HapiApiSpec priorityAddressIsCreate2ForStaticHapiCalls() {
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
                        captureOneChildCreate2MetaFor(
                                RETURNER, CREATE_2_TXN, mirrorAddr, aliasAddr))
                .when(
                        sourcing(
                                () ->
                                        contractCallLocalWithFunctionAbi(
                                                        mirrorAddr.get(),
                                                        getABIFor(FUNCTION, "returnThis", RETURNER))
                                                .payingWith(GENESIS)
                                                .exposingTypedResultsTo(
                                                        results -> {
                                                            LOG.info(
                                                                    RETURNER_REPORTED_LOG_MESSAGE,
                                                                    results);
                                                            staticCallMirrorAns.set(
                                                                    (BigInteger) results[0]);
                                                        })),
                        sourcing(
                                () ->
                                        contractCallLocalWithFunctionAbi(
                                                        aliasAddr.get(),
                                                        getABIFor(FUNCTION, "returnThis", RETURNER))
                                                .payingWith(GENESIS)
                                                .exposingTypedResultsTo(
                                                        results -> {
                                                            LOG.info(
                                                                    "Returner reported {} when"
                                                                            + " called with alias"
                                                                            + " address",
                                                                    results);
                                                            staticCallAliasAns.set(
                                                                    (BigInteger) results[0]);
                                                        })))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    assertEquals(
                                            staticCallAliasAns.get(),
                                            staticCallMirrorAns.get(),
                                            "Static call with mirror address should be same as call"
                                                    + " with alias");
                                    assertTrue(
                                            aliasAddr
                                                    .get()
                                                    .endsWith(
                                                            staticCallAliasAns.get().toString(16)),
                                            "Alias should get priority over mirror address");
                                }),
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        aliasAddr.get(),
                                                        getABIFor(
                                                                FUNCTION,
                                                                "createPlaceholder",
                                                                RETURNER))
                                                .gas(4_000_000L)
                                                .payingWith(GENESIS)
                                                .via(CREATION)));
    }

    @SuppressWarnings("java:S5669")
    private HapiApiSpec canInternallyCallAliasedAddressesOnlyViaCreate2Address() {
        final var contract = "AddressValueRet";
        final var aliasCall = "aliasCall";
        final var mirrorCall = "mirrorCall";

        final AtomicReference<String> aliasAddr = new AtomicReference<>();
        final AtomicReference<String> mirrorAddr = new AtomicReference<>();
        final AtomicReference<BigInteger> staticCallAliasAns = new AtomicReference<>();
        final AtomicReference<BigInteger> staticCallMirrorAns = new AtomicReference<>();

        final var salt = unhex(SALT);

        return defaultHapiSpec("CanInternallyCallAliasedAddressesOnlyViaCreate2Address")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).payingWith(GENESIS),
                        contractCall(contract, "createReturner", salt)
                                .payingWith(GENESIS)
                                .gas(4_000_000L)
                                .via(CREATE_2_TXN),
                        captureOneChildCreate2MetaFor(
                                RETURNER, CREATE_2_TXN, mirrorAddr, aliasAddr))
                .when(
                        sourcing(
                                () ->
                                        contractCallLocal(contract, CALL_RETURNER, mirrorAddr.get())
                                                .hasAnswerOnlyPrecheck(INVALID_SOLIDITY_ADDRESS)
                                                .payingWith(GENESIS)
                                                .exposingTypedResultsTo(
                                                        results -> {
                                                            LOG.info(
                                                                    RETURNER_REPORTED_LOG_MESSAGE,
                                                                    results);
                                                            staticCallMirrorAns.set(
                                                                    (BigInteger) results[0]);
                                                        })),
                        sourcing(
                                () ->
                                        contractCallLocal(contract, CALL_RETURNER, aliasAddr.get())
                                                .payingWith(GENESIS)
                                                .exposingTypedResultsTo(
                                                        results -> {
                                                            LOG.info(
                                                                    "Returner reported {} when"
                                                                            + " called with alias"
                                                                            + " address",
                                                                    results);
                                                            staticCallAliasAns.set(
                                                                    (BigInteger) results[0]);
                                                        })),
                        sourcing(
                                () ->
                                        contractCall(contract, CALL_RETURNER, aliasAddr.get())
                                                .payingWith(GENESIS)
                                                .via(aliasCall)),
                        sourcing(
                                () ->
                                        contractCall(contract, CALL_RETURNER, mirrorAddr.get())
                                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS)
                                                .payingWith(GENESIS)
                                                .via(mirrorCall)))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var mirrorLookup = getTxnRecord(mirrorCall);
                                    allRunFor(spec, mirrorLookup);
                                    final var mirrorResult =
                                            mirrorLookup
                                                    .getResponseRecord()
                                                    .getContractCallResult()
                                                    .getContractCallResult();
                                    assertEquals(
                                            ByteString.EMPTY,
                                            mirrorResult,
                                            "Internal calls with mirror address should not be"
                                                    + " possible for aliased contracts");
                                }));
    }
}
