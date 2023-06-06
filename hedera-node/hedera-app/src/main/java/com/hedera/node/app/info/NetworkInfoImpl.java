/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NetworkInfoImpl implements NetworkInfo {
    private final Bytes ledgerId;
    private final NodeId selfId;
    private final Platform platform;

    @Inject
    public NetworkInfoImpl(
            @NonNull final NodeId selfNodeId,
            @NonNull final Platform platform,
            @NonNull final ConfigProvider configProvider) {
        // Load the ledger ID from configuration
        final var config = configProvider.getConfiguration();
        final var ledgerConfig = config.getConfigData(LedgerConfig.class);
        ledgerId = ledgerConfig.id();

        // Save the platform for looking up the address book later
        this.platform = requireNonNull(platform);

        // The node ID of **this** node within the address book
        this.selfId = requireNonNull(selfNodeId);
        if (platform.getAddressBook().getAddress(selfNodeId) == null) {
            throw new IllegalArgumentException("Node ID " + this.selfId + " is not in the address book");
        }
    }

    @NonNull
    @Override
    public Bytes ledgerId() {
        return ledgerId;
    }

    @NonNull
    @Override
    public NodeInfo selfNodeInfo() {
        final var self = nodeInfo(selfId);
        if (self == null) throw new IllegalStateException("Self Node ID " + selfId + " is not in the address book!!");
        return self;
    }

    @NonNull
    @Override
    public List<NodeInfo> addressBook() {
        final var platformAddressBook = platform.getAddressBook();
        return StreamSupport.stream(platformAddressBook.spliterator(), false)
                .map(NodeInfoImpl::fromAddress)
                .collect(Collectors.toList());
    }

    @Nullable
    @Override
    public NodeInfo nodeInfo(long nodeId) {
        return nodeInfo(new NodeId(nodeId));
    }

    @Nullable
    private NodeInfo nodeInfo(@NonNull final NodeId nodeId) {
        final var platformAddressBook = platform.getAddressBook();
        if (platformAddressBook == null) return null;

        final var address = platformAddressBook.getAddress(nodeId);
        return address == null ? null : NodeInfoImpl.fromAddress(address);
    }
}
