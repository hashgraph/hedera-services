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

package com.hedera.services.bdd.suites.contract.evm;

import static com.hedera.node.app.hapi.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.asAccountString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContract;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContractIdWithEvmAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.including;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAutoCreatedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONTRACT_CALL_RESULTS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.mirrorAddrWith;
import static com.hedera.services.bdd.suites.contract.Utils.nonMirrorAddrWith;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.updateSpecFor;
import static com.hedera.services.bdd.suites.utils.contracts.ErrorMessageResult.errorMessageResult;
import static com.hedera.services.bdd.suites.utils.contracts.SimpleBytesResult.bigIntResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
public class Evm46ValidationSuite {

    private static final long FIRST_NONEXISTENT_CONTRACT_NUM = 4303224382569680425L;
    private static final String NAME = "name";
    private static final String ERC_721_ABI = "ERC721ABI";
    private static final String NON_EXISTING_MIRROR_ADDRESS = "0000000000000000000000000000000000123456";
    private static final String NON_EXISTING_NON_MIRROR_ADDRESS = "1234561234561234561234561234568888123456";
    private static final String MAKE_CALLS_CONTRACT = "MakeCalls";
    private static final String INTERNAL_CALLER_CONTRACT = "InternalCaller";
    private static final String INTERNAL_CALLEE_CONTRACT = "InternalCallee";
    private static final String REVERT_WITH_REVERT_REASON_FUNCTION = "revertWithRevertReason";
    private static final String CALL_NON_EXISTING_FUNCTION = "callNonExisting";
    private static final String CALL_EXTERNAL_FUNCTION = "callExternalFunction";
    private static final String DELEGATE_CALL_EXTERNAL_FUNCTION = "delegateCallExternalFunction";
    private static final String STATIC_CALL_EXTERNAL_FUNCTION = "staticCallExternalFunction";
    private static final String CALL_REVERT_WITH_REVERT_REASON_FUNCTION = "callRevertWithRevertReason";
    private static final String TRANSFER_TO_FUNCTION = "transferTo";
    private static final String SEND_TO_FUNCTION = "sendTo";
    private static final String CALL_WITH_VALUE_TO_FUNCTION = "callWithValueTo";
    private static final String SELFDESTRUCT = "selfdestruct";
    private static final String INNER_TXN = "innerTx";
    private static final Long INTRINSIC_GAS_COST = 21000L;
    private static final Long GAS_LIMIT_FOR_CALL = 26000L;
    private static final Long EXTRA_GAS_FOR_FUNCTION_SELECTOR = 64L;
    private static final Long NOT_ENOUGH_GAS_LIMIT_FOR_CREATION = 500_000L;
    private static final Long ENOUGH_GAS_LIMIT_FOR_CREATION = 900_000L;
    private static final String RECEIVER = "receiver";
    private static final String ECDSA_KEY = "ecdsaKey";
    private static final String CUSTOM_PAYER = "customPayer";
    private static final String BENEFICIARY = "beneficiary";
    private static final String SIMPLE_UPDATE_CONTRACT = "SimpleUpdate";
    private static final String BALANCE_OF = "balanceOf";
    public static final List<Long> nonExistingSystemAccounts = List.of(351L, 352L, 353L, 354L, 355L, 356L, 357L, 358L);
    public static final List<Long> existingSystemAccounts = List.of(800L, 999L, 1000L);
    public static final List<Long> systemAccounts =
            List.of(0L, 1L, 9L, 10L, 358L, 359L, 360L, 361L, 750L, 751L, 799L, 800L, 999L, 1000L);
    public static final List<Long> callOperationsSuccessSystemAccounts = List.of(0L, 1L, 358L, 750L, 751L, 999L, 1000L);

