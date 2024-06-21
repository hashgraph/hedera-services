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

import static com.hedera.services.bdd.junit.hedera.ExternalPath.APPLICATION_LOG;
import static com.hedera.services.bdd.junit.hedera.subprocess.NodeStatus.GrpcStatus.DOWN;
import static com.hedera.services.bdd.junit.hedera.subprocess.NodeStatus.GrpcStatus.NA;
import static com.hedera.services.bdd.junit.hedera.subprocess.NodeStatus.GrpcStatus.UP;
import static com.hedera.services.bdd.junit.hedera.subprocess.ProcessUtils.conditionFuture;
import static com.hedera.services.bdd.junit.hedera.subprocess.ProcessUtils.destroyAnySubProcessNodeWithId;
import static com.hedera.services.bdd.junit.hedera.subprocess.ProcessUtils.startSubProcessNodeFrom;
import static com.hedera.services.bdd.junit.hedera.subprocess.StatusLookupAttempt.newLogAttempt;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.recreateWorkingDir;
import static com.swirlds.platform.system.status.PlatformStatus.ACTIVE;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.Hedera;
import com.hedera.services.bdd.junit.hedera.AbstractLocalNode;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeMetadata;
import com.swirlds.base.function.BooleanFunction;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A node running in its own OS process as a subprocess of the JUnit test runner.
 */
public class SubProcessNode extends AbstractLocalNode<SubProcessNode> implements HederaNode {
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
    private static final int MAX_PROMETHEUS_RETRIES = 666;

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
        return startWithJar(null);
    }

    public SubProcessNode startWithJar(@Nullable final Path jarPath) {
        assertStopped();
        assertWorkingDirInitialized();
        destroyAnySubProcessNodeWithId(metadata.nodeId());
        if (jarPath == null) {
            processHandle = startSubProcessNodeFrom(metadata);
        } else {
            final var jarLoc = jarPath.normalize().toAbsolutePath().toString();
            processHandle = startSubProcessNodeFrom(metadata, "-jar", jarLoc);
        }
        return this;
    }

    @Override
    public boolean stop() {
        return stopWith(ProcessHandle::destroy);
    }

    @Override
    public boolean terminate() {
        return stopWith(ProcessHandle::destroyForcibly);
    }

    @Override
    public CompletableFuture<Void> statusFuture(
            @NonNull final PlatformStatus status, @Nullable Consumer<NodeStatus> nodeStatusObserver) {
        requireNonNull(status);
        final var retryCount = new AtomicInteger();
        return conditionFuture(
                () -> {
                    final var lookupAttempt = retryCount.get() <= MAX_PROMETHEUS_RETRIES
                            ? prometheusClient.statusFromLocalEndpoint(metadata.prometheusPort())
                            : statusFromLog();
                    var grpcStatus = NA;
                    var statusReached = lookupAttempt.status() == status;
                    if (statusReached && status == ACTIVE) {
                        grpcStatus = grpcPinger.isLive(metadata.grpcPort()) ? UP : DOWN;
                        statusReached = grpcStatus == UP;
                    }
                    if (nodeStatusObserver != null) {
                        nodeStatusObserver.accept(
                                new NodeStatus(lookupAttempt, grpcStatus, retryCount.getAndIncrement()));
                    }
                    return statusReached;
                },
                () -> retryCount.get() > MAX_PROMETHEUS_RETRIES ? LOG_SCAN_BACKOFF_MS : PROMETHEUS_BACKOFF_MS);
    }

    @Override
    public CompletableFuture<Void> stopFuture() {
        return conditionFuture(() -> processHandle == null);
    }

    @Override
    public String toString() {
        return "SubProcessNode{" + "metadata=" + metadata + ", workingDirInitialized=" + workingDirInitialized + '}';
    }

    @Override
    protected SubProcessNode self() {
        return this;
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
}
