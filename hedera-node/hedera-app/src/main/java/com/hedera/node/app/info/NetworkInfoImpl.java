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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.info.SelfNodeInfo;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.Platform;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class NetworkInfoImpl implements NetworkInfo {
    private static final Logger logger = LogManager.getLogger(NetworkInfoImpl.class);
    private final Bytes ledgerId;
    private final SelfNodeInfo selfNode;
    private final Platform platform;

    @Inject
    public NetworkInfoImpl(
            @NonNull final SelfNodeInfo selfNode,
            @NonNull final Platform platform,
            @NonNull final ConfigProvider configProvider) {
        // Load the ledger ID from configuration
        final var config = configProvider.getConfiguration();
        final var ledgerConfig = config.getConfigData(LedgerConfig.class);
        ledgerId = ledgerConfig.id();

        // Save the platform for looking up the address book later
        this.platform = requireNonNull(platform);

        // The node representing **this** node within the address book
        this.selfNode = requireNonNull(selfNode);
    }

    @NonNull
    @Override
    public Bytes ledgerId() {
        return ledgerId;
    }

    @NonNull
    @Override
    public SelfNodeInfo selfNodeInfo() {
        return selfNode;
    }

    @NonNull
    @Override
    public List<NodeInfo> addressBook() {
        final var platformAddressBook = platform.getAddressBook();
        return StreamSupport.stream(platformAddressBook.spliterator(), false)
                .map(NodeInfoImpl::fromAddress)
                .toList();
    }

    @Override
    public boolean containsNode(long nodeId) {
        return platform.getAddressBook().contains(new NodeId(nodeId));
    }

    @Nullable
    @Override
    public NodeInfo nodeInfo(long nodeId) {
        return nodeInfo(new NodeId(nodeId));
    }

    @Nullable
    private NodeInfo nodeInfo(@NonNull final NodeId nodeId) {
        if (nodeId.id() == selfNode.nodeId()) {
            return selfNode;
        }

        final var platformAddressBook = platform.getAddressBook();
        if (platformAddressBook == null) return null;

        try {
            final var address = platformAddressBook.getAddress(nodeId);
            return NodeInfoImpl.fromAddress(address);
        } catch (NoSuchElementException e) {
            // The node ID is not in the address book
            logger.warn("Unable to find node with id {} in the platform address book", nodeId, e);
            return null;
        } catch (IllegalArgumentException e) {
            logger.warn("Unable to parse memo of node with id {} in the platform address book", nodeId, e);
            return null;
        }
    }
}
