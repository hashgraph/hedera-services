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

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilStateChange.stateChangesToGrpc;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.streams.assertions.EventualRecordStreamAssertion.recordStreamLocFor;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.bdd.spec.assertions.StateChange;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.verification.traceability.ExpectedSidecar;
import com.hedera.services.bdd.spec.verification.traceability.MismatchedSidecar;
import com.hedera.services.bdd.spec.verification.traceability.SidecarWatcher;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * A suite that is aware of externalized sidecar files, provides utilities to verify sidecar records.
 * This suite is meant to be extended by other suites that need to test sidecar files.
 *
 * @author vyanev
 */
@SuppressWarnings("java:S5960") // "assertions should not be used in production code" - not production
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class SidecarAwareHapiSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(SidecarAwareHapiSuite.class);

    /**
     * The sidecar watcher instance that will be used for incoming sidecar files.
     */
    private static SidecarWatcher sidecarWatcher;

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
                    TransactionSidecarRecord.newBuilder()
                            .setConsensusTimestamp(txnRecord.getResponseRecord().getConsensusTimestamp())
                            .setActions(ContractActions.newBuilder()
                                    .addAllContractActions(actions)
                                    .build())
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
                    TransactionSidecarRecord.newBuilder()
                            .setConsensusTimestamp(txnRecord.getResponseRecord().getConsensusTimestamp())
                            .setStateChanges(ContractStateChanges.newBuilder()
                                    .addAllContractStateChanges(stateChangesToGrpc(stateChanges, spec))
                                    .build())
                            .build()));
        });
    }

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
     * Asserts that all expected sidecar records have been externalized.
     * @return A {@link CustomSpecAssert} that will assert that all expected sidecar records have been externalized.
     */
    protected static CustomSpecAssert assertContainsAllExpectedSidecarRecords() {
        return assertionsHold((spec, assertLog) -> {
            assertTrue(
                    sidecarWatcher.containsAllExpectedStateChanges(),
                    sidecarWatcher.getMismatchErrors(MismatchedSidecar::hasStateChanges));
            assertTrue(
                    sidecarWatcher.containsAllExpectedContractActions(),
                    sidecarWatcher.getMismatchErrors(MismatchedSidecar::hasActions));
            assertTrue(
                    sidecarWatcher.thereAreNoPendingSidecars(),
                    "There are some sidecars that have not been yet"
                            + " externalized in the sidecar files after all"
                            + " specs: " + sidecarWatcher.getPendingErrors());
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
