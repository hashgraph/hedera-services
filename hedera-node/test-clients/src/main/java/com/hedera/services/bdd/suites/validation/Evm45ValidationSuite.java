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

import static com.hedera.services.bdd.spec.HapiPropertySource.asContractIdWithEvmAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.mirrorAddrWith;
import static com.hedera.services.bdd.suites.contract.Utils.nonMirrorAddrWith;
import static com.hedera.services.bdd.suites.utils.contracts.SimpleBytesResult.bigIntResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.swirlds.common.utility.CommonUtils.unhex;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final String INNER_TXN = "innerTx";
    private static final String CALL_NON_EXISTING_FUNCTION = "callNonExisting";
    private static final String CALL_EXTERNAL_FUNCTION = "callExternalFunction";
    private static final String CALL_REVERT_WITH_REVERT_REASON_FUNCTION = "callRevertWithRevertReason";
    private static final String CALL_REVERT_WITHOUT_REVERT_REASON_FUNCTION = "callRevertWithoutRevertReason";
    private static final String TRANSFER_TO_FUNCTION = "transferTo";
    private static final String SEND_TO_FUNCTION = "sendTo";
    private static final String CALL_WITH_VALUE_TO_FUNCTION = "callWithValueTo";

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
                // EOA -calls-> NonExistingMirror, expect noop success
                directCallToNonExistingMirrorAddressResultsInSuccessfulNoOp(),
                // EOA -calls-> NonExistingNonMirror, expect noop success
                directCallToNonExistingNonMirrorAddressResultsInSuccessfulNoOp(),

                // Internal calls:
                // EOA -calls-> InternalCaller -calls-> NonExistingMirror, expect noop success
                internalCallToNonExistingMirrorAddressResultsInNoopSuccess(),
                // EOA -calls-> InternalCaller -calls-> ExistingMirror, expect successful call
                internalCallToExistingMirrorAddressResultsInSuccessfulCall(),
                // EOA -calls-> InternalCaller -calls-> NonExistingNonMirror, expect noop success
                internalCallToNonExistingNonMirrorAddressResultsInNoopSuccess(),
                // todo EOA -calls-> InternalCaller -calls-> ExistingNonMirror, expect successful call

                // Internal transfers:
                // EOA -calls-> InternalCaller -transfer-> NonExistingMirror, expect revert due to failed internal
                // transfer
                internalTransferToNonExistingMirrorAddressResultsInRevert()
                // todo EOA -calls-> InternalCaller -transfer-> ExistingMirror, expect ?
                // todo EOA -calls-> InternalCaller -transfer-> NonExistingNonMirror, expect ?
                // todo EOA -calls-> InternalCaller -transfer-> ExistingNonMirror, expect ?

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
                                .gas(24_000L)
                                .via("directCallToNonExistingMirrorAddress"))))
                .then(getTxnRecord("directCallToNonExistingMirrorAddress")
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith().gasUsed(21000))));
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
                                .gas(24_000L)
                                .via("directCallToNonExistingNonMirrorAddress"))))
                .then(getTxnRecord("directCallToNonExistingNonMirrorAddress")
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith().gasUsed(21000))));
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
                        .gas(25000)
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
                                .gas(50000)
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
                        .gas(25000)
                        .via(INNER_TXN))
                .then(getTxnRecord(INNER_TXN)
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(
                                        resultWith().createdContractIdsCount(0).gasUsed(24618))));
    }

    private HapiSpec internalTransferToNonExistingMirrorAddressResultsInRevert() {

        return defaultHapiSpec("internalTransferToNonExistingMirrorAddressResultsInRevert")
                .given(
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(contractCall(
                                INTERNAL_CALLER_CONTRACT, TRANSFER_TO_FUNCTION, mirrorAddrWith(new Random().nextLong()))
                        .gas(100000)
                        .via(INNER_TXN)
                        .hasKnownStatus(INVALID_SOLIDITY_ADDRESS))
                .then(
                        // todo is this all we expect? no contract function result?
                        getTxnRecord(INNER_TXN).hasPriority(recordWith().status(INVALID_SOLIDITY_ADDRESS)));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
