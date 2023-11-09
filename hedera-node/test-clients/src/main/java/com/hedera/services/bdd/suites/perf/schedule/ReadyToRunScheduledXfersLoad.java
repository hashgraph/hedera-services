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

package com.hedera.services.bdd.suites.perf.schedule;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_ALLOWED_STATUSES;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.stdMgmtOf;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ReadyToRunScheduledXfersLoad extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ReadyToRunScheduledXfersLoad.class);

    private AtomicInteger numNonDefaultSenders = new AtomicInteger(0);
    private AtomicInteger numInertReceivers = new AtomicInteger(0);

    private AtomicLong duration = new AtomicLong(Long.MAX_VALUE);
    private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
    private AtomicInteger maxOpsPerSec = new AtomicInteger(500);
    private SplittableRandom r = new SplittableRandom();

    public static void main(String... args) {
        new ReadyToRunScheduledXfersLoad().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            runReadyToRunXfers(),
        });
    }

    private HapiSpec runReadyToRunXfers() {
        return defaultHapiSpec("RunReadyToRunXfers")
                .given(stdMgmtOf(duration, unit, maxOpsPerSec))
                .when(runWithProvider(readyToRunXfersFactory())
                        .lasting(duration::get, unit::get)
                        .maxOpsPerSec(maxOpsPerSec::get))
                .then(withOpContext((spec, opLog) -> {
                    if (numInertReceivers.get() > 0) {
                        var op = inParallel(IntStream.range(0, numInertReceivers.get())
                                .mapToObj(
                                        i -> getAccountBalance(inertReceiver(i)).logged())
                                .toArray(HapiSpecOperation[]::new));
                        CustomSpecAssert.allRunFor(spec, op);
                    }
                }));
    }

    static String payingSender(int id) {
        return "payingSender" + id;
    }

    static String inertReceiver(int id) {
        return "inertReceiver" + id;
    }

    static List<HapiSpecOperation> initializersGiven(int nonDefaultSenders, int inertReceivers) {
        List<HapiSpecOperation> initializers = new ArrayList<>();
        for (int i = 0; i < nonDefaultSenders; i++) {
            initializers.add(cryptoCreate(payingSender(i)).balance(ONE_HUNDRED_HBARS));
        }
        for (int i = 0; i < inertReceivers; i++) {
            initializers.add(cryptoCreate(inertReceiver(i)).balance(0L));
        }

        for (HapiSpecOperation op : initializers) {
            if (op instanceof HapiTxnOp) {
                ((HapiTxnOp) op).hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS);
            }
        }

        return initializers;
    }

    private Function<HapiSpec, OpProvider> readyToRunXfersFactory() {
        return spec -> new OpProvider() {
            @Override
            public List<HapiSpecOperation> suggestedInitializers() {
                var ciProps = spec.setup().ciPropertiesMap();
                numNonDefaultSenders.set(ciProps.getInteger("numNonDefaultSenders"));
                numInertReceivers.set(ciProps.getInteger("numInertReceivers"));
                return initializersGiven(numNonDefaultSenders.get(), numInertReceivers.get());
            }

            @Override
            public Optional<HapiSpecOperation> get() {
                var sendingPayer = DEFAULT_PAYER;
                if (numNonDefaultSenders.get() > 0) {
                    sendingPayer = payingSender(r.nextInt(numNonDefaultSenders.get()));
                }
                var receiver = FUNDING;
                if (numInertReceivers.get() > 0) {
                    receiver = inertReceiver(r.nextInt(numInertReceivers.get()));
                }
                var innerOp = cryptoTransfer(tinyBarsFromTo(sendingPayer, receiver, 1L))
                        .payingWith(sendingPayer)
                        .noLogging();
                var op = scheduleCreate("wrapper", innerOp)
                        .rememberingNothing()
                        .alsoSigningWith(sendingPayer)
                        .hasKnownStatusFrom(NOISY_ALLOWED_STATUSES)
                        .hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
                        .noLogging()
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
