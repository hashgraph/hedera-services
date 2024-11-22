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

package com.hedera.node.app.service.addressbook.impl.helpers;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.service.addressbook.impl.WritableNodeStore;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper class to update node store after upgrade.
 */
@Singleton
public class AddressBookHelper {
    private static final Logger log = LogManager.getLogger(AddressBookHelper.class);

    @Inject
    public AddressBookHelper() {}

    /**
     * Adjusts the node metadata after upgrade. This method will mark nodes as deleted if they are not present in the
     * address book and add new nodes to the node store.
     * @param networkInfo the network info
     * @param config configuration
     * @param nodeStore the node store
     */
    public void adjustPostUpgradeNodeMetadata(
            @NonNull final NetworkInfo networkInfo,
            @NonNull final Configuration config,
            @NonNull final WritableNodeStore nodeStore) {
        requireNonNull(networkInfo);
        requireNonNull(config);
        final var nodeStoreIds = getNodeIds(nodeStore.keys());
        nodeStoreIds.stream().sorted().forEach(nodeId -> {
            final var node = requireNonNull(nodeStore.get(nodeId));
            if (!networkInfo.containsNode(nodeId) && !node.deleted()) {
                nodeStore.put(node.copyBuilder().weight(0).deleted(true).build());
                log.info("Marked node{} as deleted since it has been removed from the address book", nodeId);
            }
        });
        // Add new nodes
        for (final var nodeInfo : networkInfo.addressBook()) {
            final var node = nodeStore.get(nodeInfo.nodeId());
            if (node == null) {
                final var newNode = Node.newBuilder()
                        .nodeId(nodeInfo.nodeId())
                        .weight(nodeInfo.stake())
                        .accountId(nodeInfo.accountId())
                        .gossipCaCertificate(nodeInfo.sigCertBytes())
                        .gossipEndpoint(nodeInfo.gossipEndpoints())
                        .build();
                nodeStore.put(newNode);
            }
        }
    }

    private Set<Long> getNodeIds(final Iterator<EntityNumber> nodeStoreIds) {
        if (!nodeStoreIds.hasNext()) return Collections.emptySet();

        final var nodeIds = new HashSet<Long>();
        while (nodeStoreIds.hasNext()) {
            nodeIds.add(nodeStoreIds.next().number());
        }
        return nodeIds;
    }
}
