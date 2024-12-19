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

package com.hedera.services.bdd.suites.contract.precompile.schedule;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.mirrorAddrWith;
import static com.hedera.services.bdd.suites.leaky.LeakyContractTestsSuite.RECEIVER;
import static com.hedera.services.bdd.suites.leaky.LeakyContractTestsSuite.SENDER;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.ScheduleID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@OrderedInIsolation
@DisplayName("Contract sign schedule")
@HapiTestLifecycle
public class ContractSignScheduleTest {
    private static final String CONTRACT = "HRC755Contract";
    private static final String AUTHORIZE_SCHEDULE_CALL = "authorizeScheduleCall";

    @Nested
    @DisplayName("Authorize schedule from EOA controlled by contract")
    class AuthorizeScheduleFromEOATest {
        private static final String SCHEDULE_A = "testScheduleA";
        private static final String SCHEDULE_B = "testScheduleB";
        private static final String CONTRACT_CONTROLLED = "contractControlled";
        private static final AtomicReference<ScheduleID> scheduleIDA = new AtomicReference<>();
        private static final AtomicReference<ScheduleID> scheduleIDB = new AtomicReference<>();

        @BeforeAll
        static void beforeAll(final TestLifecycle testLifecycle) {
            testLifecycle.doAdhoc(
                    overriding("contracts.systemContract.scheduleService.enabled", "true"),
                    overriding("contracts.systemContract.scheduleService.authorizeSchedule.enabled", "true"),
                    cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(RECEIVER),
                    uploadInitCode(CONTRACT),
                    contractCreate(CONTRACT),
                    cryptoCreate(CONTRACT_CONTROLLED).keyShape(KeyShape.CONTRACT.signedWith(CONTRACT)),
                    cryptoTransfer(TokenMovement.movingHbar(ONE_HUNDRED_HBARS).between(GENESIS, CONTRACT_CONTROLLED)),
                    scheduleCreate(SCHEDULE_A, cryptoTransfer(tinyBarsFromTo(CONTRACT_CONTROLLED, RECEIVER, 1)))
                            .exposingCreatedIdTo(scheduleIDA::set),
                    scheduleCreate(SCHEDULE_B, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                            .exposingCreatedIdTo(scheduleIDB::set));
        }

        @HapiTest
        @DisplayName("Signature executes schedule transaction")
        final Stream<DynamicTest> authorizeScheduleWithContract() {
            return hapiTest(
                    getScheduleInfo(SCHEDULE_A).isNotExecuted(),
                    contractCall(
                                    CONTRACT,
                                    AUTHORIZE_SCHEDULE_CALL,
                                    mirrorAddrWith(scheduleIDA.get().getScheduleNum()))
                            .gas(1_000_000L),
                    getScheduleInfo(SCHEDULE_A).isExecuted());
        }

        @HapiTest
        @DisplayName("Signature does not executes schedule transaction")
        final Stream<DynamicTest> authorizeScheduleWithContractNoExec() {
            return hapiTest(
                    getScheduleInfo(SCHEDULE_B).isNotExecuted(),
                    contractCall(
                                    CONTRACT,
                                    AUTHORIZE_SCHEDULE_CALL,
                                    mirrorAddrWith(scheduleIDB.get().getScheduleNum()))
                            .gas(1_000_000L),
                    getScheduleInfo(SCHEDULE_B).isNotExecuted());
        }
    }

    @Nested
    @DisplayName("Authorize schedule from contract")
    class AuthorizeScheduleFromContractTest {
        private static final String SCHEDULE_C = "testScheduleC";
        private static final String SCHEDULE_D = "testScheduleD";
        private static final AtomicReference<ScheduleID> scheduleIDC = new AtomicReference<>();
        private static final AtomicReference<ScheduleID> scheduleIDD = new AtomicReference<>();

