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
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.NODES_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.lifecycle.info.NodeInfo;
import com.swirlds.state.spi.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides information about the network, including the ledger ID, which may be
 * overridden by configuration in state and cannot be used during state migrations
 * that precede loading configuration sources from state.
 */
@Singleton
public class StateNetworkInfo implements NetworkInfo {
    private static final Logger log = LogManager.getLogger(StateNetworkInfo.class);
    private final long selfId;
    private final Bytes ledgerId;
    /**
     * The active roster, used to limit exposed node info to the active set of nodes.
     */
    private final Roster activeRoster;

    private final Map<Long, NodeInfo> nodeInfos;

    /**
     * Constructs a new network information provider from the given state, roster, selfID, and configuration provider.
     *
     * @param selfId the ID of the node
     * @param state the state to retrieve the network information from
     * @param roster the roster to retrieve the network information from
     * @param configProvider the configuration provider to retrieve the ledger ID from
     */
    public StateNetworkInfo(
            final long selfId,
            @NonNull final State state,
            @NonNull final Roster roster,
            @NonNull final ConfigProvider configProvider) {
        requireNonNull(state);
        requireNonNull(configProvider);
        this.activeRoster = requireNonNull(roster);
        this.ledgerId = configProvider
                .getConfiguration()
                .getConfigData(LedgerConfig.class)
                .id();
        this.nodeInfos = nodeInfosFrom(state);
        this.selfId = selfId;
    }

    @NonNull
    @Override
    public Bytes ledgerId() {
        return ledgerId;
    }

    @NonNull
    @Override
    public NodeInfo selfNodeInfo() {
        return nodeInfos.get(selfId);
    }

    @NonNull
    @Override
    public List<NodeInfo> addressBook() {
        return List.copyOf(nodeInfos.values());
    }

    @Nullable
    @Override
    public NodeInfo nodeInfo(final long nodeId) {
        return nodeInfos.get(nodeId);
    }

    @Override
    public boolean containsNode(final long nodeId) {
        return nodeInfos.containsKey(nodeId);
    }

    @Override
    public void updateFrom(@NonNull final State state) {
        nodeInfos.clear();
        nodeInfos.putAll(nodeInfosFrom(state));
    }

    /**
     * Build a map of node information from the state. The map is keyed by node ID.
     * The node information is retrieved from the address book service. If the node
     * information is not found in the address book service, it is not included in the map.
     *
     * @param state the state to retrieve the node information from
     * @return a map of node information
     */
    private Map<Long, NodeInfo> nodeInfosFrom(@NonNull final State state) {
        final ReadableKVState<EntityNumber, Node> nodes =
                state.getReadableStates(AddressBookService.NAME).get(NODES_KEY);
        final Map<Long, NodeInfo> nodeInfos = new LinkedHashMap<>();
        for (final var rosterEntry : activeRoster.rosterEntries()) {
            // At genesis the node store is derived from the roster, hence must have info for every
            // node id; and from then on, the roster is derived from the node store, and hence the
            // node store must have every node id in the roster.
            final var node = nodes.get(new EntityNumber(rosterEntry.nodeId()));
            if (node != null) {
                // Notice it's possible the node could be deleted here, because a DAB transaction removed
                // it from the future address book; that doesn't mean we should stop using it in the current
                // version of the software
                nodeInfos.put(rosterEntry.nodeId(), fromRosterEntry(rosterEntry, node));
            } else {
                nodeInfos.put(
                        rosterEntry.nodeId(),
                        fromRosterEntry(
                                rosterEntry,
                                AccountID.newBuilder()
                                        .accountNum(rosterEntry.nodeId() + 3)
                                        .build()));
                log.warn("Node {} not found in node store", rosterEntry.nodeId());
            }
        }
        return nodeInfos;
    }
}
