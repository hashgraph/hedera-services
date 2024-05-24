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

package com.hedera.services.bdd.spec.utilops.lifecycle.ops;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.junit.hedera.live.NodeStatus;
import com.hedera.services.bdd.spec.utilops.lifecycle.AbstractLifecycleOp;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

/**
 * Waits for the selected node or nodes specified by the {@link NodeSelector} to
 * reach the specified status within the given timeout.
 */
public class WaitForStatusOp extends AbstractLifecycleOp {
    private static final Logger log = LogManager.getLogger(WaitForStatusOp.class);

    private final Duration timeout;
    private final PlatformStatus status;

    public WaitForStatusOp(
            @NonNull NodeSelector selector, @NonNull final PlatformStatus status, @NonNull final Duration timeout) {
        super(selector);
        this.timeout = requireNonNull(timeout);
        this.status = requireNonNull(status);
    }

    @Override
    // assertion in production code
    @SuppressWarnings("java:S5960")
    public void run(@NonNull final HederaNode node) {
        final AtomicReference<NodeStatus> lastStatus = new AtomicReference<>();
        log.info("Waiting for node '{}' to be {} within {}", node.getName(), status, timeout);
        try {
            node.statusFuture(status, lastStatus::set).get(timeout.toMillis(), MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Assertions.fail("Node '" + node.getName() + "' did not reach status " + status + " within " + timeout
                    + "\n  Last status: " + lastStatus.get() + "\n  Cause: " + e.getMessage());
        }
        log.info("Node '{}' is {}", node.getName(), status);
    }

    @Override
    public String toString() {
        return "WaitFor" + status + "Within" + timeout;
    }
}
