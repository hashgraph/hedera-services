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
import static com.hedera.services.bdd.suites.contract.Utils.extractBytecodeUnhexed;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.getResourcePath;
import static com.hedera.services.stream.proto.ContractActionType.CALL;
import static com.hedera.services.stream.proto.ContractActionType.CREATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.apache.commons.lang3.StringUtils.EMPTY;
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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.verification.traceability.ExpectedSidecar;
import com.hedera.services.bdd.spec.verification.traceability.SidecarWatcher;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.Utils.FunctionType;
import com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileSuite;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.ethereum.core.CallTransaction;

public class NewTraceabilitySuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(NewTraceabilitySuite.class);
    private static final String RECORD_STREAM_FOLDER_PATH_PROPERTY_KEY = "recordStream.path";

    public static final int INTRINSIC_GAS = 21_000;

    private static SidecarWatcher sidecarWatcher;
    private static CompletableFuture<Void> sidecarWatcherTask;

    public static void main(String... args) {
        new NewTraceabilitySuite().runSuiteSync();
    }

    @SuppressWarnings("java:S5960")
    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        try {
            initialize();
            return List.of(traceabilityE2EScenario1(), assertSidecars());
        } catch (IOException e) {
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
    }

    private static final String TRACEABILITY = "Traceability";
    public static final String FIRST_CREATE_TXN = "FirstCreateTxn";
    public static final String SECOND_CREATE_TXN = "SecondCreateTxn";
    public static final String THIRD_CREATE_TXN = "ThirdCreateTxn";
    private static final String FIRST = EMPTY;
    private static final String SECOND = "B";
    private static final String THIRD = "C";
    private final String traceabilityTxn = "nestedtxn";

    private HapiApiSpec traceabilityE2EScenario1() {
        final var initialGas = 1_000_000;
        return defaultHapiSpec("traceabilityE2EScenario1")
                .given(
                        uploadInitCode(TRACEABILITY),
                        // TODO: for each we would have bytecode + state changes sidecar (eventually
                        // actions, but eventually)
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
                                (spec, opLog) -> {
                                    final var txnRecord = getTxnRecord(FIRST_CREATE_TXN);
                                    allRunFor(spec, txnRecord);
                                    allRunFor(
                                            spec,
                                            expectContractActionSidecarFor(
                                                    FIRST_CREATE_TXN,
                                                    List.of(
                                                            com.hedera.services.stream.proto
                                                                    .ContractAction.newBuilder()
                                                                    .setCallType(CREATE)
                                                                    .setCallingAccount(
                                                                            TxnUtils.asId(
                                                                                    GENESIS, spec))
                                                                    .setGas(197000)
                                                                    .setRecipientContract(
                                                                            txnRecord
                                                                                    .getResponseRecord()
                                                                                    .getContractCreateResult()
                                                                                    .getContractID())
                                                                    .setGasUsed(68492)
                                                                    .setOutput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            new byte
                                                                                                    [0]))
                                                                    .build())));
                                }),
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
                                (spec, opLog) -> {
                                    final var txnRecord = getTxnRecord(SECOND_CREATE_TXN);
                                    allRunFor(spec, txnRecord);
                                    allRunFor(
                                            spec,
                                            expectContractActionSidecarFor(
                                                    SECOND_CREATE_TXN,
                                                    List.of(
                                                            com.hedera.services.stream.proto
                                                                    .ContractAction.newBuilder()
                                                                    .setCallType(CREATE)
                                                                    .setCallingAccount(
                                                                            TxnUtils.asId(
                                                                                    GENESIS, spec))
                                                                    .setGas(197000)
                                                                    .setRecipientContract(
                                                                            txnRecord
                                                                                    .getResponseRecord()
                                                                                    .getContractCreateResult()
                                                                                    .getContractID())
                                                                    .setGasUsed(28692)
                                                                    .setOutput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            new byte
                                                                                                    [0]))
                                                                    .build())));
                                }),
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
                                (spec, opLog) -> {
                                    final var txnRecord = getTxnRecord(THIRD_CREATE_TXN);
                                    allRunFor(spec, txnRecord);
                                    allRunFor(
                                            spec,
                                            expectContractActionSidecarFor(
                                                    THIRD_CREATE_TXN,
                                                    List.of(
                                                            com.hedera.services.stream.proto
                                                                    .ContractAction.newBuilder()
                                                                    .setCallType(CREATE)
                                                                    .setCallingAccount(
                                                                            TxnUtils.asId(
                                                                                    GENESIS, spec))
                                                                    .setGas(197000)
                                                                    .setRecipientContract(
                                                                            txnRecord
                                                                                    .getResponseRecord()
                                                                                    .getContractCreateResult()
                                                                                    .getContractID())
                                                                    .setGasUsed(28692)
                                                                    .setOutput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            new byte
                                                                                                    [0]))
                                                                    .build())));
                                }),
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
                                                                AssociatePrecompileSuite
                                                                        .getNestedContractAddress(
                                                                                TRACEABILITY + "B",
                                                                                spec),
                                                                AssociatePrecompileSuite
                                                                        .getNestedContractAddress(
                                                                                TRACEABILITY + "C",
                                                                                spec))
                                                        .gas(initialGas)
                                                        .via(traceabilityTxn))))
                .then(
                        expectContractStateChangesSidecarFor(
                                traceabilityTxn,
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
                                (spec, opLog) -> {
                                    allRunFor(
                                            spec,
                                            expectContractActionSidecarFor(
                                                    traceabilityTxn,
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CALL)
                                                                    .setCallingAccount(
                                                                            TxnUtils.asId(
                                                                                    GENESIS, spec))
                                                                    .setGas(
                                                                            initialGas
                                                                                    - INTRINSIC_GAS)
                                                                    .setGasUsed(33979)
                                                                    .setRecipientContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            TRACEABILITY))
                                                                    .setOutput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            new byte
                                                                                                    [0]))
                                                                    .setInput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            Function
                                                                                                    .fromJson(
                                                                                                            getABIFor(
                                                                                                                    FunctionType
                                                                                                                            .FUNCTION,
                                                                                                                    "eetScenario1",
                                                                                                                    TRACEABILITY))
                                                                                                    .encodeCallWithArgs(
                                                                                                            wrapHexedSolidityAddress(
                                                                                                                    AssociatePrecompileSuite
                                                                                                                            .getNestedContractAddress(
                                                                                                                                    TRACEABILITY
                                                                                                                                            + "B",
                                                                                                                                    spec)),
                                                                                                            wrapHexedSolidityAddress(
                                                                                                                    AssociatePrecompileSuite
                                                                                                                            .getNestedContractAddress(
                                                                                                                                    TRACEABILITY
                                                                                                                                            + "C",
                                                                                                                                    spec)))
                                                                                                    .array()))
                                                                    .build(),
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CALL)
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
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            TupleType
                                                                                                    .parse(
                                                                                                            "(uint256)")
                                                                                                    .encode(
                                                                                                            Tuple
                                                                                                                    .of(
                                                                                                                            BigInteger
                                                                                                                                    .valueOf(
                                                                                                                                            55)))
                                                                                                    .array()))
                                                                    .setInput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            Function
                                                                                                    .fromJson(
                                                                                                            getABIFor(
                                                                                                                    FunctionType
                                                                                                                            .FUNCTION,
                                                                                                                    "getSlot0",
                                                                                                                    TRACEABILITY))
                                                                                                    .encodeCall(
                                                                                                            Tuple
                                                                                                                    .EMPTY)
                                                                                                    .array()))
                                                                    .build(),
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CALL)
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
                                                                    .setOutput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            new byte
                                                                                                    [0]))
                                                                    .setInput(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            Function
                                                                                                    .fromJson(
                                                                                                            getABIFor(
                                                                                                                    FunctionType
                                                                                                                            .FUNCTION,
                                                                                                                    "setSlot1",
                                                                                                                    TRACEABILITY))
                                                                                                    .encodeCall(
                                                                                                            Tuple
                                                                                                                    .of(
                                                                                                                            BigInteger
                                                                                                                                    .valueOf(
                                                                                                                                            55)))
                                                                                                    .array()))
                                                                    .build(),
                                                        ContractAction.newBuilder()
                                                            .setCallType(CALL)
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
                                                                        TRACEABILITY + SECOND))
                                                            .setOutput(
                                                                ByteStringUtils
                                                                    .wrapUnsafely(
                                                                        TupleType
                                                                            .parse(
                                                                                "(uint256)")
                                                                            .encode(
                                                                                Tuple
                                                                                    .of(
                                                                                        BigInteger
                                                                                            .valueOf(
                                                                                                12)))
                                                                            .array()))
                                                            .setInput(
                                                                ByteStringUtils
                                                                    .wrapUnsafely(
                                                                        Function
                                                                            .fromJson(
                                                                                getABIFor(
                                                                                    FunctionType
                                                                                        .FUNCTION,
                                                                                    "getSlot2",
                                                                                    TRACEABILITY))
                                                                            .encodeCall(
                                                                                Tuple
                                                                                    .EMPTY)
                                                                            .array()))
                                                            .build(),
                                                        ContractAction.newBuilder()
                                                            .setCallType(CALL)
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
                                                                        TRACEABILITY + SECOND))
                                                            .setOutput(
                                                                ByteStringUtils
                                                                    .wrapUnsafely(
new byte[0]))
                                                            .setInput(
                                                                ByteStringUtils
                                                                    .wrapUnsafely(
                                                                        Function
                                                                            .fromJson(
                                                                                getABIFor(
                                                                                    FunctionType
                                                                                        .FUNCTION,
                                                                                    "setSlot2",
                                                                                    TRACEABILITY))
                                                                            .encodeCall(
                                                                                Tuple
                                                                                    .of(BigInteger.valueOf(143)))
                                                                            .array()))
                                                            .build()
