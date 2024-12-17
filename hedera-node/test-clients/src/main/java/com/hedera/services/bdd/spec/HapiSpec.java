/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_STATES_KEY;
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.NODES_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ACCOUNTS_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.TOKENS_KEY;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_MESSAGE_MAP_KEY;
import static com.hedera.services.bdd.junit.extensions.NetworkTargetingExtension.REPEATABLE_KEY_GENERATOR;
import static com.hedera.services.bdd.junit.extensions.NetworkTargetingExtension.SHARED_NETWORK;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.RECORD_STREAMS_DIR;
import static com.hedera.services.bdd.junit.support.StreamFileAccess.STREAM_FILE_ACCESS;
import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.ERROR;
import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.FAILED;
import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.FAILED_AS_EXPECTED;
import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.PASSED;
import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.PASSED_UNEXPECTEDLY;
import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.PENDING;
import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.RUNNING;
import static com.hedera.services.bdd.spec.HapiSpecSetup.setupFrom;
import static com.hedera.services.bdd.spec.infrastructure.HapiClients.clientsFor;
import static com.hedera.services.bdd.spec.keys.DefaultKeyGen.DEFAULT_KEY_GEN;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.doIfNotInterrupted;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.resourceAsString;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.turnLoggingOff;
import static com.hedera.services.bdd.spec.utilops.SysFileOverrideOp.Target.FEES;
import static com.hedera.services.bdd.spec.utilops.SysFileOverrideOp.Target.THROTTLES;
import static com.hedera.services.bdd.spec.utilops.UtilStateChange.createEthereumAccountForSpec;
import static com.hedera.services.bdd.spec.utilops.UtilStateChange.isEthereumAccountCreatedForSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.convertHapiCallsToEthereumCalls;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.remembering;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_CONTRACT_SENDER;
import static com.hedera.services.bdd.suites.HapiSuite.ETH_SUFFIX;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SOURCE_KEY;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.joining;

import com.google.common.base.MoreObjects;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.schedule.ScheduledCounts;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.extensions.NetworkTargetingExtension;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.junit.hedera.embedded.EmbeddedHedera;
import com.hedera.services.bdd.junit.hedera.embedded.EmbeddedNetwork;
import com.hedera.services.bdd.junit.hedera.embedded.RepeatableEmbeddedHedera;
import com.hedera.services.bdd.junit.hedera.remote.RemoteNetwork;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.fees.FeesAndRatesProvider;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.infrastructure.SpecStateObserver;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.KeyGenerator;
import com.hedera.services.bdd.spec.props.MapPropertySource;
import com.hedera.services.bdd.spec.transactions.TxnFactory;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.SysFileOverrideOp;
import com.hedera.services.bdd.spec.utilops.streams.assertions.AbstractEventualStreamAssertion;
import com.hedera.services.bdd.spec.verification.traceability.SidecarWatcher;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.function.Executable;

/**
 * A specification for a Hedera network test. A spec is a sequence of operations
 * that are executed in order, although in some cases their statuses will be resolved
 * asynchronously.
 *
 * <p>Most specs can be run against any {@link HederaNetwork} implementation, though
 * some operations do require an embedded or subprocess network.
 */
public class HapiSpec implements Runnable, Executable {
    private static final int CONCURRENT_EMBEDDED_STATUS_WAIT_SLEEP_MS = 1;
    private static final String CI_CHECK_NAME_SYSTEM_PROPERTY = "ci.check.name";
    private static final String QUIET_MODE_SYSTEM_PROPERTY = "hapi.spec.quiet.mode";
    private static final Duration NETWORK_ACTIVE_TIMEOUT = Duration.ofSeconds(300);
    /**
     * The name of the DynamicTest that executes the HapiSpec as written,
     * without modifications such as replacing ContractCall and ContractCreate
     * operations with equivalent EthereumTransactions
     */
    private static final String AS_WRITTEN_DISPLAY_NAME = "as written";

