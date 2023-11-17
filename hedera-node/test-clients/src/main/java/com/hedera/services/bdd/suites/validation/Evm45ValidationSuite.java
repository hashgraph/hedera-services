/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.validation;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiPropertySource.asAccountString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContractIdWithEvmAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.including;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAutoCreatedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.mirrorAddrWith;
import static com.hedera.services.bdd.suites.contract.Utils.nonMirrorAddrWith;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.updateSpecFor;
import static com.hedera.services.bdd.suites.utils.contracts.ErrorMessageResult.errorMessageResult;
import static com.hedera.services.bdd.suites.utils.contracts.SimpleBytesResult.bigIntResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.swirlds.common.utility.CommonUtils.unhex;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// @HapiTestSuite
public class Evm45ValidationSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(Evm45ValidationSuite.class);
    private static final String NAME = "name";
    private static final String ERC_721_ABI = "ERC721ABI";
    private static final String NON_EXISTING_MIRROR_ADDRESS = "0000000000000000000000000000000000123456";
    private static final String NON_EXISTING_NON_MIRROR_ADDRESS = "1234561234561234561234561234568888123456";
    private static final String INTERNAL_CALLER_CONTRACT = "InternalCaller";
    private static final String INTERNAL_CALLEE_CONTRACT = "InternalCallee";
    private static final String REVERT_WITH_REVERT_REASON_FUNCTION = "revertWithRevertReason";
    private static final String REVERT_WITHOUT_REVERT_REASON_FUNCTION = "revertWithoutRevertReason";
    private static final String CALL_NON_EXISTING_FUNCTION = "callNonExisting";
    private static final String CALL_EXTERNAL_FUNCTION = "callExternalFunction";
    private static final String CALL_REVERT_WITH_REVERT_REASON_FUNCTION = "callRevertWithRevertReason";
    private static final String CALL_REVERT_WITHOUT_REVERT_REASON_FUNCTION = "callRevertWithoutRevertReason";
    private static final String TRANSFER_TO_FUNCTION = "transferTo";
    private static final String SEND_TO_FUNCTION = "sendTo";
    private static final String CALL_WITH_VALUE_TO_FUNCTION = "callWithValueTo";
    private static final String INNER_TXN = "innerTx";
    private static final Long INTRINSIC_GAS_COST = 21000L;
    private static final Long GAS_LIMIT_FOR_CALL = 25000L;
    private static final String RECEIVER = "receiver";

    public static void main(String... args) {
        new Evm45ValidationSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                // Top-level calls:
                // todo add cases with repeat call attempt, assert that the account is not created
                // EOA -calls-> NonExistingMirror, expect noop success
                directCallToNonExistingMirrorAddressResultsInSuccessfulNoOp(),
                // EOA -calls-> NonExistingNonMirror, expect noop success
                directCallToNonExistingNonMirrorAddressResultsInSuccessfulNoOp(),
                // EOA -calls-> Reverting, expect revert
                directCallToRevertingContractRevertsWithCorrectRevertReason(),

                // Internal calls:
                // EOA -calls-> InternalCaller -calls-> NonExistingMirror, expect noop success
                internalCallToNonExistingMirrorAddressResultsInNoopSuccess(),
                // EOA -calls-> InternalCaller -calls-> ExistingMirror, expect successful call
                internalCallToExistingMirrorAddressResultsInSuccessfulCall(),
                // EOA -calls-> InternalCaller -calls-> NonExistingNonMirror, expect noop success
                internalCallToNonExistingNonMirrorAddressResultsInNoopSuccess(),
                // EOA -calls-> InternalCaller -calls-> Existing reverting without revert message
                internalCallToExistingRevertingResultsInSuccessfulTopLevelTxn(),
                // todo EOA -calls-> InternalCaller -calls-> ExistingNonMirror, expect successful call

                // Internal transfers:
                // EOA -calls-> InternalCaller -transfer-> NonExistingMirror, expect revert
                // todo is this all we expect? no contract function result? INVALID_SOLIDITY_ADDRESS?
                internalTransferToNonExistingMirrorAddressResultsInRevert(),
                // EOA -calls-> InternalCaller -transfer-> ExistingMirror, expect success
                internalTransferToExistingMirrorAddressResultsInSuccess(),
                // EOA -calls-> InternalCaller -transfer-> NonExistingNonMirror, expect revert
                // todo is this all we expect? no contract function result? CONTRACT_REVERT_EXECUTED?
                internalTransferToNonExistingNonMirrorAddressResultsInRevert(),
                // EOA -calls-> InternalCaller -transfer-> ExistingNonMirror, expect success
                internalTransferToExistingNonMirrorAddressResultsInSuccess()

                // Internal sends:
                // todo EOA -calls-> InternalCaller -send-> NonExistingMirror, expect ?
                // todo EOA -calls-> InternalCaller -send-> ExistingMirror, expect ?
                // todo EOA -calls-> InternalCaller -send-> NonExistingNonMirror, expect ?
                // todo EOA -calls-> InternalCaller -send-> ExistingNonMirror, expect ?

                // Internal calls with value:
                // todo EOA -calls-> InternalCaller -callWValue-> NonExistingMirror, expect ?
                // todo EOA -calls-> InternalCaller -callWValue-> ExistingMirror, expect ?
                // todo EOA -calls-> InternalCaller -callWValue-> NonExistingNonMirror, expect ?
                // todo EOA -calls-> InternalCaller -callWValue-> ExistingNonMirror, expect ?

                // Ethereum transaction cases
                // todo
                );
    }

    private HapiSpec directCallToNonExistingMirrorAddressResultsInSuccessfulNoOp() {

        return defaultHapiSpec("directCallToNonExistingMirrorAddressResultsInSuccessfulNoOp")
                .given(withOpContext((spec, ctxLog) -> spec.registry()
                        .saveContractId(
                                "nonExistingMirrorAddress",
                                asContractIdWithEvmAddress(ByteString.copyFrom(unhex(NON_EXISTING_MIRROR_ADDRESS))))))
                .when(withOpContext((spec, ctxLog) -> allRunFor(
                        spec,
                        contractCallWithFunctionAbi(
                                        "nonExistingMirrorAddress",
                                        getABIFor(
                                                com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                NAME,
                                                ERC_721_ABI))
                                .gas(GAS_LIMIT_FOR_CALL)
                                .via("directCallToNonExistingMirrorAddress"))))
                .then(getTxnRecord("directCallToNonExistingMirrorAddress")
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith().gasUsed(INTRINSIC_GAS_COST))));
    }

    private HapiSpec directCallToNonExistingNonMirrorAddressResultsInSuccessfulNoOp() {

        return defaultHapiSpec("directCallToNonExistingNonMirrorAddressResultsInSuccessfulNoOp")
                .given(withOpContext((spec, ctxLog) -> spec.registry()
                        .saveContractId(
                                "nonExistingNonMirrorAddress",
                                asContractIdWithEvmAddress(
                                        ByteString.copyFrom(unhex(NON_EXISTING_NON_MIRROR_ADDRESS))))))
                .when(withOpContext((spec, ctxLog) -> allRunFor(
                        spec,
                        contractCallWithFunctionAbi(
                                        "nonExistingNonMirrorAddress",
                                        getABIFor(
                                                com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                NAME,
                                                ERC_721_ABI))
                                .gas(GAS_LIMIT_FOR_CALL)
                                .via("directCallToNonExistingNonMirrorAddress"))))
                .then(getTxnRecord("directCallToNonExistingNonMirrorAddress")
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith().gasUsed(INTRINSIC_GAS_COST))));
    }

    private HapiSpec directCallToRevertingContractRevertsWithCorrectRevertReason() {

        return defaultHapiSpec("directCallToRevertingContractRevertsWithCorrectRevertReason")
                .given(uploadInitCode(INTERNAL_CALLEE_CONTRACT), contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(withOpContext((spec, ctxLog) -> allRunFor(
                        spec,
                        contractCall(INTERNAL_CALLEE_CONTRACT, REVERT_WITH_REVERT_REASON_FUNCTION)
                                .gas(GAS_LIMIT_FOR_CALL)
                                .via(INNER_TXN)
                                .hasKnownStatusFrom(CONTRACT_REVERT_EXECUTED))))
                .then(getTxnRecord(INNER_TXN)
                        .logged()
                        .hasPriority(recordWith()
                                .status(CONTRACT_REVERT_EXECUTED)
                                .contractCallResult(resultWith()
                                        .gasUsed(21408)
                                        .error(errorMessageResult("RevertReasоn")
                                                .getBytes()
                                                .toString()))));
    }

    private HapiSpec internalCallToNonExistingMirrorAddressResultsInNoopSuccess() {

        return defaultHapiSpec("internalCallToNonExistingMirrorAddressResultsInNoopSuccess")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(contractCall(
                                INTERNAL_CALLER_CONTRACT,
                                CALL_NON_EXISTING_FUNCTION,
                                mirrorAddrWith(new Random().nextLong()))
                        .gas(GAS_LIMIT_FOR_CALL)
                        .via(INNER_TXN))
                .then(getTxnRecord(INNER_TXN)
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(
                                        resultWith().createdContractIdsCount(0).gasUsed(24618))));
    }

    private HapiSpec internalCallToExistingMirrorAddressResultsInSuccessfulCall() {

        final AtomicLong calleeNum = new AtomicLong();

        return defaultHapiSpec("internalCallToNonExistingMirrorAddressResultsInNoopSuccess")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT, INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR),
                        contractCreate(INTERNAL_CALLEE_CONTRACT).exposingNumTo(calleeNum::set))
                .when(withOpContext((spec, ignored) -> allRunFor(
                        spec,
                        contractCall(INTERNAL_CALLER_CONTRACT, CALL_EXTERNAL_FUNCTION, mirrorAddrWith(calleeNum.get()))
                                .gas(GAS_LIMIT_FOR_CALL * 2)
                                .via(INNER_TXN))))
                .then(getTxnRecord(INNER_TXN)
                        .logged()
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .createdContractIdsCount(0)
                                        .contractCallResult(bigIntResult(1))
                                        .gasUsed(47751))));
    }

    private HapiSpec internalCallToNonExistingNonMirrorAddressResultsInNoopSuccess() {

        return defaultHapiSpec("internalCallToNonExistingNonMirrorAddressResultsInNoopSuccess")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(contractCall(
                                INTERNAL_CALLER_CONTRACT,
                                CALL_NON_EXISTING_FUNCTION,
                                nonMirrorAddrWith(new Random().nextLong()))
                        .gas(GAS_LIMIT_FOR_CALL)
                        .via(INNER_TXN))
                .then(getTxnRecord(INNER_TXN)
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(
                                        resultWith().createdContractIdsCount(0).gasUsed(24618))));
    }

    private HapiSpec internalCallToExistingRevertingResultsInSuccessfulTopLevelTxn() {

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

    private HapiSpec internalTransferToNonExistingMirrorAddressResultsInRevert() {

        return defaultHapiSpec("internalTransferToNonExistingMirrorAddressResultsInRevert")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(contractCall(
                                INTERNAL_CALLER_CONTRACT, TRANSFER_TO_FUNCTION, mirrorAddrWith(new Random().nextLong()))
                        .gas(GAS_LIMIT_FOR_CALL * 4)
                        .via(INNER_TXN)
                        .hasKnownStatus(INVALID_SOLIDITY_ADDRESS))
                .then(getTxnRecord(INNER_TXN).hasPriority(recordWith().status(INVALID_SOLIDITY_ADDRESS)));
    }

    private HapiSpec internalTransferToExistingMirrorAddressResultsInSuccess() {

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

    private HapiSpec internalTransferToNonExistingNonMirrorAddressResultsInRevert() {

        return defaultHapiSpec("internalTransferToNonExistingNonMirrorAddressResultsInRevert")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(contractCall(
                                INTERNAL_CALLER_CONTRACT,
                                TRANSFER_TO_FUNCTION,
                                nonMirrorAddrWith(new Random().nextLong()))
                        .gas(GAS_LIMIT_FOR_CALL * 4)
                        .via(INNER_TXN)
                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))
                .then(getTxnRecord(INNER_TXN).logged().hasPriority(recordWith().status(CONTRACT_REVERT_EXECUTED)));
    }

    private HapiSpec internalTransferToExistingNonMirrorAddressResultsInSuccess() {

        return defaultHapiSpec("internalTransferToExistingNonMirrorAddressResultsInSuccess")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> {
                    final var ecdsaKey = spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    allRunFor(
                            spec,
                            balanceSnapshot("autoCreatedSnapshot", SECP_256K1_SOURCE_KEY)
                                    .accountIsAlias(),
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
                                        .transfers(including(
                                                tinyBarsFromTo(INTERNAL_CALLER_CONTRACT, SECP_256K1_SOURCE_KEY, 1)))),
                        getAutoCreatedAccountBalance(SECP_256K1_SOURCE_KEY)
                                .hasTinyBars(changeFromSnapshot("autoCreatedSnapshot", 1)));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
