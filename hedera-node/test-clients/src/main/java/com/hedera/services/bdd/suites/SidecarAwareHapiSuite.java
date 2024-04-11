/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilStateChange.stateChangesToGrpc;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.streams.assertions.EventualRecordStreamAssertion.recordStreamLocFor;
import static com.hedera.services.bdd.suites.contract.traceability.EncodingUtils.getInitcode;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.assertions.StateChange;
import com.hedera.services.bdd.spec.assertions.matchers.TransactionSidecarRecordMatcher;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.verification.traceability.ExpectedSidecar;
import com.hedera.services.bdd.spec.verification.traceability.MismatchedSidecar;
import com.hedera.services.bdd.spec.verification.traceability.SidecarWatcher;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A suite that is aware of externalized sidecar files, provides utilities to verify sidecar records.
 * This suite is meant to be extended by other suites that need to test sidecar files.
 *
 * @author vyanev
 */
@SuppressWarnings("java:S5960") // "assertions should not be used in production code" - not production
public abstract class SidecarAwareHapiSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(SidecarAwareHapiSuite.class);
    private static final String RUNTIME_CODE = "runtimeBytecode";

    /**
     * The sidecar watcher instance that will be used for incoming sidecar files.
     */
    private static SidecarWatcher sidecarWatcher;

    /**
     * Initialize the sidecar watcher for the current spec.
     * @return A {@link CustomSpecAssert} that will initialize the sidecar watcher.
     */
    protected static CustomSpecAssert initializeSidecarWatcher() {
        return withOpContext((spec, opLog) -> {
            final Path path = Paths.get(recordStreamLocFor(spec));
            if (LOG.isInfoEnabled()) {
                LOG.info("Watching for sidecars at absolute path {}", path.toAbsolutePath());
            }
            sidecarWatcher = new SidecarWatcher(path);
            sidecarWatcher.watch();
        });
    }

    /**
     * Waits for expected sidecars and tears down the sidecar watcher for the current spec.
     * @return A {@link CustomSpecAssert} that will tear down the sidecar watcher.
     */
    protected static CustomSpecAssert tearDownSidecarWatcher() {
        return withOpContext((spec, opLog) -> {
            // send a dummy transaction to trigger externalization of last sidecars
            allRunFor(spec, cryptoCreate("externalizeFinalSidecars").delayBy(2000));
            sidecarWatcher.waitUntilFinished();
            sidecarWatcher.tearDown();
        });
    }

    /**
     * Add an expected sidecar to the sidecar watcher instance.
     * @param expectedSidecar The {@link ExpectedSidecar} to add.
     */
    protected static void addExpectedSidecar(final ExpectedSidecar expectedSidecar) {
        sidecarWatcher.addExpectedSidecar(expectedSidecar);
    }

    /**
     * Expect a sidecar file to be generated for the given transaction name with the given contract actions.
     * @param txnName The name of the transaction to expect the sidecar for.
     * @param actions The contract actions to expect in the sidecar.
     * @return A {@link CustomSpecAssert} that will expect the sidecar file to be generated.
     */
    protected static CustomSpecAssert expectContractActionSidecarFor(
            final String txnName, final List<ContractAction> actions) {
        return withOpContext((spec, opLog) -> {
            final var txnRecord = getTxnRecord(txnName);
            allRunFor(spec, txnRecord);
            addExpectedSidecar(new ExpectedSidecar(
                    spec.getName(),
                    TransactionSidecarRecordMatcher.newBuilder()
                            .setConsensusTimestamp(txnRecord.getResponseRecord().getConsensusTimestamp())
                            .setActions(containsInAnyOrder(
                                    actions,
                                    action -> withEqualFields(action)
                                            .ignoringFields("memoizedIsInitialized")
                                            .withCustomMatchersForFields(
                                                    Map.of("gas", within32Units(action.getGas()),
                                                            "gasUsed", within32Units(action.getGasUsed())))))
                            .build()));
        });
    }

    /**
     * Expect a sidecar file to be generated for the given transaction name with the given contract state changes.
     * @param txnName The name of the transaction to expect the sidecar for.
     * @param stateChanges The contract state changes to expect in the sidecar.
     * @return A {@link CustomSpecAssert} that will expect the sidecar file to be generated.
     */
    protected static CustomSpecAssert expectContractStateChangesSidecarFor(
            final String txnName, final List<StateChange> stateChanges) {
        return withOpContext((spec, opLog) -> {
            final var txnRecord = getTxnRecord(txnName);
            allRunFor(spec, txnRecord);
            addExpectedSidecar(new ExpectedSidecar(
                    spec.getName(),
                    TransactionSidecarRecordMatcher.newBuilder()
                            .setConsensusTimestamp(txnRecord.getResponseRecord().getConsensusTimestamp())
                            .setStateChanges(containsInAnyOrder(
                                    stateChangesToGrpc(stateChanges, spec),
                                    stateChange ->
                                            withEqualFields(stateChange).ignoringFields("memoizedIsInitialized")))
                            .build()));
        });
    }

    /**
     * Expect a sidecar file to be generated for the given transaction name with the given contract bytecode.
     *
     * @param txnName The name of the transaction to expect the sidecar for.
     * @param contractName The name of the contract to expect the bytecode for.
     * @param binFileName The name of the bin file to get the init-code from.
     * @param constructorArgs The constructor arguments to use for the init-code.
     * @return A {@link CustomSpecAssert} that will expect the sidecar file to be generated.
     */
    protected static CustomSpecAssert expectContractBytecodeSidecarFor(
            final String txnName,
            final String contractName,
            final String binFileName,
            final Object... constructorArgs) {
        return withOpContext((spec, opLog) -> {
            final var txnRecord = getTxnRecord(txnName);
            final var contractBytecode = getContractBytecode(contractName).saveResultTo(RUNTIME_CODE);
            allRunFor(spec, txnRecord, contractBytecode);
            final var consensusTimestamp = txnRecord.getResponseRecord().getConsensusTimestamp();
            final var initCode = getInitcode(binFileName, constructorArgs);
            addExpectedSidecar(new ExpectedSidecar(
                    spec.getName(),
                    TransactionSidecarRecordMatcher.newBuilder()
                            .setConsensusTimestamp(consensusTimestamp)
                            .setBytecode(ContractBytecode.newBuilder()
                                    .setContractId(txnRecord
                                            .getResponseRecord()
                                            .getContractCreateResult()
                                            .getContractID())
                                    .setInitcode(initCode)
                                    .setRuntimeBytecode(
                                            ByteString.copyFrom(spec.registry().getBytes(RUNTIME_CODE)))
                                    .build())
                            .build()));
        });
    }

    /**
     * Expect a sidecar file to be generated with a record (for the given transaction)
     * that contains the init-code from the given bin file.
     *
     * @param txnName The name of the transaction to expect the sidecar for.
     * @param binFileName The name of the bin file to get the init-code from.
     * @param constructorArgs The constructor arguments to use for the init-code.
     * @return A {@link CustomSpecAssert} that will expect the sidecar file to be generated.
     */
    protected static CustomSpecAssert expectFailedContractBytecodeSidecarFor(
            final String txnName, final String binFileName, final Object... constructorArgs) {
        return withOpContext((spec, opLog) -> {
            final var txnRecord = getTxnRecord(txnName);
            allRunFor(spec, txnRecord);
            final var consensusTimestamp = txnRecord.getResponseRecord().getConsensusTimestamp();
            final var initCode = getInitcode(binFileName, constructorArgs);
            addExpectedSidecar(new ExpectedSidecar(
                    spec.getName(),
                    TransactionSidecarRecordMatcher.newBuilder()
                            .setConsensusTimestamp(consensusTimestamp)
                            .setBytecode(ContractBytecode.newBuilder()
                                    .setInitcode(initCode)
                                    .build())
                            .build()));
        });
    }

    /**
     * Expect a sidecar file to be generated with a record (for the given transaction)
     * that contains the runtime bytecode of the given contract.
     *
     * @param txnName The name of the transaction to expect the sidecar for.
     * @param contractName The name of the contract to expect the bytecode for.
     * @return A {@link CustomSpecAssert} that will expect the sidecar file to be generated.
     */
    protected static CustomSpecAssert expectContractBytecodeWithMinimalFieldsSidecarFor(
            final String txnName, final String contractName) {
        return withOpContext((spec, opLog) -> {
            final var txnRecord = getTxnRecord(txnName).andAllChildRecords();
            final var contractBytecode = getContractBytecode(contractName).saveResultTo(RUNTIME_CODE);
            allRunFor(spec, txnRecord, contractBytecode);
            final var consensusTimestamp =
                    txnRecord.getFirstNonStakingChildRecord().getConsensusTimestamp();
            addExpectedSidecar(new ExpectedSidecar(
                    spec.getName(),
                    TransactionSidecarRecordMatcher.newBuilder()
                            .setConsensusTimestamp(consensusTimestamp)
                            .setBytecode(ContractBytecode.newBuilder()
                                    .setContractId(txnRecord
                                            .getResponseRecord()
                                            .getContractCreateResult()
                                            .getContractID())
                                    .setRuntimeBytecode(
                                            ByteString.copyFrom(spec.registry().getBytes(RUNTIME_CODE)))
                                    .build())
                            .build()));
        });
    }

    /**
     * Expect a sidecar file to be generated with a record (for the given transaction)
     * that contains the runtime bytecode of the given contract.
     *
     * @param txnName The name of the transaction.
     * @param contractName The name of the contract.
     * @return {@link CustomSpecAssert} that expects the sidecar file to be generated.
     */
    protected static CustomSpecAssert expectContractBytecode(final String txnName,
                                                             final String contractName) {
        return withOpContext((spec, opLog) -> {
            final var txnRecord = getTxnRecord(txnName);
            final var contractBytecode = getContractBytecode(contractName).saveResultTo(RUNTIME_CODE);
            allRunFor(spec, txnRecord, contractBytecode);
            final var consensusTimestamp = txnRecord.getResponseRecord().getConsensusTimestamp();
            addExpectedSidecar(new ExpectedSidecar(
                    spec.getName(),
                    TransactionSidecarRecordMatcher.newBuilder()
                            .setConsensusTimestamp(consensusTimestamp)
                            .setBytecode(ContractBytecode.newBuilder()
                                    .setContractId(txnRecord
                                            .getResponseRecord()
                                            .getContractCreateResult()
                                            .getContractID())
                                    .setRuntimeBytecode(
                                            ByteString.copyFrom(spec.registry().getBytes(RUNTIME_CODE)))
                                    .build())
                            .build()));
        });
    }

    /**
     * Expect a sidecar file to be generated with a record (for the transaction with the given consensus timestamp)
     * that contains the given contract ID, init-code, and runtime code.
     *
     * @param specName The name of the spec.
     * @param consensusTimestamp The consensus timestamp of the transaction.
     * @param contractID The contract ID.
     * @param initCode The init bytecode of the contract.
     * @param runtimeCode The runtime bytecode of the contract.
     */
    protected static void expectContractBytecode(
            final String specName,
            final Timestamp consensusTimestamp,
            final ContractID contractID,
            final ByteString initCode,
            final ByteString runtimeCode) {
        addExpectedSidecar(new ExpectedSidecar(
                specName,
                TransactionSidecarRecordMatcher.newBuilder()
                        .setConsensusTimestamp(consensusTimestamp)
                        .setBytecode(ContractBytecode.newBuilder()
                                // recipient should be the original hollow account
                                // id as a contract
                                .setContractId(contractID)
                                .setInitcode(initCode)
                                .setRuntimeBytecode(runtimeCode)
                                .build())
                        .build()));
    }

    /**
     * Asserts that all expected sidecar records for contract actions have been externalized.
     * @return A {@link CustomSpecAssert} that will assert that all expected sidecar records have been externalized.
     */
    protected static CustomSpecAssert assertContainsAllExpectedContractActions() {
        return assertionsHold((spec, assertLog) -> {
            assertTrue(
                    sidecarWatcher.containsAllExpectedSidecarRecords(MismatchedSidecar::hasActions),
                    sidecarWatcher.getMismatchErrors(MismatchedSidecar::hasActions));
            assertTrue(
                    sidecarWatcher.thereAreNoPendingSidecars(),
                    "There are some contract actions that have not been yet externalized"
                            + " in the sidecar files after all specs: " + sidecarWatcher.getPendingErrors());
        });
    }

    /**
     * Asserts that all expected sidecar records for contract state changes have been externalized.
     * @return A {@link CustomSpecAssert} that will assert that all expected sidecar records have been externalized.
     */
    protected static CustomSpecAssert assertContainsAllExpectedStateChanges() {
        return assertionsHold((spec, assertLog) -> {
            assertTrue(
                    sidecarWatcher.containsAllExpectedSidecarRecords(MismatchedSidecar::hasStateChanges),
                    sidecarWatcher.getMismatchErrors(MismatchedSidecar::hasStateChanges));
            assertTrue(
                    sidecarWatcher.thereAreNoPendingSidecars(),
                    "There are some contract state changes that have not been yet externalized" +
                            " in the sidecar files after all specs: " + sidecarWatcher.getPendingErrors());
        });
    }

    /**
     * Asserts that there are no mismatched sidecars.
     * @return A {@link CustomSpecAssert} that will assert that there are no mismatched sidecars.
     */
    protected static CustomSpecAssert assertNoMismatchedSidecars() {
        return assertionsHold((spec, assertLog) -> {
            assertTrue(sidecarWatcher.thereAreNoMismatchedSidecars(), sidecarWatcher.getMismatchErrors());
            assertTrue(
                    sidecarWatcher.thereAreNoPendingSidecars(),
                    "There are some sidecars that have not been yet"
                            + " externalized in the sidecar files after all"
                            + " specs: " + sidecarWatcher.getPendingErrors());
        });
    }
}
