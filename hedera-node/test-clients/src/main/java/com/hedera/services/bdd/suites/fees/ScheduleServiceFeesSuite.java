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

package com.hedera.services.bdd.suites.fees;

import static com.hedera.services.bdd.junit.ContextRequirement.FEE_SCHEDULE_OVERRIDES;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.uploadScheduledContractPrices;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.OTHER_PAYER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.PAYING_SENDER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.RECEIVER;
import static com.hedera.services.bdd.suites.schedule.ScheduleUtils.SIMPLE_UPDATE;

import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;

@HapiTestLifecycle
public class ScheduleServiceFeesSuite {
    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
        testLifecycle.overrideInClass(Map.of(
                "scheduling.whitelist", "ContractCall,CryptoCreate,CryptoTransfer,FileDelete,FileUpdate,SystemDelete"));
    }

    @LeakyHapiTest(requirement = FEE_SCHEDULE_OVERRIDES)
    final Stream<DynamicTest> canonicalScheduleOpsHaveExpectedUsdFees() {
        return hapiTest(
                uploadScheduledContractPrices(GENESIS),
                uploadInitCode(SIMPLE_UPDATE),
                cryptoCreate(OTHER_PAYER),
                cryptoCreate(PAYING_SENDER),
                cryptoCreate(RECEIVER).receiverSigRequired(true),
                contractCreate(SIMPLE_UPDATE).gas(300_000L),
                scheduleCreate(
                                "canonical",
                                cryptoTransfer(tinyBarsFromTo(PAYING_SENDER, RECEIVER, 1L))
                                        .memo("")
                                        .fee(ONE_HBAR))
                        .payingWith(OTHER_PAYER)
                        .via("canonicalCreation")
                        .alsoSigningWith(PAYING_SENDER)
                        .adminKey(OTHER_PAYER),
                scheduleSign("canonical")
                        .via("canonicalSigning")
                        .payingWith(PAYING_SENDER)
                        .alsoSigningWith(RECEIVER),
                scheduleCreate(
                                "tbd",
                                cryptoTransfer(tinyBarsFromTo(PAYING_SENDER, RECEIVER, 1L))
                                        .memo("")
                                        .fee(ONE_HBAR))
                        .payingWith(PAYING_SENDER)
                        .adminKey(PAYING_SENDER),
                scheduleDelete("tbd").via("canonicalDeletion").payingWith(PAYING_SENDER),
                scheduleCreate(
                                "contractCall",
                                contractCall(SIMPLE_UPDATE, "set", BigInteger.valueOf(5), BigInteger.valueOf(42))
                                        .gas(24_000)
                                        .memo("")
                                        .fee(ONE_HBAR))
                        .payingWith(OTHER_PAYER)
                        .via("canonicalContractCall")
                        .adminKey(OTHER_PAYER),
                validateChargedUsdWithin("canonicalCreation", 0.01, 3.0),
                validateChargedUsdWithin("canonicalSigning", 0.001, 3.0),
                validateChargedUsdWithin("canonicalDeletion", 0.001, 3.0),
                validateChargedUsdWithin("canonicalContractCall", 0.1, 3.0));
    }
}
