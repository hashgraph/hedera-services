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

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.suites.perf.crypto.SimpleXfersAvoidingHotspot.uniqueQuietCreation;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NWayDistNoHotspots extends HapiSuite {
    private static final Logger log = LogManager.getLogger(NWayDistNoHotspots.class);

    private static final int XFER_DURATION = 600;
    private static final int NUM_BENEFICIARIES = 3;
    private static final int DISTRIBUTIONS_PER_SEC = 500;
    private static final int CREATIONS_PER_SEC = 50;
    private static final double ACCOUNT_BUFFER_PERCENTAGE = 10.0;

    private static final int NUM_ACCOUNTS =
            (int)
                    Math.ceil(
                            DISTRIBUTIONS_PER_SEC
                                    * (1 + NUM_BENEFICIARIES)
                                    * (1.0 + ACCOUNT_BUFFER_PERCENTAGE / 100.0));

    private final IntFunction<String> nameFn = i -> "account" + i;
    private final AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
    private final AtomicInteger maxOpsPerSec = new AtomicInteger(DISTRIBUTIONS_PER_SEC);
    private final AtomicInteger maxCreatesPerSec = new AtomicInteger(CREATIONS_PER_SEC);
    private final AtomicLong createDuration = new AtomicLong(NUM_ACCOUNTS / CREATIONS_PER_SEC);
    private final AtomicLong duration = new AtomicLong(XFER_DURATION);

    public static void main(String... args) {
        new NWayDistNoHotspots().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    runDistributions(),
                });
    }

    private HapiSpec runDistributions() {
        return customHapiSpec("runCreations")
                .withProperties(Map.of("default.keyAlgorithm", "ED25519"))
                .given(/*logIt(
                                "Creating at least "
                                        + NUM_ACCOUNTS
                                        + " accounts to avoid hotspots while doing "
                                        + DISTRIBUTIONS_PER_SEC
                                        + (" " + NUM_BENEFICIARIES + "-way distributions/sec"))*/ )
                .when()
                .then(
                        runWithProvider(creationsFactory())
                                .lasting(createDuration::get, unit::get)
                                .maxOpsPerSec(maxCreatesPerSec::get),
                        runWithProvider(distributionsFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get));
    }

    private Function<HapiSpec, OpProvider> creationsFactory() {
        final var nextAccount = new AtomicInteger();
        final var payer = "metaPayer";

        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return List.of(uniqueQuietCreation(payer));
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        final var nextI = nextAccount.getAndIncrement();
                        return Optional.of(
                                cryptoCreate(nameFn.apply(nextI))
                                        .balance(ONE_HUNDRED_HBARS)
                                        .payingWith(payer)
                                        .noLogging()
                                        .deferStatusResolution());
                    }
                };
    }

    private Function<HapiSpec, OpProvider> distributionsFactory() {
        final var nextSender = new AtomicInteger();
        final var n = NUM_ACCOUNTS / 100 * 99;

        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return List.of();
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        final int sender = nextSender.getAndUpdate(i -> (i + 1) % n);
                        final var from = nameFn.apply(sender);
                        final String[] tos = new String[NUM_BENEFICIARIES];
                        for (int i = (sender + 1) % n, j = 0;
                                j < NUM_BENEFICIARIES;
                                i = (i + 1) % n, j++) {
                            tos[j] = nameFn.apply(i);
                        }
                        final var op =
                                cryptoTransfer(
                                                movingHbar(NUM_BENEFICIARIES)
                                                        .distributing(from, tos))
                                        .payingWith(from)
                                        .hasKnownStatusFrom(ACCEPTED_STATUSES)
                                        .noLogging()
                                        .deferStatusResolution();
                        return Optional.of(op);
                    }
                };
    }

    private static final ResponseCodeEnum[] ACCEPTED_STATUSES = {
        SUCCESS, OK, INSUFFICIENT_PAYER_BALANCE, UNKNOWN, TRANSACTION_EXPIRED
    };

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
