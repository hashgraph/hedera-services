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
import static com.hedera.node.app.service.addressbook.AddressBookHelper.NODES_KEY;
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
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
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
    private final Bytes ledgerId;
    private final Map<Long, NodeInfo> nodeInfos;
    private final long selfId;
    private final Roster activeRoster;

    public StateNetworkInfo(
            @NonNull final State state,
            final long selfId,
            @NonNull final ConfigProvider configProvider,
            @NonNull final Roster activeRoster) {
        this.selfId = selfId;
        this.nodeInfos = buildNodeInfoMap(state);
        // Load the ledger ID from configuration
        final var config = requireNonNull(configProvider.getConfiguration());
        final var ledgerConfig = config.getConfigData(LedgerConfig.class);
        ledgerId = ledgerConfig.id();
        this.activeRoster = requireNonNull(activeRoster);
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
        nodeInfos.putAll(buildNodeInfoMap(state));
    }

    /**
     * Build a map of node information from the state. The map is keyed by node ID.
     * The node information is retrieved from the address book service. If the node
     * information is not found in the address book service, it is not included in the map.
     *
     * @param state the state to retrieve the node information from
     * @return a map of node information
     */
    private Map<Long, NodeInfo> buildNodeInfoMap(final State state) {
        final var nodeInfos = new LinkedHashMap<Long, NodeInfo>();
        // Add all nodeInfo from activeRoster
        final ReadableKVState<EntityNumber, Node> nodeState =
                state.getReadableStates(AddressBookService.NAME).get(NODES_KEY);
        for (final var rosterEntry : requireNonNull(activeRoster).rosterEntries()) {
            final var node = nodeState.get(
                    EntityNumber.newBuilder().number(rosterEntry.nodeId()).build());
            if (node != null) {
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
