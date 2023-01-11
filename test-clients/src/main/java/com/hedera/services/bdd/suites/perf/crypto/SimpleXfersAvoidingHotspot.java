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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SimpleXfersAvoidingHotspot extends HapiSuite {
    private static final Logger log = LogManager.getLogger(SimpleXfersAvoidingHotspot.class);

    private static final int NUM_ACCOUNTS = 100;

    private AtomicLong duration = new AtomicLong(600);
    private AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
    private AtomicInteger maxOpsPerSec = new AtomicInteger(70);

    public static void main(String... args) {
        new SimpleXfersAvoidingHotspot().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    runSimpleXfers(),
                });
    }

    private HapiSpec runSimpleXfers() {
        return HapiSpec.customHapiSpec("RunTokenTransfers")
                .withProperties(
                        Map.of(
                                //				"default.keyAlgorithm", "SECP256K1"
                                "default.keyAlgorithm", "ED25519"))
                .given()
                .when()
                .then(
                        runWithProvider(avoidantXfersFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get));
    }

    private Function<HapiSpec, OpProvider> avoidantXfersFactory() {
        final var nextSender = new AtomicInteger();
        final IntFunction<String> nameFn = i -> "account" + i;

        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return List.of(
                                inParallel(
                                        IntStream.range(0, NUM_ACCOUNTS)
                                                .mapToObj(i -> uniqueCreation(nameFn.apply(i)))
                                                .toArray(HapiSpecOperation[]::new)),
                                sleepFor(10_000L));
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        final int sender = nextSender.getAndUpdate(i -> (i + 1) % NUM_ACCOUNTS);
                        final int receiver = (sender + 1) % NUM_ACCOUNTS;
                        final var from = nameFn.apply(sender);
                        final var to = nameFn.apply(receiver);
                        final var op =
                                cryptoTransfer(tinyBarsFromTo(from, to, 1))
                                        .payingWith(from)
                                        .hasKnownStatusFrom(ACCEPTED_STATUSES)
                                        .deferStatusResolution()
                                        .noLogging();
                        return Optional.of(op);
                    }
                };
    }

    static HapiSpecOperation uniqueQuietCreation(final String name) {
        return internalUniqueCreation(name, false);
    }

    static HapiSpecOperation uniqueCreation(final String name) {
        return internalUniqueCreation(name, true);
    }

    private static HapiSpecOperation internalUniqueCreation(
            final String name, final boolean verbose) {
        return withOpContext(
                (spec, opLog) -> {
                    while (true) {
                        try {
                            final var attempt =
                                    cryptoCreate(name)
                                            .payingWith(GENESIS)
                                            .ensuringResolvedStatusIsntFromDuplicate()
                                            .balance(ONE_HUNDRED_HBARS * 10_000);
                            if (!verbose) {
                                attempt.noLogging();
                            }
                            allRunFor(spec, attempt);
                            return;
                        } catch (IllegalStateException ignore) {
                            /* Collision with another client also using the treasury as its payer */
                        }
                    }
                });
    }

    private static final ResponseCodeEnum[] ACCEPTED_STATUSES = {
        SUCCESS, OK, INSUFFICIENT_PAYER_BALANCE, UNKNOWN, TRANSACTION_EXPIRED
    };

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
