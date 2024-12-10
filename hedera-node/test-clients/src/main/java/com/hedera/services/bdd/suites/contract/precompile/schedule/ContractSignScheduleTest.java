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
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.ScheduleID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@DisplayName("Contract Sign Schedule")
@HapiTestLifecycle
public class ContractSignScheduleTest {

    private static final String A_SCHEDULE = "testSchedule";
    private static final String CONTRACT = "HRC755Contract";

    @Nested
    @DisplayName("Authorize Schedule")
    class AuthorizeScheduleTest {
        private static final AtomicReference<ScheduleID> scheduleID = new AtomicReference<>();

        @BeforeAll
        static void beforeAll(final TestLifecycle testLifecycle) {
            testLifecycle.doAdhoc(
                    cryptoCreate(RECEIVER),
                    uploadInitCode(CONTRACT),
                    contractCreate(CONTRACT),
                    cryptoTransfer(TokenMovement.movingHbar(ONE_HUNDRED_HBARS).between(GENESIS, CONTRACT)),
                    scheduleCreate(A_SCHEDULE, cryptoTransfer(tinyBarsFromTo(CONTRACT, RECEIVER, 1)))
                            .exposingCreatedIdTo(scheduleID::set));
        }

        @HapiTest
        @Disabled
        final Stream<DynamicTest> authorizeScheduleWithContract() {
            return hapiTest(
                    overriding("contracts.systemContract.scheduleService.enabled", "true"),
                    overriding("contracts.systemContract.scheduleService.authorizeSchedule.enabled", "true"),
                    contractCall(
                            CONTRACT,
                            "authorizeScheduleCall",
                            mirrorAddrWith(scheduleID.get().getScheduleNum())));
        }
    }

    @Nested
    @DisplayName("Sign Schedule From EOA")
    class SignScheduleFromEOATest {
        private static final AtomicReference<ScheduleID> scheduleID = new AtomicReference<>();
        private static final String SIGN_SCHEDULE = "signSchedule";
        private static final String IHRC755 = "IHRC755";

        @BeforeAll
        static void beforeAll(final TestLifecycle testLifecycle) {
            testLifecycle.doAdhoc(
                    cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS),
                    scheduleCreate(A_SCHEDULE, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                            .exposingCreatedIdTo(scheduleID::set));
        }

        @HapiTest
        final Stream<DynamicTest> authorizeScheduleWithContract() {
            var scheduleAddress = "0.0." + scheduleID.get().getScheduleNum();
            return hapiTest(
                    overriding("contracts.systemContract.scheduleService.enabled", "true"),
                    overriding("contracts.systemContract.scheduleService.signSchedule.enabled", "true"),
                    getScheduleInfo(A_SCHEDULE).isNotExecuted(),
                    contractCallWithFunctionAbi(
                                    scheduleAddress,
                                    getABIFor(
                                            com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                            SIGN_SCHEDULE,
                                            IHRC755))
                            .payingWith(SENDER)
                            .gas(1_000_000),
                    getScheduleInfo(A_SCHEDULE).isExecuted());
        }
    }
}