,                                                        ContractAction.newBuilder()
                                                            .setCallType(CALL)
                                                            .setCallingContract(
                                                                spec.registry()
                                                                    .getContractId(
                                                                        TRACEABILITY))
                                                            .setGas(946053)
                                                            .setGasUsed(1121)
                                                            .setCallDepth(1)
                                                            .setRecipientContract(
                                                                spec.registry()
                                                                    .getContractId(
                                                                        TRACEABILITY + SECOND))
                                                            .setOutput(
                                                                ByteStringUtils
                                                                    .wrapUnsafely(
                                                                        new byte[0]))
                                                            .setInput(
                                                                ByteStringUtils
                                                                    .wrapUnsafely(
                                                                        Function
                                                                            .fromJson(
                                                                                getABIFor(
                                                                                    FunctionType
                                                                                        .FUNCTION,
                                                                                    "callAddressGetSlot0",
                                                                                    TRACEABILITY))
                                                                            .encodeCallWithArgs(wrapHexedSolidityAddress(AssociatePrecompileSuite.getNestedContractAddress(TRACEABILITY + THIRD, spec)))
                                                                            .array()))
                                                            .build()

                                                    )));
                                }));
    }

    private Address wrapHexedSolidityAddress(String hexedSolidityAddress) {
        return Address.wrap(Address.toChecksumAddress("0x" + hexedSolidityAddress));
    }

    private ByteString formattedAssertionValue(long value) {
        return ByteString.copyFrom(
                Bytes.wrap(UInt256.valueOf(value)).trimLeadingZeros().toArrayUnsafe());
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
                        expectContractBytecodeSidecarFor(
                                firstTxn, EMPTY_CONSTRUCTOR_CONTRACT, EMPTY_CONSTRUCTOR_CONTRACT));
    }

    HapiApiSpec vanillaBytecodeSidecar2() {
        final var contract = "CreateTrivial";
        final String trivialCreate = "vanillaBytecodeSidecar2";
        final var firstTxn = "firstTxn";
        return defaultHapiSpec(trivialCreate)
                .given(uploadInitCode(contract))
                .when(contractCreate(contract).via(firstTxn))
                .then(expectContractBytecodeSidecarFor(firstTxn, contract, contract));
    }

    @SuppressWarnings("java:S5960")
    private HapiApiSpec assertSidecars() {
        return defaultHapiSpec("assertSidecars")
                // send a dummy transaction to trigger externalization of last sidecars
                .given(
                        withOpContext(
                                (spec, opLog) -> sidecarWatcher.finishWatchingAfterNextSidecar()),
                        cryptoCreate("externalizeFinalSidecars").delayBy(2000))
                .when()
                .then(
                        assertionsHold(
                                (spec, assertLog) -> {
                                    // wait until assertion thread is finished
                                    sidecarWatcherTask.join();

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
            String txnName, List<com.hedera.services.stream.proto.ContractAction> actions) {
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
                    final var initCode =
                            extractBytecodeUnhexed(getResourcePath(binFileName, ".bin"));
                    final byte[] params =
                            constructorArgs.length == 0
                                    ? new byte[] {}
                                    : CallTransaction.Function.fromJsonInterface(
                                                    getABIFor(
                                                            FunctionType.CONSTRUCTOR,
                                                            EMPTY,
                                                            binFileName))
                                            .encodeArguments(constructorArgs);
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
                                                            .setInitcode(
                                                                    initCode.concat(
                                                                            ByteStringUtils
                                                                                    .wrapUnsafely(
                                                                                            params)))
                                                            .setRuntimeBytecode(
                                                                    ByteString.copyFrom(
                                                                            spec.registry()
                                                                                    .getBytes(
                                                                                            runtimeBytecode)))
                                                            .build())
                                            .build()));
                });
    }

    private static void initialize() throws IOException {
        final var recordStreamFolderPath =
                HapiApiSpec.isRunningInCi()
                        ? HapiApiSpec.ciPropOverrides().get(RECORD_STREAM_FOLDER_PATH_PROPERTY_KEY)
                        : HapiSpecSetup.getDefaultPropertySource()
                                .get(RECORD_STREAM_FOLDER_PATH_PROPERTY_KEY);
        sidecarWatcher = new SidecarWatcher(Paths.get(recordStreamFolderPath));
        sidecarWatcher.prepareInfrastructure();
        sidecarWatcherTask =
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                sidecarWatcher.watch();
                            } catch (IOException e) {
                                log.fatal(
                                        "An invalid sidecar file was generated from the consensus"
                                                + " node.",
                                        e);
                            }
                            sidecarWatcher.tearDown();
                        });
    }
}
