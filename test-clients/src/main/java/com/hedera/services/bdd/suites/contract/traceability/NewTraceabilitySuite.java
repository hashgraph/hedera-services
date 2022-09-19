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
package com.hedera.services.bdd.suites.contract.traceability;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilStateChange.stateChangesToGrpc;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asSolidityAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractBytecodeUnhexed;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.getResourcePath;
import static com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileSuite.getNestedContractAddress;
import static com.hedera.services.stream.proto.ContractActionType.CALL;
import static com.hedera.services.stream.proto.ContractActionType.CREATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.StateChange;
import com.hedera.services.bdd.spec.assertions.StorageChange;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.verification.traceability.ExpectedSidecar;
import com.hedera.services.bdd.spec.verification.traceability.SidecarWatcher;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.Utils.FunctionType;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.stream.proto.CallOperationType;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.ethereum.core.CallTransaction;

public class NewTraceabilitySuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(NewTraceabilitySuite.class);
    private static final String RECORD_STREAM_FOLDER_PATH_PROPERTY_KEY = "recordStream.path";

    private static SidecarWatcher sidecarWatcher;
    private static final ByteString EMPTY = ByteStringUtils.wrapUnsafely(new byte[0]);
    private static final String TRACEABILITY = "Traceability";
    private static final String REVERTING_CONTRACT = "RevertingContract";
    private static final String FIRST_CREATE_TXN = "FirstCreateTxn";
    private static final String SECOND_CREATE_TXN = "SecondCreateTxn";
    private static final String THIRD_CREATE_TXN = "ThirdCreateTxn";
    private static final String SECOND = "B";
    private static final String THIRD = "C";
    private static final String TRACEABILITY_TXN = "nestedtxn";
    private static final String GET_ZERO_SLOT = "getSlot0";
    private static final String GET_FIRST_SLOT = "getSlot1";
    private static final String GET_SECOND_SLOT = "getSlot2";
    private static final String SET_ZERO_SLOT = "setSlot0";
    private static final String SET_FIRST_SLOT = "setSlot1";
    private static final String SET_SECOND_SLOT = "setSlot2";

    public static void main(String... args) {
        new NewTraceabilitySuite().runSuiteSync();
    }

    @SuppressWarnings("java:S5960")
    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        try {
            initialize();
        } catch (Exception e) {
            log.warn("An exception occurred initializing watch service", e);
            return List.of(
                    defaultHapiSpec("initialize")
                            .given()
                            .when()
                            .then(
                                    assertionsHold(
                                            (spec, opLog) ->
                                                    fail(
                                                            "Watch service couldn't be"
                                                                    + " initialized."))));
        }
        return List.of(
                traceabilityE2EScenario1(),
                traceabilityE2EScenario17(),
                traceabilityE2EScenario18(),
                traceabilityE2EScenario21(),
                vanillaBytecodeSidecar(),
                vanillaBytecodeSidecar2(),
                assertSidecars());
    }

    private HapiApiSpec traceabilityE2EScenario1() {
        return defaultHapiSpec("traceabilityE2EScenario1")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(TRACEABILITY, 55, 2, 2).via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGasUsed(68492)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, TRACEABILITY, TRACEABILITY, 55, 2, 2),
                        contractCustomCreate(TRACEABILITY, SECOND, 0, 0, 12).via(SECOND_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                SECOND_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(12))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        SECOND_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN, TRACEABILITY + SECOND, TRACEABILITY, 0, 0, 12),
                        contractCustomCreate(TRACEABILITY, THIRD, 0, 11, 0).via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(11)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        THIRD_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN, TRACEABILITY + THIRD, TRACEABILITY, 0, 11, 0))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario1",
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "B", spec),
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "C", spec))
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(55))),
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(12),
                                                                formattedAssertionValue(143))),
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(11),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(33979)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "B",
                                                                                                        spec)),
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "C",
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(963018)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setCallDepth(1)
                                                                        .setGas(960236)
                                                                        .setGasUsed(5324)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(952309)
                                                                        .setGasUsed(2315)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        12)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_SECOND_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(949543)
                                                                        .setGasUsed(3180)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_SECOND_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        143)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(946053)
                                                                        .setGasUsed(5778)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressGetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(928026)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(939987)
                                                                        .setGasUsed(1501)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressSetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(924301)
                                                                        .setGasUsed(423)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_ZERO_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(938149)
                                                                        .setGasUsed(3345)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressGetSlot1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(922684)
                                                                        .setGasUsed(2391)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        11)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_FIRST_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(934470)
                                                                        .setGasUsed(4235)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressSetSlot1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(918936)
                                                                        .setGasUsed(3224)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build())))));
    }

    private HapiApiSpec traceabilityE2EScenario17() {
        return defaultHapiSpec("traceabilityE2EScenario17")
                .given(
                        uploadInitCode(REVERTING_CONTRACT),
                        contractCreate(REVERTING_CONTRACT, 6).via(FIRST_CREATE_TXN),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                REVERTING_CONTRACT))
                                                                        .setGasUsed(345)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, REVERTING_CONTRACT, REVERTING_CONTRACT, 6))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                REVERTING_CONTRACT,
                                                                "createContract",
                                                                BigInteger.valueOf(4))
                                                        .gas(1_000_000)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(32583)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                REVERTING_CONTRACT))
                                                                        .setRevertReason(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        REVERTING_CONTRACT,
                                                                                        "createContract",
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        4)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                REVERTING_CONTRACT))
                                                                        .setGas(931868)
                                                                        .setCallDepth(1)
                                                                        .setGasUsed(201)
                                                                        .setRevertReason(EMPTY)
                                                                        .build())))));
    }

    private HapiApiSpec traceabilityE2EScenario18() {
        return defaultHapiSpec("traceabilityE2EScenario18")
                .given(uploadInitCode(REVERTING_CONTRACT))
                .when(
                        contractCreate(REVERTING_CONTRACT, 6)
                                .via(FIRST_CREATE_TXN)
                                .gas(53050)
                                .hasKnownStatus(INSUFFICIENT_GAS))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(50)
                                                                        .setGasUsed(50)
                                                                        .setError(
                                                                                ByteString
                                                                                        .copyFromUtf8(
                                                                                                INSUFFICIENT_GAS
                                                                                                        .name()))
                                                                        .build())))),
                        expectFailedContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, REVERTING_CONTRACT, 6));
    }

    private HapiApiSpec traceabilityE2EScenario21() {
        return defaultHapiSpec("traceabilityE2EScenario21")
                .given(
                        uploadInitCode(REVERTING_CONTRACT),
                        contractCreate(REVERTING_CONTRACT, 6).via(FIRST_CREATE_TXN),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                REVERTING_CONTRACT))
                                                                        .setGasUsed(345)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, REVERTING_CONTRACT, REVERTING_CONTRACT, 6))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                REVERTING_CONTRACT,
                                                                "callingWrongAddress")
                                                        .gas(1_000_000)
                                                        .hasKnownStatus(INVALID_SOLIDITY_ADDRESS)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(979000)
                                                                        .setError(
                                                                                ByteString
                                                                                        .copyFromUtf8(
                                                                                                INVALID_SOLIDITY_ADDRESS
                                                                                                        .name()))
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                REVERTING_CONTRACT))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        REVERTING_CONTRACT,
                                                                                        "callingWrongAddress"))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                REVERTING_CONTRACT))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallDepth(1)
                                                                        .setGas(978487)
                                                                        .setError(
                                                                                ByteString
                                                                                        .copyFromUtf8(
                                                                                                INVALID_SOLIDITY_ADDRESS
                                                                                                        .name()))
                                                                        .setInvalidSolidityAddress(
                                                                                ByteString.copyFrom(
                                                                                        asSolidityAddress(
                                                                                                0,
                                                                                                0,
                                                                                                0)))
                                                                        .build())))));
    }

    private HapiApiSpec vanillaBytecodeSidecar() {
        final var EMPTY_CONSTRUCTOR_CONTRACT = "EmptyConstructor";
        final var vanillaBytecodeSidecar = "vanillaBytecodeSidecar";
        final var firstTxn = "firstTxn";
        return defaultHapiSpec(vanillaBytecodeSidecar)
                .given(uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when(
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .hasKnownStatus(SUCCESS)
                                .via(firstTxn))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final HapiGetTxnRecord txnRecord = getTxnRecord(firstTxn);
                                    allRunFor(
                                            spec,
                                            txnRecord,
                                            expectContractActionSidecarFor(
                                                    firstTxn,
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CREATE)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CREATE)
                                                                    .setCallingAccount(
                                                                            spec.registry()
                                                                                    .getAccountID(
                                                                                            GENESIS))
                                                                    .setRecipientContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            EMPTY_CONSTRUCTOR_CONTRACT))
                                                                    .setGas(197000)
                                                                    .setGasUsed(66)
                                                                    .setOutput(EMPTY)
                                                                    .build())));
                                }),
                        expectContractBytecodeSidecarFor(
                                firstTxn, EMPTY_CONSTRUCTOR_CONTRACT, EMPTY_CONSTRUCTOR_CONTRACT));
    }

    private HapiApiSpec vanillaBytecodeSidecar2() {
        final var contract = "CreateTrivial";
        final String trivialCreate = "vanillaBytecodeSidecar2";
        final var firstTxn = "firstTxn";
        return defaultHapiSpec(trivialCreate)
                .given(uploadInitCode(contract))
                .when(contractCreate(contract).via(firstTxn))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final HapiGetTxnRecord txnRecord = getTxnRecord(firstTxn);
                                    allRunFor(
                                            spec,
                                            txnRecord,
                                            expectContractActionSidecarFor(
                                                    firstTxn,
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CREATE)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CREATE)
                                                                    .setCallingAccount(
                                                                            spec.registry()
                                                                                    .getAccountID(
                                                                                            GENESIS))
                                                                    .setRecipientContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            contract))
                                                                    .setGas(197000)
                                                                    .setGasUsed(214)
                                                                    .setOutput(EMPTY)
                                                                    .build())));
                                }),
                        expectContractBytecodeSidecarFor(firstTxn, contract, contract));
    }

    @SuppressWarnings("java:S5960")
    private HapiApiSpec assertSidecars() {
        return defaultHapiSpec("assertSidecars")
                .given(
                        // send a dummy transaction to trigger externalization of last sidecars
                        cryptoCreate("externalizeFinalSidecars").delayBy(2000))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    sidecarWatcher.waitUntilFinished();
                                    sidecarWatcher.tearDown();
                                }))
                .then(
                        assertionsHold(
                                (spec, assertLog) -> {
                                    assertTrue(
                                            sidecarWatcher.thereAreNoMismatchedSidecars(),
                                            sidecarWatcher.getErrors());
                                    assertTrue(
                                            sidecarWatcher.thereAreNoPendingSidecars(),
                                            "There are some sidecars that have not been yet"
                                                    + " externalized in the sidecar files after all"
                                                    + " specs.");
                                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    private CustomSpecAssert expectContractActionSidecarFor(
            String txnName, List<ContractAction> actions) {
        return withOpContext(
                (spec, opLog) -> {
                    final var txnRecord = getTxnRecord(txnName);
                    allRunFor(spec, txnRecord);
                    final var consensusTimestamp =
                            txnRecord.getResponseRecord().getConsensusTimestamp();
                    sidecarWatcher.addExpectedSidecar(
                            new ExpectedSidecar(
                                    spec.getName(),
                                    TransactionSidecarRecord.newBuilder()
                                            .setConsensusTimestamp(consensusTimestamp)
                                            .setActions(
                                                    ContractActions.newBuilder()
                                                            .addAllContractActions(actions)
                                                            .build())
                                            .build()));
                });
    }

    private CustomSpecAssert expectContractStateChangesSidecarFor(
            final String txnName, final List<StateChange> stateChanges) {
        return withOpContext(
                (spec, opLog) -> {
                    final var txnRecord = getTxnRecord(txnName);
                    allRunFor(spec, txnRecord);
                    final var consensusTimestamp =
                            txnRecord.getResponseRecord().getConsensusTimestamp();
                    sidecarWatcher.addExpectedSidecar(
                            new ExpectedSidecar(
                                    spec.getName(),
                                    TransactionSidecarRecord.newBuilder()
                                            .setConsensusTimestamp(consensusTimestamp)
                                            .setStateChanges(
                                                    ContractStateChanges.newBuilder()
                                                            .addAllContractStateChanges(
                                                                    stateChangesToGrpc(
                                                                            stateChanges, spec))
                                                            .build())
                                            .build()));
                });
    }

    private CustomSpecAssert expectContractBytecodeSidecarFor(
            final String contractCreateTxn,
            final String contractName,
            final String binFileName,
            final Object... constructorArgs) {
        return withOpContext(
                (spec, opLog) -> {
                    final var txnRecord = getTxnRecord(contractCreateTxn);
                    final String runtimeBytecode = "runtimeBytecode";
                    final var contractBytecode =
                            getContractBytecode(contractName).saveResultTo(runtimeBytecode);
                    allRunFor(spec, txnRecord, contractBytecode);
                    final var consensusTimestamp =
                            txnRecord.getResponseRecord().getConsensusTimestamp();
                    final var initCode = getInitcode(binFileName, constructorArgs);
                    sidecarWatcher.addExpectedSidecar(
                            new ExpectedSidecar(
                                    spec.getName(),
                                    TransactionSidecarRecord.newBuilder()
                                            .setConsensusTimestamp(consensusTimestamp)
                                            .setBytecode(
                                                    ContractBytecode.newBuilder()
                                                            .setContractId(
                                                                    txnRecord
                                                                            .getResponseRecord()
                                                                            .getContractCreateResult()
                                                                            .getContractID())
                                                            .setInitcode(initCode)
                                                            .setRuntimeBytecode(
                                                                    ByteString.copyFrom(
                                                                            spec.registry()
                                                                                    .getBytes(
                                                                                            runtimeBytecode)))
                                                            .build())
                                            .build()));
                });
    }

    private CustomSpecAssert expectFailedContractBytecodeSidecarFor(
            final String contractCreateTxn,
            final String binFileName,
            final Object... constructorArgs) {
        return withOpContext(
                (spec, opLog) -> {
                    final var txnRecord = getTxnRecord(contractCreateTxn);
                    allRunFor(spec, txnRecord);
                    final var consensusTimestamp =
                            txnRecord.getResponseRecord().getConsensusTimestamp();
                    final var initCode = getInitcode(binFileName, constructorArgs);
                    sidecarWatcher.addExpectedSidecar(
                            new ExpectedSidecar(
                                    spec.getName(),
                                    TransactionSidecarRecord.newBuilder()
                                            .setConsensusTimestamp(consensusTimestamp)
                                            .setBytecode(
                                                    ContractBytecode.newBuilder()
                                                            .setInitcode(initCode)
                                                            .build())
                                            .build()));
                });
    }

    private ByteString getInitcode(final String binFileName, final Object... constructorArgs) {
        final var initCode = extractBytecodeUnhexed(getResourcePath(binFileName, ".bin"));
        final var params =
                constructorArgs.length == 0
                        ? new byte[] {}
                        : CallTransaction.Function.fromJsonInterface(
                                        getABIFor(
                                                FunctionType.CONSTRUCTOR,
                                                StringUtils.EMPTY,
                                                binFileName))
                                .encodeArguments(constructorArgs);
        return initCode.concat(ByteStringUtils.wrapUnsafely(params));
    }

    private static void initialize() throws Exception {
        final var recordStreamFolderPath =
                HapiApiSpec.isRunningInCi()
                        ? HapiApiSpec.ciPropOverrides().get(RECORD_STREAM_FOLDER_PATH_PROPERTY_KEY)
                        : HapiSpecSetup.getDefaultPropertySource()
                                .get(RECORD_STREAM_FOLDER_PATH_PROPERTY_KEY);
        sidecarWatcher = new SidecarWatcher(Paths.get(recordStreamFolderPath));
        sidecarWatcher.watch();
    }

    private ByteString encodeFunctionCall(
            final String contractName, final String functionName, final Object... args) {
        return ByteStringUtils.wrapUnsafely(
                Function.fromJson(getABIFor(FunctionType.FUNCTION, functionName, contractName))
                        .encodeCallWithArgs(args)
                        .array());
    }

    private byte[] encodeTuple(final String argumentsSignature, final Object... actualArguments) {
        return TupleType.parse(argumentsSignature).encode(Tuple.of(actualArguments)).array();
    }

    private ByteString uint256ReturnWithValue(final BigInteger value) {
        return ByteStringUtils.wrapUnsafely(encodeTuple("(uint256)", value));
    }

    private Address hexedSolidityAddressToHeadlongAddress(final String hexedSolidityAddress) {
        return Address.wrap(Address.toChecksumAddress("0x" + hexedSolidityAddress));
    }

    private ByteString formattedAssertionValue(final long value) {
        return ByteString.copyFrom(
                Bytes.wrap(UInt256.valueOf(value)).trimLeadingZeros().toArrayUnsafe());
    }
}
