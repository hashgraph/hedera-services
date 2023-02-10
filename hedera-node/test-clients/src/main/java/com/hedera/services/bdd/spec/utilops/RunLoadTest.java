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
package com.hedera.services.bdd.spec.utilops;

import static com.google.common.base.Stopwatch.createStarted;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_BALANCES_EXPORT_PERIOD_SECS;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_DURATION_CREATE_TOKEN_ASSOCIATION;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_DURATION_TOKEN_TRANSFER;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_EXPORT_BALANCES_ON_CLIENT_SIDE;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_INITIAL_BALANCE;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_MEMO_LENGTH;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_SUBMIT_MESSAGE_SIZE;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_SUBMIT_MESSAGE_SIZE_VAR;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_TEST_TOPIC_ID;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_TEST_TREASURE_START_ACCOUNT;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_TOTAL_SCHEDULED;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_TOTAL_TEST_ACCOUNTS;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_TOTAL_TEST_TOKENS;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_TOTAL_TEST_TOKEN_ACCOUNTS;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_TOTAL_TEST_TOPICS;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_TOTAL_TOKEN_ASSOCIATIONS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Stopwatch;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RunLoadTest extends UtilOp {
    private static final Logger log = LogManager.getLogger(RunLoadTest.class);
    private static final int DEFAULT_SECS_ALLOWED_BELOW_TOLERANCE = 0;
    private static final int DEFAULT_TPS_TARGET = 500;
    private static final int DEFAULT_TPS_TOLERANCE_PERCENTAGE = 5;
    private static final long DEFAULT_DURATION = 30;
    private static final TimeUnit DEFAULT_DURATION_UNIT = TimeUnit.SECONDS;
    private static final int DEFAULT_THREADS = 1;

    private DoubleSupplier targetTps = () -> DEFAULT_TPS_TARGET;
    private IntSupplier memoLength = () -> DEFAULT_MEMO_LENGTH;
    private IntSupplier tpsTolerancePercentage = () -> DEFAULT_TPS_TOLERANCE_PERCENTAGE;
    private IntSupplier secsAllowedBelowTolerance = () -> DEFAULT_SECS_ALLOWED_BELOW_TOLERANCE;
    private LongSupplier testDuration = () -> DEFAULT_DURATION;
    private Supplier<TimeUnit> ofUnit = () -> DEFAULT_DURATION_UNIT;
    private IntSupplier threads = () -> DEFAULT_THREADS;
    private IntSupplier hcsSubmitMessageSize = () -> DEFAULT_SUBMIT_MESSAGE_SIZE;
    private IntSupplier hcsSubmitMessageSizeVar = () -> DEFAULT_SUBMIT_MESSAGE_SIZE_VAR;
    private IntSupplier totalTestAccounts = () -> DEFAULT_TOTAL_TEST_ACCOUNTS;
    private IntSupplier totalTestTopics = () -> DEFAULT_TOTAL_TEST_TOPICS;
    private IntSupplier totalTestTokens = () -> DEFAULT_TOTAL_TEST_TOKENS;
    private IntSupplier testTreasureStartAccount = () -> DEFAULT_TEST_TREASURE_START_ACCOUNT;
    private IntSupplier totalTestTokenAccounts = () -> DEFAULT_TOTAL_TEST_TOKEN_ACCOUNTS;
    private IntSupplier durationCreateTokenAssociation =
            () -> DEFAULT_DURATION_CREATE_TOKEN_ASSOCIATION;
    private IntSupplier durationTokenTransfer = () -> DEFAULT_DURATION_TOKEN_TRANSFER;
    private IntSupplier totalTokenAssociations = () -> DEFAULT_TOTAL_TOKEN_ASSOCIATIONS;
    private IntSupplier totalScheduled = () -> DEFAULT_TOTAL_SCHEDULED;
    private LongSupplier initialBalance = () -> DEFAULT_INITIAL_BALANCE;
    private IntSupplier testTopicId = () -> DEFAULT_TEST_TOPIC_ID;
    private IntSupplier balancesExportPeriodSecs = () -> DEFAULT_BALANCES_EXPORT_PERIOD_SECS;
    private BooleanSupplier clientToExportBalances = () -> DEFAULT_EXPORT_BALANCES_ON_CLIENT_SIDE;

    private final Supplier<HapiSpecOperation[]> opSource;

    private AtomicLong totalOpsAllThread = new AtomicLong();

    public RunLoadTest tps(DoubleSupplier targetTps) {
        this.targetTps = targetTps;
        return this;
    }

    public RunLoadTest setMemoLength(IntSupplier memoLength) {
        this.memoLength = memoLength;
        return this;
    }

    public RunLoadTest tolerance(IntSupplier tpsTolerance) {
        this.tpsTolerancePercentage = tpsTolerance;
        return this;
    }

    public RunLoadTest allowedSecsBelow(IntSupplier allowedSecsBelow) {
        this.secsAllowedBelowTolerance = allowedSecsBelow;
        return this;
    }

    public RunLoadTest setNumberOfThreads(IntSupplier numberOfThreads) {
        this.threads = numberOfThreads;
        return this;
    }

    public RunLoadTest setTotalTestAccounts(IntSupplier totalTestAccounts) {
        this.totalTestAccounts = totalTestAccounts;
        return this;
    }

    public RunLoadTest setTotalTestTopics(IntSupplier totalTestTopics) {
        this.totalTestTopics = totalTestTopics;
        return this;
    }

    public RunLoadTest setTotalTestTokens(IntSupplier totalTestTokens) {
        this.totalTestTokens = totalTestTokens;
        return this;
    }

    public RunLoadTest setTotalTokenAssociations(IntSupplier totalTokenAssociations) {
        this.totalTokenAssociations = totalTokenAssociations;
        return this;
    }

    public RunLoadTest setTotalScheduled(IntSupplier totalScheduled) {
        this.totalScheduled = totalScheduled;
        return this;
    }

    public RunLoadTest setDurationTokenTransfer(IntSupplier durationTokenTransfer) {
        this.durationTokenTransfer = durationTokenTransfer;
        return this;
    }

    public RunLoadTest setDurationCreateTokenAssociation(
            IntSupplier durationCreateTokenAssociation) {
        this.durationCreateTokenAssociation = durationCreateTokenAssociation;
        return this;
    }

    public RunLoadTest setTestTreasureStartAccount(IntSupplier testTreasureStartAccount) {
        this.testTreasureStartAccount = testTreasureStartAccount;
        return this;
    }

    public RunLoadTest setTotalTestTokenAccounts(IntSupplier totalTestTokenAccts) {
        this.totalTestTokenAccounts = totalTestTokenAccts;
        return this;
    }

    public RunLoadTest setHCSSubmitMessageSize(IntSupplier submitMessageSize) {
        this.hcsSubmitMessageSize = submitMessageSize;
        return this;
    }

    public RunLoadTest setHCSSubmitMessageSizeVar(IntSupplier submitMessageSizeVar) {
        this.hcsSubmitMessageSizeVar = submitMessageSizeVar;
        return this;
    }

    public RunLoadTest setInitialBalance(LongSupplier initialBalance) {
        this.initialBalance = initialBalance;
        return this;
    }

    public RunLoadTest setTestTopicId(IntSupplier testTopicId) {
        this.testTopicId = testTopicId;
        return this;
    }

    public RunLoadTest setBalancesExportPeriodSecs(IntSupplier balancesExportPeriodSecs) {
        this.balancesExportPeriodSecs = balancesExportPeriodSecs;
        return this;
    }

    public RunLoadTest setClientToExportBalances(BooleanSupplier clientToExportBalances) {
        this.clientToExportBalances = clientToExportBalances;
        return this;
    }

    public RunLoadTest lasting(LongSupplier duration, Supplier<TimeUnit> ofUnit) {
        this.testDuration = duration;
        this.ofUnit = ofUnit;
        return this;
    }

    public RunLoadTest(Supplier<HapiSpecOperation[]> opSource) {
        this.opSource = opSource;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) {
        return threadMode(spec);
    }

    protected boolean threadMode(HapiSpec spec) {
        int numberOfThreads = threads.getAsInt();
        Thread[] threadClients = new Thread[numberOfThreads];

        // Dynamically instantiate test case thread and pass arguments to it
        for (int k = 0; k < numberOfThreads; k++) {
            threadClients[k] = new Thread(() -> testRun(spec));
            threadClients[k].setName("thread" + k);
        }

        for (int k = 0; k < numberOfThreads; k++) {
            threadClients[k].start();
        }
        for (int k = 0; k < numberOfThreads; k++) {
            try {
                threadClients[k].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        log.info(
                "Total Ops submitted {}, actual TPS {}",
                totalOpsAllThread.get(),
                totalOpsAllThread.get() / ((float) (testDuration.getAsLong() * 60)));
        return false;
    }

    void testRun(HapiSpec spec) {
        double _targetTps = targetTps.getAsDouble();
        long _testDuration = testDuration.getAsLong();
        TimeUnit _ofUnit = ofUnit.get();
        int totalOps = 0;
        float currentTPS = 0;
        Stopwatch duration = createStarted();

        boolean reported = false;
        Stopwatch statDuration = duration;
        int submitOps = 0; // submitted tran during the stat window
        while (duration.elapsed(_ofUnit) < _testDuration) {
            HapiSpecOperation[] ops = opSource.get();
            allRunFor(spec, ops);
            submitOps += ops.length;
            totalOps += ops.length;

            long elapsedMS = statDuration.elapsed(MILLISECONDS);
            currentTPS = submitOps / (elapsedMS * 0.001f);

            if (statDuration.elapsed(SECONDS) % 10 == 0) { // report periodically
                if (!reported) {
                    log.info(
                            "Thread {} ops {} current TPS {}",
                            Thread.currentThread().getName(),
                            submitOps,
                            currentTPS);
                    reported = true;
                    submitOps = 0;
                    statDuration = createStarted();
                }
            } else {
                reported = false;
            }
            try {
                if (currentTPS > _targetTps) {
                    long pauseMillieSeconds =
                            (long) ((submitOps / (float) _targetTps) * 1000 - elapsedMS);
                    Thread.sleep(Math.max(5, pauseMillieSeconds));
                }
            } catch (InterruptedException irrelevant) {
            }
        }
        log.info(
                "Thread {} final ops {} in {} seconds, TPS {} ",
                Thread.currentThread().getName(),
                totalOps,
                duration.elapsed(SECONDS),
                currentTPS);

        totalOpsAllThread.addAndGet(totalOps);
    }
}