    @HapiTest
    final Stream<DynamicTest> directCallToDeletedContractResultsInSuccessfulNoop() {
        AtomicReference<AccountID> receiverId = new AtomicReference<>();

        return defaultHapiSpec("directCallToDeletedContractResultsInSuccessfulNoop")
                .given(
                        cryptoCreate(RECEIVER).exposingCreatedIdTo(receiverId::set),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT)
                                // Refusing ethereum create conversion, because we get INVALID_SIGNATURE upon
                                // tokenAssociate,
                                // since we have CONTRACT_ID key
                                .refusingEthConversion()
                                .balance(ONE_HBAR),
                        contractDelete(INTERNAL_CALLER_CONTRACT))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        balanceSnapshot("initialBalance", asAccountString(receiverId.get())),
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        CALL_WITH_VALUE_TO_FUNCTION,
                                        mirrorAddrWith(receiverId.get().getAccountNum()))
                                .gas(GAS_LIMIT_FOR_CALL * 4)
                                .via(INNER_TXN))))
                .then(
                        getTxnRecord(INNER_TXN).hasPriority(recordWith().status(SUCCESS)),
                        getAccountBalance(RECEIVER).hasTinyBars(changeFromSnapshot("initialBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> selfdestructToExistingMirrorAddressResultsInSuccess() {
        AtomicReference<AccountID> receiverId = new AtomicReference<>();
        return defaultHapiSpec("selfdestructToExistingMirrorAddressResultsInSuccess")
                .given(
                        cryptoCreate(RECEIVER).exposingCreatedIdTo(receiverId::set),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> {
                    allRunFor(
                            spec,
                            balanceSnapshot("selfdestructTargetAccount", asAccountString(receiverId.get())),
                            contractCall(
                                            INTERNAL_CALLER_CONTRACT,
                                            SELFDESTRUCT,
                                            mirrorAddrWith(receiverId.get().getAccountNum()))
                                    .gas(GAS_LIMIT_FOR_CALL * 4)
                                    .via(INNER_TXN));
                }))
                .then(getAccountBalance(RECEIVER)
                        .hasTinyBars(changeFromSnapshot("selfdestructTargetAccount", 100000000)));
    }

    @HapiTest
    final Stream<DynamicTest> selfdestructToExistingNonMirrorAddressResultsInSuccess() {
        return defaultHapiSpec("selfdestructToExistingNonMirrorAddressResultsInSuccess")
                .given(
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, ECDSA_KEY, ONE_HUNDRED_HBARS)),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, ECDSA_KEY)),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    allRunFor(
                            spec,
                            balanceSnapshot("selfdestructTargetAccount", ECDSA_KEY)
                                    .accountIsAlias(),
                            contractCall(INTERNAL_CALLER_CONTRACT, SELFDESTRUCT, asHeadlongAddress(addressBytes))
                                    .gas(GAS_LIMIT_FOR_CALL * 4)
                                    .via(INNER_TXN));
                }))
                .then(getAccountBalance(ECDSA_KEY)
                        .hasTinyBars(changeFromSnapshot("selfdestructTargetAccount", 100000000)));
    }

    @HapiTest
    final Stream<DynamicTest> selfdestructToNonExistingNonMirrorAddressResultsInInvalidSolidityAddress() {
        AtomicReference<Bytes> nonExistingNonMirrorAddress = new AtomicReference<>();

        return defaultHapiSpec("selfdestructToNonExistingNonMirrorAddressResultsInInvalidSolidityAddress")
                .given(
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        withOpContext((spec, op) -> {
                            final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                            final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                            final var addressBytes = recoverAddressFromPubKey(tmp);
                            nonExistingNonMirrorAddress.set(Bytes.of(addressBytes));
                        }),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        SELFDESTRUCT,
                                        asHeadlongAddress(nonExistingNonMirrorAddress
                                                .get()
                                                .toArray()))
                                .gas(ENOUGH_GAS_LIMIT_FOR_CREATION)
                                .via(INNER_TXN)
                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS))))
                .then(getTxnRecord(INNER_TXN)
                        .hasPriority(recordWith()
                                .status(INVALID_SOLIDITY_ADDRESS)
                                .contractCallResult(resultWith().gasUsed(900000))));
    }

    @HapiTest
    final Stream<DynamicTest> selfdestructToNonExistingMirrorAddressResultsInInvalidSolidityAddress() {
        return defaultHapiSpec("selfdestructToNonExistingMirrorAddressResultsInInvalidSolidityAddress")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        SELFDESTRUCT,
                                        mirrorAddrWith(FIRST_NONEXISTENT_CONTRACT_NUM))
                                .gas(ENOUGH_GAS_LIMIT_FOR_CREATION)
                                .via(INNER_TXN)
                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS))))
                .then(getTxnRecord(INNER_TXN)
                        .hasPriority(recordWith()
                                .status(INVALID_SOLIDITY_ADDRESS)
                                .contractCallResult(resultWith().gasUsed(900000))));
    }

    @HapiTest
    final Stream<DynamicTest> directCallToNonExistingMirrorAddressResultsInSuccessfulNoOp() {

        return defaultHapiSpec("directCallToNonExistingMirrorAddressResultsInSuccessfulNoOp")
                .given(withOpContext((spec, ctxLog) -> spec.registry()
                        .saveContractId(
                                "nonExistingMirrorAddress",
                                asContractIdWithEvmAddress(ByteString.copyFrom(unhex(NON_EXISTING_MIRROR_ADDRESS))))))
                .when(withOpContext((spec, ctxLog) -> allRunFor(
                        spec,
                        contractCallWithFunctionAbi("nonExistingMirrorAddress", getABIFor(FUNCTION, NAME, ERC_721_ABI))
                                .gas(GAS_LIMIT_FOR_CALL)
                                .via("directCallToNonExistingMirrorAddress"),
                        // attempt call again, make sure the result is the same
                        contractCallWithFunctionAbi("nonExistingMirrorAddress", getABIFor(FUNCTION, NAME, ERC_721_ABI))
                                .gas(GAS_LIMIT_FOR_CALL)
                                .via("directCallToNonExistingMirrorAddress2"))))
                .then(
                        getTxnRecord("directCallToNonExistingMirrorAddress")
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .gasUsed(INTRINSIC_GAS_COST + EXTRA_GAS_FOR_FUNCTION_SELECTOR))),
                        getTxnRecord("directCallToNonExistingMirrorAddress2")
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .gasUsed(INTRINSIC_GAS_COST + EXTRA_GAS_FOR_FUNCTION_SELECTOR))),
                        getContractInfo("nonExistingMirrorAddress").hasCostAnswerPrecheck(INVALID_CONTRACT_ID));
    }

    @HapiTest
    final Stream<DynamicTest> directCallToNonExistingNonMirrorAddressResultsInSuccessfulNoOp() {

        return defaultHapiSpec("directCallToNonExistingNonMirrorAddressResultsInSuccessfulNoOp")
                .given(withOpContext((spec, ctxLog) -> spec.registry()
                        .saveContractId(
                                "nonExistingNonMirrorAddress",
                                asContractIdWithEvmAddress(
                                        ByteString.copyFrom(unhex(NON_EXISTING_NON_MIRROR_ADDRESS))))))
                .when(withOpContext((spec, ctxLog) -> allRunFor(
                        spec,
                        contractCallWithFunctionAbi(
                                        "nonExistingNonMirrorAddress", getABIFor(FUNCTION, NAME, ERC_721_ABI))
                                .gas(GAS_LIMIT_FOR_CALL)
                                .via("directCallToNonExistingNonMirrorAddress"),
                        // attempt call again, make sure the result is the same
                        contractCallWithFunctionAbi(
                                        "nonExistingNonMirrorAddress", getABIFor(FUNCTION, NAME, ERC_721_ABI))
                                .gas(GAS_LIMIT_FOR_CALL)
                                .via("directCallToNonExistingNonMirrorAddress2"))))
                .then(
                        getTxnRecord("directCallToNonExistingNonMirrorAddress")
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .gasUsed(INTRINSIC_GAS_COST + EXTRA_GAS_FOR_FUNCTION_SELECTOR))),
                        getTxnRecord("directCallToNonExistingNonMirrorAddress2")
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .gasUsed(INTRINSIC_GAS_COST + EXTRA_GAS_FOR_FUNCTION_SELECTOR))),
                        getContractInfo("nonExistingNonMirrorAddress").hasCostAnswerPrecheck(INVALID_CONTRACT_ID));
    }

    @HapiTest
    final Stream<DynamicTest> directCallToRevertingContractRevertsWithCorrectRevertReason() {

        return defaultHapiSpec("directCallToRevertingContractRevertsWithCorrectRevertReason")
                .given(uploadInitCode(INTERNAL_CALLEE_CONTRACT), contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(withOpContext((spec, ctxLog) -> allRunFor(
                        spec,
                        contractCall(INTERNAL_CALLEE_CONTRACT, REVERT_WITH_REVERT_REASON_FUNCTION)
                                .gas(GAS_LIMIT_FOR_CALL)
                                .via(INNER_TXN)
                                .hasKnownStatusFrom(CONTRACT_REVERT_EXECUTED))))
                .then(getTxnRecord(INNER_TXN)
                        .hasPriority(recordWith()
                                .status(CONTRACT_REVERT_EXECUTED)
                                .contractCallResult(resultWith()
                                        .gasUsed(21472)
                                        .error(errorMessageResult("RevertReason")
                                                .getBytes()
                                                .toString()))));
    }

    @HapiTest
    final Stream<DynamicTest> directCallToExistingCryptoAccountResultsInSuccess() {

        AtomicReference<AccountID> mirrorAccountID = new AtomicReference<>();

        return defaultHapiSpec("directCallToExistingCryptoAccountResultsInSuccess")
                .given(
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate("MirrorAccount")
                                .balance(ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(mirrorAccountID::set),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, ECDSA_KEY, ONE_HUNDRED_HBARS)),
                        withOpContext((spec, opLog) -> {
                            spec.registry()
                                    .saveContractId(
                                            "mirrorAddress",
                                            asContract("0.0."
                                                    + mirrorAccountID.get().getAccountNum()));
                            updateSpecFor(spec, ECDSA_KEY);
                            spec.registry()
                                    .saveContractId(
                                            "nonMirrorAddress",
                                            asContract("0.0."
                                                    + spec.registry()
                                                            .getAccountID(ECDSA_KEY)
                                                            .getAccountNum()));
                        }))
                .when(withOpContext((spec, ctxLog) -> allRunFor(
                        spec,
                        contractCallWithFunctionAbi("mirrorAddress", getABIFor(FUNCTION, NAME, ERC_721_ABI))
                                .gas(GAS_LIMIT_FOR_CALL)
                                .via("callToMirrorAddress"),
                        contractCallWithFunctionAbi("nonMirrorAddress", getABIFor(FUNCTION, NAME, ERC_721_ABI))
                                .gas(GAS_LIMIT_FOR_CALL)
                                .via("callToNonMirrorAddress"))))
                .then(
                        getTxnRecord("callToMirrorAddress")
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .gasUsed(INTRINSIC_GAS_COST + EXTRA_GAS_FOR_FUNCTION_SELECTOR))),
                        getTxnRecord("callToNonMirrorAddress")
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .gasUsed(INTRINSIC_GAS_COST + EXTRA_GAS_FOR_FUNCTION_SELECTOR))));
    }

    @HapiTest
    final Stream<DynamicTest> directCallWithValueToExistingCryptoAccountResultsInSuccess() {

        AtomicReference<AccountID> mirrorAccountID = new AtomicReference<>();

        return defaultHapiSpec("directCallWithValueToExistingCryptoAccountResultsInSuccess")
                .given(
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate("MirrorAccount")
                                .balance(ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(mirrorAccountID::set),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, ECDSA_KEY, ONE_HUNDRED_HBARS)),
                        withOpContext((spec, opLog) -> {
                            spec.registry()
                                    .saveContractId(
                                            "mirrorAddress",
                                            asContract("0.0."
                                                    + mirrorAccountID.get().getAccountNum()));
                            updateSpecFor(spec, ECDSA_KEY);
                            final var ecdsaKey = spec.registry()
                                    .getKey(ECDSA_KEY)
                                    .getECDSASecp256K1()
                                    .toByteArray();
                            final var senderAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                            spec.registry()
                                    .saveContractId(
                                            "nonMirrorAddress",
                                            ContractID.newBuilder()
                                                    .setEvmAddress(senderAddress)
                                                    .build());
                            spec.registry()
                                    .saveAccountId(
                                            "NonMirrorAccount",
                                            AccountID.newBuilder()
                                                    .setAccountNum(spec.registry()
                                                            .getAccountID(ECDSA_KEY)
                                                            .getAccountNum())
                                                    .build());
                        }))
                .when(withOpContext((spec, ctxLog) -> allRunFor(
                        spec,
                        balanceSnapshot("mirrorSnapshot", "MirrorAccount"),
                        balanceSnapshot("nonMirrorSnapshot", "NonMirrorAccount"),
                        contractCallWithFunctionAbi("mirrorAddress", getABIFor(FUNCTION, NAME, ERC_721_ABI))
                                .gas(GAS_LIMIT_FOR_CALL)
                                .sending(ONE_HBAR)
                                .via("callToMirrorAddress"),
                        contractCallWithFunctionAbi("nonMirrorAddress", getABIFor(FUNCTION, NAME, ERC_721_ABI))
                                .sending(ONE_HBAR)
                                .gas(GAS_LIMIT_FOR_CALL)
                                .via("callToNonMirrorAddress"))))
                .then(
                        getTxnRecord("callToMirrorAddress")
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .gasUsed(INTRINSIC_GAS_COST + EXTRA_GAS_FOR_FUNCTION_SELECTOR))),
                        getTxnRecord("callToNonMirrorAddress")
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .gasUsed(INTRINSIC_GAS_COST + EXTRA_GAS_FOR_FUNCTION_SELECTOR))),
                        getAccountBalance("MirrorAccount").hasTinyBars(changeFromSnapshot("mirrorSnapshot", ONE_HBAR)),
                        getAccountBalance("NonMirrorAccount")
                                .hasTinyBars(changeFromSnapshot("nonMirrorSnapshot", ONE_HBAR)));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallToNonExistingMirrorAddressResultsInNoopSuccess() {

        return defaultHapiSpec("internalCallToNonExistingMirrorAddressResultsInNoopSuccess")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(contractCall(
                                INTERNAL_CALLER_CONTRACT,
                                CALL_NON_EXISTING_FUNCTION,
                                mirrorAddrWith(FIRST_NONEXISTENT_CONTRACT_NUM + 1))
                        .gas(GAS_LIMIT_FOR_CALL)
                        .via(INNER_TXN))
                .then(getTxnRecord(INNER_TXN)
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(
                                        resultWith().createdContractIdsCount(0).gasUsed(24972))));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallToExistingMirrorAddressResultsInSuccessfulCall() {

        final AtomicLong calleeNum = new AtomicLong();

        return defaultHapiSpec("internalCallToExistingMirrorAddressResultsInSuccessfulCall")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT, INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT)
                                .balance(ONE_HBAR)
                                // Adding refusingEthConversion() due to fee differences and not supported address type
                                .refusingEthConversion(),
                        contractCreate(INTERNAL_CALLEE_CONTRACT)
                                .exposingNumTo(calleeNum::set)
                                // Adding refusingEthConversion() due to fee differences and not supported address type
                                .refusingEthConversion())
                .when(withOpContext((spec, ignored) -> allRunFor(
                        spec,
                        contractCall(INTERNAL_CALLER_CONTRACT, CALL_EXTERNAL_FUNCTION, mirrorAddrWith(calleeNum.get()))
                                .gas(GAS_LIMIT_FOR_CALL * 2)
                                .via(INNER_TXN))))
                .then(getTxnRecord(INNER_TXN)
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .createdContractIdsCount(0)
                                        .contractCallResult(bigIntResult(1))
                                        .gasUsedModuloIntrinsicVariation(48107))));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallToNonExistingNonMirrorAddressResultsInNoopSuccess() {

        return defaultHapiSpec("internalCallToNonExistingNonMirrorAddressResultsInNoopSuccess")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(contractCall(
                                INTERNAL_CALLER_CONTRACT,
                                CALL_NON_EXISTING_FUNCTION,
                                nonMirrorAddrWith(FIRST_NONEXISTENT_CONTRACT_NUM + 2))
                        .gas(GAS_LIMIT_FOR_CALL)
                        .via(INNER_TXN))
                .then(getTxnRecord(INNER_TXN)
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(
                                        resultWith().createdContractIdsCount(0).gasUsed(25020))));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallToExistingRevertingResultsInSuccessfulTopLevelTxn() {

        final AtomicLong calleeNum = new AtomicLong();

        return defaultHapiSpec("internalCallToExistingRevertingWithoutMessageResultsInSuccessfulTopLevelTxn")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT, INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR),
                        contractCreate(INTERNAL_CALLEE_CONTRACT).exposingNumTo(calleeNum::set))
                .when(withOpContext((spec, ignored) -> allRunFor(
                        spec,
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        CALL_REVERT_WITH_REVERT_REASON_FUNCTION,
                                        mirrorAddrWith(calleeNum.get()))
                                .gas(GAS_LIMIT_FOR_CALL * 8)
                                .hasKnownStatus(SUCCESS)
                                .via(INNER_TXN))))
                .then(getTxnRecord(INNER_TXN).hasPriority(recordWith().status(SUCCESS)));
    }

    @HapiTest
    final Stream<DynamicTest> internalTransferToNonExistingMirrorAddressResultsInInvalidAliasKey() {
        return defaultHapiSpec("internalTransferToNonExistingMirrorAddressResultsInInvalidAliasKey")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(contractCall(
                                INTERNAL_CALLER_CONTRACT,
                                TRANSFER_TO_FUNCTION,
                                mirrorAddrWith(FIRST_NONEXISTENT_CONTRACT_NUM + 3))
                        .gas(GAS_LIMIT_FOR_CALL * 4)
                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))
                .then(getAccountBalance("0.0." + (FIRST_NONEXISTENT_CONTRACT_NUM + 3))
                        .nodePayment(ONE_HBAR)
                        .hasAnswerOnlyPrecheck(INVALID_ACCOUNT_ID));
    }

    @HapiTest
    final Stream<DynamicTest> internalTransferToExistingMirrorAddressResultsInSuccess() {

        AtomicReference<AccountID> receiverId = new AtomicReference<>();

        return defaultHapiSpec("internalTransferToExistingMirrorAddressResultsInSuccess")
                .given(
                        cryptoCreate(RECEIVER).exposingCreatedIdTo(receiverId::set),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        balanceSnapshot("initialBalance", asAccountString(receiverId.get())),
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        TRANSFER_TO_FUNCTION,
                                        mirrorAddrWith(receiverId.get().getAccountNum()))
                                .gas(GAS_LIMIT_FOR_CALL * 4)
                                .via(INNER_TXN))))
                .then(
                        getTxnRecord(INNER_TXN)
                                .hasPriority(recordWith()
                                        .transfers(including(tinyBarsFromTo(INTERNAL_CALLER_CONTRACT, RECEIVER, 1)))),
                        getAccountBalance(RECEIVER).hasTinyBars(changeFromSnapshot("initialBalance", 1)));
    }

    @HapiTest
    final Stream<DynamicTest> internalTransferToNonExistingNonMirrorAddressResultsInRevert() {
        return defaultHapiSpec("internalTransferToNonExistingNonMirrorAddressResultsInRevert")
                .given(
                        cryptoCreate(CUSTOM_PAYER).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(contractCall(
                                INTERNAL_CALLER_CONTRACT,
                                TRANSFER_TO_FUNCTION,
                                nonMirrorAddrWith(FIRST_NONEXISTENT_CONTRACT_NUM + 4))
                        .gas(GAS_LIMIT_FOR_CALL * 4)
                        .payingWith(CUSTOM_PAYER)
                        .via(INNER_TXN)
                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))
                .then(getTxnRecord(INNER_TXN).hasPriority(recordWith().status(CONTRACT_REVERT_EXECUTED)));
    }

    @HapiTest
    final Stream<DynamicTest> internalTransferToExistingNonMirrorAddressResultsInSuccess() {

        return defaultHapiSpec("internalTransferToExistingNonMirrorAddressResultsInSuccess")
                .given(
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, ECDSA_KEY, ONE_HUNDRED_HBARS)),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, ECDSA_KEY)),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    allRunFor(
                            spec,
                            balanceSnapshot("autoCreatedSnapshot", ECDSA_KEY).accountIsAlias(),
                            contractCall(
                                            INTERNAL_CALLER_CONTRACT,
                                            TRANSFER_TO_FUNCTION,
                                            asHeadlongAddress(addressBytes))
                                    .gas(GAS_LIMIT_FOR_CALL * 4)
                                    .via(INNER_TXN));
                }))
                .then(
                        getTxnRecord(INNER_TXN)
                                .hasPriority(recordWith()
                                        .transfers(including(tinyBarsFromTo(INTERNAL_CALLER_CONTRACT, ECDSA_KEY, 1)))),
                        getAutoCreatedAccountBalance(ECDSA_KEY)
                                .hasTinyBars(changeFromSnapshot("autoCreatedSnapshot", 1)));
    }

    @HapiTest
    final Stream<DynamicTest> internalSendToNonExistingMirrorAddressDoesNotLazyCreateIt() {
        return defaultHapiSpec("internalSendToNonExistingMirrorAddressDoesNotLazyCreateIt")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(contractCall(
                                INTERNAL_CALLER_CONTRACT,
                                SEND_TO_FUNCTION,
                                mirrorAddrWith(FIRST_NONEXISTENT_CONTRACT_NUM + 5))
                        .gas(GAS_LIMIT_FOR_CALL * 4)
                        .via(INNER_TXN))
                .then(getAccountBalance("0.0." + (FIRST_NONEXISTENT_CONTRACT_NUM + 5))
                        .nodePayment(ONE_HBAR)
                        .hasAnswerOnlyPrecheck(INVALID_ACCOUNT_ID));
    }

    @HapiTest
    final Stream<DynamicTest> internalSendToExistingMirrorAddressResultsInSuccess() {

        AtomicReference<AccountID> receiverId = new AtomicReference<>();

        return defaultHapiSpec("internalSendToExistingMirrorAddressResultsInSuccess")
                .given(
                        cryptoCreate(RECEIVER).exposingCreatedIdTo(receiverId::set),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        balanceSnapshot("initialBalance", asAccountString(receiverId.get())),
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        SEND_TO_FUNCTION,
                                        mirrorAddrWith(receiverId.get().getAccountNum()))
                                .gas(GAS_LIMIT_FOR_CALL * 4)
                                .via(INNER_TXN))))
                .then(
                        getTxnRecord(INNER_TXN)
                                .hasPriority(recordWith()
                                        .transfers(including(tinyBarsFromTo(INTERNAL_CALLER_CONTRACT, RECEIVER, 1)))),
                        getAccountBalance(RECEIVER).hasTinyBars(changeFromSnapshot("initialBalance", 1)));
    }

    @HapiTest
    final Stream<DynamicTest> internalSendToNonExistingNonMirrorAddressResultsInSuccess() {

        AtomicReference<Bytes> nonExistingNonMirrorAddress = new AtomicReference<>();

        return defaultHapiSpec("internalSendToNonExistingNonMirrorAddressResultsInSuccess")
                .given(
                        cryptoCreate(CUSTOM_PAYER).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        withOpContext((spec, op) -> {
                            final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                            final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                            final var addressBytes = recoverAddressFromPubKey(tmp);
                            nonExistingNonMirrorAddress.set(Bytes.of(addressBytes));
                        }),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        balanceSnapshot("contractBalance", INTERNAL_CALLER_CONTRACT),
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        SEND_TO_FUNCTION,
                                        asHeadlongAddress(nonExistingNonMirrorAddress
                                                .get()
                                                .toArray()))
                                .gas(GAS_LIMIT_FOR_CALL * 4)
                                .payingWith(CUSTOM_PAYER))))
                .then(
                        getAccountBalance(INTERNAL_CALLER_CONTRACT)
                                .hasTinyBars(changeFromSnapshot("contractBalance", 0)),
                        sourcing(() -> getAliasedAccountInfo(ByteString.copyFrom(
                                        nonExistingNonMirrorAddress.get().toArray()))
                                .hasCostAnswerPrecheck(INVALID_ACCOUNT_ID)));
    }

    @HapiTest
    final Stream<DynamicTest> internalSendToExistingNonMirrorAddressResultsInSuccess() {

        return defaultHapiSpec("internalSendToExistingNonMirrorAddressResultsInSuccess")
                .given(
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, ECDSA_KEY, ONE_HUNDRED_HBARS)),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, ECDSA_KEY)),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    allRunFor(
                            spec,
                            balanceSnapshot("autoCreatedSnapshot", ECDSA_KEY).accountIsAlias(),
                            contractCall(INTERNAL_CALLER_CONTRACT, SEND_TO_FUNCTION, asHeadlongAddress(addressBytes))
                                    .gas(GAS_LIMIT_FOR_CALL * 4)
                                    .via(INNER_TXN));
                }))
                .then(
                        getTxnRecord(INNER_TXN)
                                .hasPriority(recordWith()
                                        .transfers(including(tinyBarsFromTo(INTERNAL_CALLER_CONTRACT, ECDSA_KEY, 1)))),
                        getAutoCreatedAccountBalance(ECDSA_KEY)
                                .hasTinyBars(changeFromSnapshot("autoCreatedSnapshot", 1)));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallWithValueToNonExistingMirrorAddressResultsInInvalidAliasKey() {
        return defaultHapiSpec("internalCallWithValueToNonExistingMirrorAddressResultsInInvalidAliasKey")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(contractCall(
                                INTERNAL_CALLER_CONTRACT,
                                CALL_WITH_VALUE_TO_FUNCTION,
                                mirrorAddrWith(FIRST_NONEXISTENT_CONTRACT_NUM + 6))
                        .gas(ENOUGH_GAS_LIMIT_FOR_CREATION))
                .then(getAccountBalance("0.0." + (FIRST_NONEXISTENT_CONTRACT_NUM + 6))
                        .nodePayment(ONE_HBAR)
                        .hasAnswerOnlyPrecheck(INVALID_ACCOUNT_ID));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallWithValueToExistingMirrorAddressResultsInSuccess() {

        AtomicReference<AccountID> receiverId = new AtomicReference<>();

        return defaultHapiSpec("internalCallWithValueToExistingMirrorAddressResultsInSuccess")
                .given(
                        cryptoCreate(RECEIVER).exposingCreatedIdTo(receiverId::set),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        balanceSnapshot("initialBalance", asAccountString(receiverId.get())),
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        CALL_WITH_VALUE_TO_FUNCTION,
                                        mirrorAddrWith(receiverId.get().getAccountNum()))
                                .gas(GAS_LIMIT_FOR_CALL * 4)
                                .via(INNER_TXN))))
                .then(
                        getTxnRecord(INNER_TXN)
                                .hasPriority(recordWith()
                                        .transfers(including(tinyBarsFromTo(INTERNAL_CALLER_CONTRACT, RECEIVER, 1)))),
                        getAccountBalance(RECEIVER).hasTinyBars(changeFromSnapshot("initialBalance", 1)));
    }

    @HapiTest
    final Stream<DynamicTest>
            internalCallWithValueToNonExistingNonMirrorAddressWithoutEnoughGasForLazyCreationResultsInSuccessNoAccountCreated() {
        return defaultHapiSpec(
                        "internalCallWithValueToNonExistingNonMirrorAddressWithoutEnoughGasForLazyCreationResultsInSuccessNoAccountCreated")
                .given(
                        cryptoCreate(CUSTOM_PAYER),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(
                        balanceSnapshot("contractBalance", INTERNAL_CALLER_CONTRACT),
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        CALL_WITH_VALUE_TO_FUNCTION,
                                        nonMirrorAddrWith(FIRST_NONEXISTENT_CONTRACT_NUM + 7))
                                .payingWith(CUSTOM_PAYER)
                                .gas(NOT_ENOUGH_GAS_LIMIT_FOR_CREATION)
                                .via("transferWithLowGasLimit"))
                .then(
                        getTxnRecord("transferWithLowGasLimit")
                                .hasPriority(recordWith().status(SUCCESS)),
                        getAccountBalance(INTERNAL_CALLER_CONTRACT)
                                .hasTinyBars(changeFromSnapshot("contractBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest>
            internalCallWithValueToNonExistingNonMirrorAddressWithEnoughGasForLazyCreationResultsInSuccessAccountCreated() {
        return defaultHapiSpec(
                        "internalCallWithValueToNonExistingNonMirrorAddressWithEnoughGasForLazyCreationResultsInSuccessAccountCreated")
                .given(
                        cryptoCreate(CUSTOM_PAYER),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(
                        balanceSnapshot("contractBalance", INTERNAL_CALLER_CONTRACT),
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        CALL_WITH_VALUE_TO_FUNCTION,
                                        nonMirrorAddrWith(FIRST_NONEXISTENT_CONTRACT_NUM + 8))
                                .payingWith(CUSTOM_PAYER)
                                .gas(ENOUGH_GAS_LIMIT_FOR_CREATION)
                                .via("transferWithEnoughGasLimit"))
                .then(
                        getTxnRecord("transferWithEnoughGasLimit")
                                .hasPriority(recordWith().status(SUCCESS)),
                        getAccountBalance(INTERNAL_CALLER_CONTRACT)
                                .hasTinyBars(changeFromSnapshot("contractBalance", -1)));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallWithValueToExistingNonMirrorAddressResultsInSuccess() {

        return defaultHapiSpec("internalCallWithValueToExistingNonMirrorAddressResultsInSuccess")
                .given(
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, ECDSA_KEY, ONE_HUNDRED_HBARS)),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, ECDSA_KEY)),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    allRunFor(
                            spec,
                            balanceSnapshot("autoCreatedSnapshot", ECDSA_KEY).accountIsAlias(),
                            contractCall(
                                            INTERNAL_CALLER_CONTRACT,
                                            CALL_WITH_VALUE_TO_FUNCTION,
                                            asHeadlongAddress(addressBytes))
                                    .gas(GAS_LIMIT_FOR_CALL * 4)
                                    .via(INNER_TXN));
                }))
                .then(
                        getTxnRecord(INNER_TXN)
                                .hasPriority(recordWith()
                                        .transfers(including(tinyBarsFromTo(INTERNAL_CALLER_CONTRACT, ECDSA_KEY, 1)))),
                        getAutoCreatedAccountBalance(ECDSA_KEY)
                                .hasTinyBars(changeFromSnapshot("autoCreatedSnapshot", 1)));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallToDeletedContractReturnsSuccessfulNoop() {
        final AtomicLong calleeNum = new AtomicLong();
        return defaultHapiSpec("internalCallToDeletedContractReturnsSuccessfulNoop")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT, INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT)
                                // Refusing ethereum create conversion, because we get INVALID_SIGNATURE upon
                                // tokenAssociate,
                                // since we have CONTRACT_ID key
                                .refusingEthConversion()
                                .balance(ONE_HBAR),
                        contractCreate(INTERNAL_CALLEE_CONTRACT)
                                // Refusing ethereum create conversion, because we get INVALID_SIGNATURE upon
                                // tokenAssociate,
                                // since we have CONTRACT_ID key
                                .refusingEthConversion()
                                .exposingNumTo(calleeNum::set),
                        contractDelete(INTERNAL_CALLEE_CONTRACT))
                .when(withOpContext((spec, ignored) -> allRunFor(
                        spec,
                        contractCall(INTERNAL_CALLER_CONTRACT, CALL_EXTERNAL_FUNCTION, mirrorAddrWith(calleeNum.get()))
                                .gas(50_000L)
                                .via(INNER_TXN))))
                .then(withOpContext((spec, opLog) -> {
                    final var lookup = getTxnRecord(INNER_TXN);
                    allRunFor(spec, lookup);
                    final var result =
                            lookup.getResponseRecord().getContractCallResult().getContractCallResult();
                    assertEquals(ByteString.copyFrom(new byte[32]), result);
                }));
    }

    @HapiTest
    final Stream<DynamicTest> callingDestructedContractReturnsStatusSuccess() {
        final AtomicReference<AccountID> accountIDAtomicReference = new AtomicReference<>();
        return defaultHapiSpec("callingDestructedContractReturnsStatusSuccess")
                .given(
                        cryptoCreate(BENEFICIARY).exposingCreatedIdTo(accountIDAtomicReference::set),
                        uploadInitCode(SIMPLE_UPDATE_CONTRACT))
                .when(
                        contractCreate(SIMPLE_UPDATE_CONTRACT).gas(300_000L),
                        contractCall(SIMPLE_UPDATE_CONTRACT, "set", BigInteger.valueOf(5), BigInteger.valueOf(42))
                                .gas(300_000L),
                        sourcing(() -> contractCall(
                                        SIMPLE_UPDATE_CONTRACT,
                                        "del",
                                        asHeadlongAddress(asAddress(accountIDAtomicReference.get())))
                                .gas(1_000_000L)))
                .then(contractCall(SIMPLE_UPDATE_CONTRACT, "set", BigInteger.valueOf(15), BigInteger.valueOf(434))
                        .gas(350_000L)
                        .hasKnownStatus(SUCCESS));
    }

    @HapiTest
    final Stream<DynamicTest> internalStaticCallNonExistingMirrorAddressResultsInSuccess() {
        return defaultHapiSpec("internalStaticCallNonExistingMirrorAddressResultsInSuccess")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(contractCall(
                                INTERNAL_CALLER_CONTRACT,
                                STATIC_CALL_EXTERNAL_FUNCTION,
                                mirrorAddrWith(FIRST_NONEXISTENT_CONTRACT_NUM + 9))
                        .gas(GAS_LIMIT_FOR_CALL)
                        .via(INNER_TXN)
                        .hasKnownStatus(SUCCESS))
                .then(getTxnRecord(INNER_TXN)
                        .logged()
                        .hasPriority(
                                recordWith().contractCallResult(resultWith().contractCallResult(bigIntResult(0)))));
    }

    @HapiTest
    final Stream<DynamicTest> internalStaticCallExistingMirrorAddressResultsInSuccess() {
        AtomicReference<AccountID> receiverId = new AtomicReference<>();
        return defaultHapiSpec("internalStaticCallExistingMirrorAddressResultsInSuccess")
                .given(
                        cryptoCreate(RECEIVER).exposingCreatedIdTo(receiverId::set),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        balanceSnapshot("initialBalance", asAccountString(receiverId.get())),
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        STATIC_CALL_EXTERNAL_FUNCTION,
                                        mirrorAddrWith(receiverId.get().getAccountNum()))
                                .gas(GAS_LIMIT_FOR_CALL)
                                .via(INNER_TXN))))
                .then(
                        getTxnRecord(INNER_TXN)
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith().contractCallResult(bigIntResult(0)))),
                        getAccountBalance(RECEIVER).hasTinyBars(changeFromSnapshot("initialBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> internalStaticCallNonExistingNonMirrorAddressResultsInSuccess() {
        AtomicReference<Bytes> nonExistingNonMirrorAddress = new AtomicReference<>();
        return defaultHapiSpec("internalStaticCallNonExistingNonMirrorAddressResultsInSuccess")
                .given(
                        cryptoCreate(CUSTOM_PAYER).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        withOpContext((spec, op) -> {
                            final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                            final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                            final var addressBytes = recoverAddressFromPubKey(tmp);
                            nonExistingNonMirrorAddress.set(Bytes.of(addressBytes));
                        }),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        balanceSnapshot("contractBalance", INTERNAL_CALLER_CONTRACT),
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        STATIC_CALL_EXTERNAL_FUNCTION,
                                        asHeadlongAddress(nonExistingNonMirrorAddress
                                                .get()
                                                .toArray()))
                                .gas(GAS_LIMIT_FOR_CALL)
                                .payingWith(CUSTOM_PAYER)
                                .via(INNER_TXN))))
                .then(
                        getTxnRecord(INNER_TXN)
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith().contractCallResult(bigIntResult(0)))),
                        getAccountBalance(INTERNAL_CALLER_CONTRACT)
                                .hasTinyBars(changeFromSnapshot("contractBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> internalStaticCallExistingNonMirrorAddressResultsInSuccess() {
        return defaultHapiSpec("internalStaticCallExistingNonMirrorAddressResultsInSuccess")
                .given(
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, ECDSA_KEY, ONE_HUNDRED_HBARS)),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, ECDSA_KEY)),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    allRunFor(
                            spec,
                            balanceSnapshot("targetSnapshot", ECDSA_KEY).accountIsAlias(),
                            contractCall(
                                            INTERNAL_CALLER_CONTRACT,
                                            STATIC_CALL_EXTERNAL_FUNCTION,
                                            asHeadlongAddress(addressBytes))
                                    .gas(GAS_LIMIT_FOR_CALL)
                                    .via(INNER_TXN));
                }))
                .then(
                        getTxnRecord(INNER_TXN)
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith().contractCallResult(bigIntResult(0)))),
                        getAutoCreatedAccountBalance(ECDSA_KEY).hasTinyBars(changeFromSnapshot("targetSnapshot", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> internalDelegateCallNonExistingMirrorAddressResultsInSuccess() {
        return defaultHapiSpec("internalDelegateCallNonExistingMirrorAddressResultsInSuccess")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(contractCall(
                                INTERNAL_CALLER_CONTRACT,
                                DELEGATE_CALL_EXTERNAL_FUNCTION,
                                mirrorAddrWith(FIRST_NONEXISTENT_CONTRACT_NUM + 10))
                        .gas(GAS_LIMIT_FOR_CALL)
                        .via(INNER_TXN)
                        .hasKnownStatus(SUCCESS))
                .then(getTxnRecord(INNER_TXN)
                        .logged()
                        .hasPriority(
                                recordWith().contractCallResult(resultWith().contractCallResult(bigIntResult(0)))));
    }

    @HapiTest
    final Stream<DynamicTest> internalDelegateCallExistingMirrorAddressResultsInSuccess() {
        AtomicReference<AccountID> receiverId = new AtomicReference<>();
        return defaultHapiSpec("internalDelegateCallExistingMirrorAddressResultsInSuccess")
                .given(
                        cryptoCreate(RECEIVER).exposingCreatedIdTo(receiverId::set),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        balanceSnapshot("initialBalance", asAccountString(receiverId.get())),
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        DELEGATE_CALL_EXTERNAL_FUNCTION,
                                        mirrorAddrWith(receiverId.get().getAccountNum()))
                                .gas(GAS_LIMIT_FOR_CALL)
                                .via(INNER_TXN))))
                .then(
                        getTxnRecord(INNER_TXN)
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith().contractCallResult(bigIntResult(0)))),
                        getAccountBalance(RECEIVER).hasTinyBars(changeFromSnapshot("initialBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> internalDelegateCallNonExistingNonMirrorAddressResultsInSuccess() {
        AtomicReference<Bytes> nonExistingNonMirrorAddress = new AtomicReference<>();
        return defaultHapiSpec("internalDelegateCallNonExistingNonMirrorAddressResultsInSuccess")
                .given(
                        cryptoCreate(CUSTOM_PAYER).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        withOpContext((spec, op) -> {
                            final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                            final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                            final var addressBytes = recoverAddressFromPubKey(tmp);
                            nonExistingNonMirrorAddress.set(Bytes.of(addressBytes));
                        }),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        balanceSnapshot("contractBalance", INTERNAL_CALLER_CONTRACT),
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        DELEGATE_CALL_EXTERNAL_FUNCTION,
                                        asHeadlongAddress(nonExistingNonMirrorAddress
                                                .get()
                                                .toArray()))
                                .gas(GAS_LIMIT_FOR_CALL)
                                .payingWith(CUSTOM_PAYER)
                                .via(INNER_TXN))))
                .then(
                        getTxnRecord(INNER_TXN)
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith().contractCallResult(bigIntResult(0)))),
                        getAccountBalance(INTERNAL_CALLER_CONTRACT)
                                .hasTinyBars(changeFromSnapshot("contractBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> internalDelegateCallExistingNonMirrorAddressResultsInSuccess() {
        return defaultHapiSpec("internalDelegateCallExistingNonMirrorAddressResultsInSuccess")
                .given(
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, ECDSA_KEY, ONE_HUNDRED_HBARS)),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, ECDSA_KEY)),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    allRunFor(
                            spec,
                            balanceSnapshot("targetSnapshot", ECDSA_KEY).accountIsAlias(),
                            contractCall(
                                            INTERNAL_CALLER_CONTRACT,
                                            DELEGATE_CALL_EXTERNAL_FUNCTION,
                                            asHeadlongAddress(addressBytes))
                                    .gas(GAS_LIMIT_FOR_CALL)
                                    .via(INNER_TXN));
                }))
                .then(
                        getTxnRecord(INNER_TXN)
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith().contractCallResult(bigIntResult(0)))),
                        getAutoCreatedAccountBalance(ECDSA_KEY).hasTinyBars(changeFromSnapshot("targetSnapshot", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallWithValueToAccountWithReceiverSigRequiredTrue() {
        AtomicReference<AccountID> receiverId = new AtomicReference<>();

        return defaultHapiSpec("internalCallWithValueToAccountWithReceiverSigRequiredTrue")
                .given(
                        cryptoCreate(RECEIVER).receiverSigRequired(true).exposingCreatedIdTo(receiverId::set),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        balanceSnapshot("initialBalance", INTERNAL_CALLER_CONTRACT),
                        contractCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        CALL_WITH_VALUE_TO_FUNCTION,
                                        mirrorAddrWith(receiverId.get().getAccountNum()))
                                .gas(GAS_LIMIT_FOR_CALL * 4)
                                .via(INNER_TXN)
                                .hasKnownStatus(INVALID_SIGNATURE))))
                .then(
                        getTxnRecord(INNER_TXN).hasPriority(recordWith().status(INVALID_SIGNATURE)),
                        getAccountBalance(INTERNAL_CALLER_CONTRACT)
                                .hasTinyBars(changeFromSnapshot("initialBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallToSystemAccount564ResultsInSuccessNoop() {
        AtomicReference<AccountID> targetId = new AtomicReference<>();
        targetId.set(AccountID.newBuilder().setAccountNum(564L).build());

        return defaultHapiSpec("internalCallToSystemAccount564ResultsInSuccessNoop")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(
                        balanceSnapshot("initialBalance", INTERNAL_CALLER_CONTRACT),
                        withOpContext((spec, op) -> allRunFor(
                                spec,
                                contractCall(
                                                INTERNAL_CALLER_CONTRACT,
                                                CALL_EXTERNAL_FUNCTION,
                                                mirrorAddrWith(targetId.get().getAccountNum()))
                                        .gas(GAS_LIMIT_FOR_CALL * 4)
                                        .via(INNER_TXN))))
                .then(
                        getTxnRecord(INNER_TXN).hasPriority(recordWith().status(SUCCESS)),
                        getAccountBalance(INTERNAL_CALLER_CONTRACT)
                                .hasTinyBars(changeFromSnapshot("initialBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallsAgainstSystemAccountsWithValue() {
        final var withAmount = "makeCallWithAmount";
        return defaultHapiSpec(
                        "internalCallsAgainstSystemAccountsWithValue",
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
                        uploadInitCode(MAKE_CALLS_CONTRACT),
                        contractCreate(MAKE_CALLS_CONTRACT).gas(GAS_LIMIT_FOR_CALL * 4))
                .when(
                        balanceSnapshot("initialBalance", MAKE_CALLS_CONTRACT),
                        contractCall(
                                        MAKE_CALLS_CONTRACT,
                                        withAmount,
                                        idAsHeadlongAddress(AccountID.newBuilder()
                                                .setAccountNum(357)
                                                .build()),
                                        new byte[] {"system account".getBytes()[0]})
                                .gas(GAS_LIMIT_FOR_CALL * 4)
                                .sending(2L)
                                .via(INNER_TXN)
                                .hasKnownStatus(INVALID_CONTRACT_ID))
                .then(getAccountBalance(MAKE_CALLS_CONTRACT).hasTinyBars(changeFromSnapshot("initialBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallsAgainstSystemAccountsWithoutValue() {
        final var withoutAmount = "makeCallWithoutAmount";
        return defaultHapiSpec(
                        "internalCallsAgainstSystemAccountsWithoutValue",
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
                        uploadInitCode(MAKE_CALLS_CONTRACT),
                        contractCreate(MAKE_CALLS_CONTRACT).gas(GAS_LIMIT_FOR_CALL * 4))
                .when(
                        balanceSnapshot("initialBalance", MAKE_CALLS_CONTRACT),
                        contractCall(
                                        MAKE_CALLS_CONTRACT,
                                        withoutAmount,
                                        idAsHeadlongAddress(AccountID.newBuilder()
                                                .setAccountNum(357)
                                                .build()),
                                        new byte[] {"system account".getBytes()[0]})
                                .gas(GAS_LIMIT_FOR_CALL * 4)
                                .via(INNER_TXN)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getTxnRecord(INNER_TXN).hasPriority(recordWith().status(SUCCESS)),
                        getAccountBalance(MAKE_CALLS_CONTRACT).hasTinyBars(changeFromSnapshot("initialBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallToEthereumPrecompile0x2ResultsInSuccess() {
        AtomicReference<AccountID> targetId = new AtomicReference<>();
        targetId.set(AccountID.newBuilder().setAccountNum(2L).build());

        return defaultHapiSpec("internalCallToEthereumPrecompile0x2ResultsInSuccess")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(
                        balanceSnapshot("initialBalance", INTERNAL_CALLER_CONTRACT),
                        withOpContext((spec, op) -> allRunFor(
                                spec,
                                contractCall(
                                                INTERNAL_CALLER_CONTRACT,
                                                CALL_EXTERNAL_FUNCTION,
                                                mirrorAddrWith(targetId.get().getAccountNum()))
                                        .gas(GAS_LIMIT_FOR_CALL * 4)
                                        .via(INNER_TXN))))
                .then(
                        withOpContext((spec, opLog) -> {
                            final var lookup = getTxnRecord(INNER_TXN);
                            allRunFor(spec, lookup);
                            final var result = lookup.getResponseRecord()
                                    .getContractCallResult()
                                    .getContractCallResult();
                            assertNotEquals(ByteString.copyFrom(new byte[32]), result);
                        }),
                        getAccountBalance(INTERNAL_CALLER_CONTRACT)
                                .hasTinyBars(changeFromSnapshot("initialBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallWithValueToEthereumPrecompile0x2ResultsInRevert() {
        AtomicReference<AccountID> targetId = new AtomicReference<>();
        targetId.set(AccountID.newBuilder().setAccountNum(2L).build());

        return defaultHapiSpec("internalCallWithValueToEthereumPrecompile0x2ResultsInRevert")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(
                        balanceSnapshot("initialBalance", INTERNAL_CALLER_CONTRACT),
                        withOpContext((spec, op) -> allRunFor(
                                spec,
                                contractCall(
                                                INTERNAL_CALLER_CONTRACT,
                                                CALL_WITH_VALUE_TO_FUNCTION,
                                                mirrorAddrWith(targetId.get().getAccountNum()))
                                        .gas(GAS_LIMIT_FOR_CALL * 4)
                                        .via(INNER_TXN)
                                        .hasKnownStatus(INVALID_CONTRACT_ID))))
                .then(getAccountBalance(INTERNAL_CALLER_CONTRACT).hasTinyBars(changeFromSnapshot("initialBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallToNonExistingSystemAccount852ResultsInSuccessNoop() {
        AtomicReference<AccountID> targetId = new AtomicReference<>();
        targetId.set(AccountID.newBuilder().setAccountNum(852L).build());

        return defaultHapiSpec("internalCallToNonExistingSystemAccount852ResultsInSuccessNoop")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(
                        balanceSnapshot("initialBalance", INTERNAL_CALLER_CONTRACT),
                        withOpContext((spec, op) -> allRunFor(
                                spec,
                                contractCall(
                                                INTERNAL_CALLER_CONTRACT,
                                                CALL_EXTERNAL_FUNCTION,
                                                mirrorAddrWith(targetId.get().getAccountNum()))
                                        .gas(GAS_LIMIT_FOR_CALL * 4)
                                        .via(INNER_TXN))))
                .then(
                        getTxnRecord(INNER_TXN).hasPriority(recordWith().status(SUCCESS)),
                        getAccountBalance(INTERNAL_CALLER_CONTRACT)
                                .hasTinyBars(changeFromSnapshot("initialBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallWithValueToNonExistingSystemAccount852ResultsInInvalidAliasKey() {
        AtomicReference<AccountID> targetId = new AtomicReference<>();
        final var systemAccountNum = 852L;
        targetId.set(AccountID.newBuilder().setAccountNum(systemAccountNum).build());

        return defaultHapiSpec("internalCallWithValueToNonExistingSystemAccount852ResultsInInvalidAliasKey")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(
                        balanceSnapshot("initialBalance", INTERNAL_CALLER_CONTRACT),
                        withOpContext((spec, op) -> allRunFor(
                                spec,
                                contractCall(
                                                INTERNAL_CALLER_CONTRACT,
                                                CALL_WITH_VALUE_TO_FUNCTION,
                                                mirrorAddrWith(targetId.get().getAccountNum()))
                                        .gas(GAS_LIMIT_FOR_CALL * 4))))
                .then(
                        getAccountBalance(INTERNAL_CALLER_CONTRACT)
                                .hasTinyBars(changeFromSnapshot("initialBalance", 0)),
                        getAccountBalance("0.0." + systemAccountNum).hasAnswerOnlyPrecheck(INVALID_ACCOUNT_ID));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallWithValueToSystemAccount564ResultsInSuccessNoopNoTransfer() {
        AtomicReference<AccountID> targetId = new AtomicReference<>();
        targetId.set(AccountID.newBuilder().setAccountNum(564L).build());

        return defaultHapiSpec("internalCallWithValueToSystemAccount564ResultsInSuccessNoopNoTransfer")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(
                        balanceSnapshot("initialBalance", INTERNAL_CALLER_CONTRACT),
                        withOpContext((spec, op) -> allRunFor(
                                spec,
                                contractCall(
                                                INTERNAL_CALLER_CONTRACT,
                                                CALL_WITH_VALUE_TO_FUNCTION,
                                                mirrorAddrWith(targetId.get().getAccountNum()))
                                        .gas(GAS_LIMIT_FOR_CALL * 4)
                                        .via(INNER_TXN)
                                        .hasKnownStatus(INVALID_CONTRACT_ID))))
                .then(getAccountBalance(INTERNAL_CALLER_CONTRACT).hasTinyBars(changeFromSnapshot("initialBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallWithValueToExistingSystemAccount800ResultsInSuccessfulTransfer() {
        AtomicReference<AccountID> targetId = new AtomicReference<>();
        targetId.set(AccountID.newBuilder().setAccountNum(800L).build());

        return defaultHapiSpec("internalCallWithValueToExistingSystemAccount800ResultsInSuccessfulTransfer")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(
                        balanceSnapshot("initialBalance", INTERNAL_CALLER_CONTRACT),
                        withOpContext((spec, op) -> allRunFor(
                                spec,
                                contractCall(
                                                INTERNAL_CALLER_CONTRACT,
                                                CALL_WITH_VALUE_TO_FUNCTION,
                                                mirrorAddrWith(targetId.get().getAccountNum()))
                                        .gas(GAS_LIMIT_FOR_CALL * 4)
                                        .via(INNER_TXN))))
                .then(
                        getTxnRecord(INNER_TXN).hasPriority(recordWith().status(SUCCESS)),
                        getAccountBalance(INTERNAL_CALLER_CONTRACT)
                                .hasTinyBars(changeFromSnapshot("initialBalance", -1)));
    }

    @HapiTest
    final Stream<DynamicTest> internalCallToExistingSystemAccount800ResultsInSuccessNoop() {
        AtomicReference<AccountID> targetId = new AtomicReference<>();
        targetId.set(AccountID.newBuilder().setAccountNum(800L).build());

        return defaultHapiSpec("internalCallToExistingSystemAccount800ResultsInSuccessNoop")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(
                        balanceSnapshot("initialBalance", INTERNAL_CALLER_CONTRACT),
                        withOpContext((spec, op) -> allRunFor(
                                spec,
                                contractCall(
                                                INTERNAL_CALLER_CONTRACT,
                                                CALL_EXTERNAL_FUNCTION,
                                                mirrorAddrWith(targetId.get().getAccountNum()))
                                        .gas(GAS_LIMIT_FOR_CALL * 4)
                                        .via(INNER_TXN))))
                .then(
                        getTxnRecord(INNER_TXN).hasPriority(recordWith().status(SUCCESS)),
                        getAccountBalance(INTERNAL_CALLER_CONTRACT)
                                .hasTinyBars(changeFromSnapshot("initialBalance", 0)));
    }

    @HapiTest
    final Stream<DynamicTest> testBalanceOfForExistingSystemAccounts() {
        final var contract = "BalanceChecker46Version";
        final var balance = 10L;
        final var systemAccountBalance = 0L;
        final HapiSpecOperation[] opsArray = new HapiSpecOperation[existingSystemAccounts.size() * 2];

        for (int i = 0; i < existingSystemAccounts.size(); i++) {
            // add contract call for all accounts in the list
            opsArray[i] = contractCall(contract, BALANCE_OF, mirrorAddrWith(existingSystemAccounts.get(i)))
                    .hasKnownStatus(SUCCESS);

            // add contract call local for all accounts in the list
            opsArray[existingSystemAccounts.size() + i] = contractCallLocal(
                            contract, BALANCE_OF, mirrorAddrWith(existingSystemAccounts.get(i)))
                    .has(ContractFnResultAsserts.resultWith()
                            .resultThruAbi(
                                    getABIFor(FUNCTION, BALANCE_OF, contract),
                                    ContractFnResultAsserts.isEqualOrGreaterThan(
                                            BigInteger.valueOf(systemAccountBalance))));
        }
        return defaultHapiSpec("verifiesSystemAccountBalanceOf")
                .given(cryptoCreate("testAccount").balance(balance), uploadInitCode(contract), contractCreate(contract))
                .when()
                .then(opsArray);
    }

    @HapiTest
    final Stream<DynamicTest> testBalanceOfForNonExistingSystemAccounts() {
        final var contract = "BalanceChecker46Version";
        final var balance = 10L;
        final var systemAccountBalance = 0;
        final HapiSpecOperation[] opsArray = new HapiSpecOperation[nonExistingSystemAccounts.size() * 2];

        for (int i = 0; i < nonExistingSystemAccounts.size(); i++) {
            // add contract call for all accounts in the list
            opsArray[i] = contractCall(contract, BALANCE_OF, mirrorAddrWith(nonExistingSystemAccounts.get(i)))
                    .hasKnownStatus(SUCCESS);

            // add contract call local for all accounts in the list
            opsArray[nonExistingSystemAccounts.size() + i] = contractCallLocal(
                            contract, BALANCE_OF, mirrorAddrWith(nonExistingSystemAccounts.get(i)))
                    .has(ContractFnResultAsserts.resultWith()
                            .resultThruAbi(
                                    getABIFor(FUNCTION, BALANCE_OF, contract),
                                    ContractFnResultAsserts.isLiteralResult(
                                            new Object[] {BigInteger.valueOf(systemAccountBalance)})));
        }
        return defaultHapiSpec("verifiesSystemAccountBalanceOf")
                .given(cryptoCreate("testAccount").balance(balance), uploadInitCode(contract), contractCreate(contract))
                .when()
                .then(opsArray);
    }

    @HapiTest
    final Stream<DynamicTest> directCallToSystemAccountResultsInSuccessfulNoOp() {
        return defaultHapiSpec("directCallToSystemAccountResultsInSuccessfulNoOp")
                .given(
                        cryptoCreate("account").balance(ONE_HUNDRED_HBARS),
                        withOpContext((spec, opLog) -> spec.registry()
                                .saveContractId(
                                        "contract",
                                        asContractIdWithEvmAddress(ByteString.copyFrom(
                                                unhex("0000000000000000000000000000000000000275"))))))
                .when(withOpContext((spec, ctxLog) -> allRunFor(
                        spec,
                        contractCallWithFunctionAbi("contract", getABIFor(FUNCTION, NAME, ERC_721_ABI))
                                .gas(GAS_LIMIT_FOR_CALL)
                                .via("callToSystemAddress")
                                .signingWith("account"))))
                .then(getTxnRecord("callToSystemAddress")
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith().gasUsed(GAS_LIMIT_FOR_CALL))));
    }

    @HapiTest
    final Stream<DynamicTest> testCallOperationsForSystemAccounts() {
        final var contract = "CallOperationsCheckerSuccess";
        final var functionName = "call";
        final HapiSpecOperation[] opsArray = getCallOperationsOnSystemAccounts(contract, functionName);
        return defaultHapiSpec("testCallOperationsForSystemAccounts")
                .given(uploadInitCode(contract), contractCreate(contract))
                .when()
                .then(opsArray);
    }

    @HapiTest
    final Stream<DynamicTest> testCallCodeOperationsForSystemAccounts() {
        final var contract = "CallOperationsCheckerSuccess";
        final var functionName = "callCode";
        final HapiSpecOperation[] opsArray = getCallOperationsOnSystemAccounts(contract, functionName);
        return defaultHapiSpec("testCallCodeOperationsForSystemAccounts")
                .given(uploadInitCode(contract), contractCreate(contract))
                .when()
                .then(opsArray);
    }

    @HapiTest
    final Stream<DynamicTest> testDelegateCallOperationsForSystemAccounts() {
        final var contract = "CallOperationsCheckerSuccess";
        final var functionName = "delegateCall";
        final HapiSpecOperation[] opsArray = getCallOperationsOnSystemAccounts(contract, functionName);
        return defaultHapiSpec("testDelegateCallOperationsForSystemAccounts")
                .given(uploadInitCode(contract), contractCreate(contract))
                .when()
                .then(opsArray);
    }

    @HapiTest
    final Stream<DynamicTest> testStaticCallOperationsForSystemAccounts() {
        final var contract = "CallOperationsCheckerSuccess";
        final var functionName = "staticcall";
        final HapiSpecOperation[] opsArray = getCallOperationsOnSystemAccounts(contract, functionName);
        return defaultHapiSpec("testStaticCallOperationsForSystemAccounts")
                .given(uploadInitCode(contract), contractCreate(contract))
                .when()
                .then(opsArray);
    }

    private HapiSpecOperation[] getCallOperationsOnSystemAccounts(final String contract, final String functionName) {
        final HapiSpecOperation[] opsArray = new HapiSpecOperation[callOperationsSuccessSystemAccounts.size()];
        for (int i = 0; i < callOperationsSuccessSystemAccounts.size(); i++) {
            int finalI = i;
            opsArray[i] = withOpContext((spec, opLog) -> allRunFor(
                    spec,
                    contractCall(
                                    contract,
                                    functionName,
                                    mirrorAddrWith(callOperationsSuccessSystemAccounts.get(finalI)))
                            .hasKnownStatus(SUCCESS)));
        }
        return opsArray;
    }
}
