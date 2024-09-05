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

package com.hedera.services.bdd.suites.staking;

import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextStakingPeriod;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.STAKING_REWARD;

import com.hedera.services.bdd.junit.RepeatableHapiTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class RepeatableStakingTests {
    @RepeatableHapiTest(NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION)
    Stream<DynamicTest> noStakingInteractionsForExtendedPeriodIsFine() {
        final var numPeriodsToElapse = 367;
        return hapiTest(
                overridingThree(
                        "staking.startThreshold", "" + 10 * ONE_HBAR,
                        "staking.perHbarRewardRate", "1",
                        "staking.rewardBalanceThreshold", "0"),
                cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, ONE_MILLION_HBARS)),
                cryptoCreate("forgottenStaker").stakedNodeId(0).balance(ONE_MILLION_HBARS),
                withOpContext((spec, opLog) -> {
                    for (int i = 0; i < numPeriodsToElapse; i++) {
                        allRunFor(
                                spec,
                                waitUntilStartOfNextStakingPeriod(1),
                                cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)));
                    }
                }),
                cryptoTransfer(tinyBarsFromTo(GENESIS, "forgottenStaker", 1L)));
    }
}
