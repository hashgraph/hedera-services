/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.services.bdd.junit.HapiTestNode;
import com.hedera.services.bdd.spec.utilops.lifecycle.LifecycleOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.selectors.NodeSelector;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Blocks waiting until the selected node or nodes are reconnected, or until a timeout of {@code waitSeconds} has happened.
 */
public class WaitForReconnectOp extends LifecycleOp {
    private static final Logger logger = LogManager.getLogger(WaitForReconnectOp.class);

    /** The number of seconds to wait for the node(s) to become active */
    private final int waitSeconds;

    public WaitForReconnectOp(@NonNull final NodeSelector selector, int waitSeconds) {
        super(selector);
        this.waitSeconds = waitSeconds;
    }

    @Override
    protected boolean run(@NonNull final HapiTestNode node) {
        logger.info("Waiting for node {} to finish reconnect, waiting up to {}s...", node, waitSeconds);
        try {
            node.waitForReconnectComplete(waitSeconds);
            logger.info("Node {} started and have reconnected successfully", node);
            return false; // Do not stop the test, all is well.
        } catch (TimeoutException e) {
            logger.info("Node {} did not reconnect within {}s", node, waitSeconds);
            return true; // Stop the test, we're toast.
        }
    }
}
