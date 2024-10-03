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

package com.hedera.node.app.info;

import static com.hedera.node.app.info.NodeInfoImpl.fromRosterEntry;

import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import javax.inject.Singleton;

/**
 * Provides information about the network, including the ledger ID, which may be
 * overridden by configuration in state and cannot be used during state migrations
 * that precede loading configuration sources from state.
 */
@Singleton
public class NetworkInfoImpl implements NetworkInfo {
    private final Bytes ledgerId;
    private final List<RosterEntry> rosterEntries;
    private final ReadableKVState<EntityNumber, Node> readableNodeState;
    private final NodeInfo selfNodeInfo;

    public NetworkInfoImpl(
            @NonNull final List<RosterEntry> rosterEntries,
            @NonNull final ReadableKVState<EntityNumber, Node> nodeState,
            @NonNull final NodeInfo selfNodeInfo,
            @NonNull final ConfigProvider configProvider) {
        this.rosterEntries = rosterEntries;
        this.selfNodeInfo = selfNodeInfo;
        this.readableNodeState = nodeState;
        // Load the ledger ID from configuration
        final var config = configProvider.getConfiguration();
        final var ledgerConfig = config.getConfigData(LedgerConfig.class);
        ledgerId = ledgerConfig.id();
    }

    @NonNull
    @Override
    public Bytes ledgerId() {
        return ledgerId;
    }

    @NonNull
    @Override
    public NodeInfo selfNodeInfo() {
        return selfNodeInfo;
    }

    @NonNull
    @Override
    public List<NodeInfo> addressBook() {
        return rosterEntries.stream()
                .map(entry -> fromRosterEntry(
                        entry,
                        readableNodeState.get(
                                EntityNumber.newBuilder().number(entry.nodeId()).build())))
                .toList();
    }

    @Nullable
    @Override
    public NodeInfo nodeInfo(final long nodeId) {
        final var rosterEntry = rosterEntries.stream()
                .filter(entry -> entry.nodeId() == nodeId)
                .findFirst()
                .orElse(null);
        final var node =
                readableNodeState.get(EntityNumber.newBuilder().number(nodeId).build());
        if (rosterEntry == null || node == null) {
            return null;
        }
        return fromRosterEntry(rosterEntry, node);
    }

    @Override
    public boolean containsNode(final long nodeId) {
        return rosterEntries.stream().anyMatch(entry -> entry.nodeId() == nodeId);
    }
}
