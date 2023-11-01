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

package com.hedera.services.bdd.spec;

import static com.hedera.services.bdd.junit.RecordStreamAccess.RECORD_STREAM_ACCESS;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSources;
import static com.hedera.services.bdd.spec.HapiPropertySource.inPriorityOrder;
import static com.hedera.services.bdd.spec.HapiSpec.CostSnapshotMode.COMPARE;
import static com.hedera.services.bdd.spec.HapiSpec.CostSnapshotMode.TAKE;
import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.ERROR;
import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.FAILED;
import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.FAILED_AS_EXPECTED;
import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.PASSED;
import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.PASSED_UNEXPECTEDLY;
import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.PENDING;
import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.RUNNING;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.infrastructure.HapiApiClients.clientsFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.utilops.UtilStateChange.initializeEthereumAccountForSpec;
import static com.hedera.services.bdd.spec.utilops.UtilStateChange.isEthereumAccountCreatedForSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.remembering;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.ETH_SUFFIX;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.joining;

import com.google.common.base.MoreObjects;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.fees.FeesAndRatesProvider;
import com.hedera.services.bdd.spec.fees.Payment;
import com.hedera.services.bdd.spec.infrastructure.HapiApiClients;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.persistence.EntityManager;
import com.hedera.services.bdd.spec.props.MapPropertySource;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnFactory;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.spec.utilops.streams.RecordAssertions;
import com.hedera.services.bdd.spec.utilops.streams.assertions.EventualRecordStreamAssertion;
import com.hedera.services.stream.proto.AllAccountBalances;
import com.hedera.services.stream.proto.SingleAccountBalances;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransferList;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.SplittableRandom;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiSpec implements Runnable {
    private static final long FIRST_NODE_ACCOUNT_NUM = 3L;
    private static final int NUM_IN_USE_NODE_ACCOUNTS = 4;
    private static final TransferList DEFAULT_NODE_BALANCE_FUNDING = TransferList.newBuilder()
            .addAllAccountAmounts(Stream.concat(
                            Stream.of(AccountAmount.newBuilder()
                                    .setAmount(-NUM_IN_USE_NODE_ACCOUNTS * ONE_HBAR)
                                    .setAccountID(AccountID.newBuilder().setAccountNum(2L))
                                    .build()),
                            LongStream.range(FIRST_NODE_ACCOUNT_NUM, FIRST_NODE_ACCOUNT_NUM + NUM_IN_USE_NODE_ACCOUNTS)
                                    .mapToObj(number -> AccountAmount.newBuilder()
                                            .setAmount(ONE_HBAR)
                                            .setAccountID(AccountID.newBuilder().setAccountNum(number))
                                            .build()))
                    .toList())
            .build();
    private static final AtomicLong NEXT_AUTO_SCHEDULE_NUM = new AtomicLong(1);
    private static final SplittableRandom RANDOM = new SplittableRandom();
    private static final String CI_PROPS_FLAG_FOR_NO_UNRECOVERABLE_NETWORK_FAILURES = "suppressNetworkFailures";
    private static final ThreadPoolExecutor THREAD_POOL =
            new ThreadPoolExecutor(0, 10_000, 250, MILLISECONDS, new SynchronousQueue<>());

    static final Logger log = LogManager.getLogger(HapiSpec.class);

    public enum SpecStatus {
        PENDING,
        RUNNING,
        PASSED,
        FAILED,
        FAILED_AS_EXPECTED,
        PASSED_UNEXPECTEDLY,
        ERROR
    }

    public enum CostSnapshotMode {
        OFF,
        TAKE,
        COMPARE
    }

    public enum UTF8Mode {
        FALSE,
        TRUE
    }

    private record Failure(Throwable cause, String opDescription) {
        private static String LOG_TPL = "%s when executing %s";

        @Override
        public String toString() {
            return String.format(LOG_TPL, cause.getMessage(), opDescription);
        }
    }

    private final Map<String, Long> privateKeyToNonce = new HashMap<>();

    public boolean isOnlySpecToRunInSuite() {
        return onlySpecToRunInSuite;
    }

    private final boolean onlySpecToRunInSuite;
    private final List<String> propertiesToPreserve;
    List<Payment> costs = new ArrayList<>();
    List<Payment> costSnapshot = Collections.emptyList();
    String name;
    String suitePrefix = "";
    SpecStatus status;
    TxnFactory txnFactory;
    KeyFactory keyFactory;
    EntityManager entities;
    FeeCalculator feeCalculator;
    FeesAndRatesProvider ratesProvider;
    HapiSpecSetup hapiSetup;
    HapiApiClients hapiClients;
    HapiSpecRegistry hapiRegistry;
    HapiSpecOperation[] given;
    HapiSpecOperation[] when;
    HapiSpecOperation[] then;
    AtomicInteger adhoc = new AtomicInteger(0);
    AtomicInteger numLedgerOpsExecuted = new AtomicInteger(0);
    AtomicBoolean allOpsSubmitted = new AtomicBoolean(false);
    ThreadPoolExecutor finalizingExecutor;
    List<Consumer<Integer>> ledgerOpCountCallbacks = new ArrayList<>();
    CompletableFuture<Void> finalizingFuture;
    AtomicReference<Optional<Failure>> finishingError = new AtomicReference<>(Optional.empty());
    BlockingQueue<HapiSpecOpFinisher> pendingOps = new PriorityBlockingQueue<>();
    EnumMap<ResponseCodeEnum, AtomicInteger> precheckStatusCounts = new EnumMap<>(ResponseCodeEnum.class);
    EnumMap<ResponseCodeEnum, AtomicInteger> finalizedStatusCounts = new EnumMap<>(ResponseCodeEnum.class);

    List<SingleAccountBalances> accountBalances = new ArrayList<>();

    /**
     * When this spec's final status is {@code FAILED}, contains the information on the failed
     * assertion that terminated {@code exec()}.
     */
    @Nullable
    private Failure failure = null;

    public static ThreadPoolExecutor getCommonThreadPool() {
        return THREAD_POOL;
    }

    public void adhocIncrement() {
        adhoc.getAndIncrement();
    }

    public int finalAdhoc() {
        return adhoc.get();
    }

    public int numPendingOps() {
        return pendingOps.size();
    }

    public int numLedgerOps() {
        return numLedgerOpsExecuted.get();
    }

    public synchronized void addLedgerOpCountCallback(Consumer<Integer> callback) {
        ledgerOpCountCallbacks.add(callback);
    }

    public void incrementNumLedgerOps() {
        int newNumLedgerOps = numLedgerOpsExecuted.incrementAndGet();
        ledgerOpCountCallbacks.forEach(c -> c.accept(newNumLedgerOps));
    }

    public synchronized void saveSingleAccountBalances(SingleAccountBalances sab) {
        accountBalances.add(sab);
    }

    public void exportAccountBalances(Supplier<String> dir) {
        AllAccountBalances.Builder allAccountBalancesBuilder =
                AllAccountBalances.newBuilder().addAllAllAccounts(accountBalances);
        try (FileOutputStream fout = FileUtils.openOutputStream(new File(dir.get()))) {
            allAccountBalancesBuilder.build().writeTo(fout);
        } catch (IOException e) {
            log.error(String.format("Could not export to '%s'!", dir), e);
            return;
        }

        log.info("Export {} account balances registered to file {}", accountBalances.size(), dir);
    }

    public void updatePrecheckCounts(ResponseCodeEnum finalStatus) {
        precheckStatusCounts
                .computeIfAbsent(finalStatus, ignore -> new AtomicInteger(0))
                .incrementAndGet();
    }

    public void updateResolvedCounts(ResponseCodeEnum finalStatus) {
        finalizedStatusCounts
                .computeIfAbsent(finalStatus, ignore -> new AtomicInteger(0))
                .incrementAndGet();
    }

    public Map<ResponseCodeEnum, AtomicInteger> finalizedStatusCounts() {
        return finalizedStatusCounts;
    }

    public Map<ResponseCodeEnum, AtomicInteger> precheckStatusCounts() {
        return precheckStatusCounts;
    }

    public String getName() {
        return name;
    }

    public void appendToName(String postfix) {
        this.name = this.name + postfix;
    }

    public String getSuitePrefix() {
        return suitePrefix;
    }

    public String logPrefix() {
        return "'" + name + "' - ";
    }

    public SpecStatus getStatus() {
        return status;
    }

    public SpecStatus getExpectedFinalStatus() {
        return setup().expectedFinalStatus();
    }

    public HapiSpec setSuitePrefix(String suitePrefix) {
        this.suitePrefix = suitePrefix;
        return this;
    }

    public static boolean ok(HapiSpec spec) {
        return spec.getStatus() == spec.getExpectedFinalStatus();
    }

    public static boolean notOk(HapiSpec spec) {
        return !ok(spec);
    }

    @Override
    public void run() {
        if (!init()) {
            return;
        }

        List<HapiSpecOperation> ops;

        if (!suitePrefix.endsWith(ETH_SUFFIX)) {
            ops = Stream.of(given, when, then).flatMap(Arrays::stream).toList();
        } else {
            if (!isEthereumAccountCreatedForSpec(this)) {
                initializeEthereumAccountForSpec(this);
            }
            ops = UtilVerbs.convertHapiCallsToEthereumCalls(
                    Stream.of(given, when, then).flatMap(Arrays::stream).toList());
        }

        exec(ops);

        if (hapiSetup.costSnapshotMode() == TAKE) {
            takeCostSnapshot();
        } else if (hapiSetup.costSnapshotMode() == COMPARE) {
            compareWithSnapshot();
        }

        nullOutInfrastructure();
    }

    @Nullable
    public Failure getCause() {
        return failure;
    }

    public long getNonce(final String privateKey) {
        return privateKeyToNonce.getOrDefault(privateKey, 0L);
    }

    public void incrementNonce(final String privateKey) {
        var updatedNonce = (privateKeyToNonce.getOrDefault(privateKey, 0L)) + 1;
        privateKeyToNonce.put(privateKey, updatedNonce);
    }

    public boolean isUsingEthCalls() {
        return suitePrefix.endsWith(ETH_SUFFIX);
    }

    public boolean tryReinitializingFees() {
        int secsWait = 0;
        if (ciPropsSource != null) {
            String secs = setup().ciPropertiesMap().get("secondsWaitingServerUp");
            if (secs != null) {
                secsWait = Integer.parseInt(secs);
                secsWait = Math.max(secsWait, 0);
            }
        }
        while (secsWait >= 0) {
            try {
                ratesProvider.init();
                feeCalculator.init();
                return true;
            } catch (final IOException t) {
                secsWait--;
                if (secsWait < 0) {
                    log.error("Fees failed to initialize! Please check if server is down...", t);
                    return false;
                } else {
                    log.warn(
                            "Hedera service is not reachable. Will wait and try connect again for" + " {} seconds...",
                            secsWait);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                        log.error("Interrupted while waiting to connect to server");
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (IllegalStateException | ReflectiveOperationException | GeneralSecurityException e) {
                status = ERROR; // These are unrecoverable; save a lot of time and just fail the test.
                log.error("Irrecoverable error in test nodes or client JVM. Unable to continue.", e);
                return false;
            }
        }
        return false;
    }

    private boolean init() {
        if (!tryReinitializingFees()) {
            status = ERROR;
            return false;
        }
        if (hapiSetup.costSnapshotMode() == COMPARE) {
            try {
                loadCostSnapshot();
            } catch (RuntimeException ignore) {
                status = ERROR;
                log.warn("Failed to load cost snapshot.", ignore);
                return false;
            }
        }
        if (hapiSetup.requiresPersistentEntities()) {
            entities = new EntityManager(this);
            if (!entities.init()) {
                status = ERROR;
                return false;
            }
        }
        return true;
    }

    private void tearDown() {
        if (finalizingExecutor != null) {
            finalizingExecutor.shutdown();
        }
    }

    @SuppressWarnings("java:S2629")
    private void exec(List<HapiSpecOperation> ops) {
        if (status == ERROR) {
            log.warn("'{}' failed to initialize, being skipped!", name);
            return;
        }

        if (hapiSetup.requiresPersistentEntities()) {
            List<HapiSpecOperation> creationOps = entities.requiredCreations();
            if (!creationOps.isEmpty()) {
                log.info("Inserting {} required creations to establish persistent entities.", creationOps.size());
                ops = Stream.concat(creationOps.stream(), ops.stream()).toList();
            }
        }
        log.info("{} test suite started !", logPrefix());

        status = RUNNING;
        if (hapiSetup.statusDeferredResolvesDoAsync()) {
            startFinalizingOps();
        }
        final var shouldPreserveProps = !propertiesToPreserve.isEmpty();
        final Map<String, String> preservedProperties = shouldPreserveProps ? new HashMap<>() : Collections.emptyMap();
        if (shouldPreserveProps) {
            ops = new ArrayList<>(ops);
            ops.add(0, remembering(preservedProperties, propertiesToPreserve));
        }
        final var autoScheduled = setup().txnTypesToSchedule();
        if (!autoScheduled.isEmpty()) {
            log.info("Auto-scheduling {}", autoScheduled);
        }
        @Nullable List<EventualRecordStreamAssertion> assertions = null;
        // No matter what, just distribute some hbar to the default node accounts
        cryptoTransfer((ignore, builder) -> builder.setTransfers(DEFAULT_NODE_BALANCE_FUNDING))
                .deferStatusResolution()
                .hasAnyStatusAtAll()
                .execFor(this);
        for (HapiSpecOperation op : ops) {
            if (!autoScheduled.isEmpty() && op.shouldSkipWhenAutoScheduling(autoScheduled)) {
                continue;
            }
            if (op instanceof EventualRecordStreamAssertion recordStreamAssertion) {
                if (assertions == null) {
                    assertions = new ArrayList<>();
                }
                assertions.add(recordStreamAssertion);
            } else if (op instanceof HapiTxnOp txn && autoScheduled.contains(txn.type())) {
                op = autoScheduledSequenceFor(txn);
            }
            Optional<Throwable> error = op.execFor(this);
            Failure asyncFailure = null;
            if (error.isPresent() || (asyncFailure = finishingError.get().orElse(null)) != null) {
                status = FAILED;
                final var failedOp = op;
                failure = error.map(t -> new Failure(t, failedOp.toString())).orElse(asyncFailure);
                break;
            } else {
                log.info("'{}' finished initial execution of {}", name, op);
            }
        }
        allOpsSubmitted.set(true);
        status = (status != FAILED) ? PASSED : FAILED;

        if (status == PASSED) {
            finishFinalizingOps();
            if (finishingError.get().isPresent()) {
                status = FAILED;
            }
        }
        finalizingFuture.join();
        if (!preservedProperties.isEmpty()) {
            final var restoration = overridingAllOf(preservedProperties);
            Optional<Throwable> error = restoration.execFor(this);
            error.ifPresent(t -> {
                status = FAILED;
                failure = new Failure(t, restoration.toString());
            });
        }
        if (status == PASSED) {
            final var maybeRecordStreamError = checkRecordStream(assertions);
            if (maybeRecordStreamError.isPresent()) {
                status = FAILED;
                failure = maybeRecordStreamError.get();
            }
        } else if (assertions != null) {
            assertions.forEach(EventualRecordStreamAssertion::unsubscribe);
            RECORD_STREAM_ACCESS.stopMonitorIfNoSubscribers();
        }

        tearDown();
        log.info("{}final status: {}!", logPrefix(), status);

        if (hapiSetup.requiresPersistentEntities() && hapiSetup.updateManifestsForCreatedPersistentEntities()) {
            entities.updateCreatedEntityManifests();
        }
    }

    /**
     * Given a transaction, creates a sequence of operations that schedules the
     * transaction and validates its execution and record. This sequence of
     * operations looks like,
     * <ol>
     *     <li>A {@code ScheduleCreate} using the default payer, and including
     *     zero or more of the required signing keys for the transaction.</li>
     *     <li>Zero or more {@code ScheduleSign}'s targeting the created
     *     schedule, each including one or more of the required signing keys
     *     not used with the {@code ScheduleCreate}.</li>
     *     <li>A {@code ScheduleInfo} query that verifies the schedule has been
     *     executed (unless the expected pre-check or status of the transaction
     *     was {@code INVALID_SIGNATURE}, in which case the schedule shouldn't
     *     have been executed.)</li>
     *     <li>A {@code GetTransactionRecord} query that verifies the triggered
     *     transaction had the expected status (unless the expected pre-check
     *     or status of the transaction was {@code INVALID_SIGNATURE}).</li>
     * </ol>
     *
     * @param txn the transaction to auto-schedule
     * @return the sequence of operations that auto-schedules the transaction
     */
    private HapiSpecOperation autoScheduledSequenceFor(final HapiTxnOp<?> txn) {
        // For the signatures to have the expected semantics, we must
        // incorporate any signature control overrides into this spec
        final var sigControlOverrides = txn.setKeyControlOverrides(this);
        if (!sigControlOverrides.isEmpty()) {
            sigControlOverrides.forEach((key, control) -> keys().setControl(key, control));
        }

        final var scheduleCreatePayerKey = registry().getKey(DEFAULT_PAYER);
        final var signingKeys = txn.signersToUseFor(this).stream()
                // Skip empty keys, which will have no required signatures, but
                // should be rejected at consensus in most cases
                .filter(key -> !isEmpty(key))
                // The ScheduleCreate uses the default payer key, so we should
                // ignore it for signing requirement purposes
                .filter(key -> !scheduleCreatePayerKey.equals(key))
                // Without this we would commonly repeat the payer key and run
                // into SCHEDULE_ALREADY_EXECUTED
                .distinct()
                .toList();
        final var numKeys = signingKeys.size();
        final var numSignTxns = RANDOM.nextInt(numKeys + 1);
        final var indices = createAndSignIndicesGiven(numKeys, numSignTxns);
        // One slot for the ScheduleCreate, one for each ScheduleSign,
        // one for the GetScheduleInfo, and one for the GetTxnRecord
        final var orderedOps = new HapiSpecOperation[1 + numSignTxns + 2];
        final var num = NEXT_AUTO_SCHEDULE_NUM.getAndIncrement();
        final var schedule = "autoScheduled" + num;
        final var creation = "autoScheduleCreation" + num;
        orderedOps[0] = scheduleCreate(schedule, txn)
                .alsoSigningWithExplicit(signingKeys.subList(indices.get(0), indices.get(1)))
                .savingExpectedScheduledTxnId()
                .via(creation);
        for (int i = 1, n = indices.size(); i < n - 1; i++) {
            orderedOps[i] = scheduleSign(schedule)
                    .alsoSigningWithExplicit(signingKeys.subList(indices.get(i), indices.get(i + 1)))
                    // It's likely that some of the top-level transaction's signing keys will already
                    // be present (e.g. the default payer's signature), so we accommodate that here
                    // by adding NO_NEW_VALID_SIGNATURES to the list of acceptable statuses
                    .hasKnownStatusFrom(SUCCESS, NO_NEW_VALID_SIGNATURES);
        }

        final var expectedStatus =
                (txn.getExpectedPrecheck() == OK) ? txn.getExpectedStatus() : txn.getExpectedPrecheck();
        final var scheduleStateAssertion = getScheduleInfo(schedule);
        // If the original transaction was supposed to fail with INVALID_SIGNATURE,
        // then the schedule should not have been executed
        if (expectedStatus != INVALID_SIGNATURE) {
            scheduleStateAssertion.isExecuted();
        } else {
            scheduleStateAssertion.isNotExecuted();
        }
        orderedOps[orderedOps.length - 2] = scheduleStateAssertion;

        if (expectedStatus != INVALID_SIGNATURE) {
            final var recordAssertion = getTxnRecord(creation)
                    .scheduledBy(schedule)
                    .hasPriority(recordWith().status(expectedStatus));
            orderedOps[orderedOps.length - 1] = recordAssertion;
        } else {
            // If the original transaction was supposed to fail with INVALID_SIGNATURE, there
            // will be no scheduled transaction record to retrieve, so just use noOp()
            orderedOps[orderedOps.length - 1] = noOp();
        }
        return blockingOrder(orderedOps);
    }

    private boolean isEmpty(final Key key) {
        if (key.hasKeyList()) {
            return key.getKeyList().getKeysCount() == 0
                    || key.getKeyList().getKeysList().stream().allMatch(this::isEmpty);
        } else if (key.hasThresholdKey()) {
            return key.getThresholdKey().getKeys().getKeysCount() == 0
                    || key.getThresholdKey().getKeys().getKeysList().stream().allMatch(this::isEmpty);
        } else {
            return false;
        }
    }

    /**
     * Given the number of keys that will be used to sign the transaction, and the number of
     * {@code ScheduleSign} transactions that will be executed, create a list of indices that
     * can be used to choose which keys to sign with in both the initial {@code ScheduleCreate},
     * and any {@code ScheduleSign} transactions.
     *
     * @param numKeys the number of keys that will be used to sign the transaction
     * @param numSignTxns the number of {@code ScheduleSign} transactions that will be executed
     * @return a list of indices that can be used to choose signing keys
     */
    private static List<Integer> createAndSignIndicesGiven(final int numKeys, final int numSignTxns) {
        final List<Integer> endIndices = new ArrayList<>();
        endIndices.add(numKeys);
        int remainingSkipsAllowed = numKeys - numSignTxns;
        for (int i = 0; i < numSignTxns; i++) {
            final var skips = remainingSkipsAllowed > 0 ? RANDOM.nextInt(remainingSkipsAllowed) : 0;
            remainingSkipsAllowed -= skips;
            final var curIndex = endIndices.get(0);
            final var nextEndIndex = curIndex - skips - 1;
            endIndices.add(0, nextEndIndex);
        }
        endIndices.add(0, 0);
        return endIndices;
    }

    private Optional<Failure> checkRecordStream(@Nullable final List<EventualRecordStreamAssertion> assertions) {
        if (assertions == null) {
            return Optional.empty();
        }
        log.info("Checking record stream for {} assertions", assertions.size());
        Optional<Failure> answer = Optional.empty();
        // Keep submitting transactions to close record files (in almost every case, just
        // one file will need to be closed, since it's very rare to have a long-running spec)
        final var backgroundTraffic = THREAD_POOL.submit(() -> {
            while (true) {
                try {
                    RecordAssertions.triggerAndCloseAtLeastOneFile(this);
                } catch (final InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
        for (final var assertion : assertions) {
            log.info("Checking record stream for {}", assertion);
            try {
                // Each blocks until the assertion passes or times out
                assertion.assertHasPassed();
            } catch (final Throwable t) {
                log.warn("{}{} failed", logPrefix(), assertion, t);
                answer = Optional.of(new Failure(t, assertion.toString()));
                break;
            }
        }

        backgroundTraffic.cancel(true);
        RECORD_STREAM_ACCESS.stopMonitorIfNoSubscribers();
        return answer;
    }

    private void startFinalizingOps() {
        finalizingExecutor = new ThreadPoolExecutor(
                hapiSetup.numOpFinisherThreads(),
                hapiSetup.numOpFinisherThreads(),
                0,
                TimeUnit.SECONDS,
                new SynchronousQueue<>());
        finalizingFuture = allOf(IntStream.range(0, hapiSetup.numOpFinisherThreads())
                .mapToObj(ignore -> runAsync(
                        () -> {
                            while (true) {
                                HapiSpecOpFinisher op = pendingOps.poll();
                                if (op != null) {
                                    if (status != FAILED && finishingError.get().isEmpty()) {
                                        try {
                                            op.finishFor(this);
                                        } catch (Throwable t) {
                                            log.warn("{}{} failed!", logPrefix(), op);
                                            finishingError.set(Optional.of(new Failure(t, op.toString())));
                                        }
                                    }
                                } else {
                                    if (allOpsSubmitted.get()) {
                                        break;
                                    } else {
                                        try {
                                            MILLISECONDS.sleep(500);
                                        } catch (InterruptedException ignored) {
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                }
                            }
                        },
                        finalizingExecutor))
                .toArray(CompletableFuture[]::new));
    }

    @SuppressWarnings("java:S2629")
    private void finishFinalizingOps() {
        if (pendingOps.isEmpty()) {
            return;
        }
        log.info("{}executed {} ledger ops.", logPrefix(), numLedgerOpsExecuted.get());
        log.info("{}now finalizing {} more pending ops...", logPrefix(), pendingOps.size());
        if (!hapiSetup.statusDeferredResolvesDoAsync()) {
            startFinalizingOps();
        }
    }

    @SuppressWarnings({"java:S899", "ResultOfMethodCallIgnored"})
    public void offerFinisher(HapiSpecOpFinisher finisher) {
        pendingOps.offer(finisher);
    }

    public FeeCalculator fees() {
        return feeCalculator;
    }

    public TxnFactory txns() {
        return txnFactory;
    }

    public KeyFactory keys() {
        return keyFactory;
    }

    public EntityManager persistentEntities() {
        return entities;
    }

    public HapiSpecRegistry registry() {
        return hapiRegistry;
    }

    public HapiSpecSetup setup() {
        return hapiSetup;
    }

    public HapiApiClients clients() {
        return hapiClients;
    }

    public FeesAndRatesProvider ratesProvider() {
        return ratesProvider;
    }

    private static Map<String, String> ciPropsSource;
    private static String dynamicNodes;
    private static String tlsFromCi;
    private static String txnFromCi;
    private static String defaultPayer;
    private static String nodeSelectorFromCi;
    private static String defaultNodeAccount;
    private static Map<String, String> otherOverrides;
    private static boolean runningInCi = false;

    public static boolean isRunningInCi() {
        return runningInCi;
    }

    public static void runInCiMode(
            String nodes,
            String payer,
            String suggestedNode,
            String envTls,
            String envTxn,
            String envNodeSelector,
            Map<String, String> overrides) {
        runningInCi = true;
        tlsFromCi = envTls;
        txnFromCi = envTxn;
        dynamicNodes = nodes;
        defaultPayer = payer;
        defaultNodeAccount = String.format("0.0.%s", suggestedNode);
        nodeSelectorFromCi = envNodeSelector;
        otherOverrides = overrides;
        ciPropsSource = null;
    }

    public static Def.Given defaultHapiSpec(String name) {
        return internalDefaultHapiSpec(name, false, Collections.emptyList());
    }

    public static Def.PropertyPreserving propertyPreservingHapiSpec(final String name) {
        return (String... props) -> internalDefaultHapiSpec(name, false, Arrays.asList(props));
    }

    public static Def.PropertyPreserving onlyPropertyPreservingHapiSpec(final String name) {
        return (String... props) -> internalDefaultHapiSpec(name, true, Arrays.asList(props));
    }

    public static Def.Given onlyDefaultHapiSpec(final String name) {
        return internalDefaultHapiSpec(name, true, Collections.emptyList());
    }

    private static Def.Given internalDefaultHapiSpec(
            final String name, final boolean isOnly, final List<String> propertiesToPreserve) {
        final Stream<Map<String, String>> prioritySource = runningInCi ? Stream.of(ciPropOverrides()) : Stream.empty();
        return customizedHapiSpec(isOnly, name, prioritySource, propertiesToPreserve)
                .withProperties();
    }

    public static Map<String, String> ciPropOverrides() {
        if (ciPropsSource == null) {
            // Make sure there is a port number specified for each node. Note that the node specification
            // is expected to be in the form of <host>[:<port>[:<account>]]. If port is not specified we will
            // default to 50211, if account is not specified, we leave it off.
            dynamicNodes = Stream.of(dynamicNodes.split(","))
                    .map(s -> s.contains(":") ? s : s + ":" + 50211)
                    .collect(joining(","));
            String ciPropertiesMap =
                    Optional.ofNullable(System.getenv("CI_PROPERTIES_MAP")).orElse("");
            log.info("CI_PROPERTIES_MAP: {}", ciPropertiesMap);
            ciPropsSource = new HashMap<>();
            ciPropsSource.put("node.selector", nodeSelectorFromCi);
            ciPropsSource.put("nodes", dynamicNodes);
            ciPropsSource.put("default.payer", defaultPayer);
            ciPropsSource.put("default.node", defaultNodeAccount);
            ciPropsSource.put("ci.properties.map", ciPropertiesMap);
            ciPropsSource.put("tls", tlsFromCi);
            ciPropsSource.put("txn", txnFromCi);

            final var explicitCiProps = MapPropertySource.parsedFromCommaDelimited(ciPropertiesMap);
            if (explicitCiProps.has(CI_PROPS_FLAG_FOR_NO_UNRECOVERABLE_NETWORK_FAILURES)) {
                ciPropsSource.put("warnings.suppressUnrecoverableNetworkFailures", "true");
            }
            ciPropsSource.putAll(otherOverrides);
            // merge properties from CI Properties Map with default ones
            ciPropsSource.putAll(explicitCiProps.getProps());
        }
        return ciPropsSource;
    }

    public static Def.Given defaultFailingHapiSpec(String name) {
        final Stream<Map<String, String>> prioritySource = Stream.of(
                runningInCi ? ciPropOverrides() : Collections.emptyMap(), Map.of("expected.final.status", "FAILED"));
        return customizedHapiSpec(false, name, prioritySource).withProperties();
    }

    public static Def.Sourced customHapiSpec(String name) {
        final Stream<Map<String, String>> prioritySource = runningInCi ? Stream.of(ciPropOverrides()) : Stream.empty();
        return customizedHapiSpec(false, name, prioritySource);
    }

    public static Def.Sourced customFailingHapiSpec(String name) {
        final Stream<Map<String, String>> prioritySource =
                runningInCi ? Stream.of(ciPropOverrides(), Map.of("expected.final.status", "FAILED")) : Stream.empty();
        return customizedHapiSpec(false, name, prioritySource);
    }

    private static <T> Def.Sourced customizedHapiSpec(
            final boolean isOnly, final String name, final Stream<T> prioritySource) {
        return customizedHapiSpec(isOnly, name, prioritySource, Collections.emptyList());
    }

    private static <T> Def.Sourced customizedHapiSpec(
            final boolean isOnly,
            final String name,
            final Stream<T> prioritySource,
            final List<String> propertiesToPreserve) {
        return (Object... sources) -> {
            Object[] allSources = Stream.of(
                            prioritySource, Stream.of(sources), Stream.of(HapiSpecSetup.getDefaultPropertySource()))
                    .flatMap(Function.identity())
                    .toArray();
            return (isOnly ? onlyHapiSpec(name, propertiesToPreserve) : hapiSpec(name, propertiesToPreserve))
                    .withSetup(setupFrom(allSources));
        };
    }

    private static HapiSpecSetup setupFrom(Object... objs) {
        return new HapiSpecSetup(inPriorityOrder(asSources(objs)));
    }

    public static Def.Setup hapiSpec(String name, List<String> propertiesToPreserve) {
        return setup ->
                given -> when -> then -> new HapiSpec(name, false, setup, given, when, then, propertiesToPreserve);
    }

    public static Def.Setup onlyHapiSpec(final String name, final List<String> propertiesToPreserve) {
        return setup ->
                given -> when -> then -> new HapiSpec(name, true, setup, given, when, then, propertiesToPreserve);
    }

    private HapiSpec(
            String name,
            boolean onlySpecToRunInSuite,
            HapiSpecSetup hapiSetup,
            HapiSpecOperation[] given,
            HapiSpecOperation[] when,
            HapiSpecOperation[] then,
            List<String> propertiesToPreserve) {
        status = PENDING;
        this.name = name;
        this.hapiSetup = hapiSetup;
        this.given = given;
        this.when = when;
        this.then = then;
        this.onlySpecToRunInSuite = onlySpecToRunInSuite;
        this.propertiesToPreserve = propertiesToPreserve;
        hapiClients = clientsFor(hapiSetup);
        try {
            hapiRegistry = new HapiSpecRegistry(hapiSetup);
            keyFactory = new KeyFactory(hapiSetup, hapiRegistry);
            txnFactory = new TxnFactory(hapiSetup, keyFactory);
            FeesAndRatesProvider scheduleProvider =
                    new FeesAndRatesProvider(txnFactory, keyFactory, hapiSetup, hapiClients, hapiRegistry);
            feeCalculator = new FeeCalculator(hapiSetup, scheduleProvider);
            this.ratesProvider = scheduleProvider;
        } catch (Throwable t) {
            log.error("Initialization failed for spec '{}'!", name, t);
            status = ERROR;
        }
    }

    interface Def {
        @FunctionalInterface
        interface Sourced {
            Given withProperties(Object... sources);
        }

        @FunctionalInterface
        interface PropertyPreserving {
            Given preserving(String... properties);
        }

        @FunctionalInterface
        interface Setup {
            Given withSetup(HapiSpecSetup setup);
        }

        @FunctionalInterface
        interface Given {
            When given(HapiSpecOperation... ops);
        }

        @FunctionalInterface
        interface When {
            Then when(HapiSpecOperation... ops);
        }

        @FunctionalInterface
        interface Then {
            HapiSpec then(HapiSpecOperation... ops);
        }
    }

    @Override
    public String toString() {
        final SpecStatus passingSpecStatus = ((status == PASSED) && notOk(this)) ? PASSED_UNEXPECTEDLY : status;
        final SpecStatus resolved = ((status == FAILED) && ok(this)) ? FAILED_AS_EXPECTED : passingSpecStatus;
        return MoreObjects.toStringHelper("Spec")
                .add("name", name)
                .add("status", resolved)
                .toString();
    }

    @SuppressWarnings("java:S2629")
    public synchronized void recordPayment(Payment payment) {
        log.info("{}+ cost snapshot :: {}", logPrefix(), payment);
        costs.add(payment);
    }

    private void compareWithSnapshot() {
        int nActual = costs.size();
        int nExpected = costSnapshot.size();
        boolean allMatch = (nActual == nExpected);
        if (!allMatch) {
            log.error("Expected {} payments to be recorded, not {}!", nExpected, nActual);
        }

        for (int i = 0; i < Math.min(nActual, nExpected); i++) {
            Payment actual = costs.get(i);
            Payment expected = costSnapshot.get(i);
            if (!actual.equals(expected)) {
                allMatch = false;
                log.error("Expected {} for payment {}, not {}!", expected, i, actual);
            }
        }

        if (!allMatch) {
            status = FAILED;
        }
    }

    private void takeCostSnapshot() {
        try {
            Properties deserializedCosts = new Properties();
            for (int i = 0; i < costs.size(); i++) {
                Payment cost = costs.get(i);
                deserializedCosts.put(String.format("%d.%s", i, cost.entryName()), "" + cost.tinyBars);
            }
            File file = new File(costSnapshotFilePath());
            CharSink sink = Files.asCharSink(file, StandardCharsets.UTF_8);
            try (final Writer writer = sink.openBufferedStream()) {
                deserializedCosts.store(writer, "Cost snapshot");
            }
        } catch (Exception e) {
            log.warn("Couldn't take cost snapshot to file '{}'!", costSnapshotFile(), e);
        }
    }

    private void loadCostSnapshot() {
        costSnapshot = costSnapshotFrom(costSnapshotFilePath());
    }

    public static List<Payment> costSnapshotFrom(String loc) {
        Properties serializedCosts = new Properties();
        final ByteSource source = Files.asByteSource(new File(loc));
        try (InputStream inStream = source.openBufferedStream()) {
            serializedCosts.load(inStream);
        } catch (IOException ie) {
            log.error("Couldn't load cost snapshots as requested!", ie);
            throw new IllegalArgumentException(ie);
        }
        Map<Integer, Payment> costsByOrder = new HashMap<>();
        serializedCosts.forEach((a, b) -> {
            String meta = (String) a;
            long amount = Long.parseLong((String) b);
            int i = meta.indexOf(".");
            costsByOrder.put(Integer.valueOf(meta.substring(0, i)), Payment.fromEntry(meta.substring(i + 1), amount));
        });
        return IntStream.range(0, costsByOrder.size())
                .mapToObj(costsByOrder::get)
                .toList();
    }

    private String costSnapshotFile() {
        return (suitePrefix.length() > 0)
                ? String.format("%s-%s-costs.properties", suitePrefix, name)
                : String.format("%s-costs.properties", name);
    }

    private String costSnapshotFilePath() {
        String dir = "cost-snapshots";
        ensureDir(dir);
        dir += ("/" + hapiSetup.costSnapshotDir());
        ensureDir(dir);
        return String.format("cost-snapshots/%s/%s", hapiSetup.costSnapshotDir(), costSnapshotFile());
    }

    /**
     * Add new properties that would merge with existing ones, if a property already exist then
     * override it with new value
     *
     * @param props A map of new properties
     */
    public void addOverrideProperties(final Map<String, Object> props) {
        hapiSetup.addOverrides(props);
    }

    public static void ensureDir(String path) {
        File f = new File(path);
        if (!f.exists()) {
            if (f.mkdirs()) {
                log.info("Created directory: {}", f.getAbsolutePath());
            } else {
                throw new IllegalStateException("Failed to create directory: " + f.getAbsolutePath());
            }
        }
    }

    private void nullOutInfrastructure() {
        txnFactory = null;
        keyFactory = null;
        entities = null;
        feeCalculator = null;
        ratesProvider = null;
        hapiClients = null;
        hapiRegistry = null;
    }
}
