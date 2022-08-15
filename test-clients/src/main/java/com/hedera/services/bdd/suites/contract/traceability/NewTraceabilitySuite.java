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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.extractBytecodeUnhexed;
import static com.hedera.services.bdd.suites.contract.Utils.getResourcePath;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NewTraceabilitySuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(NewTraceabilitySuite.class);

    private static SidecarWatcher sidecarWatcher;
    private static CompletableFuture<Void> sidecarWatcherTask;

    public static void main(String... args) {
        new NewTraceabilitySuite().runSuiteSync();
    }

    private static void initWatching() {
        sidecarWatcher = new SidecarWatcher();
        sidecarWatcherTask =
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                sidecarWatcher.startWatching(
                                        HapiApiSpec.isRunningInCi()
                                                ? HapiApiSpec.ciPropOverrides()
                                                        .get("recordStream.path")
                                                : HapiSpecSetup.getDefaultPropertySource()
                                                        .get("recordStream.path"));
                            } catch (IOException e) {
                                log.warn("Sidecar watching couldn't be initialized.", e);
                            }
                        });
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        initWatching();
        return List.of(vanillaBytecodeSidecar(), vanillaBytecodeSidecar2(), assertSidecars());
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
                        contractBytecodeSidecar(
                                EMPTY_CONSTRUCTOR_CONTRACT, vanillaBytecodeSidecar, firstTxn));
    }

    HapiApiSpec vanillaBytecodeSidecar2() {
        final var contract = "CreateTrivial";
        final String trivialCreate = "vanillaBytecodeSidecar2";
        final var firstTxn = "firstTxn";
        return defaultHapiSpec(trivialCreate)
                .given(uploadInitCode(contract))
                .when(contractCreate(contract).via(firstTxn))
                .then(contractBytecodeSidecar(contract, trivialCreate, firstTxn));
    }

    @SuppressWarnings("java:S5960")
    private HapiApiSpec assertSidecars() {
        return defaultHapiSpec("assertSidecars")
                // send a dummy transaction to trigger externalization of last sidecars
                .given(
                        withOpContext(
                                (spec, opLog) -> sidecarWatcher.finishWatchingAfterNextSidecar()),
                        cryptoCreate("assertSidecars").delayBy(2000))
                .when()
                .then(
                        assertionsHold(
                                (spec, assertLog) -> {
                                    // wait until assertion thread is finished
                                    sidecarWatcherTask.join();

                                    assertTrue(
                                            sidecarWatcher.thereAreNoMismatchedSidecars(),
                                            sidecarWatcher.printErrors());
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

    private CustomSpecAssert contractBytecodeSidecar(
            final String contractName, final String specName, final String contractCreateTxn) {
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
                            extractBytecodeUnhexed(getResourcePath(contractName, ".bin"));
                    sidecarWatcher.addExpectedSidecar(
                            Pair.of(
                                    specName,
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
}
