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

package com.hedera.services.bdd.junit.hedera.live;

import static com.hedera.services.bdd.junit.hedera.live.ProcessUtils.destroyAnySubProcessNode;
import static com.hedera.services.bdd.junit.hedera.live.ProcessUtils.startSubProcessNodeFrom;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.hedera.services.bdd.junit.hedera.AbstractNode;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeMetadata;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A node running in its own OS process as a subprocess of the JUnit test runner.
 */
public class SubProcessNode extends AbstractNode implements HederaNode {
    private static final long WAIT_SLEEP_MILLIS = 10L;

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
        this.prometheusClient = requireNonNull(prometheusClient);
    }

    @Override
    public void start() {
        assertStopped();
        destroyAnySubProcessNode(metadata.nodeId());
        processHandle = startSubProcessNodeFrom(metadata);
    }

    @Override
    public void stop() {
        stopWith(ProcessHandle::destroy);
    }

    @Override
    public void terminate() {
        stopWith(ProcessHandle::destroyForcibly);
    }

    @Override
    public CompletableFuture<Void> waitForStatus(@NonNull final PlatformStatus status) {
        return waitFor(() -> {
            final var currentStatus = prometheusClient.statusFromLocalEndpoint(metadata.prometheusPort());
            if (!status.equals(currentStatus)) {
                return false;
            }
            return status != PlatformStatus.ACTIVE || grpcPinger.isLive(metadata.grpcPort());
        });
    }

    @Override
    public CompletableFuture<Void> waitForStopped() {
        return waitFor(() -> processHandle == null);
    }

    private CompletableFuture<Void> waitFor(@NonNull final BooleanSupplier condition) {
        return CompletableFuture.runAsync(() -> {
            while (!condition.getAsBoolean()) {
                try {
                    MILLISECONDS.sleep(WAIT_SLEEP_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for condition", e);
                }
            }
        });
    }

    private void stopWith(@NonNull final Consumer<ProcessHandle> stop) {
        assertStarted();
        stop.accept(processHandle);
        processHandle = null;
    }

    private void assertStopped() {
        if (processHandle != null) {
            throw new IllegalStateException("Node is still running");
        }
    }

    private void assertStarted() {
        if (processHandle == null) {
            throw new IllegalStateException("Node is not running");
        }
    }
}
