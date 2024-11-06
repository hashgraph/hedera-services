/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.hedera.subprocess;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.toPbj;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.APPLICATION_LOG;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.SWIRLDS_LOG;
import static com.hedera.services.bdd.junit.hedera.subprocess.ConditionStatus.PENDING;
import static com.hedera.services.bdd.junit.hedera.subprocess.ConditionStatus.REACHED;
import static com.hedera.services.bdd.junit.hedera.subprocess.NodeStatus.BindExceptionSeen.NO;
import static com.hedera.services.bdd.junit.hedera.subprocess.NodeStatus.BindExceptionSeen.YES;
import static com.hedera.services.bdd.junit.hedera.subprocess.NodeStatus.GrpcStatus.DOWN;
import static com.hedera.services.bdd.junit.hedera.subprocess.NodeStatus.GrpcStatus.NA;
import static com.hedera.services.bdd.junit.hedera.subprocess.NodeStatus.GrpcStatus.UP;
import static com.hedera.services.bdd.junit.hedera.subprocess.ProcessUtils.conditionFuture;
import static com.hedera.services.bdd.junit.hedera.subprocess.ProcessUtils.destroyAnySubProcessNodeWithId;
import static com.hedera.services.bdd.junit.hedera.subprocess.ProcessUtils.startSubProcessNodeFrom;
import static com.hedera.services.bdd.junit.hedera.subprocess.StatusLookupAttempt.newLogAttempt;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.recreateWorkingDir;
import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.swirlds.platform.system.status.PlatformStatus.ACTIVE;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.Hedera;
import com.hedera.services.bdd.junit.hedera.AbstractLocalNode;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeMetadata;
import com.hedera.services.bdd.junit.hedera.subprocess.NodeStatus.BindExceptionSeen;
import com.hedera.services.bdd.suites.regression.system.LifecycleTest;
import com.swirlds.base.function.BooleanFunction;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A node running in its own OS process as a subprocess of the JUnit test runner.
 */
public class SubProcessNode extends AbstractLocalNode<SubProcessNode> implements HederaNode {
    private static final Logger log = LogManager.getLogger(SubProcessNode.class);

    /**
     * How many milliseconds to wait between retries when scanning the application log for
     * the node status.
     */
    private static final long LOG_SCAN_BACKOFF_MS = 1000L;
    /**
     * How many milliseconds to wait between retrying a Prometheus status lookup.
     */
    private static final long PROMETHEUS_BACKOFF_MS = 100L;
    /**
     * The maximum number of retries to make when looking up the status of a node via Prometheus
     * before resorting to scanning the application log. (Empirically, if Prometheus is not up
     * within a minute or so, it's not going to be; and we should fall back to log scanning.)
     */
    private static final int MAX_PROMETHEUS_RETRIES = 1000;
    /**
     * How many retries to make between checking if a bind exception has been thrown in the logs.
     */
    private static final int BINDING_CHECK_INTERVAL = 10;

    private final Pattern statusPattern;
    private final GrpcPinger grpcPinger;
    private final PrometheusClient prometheusClient;
    /**
     * If this node is running, the {@link ProcessHandle} of the node's process; null otherwise.
     */
    @Nullable
    private ProcessHandle processHandle;

    public SubProcessNode(
            @NonNull final NodeMetadata metadata,
            @NonNull final GrpcPinger grpcPinger,
            @NonNull final PrometheusClient prometheusClient) {
        super(metadata);
        this.grpcPinger = requireNonNull(grpcPinger);
        this.statusPattern = Pattern.compile(".*HederaNode#" + getNodeId() + " is (\\w+)");
        this.prometheusClient = requireNonNull(prometheusClient);
        // Just something to keep checkModuleInfo from claiming we don't require com.hedera.node.app
        requireNonNull(Hedera.class);
    }

    @Override
    public SubProcessNode initWorkingDir(@NonNull final String configTxt) {
        recreateWorkingDir(requireNonNull(metadata.workingDir()), configTxt);
        workingDirInitialized = true;
        return this;
    }

    @Override
    public SubProcessNode start() {
        return startWithConfigVersion(LifecycleTest.CURRENT_CONFIG_VERSION.get());
    }

