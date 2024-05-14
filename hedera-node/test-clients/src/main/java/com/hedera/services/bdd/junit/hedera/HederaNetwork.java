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

package com.hedera.services.bdd.junit.hedera;

import static com.hedera.services.bdd.junit.hedera.live.WorkingDirUtils.configTxtFor;
import static com.swirlds.platform.system.status.PlatformStatus.ACTIVE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * A network of Hedera nodes. For now, assumed to be accessed via
 * a live gRPC connection. In the future, we will abstract the
 * submission and querying operations to allow for an embedded
 * "network".
 */
public class HederaNetwork {
    private final String configTxt;
    private final String networkName;
    private final List<HederaNode> nodes;

    public HederaNetwork(@NonNull final String networkName, @NonNull final List<HederaNode> nodes) {
        this.nodes = requireNonNull(nodes);
        this.networkName = requireNonNull(networkName);
        this.configTxt = configTxtFor(networkName, nodes);
    }

    /**
     * Starts all nodes in the network and waits for them to reach the
     * {@link PlatformStatus#ACTIVE} status, or times out.
     *
     * <p>Returns a latch that will count down when all nodes are active.
     *
     * @param timeout the maximum time to wait for all nodes to start
     * @return a latch that will count down when all nodes are active
     */
    public CountDownLatch start(@NonNull final Duration timeout) {
        final var latch = new CountDownLatch(1);
        CompletableFuture.allOf(nodes.stream()
                        .map(node -> {
                            node.start();
                            return node.waitForStatus(ACTIVE);
                        })
                        .toArray(CompletableFuture[]::new))
                .orTimeout(timeout.toMillis(), MILLISECONDS)
                .thenRun(latch::countDown);
        return latch;
    }
}
