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

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import javax.inject.Singleton;

/**
 * Provides information about the network, including the ledger ID, which may be
 */
@Singleton
public class GenesisNetworkInfo implements NetworkInfo {
    private final Roster genesisRoster;
    private final Bytes ledgerId;

    public GenesisNetworkInfo(final Roster genesisRoster, final ConfigProviderImpl configProvider) {
        this.genesisRoster = genesisRoster;
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
        throw new UnsupportedOperationException("Not implemented");
    }

    @NonNull
    @Override
    public List<NodeInfo> addressBook() {
        return genesisRoster.rosterEntries().stream().map(this::fromRosterEntry).toList();
    }

    @Nullable
    @Override
    public NodeInfo nodeInfo(final long nodeId) {
        return genesisRoster.rosterEntries().stream()
                .filter(entry -> entry.nodeId() == nodeId)
                .findFirst()
                .map(this::fromRosterEntry)
                .orElse(null);
    }

    @Override
    public boolean containsNode(final long nodeId) {
        return genesisRoster.rosterEntries().stream().anyMatch(entry -> entry.nodeId() == nodeId);
    }

    private NodeInfo fromRosterEntry(RosterEntry entry) {
        return new NodeInfoImpl(
                entry.nodeId(),
                asAccount(entry.nodeId() + 3),
                entry.weight(),
                entry.gossipEndpoint(),
                entry.gossipCaCertificate());
    }
}
