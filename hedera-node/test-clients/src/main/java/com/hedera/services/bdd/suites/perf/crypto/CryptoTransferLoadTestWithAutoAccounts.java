/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

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

public class CryptoTransferLoadTestWithAutoAccounts extends LoadTest {
    private static final Logger log = LogManager.getLogger(CryptoTransferLoadTestWithAutoAccounts.class);
    private Random r = new Random();
    private static final int AUTO_ACCOUNTS = 20;

    public static void main(String... args) {
        parseArgs(args);

        CryptoTransferLoadTestWithAutoAccounts suite = new CryptoTransferLoadTestWithAutoAccounts();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runCryptoTransfers());
    }

    protected HapiSpec runCryptoTransfers() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings();

        Supplier<HapiSpecOperation[]> transferBurst = () -> {
            String sender = "sender";
            String receiver;

            if (r.nextInt(10) < 5) {
                receiver = "alias" + r.nextInt(AUTO_ACCOUNTS);
            } else {
                receiver = "receiver";
            }

            if (receiver.startsWith("alias")) {
                return new HapiSpecOperation[] {
                    cryptoTransfer(tinyBarsFromToWithAlias(sender, receiver, 1L))
                            .logging()
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

        var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");

        return defaultHapiSpec("RunCryptoTransfersWithAutoAccounts")
                .given(
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
                        logIt(ignore -> settings.toString()))
                .when(
                        fileUpdate(THROTTLE_DEFS).payingWith(GENESIS).contents(defaultThrottles.toByteArray()),
                        cryptoCreate("sender")
                                .balance(ignore -> settings.getInitialBalance())
                                .payingWith(GENESIS)
                                .withRecharging()
                                .key(GENESIS)
                                .rechargeWindow(3)
                                .logging()
                                .hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
                        cryptoCreate("receiver")
                                .payingWith(GENESIS)
                                .hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
                                .key(GENESIS)
                                .logging(),
                        withOpContext((spec, opLog) -> {
                            List<HapiSpecOperation> ops = new ArrayList<>();
                            for (int i = 0; i < AUTO_ACCOUNTS; i++) {
                                var alias = "alias" + i;
                                ops.add(newKeyNamed(alias));
                                ops.add(cryptoTransfer(tinyBarsFromToWithAlias(DEFAULT_PAYER, alias, ONE_HBAR)));
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