        @BeforeAll
        static void beforeAll(final TestLifecycle testLifecycle) {
            testLifecycle.doAdhoc(
                    overriding("contracts.systemContract.scheduleService.enabled", "true"),
                    overriding("contracts.systemContract.scheduleService.authorizeSchedule.enabled", "true"),
                    cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(RECEIVER),
                    uploadInitCode(CONTRACT),
                    // For whatever reason, omitting the admin key sets the admin key to the contract key
                    contractCreate(CONTRACT).omitAdminKey(),
                    cryptoTransfer(TokenMovement.movingHbar(ONE_HUNDRED_HBARS).between(GENESIS, CONTRACT)),
                    scheduleCreate(SCHEDULE_C, cryptoTransfer(tinyBarsFromTo(CONTRACT, RECEIVER, 1)))
                            .exposingCreatedIdTo(scheduleIDC::set),
                    scheduleCreate(SCHEDULE_D, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                            .exposingCreatedIdTo(scheduleIDD::set));
        }

        @HapiTest
        @DisplayName("Signature executes schedule transaction")
        final Stream<DynamicTest> authorizeScheduleWithContract() {
            return hapiTest(
                    getScheduleInfo(SCHEDULE_C).isNotExecuted(),
                    contractCall(
                                    CONTRACT,
                                    AUTHORIZE_SCHEDULE_CALL,
                                    mirrorAddrWith(scheduleIDC.get().getScheduleNum()))
                            .gas(1_000_000L),
                    getScheduleInfo(SCHEDULE_C).isExecuted());
        }

        @HapiTest
        @DisplayName("Signature does not executes schedule transaction")
        final Stream<DynamicTest> authorizeScheduleWithContractNoExec() {
            return hapiTest(
                    getScheduleInfo(SCHEDULE_D).isNotExecuted(),
                    contractCall(
                                    CONTRACT,
                                    AUTHORIZE_SCHEDULE_CALL,
                                    mirrorAddrWith(scheduleIDD.get().getScheduleNum()))
                            .gas(1_000_000L),
                    getScheduleInfo(SCHEDULE_D).isNotExecuted());
        }
    }

    @Nested
    @DisplayName("Sign Schedule From EOA")
    class SignScheduleFromEOATest {
        private static final AtomicReference<ScheduleID> scheduleIDE = new AtomicReference<>();
        private static final AtomicReference<ScheduleID> scheduleIDF = new AtomicReference<>();
        private static final String OTHER_SENDER = "otherSender";
        private static final String SIGN_SCHEDULE = "signSchedule";
        private static final String IHRC755 = "IHRC755";
        private static final String SCHEDULE_E = "testScheduleE";
        private static final String SCHEDULE_F = "testScheduleF";

        @BeforeAll
        static void beforeAll(final TestLifecycle testLifecycle) {
            testLifecycle.doAdhoc(
                    overriding("contracts.systemContract.scheduleService.enabled", "true"),
                    overriding("contracts.systemContract.scheduleService.signSchedule.enabled", "true"),
                    cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(OTHER_SENDER).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS),
                    scheduleCreate(SCHEDULE_E, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                            .exposingCreatedIdTo(scheduleIDE::set),
                    scheduleCreate(SCHEDULE_F, cryptoTransfer(tinyBarsFromTo(OTHER_SENDER, RECEIVER, 1)))
                            .exposingCreatedIdTo(scheduleIDF::set));
        }

        @HapiTest
        @DisplayName("Signature executes schedule transaction")
        final Stream<DynamicTest> authorizeScheduleWithContract() {
            var scheduleAddress = "0.0." + scheduleIDE.get().getScheduleNum();
            return hapiTest(
                    getScheduleInfo(SCHEDULE_E).isNotExecuted(),
                    contractCallWithFunctionAbi(
                                    scheduleAddress,
                                    getABIFor(
                                            com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                            SIGN_SCHEDULE,
                                            IHRC755))
                            .payingWith(SENDER)
                            .gas(1_000_000),
                    getScheduleInfo(SCHEDULE_E).isExecuted());
        }

        @HapiTest
        @DisplayName("Signature does not executes schedule transaction")
        final Stream<DynamicTest> authorizeScheduleWithContractNoExec() {
            var scheduleAddress = "0.0." + scheduleIDF.get().getScheduleNum();
            return hapiTest(
                    getScheduleInfo(SCHEDULE_F).isNotExecuted(),
                    contractCallWithFunctionAbi(
                                    scheduleAddress,
                                    getABIFor(
                                            com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                            SIGN_SCHEDULE,
                                            IHRC755))
                            .payingWith(SENDER)
                            .gas(1_000_000),
                    getScheduleInfo(SCHEDULE_F).isNotExecuted());
        }
    }
}
