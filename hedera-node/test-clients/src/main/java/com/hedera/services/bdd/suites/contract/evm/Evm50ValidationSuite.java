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

package com.hedera.services.bdd.suites.contract.evm;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.junit.HapiTest;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
public class Evm50ValidationSuite {

    private static final Logger LOG = LogManager.getLogger(Evm50ValidationSuite.class);
    private static final String EVM_VERSION_PROPERTY = "contracts.evm.version";
    private static final String DYNAMIC_EVM_PROPERTY = "contracts.evm.version.dynamic";
    private static final String EVM_VERSION_046 = "v0.46";
    private static final String EVM_VERSION_050 = "v0.50";
    private static final String ACCOUNT = "account";
    private static final String Module05OpcodesExist_CONTRACT = "Module050OpcodesExist";
    private static final long A_BUNCH_OF_GAS = 500_000L;

    @HapiTest
    final Stream<DynamicTest> verifiesNonExistenceForTransientStorageOpcodesV46() {
        final var contract = Module05OpcodesExist_CONTRACT;

        return propertyPreservingHapiSpec(
                        "verifiesNonExistenceForTransientStorageOpcodesV46", NONDETERMINISTIC_TRANSACTION_FEES)
                .preserving(EVM_VERSION_PROPERTY, DYNAMIC_EVM_PROPERTY)
                .given(
                        overriding(DYNAMIC_EVM_PROPERTY, "true"),
                        overriding(EVM_VERSION_PROPERTY, EVM_VERSION_046),
                        cryptoCreate(ACCOUNT),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when()
                .then(sourcing(() -> contractCall(contract, "try_transient_storage")
                        .gas(A_BUNCH_OF_GAS)
                        .payingWith(ACCOUNT)
                        .via(txnName("non_transient"))
                        .hasKnownStatus(CONTRACT_EXECUTION_EXCEPTION)
                        .logged()));
    }

    @HapiTest
    final Stream<DynamicTest> verifiesNonExistenceForMCOPYOpcodeV46() {
        final var contract = Module05OpcodesExist_CONTRACT;

        return propertyPreservingHapiSpec("verifiesNonExistenceForMCOPYOpcodeV46", NONDETERMINISTIC_TRANSACTION_FEES)
                .preserving(EVM_VERSION_PROPERTY, DYNAMIC_EVM_PROPERTY)
                .given(
                        overriding(DYNAMIC_EVM_PROPERTY, "true"),
                        overriding(EVM_VERSION_PROPERTY, EVM_VERSION_046),
                        cryptoCreate(ACCOUNT),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when()
                .then(sourcing(() -> contractCall(contract, "try_mcopy")
                        .gas(A_BUNCH_OF_GAS)
                        .payingWith(ACCOUNT)
                        .via(txnName("non_mcopy"))
                        .hasKnownStatus(CONTRACT_EXECUTION_EXCEPTION)
                        .logged()));
    }

    @HapiTest
    final Stream<DynamicTest> verifiesNonExistenceForKZGPrecompileV46() {
        final var contract = Module05OpcodesExist_CONTRACT;

        return propertyPreservingHapiSpec("verifiesNonExistenceForKZGPrecompileV46")
                .preserving(EVM_VERSION_PROPERTY, DYNAMIC_EVM_PROPERTY)
                .given(
                        overriding(DYNAMIC_EVM_PROPERTY, "true"),
                        overriding(EVM_VERSION_PROPERTY, EVM_VERSION_046),
                        cryptoCreate(ACCOUNT),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when()
                .then(sourcing(() -> contractCall(contract, "try_kzg_precompile")
                        .gas(A_BUNCH_OF_GAS)
                        .payingWith(ACCOUNT)
                        .via(txnName("non-kzg"))
                        .hasKnownStatus(CONTRACT_EXECUTION_EXCEPTION)
                        .logged()));
    }

    @HapiTest
    final Stream<DynamicTest> verifiesExistenceForTransientStorageOpcodesV050() {
        final var contract = Module05OpcodesExist_CONTRACT;

        return propertyPreservingHapiSpec("verifiesExistenceForTransientStorageOpcodesV050")
                .preserving(EVM_VERSION_PROPERTY, DYNAMIC_EVM_PROPERTY)
                .given(
                        overriding(DYNAMIC_EVM_PROPERTY, "true"),
                        overriding(EVM_VERSION_PROPERTY, EVM_VERSION_050),
                        cryptoCreate(ACCOUNT),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when()
                .then(sourcing(() -> contractCall(contract, "try_transient_storage")
                        .gas(A_BUNCH_OF_GAS)
                        .payingWith(ACCOUNT)
                        .via(txnName("transient"))
                        .hasKnownStatus(SUCCESS)
                        .logged()));
    }

    @HapiTest
    final Stream<DynamicTest> verifiesExistenceForMCOPYOpcodeV050() {
        final var contract = Module05OpcodesExist_CONTRACT;

        return propertyPreservingHapiSpec("verifiesExistenceForMCOPYOpcodeV050")
                .preserving(EVM_VERSION_PROPERTY, DYNAMIC_EVM_PROPERTY)
                .given(
                        overriding(DYNAMIC_EVM_PROPERTY, "true"),
                        overriding(EVM_VERSION_PROPERTY, EVM_VERSION_050),
                        cryptoCreate(ACCOUNT),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when()
                .then(sourcing(() -> contractCall(contract, "try_mcopy")
                        .gas(A_BUNCH_OF_GAS)
                        .payingWith(ACCOUNT)
                        .via("mcopy")
                        .hasKnownStatus(SUCCESS)
                        .logged()));
    }

    @HapiTest
    final Stream<DynamicTest> verifiesExistenceForKZGPrecompileV050() {
        final var contract = Module05OpcodesExist_CONTRACT;

        return propertyPreservingHapiSpec("verifiesExistenceForKZGPrecompileV050")
                .preserving(EVM_VERSION_PROPERTY, DYNAMIC_EVM_PROPERTY)
                .given(
                        overriding(DYNAMIC_EVM_PROPERTY, "true"),
                        overriding(EVM_VERSION_PROPERTY, EVM_VERSION_050),
                        cryptoCreate(ACCOUNT),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when()
                .then(sourcing(() -> contractCall(contract, "try_kzg_precompile")
                        .gas(A_BUNCH_OF_GAS)
                        .payingWith(ACCOUNT)
                        .via(txnName("kzg"))
                        .hasKnownStatus(SUCCESS)
                        .logged()));
    }

    @HapiTest
    final Stream<DynamicTest> successTestForKZGPrecompileV050() {
        final var contract = Module05OpcodesExist_CONTRACT;

        return propertyPreservingHapiSpec("successTestForKZGPrecompileV050")
                .preserving(EVM_VERSION_PROPERTY, DYNAMIC_EVM_PROPERTY)
                .given(
                        overriding(DYNAMIC_EVM_PROPERTY, "true"),
                        overriding(EVM_VERSION_PROPERTY, EVM_VERSION_050),
                        cryptoCreate(ACCOUNT),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when()
                .then(sourcing(() -> contractCall(contract, "kzg_precompile_success_case")
                        .gas(A_BUNCH_OF_GAS)
                        .payingWith(ACCOUNT)
                        .via(txnName("kzg_success"))
                        .hasKnownStatus(SUCCESS)
                        .logged()));
    }

    private String txnName(final String suffix) {
        return "Module0050_" + suffix;
    }
}
