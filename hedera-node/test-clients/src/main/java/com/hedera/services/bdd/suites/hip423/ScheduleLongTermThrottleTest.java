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

package com.hedera.services.bdd.suites.hip423;

import static com.hedera.services.bdd.junit.ContextRequirement.THROTTLE_OVERRIDES;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemFileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThrottles;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.ORIG_FILE;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.PAYING_ACCOUNT_2;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.RECEIVER;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SENDER;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SENDER_TXN;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.VALID_SCHEDULE;
import static com.hedera.services.bdd.suites.hip423.ScheduleLongTermExecutionTest.PAYER_TXN;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.PAYING_ACCOUNT;
import static com.hedera.services.bdd.suites.utils.ECDSAKeysUtils.randomHeadlongAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_FUTURE_THROTTLE_EXCEEDED;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCall;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@HapiTestLifecycle
public class ScheduleLongTermThrottleTest {

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle lifecycle) {
        // override and preserve old values
        lifecycle.overrideInClass(Map.of(
                "scheduling.longTermEnabled",
                "true",
                "scheduling.whitelist",
                "ConsensusSubmitMessage,CryptoTransfer,TokenMint,TokenBurn,"
                        + "CryptoCreate,CryptoUpdate,FileUpdate,SystemDelete,SystemUndelete,"
                        + "Freeze,ContractCall,ContractCreate,ContractUpdate,ContractDelete"));
    }

    @HapiTest
    @Order(1)
    final Stream<DynamicTest> scheduledSystemDeleteUnauthorizedPayerFails() {

        return defaultHapiSpec("ScheduledSystemDeleteUnauthorizedPayerFailsAtExpiry")
                .given(
                        cryptoCreate(PAYING_ACCOUNT).via(PAYER_TXN),
                        cryptoCreate(PAYING_ACCOUNT_2),
                        fileCreate("misc").lifetime(THREE_MONTHS_IN_SECONDS).contents(ORIG_FILE))
                .when()
                .then(scheduleCreate(VALID_SCHEDULE, systemFileDelete("misc").updatingExpiry(1L))
                        .withEntityMemo(randomUppercase(100))
                        .designatingPayer(PAYING_ACCOUNT_2)
                        .payingWith(PAYING_ACCOUNT)
                        .waitForExpiry()
                        .withRelativeExpiry(PAYER_TXN, 4)
                        // future throttles will be exceeded because there is no throttle
                        // for system delete
                        // and the custom payer is not exempt from throttles like and admin
                        // user would be
                        .hasKnownStatus(SCHEDULE_FUTURE_THROTTLE_EXCEEDED));
    }

    @LeakyHapiTest(requirement = THROTTLE_OVERRIDES)
    @Order(2)
    final Stream<DynamicTest> throttleWorksAsExpected() {
        return hapiTest(
                cryptoCreate(PAYING_ACCOUNT),
                cryptoCreate(RECEIVER),
                cryptoCreate(SENDER).via(SENDER_TXN),
                overridingThrottles("testSystemFiles/artificial-limits-schedule.json"),
                scheduleCreate("firstSchedule", cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                        .designatingPayer(PAYING_ACCOUNT)
                        .waitForExpiry()
                        .withRelativeExpiry(SENDER_TXN, 4)
                        .recordingScheduledTxn()
                        .alsoSigningWith(PAYING_ACCOUNT, SENDER)
                        .payingWith(PAYING_ACCOUNT),
                scheduleCreate("secondSchedule", cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 2)))
                        .designatingPayer(PAYING_ACCOUNT)
                        .waitForExpiry()
                        .withRelativeExpiry(SENDER_TXN, 4)
                        .recordingScheduledTxn()
                        .alsoSigningWith(PAYING_ACCOUNT, SENDER)
                        .payingWith(PAYING_ACCOUNT),
                scheduleCreate("thirdSchedule", cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 3)))
                        .designatingPayer(PAYING_ACCOUNT)
                        .waitForExpiry()
                        .withRelativeExpiry(SENDER_TXN, 4)
                        .recordingScheduledTxn()
                        .alsoSigningWith(PAYING_ACCOUNT, SENDER)
                        .payingWith(PAYING_ACCOUNT)
                        .hasKnownStatus(SCHEDULE_FUTURE_THROTTLE_EXCEEDED));
    }

    @LeakyHapiTest(requirement = THROTTLE_OVERRIDES)
    @Order(3)
    final Stream<DynamicTest> gasThrottleWorksAsExpected() {
        final var contract = "HollowAccountCreator";
        final var gasToOffer = 2_000_000L;
        return hapiTest(
                overridingThrottles("testSystemFiles/artificial-limits-schedule.json"),
                cryptoCreate(PAYING_ACCOUNT),
                uploadInitCode(contract),
                contractCreate(contract).via("contractCreate"),
                // schedule at another second, should not affect the throttle
                scheduleCreate("1", testContractCall(1, gasToOffer))
                        .withRelativeExpiry("contractCreate", 100)
                        .payingWith(PAYING_ACCOUNT),
                scheduleCreate("2", testContractCall(2, gasToOffer))
                        .withRelativeExpiry("contractCreate", 10)
                        .payingWith(PAYING_ACCOUNT),
                scheduleCreate("3", testContractCall(3, gasToOffer))
                        .withRelativeExpiry("contractCreate", 10)
                        .payingWith(PAYING_ACCOUNT),
                scheduleCreate("4", testContractCall(4, gasToOffer))
                        .withRelativeExpiry("contractCreate", 10)
                        .payingWith(PAYING_ACCOUNT)
                        .hasKnownStatus(SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED));
    }

    private HapiContractCall testContractCall(long sending, long gas) {
        final var contract = "HollowAccountCreator";
        return contractCall(contract, "testCallFoo", randomHeadlongAddress(), BigInteger.valueOf(500_000L))
                .sending(sending)
                .gas(gas);
    }
}
