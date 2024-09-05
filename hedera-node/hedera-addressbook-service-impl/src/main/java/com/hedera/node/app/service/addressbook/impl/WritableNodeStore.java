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

package com.hedera.node.app.service.addressbook.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.metrics.StoreMetricsService.StoreType;
import com.hedera.node.config.data.NodesConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Provides write methods for modifying underlying data storage mechanisms for
 * working with Nodes.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 * This class is not complete, it will be extended with other methods like remove, update etc.,
 */
public class WritableNodeStore extends ReadableNodeStoreImpl {
    /**
     * Create a new {@link WritableNodeStore} instance.
     *
     * @param states The state to use.
     * @param configuration The configuration used to read the maximum capacity.
     * @param storeMetricsService Service that provides utilization metrics.
     */
    public WritableNodeStore(
            @NonNull final WritableStates states,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        super(states);

        final long maxCapacity = configuration.getConfigData(NodesConfig.class).maxNumber();
        final var storeMetrics = storeMetricsService.get(StoreType.NODE, maxCapacity);
        nodesState().setMetrics(storeMetrics);
    }

    @Override
    protected WritableKVState<EntityNumber, Node> nodesState() {
        return super.nodesState();
    }

    /**
     * Persists a new {@link Node} into the state, as well as exporting its ID to the transaction
     * receipt.
     *
     * @param node - the node to be mapped onto a new {@link Node}
     */
    public void put(@NonNull final Node node) {
        requireNonNull(node);
        nodesState().put(EntityNumber.newBuilder().number(node.nodeId()).build(), node);
    }

    /**
     * Returns the {@link Node} with the given number using {@link WritableKVState#getForModify}.
     * If no such node exists, returns {@code Optional.empty()}
     * @param nodeId - the id of the node to be retrieved.
     */
    public Node getForModify(final long nodeId) {
        return nodesState()
                .getForModify(EntityNumber.newBuilder().number(nodeId).build());
    }

    /**
     * Returns the number of nodes in the state.
     * @return the number of nodes in the state
     */
    @Override
    public long sizeOfState() {
        return nodesState().size();
    }

    /**
     * Returns the set of nodes modified in existing state.
     * @return the set of nodes modified in existing state
     */
    public Set<EntityNumber> modifiedNodes() {
        return nodesState().modifiedKeys();
    }
}
