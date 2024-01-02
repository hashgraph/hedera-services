/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.perf.contract;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralArrayResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.mgmtOfBooleanProp;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.mgmtOfIntProp;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.stdMgmtOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.lang.Math.ceil;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.util.concurrent.AtomicDouble;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.suites.HapiSuite;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FibonacciPlusLoadProvider extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(FibonacciPlusLoadProvider.class);

    private static final int CALL_TPS = 200;
    private static final int SMALLEST_NUM_SLOTS = 32;
    private static final int SLOTS_PER_CALL = 12;
    private static final int APPROX_NUM_CONTRACTS = 1000;
    private static final int FIBONACCI_NUM_TO_USE = 12;
    private static final long SECS_TO_RUN = 600;
    private static final long MS_TO_DRAIN_QUEUE = 60_000L;
    private static final long GAS_TO_OFFER = 300_000L;
    private static final String CONTRACT = "FibonacciPlus";

    private static final String SUITE_PROPS_PREFIX = "fibplus_";

    private static final int POWER_LAW_BASE_RECIPROCAL = 4;
    private static final double POWER_LAW_SCALE = 2;
    private static final double MIN_CALL_PROB = 0.90;
    private static final String ADD_NTH_FIB = "addNthFib";

    private final AtomicLong duration = new AtomicLong(SECS_TO_RUN);
    private final AtomicDouble minCallProb = new AtomicDouble(MIN_CALL_PROB);
    private final AtomicDouble powerLawScale = new AtomicDouble(POWER_LAW_SCALE);
    private final AtomicInteger powerLawBaseReciprocal = new AtomicInteger(POWER_LAW_BASE_RECIPROCAL);
    private final AtomicInteger maxOpsPerSec = new AtomicInteger(CALL_TPS);
    private final AtomicInteger smallestNumSlots = new AtomicInteger(SMALLEST_NUM_SLOTS);
    private final AtomicInteger slotsPerCall = new AtomicInteger(SLOTS_PER_CALL);
    private final AtomicInteger numContracts = new AtomicInteger(APPROX_NUM_CONTRACTS);
    private final AtomicInteger fibN = new AtomicInteger(FIBONACCI_NUM_TO_USE);
    private final AtomicReference<BigInteger> fibNValue = new AtomicReference<>(null);
    private final AtomicBoolean validateStorage = new AtomicBoolean(false);
    private final AtomicBoolean verbose = new AtomicBoolean(false);

    private final AtomicLong gasUsed = new AtomicLong(0);
    private final AtomicInteger submittedOps = new AtomicInteger(0);
    private final AtomicInteger completedOps = new AtomicInteger(0);

    private final AtomicReference<Instant> effStart = new AtomicReference<>();
    private final AtomicReference<Instant> effEnd = new AtomicReference<>();
    private final AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);

    private final Map<String, Integer> contractSlots = new HashMap<>();
    private final Map<String, BigInteger[]> contractStorage = new HashMap<>();
    private final Set<String> createdSoFar = ConcurrentHashMap.newKeySet();

    private final Function<SplittableRandom, String> randomCallChoice = random -> {
        final var iter = createdSoFar.iterator();
        final var n = createdSoFar.size();
        if (n == 1) {
            return iter.next();
        }
        for (int i = 0; i < random.nextInt(n - 1); i++, iter.next()) {
            /* No-op */
        }
        return iter.next();
    };

    public static void main(String... args) {
        final var suite = new FibonacciPlusLoadProvider();
        suite.runSuiteSync();
    }

    public void logResults() {
        final var start = effStart.get();
        final var end = effEnd.get();
        if (start == null || end == null) {
            return;
        }
        final var secs = Duration.between(start, end).toSeconds();
        final var gasPerSec = gasUsed.get() / secs;
        final var summary = "Consumed "
                + gasUsed.get()
                + " gas (~"
                + gasPerSec
                + " gas/sec) in "
                + completedOps.get()
                + " completed ops at attempted "
                + maxOpsPerSec.get()
                + " ops/sec";
        LOG.info(summary);
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(justDoOne(), addFibNums());
    }

    final HapiSpec addFibNums() {
        return defaultHapiSpec("AddFibNums")
                .given(
                        stdMgmtOf(duration, unit, maxOpsPerSec, SUITE_PROPS_PREFIX),
                        mgmtOfIntProp(smallestNumSlots, SUITE_PROPS_PREFIX + "smallestNumSlots"),
                        mgmtOfIntProp(slotsPerCall, SUITE_PROPS_PREFIX + "slotsPerCall"),
                        mgmtOfIntProp(numContracts, SUITE_PROPS_PREFIX + "numContracts"),
                        mgmtOfBooleanProp(validateStorage, SUITE_PROPS_PREFIX + "validateStorage"),
                        mgmtOfBooleanProp(verbose, SUITE_PROPS_PREFIX + "verbose"),
                        withOpContext((spec, opLog) -> {
                            fibNValue.set(BigInteger.valueOf(fib(fibN.get())));
                            opLog.info(
                                    "Resolved configuration:\n  "
                                            + SUITE_PROPS_PREFIX
                                            + "duration={}\n  "
                                            + SUITE_PROPS_PREFIX
                                            + "maxOpsPerSec={}\n  "
                                            + SUITE_PROPS_PREFIX
                                            + "numContracts={}\n  "
                                            + SUITE_PROPS_PREFIX
                                            + "smallestNumSlots={}\n  "
                                            + SUITE_PROPS_PREFIX
                                            + "powerLawScale={}\n  "
                                            + SUITE_PROPS_PREFIX
                                            + "powerLawBaseReciprocal={}\n  "
                                            + SUITE_PROPS_PREFIX
                                            + "minCallProb={}\n  "
                                            + SUITE_PROPS_PREFIX
                                            + "fibN={} ({})\n  "
                                            + SUITE_PROPS_PREFIX
                                            + "slotsPerCall={}",
                                    duration.get(),
                                    maxOpsPerSec.get(),
                                    numContracts.get(),
                                    smallestNumSlots.get(),
                                    powerLawScale.get(),
                                    powerLawBaseReciprocal.get(),
                                    minCallProb.get(),
                                    fibN.get(),
                                    fibNValue.get(),
                                    slotsPerCall.get());
                        }))
                .when()
                .then(
                        runWithProvider(contractOpsFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get),
                        sleepFor(MS_TO_DRAIN_QUEUE),
                        withOpContext((spec, opLog) -> logResults()),
                        sourcing(() -> validateStorage.get() ? doStorageValidation() : noOp()));
    }

    private HapiSpecOperation doStorageValidation() {
        final var currentSlots = getABIFor(FUNCTION, "currentSlots", CONTRACT);
        return withOpContext((spec, opLog) -> {
            for (var contract : createdSoFar) {
                final var expectedStorage = contractStorage.get(contract);
                opLog.info("Expecting {} to have storage slots {}", contract, Arrays.toString(expectedStorage));
                final var lookup = contractCallLocal(contract, currentSlots)
                        .payingWith(GENESIS)
                        .nodePayment(ONE_HBAR)
                        .gas(GAS_TO_OFFER)
                        .has(resultWith().resultThruAbi(currentSlots, isLiteralArrayResult(expectedStorage)));
                allRunFor(spec, lookup);
            }
        });
    }

    @SuppressWarnings("java:S3776")
    private Function<HapiSpec, OpProvider> contractOpsFactory() {
        final String civilian = "civilian";
        final SplittableRandom random = new SplittableRandom(1_234_567L);
        final IntFunction<String> contractNameFn = i -> "contract" + i;

        final int r = powerLawBaseReciprocal.get();
        final DoubleUnaryOperator logBaseReciprocal = x -> Math.log(x) / Math.log(r);
        final int numDiscreteSizes = (int) ceil(logBaseReciprocal.applyAsDouble(numContracts.get() * (r - 1d)));

        double scale = powerLawScale.get();
        int numSlots = (int) Math.pow(scale, numDiscreteSizes - 1d) * smallestNumSlots.get();
        int numContractsWithThisManySlots = 1;
        int nextContractNum = 0;
        for (int i = 0; i < numDiscreteSizes; i++) {
            LOG.info("Will use {} contracts with {} slots", numContractsWithThisManySlots, numSlots);
            for (int j = 0; j < numContractsWithThisManySlots; j++) {
                final var thisContractNum = nextContractNum++;
                final var thisContract = contractNameFn.apply(thisContractNum);
                contractSlots.put(thisContract, numSlots);
                if (validateStorage.get()) {
                    final var slots = new BigInteger[numSlots];
                    Arrays.fill(slots, BigInteger.ZERO);
                    contractStorage.put(thisContract, slots);
                }
            }
            numSlots /= scale;
            numContractsWithThisManySlots *= r;
        }
        LOG.info("Will use {} contracts in total", nextContractNum);
        numContracts.set(nextContractNum);

        final var that = this;

        return spec -> new OpProvider() {
            @Override
            public List<HapiSpecOperation> suggestedInitializers() {
                final List<HapiSpecOperation> inits = new ArrayList<>();
                inits.add(uploadInitCode(CONTRACT));
                inits.add(
                        cryptoCreate(civilian).balance(100 * ONE_MILLION_HBARS).payingWith(GENESIS));
                return inits;
            }

            @Override
            public Optional<HapiSpecOperation> get() {
                final var aCallNum = submittedOps.incrementAndGet();
                if (aCallNum == 1) {
                    effStart.set(Instant.now());
                }

                final var choice = (createdSoFar.isEmpty() || random.nextDouble() > MIN_CALL_PROB)
                        ? contractNameFn.apply(random.nextInt(numContracts.get()))
                        : randomCallChoice.apply(random);
                final HapiSpecOperation op;
                if (createdSoFar.contains(choice)) {
                    final var n = slotsPerCall.get();
                    final int[] targets = new int[n];
                    final var m = contractSlots.get(choice);
                    for (int i = 0; i < n; i++) {
                        targets[i] = random.nextInt(m);
                    }
                    final var targetsDesc = Arrays.toString(targets);
                    if (verbose.get()) {
                        LOG.info("Calling {} with targets {} and fibN {}", choice, targetsDesc, fibN.get());
                    }

                    op = contractCall(CONTRACT, ADD_NTH_FIB, targets, fibN.get())
                            .noLogging()
                            .payingWith(civilian)
                            .gas(GAS_TO_OFFER)
                            .exposingGasTo((code, gas) -> {
                                if (verbose.get()) {
                                    LOG.info(
                                            "(Tried to) call {} (targets =" + " {}, fibN = {}) with {}" + " gas --> {}",
                                            choice,
                                            targetsDesc,
                                            fibN.get(),
                                            gas,
                                            code);
                                }
                                that.observeExposedGas(gas);
                                if (code == SUCCESS && validateStorage.get()) {
                                    final var curSlots = contractStorage.get(choice);
                                    final var newSlots = Arrays.copyOf(curSlots, m);
                                    for (int i = 0; i < n; i++) {
                                        final var j = targets[i];
                                        newSlots[j] = newSlots[j].add(fibNValue.get());
                                    }
                                    contractStorage.put(choice, newSlots);
                                }
                            })
                            .hasKnownStatusFrom(SUCCESS, CONTRACT_EXECUTION_EXCEPTION, INSUFFICIENT_GAS)
                            .deferStatusResolution();
                } else {
                    final var numSlots = contractSlots.get(choice);
                    op = contractCreate(CONTRACT, numSlots)
                            .payingWith(civilian)
                            .balance(0L)
                            .gas(GAS_TO_OFFER)
                            .exposingGasTo((code, gas) -> {
                                if (code == SUCCESS) {
                                    createdSoFar.add(choice);
                                }
                                LOG.info(
                                        "(Tried to) create {} ({} slots)" + " with {} gas --> {}",
                                        choice,
                                        numSlots,
                                        gas,
                                        code);
                                that.observeExposedGas(gas);
                            })
                            .noLogging()
                            .hasKnownStatusFrom(SUCCESS, INSUFFICIENT_GAS)
                            .deferStatusResolution();
                }

                return Optional.of(op);
            }
        };
    }

    private void observeExposedGas(final long gas) {
        final var bCallNum = completedOps.incrementAndGet();
        if (bCallNum == submittedOps.get()) {
            effEnd.set(Instant.now());
        }
        gasUsed.addAndGet(gas);
    }

    final HapiSpec justDoOne() {
        final var civilian = "civilian";
        final int[] firstTargets = {19, 24};
        final int[] secondTargets = {30, 31};
        final var firstCallTxn = "firstCall";
        final var secondCallTxn = "secondCall";
        final var createTxn = "creation";

        final AtomicReference<Instant> callStart = new AtomicReference<>();

        return defaultHapiSpec("JustDoOne")
                .given(
                        uploadInitCode(CONTRACT),
                        cryptoCreate(civilian).balance(100 * ONE_MILLION_HBARS).payingWith(GENESIS))
                .when(contractCreate(CONTRACT, 32)
                        .payingWith(civilian)
                        .balance(0L)
                        .gas(300_000L)
                        .exposingGasTo((code, gas) -> {
                            LOG.info("Got {} for creation using {} gas", code, gas);
                            this.observeExposedGas(gas);
                        })
                        .via(createTxn))
                .then(
                        sourcing(() -> {
                            callStart.set(Instant.now());
                            return noOp();
                        }),
                        contractCall(CONTRACT, ADD_NTH_FIB, firstTargets, FIBONACCI_NUM_TO_USE)
                                .payingWith(civilian)
                                .gas(300_000L)
                                .exposingGasTo((code, gas) -> {
                                    final var done = Instant.now();
                                    LOG.info(
                                            "Called FIRST in {}ms using {} gas --> {}",
                                            Duration.between(callStart.get(), done)
                                                    .toMillis(),
                                            gas,
                                            code);
                                    callStart.set(done);
                                })
                                .via(firstCallTxn),
                        contractCall(CONTRACT, ADD_NTH_FIB, secondTargets, FIBONACCI_NUM_TO_USE)
                                .payingWith(civilian)
                                .gas(300_000L)
                                .exposingGasTo((code, gas) -> {
                                    final var done = Instant.now();
                                    LOG.info(
                                            "Called SECOND in {}ms using {} gas --> {}",
                                            Duration.between(callStart.get(), done)
                                                    .toMillis(),
                                            gas,
                                            code);
                                })
                                .via(secondCallTxn));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }

    private static long fib(int n) {
        long ans = 0;
        long next = 1;

        while (n-- > 0) {
            final long prior = next;
            next += ans;
            ans = prior;
        }

        return ans;
    }
}
