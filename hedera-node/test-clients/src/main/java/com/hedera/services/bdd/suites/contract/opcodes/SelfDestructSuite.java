/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.opcodes;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hedera.services.bdd.suites.contract.Utils.mirrorAddrWith;
import static com.hedera.services.bdd.suites.contract.evm.Evm46ValidationSuite.existingSystemAccounts;
import static com.hedera.services.bdd.suites.contract.evm.Evm46ValidationSuite.nonExistingSystemAccounts;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.SpecManager;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;

@Tag(SMART_CONTRACT)
@HapiTestLifecycle
@DisplayName("SELFDESTRUCT")
public class SelfDestructSuite {
    private static final String SELF_DESTRUCT_CALLABLE_CONTRACT = "SelfDestructCallable";
    private static final String DESTROY_EXPLICIT_BENEFICIARY = "destroyExplicitBeneficiary";
    private static final String BENEFICIARY = "beneficiary";

    @BeforeAll
    static void beforeAll(@NonNull final SpecManager specManager) throws Throwable {
        // Multiple tests use the same contract, so we upload it once here
        specManager.setup(uploadInitCode(SELF_DESTRUCT_CALLABLE_CONTRACT));
    }

    @Nested
    @DisplayName("with 0.46 EVM")
    @ResourceLock(value = "EVM_VERSION", mode = READ_WRITE)
    class WithV046EVM {
        @BeforeAll
        static void beforeAll(@NonNull final SpecManager specManager) throws Throwable {
            specManager.setup(overriding(HapiSuite.EVM_VERSION_PROPERTY, HapiSuite.EVM_VERSION_046));
        }

        @AfterAll
        static void afterAll(@NonNull final SpecManager specManager) throws Throwable {
            specManager.teardown(overriding(HapiSuite.EVM_VERSION_PROPERTY, HapiSuite.EVM_VERSION_050));
        }

        @HapiTest
        @DisplayName("can SELFDESTRUCT in constructor without destroying created child")
        final Stream<DynamicTest> hscsEvm008SelfDestructInConstructorWorks() {
            final var contract = "FactorySelfDestructConstructor";
            final var nextAccount = "civilian";
            return hapiTest(
                    cryptoCreate(BENEFICIARY).balance(ONE_HUNDRED_HBARS),
                    uploadInitCode(contract),
                    contractCreate(contract)
                            .balance(3 * ONE_HBAR)
                            .via("contractCreate")
                            .payingWith(BENEFICIARY),
                    cryptoCreate(nextAccount),
                    getAccountInfo(contract).hasCostAnswerPrecheck(ACCOUNT_DELETED),
                    getContractInfo(contract).has(contractWith().isDeleted()),
                    withOpContext((spec, opLog) -> {
                        final var registry = spec.registry();
                        final var destroyedNum =
                                registry.getContractId(contract).getContractNum();
                        final var childInfoQuery = getContractInfo("0.0." + (destroyedNum + 1))
                                .has(contractWith().isNotDeleted());
                        allRunFor(spec, childInfoQuery);
                    }));
        }

        @HapiTest
        @DisplayName("can SELFDESTRUCT after being constructed")
        final Stream<DynamicTest> hscsEvm008SelfDestructWhenCalling() {
            return hapiTest(
                    cryptoCreate("acc").balance(5 * ONE_HUNDRED_HBARS),
                    contractCreate(SELF_DESTRUCT_CALLABLE_CONTRACT).via("cc").payingWith("acc"),
                    contractCall(SELF_DESTRUCT_CALLABLE_CONTRACT, "destroy").payingWith("acc"),
                    getAccountInfo(SELF_DESTRUCT_CALLABLE_CONTRACT).hasCostAnswerPrecheck(ACCOUNT_DELETED),
                    getContractInfo(SELF_DESTRUCT_CALLABLE_CONTRACT)
                            .has(contractWith().isDeleted()));
        }

        @HapiTest
        @DisplayName("cannot SELFDESTRUCT to a beneficiary with receiverSigRequired that has not signed")
        final Stream<DynamicTest> selfDestructFailsWhenBeneficiaryHasReceiverSigRequiredAndHasNotSignedTheTxn46() {
            return selfDestructFailsWhenBeneficiaryHasReceiverSigRequiredAndHasNotSignedTheTxn();
        }

        @HapiTest
        @DisplayName("cannot SELFDESTRUCT within a static call")
        final Stream<DynamicTest>
                selfDestructViaCallLocalWithAccount999ResultsInLocalCallModificationPrecheckFailed46() {
            return selfDestructViaCallLocalWithAccount999ResultsInLocalCallModificationPrecheckFailed();
        }

        @HapiTest
        @DisplayName("cannot SELFDESTRUCT with system account beneficiary")
        final Stream<DynamicTest> testSelfDestructForSystemAccounts46() {
            return testSelfDestructForSystemAccounts();
        }

        @HapiTest
        @DisplayName("cannot update a contract after SELFDESTRUCT")
        final Stream<DynamicTest> deletedContractsCannotBeUpdated46() {
            return deletedContractsCannotBeUpdated(HapiSuite.EVM_VERSION_046);
        }
    }

    @Nested
    @DisplayName("with 0.50 EVM")
    @ResourceLock(value = "EVM_VERSION", mode = READ)
    class WithV050EVM {
        @HapiTest
        @DisplayName("cannot SELFDESTRUCT to a beneficiary with receiverSigRequired that has not signed")
        final Stream<DynamicTest> selfDestructFailsWhenBeneficiaryHasReceiverSigRequiredAndHasNotSignedTheTxn50() {
            return selfDestructFailsWhenBeneficiaryHasReceiverSigRequiredAndHasNotSignedTheTxn();
        }