    @Override
    public CompletableFuture<Void> statusFuture(
            @NonNull final PlatformStatus status, @Nullable Consumer<NodeStatus> nodeStatusObserver) {
        requireNonNull(status);
        final var retryCount = new AtomicInteger();
        return conditionFuture(
                () -> {
                    final var nominalSoFar = retryCount.get() <= MAX_PROMETHEUS_RETRIES;
                    final var lookupAttempt = nominalSoFar
                            ? prometheusClient.statusFromLocalEndpoint(metadata.prometheusPort())
                            : statusFromLog();
                    var grpcStatus = NA;
                    var statusReached = lookupAttempt.status() == status;
                    if (statusReached && status == ACTIVE) {
                        grpcStatus = grpcPinger.isLive(metadata.grpcPort()) ? UP : DOWN;
                        statusReached = grpcStatus == UP;
                    }
                    var bindExceptionSeen = BindExceptionSeen.NA;
                    // This extra logic just barely justifies its existence by giving up
                    // immediately when a bind exception is seen in the logs, since in
                    // practice these are never transient; it also lets us try reassigning
                    // ports when first starting the network to maybe salvage the run
                    if (!statusReached
                            && status == ACTIVE
                            && !nominalSoFar
                            && retryCount.get() % BINDING_CHECK_INTERVAL == 0) {
                        if (swirldsLogContains("java.net.BindException")) {
                            bindExceptionSeen = YES;
                        } else {
                            bindExceptionSeen = NO;
                        }
                    }
                    if (nodeStatusObserver != null) {
                        nodeStatusObserver.accept(new NodeStatus(
                                lookupAttempt, grpcStatus, bindExceptionSeen, retryCount.getAndIncrement()));
                    }
                    return statusReached ? REACHED : PENDING;
                },
                () -> retryCount.get() > MAX_PROMETHEUS_RETRIES ? LOG_SCAN_BACKOFF_MS : PROMETHEUS_BACKOFF_MS);
    }

    @Override
    public CompletableFuture<Void> stopFuture() {
        if (processHandle == null) {
            return CompletableFuture.completedFuture(null);
        }
        log.info(
                "Destroying node '{}' with PID '{}' (Alive? {})",
                metadata.nodeId(),
                processHandle.pid(),
                processHandle.isAlive() ? "Yes" : "No");
        if (!processHandle.destroy()) {
            log.warn("May have failed to stop node '{}' with PID '{}'", metadata.nodeId(), processHandle.pid());
        }
        return processHandle.onExit().thenAccept(handle -> {
            log.info("Destroyed PID {}", handle.pid());
            this.processHandle = null;
        });
    }

    @Override
    public String toString() {
        return "SubProcessNode{" + "metadata=" + metadata + ", workingDirInitialized=" + workingDirInitialized + '}';
    }

    @Override
    protected SubProcessNode self() {
        return this;
    }

    public SubProcessNode startWithConfigVersion(final int configVersion) {
        assertStopped();
        assertWorkingDirInitialized();
        destroyAnySubProcessNodeWithId(metadata.nodeId());
        processHandle = startSubProcessNodeFrom(metadata, configVersion);
        return this;
    }

    /**
     * Reassigns the ports used by this node.
     *
     * @param grpcPort the new gRPC port
     *                 @param grpcNodeOperatorPort the new gRPC node operator port
     * @param gossipPort the new gossip port
     * @param tlsGossipPort the new TLS gossip port
     * @param prometheusPort the new Prometheus port
     */
    public void reassignPorts(
            final int grpcPort,
            final int grpcNodeOperatorPort,
            final int gossipPort,
            final int tlsGossipPort,
            final int prometheusPort) {
        metadata = metadata.withNewPorts(grpcPort, grpcNodeOperatorPort, gossipPort, tlsGossipPort, prometheusPort);
    }

    /**
     * Reassigns the account ID used by this node.
     * @param memo the memo containing the new account ID to use
     */
    public void reassignNodeAccountIdFrom(@NonNull final String memo) {
        metadata = metadata.withNewAccountId(toPbj(asAccount(memo)));
    }

    /**
     * Reassigns node operator port to be disabled for this node.
     */
    public void reassignWithNodeOperatorPortDisabled() {
        metadata = metadata.withNewNodeOperatorPortDisabled();
    }

    private boolean swirldsLogContains(@NonNull final String text) {
        try (var lines = Files.lines(getExternalPath(SWIRLDS_LOG))) {
            return lines.anyMatch(line -> line.contains(text));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean stopWith(@NonNull final BooleanFunction<ProcessHandle> stop) {
        if (processHandle == null) {
            return false;
        }
        final var result = stop.apply(processHandle);
        processHandle = null;
        return result;
    }

    private void assertStopped() {
        if (processHandle != null) {
            throw new IllegalStateException("Node is still running");
        }
    }

    private StatusLookupAttempt statusFromLog() {
        final AtomicReference<String> status = new AtomicReference<>();
        try (final var lines = Files.lines(getExternalPath(APPLICATION_LOG))) {
            lines.map(statusPattern::matcher).filter(Matcher::matches).forEach(matcher -> status.set(matcher.group(1)));
            return newLogAttempt(status.get(), status.get() == null ? "No status line in log" : null);
        } catch (IOException e) {
            return newLogAttempt(null, e.getMessage());
        }
    }

    public enum ReassignPorts {
        YES,
        NO
    }
}
