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

import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.spec.utilops.lifecycle.AbstractLifecycleOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

/**
 * Shuts down the selected node or nodes specified by the {@link NodeSelector}.
 */
public class TryToStartNodesOp extends AbstractLifecycleOp {
    private static final Logger log = LogManager.getLogger(TryToStartNodesOp.class);

    public TryToStartNodesOp(@NonNull final NodeSelector selector) {
        super(selector);
    }

    @Override
    protected void run(@NonNull final HederaNode node) {
        log.info("Starting node '{}'", node.getName());
        try {
            node.start();
        } catch (Exception e) {
            log.error("Node '{}' failed to start", node);
            Assertions.fail("Node " + node + " failed to start");
        }
        log.info("Node '{}' has started", node.getName());
    }
}