    public static final ThreadLocal<HederaNetwork> TARGET_NETWORK = new ThreadLocal<>();
    /**
     * If set, a list of properties to preserve in construction of this thread's next {@link HapiSpec} instance.
     * Typically the {@link NetworkTargetingExtension} will bind this value to the thread prior to executing a
     * test factory based on its {@link LeakyHapiTest#overrides()} attribute.
     */
    public static final ThreadLocal<List<String>> PROPERTIES_TO_PRESERVE = new ThreadLocal<>();
    /**
     * If set, a resource to load throttles from for this thread's next {@link HapiSpec} instance. Typically the
     * {@link NetworkTargetingExtension} will bind this value to the thread prior to executing a test factory based
     * on its {@link LeakyHapiTest#throttles()} attribute.
     */
    public static final ThreadLocal<String> THROTTLES_OVERRIDE = new ThreadLocal<>();
    /**
     * If set, a resource to load fee schedules from for this thread's next {@link HapiSpec} instance. Typically the
     * {@link NetworkTargetingExtension} will bind this value to the thread prior to executing a test factory based
     * on its {@link LeakyHapiTest#fees()} ()} attribute.
     */
    public static final ThreadLocal<String> FEES_OVERRIDE = new ThreadLocal<>();

    public static final ThreadLocal<TestLifecycle> TEST_LIFECYCLE = new ThreadLocal<>();
    public static final ThreadLocal<String> SPEC_NAME = new ThreadLocal<>();

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

    public enum UTF8Mode {
        FALSE,
        TRUE
    }

    public record Failure(Throwable cause, String opDescription) {
        private static final String LOG_TPL = "%s when executing %s";

        @Override
        public String toString() {
            return String.format(LOG_TPL, cause.getMessage(), opDescription);
        }
    }

    private final Map<String, Long> privateKeyToNonce = new HashMap<>();

    private final List<String> propertiesToPreserve;
    private final HapiSpecSetup hapiSetup;
    private final SpecOperation[] given;
    private final SpecOperation[] when;
    private final SpecOperation[] then;
    private final AtomicInteger adhoc = new AtomicInteger(0);
    private final AtomicBoolean allOpsSubmitted = new AtomicBoolean(false);
    private final AtomicReference<Optional<Failure>> finishingError = new AtomicReference<>(Optional.empty());
    private final BlockingQueue<HapiSpecOpFinisher> pendingOps = new PriorityBlockingQueue<>();
    private final EnumMap<ResponseCodeEnum, AtomicInteger> precheckStatusCounts = new EnumMap<>(ResponseCodeEnum.class);
    private final EnumMap<ResponseCodeEnum, AtomicInteger> finalizedStatusCounts =
            new EnumMap<>(ResponseCodeEnum.class);

    private String name;
    private String suitePrefix = "";
    private SpecStatus status;
    private TxnFactory txnFactory;
    private KeyFactory keyFactory;
    private KeyGenerator keyGenerator = DEFAULT_KEY_GEN;
    private FeeCalculator feeCalculator;
    private HapiSpecRegistry hapiRegistry;
    private FeesAndRatesProvider ratesProvider;
    private ThreadPoolExecutor finalizingExecutor;
    private CompletableFuture<Void> finalizingFuture;

    /**
     * If non-null, the non-remote network to target with this spec.
     */
    @Nullable
    private HederaNetwork targetNetwork;
    /**
     * If non-null, an observer to receive the final state of this spec's register and key factory
     * after it has executed.
     */
    @Nullable
    private SpecStateObserver specStateObserver;
    /**
     * If non-null, a list of shared states to include in this spec's initial state.
     */
    @Nullable
    private List<SpecStateObserver.SpecState> sharedStates;
    /**
     * If non-null, a spec-scoped sidecar watcher to use with sidecar assertions.
     */
    @Nullable
    private SidecarWatcher sidecarWatcher;
    /**
     * If non-null, a supplier to use within this spec's {@link TxnFactory}.
     */
    @Nullable
    private Supplier<Timestamp> nextValidStart;
    /**
     * If non-null, a resource to load override throttles from for this spec, restoring the previous
     * contents of the 0.0.123 system file after the spec completes.
     */
    @Nullable
    private String throttleResource;
    /**
     * If non-null, a resource to load override fees from for this spec, restoring the previous
     * contents of the 0.0.111 system file after the spec completes.
     */
    @Nullable
    private String feeResource;

    boolean quietMode;

    /**
     * When this spec's final status is {@code FAILED}, contains the information on the failed
     * assertion that terminated {@code exec()}.
     */
    @Nullable
    private Failure failure = null;

