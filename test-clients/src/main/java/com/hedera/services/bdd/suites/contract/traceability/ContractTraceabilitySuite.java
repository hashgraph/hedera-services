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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.assertions.StateChange;
import com.hedera.services.bdd.spec.assertions.StorageChange;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCreate;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;

public class ContractTraceabilitySuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(ContractTraceabilitySuite.class);
    private static final String TRACEABILITY = "Traceability";
    private static final String TRACEABILITY_CALLCODE = "TraceabilityCallcode";
    private static final String FIRST = EMPTY;
    private static final String SECOND = "B";
    private static final String THIRD = "C";
    private final String traceabilityTxn = "nestedtxn";

    public static void main(String... args) {
        new ContractTraceabilitySuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                traceabilityE2EScenario2(),
                traceabilityE2EScenario3(),
                traceabilityE2EScenario4(),
                traceabilityE2EScenario5(),
                traceabilityE2EScenario6(),
                traceabilityE2EScenario7(),
                traceabilityE2EScenario8(),
                traceabilityE2EScenario9(),
                traceabilityE2EScenario10(),
                traceabilityE2EScenario11());
    }

    private HapiApiSpec traceabilityE2EScenario2() {
        return defaultHapiSpec("traceabilityE2EScenario2")
                .given(
                        setup(
                                TRACEABILITY,
                                createContractWithSlotValues(TRACEABILITY, FIRST, 0, 0, 0),
                                createContractWithSlotValues(TRACEABILITY, SECOND, 0, 0, 99),
                                createContractWithSlotValues(TRACEABILITY, THIRD, 0, 88, 0)))
                .when(executeScenario(TRACEABILITY, "eetScenario2", SUCCESS))
                .then(
                        assertStateChanges(
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
                                                        formattedAssertionValue(143)))),
                        tearDown());
    }

    private HapiApiSpec traceabilityE2EScenario3() {
        return defaultHapiSpec("traceabilityE2EScenario3")
                .given(
                        setup(
                                TRACEABILITY,
                                createContractWithSlotValues(TRACEABILITY, FIRST, 55, 2, 2),
                                createContractWithSlotValues(TRACEABILITY, SECOND, 0, 0, 12),
                                createContractWithSlotValues(TRACEABILITY, THIRD, 0, 11, 0)))
                .when(executeScenario(TRACEABILITY, "eetScenario3", SUCCESS))
                .then(
                        assertStateChanges(
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
                                                        formattedAssertionValue(0)))),
                        tearDown());
    }

    private HapiApiSpec traceabilityE2EScenario4() {
        return defaultHapiSpec("traceabilityE2EScenario4")
                .given(
                        setup(
                                TRACEABILITY,
                                createContractWithSlotValues(TRACEABILITY, FIRST, 2, 3, 4),
                                createContractWithSlotValues(TRACEABILITY, SECOND, 0, 0, 0),
                                createContractWithSlotValues(TRACEABILITY, THIRD, 0, 0, 0)))
                .when(executeScenario(TRACEABILITY, "eetScenario4", SUCCESS))
                .then(
                        assertStateChanges(
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
                                                        formattedAssertionValue(4)))),
                        tearDown());
    }

    private HapiApiSpec traceabilityE2EScenario5() {
        return defaultHapiSpec("traceabilityE2EScenario5")
                .given(
                        setup(
                                TRACEABILITY,
                                createContractWithSlotValues(TRACEABILITY, FIRST, 55, 2, 2),
                                createContractWithSlotValues(TRACEABILITY, SECOND, 0, 0, 12),
                                createContractWithSlotValues(TRACEABILITY, THIRD, 4, 1, 0)))
                .when(executeScenario(TRACEABILITY, "eetScenario5", SUCCESS))
                .then(
                        assertStateChanges(
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
                                                        formattedAssertionValue(1)))),
                        tearDown());
    }

    private HapiApiSpec traceabilityE2EScenario6() {
        return defaultHapiSpec("traceabilityE2EScenario6")
                .given(
                        setup(
                                TRACEABILITY,
                                createContractWithSlotValues(TRACEABILITY, FIRST, 2, 3, 4),
                                createContractWithSlotValues(TRACEABILITY, SECOND, 0, 0, 3),
                                createContractWithSlotValues(TRACEABILITY, THIRD, 0, 1, 0)))
                .when(executeScenario(TRACEABILITY, "eetScenario6", SUCCESS))
                .then(
                        assertStateChanges(
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
                                                        formattedAssertionValue(1)))),
                        tearDown());
    }

    private HapiApiSpec traceabilityE2EScenario7() {
        return defaultHapiSpec("traceabilityE2EScenario7")
                .given(
                        setup(
                                TRACEABILITY_CALLCODE,
                                createContractWithSlotValues(
                                        TRACEABILITY_CALLCODE, FIRST, 55, 2, 2),
                                createContractWithSlotValues(
                                        TRACEABILITY_CALLCODE, SECOND, 0, 0, 12),
                                createContractWithSlotValues(
                                        TRACEABILITY_CALLCODE, THIRD, 4, 1, 0)))
                .when(executeScenario(TRACEABILITY_CALLCODE, "eetScenario7", SUCCESS))
                .then(
                        assertStateChanges(
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
                                                        formattedAssertionValue(524)))),
                        tearDown());
    }

    private HapiApiSpec traceabilityE2EScenario8() {
        return defaultHapiSpec("traceabilityE2EScenario8")
                .given(
                        setup(
                                TRACEABILITY_CALLCODE,
                                createContractWithSlotValues(
                                        TRACEABILITY_CALLCODE, FIRST, 55, 2, 2),
                                createContractWithSlotValues(
                                        TRACEABILITY_CALLCODE, SECOND, 0, 0, 12),
                                createContractWithSlotValues(
                                        TRACEABILITY_CALLCODE, THIRD, 4, 1, 0)))
                .when(executeScenario(TRACEABILITY_CALLCODE, "eetScenario8", SUCCESS))
                .then(
                        assertStateChanges(
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
                                                        formattedAssertionValue(524)))),
                        tearDown());
    }

    private HapiApiSpec traceabilityE2EScenario9() {
        return defaultHapiSpec("traceabilityE2EScenario9")
                .given(
                        setup(
                                TRACEABILITY,
                                createContractWithSlotValues(TRACEABILITY, FIRST, 55, 2, 2),
                                createContractWithSlotValues(TRACEABILITY, SECOND, 0, 0, 12),
                                createContractWithSlotValues(TRACEABILITY, THIRD, 0, 1, 0)))
                .when(executeScenario(TRACEABILITY, "eetScenario9", CONTRACT_REVERT_EXECUTED))
                .then(
                        assertStateChanges(
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
                                                        formattedAssertionValue(1)))),
                        tearDown());
    }

    private HapiApiSpec traceabilityE2EScenario10() {
        return defaultHapiSpec("traceabilityE2EScenario10")
                .given(
                        setup(
                                TRACEABILITY,
                                createContractWithSlotValues(TRACEABILITY, FIRST, 2, 3, 4),
                                createContractWithSlotValues(TRACEABILITY, SECOND, 0, 0, 3),
                                createContractWithSlotValues(TRACEABILITY, THIRD, 0, 1, 0)))
                .when(executeScenario(TRACEABILITY, "eetScenario10", SUCCESS))
                .then(
                        assertStateChanges(
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
                                                        formattedAssertionValue(1)))),
                        tearDown());
    }

    private HapiApiSpec traceabilityE2EScenario11() {
        return defaultHapiSpec("traceabilityE2EScenario11")
                .given(
                        setup(
                                TRACEABILITY,
                                createContractWithSlotValues(TRACEABILITY, FIRST, 2, 3, 4),
                                createContractWithSlotValues(TRACEABILITY, SECOND, 0, 0, 3),
                                createContractWithSlotValues(TRACEABILITY, THIRD, 0, 1, 0)))
                .when(executeScenario(TRACEABILITY, "eetScenario11", SUCCESS))
                .then(
                        assertStateChanges(
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
                                                        formattedAssertionValue(0)))),
                        tearDown());
    }

    private HapiSpecOperation[] setup(
            final String contract,
            final HapiContractCreate contractA,
            final HapiContractCreate contractB,
            final HapiContractCreate contractC) {
        return new HapiSpecOperation[] {
            UtilVerbs.overriding("contracts.enableTraceability", "true"),
            uploadInitCode(contract),
            contractA,
            contractB,
            contractC
        };
    }

    private HapiSpecOperation tearDown() {
        return UtilVerbs.resetToDefault("contracts.enableTraceability");
    }

    private HapiContractCreate createContractWithSlotValues(
            final String contract,
            final String suffix,
            final int slot0,
            final int slot1,
            final int slot2) {
        if (suffix.equals(FIRST)) return contractCreate(contract, slot0, slot1, slot2).gas(300_000);
        return contractCustomCreate(contract, suffix, slot0, slot1, slot2).gas(300_000);
    }

    private HapiSpecOperation executeScenario(
            final String contract,
            final String scenario,
            final ResponseCodeEnum expectedExecutionStatus) {
        return withOpContext(
                (spec, opLog) ->
                        allRunFor(
                                spec,
                                contractCall(
                                                contract,
                                                scenario,
                                                AssociatePrecompileSuite.getNestedContractAddress(
                                                        contract + "B", spec),
                                                AssociatePrecompileSuite.getNestedContractAddress(
                                                        contract + "C", spec))
                                        .gas(1000000)
                                        .via(traceabilityTxn)
                                        .hasKnownStatus(expectedExecutionStatus)));
    }

    private CustomSpecAssert assertStateChanges(final StateChange... stateChanges) {
        // FUTURE WORK: state changes must be asserted from sidecar files,
        // not from the transaction record
        log.info("Expected state changes {}", stateChanges);
        return withOpContext(
                (spec, opLog) -> allRunFor(spec, getTxnRecord(traceabilityTxn).logged()));
    }

    @NotNull
    private ByteString formattedAssertionValue(long value) {
        return ByteString.copyFrom(
                Bytes.wrap(UInt256.valueOf(value)).trimLeadingZeros().toArrayUnsafe());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
