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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CryptoTransferLoadTestWithInvalidAccounts extends LoadTest {
    private static final Logger log =
            LogManager.getLogger(CryptoTransferLoadTestWithInvalidAccounts.class);

    public static void main(String... args) {
        parseArgs(args);

        CryptoTransferLoadTestWithInvalidAccounts suite =
                new CryptoTransferLoadTestWithInvalidAccounts();
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
                    return new HapiSpecOperation[] {
                        cryptoTransfer(tinyBarsFromTo("0.0.1000000001", "0.0.1000000002", 1L))
                                .noLogging()
                                .signedBy(GENESIS)
                                .suppressStats(true)
                                .fee(100_000_000L)
                                .hasKnownStatusFrom(INVALID_ACCOUNT_ID)
                                .hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED)
                                .deferStatusResolution()
                    };
                };

        return defaultHapiSpec("RunCryptoTransfers")
                .given(
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap()))
                        /*logIt(ignore -> settings.toString())*/ )
                .when()
                .then(defaultLoadTest(transferBurst, settings));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
