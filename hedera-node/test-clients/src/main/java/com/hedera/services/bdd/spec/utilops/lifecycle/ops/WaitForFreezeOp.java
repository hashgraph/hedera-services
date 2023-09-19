/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
 * Blocks waiting until the selected node or nodes are frozen, or until a timeout of {@code waitSeconds} has
 * happened.
 */
public class WaitForFreezeOp extends LifecycleOp {
    private static final Logger logger = LogManager.getLogger(WaitForFreezeOp.class);

    /** The number of seconds to wait for the node(s) to freeze */
    private final int waitSeconds;

    public WaitForFreezeOp(@NonNull final NodeSelector selector, int waitSeconds) {
        super(selector);
        this.waitSeconds = waitSeconds;
    }

    @Override
    protected boolean run(@NonNull final HapiTestNode node) {
        logger.info("Waiting for node {} to freeze, waiting up to {}s...", node, waitSeconds);
        try {
            node.waitForFreeze(waitSeconds);
            logger.info("Node {} is frozen", node);
            return false; // Do not stop the test, all is well.
        } catch (TimeoutException e) {
            logger.info("Node {} did not freeze within {}s", node, waitSeconds);
            return true; // Stop the test, we're toast.
        }
    }
}
