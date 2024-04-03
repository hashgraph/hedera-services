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

@SuppressWarnings("java:S5960") // "assertions should not be used in production code" - not production
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class SidecarAwareHapiSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(SidecarAwareHapiSuite.class);

    private static SidecarWatcher sidecarWatcher;

    protected static void addExpectedSidecar(ExpectedSidecar expectedSidecar) {
        sidecarWatcher.addExpectedSidecar(expectedSidecar);
    }

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

    protected static CustomSpecAssert initializeSidecarWatcher() {
        return withOpContext((spec, opLog) -> {
            Path path = Paths.get(recordStreamLocFor(spec));
            log.info("Watching for sidecars at absolute path {}", path.toAbsolutePath());
            sidecarWatcher = new SidecarWatcher(path);
            sidecarWatcher.watch();
        });
    }

    protected static CustomSpecAssert tearDownSidecarWatcher() {
        return withOpContext((spec, opLog) -> {
            // send a dummy transaction to trigger externalization of last sidecars
            allRunFor(spec, cryptoCreate("externalizeFinalSidecars").delayBy(2000));
            sidecarWatcher.waitUntilFinished();
            sidecarWatcher.tearDown();
        });
    }

    protected static CustomSpecAssert assertContainsAllExpectedContractActions() {
        return assertionsHold((spec, assertLog) -> {
            assertTrue(sidecarWatcher.containsAllExpectedContractActions(), sidecarWatcher.getMismatchErrors());
            assertTrue(
                    sidecarWatcher.thereAreNoPendingSidecars(),
                    "There are some sidecars that have not been yet"
                            + " externalized in the sidecar files after all"
                            + " specs: " + sidecarWatcher.getPendingErrors());
        });
    }

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
