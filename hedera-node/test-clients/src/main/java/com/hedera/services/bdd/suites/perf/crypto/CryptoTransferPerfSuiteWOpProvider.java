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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoTransferPerfSuiteWOpProvider extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CryptoTransferPerfSuiteWOpProvider.class);

    public static void main(String... args) {
        CryptoTransferPerfSuiteWOpProvider suite = new CryptoTransferPerfSuiteWOpProvider();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runMixedTransferAndSubmits());
    }

    private HapiSpec runMixedTransferAndSubmits() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings();
        return defaultHapiSpec("CryptoTransferPerfSuiteWOpProvider")
                .given(
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
                        logIt(ignore -> settings.toString()))
                .when()
                .then(runWithProvider(XfersFactory())
                        .lasting(settings::getMins, () -> MINUTES)
                        .maxOpsPerSec(settings::getTps));
    }

    private Function<HapiSpec, OpProvider> XfersFactory() {
        return spec -> new OpProvider() {
            @Override
            public List<HapiSpecOperation> suggestedInitializers() {
                return List.of(
                        cryptoCreate("sender")
                                .balance(ONE_HUNDRED_HBARS)
                                .hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED),
                        cryptoCreate("receiver").hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED),
                        sleepFor(10_000L));
            }

            @Override
            public Optional<HapiSpecOperation> get() {
                final var op = cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1L))
                        .noLogging()
                        .hasKnownStatusFrom(
                                SUCCESS,
                                OK,
                                INSUFFICIENT_PAYER_BALANCE,
                                UNKNOWN,
                                TRANSACTION_EXPIRED,
                                INSUFFICIENT_ACCOUNT_BALANCE)
                        .hasPrecheckFrom(DUPLICATE_TRANSACTION, OK, INVALID_SIGNATURE)
                        .hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED, PAYER_ACCOUNT_NOT_FOUND)
                        .deferStatusResolution();
                return Optional.of(op);
            }
        };
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
