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

package com.hedera.services.bdd.spec.utilops.lifecycle;

import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.CompletableFuture;

/**
 * A convenient base class for dealing with node lifecycle operations.
 */
public abstract class AbstractLifecycleOp extends UtilOp {
    /**
     * The {@link com.hedera.services.bdd.junit.hedera.NodeSelector} to use to choose which node(s) to operate on
     */
    private final NodeSelector selector;

    protected AbstractLifecycleOp(@NonNull final NodeSelector selector) {
        this.selector = requireNonNull(selector);
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        CompletableFuture.allOf(spec.targetNetworkOrThrow().nodesFor(selector).stream()
                        .map(node -> CompletableFuture.runAsync(() -> run(node)))
                        .toArray(CompletableFuture[]::new))
                .join();
        // This operation has nothing more to do after all nodes have finished their run() methods
        return false;
    }

    protected abstract void run(@NonNull final HederaNode node);
}