    /**
     * Add new properties that would merge with existing ones, if a property already exist then
     * override it with new value
     *
     * @param props A map of new properties
     */
    public void addOverrideProperties(@NonNull final Map<String, String> props) {
        hapiSetup.addOverrides(props);
    }

    /**
     * Returns the {@link KeyGenerator} used by this spec.
     *
     * <p><b>IMPORTANT:</b> Any operation that uses a different key generator cannot be run in
     * repeatable mode, as then this key generator must be
     *
     * @return the key generator
     */
    public KeyGenerator keyGenerator() {
        return keyGenerator;
    }

    /**
     * Sets the key generator to use for this spec.
     *
     * @param keyGenerator the key generator
     */
    public void setKeyGenerator(@NonNull final KeyGenerator keyGenerator) {
        this.keyGenerator = requireNonNull(keyGenerator);
    }

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

    public TargetNetworkType targetNetworkType() {
        return targetNetworkOrThrow().type();
    }

    public void setSpecStateObserver(@NonNull final SpecStateObserver specStateObserver) {
        this.specStateObserver = specStateObserver;
    }

    public void setSidecarWatcher(@NonNull final SidecarWatcher watcher) {
        this.sidecarWatcher = requireNonNull(watcher);
    }

    /**
     * Overrides the spec's default strategy for determining the next valid start time for transactions.
     *
     * @param nextValidStart the new strategy
     */
    public void setNextValidStart(@NonNull final Supplier<Timestamp> nextValidStart) {
        this.nextValidStart = requireNonNull(nextValidStart);
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

    public void setTargetNetwork(@NonNull final HederaNetwork targetNetwork) {
        this.targetNetwork = requireNonNull(targetNetwork);
    }

    public void setSharedStates(@NonNull final List<SpecStateObserver.SpecState> sharedStates) {
        this.sharedStates = sharedStates;
    }

    @Nullable
    public SidecarWatcher getSidecarWatcher() {
        return sidecarWatcher;
    }

    /**
     * Get the path to the record stream for the node selected by the given selector.
     *
     * @param selector the selector for the node
     * @return the path to the record stream
     * @throws RuntimeException if the spec has no target network or the node is not found
     */
    public @NonNull Path streamsLoc(@NonNull final NodeSelector selector) {
        requireNonNull(selector);
        return targetNetworkOrThrow().getRequiredNode(selector).getExternalPath(RECORD_STREAMS_DIR);
    }

    /**
     * Returns the network targeted by this spec.
     *
     * @return the target network
     */
    public @NonNull HederaNetwork targetNetworkOrThrow() {
        return requireNonNull(targetNetwork);
    }

    /**
     * Returns the best-effort representation of the properties in effect on startup of the target network.
     *
     * @return the startup properties
     */
    public @NonNull HapiPropertySource startupProperties() {
        return targetNetworkOrThrow().startupProperties();
    }

    /**
     * Returns the approximate consensus time of the network targeted by this spec.
     *
     * @return the approximate consensus time
     */
    public @NonNull Instant consensusTime() {
        if (targetNetworkOrThrow() instanceof EmbeddedNetwork embeddedNetwork) {
            return embeddedNetwork.embeddedHederaOrThrow().now();
        } else {
            return Instant.now();
        }
    }

    /**
     * Returns the {@link EmbeddedHedera} for a spec in embedded mode, or throws if the spec is not in embedded mode.
     *
     * @return the embedded Hedera
     * @throws IllegalStateException if the spec is not in embedded mode
     */
    public EmbeddedHedera embeddedHederaOrThrow() {
        return embeddedNetworkOrThrow().embeddedHederaOrThrow();
    }

    /**
     * Returns the {@link EmbeddedHedera} for a spec in embedded mode, or throws if the spec is not in embedded mode.
     *
     * @return the embedded Hedera
     * @throws IllegalStateException if the spec is not in embedded mode
     */
    public RepeatableEmbeddedHedera repeatableEmbeddedHederaOrThrow() {
        final var embeddedHedera = embeddedNetworkOrThrow().embeddedHederaOrThrow();
        if (embeddedHedera instanceof RepeatableEmbeddedHedera repeatableEmbeddedHedera) {
            return repeatableEmbeddedHedera;
        } else {
            throw new IllegalStateException(embeddedHedera.getClass().getSimpleName() + " is not repeatable");
        }
    }

    /**
     * Sleeps for the approximate wall clock time it will take for the spec's target
     * network to advance consensus time by the given duration.
     *
     * @param duration the duration to sleep for
     */
    public void sleepConsensusTime(@NonNull final Duration duration) {
        requireNonNull(duration);
        if (targetNetworkOrThrow() instanceof EmbeddedNetwork embeddedNetwork) {
            embeddedNetwork.embeddedHederaOrThrow().tick(duration);
        } else {
            doIfNotInterrupted(() -> Thread.sleep(duration.toMillis()));
        }
    }

    /**
     * Get the {@link FakeState} for the embedded network, if this spec is targeting an embedded network.
     *
     * @return the embedded state
     * @throws IllegalStateException if this spec is not targeting an embedded network
     */
    public @NonNull FakeState embeddedStateOrThrow() {
        if (!(targetNetworkOrThrow() instanceof EmbeddedNetwork network)) {
            throw new IllegalStateException("Cannot access embedded state for non-embedded network");
        }
        return network.embeddedHederaOrThrow().state();
    }

    /**
     * Get the {@link WritableKVState} for the embedded network's accounts, if this spec is targeting an embedded network.
     *
     * @return the embedded accounts state
     * @throws IllegalStateException if this spec is not targeting an embedded network
     */
    public @NonNull WritableKVState<com.hedera.hapi.node.base.AccountID, Account> embeddedAccountsOrThrow() {
        final var state = embeddedStateOrThrow();
        return state.getWritableStates(TokenService.NAME).get(ACCOUNTS_KEY);
    }

    /**
     * Get the {@link WritableKVState} for the embedded network's tokens, if this spec is targeting an embedded network.
     *
     * @return the embedded tokens state
     * @throws IllegalStateException if this spec is not targeting an embedded network
     */
    public @NonNull WritableKVState<com.hedera.hapi.node.base.TokenID, Token> embeddedTokensOrThrow() {
        final var state = embeddedStateOrThrow();
        return state.getWritableStates(TokenService.NAME).get(TOKENS_KEY);
    }

    /**
     * Get the {@link WritableKVState} for the embedded network's schedule counts, if this spec is
     * targeting an embedded network.
     *
     * @return the embedded schedule counts state
     * @throws IllegalStateException if this spec is not targeting an embedded network
     */
    public @NonNull WritableKVState<TimestampSeconds, ScheduledCounts> embeddedScheduleCountsOrThrow() {
        final var state = embeddedStateOrThrow();
        return state.getWritableStates(ScheduleService.NAME).get(V0570ScheduleSchema.SCHEDULED_COUNTS_KEY);
    }

    /**
     * Get the {@link WritableKVState} for the embedded network's tssMessages, if this spec is targeting an
     * embedded network.
     *
     * @return the embedded tss messages state
     * @throws IllegalStateException if this spec is not targeting an embedded network
     */
    public @NonNull WritableKVState<TssMessageMapKey, TssMessageTransactionBody> embeddedTssMsgStateOrThrow() {
        final var state = embeddedStateOrThrow();
        return state.getWritableStates(TssBaseService.NAME).get(TSS_MESSAGE_MAP_KEY);
    }

    /**
     * Get the {@link WritableKVState} for the embedded network's rosters, if this spec is targeting an
     * embedded network.
     *
     * @return the embedded roster state
     * @throws IllegalStateException if this spec is not targeting an embedded network
     */
    public @NonNull WritableSingletonState<RosterState> embeddedRosterStateOrThrow() {
        final var state = embeddedStateOrThrow();
        return state.getWritableStates(RosterService.NAME).getSingleton(ROSTER_STATES_KEY);
    }

    /**
     * Get the {@link WritableKVState} for the embedded network's nodes, if this spec is targeting an embedded network.
     *
     * @return the embedded nodes state
     * @throws IllegalStateException if this spec is not targeting an embedded network
     */
    public @NonNull WritableKVState<EntityNumber, Node> embeddedNodesOrThrow() {
        final var state = embeddedStateOrThrow();
        return state.getWritableStates(com.hedera.node.app.service.addressbook.AddressBookService.NAME)
                .get(NODES_KEY);
    }

    /**
     * Commits all pending changes to the embedded {@link FakeState} if this spec is targeting
     * an embedded network.
     */
    public void commitEmbeddedState() {
        embeddedStateOrThrow().commit();
    }

    public List<HederaNode> getNetworkNodes() {
        return requireNonNull(targetNetwork).nodes();
    }

    public static boolean ok(HapiSpec spec) {
        return spec.getStatus() == spec.getExpectedFinalStatus();
    }

    public static boolean notOk(HapiSpec spec) {
        return !ok(spec);
    }

    @Override
    public void execute() throws Throwable {
        // Only JUnit will use execute(), and in that case the target network must be set
        requireNonNull(targetNetwork).awaitReady(NETWORK_ACTIVE_TIMEOUT);
        run();
        if (failure != null) {
            throw failure.cause;
        }
    }

    @Override
    public void run() {
        if (!init()) {
            return;
        }

        List<SpecOperation> ops = new ArrayList<>();

        if (!suitePrefix.endsWith(ETH_SUFFIX)) {
            ops.addAll(Stream.of(given, when, then).flatMap(Arrays::stream).toList());
        } else {
            if (!isEthereumAccountCreatedForSpec(this)) {
                ops.addAll(createEthereumAccountForSpec(this));
            }
            final var adminKey = this.registry().getKey(DEFAULT_CONTRACT_SENDER);
            ops.addAll(convertHapiCallsToEthereumCalls(
                    Stream.of(given, when, then).flatMap(Arrays::stream).toList(),
                    SECP_256K1_SOURCE_KEY,
                    adminKey,
                    hapiSetup.defaultCreateGas(),
                    this));
        }

        try {
            exec(ops);
        } catch (Throwable t) {
            log.error("Uncaught exception in HapiSpec::exec", t);
            status = FAILED;
            failure = new Failure(t, "Unhandled exception executing '" + name + "' - " + t.getMessage());
            tearDown();
        }

        if (specStateObserver != null) {
            specStateObserver.observe(new SpecStateObserver.SpecState(hapiRegistry, keyFactory));
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
                    failure = new Failure(t, "Fees initialization");
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
                // These are unrecoverable; save a lot of time and just fail the test.
                failure = new Failure(e, "Irrecoverable error in test nodes or client JVM. Unable to continue.");
                return false;
            }
        }
        failure = new Failure(new IllegalStateException("Timed out fetching fee schedules"), "Fees initialization");
        return false;
    }

