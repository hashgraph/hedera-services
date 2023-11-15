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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.swirlds.common.utility.CommonUtils.unhex;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// @HapiTestSuite
public class Evm45ValidationSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(Evm45ValidationSuite.class);
    private static final String NAME = "name";
    private static final String ERC_721_ABI = "ERC721ABI";
    private static final String NON_EXISTING_MIRROR_ADDRESS = "0000000000000000000000000000000000123456";
    private static final String NON_EXISTING_NON_MIRROR_ADDRESS = "1234561234561234561234561234568888123456";

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
                directCallToNonExistingLongZeroAddress()
                // call to 0.0.5435435 (mirror) what should happen?
                //                directCallToNonExistingEvmAddress(),
                //                internalCallToNonExistingLongZeroAddress(),§
                //                //                internalCallToExistingLongZeroAddress(),
                //                internalCallToNonExistingEvmAddress(),
                //                //                internalCallToExistingEvmAddress(),
                //
                //                internalTransferToNonExistingLongZeroAddress(),
                //                //                internalTransferToExistingLongZeroAddress(),
                //                internalTransferToNonExistingEvmAddress(),
                //                //                internalTransferToExistingEvmAddress(),
                //
                //                internalCallWithValueToNonExistingLongZeroAddress(),
                //                //                internalCallWithValueToExistingLongZeroAddress(),
                //                internalCallWithValueToNonExistingEvmAddress()
                //                //                internalCallWithValueToExistingEvmAddress()
                );
    }

    private HapiSpec directCallToNonExistingLongZeroAddress() {

        return defaultHapiSpec("directCallToNonExistingLongZeroAddress")
                .given(
                        withOpContext((spec, ctxLog) -> spec.registry()
                                .saveContractId(
                                        "nonExistingMirrorAddress",
                                        asContractIdWithEvmAddress(
                                                ByteString.copyFrom(unhex(NON_EXISTING_MIRROR_ADDRESS))))),
                        withOpContext((spec, ctxLog) -> spec.registry()
                                .saveContractId(
                                        "nonExistingNonMirrorAddress",
                                        asContractIdWithEvmAddress(
                                                ByteString.copyFrom(unhex(NON_EXISTING_NON_MIRROR_ADDRESS))))))
                .when(withOpContext((spec, ctxLog) -> allRunFor(
                        spec,
                        contractCallWithFunctionAbi(
                                        "nonExistingMirrorAddress",
                                        getABIFor(
                                                com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                NAME,
                                                ERC_721_ABI))
                                .gas(24_000L)
                                .via("directCallToNonExistingMirrorAddress"),
                        contractCallWithFunctionAbi(
                                        "nonExistingNonMirrorAddress",
                                        getABIFor(
                                                com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                NAME,
                                                ERC_721_ABI))
                                .gas(24_000L)
                                .via("directCallToNonExistingNonMirrorAddress"))))
                .then(
                        getTxnRecord("directCallToNonExistingMirrorAddress")
                                .hasPriority(TransactionRecordAsserts.recordWith()
                                        .status(ResponseCodeEnum.SUCCESS)
                                        .contractCallResult(ContractFnResultAsserts.resultWith()
                                                .gasUsed(21364))),
                        getTxnRecord("directCallToNonExistingNonMirrorAddress").logged());
    }

    //    private HapiSpec directCallToNonExistingEvmAddress() {
    //        final String SPONSOR = "autoCreateSponsor";
    //        return defaultHapiSpec("directCallToNonExistingEvmAddress")
    //                .given(cryptoCreate(SPONSOR).balance(10L * ONE_HBAR))
    //                .when()
    //                .then(
    //                        cryptoTransfer(tinyBarsFromToWithAlias(
    //                                SPONSOR, "0x" + shuffle("0B759e491B554D8b3fD3F2fe8Be4035E289b489C"), ONE_HBAR))
    //                        .hasKnownStatusFrom(SUCCESS)
    //                );
    //    }
    //
    //    private HapiSpec internalCallToNonExistingLongZeroAddress() {
    //        final var contract = "InternalCaller";
    //
    //        return defaultHapiSpec("internalCallToNonExistingLongZeroAddress")
    //                .given(uploadInitCode(contract), contractCreate(contract).balance(ONE_HBAR))
    //                .when()
    //                .then(contractCall(
    //                                contract,
    //                                "callContract",
    //                                idAsHeadlongAddress(AccountID.newBuilder()
    //                                        .setAccountNum(5644L)
    //                                        .build()))
    //                        .hasKnownStatus(SUCCESS));
    //    }
    //
    //    private HapiSpec internalCallToNonExistingEvmAddress() {
    //        final var contract = "InternalCaller";
    //
    //        return defaultHapiSpec("internalCallToNonExistingEvmAddress")
    //                .given(uploadInitCode(contract), contractCreate(contract).balance(ONE_HBAR))
    //                .when()
    //                .then(contractCall(
    //                                contract,
    //                                "callContract",
    //                                headlongFromHexed(shuffle("0B759e491B554D8b3fD3F2fe8Be4035E289b489C")))
    //                        .hasKnownStatus(SUCCESS));
    //    }
    //
    //    private HapiSpec internalTransferToNonExistingLongZeroAddress() {
    //        final var contract = "InternalCaller";
    //
    //        return defaultHapiSpec("internalTransferToNonExistingLongZeroAddress")
    //                .given(uploadInitCode(contract), contractCreate(contract).balance(ONE_HBAR))
    //                .when()
    //                .then(contractCall(
    //                                contract,
    //                                "callTransfer",
    //                                idAsHeadlongAddress(AccountID.newBuilder()
    //                                        .setAccountNum(5644L)
    //                                        .build()))
    //                        .hasKnownStatus(SUCCESS));
    //    }
    //
    //    private HapiSpec internalTransferToNonExistingEvmAddress() {
    //        final var contract = "InternalCaller";
    //
    //        return defaultHapiSpec("internalTransferToNonExistingEvmAddress")
    //                .given(uploadInitCode(contract), contractCreate(contract).balance(ONE_HBAR))
    //                .when()
    //                .then(contractCall(
    //                                contract,
    //                                "callTransfer",
    //                                headlongFromHexed(shuffle("0B759e491B554D8b3fD3F2fe8Be4035E289b489C")))
    //                        .hasKnownStatus(SUCCESS));
    //    }
    //
    //    private HapiSpec internalCallWithValueToNonExistingLongZeroAddress() {
    //        final var contract = "InternalCaller";
    //
    //        return defaultHapiSpec("internalCallWithValueToNonExistingLongZeroAddress")
    //                .given(uploadInitCode(contract), contractCreate(contract).balance(ONE_HBAR))
    //                .when()
    //                .then(contractCall(
    //                                contract,
    //                                "callWithValue",
    //                                idAsHeadlongAddress(AccountID.newBuilder()
    //                                        .setAccountNum(5644L)
    //                                        .build()))
    //                        .hasKnownStatus(SUCCESS));
    //    }
    //
    //    private HapiSpec internalCallWithValueToNonExistingEvmAddress() {
    //        final var contract = "InternalCaller";
    //
    //        return defaultHapiSpec("internalCallWithValueToNonExistingEvmAddress")
    //                .given(uploadInitCode(contract), contractCreate(contract).balance(ONE_HBAR))
    //                .when()
    //                .then(contractCall(
    //                                contract,
    //                                "callWithValue",
    //                                headlongFromHexed(shuffle("0B759e491B554D8b3fD3F2fe8Be4035E289b489C")))
    //                        .hasKnownStatus(SUCCESS));
    //    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
