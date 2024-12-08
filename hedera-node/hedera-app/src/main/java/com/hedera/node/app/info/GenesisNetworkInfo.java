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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.internal.network.NodeMetadata;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.lifecycle.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides information about the network based on the given roster and ledger ID.
 */
public class GenesisNetworkInfo implements NetworkInfo {
    private final Bytes ledgerId;
    private final Map<Long, NodeInfo> nodeInfos;

    /**
     * Constructs a new {@link GenesisNetworkInfo} instance.
     *
     * @param genesisNetwork The genesis network
     * @param ledgerId      The ledger ID
     */
    public GenesisNetworkInfo(@NonNull final List<NodeMetadata> genesisNetwork, @NonNull final Bytes ledgerId) {
        this.ledgerId = requireNonNull(ledgerId);
        this.nodeInfos = GenesisNetworkInfo.fromMetadata(genesisNetwork);
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
        // This class should be constructed using the genesis AddressBook. The roster, on the other hand, should not be
        // created until just before handing the roster over to the platform; therefore, we throw an illegal exception
        // here
        throw new IllegalStateException("No roster available at network genesis");
    }

    private static Map<Long, NodeInfo> fromMetadata(@NonNull List<NodeMetadata> metadatas) {
        final var nodeInfos = new LinkedHashMap<Long, NodeInfo>();
        for (final var metadata : metadatas) {
            final var node = metadata.nodeOrThrow();
            final var nodeInfo = new NodeInfoImpl(
                    node.nodeId(), node.accountId(), node.weight(), node.gossipEndpoint(), node.gossipCaCertificate());
            nodeInfos.put(node.nodeId(), nodeInfo);
        }
        return nodeInfos;
    }
}