    private boolean init() {
        if (targetNetwork == null) {
            targetNetwork = RemoteNetwork.newRemoteNetwork(hapiSetup.nodes(), clientsFor(hapiSetup));
        }
        if (!propertiesToPreserve.isEmpty()) {
            final var missingProperties = propertiesToPreserve.stream()
                    .filter(p -> !startupProperties().has(p))
                    .collect(joining(", "));
            if (!missingProperties.isEmpty()) {
                status = ERROR;
                failure = new Failure(
                        new IllegalStateException("Cannot preserve missing properties: '" + missingProperties + "'"),
                        "Initialization");
                return false;
            }
        }
        try {
            hapiRegistry = new HapiSpecRegistry(hapiSetup);
            keyFactory = new KeyFactory(hapiSetup, hapiRegistry);
            if (sharedStates != null) {
                sharedStates.forEach(sharedState -> {
                    hapiRegistry.include(sharedState.registry());
                    keyFactory.include(sharedState.keyFactory());
                });
            }
            txnFactory =
                    (nextValidStart == null) ? new TxnFactory(hapiSetup) : new TxnFactory(hapiSetup, nextValidStart);
            FeesAndRatesProvider scheduleProvider =
                    new FeesAndRatesProvider(txnFactory, keyFactory, hapiSetup, hapiRegistry, targetNetwork);
            feeCalculator = new FeeCalculator(hapiSetup, scheduleProvider);
            this.ratesProvider = scheduleProvider;
        } catch (Throwable t) {
            log.error("Initialization failed for spec '{}'!", name, t);
            status = ERROR;
            failure = new Failure(t, "Initialization");
            return false;
        }
        if (!tryReinitializingFees()) {
            status = ERROR;
            return false;
        }
        return true;
    }