        @HapiTest
        @DisplayName("cannot SELFDESTRUCT within a static call")
        final Stream<DynamicTest>
                selfDestructViaCallLocalWithAccount999ResultsInLocalCallModificationPrecheckFailed50() {
            return selfDestructViaCallLocalWithAccount999ResultsInLocalCallModificationPrecheckFailed();
        }

        @HapiTest
        @DisplayName("cannot SELFDESTRUCT with system account beneficiary")
        final Stream<DynamicTest> testSelfDestructForSystemAccounts50() {
            return testSelfDestructForSystemAccounts();
        }

        @HapiTest
        @DisplayName("can update a contract after SELFDESTRUCT")
        final Stream<DynamicTest> deletedContractsCannotBeUpdated50() {
            return deletedContractsCannotBeUpdated(HapiSuite.EVM_VERSION_050);
        }
    }

    final Stream<DynamicTest> selfDestructFailsWhenBeneficiaryHasReceiverSigRequiredAndHasNotSignedTheTxn() {
        final AtomicLong beneficiaryId = new AtomicLong();
        return hapiTest(
                cryptoCreate(BENEFICIARY)
                        .balance(ONE_HUNDRED_HBARS)
                        .receiverSigRequired(true)
                        .exposingCreatedIdTo(id -> beneficiaryId.set(id.getAccountNum())),
                contractCreate(SELF_DESTRUCT_CALLABLE_CONTRACT).balance(ONE_HBAR),
                sourcing(() -> contractCall(
                                SELF_DESTRUCT_CALLABLE_CONTRACT,
                                "destroyExplicitBeneficiary",
                                mirrorAddrWith(beneficiaryId.get()))
                        .hasKnownStatus(INVALID_SIGNATURE)),
                getAccountInfo(BENEFICIARY).has(accountWith().balance(ONE_HUNDRED_HBARS)),
                getContractInfo(SELF_DESTRUCT_CALLABLE_CONTRACT)
                        .has(contractWith().balance(ONE_HBAR)));
    }

    final Stream<DynamicTest> selfDestructViaCallLocalWithAccount999ResultsInLocalCallModificationPrecheckFailed() {
        return hapiTest(
                contractCreate(SELF_DESTRUCT_CALLABLE_CONTRACT).balance(ONE_HBAR),
                contractCallLocal(SELF_DESTRUCT_CALLABLE_CONTRACT, "destroyExplicitBeneficiary", mirrorAddrWith(999L))
                        .hasAnswerOnlyPrecheck(LOCAL_CALL_MODIFICATION_EXCEPTION));
    }

    final Stream<DynamicTest> testSelfDestructForSystemAccounts() {
        final AtomicLong deployer = new AtomicLong();
        final var nonExistingAccountsOps = createOpsArray(
                nonExistingSystemAccounts,
                SELF_DESTRUCT_CALLABLE_CONTRACT,
                DESTROY_EXPLICIT_BENEFICIARY,
                INVALID_SOLIDITY_ADDRESS);
        final var existingAccountsOps = createOpsArray(
                existingSystemAccounts, SELF_DESTRUCT_CALLABLE_CONTRACT, DESTROY_EXPLICIT_BENEFICIARY, SUCCESS);
        final var opsArray = new HapiSpecOperation[nonExistingAccountsOps.length + existingAccountsOps.length];

        System.arraycopy(nonExistingAccountsOps, 0, opsArray, 0, nonExistingAccountsOps.length);
        System.arraycopy(existingAccountsOps, 0, opsArray, nonExistingAccountsOps.length, existingAccountsOps.length);
        return hapiTest(flattened(
                cryptoCreate(BENEFICIARY)
                        .balance(ONE_HUNDRED_HBARS)
                        .receiverSigRequired(false)
                        .exposingCreatedIdTo(id -> deployer.set(id.getAccountNum())),
                contractCreate(SELF_DESTRUCT_CALLABLE_CONTRACT).balance(ONE_HBAR),
                nonExistingAccountsOps));
    }

    final Stream<DynamicTest> deletedContractsCannotBeUpdated(@NonNull final String evmVersion) {
        final var contract = SELF_DESTRUCT_CALLABLE_CONTRACT;
        final var beneficiary = BENEFICIARY;

        final var expectedStatus =
                switch (evmVersion) {
                    case HapiSuite.EVM_VERSION_046 -> INVALID_CONTRACT_ID;
                    case HapiSuite.EVM_VERSION_050 -> SUCCESS;
                    default -> throw new IllegalArgumentException("unexpected evm version: " + evmVersion);
                };

        return hapiTest(
                uploadInitCode(contract),
                contractCreate(contract).gas(300_000),
                cryptoCreate(beneficiary).balance(ONE_HUNDRED_HBARS),
                contractCall(contract, "destroy").deferStatusResolution().payingWith(beneficiary),
                contractUpdate(contract).newMemo("Hi there!").hasKnownStatus(expectedStatus));
    }

    private HapiSpecOperation[] createOpsArray(
            List<Long> accounts, String contract, String methodName, ResponseCodeEnum status) {
        HapiSpecOperation[] opsArray = new HapiSpecOperation[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            opsArray[i] = contractCall(contract, methodName, mirrorAddrWith(accounts.get(i)))
                    .hasKnownStatus(status);
        }
        return opsArray;
    }
}
