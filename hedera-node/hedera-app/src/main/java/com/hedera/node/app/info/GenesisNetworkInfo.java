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

package com.hedera.node.app.info;

import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.lifecycle.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides information about the network based on the given roster and ledger ID.
 */
public class GenesisNetworkInfo implements NetworkInfo {
    private final Bytes ledgerId;
    private final Roster genesisRoster;
    private final Map<Long, NodeInfo> nodeInfos;

    /**
     * Constructs a new {@link GenesisNetworkInfo} instance.
     *
     * @param genesisRoster The genesis roster
     * @param ledgerId      The ledger ID
     */
    public GenesisNetworkInfo(@NonNull final Roster genesisRoster, @NonNull final Bytes ledgerId) {
        this.ledgerId = requireNonNull(ledgerId);
        this.genesisRoster = requireNonNull(genesisRoster);
        this.nodeInfos = buildNodeInfoMap(genesisRoster);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Bytes ledgerId() {
        return ledgerId;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public NodeInfo selfNodeInfo() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<NodeInfo> addressBook() {
        return List.copyOf(nodeInfos.values());
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public NodeInfo nodeInfo(final long nodeId) {
        return nodeInfos.get(nodeId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsNode(final long nodeId) {
        return nodeInfos.containsKey(nodeId);
    }

    @Override
    public void updateFrom(final State state) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Roster roster() {
        return genesisRoster;
    }

    /**
     * Builds a map of node information from the given roster. The map is keyed by node ID.
     * The node information is retrieved from the roster entry.
     * If the node information is not found in the roster entry, it is not included in the map.
     *
     * @param roster The roster to retrieve the node information from
     * @return A map of node information
     */
    private Map<Long, NodeInfo> buildNodeInfoMap(@NonNull final Roster roster) {
        final var nodeInfos = new LinkedHashMap<Long, NodeInfo>();
        final var rosterEntries = roster.rosterEntries();
        for (final var rosterEntry : rosterEntries) {
            nodeInfos.put(rosterEntry.nodeId(), fromRosterEntry(rosterEntry));
        }
        return nodeInfos;
    }

    /**
     * Builds a node info from a roster entry from the given roster.
     * Since this is only used in the genesis case, the account ID is generated from the node ID
     * by adding 3 to it, as a default case.
     *
     * @param entry The roster entry
     * @return The node info
     */
    private NodeInfo fromRosterEntry(@NonNull final RosterEntry entry) {
        // The RosterEntry has external ip address at index 0.
        // The NodeInfo needs to have internal ip address at index 0.
        // swap the internal and external endpoints when converting to NodeInfo.
        final var gossipEndpointsCopy = new ArrayList<>(entry.gossipEndpoint());
        if (gossipEndpointsCopy.size() > 1) {
            Collections.swap(gossipEndpointsCopy, 0, 1);
        }

        return new NodeInfoImpl(
                entry.nodeId(),
                asAccount(entry.nodeId() + 3),
                entry.weight(),
                gossipEndpointsCopy,
                entry.gossipCaCertificate());
    }
}
