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

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

public abstract class AbstractNetwork implements HederaNetwork {
    protected final String networkName;
    protected final List<HederaNode> nodes;

    protected AbstractNetwork(@NonNull final String networkName, @NonNull final List<HederaNode> nodes) {
        this.networkName = requireNonNull(networkName);
        this.nodes = requireNonNull(nodes);
    }

    /**
     * {@inheritDoc}
     */
    public List<HederaNode> nodes() {
        return nodes;
    }

    /**
     * Returns the node of the network that matches the given selector.
     *
     * @param selector the selector
     * @return the nodes that match the selector
     */
    public HederaNode getRequiredNode(@NonNull final NodeSelector selector) {
        return nodes.stream().filter(selector).findAny().orElseThrow();
    }

    /**
     * Returns the name of the network.
     *
     * @return the name of the network
     */
    public String name() {
        return networkName;
    }
}
