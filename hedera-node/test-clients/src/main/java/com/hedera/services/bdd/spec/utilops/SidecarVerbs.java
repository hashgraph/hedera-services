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

package com.hedera.services.bdd.spec.utilops;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilStateChange.stateChangesToGrpc;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.traceability.EncodingUtils.getInitcode;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.StateChange;
import com.hedera.services.bdd.spec.assertions.matchers.TransactionSidecarRecordMatcher;
import com.hedera.services.bdd.spec.verification.traceability.ExpectedSidecar;
import com.hedera.services.bdd.spec.verification.traceability.SidecarWatcher;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides utilities to verify sidecar records.
 *
 * @author vyanev
 */
// assertions should not be used in production code
@SuppressWarnings("java:S5960")
public class SidecarVerbs {
    private static final String RUNTIME_CODE = "runtimeBytecode";

    private interface MatcherSpec {
        void customize(TransactionSidecarRecordMatcher.Builder builder, HapiSpec spec, TransactionRecord originRecord);
    }

    /**
     * Add an expected sidecar to the sidecar watcher instance.
     * @param expectedSidecar The {@link ExpectedSidecar} to add.
     */
    protected static void addExpectedSidecar(final ExpectedSidecar expectedSidecar) {
//        sidecarWatcher.addExpectedSidecar(expectedSidecar);
    }

    public static AtomicReference<SidecarWatcher> GLOBAL_WATCHER = new AtomicReference<>();

    /**
     * Expect a sidecar file to be generated for the given transaction name with the given contract state changes,
     * for the default {@link SidecarWatcher}.
     *
     * @param txnName The name of the transaction to expect the sidecar for.
     * @param stateChanges The contract state changes to expect in the sidecar.
     * @return A {@link CustomSpecAssert} that will expect the sidecar file to be generated.
     */
    public static CustomSpecAssert expectContractStateChangesSidecarFor(
            @NonNull final String txnName,
            @NonNull final List<StateChange> stateChanges) {
        requireNonNull(txnName);
        requireNonNull(stateChanges);
        return expectSidecarRecord(txnName, (builder, spec, originRecord) -> builder.setStateChanges(ContractStateChanges.newBuilder()
                .addAllContractStateChanges(stateChangesToGrpc(stateChanges, spec))
                .build()));
    }

    /**
     * Expect a sidecar file to be generated for the given transaction name with the given contract actions,
     * for the default {@link SidecarWatcher}.
     *
     * @param txnName The name of the transaction to expect the sidecar for.
     * @param actions The contract actions to expect in the sidecar.
     * @return a {@link CustomSpecAssert} that will expect the sidecar file to be generated.
     */
    public static CustomSpecAssert expectContractActionSidecarFor(
            @NonNull final String txnName,
            @NonNull final List<ContractAction> actions) {
        return expectSidecarRecord(txnName, (builder, spec, originRecord) -> builder.setActions(ContractActions.newBuilder()
                .addAllContractActions(actions)
                .build()));
    }

    /**
     * Expect a sidecar file to be generated with a sidecar record for the given transaction name with the
     * given contract bytecode, for the in-scope {@link SidecarWatcher}.
     *
     * @param txnName The name of the transaction to expect the sidecar for.
     * @param contractName The name of the contract to expect the bytecode for.
     * @param binFileName The name of the bin file to get the init-code from.
     * @param constructorArgs The constructor arguments to use for the init-code.
     * @return {@link CustomSpecAssert} expecting the sidecar file to be generated.
     */
    public static CustomSpecAssert expectContractBytecodeSidecarFor(
            @NonNull final String txnName,
            @NonNull final String contractName,
            @NonNull final String binFileName,
            @NonNull final Object... constructorArgs) {
        requireNonNull(txnName);
        requireNonNull(contractName);
        requireNonNull(binFileName);
        requireNonNull(constructorArgs);
        return expectSidecarRecord(txnName, (builder, spec, originRecord) -> {
           builder.setBytecode(ContractBytecode.newBuilder()
                   .setContractId(getTxnRecord(txnName)
                           .getResponseRecord()
                           .getContractCreateResult()
                           .getContractID())
                   .setInitcode(getInitcode(binFileName, constructorArgs))
                   .setRuntimeBytecode(ByteString.copyFrom(spec.registry().getBytes(RUNTIME_CODE)))
                   .build());
        });
    }

    /**
     * Expect a sidecar file to be generated with a record (for the given transaction)
     * that contains the init-code from the given bin file, for the in-scope {@link SidecarWatcher}.
     *
     * @param txnName The name of the transaction to expect the sidecar for.
     * @param binFileName The name of the bin file to get the init-code from.
     * @param constructorArgs The constructor arguments to use for the init-code.
     * @return {@link CustomSpecAssert} expecting the sidecar file to be generated.
     */
    public static CustomSpecAssert expectFailedContractBytecodeSidecarFor(
            @NonNull final String txnName,
            @NonNull final String binFileName,
            @NonNull final Object... constructorArgs) {
        requireNonNull(txnName);
        requireNonNull(binFileName);
        requireNonNull(constructorArgs);
        return expectSidecarRecord(txnName, (builder, spec, originRecord) -> {
            builder.setBytecode(ContractBytecode.newBuilder()
                    .setInitcode(getInitcode(binFileName, constructorArgs))
                    .build());
        });
    }


    /**
     * Expect a sidecar file to be generated with a record (for the given transaction)
     * that contains the runtime bytecode of the given contract.
     *
     * @param txnName The name of the transaction to expect the sidecar for.
     * @param contractName The name of the contract to expect the bytecode for.
     * @return {@link CustomSpecAssert} expecting the sidecar file to be generated.
     */
    public static CustomSpecAssert expectContractBytecodeWithMinimalFieldsSidecarFor(
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

    private static CustomSpecAssert expectSidecarRecord(
            @NonNull final String txnName,
            @NonNull final MatcherSpec matcherSpec) {
        return withOpContext((spec, opLog) -> {
            final var txnRecord = getTxnRecord(txnName);
            allRunFor(spec, txnRecord);
            final var builder = TransactionSidecarRecordMatcher.newBuilder()
                    .setConsensusTimestamp(txnRecord.getResponseRecord().getConsensusTimestamp());
            matcherSpec.customize(builder, spec, txnRecord.getResponseRecord());
            final var watcher = Optional.ofNullable(spec.getSidecarWatcher())
                    .or(() -> Optional.ofNullable(GLOBAL_WATCHER.get()))
                    .orElseThrow();
            watcher.addExpectedSidecar(new ExpectedSidecar(spec.getName(), builder.build()));
        });
    }

    /**
     * Expect a sidecar file to be generated with a record (for the given transaction)
     * that contains the runtime bytecode of the given contract.
     *
     * @param txnName The name of the transaction.
     * @param contractName The name of the contract.
     * @return {@link CustomSpecAssert} expecting the sidecar file to be generated.
     */
    public static CustomSpecAssert expectContractBytecode(final String txnName, final String contractName) {
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
     * Expect a sidecar file to be generated with a record
     * (for the transaction with the given consensus timestamp)
     * that contains the given contract ID, init-code, and runtime code.
     *
     * @param specName The name of the spec.
     * @param consensusTimestamp The consensus timestamp of the transaction.
     * @param contractID The contract ID.
     * @param initCode The init bytecode of the contract.
     * @param runtimeCode The runtime bytecode of the contract.
     */
    public static void expectContractBytecode(
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

}
