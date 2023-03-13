/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContract;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.stripSelector;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilStateChange.stateChangesToGrpc;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.contract.Utils.*;
import static com.hedera.services.bdd.suites.contract.opcodes.Create2OperationSuite.*;
import static com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileSuite.getNestedContractAddress;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.*;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hedera.services.stream.proto.ContractActionType.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.swirlds.common.utility.CommonUtils.hex;
import static org.hyperledger.besu.crypto.Hash.keccak256;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.google.common.hash.Hashing;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.StateChange;
import com.hedera.services.bdd.spec.assertions.StorageChange;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.verification.traceability.ExpectedSidecar;
import com.hedera.services.bdd.spec.verification.traceability.SidecarWatcher;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.stream.proto.*;
import com.hederahashgraph.api.proto.java.*;
import com.swirlds.common.utility.CommonUtils;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class TraceabilitySuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TraceabilitySuite.class);
    private static final String RECORD_STREAM_FOLDER_PATH_PROPERTY_KEY = "recordStream.path";

    private static SidecarWatcher sidecarWatcher;
    private static final ByteString EMPTY = ByteStringUtils.wrapUnsafely(new byte[0]);
    private static final ByteString CALL_CODE_INPUT_SUFFIX =
            ByteStringUtils.wrapUnsafely(new byte[28]);
    private static final String TRACEABILITY = "Traceability";
    private static final String TRACEABILITY_CALLCODE = "TraceabilityCallcode";
    private static final String REVERTING_CONTRACT = "RevertingContract";
    private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";
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
    private static final String DELEGATE_CALL_ADDRESS_GET_SLOT_2 = "delegateCallAddressGetSlot2";
    private static final String AUTO_ACCOUNT_TXN = "autoAccount";
    private static final String CHAIN_ID_PROPERTY = "contracts.chainId";
    private static final String LAZY_CREATE_PROPERTY = "lazyCreation.enabled";
    private static final String RUNTIME_CODE = "runtimeBytecode";
    public static final String SIDECARS_PROP = "contracts.sidecars";

    public static void main(final String... args) {
        new TraceabilitySuite().runSuiteSync();
    }

    @SuppressWarnings("java:S5960")
    @Override
    public List<HapiSpec> getSpecsInSuite() {
        try {
            initialize();
        } catch (final Exception e) {
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
        return Stream.of(
                        traceabilityE2EScenario1(),
                        traceabilityE2EScenario2(),
                        traceabilityE2EScenario3(),
                        traceabilityE2EScenario4(),
                        traceabilityE2EScenario5(),
                        traceabilityE2EScenario6(),
                        traceabilityE2EScenario7(),
                        traceabilityE2EScenario8(),
                        traceabilityE2EScenario9(),
                        traceabilityE2EScenario10(),
                        traceabilityE2EScenario11(),
                        traceabilityE2EScenario12(),
                        traceabilityE2EScenario13(),
                        traceabilityE2EScenario14(),
                        traceabilityE2EScenario15(),
                        traceabilityE2EScenario16(),
                        traceabilityE2EScenario17(),
                        traceabilityE2EScenario18(),
                        traceabilityE2EScenario19(),
                        traceabilityE2EScenario20(),
                        traceabilityE2EScenario21(),
                        vanillaBytecodeSidecar(),
                        vanillaBytecodeSidecar2(),
                        actionsShowPropagatedRevert(),
                        ethereumLazyCreateExportsExpectedSidecars(),
                        hollowAccountCreate2MergeExportsExpectedSidecars(),
                        assertSidecars())
                .toList();
    }

    private HapiSpec traceabilityE2EScenario1() {
        return defaultHapiSpec("traceabilityE2EScenario1")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(
                                        TRACEABILITY,
                                        BigInteger.valueOf(55),
                                        BigInteger.TWO,
                                        BigInteger.TWO)
                                .via(FIRST_CREATE_TXN),
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
                                FIRST_CREATE_TXN,
                                TRACEABILITY,
                                TRACEABILITY,
                                BigInteger.valueOf(55),
                                BigInteger.TWO,
                                BigInteger.TWO),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        SECOND,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO,
                                        BigInteger.valueOf(12))
                                .via(SECOND_CREATE_TXN),
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
                                SECOND_CREATE_TXN,
                                TRACEABILITY + SECOND,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ZERO,
                                BigInteger.valueOf(12)),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        THIRD,
                                        BigInteger.ZERO,
                                        BigInteger.valueOf(11),
                                        BigInteger.ZERO)
                                .via(THIRD_CREATE_TXN),
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
                                THIRD_CREATE_TXN,
                                TRACEABILITY + THIRD,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.valueOf(11),
                                BigInteger.ZERO))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario1",
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "B",
                                                                                spec)),
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "C",
                                                                                spec)))
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

    private HapiSpec traceabilityE2EScenario2() {
        return defaultHapiSpec("traceabilityE2EScenario2")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(
                                        TRACEABILITY,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO)
                                .via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
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
                                                                formattedAssertionValue(0))))),
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
                                                                        .setGasUsed(8792)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN,
                                TRACEABILITY,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ZERO,
                                BigInteger.ZERO),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        SECOND,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO,
                                        BigInteger.valueOf(99))
                                .via(SECOND_CREATE_TXN),
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
                                                                formattedAssertionValue(99))))),
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
                                SECOND_CREATE_TXN,
                                TRACEABILITY + SECOND,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ZERO,
                                BigInteger.valueOf(99)),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        THIRD,
                                        BigInteger.ZERO,
                                        BigInteger.valueOf(88),
                                        BigInteger.ZERO)
                                .via(THIRD_CREATE_TXN),
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
                                                                formattedAssertionValue(88)),
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
                                THIRD_CREATE_TXN,
                                TRACEABILITY + THIRD,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.valueOf(88),
                                BigInteger.ZERO))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario2",
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "B",
                                                                                spec)),
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "C",
                                                                                spec)))
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
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55))),
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(100)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(99),
                                                                formattedAssertionValue(143))))),
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
                                                                        .setGasUsed(70255)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario2",
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
                                                                        .setGas(963083)
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
                                                                        .setCallDepth(1)
                                                                        .setGas(960302)
                                                                        .setGasUsed(22424)
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
                                                                        .setGas(937875)
                                                                        .setGasUsed(5811)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressGetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + SECOND,
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
                                                                        .setGas(919912)
                                                                        .setGasUsed(2315)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        99)))
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
                                                                        .setGas(931783)
                                                                        .setGasUsed(4235)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressSetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + SECOND,
                                                                                                        spec)),
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
                                                                        .setGas(916248)
                                                                        .setGasUsed(3180)
                                                                        .setCallDepth(2)
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
                                                                        .setGas(927248)
                                                                        .setGasUsed(5819)
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
                                                                                        "delegateCallAddressGetSlot0",
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
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(909474)
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
                                                                        .setGas(921145)
                                                                        .setGasUsed(21353)
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
                                                                                        "delegateCallAddressSetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        100)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(905801)
                                                                        .setGasUsed(20323)
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
                                                                                                        100)))
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
                                                                        .setGas(899766)
                                                                        .setGasUsed(3387)
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
                                                                                        "delegateCallAddressGetSlot1",
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
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(884859)
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
                                                                                                        0)))
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
                                                                        .setGas(896045)
                                                                        .setGasUsed(1476)
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
                                                                                        "delegateCallAddressSetSlot1",
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
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(881071)
                                                                        .setGasUsed(424)
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

    private HapiSpec traceabilityE2EScenario3() {
        return defaultHapiSpec("traceabilityE2EScenario3")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(
                                        TRACEABILITY,
                                        BigInteger.valueOf(55),
                                        BigInteger.TWO,
                                        BigInteger.TWO)
                                .via(FIRST_CREATE_TXN),
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
                                FIRST_CREATE_TXN,
                                TRACEABILITY,
                                TRACEABILITY,
                                BigInteger.valueOf(55),
                                BigInteger.TWO,
                                BigInteger.TWO),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        SECOND,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO,
                                        BigInteger.valueOf(12))
                                .via(SECOND_CREATE_TXN),
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
                                SECOND_CREATE_TXN,
                                TRACEABILITY + SECOND,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ZERO,
                                BigInteger.valueOf(12)),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        THIRD,
                                        BigInteger.ZERO,
                                        BigInteger.valueOf(11),
                                        BigInteger.ZERO)
                                .via(THIRD_CREATE_TXN),
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
                                THIRD_CREATE_TXN,
                                TRACEABILITY + THIRD,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.valueOf(11),
                                BigInteger.ZERO))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario3",
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "B",
                                                                                spec)),
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "C",
                                                                                spec)))
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
                                                                formattedAssertionValue(55252)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(524))),
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(54)),
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
                                                                        .setGasUsed(57011)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario3",
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
                                                                        .setGas(963059)
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
                                                                        .setGas(960277)
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
                                                                                                        55252)))
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
                                                                        .setGas(954683)
                                                                        .setGasUsed(5810)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        DELEGATE_CALL_ADDRESS_GET_SLOT_2,
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
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(936458)
                                                                        .setGasUsed(2315)
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
                                                                                                        2)))
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
                                                                        .setGas(948592)
                                                                        .setGasUsed(4209)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "delegateCallAddressSetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        524)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(932820)
                                                                        .setGasUsed(3180)
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
                                                                                        SET_SECOND_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        524)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(941399)
                                                                        .setGasUsed(3278)
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
                                                                        .setGas(925906)
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
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(937474)
                                                                        .setGasUsed(21401)
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
                                                                                                        54)))
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
                                                                        .setGas(921827)
                                                                        .setGasUsed(20323)
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
                                                                                                        54)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(915805)
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
                                                                        .setGas(900689)
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
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(911814)
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
                                                                        .setGas(896634)
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

    private HapiSpec traceabilityE2EScenario4() {
        return defaultHapiSpec("traceabilityE2EScenario4")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(
                                        TRACEABILITY,
                                        BigInteger.TWO,
                                        BigInteger.valueOf(3),
                                        BigInteger.valueOf(4))
                                .via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(3)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4))))),
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
                                FIRST_CREATE_TXN,
                                TRACEABILITY,
                                TRACEABILITY,
                                BigInteger.TWO,
                                BigInteger.valueOf(3),
                                BigInteger.valueOf(4)),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        SECOND,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO)
                                .via(SECOND_CREATE_TXN),
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
                                                                formattedAssertionValue(0))))),
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
                                                                        .setGasUsed(8792)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN,
                                TRACEABILITY + SECOND,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ZERO,
                                BigInteger.ZERO),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        THIRD,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO)
                                .via(THIRD_CREATE_TXN),
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
                                                                formattedAssertionValue(0)),
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
                                                                        .setGasUsed(8792)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN,
                                TRACEABILITY + THIRD,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ZERO,
                                BigInteger.ZERO))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario4",
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "B",
                                                                                spec)),
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "C",
                                                                                spec)))
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(3),
                                                                formattedAssertionValue(4)),
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(4))))),
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
                                                                        .setGasUsed(23913)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario4",
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
                                                                        .setGas(963038)
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
                                                                                                        2)))
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
                                                                        .setGas(960256)
                                                                        .setGasUsed(3223)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_ZERO_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        3)))
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
                                                                        .setGas(956871)
                                                                        .setGasUsed(2391)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        3)))
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
                                                                        .setCallDepth(1)
                                                                        .setGas(954049)
                                                                        .setGasUsed(3224)
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
                                                                                                        4)))
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
                                                                        .setGas(950522)
                                                                        .setGasUsed(5810)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        DELEGATE_CALL_ADDRESS_GET_SLOT_2,
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + SECOND,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(932362)
                                                                        .setGasUsed(2315)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        4)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_SECOND_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(944118)
                                                                        .setGasUsed(3953)
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
                                                                                        "delegateCallAddressSetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(925954)
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
                                                                                                        55)))
                                                                        .build())))));
    }

    private HapiSpec traceabilityE2EScenario5() {
        return defaultHapiSpec("traceabilityE2EScenario5")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(
                                        TRACEABILITY,
                                        BigInteger.valueOf(55),
                                        BigInteger.TWO,
                                        BigInteger.TWO)
                                .via(FIRST_CREATE_TXN),
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
                                FIRST_CREATE_TXN,
                                TRACEABILITY,
                                TRACEABILITY,
                                BigInteger.valueOf(55),
                                BigInteger.TWO,
                                BigInteger.TWO),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        SECOND,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO,
                                        BigInteger.valueOf(12))
                                .via(SECOND_CREATE_TXN),
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
                                SECOND_CREATE_TXN,
                                TRACEABILITY + SECOND,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ZERO,
                                BigInteger.valueOf(12)),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        THIRD,
                                        BigInteger.valueOf(4),
                                        BigInteger.ONE,
                                        BigInteger.ZERO)
                                .via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(1)),
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
                                                                        .setGasUsed(48592)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN,
                                TRACEABILITY + THIRD,
                                TRACEABILITY,
                                BigInteger.valueOf(4),
                                BigInteger.ONE,
                                BigInteger.ZERO))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario5",
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "B",
                                                                                spec)),
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "C",
                                                                                spec)))
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
                                                                formattedAssertionValue(55252))),
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(12),
                                                                formattedAssertionValue(524))),
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4)),
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(1))))),
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
                                                                        .setGasUsed(27376)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario5",
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
                                                                        .setGas(963081)
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
                                                                        .setGas(960300)
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
                                                                                                        55252)))
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
                                                                        .setGas(952373)
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
                                                                        .setGas(949607)
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
                                                                                                        524)))
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
                                                                        .setGas(946117)
                                                                        .setGasUsed(5777)
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
                                                                                        "staticCallAddressGetSlot0",
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
                                                                                        .OP_STATICCALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(928090)
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
                                                                                                        4)))
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
                                                                        .setGas(940069)
                                                                        .setGasUsed(3320)
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
                                                                                        "staticCallAddressGetSlot1",
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
                                                                                        .OP_STATICCALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(924598)
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
                                                                                                        1)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_FIRST_SLOT))
                                                                        .build())))));
    }

    private HapiSpec traceabilityE2EScenario6() {
        return defaultHapiSpec("traceabilityE2EScenario6")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(
                                        TRACEABILITY,
                                        BigInteger.TWO,
                                        BigInteger.valueOf(3),
                                        BigInteger.valueOf(4))
                                .via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(3)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4))))),
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
                                FIRST_CREATE_TXN,
                                TRACEABILITY,
                                TRACEABILITY,
                                BigInteger.TWO,
                                BigInteger.valueOf(3),
                                BigInteger.valueOf(4)),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        SECOND,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO,
                                        BigInteger.valueOf(3))
                                .via(SECOND_CREATE_TXN),
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
                                                                formattedAssertionValue(3))))),
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
                                SECOND_CREATE_TXN,
                                TRACEABILITY + SECOND,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ZERO,
                                BigInteger.valueOf(3)),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        THIRD,
                                        BigInteger.ZERO,
                                        BigInteger.ONE,
                                        BigInteger.ZERO)
                                .via(THIRD_CREATE_TXN),
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
                                                                formattedAssertionValue(1)),
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
                                THIRD_CREATE_TXN,
                                TRACEABILITY + THIRD,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ONE,
                                BigInteger.ZERO))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario6",
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "B",
                                                                                spec)),
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "C",
                                                                                spec)))
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
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(3),
                                                                formattedAssertionValue(4)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(4),
                                                                formattedAssertionValue(5))),
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(1))))),
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
                                                                        .setGasUsed(29910)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario6",
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
                                                                        .setGas(963082)
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
                                                                                                        2)))
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
                                                                        .setGas(960301)
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
                                                                                                        4)))
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
                                                                        .setGas(954706)
                                                                        .setGasUsed(5810)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        DELEGATE_CALL_ADDRESS_GET_SLOT_2,
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + SECOND,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(936481)
                                                                        .setGasUsed(2315)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        4)))
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
                                                                        .setGas(948616)
                                                                        .setGasUsed(4209)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "delegateCallAddressSetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + SECOND,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        5)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(932843)
                                                                        .setGasUsed(3180)
                                                                        .setCallDepth(2)
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
                                                                                                        5)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(943883)
                                                                        .setGasUsed(5777)
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
                                                                                        "staticCallAddressGetSlot0",
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
                                                                                        .OP_STATICCALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(925891)
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
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(937591)
                                                                        .setGasUsed(3320)
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
                                                                                        "staticCallAddressGetSlot1",
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
                                                                                        .OP_STATICCALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(922159)
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
                                                                                                        1)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_FIRST_SLOT))
                                                                        .build())))));
    }

    private HapiSpec traceabilityE2EScenario7() {
        return defaultHapiSpec("traceabilityE2EScenario7")
                .given(
                        uploadInitCode(TRACEABILITY_CALLCODE),
                        contractCreate(
                                        TRACEABILITY_CALLCODE,
                                        BigInteger.valueOf(55),
                                        BigInteger.TWO,
                                        BigInteger.TWO)
                                .via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE)
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
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGasUsed(67632)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN,
                                TRACEABILITY_CALLCODE,
                                TRACEABILITY_CALLCODE,
                                BigInteger.valueOf(55),
                                BigInteger.TWO,
                                BigInteger.TWO),
                        contractCustomCreate(
                                        TRACEABILITY_CALLCODE,
                                        SECOND,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO,
                                        BigInteger.valueOf(12))
                                .via(SECOND_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                SECOND_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE + SECOND)
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
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setGasUsed(27832)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN,
                                TRACEABILITY_CALLCODE + SECOND,
                                TRACEABILITY_CALLCODE,
                                BigInteger.ZERO,
                                BigInteger.ZERO,
                                BigInteger.valueOf(12)),
                        contractCustomCreate(
                                        TRACEABILITY_CALLCODE,
                                        THIRD,
                                        BigInteger.valueOf(4),
                                        BigInteger.ONE,
                                        BigInteger.ZERO)
                                .via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(1)),
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
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + THIRD))
                                                                        .setGasUsed(47732)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN,
                                TRACEABILITY_CALLCODE + THIRD,
                                TRACEABILITY_CALLCODE,
                                BigInteger.valueOf(4),
                                BigInteger.ONE,
                                BigInteger.ZERO))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY_CALLCODE,
                                                                "eetScenario7",
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY_CALLCODE
                                                                                        + "B",
                                                                                spec)),
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY_CALLCODE
                                                                                        + "C",
                                                                                spec)))
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(55252))),
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(54)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(12),
                                                                formattedAssertionValue(524))))),
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
                                                                        .setGasUsed(51483)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "eetScenario7",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + "B",
                                                                                                        spec)),
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
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
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(963159)
                                                                        .setGasUsed(2500)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
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
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setCallDepth(1)
                                                                        .setGas(960259)
                                                                        .setGasUsed(5249)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55252)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(952294)
                                                                        .setGasUsed(2368)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        12)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
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
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(949526)
                                                                        .setGasUsed(3215)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_SECOND_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        524)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(945992)
                                                                        .setGasUsed(6069)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "callcodeAddressGetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setGas(927718)
                                                                        .setGasUsed(2500)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                GET_ZERO_SLOT)
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(939626)
                                                                        .setGasUsed(21544)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "callcodeAddressSetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        54)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setGas(923822)
                                                                        .setGasUsed(20381)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                SET_ZERO_SLOT,
                                                                                                BigInteger
                                                                                                        .valueOf(
                                                                                                                54))
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(918049)
                                                                        .setGasUsed(3393)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "callcodeAddressGetSlot1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setGas(902867)
                                                                        .setGasUsed(2522)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                GET_FIRST_SLOT)
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(914320)
                                                                        .setGasUsed(1270)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "callcodeAddressSetSlot1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
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
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setGas(899149)
                                                                        .setGasUsed(349)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                SET_FIRST_SLOT,
                                                                                                BigInteger
                                                                                                        .valueOf(
                                                                                                                0))
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build())))));
    }

    private HapiSpec traceabilityE2EScenario8() {
        return defaultHapiSpec("traceabilityE2EScenario8")
                .given(
                        uploadInitCode(TRACEABILITY_CALLCODE),
                        contractCreate(
                                        TRACEABILITY_CALLCODE,
                                        BigInteger.valueOf(55),
                                        BigInteger.TWO,
                                        BigInteger.TWO)
                                .via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE)
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
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGasUsed(67632)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN,
                                TRACEABILITY_CALLCODE,
                                TRACEABILITY_CALLCODE,
                                BigInteger.valueOf(55),
                                BigInteger.TWO,
                                BigInteger.TWO),
                        contractCustomCreate(
                                        TRACEABILITY_CALLCODE,
                                        SECOND,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO,
                                        BigInteger.valueOf(12))
                                .via(SECOND_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                SECOND_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE + SECOND)
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
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setGasUsed(27832)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN,
                                TRACEABILITY_CALLCODE + SECOND,
                                TRACEABILITY_CALLCODE,
                                BigInteger.ZERO,
                                BigInteger.ZERO,
                                BigInteger.valueOf(12)),
                        contractCustomCreate(
                                        TRACEABILITY_CALLCODE,
                                        THIRD,
                                        BigInteger.valueOf(4),
                                        BigInteger.ONE,
                                        BigInteger.ZERO)
                                .via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(1)),
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
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + THIRD))
                                                                        .setGasUsed(47732)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN,
                                TRACEABILITY_CALLCODE + THIRD,
                                TRACEABILITY_CALLCODE,
                                BigInteger.valueOf(4),
                                BigInteger.ONE,
                                BigInteger.ZERO))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY_CALLCODE,
                                                                "eetScenario8",
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY_CALLCODE
                                                                                        + "B",
                                                                                spec)),
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY_CALLCODE
                                                                                        + "C",
                                                                                spec)))
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(55252)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(524))))),
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
                                                                        .setGasUsed(29301)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "eetScenario8",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + "B",
                                                                                                        spec)),
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
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
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(962924)
                                                                        .setGasUsed(2500)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
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
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setCallDepth(1)
                                                                        .setGas(960024)
                                                                        .setGasUsed(3281)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        SET_ZERO_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        2)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(956466)
                                                                        .setGasUsed(2522)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        2)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
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
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setCallDepth(1)
                                                                        .setGas(953547)
                                                                        .setGasUsed(3149)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55252)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(950079)
                                                                        .setGasUsed(5783)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "callcodeAddressGetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + SECOND,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(931893)
                                                                        .setGasUsed(2368)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        2)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                GET_SECOND_SLOT)
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(943995)
                                                                        .setGasUsed(4290)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "callcodeAddressSetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + SECOND,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        524)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(928209)
                                                                        .setGasUsed(3215)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                SET_SECOND_SLOT,
                                                                                                BigInteger
                                                                                                        .valueOf(
                                                                                                                524))
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(938961)
                                                                        .setGasUsed(4144)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                "callcodeAddressSetSlot0",
                                                                                                hexedSolidityAddressToHeadlongAddress(
                                                                                                        getNestedContractAddress(
                                                                                                                TRACEABILITY_CALLCODE
                                                                                                                        + THIRD,
                                                                                                                spec)),
                                                                                                BigInteger
                                                                                                        .valueOf(
                                                                                                                55))
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setGas(920706)
                                                                        .setGasUsed(481)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                SET_ZERO_SLOT,
                                                                                                BigInteger
                                                                                                        .valueOf(
                                                                                                                55))
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build())))));
    }

    private HapiSpec traceabilityE2EScenario9() {
        return defaultHapiSpec("traceabilityE2EScenario9")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(
                                        TRACEABILITY,
                                        BigInteger.valueOf(55),
                                        BigInteger.TWO,
                                        BigInteger.TWO)
                                .via(FIRST_CREATE_TXN),
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
                                FIRST_CREATE_TXN,
                                TRACEABILITY,
                                TRACEABILITY,
                                BigInteger.valueOf(55),
                                BigInteger.TWO,
                                BigInteger.TWO),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        SECOND,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO,
                                        BigInteger.valueOf(12))
                                .via(SECOND_CREATE_TXN),
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
                                SECOND_CREATE_TXN,
                                TRACEABILITY + SECOND,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ZERO,
                                BigInteger.valueOf(12)),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        THIRD,
                                        BigInteger.ZERO,
                                        BigInteger.ONE,
                                        BigInteger.ZERO)
                                .via(THIRD_CREATE_TXN),
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
                                                                formattedAssertionValue(1)),
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
                                THIRD_CREATE_TXN,
                                TRACEABILITY + THIRD,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ONE,
                                BigInteger.ZERO))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario9",
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "B",
                                                                                spec)),
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "C",
                                                                                spec)))
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
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
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(2))),
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(12))),
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(1))))),
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
                                                                        .setGasUsed(50335)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setRevertReason(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario9",
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
                                                                        .setGas(963040)
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
                                                                        .setGas(960258)
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
                                                                                                        55252)))
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
                                                                        .setGas(952332)
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
                                                                        .setGas(949566)
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
                                                                                                        524)))
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
                                                                        .setGas(943624)
                                                                        .setGasUsed(29899)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setRevertReason(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callToContractCForE2EScenario92"))
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
                                                                                                        + THIRD))
                                                                        .setGas(928493)
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
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGas(925711)
                                                                        .setGasUsed(20323)
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
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGas(905493)
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
                                                                                                        1)))
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
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGas(902659)
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
                                                                                                        155)))
                                                                        .build())))));
    }

    private HapiSpec traceabilityE2EScenario10() {
        return defaultHapiSpec("traceabilityE2EScenario10")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(
                                        TRACEABILITY,
                                        BigInteger.TWO,
                                        BigInteger.valueOf(3),
                                        BigInteger.valueOf(4))
                                .via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(3)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4))))),
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
                                FIRST_CREATE_TXN,
                                TRACEABILITY,
                                TRACEABILITY,
                                BigInteger.TWO,
                                BigInteger.valueOf(3),
                                BigInteger.valueOf(4)),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        SECOND,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO,
                                        BigInteger.valueOf(3))
                                .via(SECOND_CREATE_TXN),
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
                                                                formattedAssertionValue(3))))),
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
                                SECOND_CREATE_TXN,
                                TRACEABILITY + SECOND,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ZERO,
                                BigInteger.valueOf(3)),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        THIRD,
                                        BigInteger.ZERO,
                                        BigInteger.ONE,
                                        BigInteger.ZERO)
                                .via(THIRD_CREATE_TXN),
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
                                                                formattedAssertionValue(1)),
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
                                THIRD_CREATE_TXN,
                                TRACEABILITY + THIRD,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ONE,
                                BigInteger.ZERO))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario10",
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "B",
                                                                                spec)),
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "C",
                                                                                spec)))
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
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(3),
                                                                formattedAssertionValue(4))),
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(3),
                                                                formattedAssertionValue(5))),
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(1))))),
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
                                                                        .setGasUsed(52541)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario10",
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
                                                                        .setGas(963038)
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
                                                                                                        2)))
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
                                                                        .setGas(960256)
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
                                                                                                        4)))
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
                                                                        .setGas(954662)
                                                                        .setGasUsed(5811)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressGetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + SECOND,
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
                                                                        .setGas(936436)
                                                                        .setGasUsed(2315)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        3)))
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
                                                                        .setGas(948571)
                                                                        .setGasUsed(4235)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressSetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + SECOND,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        5)))
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
                                                                        .setGas(932774)
                                                                        .setGasUsed(3180)
                                                                        .setCallDepth(2)
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
                                                                                                        5)))
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
                                                                        .setGas(941591)
                                                                        .setGasUsed(29898)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setRevertReason(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "failingGettingAndSetting"))
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
                                                                                                        + THIRD))
                                                                        .setGas(926492)
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
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGas(923710)
                                                                        .setGasUsed(20323)
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
                                                                                                        12)))
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
                                                                                                        + THIRD))
                                                                        .setGas(903492)
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
                                                                                                        1)))
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
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGas(900658)
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

    private HapiSpec traceabilityE2EScenario11() {
        return defaultHapiSpec("traceabilityE2EScenario11")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(
                                        TRACEABILITY,
                                        BigInteger.TWO,
                                        BigInteger.valueOf(3),
                                        BigInteger.valueOf(4))
                                .via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(3)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4))))),
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
                                FIRST_CREATE_TXN,
                                TRACEABILITY,
                                TRACEABILITY,
                                BigInteger.TWO,
                                BigInteger.valueOf(3),
                                BigInteger.valueOf(4)),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        SECOND,
                                        BigInteger.ZERO,
                                        BigInteger.ZERO,
                                        BigInteger.valueOf(3))
                                .via(SECOND_CREATE_TXN),
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
                                                                formattedAssertionValue(3))))),
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
                                SECOND_CREATE_TXN,
                                TRACEABILITY + SECOND,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ZERO,
                                BigInteger.valueOf(3)),
                        contractCustomCreate(
                                        TRACEABILITY,
                                        THIRD,
                                        BigInteger.ZERO,
                                        BigInteger.ONE,
                                        BigInteger.ZERO)
                                .via(THIRD_CREATE_TXN),
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
                                                                formattedAssertionValue(1)),
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
                                THIRD_CREATE_TXN,
                                TRACEABILITY + THIRD,
                                TRACEABILITY,
                                BigInteger.ZERO,
                                BigInteger.ONE,
                                BigInteger.ZERO))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario11",
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "B",
                                                                                spec)),
                                                                asHeadlongAddress(
                                                                        getNestedContractAddress(
                                                                                TRACEABILITY + "C",
                                                                                spec)))
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
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(3),
                                                                formattedAssertionValue(4))),
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(123)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(1),
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
                                                                        .setGasUsed(44077)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario11",
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
                                                                        .setGas(963038)
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
                                                                                                        2)))
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
                                                                        .setGas(960256)
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
                                                                                                        4)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(952341)
                                                                        .setGasUsed(237)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setRevertReason(EMPTY)
                                                                        .setInput(
                                                                                ByteString.copyFrom(
                                                                                        "readAndWriteThenRevert()"
                                                                                                .getBytes(
                                                                                                        StandardCharsets
                                                                                                                .UTF_8)))
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
                                                                        .setGas(949404)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(1)
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
                                                                        .setCallDepth(1)
                                                                        .setGas(946606)
                                                                        .setGasUsed(20323)
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
                                                                                                        123)))
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
                                                                        .setGas(926387)
                                                                        .setGasUsed(2391)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        1)))
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
                                                                        .setGas(923534)
                                                                        .setGasUsed(3224)
                                                                        .setCallDepth(1)
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

    private HapiSpec traceabilityE2EScenario12() {
        final var contract = "CreateTrivial";
        final var scenario12 = "traceabilityE2EScenario12";
        return defaultHapiSpec(scenario12)
                .given(uploadInitCode(contract))
                .when(
                        contractCreate(contract)
                                .via(TRACEABILITY_TXN)
                                .inlineInitCode(
                                        extractBytecodeUnhexed(getResourcePath(contract, ".bin"))))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final HapiGetTxnRecord txnRecord =
                                            getTxnRecord(TRACEABILITY_TXN);
                                    allRunFor(
                                            spec,
                                            txnRecord,
                                            expectContractActionSidecarFor(
                                                    TRACEABILITY_TXN,
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
                        expectContractBytecode(TRACEABILITY_TXN, contract));
    }

    HapiSpec traceabilityE2EScenario13() {
        final AtomicReference<AccountID> accountIDAtomicReference = new AtomicReference<>();
        return defaultHapiSpec("traceabilityE2EScenario13")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TXN),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .exposingIdTo(accountIDAtomicReference::set),
                        getTxnRecord(AUTO_ACCOUNT_TXN).andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT))
                .when(
                        ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .maxGasAllowance(ONE_HUNDRED_HBARS)
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(SUCCESS)
                                .via(FIRST_CREATE_TXN))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final HapiGetTxnRecord txnRecord =
                                            getTxnRecord(FIRST_CREATE_TXN);
                                    allRunFor(
                                            spec,
                                            txnRecord,
                                            expectContractActionSidecarFor(
                                                    FIRST_CREATE_TXN,
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CREATE)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CREATE)
                                                                    .setCallingAccount(
                                                                            accountIDAtomicReference
                                                                                    .get())
                                                                    .setRecipientContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            PAY_RECEIVABLE_CONTRACT))
                                                                    .setGas(947000)
                                                                    .setGasUsed(135)
                                                                    .setOutput(EMPTY)
                                                                    .build())));
                                }),
                        expectContractBytecodeWithMinimalFieldsSidecarFor(
                                FIRST_CREATE_TXN, PAY_RECEIVABLE_CONTRACT));
    }

    private HapiSpec traceabilityE2EScenario14() {
        return defaultHapiSpec("traceabilityE2EScenario14")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TXN),
                        getTxnRecord(AUTO_ACCOUNT_TXN).andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT))
                .when(
                        ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .maxGasAllowance(ONE_HUNDRED_HBARS)
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(SUCCESS)
                                .via(TRACEABILITY_TXN))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final AtomicReference<AccountID> accountIDAtomicReference =
                                            new AtomicReference<>();
                                    final var hapiGetAccountInfo =
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .exposingIdTo(accountIDAtomicReference::set);
                                    allRunFor(spec, hapiGetAccountInfo);
                                    allRunFor(
                                            spec,
                                            expectContractActionSidecarFor(
                                                    TRACEABILITY_TXN,
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CREATE)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CREATE)
                                                                    .setCallingAccount(
                                                                            accountIDAtomicReference
                                                                                    .get())
                                                                    .setGas(947000)
                                                                    .setRecipientContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            PAY_RECEIVABLE_CONTRACT))
                                                                    .setGasUsed(135)
                                                                    .setOutput(EMPTY)
                                                                    .build())),
                                            expectContractBytecodeWithMinimalFieldsSidecarFor(
                                                    TRACEABILITY_TXN, PAY_RECEIVABLE_CONTRACT));
                                }));
    }

    HapiSpec traceabilityE2EScenario15() {
        final String GET_BYTECODE = "getBytecode";
        final String DEPLOY = "deploy";
        final var CREATE_2_TXN = "Create2Txn";
        final var CREATE_TXN = "CreateTxn";
        final var tcValue = 1_234L;
        final var contract = "Create2Factory";
        final var salt = 42;
        final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
        final AtomicReference<String> expectedCreate2Address = new AtomicReference<>();
        final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();
        final AtomicReference<byte[]> bytecodeFromMirror = new AtomicReference<>();
        final AtomicReference<String> mirrorLiteralId = new AtomicReference<>();
        final String specName = "traceabilityE2EScenario15";
        return defaultHapiSpec(specName)
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .via(CREATE_TXN)
                                .exposingNumTo(
                                        num ->
                                                factoryEvmAddress.set(
                                                        HapiPropertySource.asHexedSolidityAddress(
                                                                0, 0, num))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        CREATE_TXN,
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
                                                                                                contract))
                                                                        .setGasUsed(613)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(CREATE_TXN, contract, contract))
                .when(
                        sourcing(
                                () ->
                                        contractCallLocal(
                                                        contract,
                                                        GET_BYTECODE,
                                                        asHeadlongAddress(factoryEvmAddress.get()),
                                                        BigInteger.valueOf(salt))
                                                .exposingTypedResultsTo(
                                                        results -> {
                                                            final var tcInitcode =
                                                                    (byte[]) results[0];
                                                            testContractInitcode.set(tcInitcode);
                                                            log.info(
                                                                    "Contract reported TestContract"
                                                                        + " initcode is {} bytes",
                                                                    tcInitcode.length);
                                                        })),
                        sourcing(
                                () ->
                                        contractCallLocal(
                                                        contract,
                                                        "getAddress",
                                                        testContractInitcode.get(),
                                                        BigInteger.valueOf(salt))
                                                .exposingTypedResultsTo(
                                                        results -> {
                                                            log.info(
                                                                    "Contract reported address"
                                                                            + " results {}",
                                                                    results);
                                                            final var expectedAddr =
                                                                    (Address) results[0];
                                                            final var hexedAddress =
                                                                    expectedAddr.toString();
                                                            log.info(
                                                                    "  --> Expected CREATE2 address"
                                                                            + " is {}",
                                                                    hexedAddress);
                                                            expectedCreate2Address.set(
                                                                    hexedAddress);
                                                        })),
                        sourcing(
                                () ->
                                        contractCall(
                                                        contract,
                                                        DEPLOY,
                                                        testContractInitcode.get(),
                                                        BigInteger.valueOf(salt))
                                                .payingWith(GENESIS)
                                                .gas(4_000_000L)
                                                .sending(tcValue)
                                                .via(CREATE_2_TXN)),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var parentId = spec.registry().getContractId(contract);
                                    final var childId =
                                            ContractID.newBuilder()
                                                    .setContractNum(parentId.getContractNum() + 1L)
                                                    .build();
                                    mirrorLiteralId.set("0.0." + childId.getContractNum());
                                    final var topLevelCallTxnRecord =
                                            getTxnRecord(CREATE_2_TXN).andAllChildRecords();
                                    final var hapiGetContractBytecode =
                                            getContractBytecode(mirrorLiteralId.get())
                                                    .exposingBytecodeTo(bytecodeFromMirror::set);
                                    allRunFor(
                                            spec,
                                            topLevelCallTxnRecord,
                                            expectContractStateChangesSidecarFor(
                                                    CREATE_2_TXN,
                                                    List.of(
                                                            StateChange.stateChangeFor(
                                                                            HapiPropertySource
                                                                                    .asContractString(
                                                                                            childId))
                                                                    .withStorageChanges(
                                                                            StorageChange
                                                                                    .readAndWritten(
                                                                                            formattedAssertionValue(
                                                                                                    0L),
                                                                                            formattedAssertionValue(
                                                                                                    0L),
                                                                                            ByteStringUtils
                                                                                                    .wrapUnsafely(
                                                                                                            Bytes
                                                                                                                    .fromHexString(
                                                                                                                            factoryEvmAddress
                                                                                                                                    .get())
                                                                                                                    .trimLeadingZeros()
                                                                                                                    .toArrayUnsafe())),
                                                                            StorageChange
                                                                                    .readAndWritten(
                                                                                            formattedAssertionValue(
                                                                                                    1L),
                                                                                            formattedAssertionValue(
                                                                                                    0L),
                                                                                            formattedAssertionValue(
                                                                                                    salt))))),
                                            expectContractActionSidecarFor(
                                                    CREATE_2_TXN,
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CALL)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CALL)
                                                                    .setCallingAccount(
                                                                            TxnUtils.asId(
                                                                                    GENESIS, spec))
                                                                    .setGas(3979000)
                                                                    .setValue(tcValue)
                                                                    .setRecipientContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            contract))
                                                                    .setGasUsed(80135)
                                                                    .setOutput(EMPTY)
                                                                    .setInput(
                                                                            encodeFunctionCall(
                                                                                    contract,
                                                                                    DEPLOY,
                                                                                    testContractInitcode
                                                                                            .get(),
                                                                                    BigInteger
                                                                                            .valueOf(
                                                                                                    salt)))
                                                                    .build(),
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CREATE)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CREATE2)
                                                                    .setCallingContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            contract))
                                                                    .setGas(3883883)
                                                                    .setRecipientContract(childId)
                                                                    .setGasUsed(44936)
                                                                    .setValue(tcValue)
                                                                    .setOutput(EMPTY)
                                                                    .setCallDepth(1)
                                                                    .build())),
                                            hapiGetContractBytecode);
                                    expectContractBytecode(
                                            specName,
                                            topLevelCallTxnRecord
                                                    .getChildRecord(0)
                                                    .getConsensusTimestamp(),
                                            asContract(mirrorLiteralId.get()),
                                            ByteStringUtils.wrapUnsafely(
                                                    testContractInitcode.get()),
                                            ByteStringUtils.wrapUnsafely(bytecodeFromMirror.get()));
                                }))
                .then();
    }

    HapiSpec traceabilityE2EScenario16() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final String PRECOMPILE_CALLER = "PrecompileCaller";
        final String txn = "payTxn";
        final String toHash = "toHash";
        return defaultHapiSpec("traceabilityE2EScenario16")
                .given(
                        tokenCreate("goodToken")
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(GENESIS)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(PRECOMPILE_CALLER),
                        contractCreate(PRECOMPILE_CALLER).via(txn),
                        withOpContext(
                                (spec, opLog) -> {
                                    final HapiGetTxnRecord txnRecord = getTxnRecord(txn);
                                    allRunFor(
                                            spec,
                                            txnRecord,
                                            expectContractActionSidecarFor(
                                                    txn,
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
                                                                                            PRECOMPILE_CALLER))
                                                                    .setGas(197000)
                                                                    .setGasUsed(942)
                                                                    .setOutput(EMPTY)
                                                                    .build())));
                                }),
                        expectContractBytecodeSidecarFor(txn, PRECOMPILE_CALLER, PRECOMPILE_CALLER))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        PRECOMPILE_CALLER,
                                                        "callSha256AndIsToken",
                                                        toHash.getBytes(),
                                                        HapiParserUtil.asHeadlongAddress(
                                                                asAddress(vanillaTokenID.get())))
                                                .via("callTxn")))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final byte[] expectedHash =
                                            Hashing.sha256().hashBytes(toHash.getBytes()).asBytes();
                                    allRunFor(
                                            spec,
                                            expectContractActionSidecarFor(
                                                    "callTxn",
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CALL)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CALL)
                                                                    .setCallingAccount(
                                                                            spec.registry()
                                                                                    .getAccountID(
                                                                                            GENESIS))
                                                                    .setGas(79000)
                                                                    .setRecipientContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            PRECOMPILE_CALLER))
                                                                    .setGasUsed(5232)
                                                                    .setInput(
                                                                            encodeFunctionCall(
                                                                                    PRECOMPILE_CALLER,
                                                                                    "callSha256AndIsToken",
                                                                                    toHash
                                                                                            .getBytes(),
                                                                                    hexedSolidityAddressToHeadlongAddress(
                                                                                            HapiPropertySource
                                                                                                    .asHexedSolidityAddress(
                                                                                                            vanillaTokenID
                                                                                                                    .get()))))
                                                                    .setOutput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            encodeTuple(
                                                                                                    "(bool,bytes32)",
                                                                                                    true,
                                                                                                    expectedHash)))
                                                                    .build(),
                                                            ContractAction.newBuilder()
                                                                    .setCallType(PRECOMPILE)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_STATICCALL)
                                                                    .setCallingContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            PRECOMPILE_CALLER))
                                                                    .setGas(76587)
                                                                    // SHA 256 precompile address is
                                                                    // 0x02
                                                                    .setRecipientContract(
                                                                            ContractID.newBuilder()
                                                                                    .setContractNum(
                                                                                            2)
                                                                                    .build())
                                                                    .setGasUsed(72)
                                                                    .setInput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            toHash
                                                                                                    .getBytes()))
                                                                    .setOutput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            expectedHash))
                                                                    .setCallDepth(1)
                                                                    .build(),
                                                            ContractAction.newBuilder()
                                                                    .setCallType(SYSTEM)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CALL)
                                                                    .setCallingContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            PRECOMPILE_CALLER))
                                                                    .setGas(73240)
                                                                    // HTS precompile address is
                                                                    // 0x167
                                                                    .setRecipientContract(
                                                                            ContractID.newBuilder()
                                                                                    .setContractNum(
                                                                                            359)
                                                                                    .build())
                                                                    .setGasUsed(2)
                                                                    .setInput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            Function
                                                                                                    .parse(
                                                                                                            "isToken"
                                                                                                                + "(address)")
                                                                                                    .encodeCallWithArgs(
                                                                                                            hexedSolidityAddressToHeadlongAddress(
                                                                                                                    HapiPropertySource
                                                                                                                            .asHexedSolidityAddress(
                                                                                                                                    vanillaTokenID
                                                                                                                                            .get())))
                                                                                                    .array()))
                                                                    .setOutput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            encodeTuple(
                                                                                                    ("(int64,bool)"),
                                                                                                    (long)
                                                                                                            SUCCESS
                                                                                                                    .getNumber(),
                                                                                                    true)))
                                                                    .setCallDepth(1)
                                                                    .build())));
                                }));
    }

    private HapiSpec traceabilityE2EScenario17() {
        return defaultHapiSpec("traceabilityE2EScenario17")
                .given(
                        uploadInitCode(REVERTING_CONTRACT),
                        contractCreate(REVERTING_CONTRACT, BigInteger.valueOf(6))
                                .via(FIRST_CREATE_TXN),
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
                                FIRST_CREATE_TXN,
                                REVERTING_CONTRACT,
                                REVERTING_CONTRACT,
                                BigInteger.valueOf(6)))
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

    private HapiSpec traceabilityE2EScenario18() {
        return defaultHapiSpec("traceabilityE2EScenario18")
                .given(uploadInitCode(REVERTING_CONTRACT))
                .when(
                        contractCreate(REVERTING_CONTRACT, BigInteger.valueOf(4))
                                .via(FIRST_CREATE_TXN)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))
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
                                                                        .setGas(197000)
                                                                        .setGasUsed(201)
                                                                        .setRevertReason(EMPTY)
                                                                        .build())))),
                        expectFailedContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, REVERTING_CONTRACT, BigInteger.valueOf(4)));
    }

    HapiSpec traceabilityE2EScenario19() {
        final var RECEIVER = "RECEIVER";
        final var hbarsToSend = 1;
        final var transferTxn = "payTxn";
        return defaultHapiSpec("traceabilityE2EScenario19")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RECEIVER).balance(0L),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TXN),
                        getTxnRecord(AUTO_ACCOUNT_TXN).andAllChildRecords())
                .when(
                        ethereumCryptoTransfer(RECEIVER, hbarsToSend)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .gasLimit(2_000_000L)
                                .payingWith(RELAYER)
                                .via(transferTxn))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final AtomicReference<AccountID> ethSenderAccountReference =
                                            new AtomicReference<>();
                                    final var hapiGetAccountInfo =
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .exposingIdTo(ethSenderAccountReference::set);
                                    allRunFor(spec, hapiGetAccountInfo);
                                    allRunFor(
                                            spec,
                                            expectContractActionSidecarFor(
                                                    transferTxn,
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CALL)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CALL)
                                                                    .setCallingAccount(
                                                                            ethSenderAccountReference
                                                                                    .get())
                                                                    .setGas(1979000)
                                                                    .setGasUsed(
                                                                            0) // we only transfer
                                                                    // hbars, no code
                                                                    // executed
                                                                    .setValue(hbarsToSend)
                                                                    .setRecipientAccount(
                                                                            spec.registry()
                                                                                    .getAccountID(
                                                                                            RECEIVER))
                                                                    .setOutput(EMPTY)
                                                                    .build())));
                                }));
    }

    private HapiSpec traceabilityE2EScenario20() {
        return defaultHapiSpec("traceabilityE2EScenario20")
                .given(uploadInitCode(REVERTING_CONTRACT))
                .when(
                        contractCreate(REVERTING_CONTRACT, BigInteger.valueOf(6))
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
                                FIRST_CREATE_TXN, REVERTING_CONTRACT, BigInteger.valueOf(6)));
    }

    private HapiSpec traceabilityE2EScenario21() {
        return defaultHapiSpec("traceabilityE2EScenario21")
                .given(
                        uploadInitCode(REVERTING_CONTRACT),
                        contractCreate(REVERTING_CONTRACT, BigInteger.valueOf(6))
                                .via(FIRST_CREATE_TXN),
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
                                FIRST_CREATE_TXN,
                                REVERTING_CONTRACT,
                                REVERTING_CONTRACT,
                                BigInteger.valueOf(6)))
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
                                                                        .setTargetedAddress(
                                                                                ByteString.copyFrom(
                                                                                        asSolidityAddress(
                                                                                                0,
                                                                                                0,
                                                                                                0)))
                                                                        .build())))));
    }

    private HapiSpec vanillaBytecodeSidecar() {
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

    private HapiSpec vanillaBytecodeSidecar2() {
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

    private HapiSpec actionsShowPropagatedRevert() {
        final var APPROVE_BY_DELEGATE = "ApproveByDelegateCall";
        final var badApproval = "BadApproval";
        final var somebody = "somebody";
        final var somebodyElse = "somebodyElse";
        final var tokenInQuestion = "TokenInQuestion";
        final var someSupplyKey = "someSupplyKey";
        final AtomicReference<String> tiqMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> somebodyMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> somebodyElseMirrorAddr = new AtomicReference<>();
        final String contractCreateTxn = "contractCreate";
        final var serialNumberId = MAX_UINT256_VALUE;
        return propertyPreservingHapiSpec("ActionsShowPropagatedRevert")
                .preserving(SIDECARS_PROP)
                .given(
                        overriding(SIDECARS_PROP, "CONTRACT_ACTION"),
                        uploadInitCode(APPROVE_BY_DELEGATE),
                        contractCreate(APPROVE_BY_DELEGATE).via(contractCreateTxn),
                        withOpContext(
                                (spec, opLog) -> {
                                    final HapiGetTxnRecord txnRecord =
                                            getTxnRecord(contractCreateTxn);
                                    allRunFor(
                                            spec,
                                            txnRecord,
                                            expectContractActionSidecarFor(
                                                    contractCreateTxn,
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
                                                                                            APPROVE_BY_DELEGATE))
                                                                    .setGas(197000)
                                                                    .setGasUsed(214)
                                                                    .setOutput(EMPTY)
                                                                    .build())));
                                }),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(somebody)
                                .maxAutomaticTokenAssociations(2)
                                .exposingCreatedIdTo(
                                        id ->
                                                somebodyMirrorAddr.set(
                                                        HapiPropertySource.asHexedSolidityAddress(
                                                                id))),
                        cryptoCreate(somebodyElse)
                                .maxAutomaticTokenAssociations(2)
                                .exposingCreatedIdTo(
                                        id ->
                                                somebodyElseMirrorAddr.set(
                                                        HapiPropertySource.asHexedSolidityAddress(
                                                                id))),
                        newKeyNamed(someSupplyKey),
                        tokenCreate(tokenInQuestion)
                                .supplyKey(someSupplyKey)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                tiqMirrorAddr.set(
                                                        HapiPropertySource.asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(
                                                                        idLit)))),
                        mintToken(
                                tokenInQuestion,
                                List.of(
                                        ByteString.copyFromUtf8("A penny for"),
                                        ByteString.copyFromUtf8("the Old Guy"))),
                        cryptoTransfer(
                                movingUnique(tokenInQuestion, 1L)
                                        .between(TOKEN_TREASURY, somebody)))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        APPROVE_BY_DELEGATE,
                                                        "doIt",
                                                        asHeadlongAddress(tiqMirrorAddr.get()),
                                                        asHeadlongAddress(
                                                                somebodyElseMirrorAddr.get()),
                                                        serialNumberId)
                                                .payingWith(somebody)
                                                .gas(1_000_000)
                                                .via(badApproval)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final HapiGetTxnRecord txnRecord = getTxnRecord(badApproval);
                                    allRunFor(
                                            spec,
                                            txnRecord,
                                            expectContractActionSidecarFor(
                                                    badApproval,
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CALL)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CALL)
                                                                    .setCallingAccount(
                                                                            spec.registry()
                                                                                    .getAccountID(
                                                                                            somebody))
                                                                    .setRecipientContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            APPROVE_BY_DELEGATE))
                                                                    .setInput(
                                                                            encodeFunctionCall(
                                                                                    APPROVE_BY_DELEGATE,
                                                                                    "doIt",
                                                                                    hexedSolidityAddressToHeadlongAddress(
                                                                                            HapiPropertySource
                                                                                                    .asHexedSolidityAddress(
                                                                                                            spec.registry()
                                                                                                                    .getTokenID(
                                                                                                                            tokenInQuestion))),
                                                                                    hexedSolidityAddressToHeadlongAddress(
                                                                                            HapiPropertySource
                                                                                                    .asHexedSolidityAddress(
                                                                                                            spec.registry()
                                                                                                                    .getAccountID(
                                                                                                                            somebodyElse))),
                                                                                    serialNumberId))
                                                                    .setGas(979000)
                                                                    .setGasUsed(948950)
                                                                    .setRevertReason(
                                                                            ByteString.EMPTY)
                                                                    .build(),
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CALL)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_DELEGATECALL)
                                                                    .setCallingContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            APPROVE_BY_DELEGATE))
                                                                    .setRecipientContract(
                                                                            ContractID.newBuilder()
                                                                                    .setContractNum(
                                                                                            spec.registry()
                                                                                                    .getTokenID(
                                                                                                            tokenInQuestion)
                                                                                                    .getTokenNum())
                                                                                    .build())
                                                                    .setGas(959347)
                                                                    .setGasUsed(944446)
                                                                    .setInput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            Function
                                                                                                    .parse(
                                                                                                            "approve(address,uint256)")
                                                                                                    .encodeCallWithArgs(
                                                                                                            hexedSolidityAddressToHeadlongAddress(
                                                                                                                    HapiPropertySource
                                                                                                                            .asHexedSolidityAddress(
                                                                                                                                    spec.registry()
                                                                                                                                            .getAccountID(
                                                                                                                                                    somebodyElse))),
                                                                                                            serialNumberId)
                                                                                                    .array()))
                                                                    .setRevertReason(
                                                                            ByteString.EMPTY)
                                                                    .setCallDepth(1)
                                                                    .build(),
                                                            ContractAction.newBuilder()
                                                                    .setCallType(SYSTEM)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_DELEGATECALL)
                                                                    .setCallingContract(
                                                                            ContractID.newBuilder()
                                                                                    .setContractNum(
                                                                                            spec.registry()
                                                                                                    .getTokenID(
                                                                                                            tokenInQuestion)
                                                                                                    .getTokenNum())
                                                                                    .build())
                                                                    .setRecipientContract(
                                                                            ContractID.newBuilder()
                                                                                    .setContractNum(
                                                                                            359L)
                                                                                    .build())
                                                                    .setGas(941693)
                                                                    .setGasUsed(941693)
                                                                    .setInput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            ArrayUtils
                                                                                                    .addAll(
                                                                                                            ArrayUtils
                                                                                                                    .addAll(
                                                                                                                            Arrays
                                                                                                                                    .copyOfRange(
                                                                                                                                            keccak256(
                                                                                                                                                            Bytes
                                                                                                                                                                    .of(
                                                                                                                                                                            "redirectForToken(address,bytes)"
                                                                                                                                                                                    .getBytes()))
                                                                                                                                                    .toArrayUnsafe(),
                                                                                                                                            0,
                                                                                                                                            4),
                                                                                                                            Arrays
                                                                                                                                    .copyOfRange(
                                                                                                                                            encodeTuple(
                                                                                                                                                    "(address)",
                                                                                                                                                    hexedSolidityAddressToHeadlongAddress(
                                                                                                                                                            HapiPropertySource
                                                                                                                                                                    .asHexedSolidityAddress(
                                                                                                                                                                            spec.registry()
                                                                                                                                                                                    .getTokenID(
                                                                                                                                                                                            tokenInQuestion)))),
                                                                                                                                            12,
                                                                                                                                            32)),
                                                                                                            Function
                                                                                                                    .parse(
                                                                                                                            "approve(address,uint256)")
                                                                                                                    .encodeCallWithArgs(
                                                                                                                            hexedSolidityAddressToHeadlongAddress(
                                                                                                                                    HapiPropertySource
                                                                                                                                            .asHexedSolidityAddress(
                                                                                                                                                    spec.registry()
                                                                                                                                                            .getAccountID(
                                                                                                                                                                    somebodyElse))),
                                                                                                                            serialNumberId)
                                                                                                                    .array())))
                                                                    .setError(
                                                                            ByteString.copyFrom(
                                                                                    "PRECOMPILE_ERROR"
                                                                                            .getBytes()))
                                                                    .setCallDepth(2)
                                                                    .build())));
                                }));
    }

    private HapiSpec ethereumLazyCreateExportsExpectedSidecars() {
        final var RECIPIENT_KEY = "lazyAccountRecipient";
        final var RECIPIENT_KEY2 = "lazyAccountRecipient2";
        final var lazyCreateTxn = "lazyCreateTxn";
        final var failedlazyCreateTxn = "payTxn2";
        final var valueToSend = FIVE_HBARS;
        return propertyPreservingHapiSpec("ethereumLazyCreateExportsExpectedSidecars")
                .preserving(CHAIN_ID_PROPERTY, LAZY_CREATE_PROPERTY, "contracts.evm.version")
                .given(
                        overridingThree(
                                CHAIN_ID_PROPERTY,
                                "298",
                                LAZY_CREATE_PROPERTY,
                                "true",
                                "contracts.evm.version",
                                "v0.34"),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(RECIPIENT_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(RECIPIENT_KEY2).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TXN),
                        getTxnRecord(AUTO_ACCOUNT_TXN).andAllChildRecords())
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                TxnVerbs.ethereumCryptoTransferToAlias(
                                                                spec.registry()
                                                                        .getKey(RECIPIENT_KEY)
                                                                        .getECDSASecp256K1(),
                                                                valueToSend)
                                                        .type(EthTxData.EthTransactionType.EIP1559)
                                                        .signingWith(SECP_256K1_SOURCE_KEY)
                                                        .payingWith(RELAYER)
                                                        .nonce(0)
                                                        .maxFeePerGas(0L)
                                                        .maxGasAllowance(FIVE_HBARS)
                                                        .gasLimit(200_000L)
                                                        .via(failedlazyCreateTxn)
                                                        .hasKnownStatus(INSUFFICIENT_GAS),
                                                TxnVerbs.ethereumCryptoTransferToAlias(
                                                                spec.registry()
                                                                        .getKey(RECIPIENT_KEY)
                                                                        .getECDSASecp256K1(),
                                                                valueToSend)
                                                        .type(EthTxData.EthTransactionType.EIP1559)
                                                        .signingWith(SECP_256K1_SOURCE_KEY)
                                                        .payingWith(RELAYER)
                                                        .nonce(1)
                                                        .maxFeePerGas(0L)
                                                        .maxGasAllowance(FIVE_HBARS)
                                                        .gasLimit(2_000_000L)
                                                        .via(lazyCreateTxn)
                                                        .hasKnownStatus(SUCCESS))))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaSecp256K1 =
                                            spec.registry()
                                                    .getKey(RECIPIENT_KEY)
                                                    .getECDSASecp256K1();
                                    final var firstAliasAsByteString =
                                            ByteStringUtils.wrapUnsafely(
                                                    recoverAddressFromPubKey(
                                                            ecdsaSecp256K1.toByteArray()));
                                    AtomicReference<AccountID> lazyAccountIdReference =
                                            new AtomicReference<>();
                                    final var lazyAccountInfoCheck =
                                            getAliasedAccountInfo(firstAliasAsByteString)
                                                    .logged()
                                                    .has(
                                                            accountWith()
                                                                    .balance(FIVE_HBARS)
                                                                    .key(EMPTY_KEY))
                                                    .exposingIdTo(lazyAccountIdReference::set);
                                    AtomicReference<AccountID> ethSenderAccountReference =
                                            new AtomicReference<>();
                                    final var hapiGetAccountInfo =
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .exposingIdTo(ethSenderAccountReference::set);
                                    allRunFor(spec, hapiGetAccountInfo, lazyAccountInfoCheck);
                                    allRunFor(
                                            spec,
                                            expectContractActionSidecarFor(
                                                    failedlazyCreateTxn,
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CALL)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CALL)
                                                                    .setCallingAccount(
                                                                            ethSenderAccountReference
                                                                                    .get())
                                                                    .setGas(179000)
                                                                    .setGasUsed(179000)
                                                                    .setValue(valueToSend)
                                                                    .setTargetedAddress(
                                                                            firstAliasAsByteString)
                                                                    .setError(
                                                                            ByteString.copyFromUtf8(
                                                                                    INSUFFICIENT_GAS
                                                                                            .name()))
                                                                    .build())),
                                            expectContractActionSidecarFor(
                                                    lazyCreateTxn,
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CALL)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CALL)
                                                                    .setCallingAccount(
                                                                            ethSenderAccountReference
                                                                                    .get())
                                                                    .setGas(1_979_000)
                                                                    .setGasUsed(555_112)
                                                                    .setValue(valueToSend)
                                                                    .setRecipientAccount(
                                                                            lazyAccountIdReference
                                                                                    .get())
                                                                    .setOutput(EMPTY)
                                                                    .build())));
                                }));
    }

    @SuppressWarnings("java:S5960")
    private HapiSpec hollowAccountCreate2MergeExportsExpectedSidecars() {
        final var tcValue = 1_234L;
        final var create2Factory = "Create2Factory";
        final var creation = "creation";
        final var salt = BigInteger.valueOf(42);
        final var adminKey = "ADMIN_KEY";
        final var entityMemo = "JUST DO IT";
        final AtomicReference<String> factoryEvmAddress = new AtomicReference<>();
        final AtomicReference<String> expectedCreate2Address = new AtomicReference<>();
        final AtomicReference<String> hollowCreationAddress = new AtomicReference<>();
        final AtomicReference<String> mergedAliasAddr = new AtomicReference<>();
        final AtomicReference<String> mergedMirrorAddr = new AtomicReference<>();
        final AtomicReference<byte[]> testContractInitcode = new AtomicReference<>();
        final AtomicReference<AccountID> mergedAccountId = new AtomicReference<>();
        final var CREATE_2_TXN = "create2Txn";
        final var specName = "hollowAccountCreate2MergeExportsExpectedSidecars";
        return propertyPreservingHapiSpec(specName)
                .preserving(LAZY_CREATE_PROPERTY, SIDECARS_PROP)
                .given(
                        overriding(LAZY_CREATE_PROPERTY, "true"),
                        overriding(SIDECARS_PROP, ""),
                        newKeyNamed(adminKey),
                        newKeyNamed(MULTI_KEY),
                        uploadInitCode(create2Factory),
                        contractCreate(create2Factory)
                                .payingWith(GENESIS)
                                .adminKey(adminKey)
                                .entityMemo(entityMemo)
                                .via(CREATE_2_TXN)
                                .exposingNumTo(
                                        num ->
                                                factoryEvmAddress.set(
                                                        HapiPropertySource.asHexedSolidityAddress(
                                                                0, 0, num))),
                        cryptoCreate(PARTY).maxAutomaticTokenAssociations(2))
                .when(
                        sourcing(
                                () ->
                                        contractCallLocal(
                                                        create2Factory,
                                                        GET_BYTECODE,
                                                        asHeadlongAddress(factoryEvmAddress.get()),
                                                        salt)
                                                .exposingTypedResultsTo(
                                                        results -> {
                                                            final var tcInitcode =
                                                                    (byte[]) results[0];
                                                            testContractInitcode.set(tcInitcode);
                                                            log.info(
                                                                    CONTRACT_REPORTED_LOG_MESSAGE,
                                                                    tcInitcode.length);
                                                        })
                                                .payingWith(GENESIS)
                                                .nodePayment(ONE_HBAR)),
                        sourcing(
                                () ->
                                        contractCallLocal(
                                                        create2Factory,
                                                        GET_ADDRESS,
                                                        testContractInitcode.get(),
                                                        salt)
                                                .exposingTypedResultsTo(
                                                        results -> {
                                                            log.info(
                                                                    CONTRACT_REPORTED_ADDRESS_MESSAGE,
                                                                    results);
                                                            final var expectedAddrBytes =
                                                                    (Address) results[0];
                                                            final var hexedAddress =
                                                                    hex(
                                                                            Bytes.fromHexString(
                                                                                            expectedAddrBytes
                                                                                                    .toString())
                                                                                    .toArray());
                                                            log.info(
                                                                    EXPECTED_CREATE2_ADDRESS_MESSAGE,
                                                                    hexedAddress);
                                                            expectedCreate2Address.set(
                                                                    hexedAddress);
                                                        })
                                                .payingWith(GENESIS)),
                        // Create a hollow account at the desired address
                        cryptoTransfer(
                                        (spec, b) -> {
                                            final var defaultPayerId =
                                                    spec.registry().getAccountID(DEFAULT_PAYER);
                                            b.setTransfers(
                                                    TransferList.newBuilder()
                                                            .addAccountAmounts(
                                                                    aaWith(
                                                                            ByteString.copyFrom(
                                                                                    CommonUtils
                                                                                            .unhex(
                                                                                                    expectedCreate2Address
                                                                                                            .get())),
                                                                            +ONE_HBAR))
                                                            .addAccountAmounts(
                                                                    aaWith(
                                                                            defaultPayerId,
                                                                            -ONE_HBAR)));
                                        })
                                .signedBy(DEFAULT_PAYER, PARTY)
                                .fee(ONE_HBAR)
                                .via(creation),
                        getTxnRecord(creation)
                                .andAllChildRecords()
                                .exposingCreationsTo(l -> hollowCreationAddress.set(l.get(0))),
                        // save the id of the hollow account
                        sourcing(
                                () ->
                                        getAccountInfo(hollowCreationAddress.get())
                                                .logged()
                                                .exposingIdTo(mergedAccountId::set)),
                        sourcing(
                                () ->
                                        overriding(
                                                SIDECARS_PROP,
                                                "CONTRACT_ACTION,CONTRACT_STATE_CHANGE,CONTRACT_BYTECODE")),
                        sourcing(
                                () ->
                                        contractCall(
                                                        create2Factory,
                                                        DEPLOY,
                                                        testContractInitcode.get(),
                                                        salt)
                                                .payingWith(GENESIS)
                                                .gas(4_000_000L)
                                                .sending(tcValue)
                                                .via(CREATE_2_TXN)),
                        captureOneChildCreate2MetaFor(
                                "Merged deployed create2Factory with hollow account",
                                CREATE_2_TXN,
                                mergedMirrorAddr,
                                mergedAliasAddr))
                .then(
                        // assert sidecars
                        withOpContext(
                                (spec, opLog) -> {
                                    final var mergedContractIdAsString =
                                            HapiPropertySource.asAccountString(
                                                    mergedAccountId.get());
                                    final AtomicReference<byte[]> mergedContractBytecode =
                                            new AtomicReference<>();
                                    final var hapiGetContractBytecode =
                                            getContractBytecode(mergedContractIdAsString)
                                                    .exposingBytecodeTo(
                                                            mergedContractBytecode::set);
                                    final var topLevelCallTxnRecord =
                                            getTxnRecord(CREATE_2_TXN).andAllChildRecords();
                                    allRunFor(
                                            spec,
                                            topLevelCallTxnRecord,
                                            expectContractStateChangesSidecarFor(
                                                    CREATE_2_TXN,
                                                    List.of(
                                                            // recipient should be the original
                                                            // hollow account id as a contract
                                                            StateChange.stateChangeFor(
                                                                            mergedContractIdAsString)
                                                                    .withStorageChanges(
                                                                            StorageChange
                                                                                    .readAndWritten(
                                                                                            formattedAssertionValue(
                                                                                                    0L),
                                                                                            formattedAssertionValue(
                                                                                                    0L),
                                                                                            ByteStringUtils
                                                                                                    .wrapUnsafely(
                                                                                                            Bytes
                                                                                                                    .fromHexString(
                                                                                                                            factoryEvmAddress
                                                                                                                                    .get())
                                                                                                                    .trimLeadingZeros()
                                                                                                                    .toArrayUnsafe())),
                                                                            StorageChange
                                                                                    .readAndWritten(
                                                                                            formattedAssertionValue(
                                                                                                    1L),
                                                                                            formattedAssertionValue(
                                                                                                    0L),
                                                                                            formattedAssertionValue(
                                                                                                    salt
                                                                                                            .longValue()))))),
                                            expectContractActionSidecarFor(
                                                    CREATE_2_TXN,
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CALL)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CALL)
                                                                    .setCallingAccount(
                                                                            TxnUtils.asId(
                                                                                    GENESIS, spec))
                                                                    .setGas(3979000)
                                                                    .setValue(tcValue)
                                                                    .setRecipientContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            create2Factory))
                                                                    .setGasUsed(80135)
                                                                    .setOutput(EMPTY)
                                                                    .setInput(
                                                                            encodeFunctionCall(
                                                                                    create2Factory,
                                                                                    DEPLOY,
                                                                                    testContractInitcode
                                                                                            .get(),
                                                                                    salt))
                                                                    .build(),
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CREATE)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CREATE2)
                                                                    .setCallingContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            create2Factory))
                                                                    .setGas(3883883)
                                                                    // recipient should be the
                                                                    // original hollow account id as
                                                                    // a contract
                                                                    .setRecipientContract(
                                                                            asContract(
                                                                                    mergedContractIdAsString))
                                                                    .setGasUsed(44936)
                                                                    .setValue(tcValue)
                                                                    .setOutput(EMPTY)
                                                                    .setCallDepth(1)
                                                                    .build())),
                                            hapiGetContractBytecode);
                                    expectContractBytecode(
                                            specName,
                                            topLevelCallTxnRecord
                                                    .getChildRecord(0)
                                                    .getConsensusTimestamp(),
                                            asContract(mergedContractIdAsString),
                                            ByteStringUtils.wrapUnsafely(
                                                    testContractInitcode.get()),
                                            ByteStringUtils.wrapUnsafely(
                                                    mergedContractBytecode.get()));
                                }));
    }

    @SuppressWarnings("java:S5960")
    private HapiSpec assertSidecars() {
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
            final String txnName, final List<ContractAction> actions) {
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
                    final var contractBytecode =
                            getContractBytecode(contractName).saveResultTo(RUNTIME_CODE);
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
                                                                                            RUNTIME_CODE)))
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

    private CustomSpecAssert expectContractBytecodeWithMinimalFieldsSidecarFor(
            final String contractCreateTxn, final String contractName) {
        return withOpContext(
                (spec, opLog) -> {
                    final var txnRecord = getTxnRecord(contractCreateTxn).andAllChildRecords();
                    final var contractBytecode =
                            getContractBytecode(contractName).saveResultTo(RUNTIME_CODE);
                    allRunFor(spec, txnRecord, contractBytecode);
                    final var consensusTimestamp =
                            txnRecord.getChildRecord(0).getConsensusTimestamp();
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
                                                            .setRuntimeBytecode(
                                                                    ByteString.copyFrom(
                                                                            spec.registry()
                                                                                    .getBytes(
                                                                                            RUNTIME_CODE)))
                                                            .build())
                                            .build()));
                });
    }

    private CustomSpecAssert expectContractBytecode(
            final String contractCreateTxn, final String contractName) {
        return withOpContext(
                (spec, opLog) -> {
                    final var txnRecord = getTxnRecord(contractCreateTxn);
                    final var contractBytecode =
                            getContractBytecode(contractName).saveResultTo(RUNTIME_CODE);
                    allRunFor(spec, txnRecord, contractBytecode);
                    final var consensusTimestamp =
                            txnRecord.getResponseRecord().getConsensusTimestamp();
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
                                                            .setRuntimeBytecode(
                                                                    ByteString.copyFrom(
                                                                            spec.registry()
                                                                                    .getBytes(
                                                                                            RUNTIME_CODE)))
                                                            .build())
                                            .build()));
                });
    }

    private void expectContractBytecode(
            final String specName,
            final Timestamp timestamp,
            final ContractID contractID,
            final ByteString initCode,
            final ByteString runtimeCode) {
        sidecarWatcher.addExpectedSidecar(
                new ExpectedSidecar(
                        specName,
                        TransactionSidecarRecord.newBuilder()
                                .setConsensusTimestamp(timestamp)
                                .setBytecode(
                                        ContractBytecode.newBuilder()
                                                // recipient should be the original hollow account
                                                // id as a contract
                                                .setContractId(contractID)
                                                .setInitcode(initCode)
                                                .setRuntimeBytecode(runtimeCode)
                                                .build())
                                .build()));
    }

    private ByteString getInitcode(final String binFileName, final Object... constructorArgs) {
        final var initCode = extractBytecodeUnhexed(getResourcePath(binFileName, ".bin"));
        final var params =
                constructorArgs.length == 0
                        ? new byte[] {}
                        : Function.fromJson(
                                        getABIFor(
                                                FunctionType.CONSTRUCTOR,
                                                StringUtils.EMPTY,
                                                binFileName))
                                .encodeCall(Tuple.of(constructorArgs))
                                .array();
        return initCode.concat(
                ByteStringUtils.wrapUnsafely(params.length > 4 ? stripSelector(params) : params));
    }

    private static void initialize() throws Exception {
        final var recordStreamFolderPath =
                HapiSpec.isRunningInCi()
                        ? HapiSpec.ciPropOverrides().get(RECORD_STREAM_FOLDER_PATH_PROPERTY_KEY)
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

    byte[] encodeTuple(final String argumentsSignature, final Object... actualArguments) {
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
