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

package com.hedera.node.app.workflows.handle.steps;

import static com.swirlds.platform.roster.RosterUtils.formatNodeName;
import static java.util.Collections.emptyMap;
import static java.util.Comparator.comparingLong;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toMap;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NodeAddress;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.service.addressbook.impl.WritableNodeStore;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.FilesConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class NodeMetadataHelper {
    private static final Logger log = LogManager.getLogger(NodeMetadataHelper.class);

    @Inject
    public NodeMetadataHelper() {
        // Dagger2
    }

    /**
     * Aligns the node metadata in the writable node store with the values in the other stores based
     * on the given configuration. This includes,
     * <ol>
     *     <li>Setting each node's admin key to the address book admin's key.</li>
     *     <li>Updating each node's weight to its stake-derived weight.</li>
     *     <li>Aligning all "node details" metadata with the file service data.</li>
     * </ol>
     *
     * @param networkInfo the network info
     * @param config the configuration
     * @param fileStore the file store
     * @param accountStore the account store
     * @param stakingInfoStore the staking info store
     * @param nodeStore the node store
     */
    public void updateMetadata(
            @NonNull final NetworkInfo networkInfo,
            @NonNull final Configuration config,
            @NonNull final ReadableFileStore fileStore,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableStakingInfoStore stakingInfoStore,
            @NonNull final WritableNodeStore nodeStore) {
        final List<EntityNumber> nodeIds = StreamSupport.stream(
                        spliteratorUnknownSize(nodeStore.keys(), NONNULL), false)
                .sorted(comparingLong(EntityNumber::number))
                .toList();
        final var addressBookAdminKey = addressBookAdminKey(config, accountStore);
        final var nodeDetails = nodeDetails(config, fileStore);
        nodeIds.forEach(nodeId -> {
            final var node = nodeStore.get(nodeId.number());
            // Collect all the information needed to update this node's metadata, logging
            // an ERROR and skipping the metadata update if any of it is somehow missing
            // (which should never happen, but here we err on the side of caution)
            if (node == null) {
                log.error("Node {} not found in the node store", nodeId.number());
                return;
            }
            if (node.deleted()) {
                log.info("Node {} is deleted, skipping metadata update", nodeId.number());
                return;
            }
            final var info = networkInfo.nodeInfo(nodeId.number());
            if (info == null) {
                log.error("Node {} not found in the network info", nodeId.number());
                return;
            }
            final var details = nodeDetails.get(nodeId);
            if (details == null) {
                log.error("Node {} not found in the node details", nodeId.number());
                return;
            }
            final var stakingInfo = stakingInfoStore.get(nodeId.number());
            if (stakingInfo == null) {
                log.error("Node {} not found in the staking info store", nodeId.number());
                return;
            }
            final var builder = node.copyBuilder()
                    .accountId(info.accountId())
                    .description(formatNodeName(details.nodeId()))
                    .gossipEndpoint(info.gossipEndpoints())
                    .gossipCaCertificate(info.sigCertBytes())
                    .weight(details.stake())
                    .adminKey(addressBookAdminKey)
                    .serviceEndpoint(details.serviceEndpoint())
                    .grpcCertificateHash(details.nodeCertHash());
            nodeStore.put(builder.build());
        });
    }

    private @NonNull Key addressBookAdminKey(
            @NonNull final Configuration config, @NonNull final ReadableAccountStore accountStore) {
        final var accountConfig = config.getConfigData(AccountsConfig.class);
        final var addressBookAdmin = accountStore.getAccountById(AccountID.newBuilder()
                .accountNum(accountConfig.addressBookAdmin())
                .build());
        return requireNonNull(addressBookAdmin).keyOrThrow();
    }

    private @NonNull Map<EntityNumber, NodeAddress> nodeDetails(
            @NonNull final Configuration config, @NonNull final ReadableFileStore fileStore) {
        final var fileConfig = config.getConfigData(FilesConfig.class);
        final var nodeDetailsFile = fileStore.getFileLeaf(
                FileID.newBuilder().fileNum(fileConfig.nodeDetails()).build());
        try {
            final var nodeDetails = NodeAddressBook.PROTOBUF
                    .parse(requireNonNull(nodeDetailsFile).contents())
                    .nodeAddress();
            return nodeDetails.stream()
                    .collect(toMap(details -> new EntityNumber(details.nodeId()), Function.identity()));
        } catch (Exception e) {
            log.error("Can not parse node details", e);
            return emptyMap();
        }
    }
}
