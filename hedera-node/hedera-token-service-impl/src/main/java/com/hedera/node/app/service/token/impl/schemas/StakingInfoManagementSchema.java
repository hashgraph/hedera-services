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

package com.hedera.node.app.service.token.impl.schemas;

import static java.util.Collections.nCopies;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.impl.ReadableStakingInfoStoreImpl;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Defines the schema for managing staking info.
 * IMPORTANT: Every TokenSchema version should extend this interface
 */
public class StakingInfoManagementSchema extends Schema {
    private final Logger log = LogManager.getLogger(StakingInfoManagementSchema.class);

    /**
     * Create a new instance.
     *
     * @param version The version of this schema
     */
    protected StakingInfoManagementSchema(@NonNull final SemanticVersion version) {
        super(version);
    }

    /**
     * Updates in-state staking info to match the address book.
     * <ol>
     *     <li>For any node with staking info in state that is no longer in the address book,
     *     marks it deleted and sets its weight to zero.</li>
     *     <li>For any node in the address book that is not in state,
     *     initializes its staking info.</li>
     *     <li>Ensures all max stake values reflect the current address book size.</li>
     * </ol>
     *
     * @param ctx {@link MigrationContext} for this schema restart operation
     */
    @Override
    public void restart(@NonNull MigrationContext ctx) {
        final var networkInfo = ctx.networkInfo();
        final var newStakingStore = new WritableStakingInfoStore(ctx.newStates());
        // We need to validate and mark any node that are removed during upgrade as deleted.
        // Since restart is called in the schema after an upgrade, and we don't want to depend on
        // schema version change, validate all the nodeIds from the addressBook in state and mark
        // them as deleted if they are not yet deleted in staking info.
        if (!ctx.previousStates().isEmpty()) {
            final var oldStakingStore = new ReadableStakingInfoStoreImpl(ctx.previousStates());
            oldStakingStore.getAll().stream().sorted().forEach(nodeId -> {
                final var stakingInfo = requireNonNull(oldStakingStore.get(nodeId));
                if (!networkInfo.containsNode(nodeId) && !stakingInfo.deleted()) {
                    newStakingStore.put(
                            nodeId,
                            stakingInfo.copyBuilder().weight(0).deleted(true).build());
                    log.info("Marked node{} as deleted since it has been removed from the address book", nodeId);
                }
            });
        }
        // Validate if any new nodes are added in addressBook and not in staking info.
        // If so, add them to staking info/ with weight 0. Also update maxStake and
        // minStake for the new nodes.
        completeUpdateFromNewAddressBook(newStakingStore, networkInfo.addressBook(), ctx.configuration());
    }

    private void completeUpdateFromNewAddressBook(
            @NonNull final WritableStakingInfoStore store,
            @NonNull final List<NodeInfo> nodeInfos,
            @NonNull final Configuration config) {
        final var numberOfNodesInAddressBook = nodeInfos.size();
        final long maxStakePerNode =
                config.getConfigData(LedgerConfig.class).totalTinyBarFloat() / numberOfNodesInAddressBook;
        final var numRewardHistoryStoredPeriods =
                config.getConfigData(StakingConfig.class).rewardHistoryNumStoredPeriods();
        for (final var nodeId : nodeInfos) {
            final var stakingInfo = store.get(nodeId.nodeId());
            if (stakingInfo != null) {
                if (stakingInfo.maxStake() != maxStakePerNode) {
                    store.put(
                            nodeId.nodeId(),
                            stakingInfo.copyBuilder().maxStake(maxStakePerNode).build());
                }
            } else {
                final var newNodeStakingInfo = StakingNodeInfo.newBuilder()
                        .nodeNumber(nodeId.nodeId())
                        .maxStake(maxStakePerNode)
                        .minStake(0L)
                        .rewardSumHistory(
                                nCopies(numRewardHistoryStoredPeriods + 1, 0L).toArray(Long[]::new))
                        .weight(0)
                        .build();
                store.put(nodeId.nodeId(), newNodeStakingInfo);
            }
        }
    }
}
