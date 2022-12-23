/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.perf.crypto;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoTransferLoadTestWithStakedAccounts extends LoadTest {
    private static final Logger log =
            LogManager.getLogger(CryptoTransferLoadTestWithStakedAccounts.class);
    private Random RANDOM = new Random();
    private static final long TEST_ACCOUNT_STARTS_FROM = 1001L;
    private static final int STAKED_CREATIONS = 50;

    public static void main(String... args) {
        parseArgs(args);

        CryptoTransferLoadTestWithStakedAccounts suite =
                new CryptoTransferLoadTestWithStakedAccounts();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runCryptoTransfers());
    }

    protected HapiSpec runCryptoTransfers() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings();

        Supplier<HapiSpecOperation[]> transferBurst =
                () -> {
                    String sender = "sender";
                    String receiver = "receiver";
                    if (settings.getTotalAccounts() > 2) {
                        int s = RANDOM.nextInt(settings.getTotalAccounts());
                        int re = 0;
                        do {
                            re = RANDOM.nextInt(settings.getTotalAccounts());
                        } while (re == s);
                        sender = String.format("0.0.%d", TEST_ACCOUNT_STARTS_FROM + s);
                        receiver = String.format("0.0.%d", TEST_ACCOUNT_STARTS_FROM + re);
                    }

                    return new HapiSpecOperation[] {
                        cryptoTransfer(tinyBarsFromTo(sender, receiver, 1L))
                                .noLogging()
                                .payingWith(sender)
                                .signedBy(GENESIS)
                                .suppressStats(true)
                                .fee(100_000_000L)
                                .hasKnownStatusFrom(
                                        SUCCESS,
                                        OK,
                                        INSUFFICIENT_PAYER_BALANCE,
                                        UNKNOWN,
                                        TRANSACTION_EXPIRED,
                                        INSUFFICIENT_ACCOUNT_BALANCE)
                                .hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED)
                                .deferStatusResolution()
                    };
                };

        return defaultHapiSpec("RunCryptoTransfers")
                .given(
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
                        logIt(ignore -> settings.toString()))
                .when(
                        cryptoCreate("sender")
                                .balance(ignore -> settings.getInitialBalance())
                                .payingWith(GENESIS)
                                .withRecharging()
                                .key(GENESIS)
                                .rechargeWindow(3)
                                .stakedNodeId(settings.getNodeToStake())
                                .logging()
                                .hasRetryPrecheckFrom(
                                        BUSY,
                                        DUPLICATE_TRANSACTION,
                                        PLATFORM_TRANSACTION_NOT_CREATED),
                        cryptoCreate("receiver")
                                .payingWith(GENESIS)
                                .stakedNodeId(settings.getNodeToStake())
                                .hasRetryPrecheckFrom(
                                        BUSY,
                                        DUPLICATE_TRANSACTION,
                                        PLATFORM_TRANSACTION_NOT_CREATED)
                                .key(GENESIS)
                                .logging(),
                        withOpContext(
                                (spec, opLog) -> {
                                    List<HapiSpecOperation> ops = new ArrayList<>();
                                    for (int i = 0; i < STAKED_CREATIONS; i++) {
                                        var stakedAccount = "stakedAccount" + i;
                                        ops.add(
                                                cryptoCreate(stakedAccount)
                                                        .payingWith("sender")
                                                        .stakedNodeId(settings.getNodeToStake())
                                                        .fee(ONE_HBAR)
                                                        .signedBy("sender", DEFAULT_PAYER));
                                    }
                                    allRunFor(spec, ops);
                                }))
                .then(
                        defaultLoadTest(transferBurst, settings),
                        getAccountBalance("sender").logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