    private void tearDown() {
        if (finalizingExecutor != null) {
            finalizingExecutor.shutdown();
        }
        if (sidecarWatcher != null) {
            sidecarWatcher.ensureUnsubscribed();
        }
        // Also terminate any embedded network not being shared by multiple specs
        if (targetNetwork instanceof EmbeddedNetwork embeddedNetwork && embeddedNetwork != SHARED_NETWORK.get()) {
            embeddedNetwork.terminate();
        }
    }

    @SuppressWarnings("java:S2629")
    private void exec(@NonNull List<SpecOperation> ops) {
        if (status == ERROR) {
            log.warn("'{}' failed to initialize, being skipped!", name);
            return;
        }
        if (!quietMode) {
            log.info("{} test suite started !", logPrefix());
        }
        status = RUNNING;
        if (hapiSetup.statusDeferredResolvesDoAsync()) {
            startFinalizingOps();
        }
        final var shouldPreserveProps = !propertiesToPreserve.isEmpty();
        final Map<String, String> preservedProperties = shouldPreserveProps ? new HashMap<>() : Collections.emptyMap();
        if (shouldPreserveProps) {
            ops.addFirst(remembering(preservedProperties, propertiesToPreserve));
        }
        if (throttleResource != null) {
            ops.addFirst(new SysFileOverrideOp(
                    THROTTLES, () -> throttleResource.isBlank() ? null : resourceAsString(throttleResource)));
        }
        if (feeResource != null) {
            ops.addFirst(
                    new SysFileOverrideOp(FEES, () -> feeResource.isBlank() ? null : resourceAsString(feeResource)));
        }
        @Nullable List<AbstractEventualStreamAssertion> streamAssertions = null;
        for (var op : ops) {
            if (op instanceof AbstractEventualStreamAssertion streamAssertion) {
                if (streamAssertions == null) {
                    streamAssertions = new ArrayList<>();
                }
                streamAssertions.add(streamAssertion);
            }
            if (quietMode) {
                turnLoggingOff(op);
            }
            final var error = op.execFor(this);
            Failure asyncFailure = null;
            if (error.isPresent() || (asyncFailure = finishingError.get().orElse(null)) != null) {
                status = FAILED;
                final var failedOp = op;
                failure = error.map(t -> new Failure(t, failedOp.toString())).orElse(asyncFailure);
                break;
            } else {
                if (!quietMode) {
                    log.info("'{}' finished initial execution of {}", name, op);
                }
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
        for (final var op : ops) {
            if (op instanceof SysFileOverrideOp sysFileOverrideOp && sysFileOverrideOp.isAutoRestoring()) {
                sysFileOverrideOp.restoreContentsIfNeeded(this);
            }
        }
        if (status == PASSED) {
            final var maybeStreamFileError = checkStream(streamAssertions);
            if (maybeStreamFileError.isPresent()) {
                status = FAILED;
                failure = maybeStreamFileError.get();
            }
            if (sidecarWatcher != null) {
                try {
                    sidecarWatcher.assertExpectations(this);
                } catch (Throwable t) {
                    log.error("Sidecar assertion failed", t);
                    status = FAILED;
                    failure = new Failure(t, "Sidecar assertion");
                }
            }
        } else if (streamAssertions != null) {
            streamAssertions.forEach(AbstractEventualStreamAssertion::unsubscribe);
            STREAM_FILE_ACCESS.stopMonitorIfNoSubscribers();
        }

        tearDown();
        if (!quietMode) {
            log.info("{}final status: {}!", logPrefix(), status);
        }
    }

    private Optional<Failure> checkStream(@Nullable final List<AbstractEventualStreamAssertion> streamAssertions) {
        if (streamAssertions == null) {
            return Optional.empty();
        }
        if (!quietMode) {
            log.info("Checking stream files for {} assertions", streamAssertions.size());
        }
        final var needsTraffic =
                streamAssertions.stream().anyMatch(AbstractEventualStreamAssertion::needsBackgroundTraffic);
        Optional<Failure> answer = Optional.empty();
        // Keep submitting transactions to close stream files (in almost every case, just
        // one file will need to be closed, since it's very rare to have a long-running spec)
        final Future<?> backgroundTraffic = needsTraffic
                ? THREAD_POOL.submit(() -> {
                    while (true) {
                        try {
                            TxnUtils.triggerAndCloseAtLeastOneFile(this);
                            if (!quietMode) {
                                log.info("Closed at least one record file via background traffic");
                            }
                        } catch (final InterruptedException ignore) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                })
                : null;
        for (final var assertion : streamAssertions) {
            if (!quietMode) {
                log.info("Checking record stream for {}", assertion);
            }
            try {
                // Each blocks until the assertion passes or times out
                assertion.assertHasPassed();
            } catch (final Throwable t) {
                log.warn("{}{} failed", logPrefix(), assertion, t);
                answer = Optional.of(new Failure(t, assertion.toString()));
                break;
            }
        }

        if (backgroundTraffic != null) {
            backgroundTraffic.cancel(true);
        }
        STREAM_FILE_ACCESS.stopMonitorIfNoSubscribers();
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
        if (!quietMode) {
            log.info("{}now finalizing {} more pending ops...", logPrefix(), pendingOps.size());
        }
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

    public HapiSpecRegistry registry() {
        return hapiRegistry;
    }

    public HapiSpecSetup setup() {
        return hapiSetup;
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
        return internalDefaultHapiSpec(name, emptyList());
    }

    private static Def.Given internalDefaultHapiSpec(final String name, final List<String> propertiesToPreserve) {
        final Stream<Map<String, String>> prioritySource = runningInCi ? Stream.of(ciPropOverrides()) : Stream.empty();
        return customizedHapiSpec(name, prioritySource, propertiesToPreserve).withProperties();
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

    public static Def.Sourced customHapiSpec(String name) {
        final Stream<Map<String, String>> prioritySource = runningInCi ? Stream.of(ciPropOverrides()) : Stream.empty();
        return customizedHapiSpec(name, prioritySource);
    }

    private static <T> Def.Sourced customizedHapiSpec(final String name, final Stream<T> prioritySource) {
        return customizedHapiSpec(name, prioritySource, emptyList());
    }

    private static <T> Def.Sourced customizedHapiSpec(
            final String name, final Stream<T> prioritySource, final List<String> propertiesToPreserve) {
        return (Object... sources) -> {
            Object[] allSources = Stream.of(
                            prioritySource, Stream.of(sources), Stream.of(HapiSpecSetup.getDefaultPropertySource()))
                    .flatMap(Function.identity())
                    .toArray();
            return hapiSpec(name, propertiesToPreserve).withSetup(HapiSpecSetup.setupFrom(allSources));
        };
    }

    public static Def.Setup hapiSpec(String name, List<String> propertiesToPreserve) {
        return setup -> given -> when -> then -> Stream.of(DynamicTest.dynamicTest(
                name + " " + AS_WRITTEN_DISPLAY_NAME,
                targeted(new HapiSpec(name, setup, given, when, then, propertiesToPreserve))));
    }

    /**
     * Creates dynamic tests derived from with the given operations, preserving any properties bound to the thread
     * by a {@link LeakyHapiTest} test factory.
     *
     * @param ops the operations
     * @return a {@link Stream} of {@link DynamicTest}s
     */
    public static Stream<DynamicTest> hapiTest(@NonNull final SpecOperation... ops) {
        return propertyPreservingHapiTest(
                Optional.ofNullable(PROPERTIES_TO_PRESERVE.get()).orElse(emptyList()), ops);
    }

    /**
     * Creates dynamic tests derived from with the given operations, ensuring the listed properties are
     * restored to their original values after running the tests.
     *
     * @param propertiesToPreserve the properties to preserve
     * @param ops                  the operations
     * @return a {@link Stream} of {@link DynamicTest}s
     */
    public static Stream<DynamicTest> propertyPreservingHapiTest(
            @NonNull final List<String> propertiesToPreserve, @NonNull final SpecOperation... ops) {
        requireNonNull(propertiesToPreserve);
        return Stream.of(DynamicTest.dynamicTest(
                AS_WRITTEN_DISPLAY_NAME,
                targeted(new HapiSpec(
                        SPEC_NAME.get(),
                        HapiSpecSetup.setupFrom(HapiSpecSetup.getDefaultPropertySource()),
                        new SpecOperation[0],
                        new SpecOperation[0],
                        ops,
                        propertiesToPreserve))));
    }

    public static DynamicTest namedHapiTest(String name, @NonNull final SpecOperation... ops) {
        return DynamicTest.dynamicTest(
                name,
                targeted(new HapiSpec(
                        name,
                        HapiSpecSetup.setupFrom(HapiSpecSetup.getDefaultPropertySource()),
                        new SpecOperation[0],
                        new SpecOperation[0],
                        ops,
                        List.of())));
    }

    private static HapiSpec targeted(@NonNull final HapiSpec spec) {
        final var targetNetwork = TARGET_NETWORK.get();
        if (targetNetwork != null) {
            log.info("Targeting network '{}' for spec '{}'", targetNetwork.name(), spec.name);
            doTargetSpec(spec, targetNetwork);
        }
        Optional.ofNullable(TEST_LIFECYCLE.get())
                .map(TestLifecycle::getSharedStates)
                .ifPresent(spec::setSharedStates);
        spec.throttleResource = THROTTLES_OVERRIDE.get();
        spec.feeResource = FEES_OVERRIDE.get();
        return spec;
    }

    /**
     * Customizes the {@link HapiSpec} to target the given network.
     *
     * @param spec          the {@link HapiSpec} to customize
     * @param targetNetwork the target network
     */
    public static void doTargetSpec(@NonNull final HapiSpec spec, @NonNull final HederaNetwork targetNetwork) {
        spec.setTargetNetwork(targetNetwork);

        // (FUTURE) Remove this override by initializing the HapiClients for a remote network
        // directly from the network's HederaNode instances instead of this "nodes" property
        final var specNodes =
                targetNetwork.nodes().stream().map(HederaNode::hapiSpecInfo).collect(joining(","));
        spec.addOverrideProperties(Map.of("nodes", specNodes));

        if (targetNetwork instanceof EmbeddedNetwork embeddedNetwork) {
            final Map<String, String> overrides;
            if (embeddedNetwork.inRepeatableMode()) {
                // Statuses are immediately available in repeatable mode because ingest is synchronous;
                // ECDSA signatures are inherently random, so use only ED25519 in repeatable mode
                overrides = Map.of("status.wait.sleep.ms", "0", "default.keyAlgorithm", "ED25519");
            } else {
                overrides = Map.of("status.wait.sleep.ms", "" + CONCURRENT_EMBEDDED_STATUS_WAIT_SLEEP_MS);
            }
            spec.addOverrideProperties(overrides);
            final var embeddedHedera = embeddedNetwork.embeddedHederaOrThrow();
            spec.setNextValidStart(embeddedHedera::nextValidStart);
            if (embeddedNetwork.inRepeatableMode()) {
                spec.setKeyGenerator(requireNonNull(REPEATABLE_KEY_GENERATOR.get()));
            }
        }
    }

    public HapiSpec(String name, SpecOperation[] ops) {
        this(
                name,
                setupFrom(HapiSpecSetup.getDefaultPropertySource()),
                new SpecOperation[0],
                new SpecOperation[0],
                ops,
                List.of());
    }

    // too many parameters
    @SuppressWarnings("java:S107")
    public HapiSpec(
            String name,
            HapiSpecSetup hapiSetup,
            SpecOperation[] given,
            SpecOperation[] when,
            SpecOperation[] then,
            List<String> propertiesToPreserve) {
        status = PENDING;
        this.name = name;
        this.hapiSetup = hapiSetup;
        this.given = given;
        this.when = when;
        this.then = then;
        this.propertiesToPreserve = propertiesToPreserve;
        final var quiet = System.getProperty(QUIET_MODE_SYSTEM_PROPERTY);
        final var isCiCheck = System.getProperty(CI_CHECK_NAME_SYSTEM_PROPERTY) != null;
        this.quietMode = "true".equalsIgnoreCase(quiet) || (!"false".equalsIgnoreCase(quiet) && isCiCheck);
    }

    public interface Def {
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
            When given(SpecOperation... ops);
        }

        @FunctionalInterface
        interface When {
            Then when(SpecOperation... ops);
        }

        @FunctionalInterface
        interface Then {
            Stream<DynamicTest> then(SpecOperation... ops);
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

    private void nullOutInfrastructure() {
        txnFactory = null;
        keyFactory = null;
        feeCalculator = null;
        ratesProvider = null;
        hapiRegistry = null;
    }

    private EmbeddedNetwork embeddedNetworkOrThrow() {
        if (!(targetNetworkOrThrow() instanceof EmbeddedNetwork network)) {
            throw new IllegalStateException("Target network is not embedded");
        }
        return network;
    }
}
