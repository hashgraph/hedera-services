/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.NODES_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.spi.ids.ReadableEntityCounters;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Nodes.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public class ReadableNodeStoreImpl implements ReadableNodeStore {
    /** The underlying data storage class that holds the node data. */
    private final ReadableKVState<EntityNumber, Node> nodesState;

    private final ReadableEntityCounters entityCounters;

    /**
     * Create a new {@link ReadableNodeStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableNodeStoreImpl(
            @NonNull final ReadableStates states, @NonNull final ReadableEntityCounters entityCounters) {
        requireNonNull(states);
        this.entityCounters = requireNonNull(entityCounters);
        this.nodesState = states.get(NODES_KEY);
    }

    @Override
    public Roster snapshotOfFutureRoster(Function<Long, Long> weightFunction) {
        return constructFromNodesStateWithStakingInfoWeight(nodesState(), weightFunction);
    }

    /**
     * Returns the node needed.
     *
     * @param nodeId node id being looked up
     * @return node
     */
    @Override
    @Nullable
    public Node get(final long nodeId) {
        return nodesState.get(EntityNumber.newBuilder().number(nodeId).build());
    }

    /**
     * Returns the number of topics in the state.
     * @return the number of topics in the state
     */
    public long sizeOfState() {
        return nodesState.size();
        // FUTURE: Use entityCounters to get size.
    }

    protected <T extends ReadableKVState<EntityNumber, Node>> T nodesState() {
        return (T) nodesState;
    }

    @NonNull
    public Iterator<EntityNumber> keys() {
        return nodesState().keys();
    }

    private Roster constructFromNodesStateWithStakingInfoWeight(
            @NonNull final ReadableKVState<EntityNumber, Node> nodesState,
            @NonNull final Function<Long, Long> weightProvider) {
        final var rosterEntries = new ArrayList<RosterEntry>();
        for (final var it = nodesState.keys(); it.hasNext(); ) {
            final var nodeNumber = it.next();
            final var node = requireNonNull(nodesState.get(nodeNumber));
            var nodeEndpoints = node.gossipEndpoint();
            // we want to swap the internal and external node endpoints
            // so that the external one is at index 0
            if (nodeEndpoints.size() > 1) {
                nodeEndpoints = List.of(nodeEndpoints.getLast(), nodeEndpoints.getFirst());
            }
            if (!node.deleted()) {
                final var entry = RosterEntry.newBuilder()
                        .nodeId(node.nodeId())
                        .weight(weightProvider.apply(node.nodeId()))
                        .gossipCaCertificate(node.gossipCaCertificate())
                        .gossipEndpoint(nodeEndpoints)
                        .build();
                rosterEntries.add(entry);
            }
        }
        rosterEntries.sort(Comparator.comparingLong(RosterEntry::nodeId));
        return Roster.newBuilder().rosterEntries(rosterEntries).build();
    }
}
