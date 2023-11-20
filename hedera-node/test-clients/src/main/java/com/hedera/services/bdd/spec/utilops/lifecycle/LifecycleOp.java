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

package com.hedera.services.bdd.spec.utilops.lifecycle;

import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.junit.HapiTestNode;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hedera.services.bdd.spec.utilops.lifecycle.selectors.NodeSelector;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A convenient base class for dealing with node lifecycle operations.
 */
public abstract class LifecycleOp extends UtilOp {
    static final Logger log = LogManager.getLogger(LifecycleOp.class);

    /** The {@link NodeSelector} to use to choose which node(s) to operate on */
    private final NodeSelector selector;

    protected LifecycleOp(@NonNull final NodeSelector selector) {
        this.selector = requireNonNull(selector);
    }

    @Override
    protected final boolean submitOp(HapiSpec spec) throws Throwable {
        final var nodes = spec.getNodes().stream().filter(selector).collect(Collectors.toList());

        if (nodes.isEmpty()) {
            log.warn("Unable to find any node with criteria {}", selector);
            return false;
        }

        // Run the op for each node, and if ANY node returns "true", then the test is over and we should also
        // return true. If they all return false, then we also return false.
        return nodes.stream().anyMatch(this::run);
    }

    /**
     * Executes the operation on the given node.
     * @param node The node to operate on.
     * @return true if the test is over and should be terminated, false if the test should continue.
     */
    protected abstract boolean run(@NonNull final HapiTestNode node);
}
