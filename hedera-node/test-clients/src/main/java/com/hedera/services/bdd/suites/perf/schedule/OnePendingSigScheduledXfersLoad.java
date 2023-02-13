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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.stdMgmtOf;
import static com.hedera.services.bdd.suites.perf.schedule.ReadyToRunScheduledXfersLoad.inertReceiver;
import static com.hedera.services.bdd.suites.perf.schedule.ReadyToRunScheduledXfersLoad.initializersGiven;
import static com.hedera.services.bdd.suites.perf.schedule.ReadyToRunScheduledXfersLoad.payingSender;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.util.concurrent.AtomicDouble;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OnePendingSigScheduledXfersLoad extends HapiSuite {
    private static final Logger log = LogManager.getLogger(OnePendingSigScheduledXfersLoad.class);

    private AtomicDouble probOfSignOp = new AtomicDouble(0.0);
    private AtomicInteger numNonDefaultSenders = new AtomicInteger(0);
    private AtomicInteger numInertReceivers = new AtomicInteger(0);

    private AtomicLong duration = new AtomicLong(Long.MAX_VALUE);
    private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
    private AtomicInteger maxOpsPerSec = new AtomicInteger(500);
    private SplittableRandom r = new SplittableRandom();

    BlockingQueue<PendingSig> q =
            new PriorityBlockingQueue<>(5000, Comparator.comparingDouble(PendingSig::getPriority));

    public static void main(String... args) {
        new OnePendingSigScheduledXfersLoad().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    runOnePendingSigXfers(),
                });
    }

    private HapiSpec runOnePendingSigXfers() {
        return defaultHapiSpec("RunOnePendingSigXfers")
                .given(stdMgmtOf(duration, unit, maxOpsPerSec))
                .when(
                        runWithProvider(pendingSigsFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    if (numInertReceivers.get() > 0) {
                                        var op =
                                                inParallel(
                                                        IntStream.range(0, numInertReceivers.get())
                                                                .mapToObj(
                                                                        i ->
                                                                                getAccountBalance(
                                                                                        inertReceiver(
                                                                                                i)))
                                                                .toArray(HapiSpecOperation[]::new));
                                        allRunFor(spec, op);
                                    }
                                }));
    }

    private Function<HapiSpec, OpProvider> pendingSigsFactory() {
        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        var ciProps = spec.setup().ciPropertiesMap();
                        numNonDefaultSenders.set(ciProps.getInteger("numNonDefaultSenders"));
                        numInertReceivers.set(ciProps.getInteger("numInertReceivers"));
                        probOfSignOp.set(ciProps.getDouble("probOfSigning"));
                        return initializersGiven(
                                numNonDefaultSenders.get(), numInertReceivers.get());
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        var sample = r.nextDouble();
                        if (sample <= probOfSignOp.get()) {
                            return getScheduleSign();
                        } else {
                            return getScheduleCreate();
                        }
                    }

                    private Optional<HapiSpecOperation> getScheduleSign() {
                        var nextSig = q.poll();
                        if (nextSig == null) {
                            return Optional.empty();
                        }
                        var op =
                                scheduleSign(nextSig.getScheduleId())
                                        .alsoSigningWith(nextSig.getSignatory())
                                        .hasKnownStatusFrom(NOISY_ALLOWED_STATUSES)
                                        .hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
                                        //						.noLogging()
                                        .deferStatusResolution();
                        return Optional.of(op);
                    }

                    private Optional<HapiSpecOperation> getScheduleCreate() {
                        var senderId = -1;
                        if (numNonDefaultSenders.get() > 0) {
                            senderId = r.nextInt(numNonDefaultSenders.get());
                        }
                        var payerId =
                                (senderId == -1 || numNonDefaultSenders.get() == 1)
                                        ? -1
                                        : (senderId + 1) % numNonDefaultSenders.get();

                        var payer = payerId == -1 ? DEFAULT_PAYER : payingSender(payerId);
                        var sender = senderId == -1 ? DEFAULT_PAYER : payingSender(senderId);
                        var receiver = FUNDING;
                        if (numInertReceivers.get() > 0) {
                            receiver = inertReceiver(r.nextInt(numInertReceivers.get()));
                        }
                        var innerOp =
                                cryptoTransfer(tinyBarsFromTo(sender, receiver, 1L)).fee(ONE_HBAR);
                        var op =
                                scheduleCreate("wrapper", innerOp)
                                        .exposingSuccessTo(
                                                (createdId, bytes) ->
                                                        q.offer(
                                                                new PendingSig(
                                                                        bytes,
                                                                        createdId,
                                                                        sender,
                                                                        r.nextDouble())))
                                        .rememberingNothing()
                                        .designatingPayer(payer)
                                        .alsoSigningWith(payer)
                                        .hasKnownStatusFrom(NOISY_ALLOWED_STATUSES)
                                        .hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
                                        //						.noLogging()
                                        .deferStatusResolution();
                        return Optional.of(op);
                    }
                };
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    private static class PendingSig {
        private final byte[] scheduledTxnBytes;
        private final String scheduleId;
        private final String signatory;
        private final double priority;

        public PendingSig(
                byte[] scheduledTxnBytes, String scheduleId, String signatory, double priority) {
            this.scheduledTxnBytes = scheduledTxnBytes;
            this.scheduleId = scheduleId;
            this.signatory = signatory;
            this.priority = priority;
        }

        public byte[] getScheduledTxnBytes() {
            return scheduledTxnBytes;
        }

        public String getScheduleId() {
            return scheduleId;
        }

        public String getSignatory() {
            return signatory;
        }

        public double getPriority() {
            return priority;
        }
    }
}
