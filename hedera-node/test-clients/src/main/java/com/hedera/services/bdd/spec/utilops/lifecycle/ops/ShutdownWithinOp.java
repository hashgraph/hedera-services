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
import com.hedera.services.bdd.spec.utilops.lifecycle.AbstractLifecycleOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shuts down the selected node or nodes specified by the {@link NodeSelector}.
 */
public class ShutdownWithinOp extends AbstractLifecycleOp {
    private static final Logger log = LogManager.getLogger(ShutdownWithinOp.class);

    private final Duration timeout;

    public ShutdownWithinOp(@NonNull final NodeSelector selector, @NonNull final Duration timeout) {
        super(selector);
        this.timeout = requireNonNull(timeout);
    }

    @Override
    protected void run(@NonNull final HederaNode node) {
        log.info("Waiting for '{}' to stop", node.getName());
        try {
            node.stopFuture().orTimeout(timeout.toMillis(), MILLISECONDS).join();
        } catch (CompletionException e) {
            // If this returns false, the node process is not actually running any longer,
            // even though we failed to reap its exit status for some reason
            if (node.dumpThreads()) {
                throw new IllegalStateException("Failed to stop '" + node.getName() + "'", e);
            }
        }
        log.info("Stopped node '{}'", node.getName());
    }
}
