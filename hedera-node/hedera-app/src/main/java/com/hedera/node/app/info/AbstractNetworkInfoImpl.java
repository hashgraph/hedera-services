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

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.Platform;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import com.swirlds.state.spi.info.SelfNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractNetworkInfoImpl implements NetworkInfo {
    private static final Logger logger = LogManager.getLogger(AbstractNetworkInfoImpl.class);

    private final SelfNodeInfo selfNode;
    private final Platform platform;

    protected AbstractNetworkInfoImpl(@NonNull final SelfNodeInfo selfNode, @NonNull final Platform platform) {
        this.selfNode = requireNonNull(selfNode);
        this.platform = requireNonNull(platform);
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
            // The node ID is not in the address book; this is a normal condition
            // if user error leads to a request for a non-existent node
            return null;
        } catch (IllegalArgumentException e) {
            logger.warn("Unable to parse memo of node with id {} in the platform address book", nodeId, e);
            return null;
        }
    }
}
