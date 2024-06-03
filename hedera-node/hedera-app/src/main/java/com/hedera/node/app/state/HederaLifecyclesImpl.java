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

package com.hedera.node.app.state;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_INFO_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.Hedera;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.migration.StateChildIndices;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.merkle.HederaLifecycles;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.config.api.Configuration;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.merkle.disk.OnDiskKey;
import com.swirlds.platform.state.merkle.disk.OnDiskValue;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.state.HederaState;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.Set;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements the major lifecycle events for Hedera Services.
 */
public class HederaLifecyclesImpl implements HederaLifecycles {
    private static final Logger logger = LogManager.getLogger(HederaLifecyclesImpl.class);
    private static final long LEDGER_TOTAL_TINY_BAR_FLOAT = 5000000000000000000L;
    private static final int NUM_REWARD_HISTORY_STORED_PERIODS = 365;

    private static final BiConsumer<
                    VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>>,
                    BiConsumer<EntityNumber, StakingNodeInfo>>
            WEIGHT_UPDATE_VISITOR = (map, visitor) -> {
                try {
                    VirtualMapMigration.extractVirtualMapData(
                            AdHocThreadManager.getStaticThreadManager(),
                            map,
                            pair -> visitor.accept(
                                    pair.key().getKey(), pair.value().getValue()),
                            1);
                } catch (InterruptedException e) {
                    logger.error("Interrupted while updating weights", e);
                    throw new IllegalStateException(e);
                }
            };

    private final Hedera hedera;
    private final BiConsumer<
                    VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>>,
                    BiConsumer<EntityNumber, StakingNodeInfo>>
            weightUpdateVisitor;

    public HederaLifecyclesImpl(@NonNull final Hedera hedera) {
        this(hedera, WEIGHT_UPDATE_VISITOR);
    }

    public HederaLifecyclesImpl(
            @NonNull final Hedera hedera,
            @NonNull
                    final BiConsumer<
                                    VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>>,
                                    BiConsumer<EntityNumber, StakingNodeInfo>>
                            weightUpdateVisitor) {
        this.hedera = requireNonNull(hedera);
        this.weightUpdateVisitor = requireNonNull(weightUpdateVisitor);
    }

    @Override
    public void onPreHandle(@NonNull final Event event, @NonNull final HederaState state) {
        hedera.onPreHandle(event, state);
    }

    @Override
    public void onHandleConsensusRound(
            @NonNull final Round round, @NonNull final PlatformState platformState, @NonNull final HederaState state) {
        hedera.onHandleConsensusRound(round, platformState, state);
    }

    @Override
    public void onStateInitialized(
            @NonNull final MerkleHederaState state,
            @NonNull final Platform platform,
            @NonNull final PlatformState platformState,
            @NonNull final InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {
        hedera.onStateInitialized(state, platform, platformState, trigger, previousVersion);
    }

    @Override
    public void onUpdateWeight(
            @NonNull final MerkleHederaState state,
            @NonNull final AddressBook configAddressBook,
            @NonNull final PlatformContext context) {
        final var isMonoState = state.getChild(StateChildIndices.STAKING_INFO) instanceof MerkleMap;
        // Get all nodeIds added in the config.txt
        final var nodeIdsLeftToUpdate = configAddressBook.getNodeIdSet();
        if (!isMonoState) {
            final var stakingInfoIndex = state.findNodeIndex(TokenService.NAME, STAKING_INFO_KEY);
            if (stakingInfoIndex == -1) {
                logger.warn("Staking info not found in state, skipping weight update");
                return;
            }
            @SuppressWarnings("unchecked")
            final var stakingInfoVMap = (VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>>)
                    state.getChild(stakingInfoIndex);
            // Since it is much easier to modify the in-state staking info after schemas
            // are registered with MerkleHederaState, we do that work later in the token
            // service schema's restart() hook. Here we only update the address book weights
            // based on the staking info in the state.
            weightUpdateVisitor.accept(stakingInfoVMap, (node, info) -> {
                final var nodeId = new NodeId(node.number());
                // If present in the address book, remove this node id from the
                // set of node ids left to update and update its weight
                if (nodeIdsLeftToUpdate.remove(nodeId)) {
                    configAddressBook.updateWeight(nodeId, info.weight());
                } else {
                    logger.info(
                            "Node {} is no longer in address book; restart() will ensure its staking info is marked deleted",
                            nodeId);
                }
            });
            nodeIdsLeftToUpdate.forEach(nodeId -> {
                configAddressBook.updateWeight(nodeId, 0);
                logger.info("Found new node {}; restart() will add its staking info", nodeId);
            });
        } else {
            final var configAddressBookSize = nodeIdsLeftToUpdate.size();
            // When doing the first upgrade from 0.48 to 0.49, we will call updateWeight before BBM migration.
            // In this case, we need to update the weight in the stakingInfo map from mono service state.
            logger.info("Token service state is empty, so we are migrating from mono to mod-service with "
                    + "and updating the mono-service stakingInfo map.");
            final var stakingInfosMap = MerkleMapLike.from(
                    (MerkleMap<EntityNum, MerkleStakingInfo>) state.getChild(StateChildIndices.STAKING_INFO));
            stakingInfosMap.forEachNode((nodeNum, stakingInfo) -> {
                final NodeId nodeId = new NodeId(nodeNum.longValue());
                if (nodeIdsLeftToUpdate.contains(nodeId)) {
                    configAddressBook.updateWeight(nodeId, stakingInfo.getWeight());
                    nodeIdsLeftToUpdate.remove(nodeId);
                } else {
                    final var newStakingInfo = stakingInfosMap.getForModify(nodeNum);
                    newStakingInfo.setWeight(0);
                    logger.warn(
                            "Node {} is deleted from configAddressBook during upgrade and "
                                    + "is updated with weight zero",
                            nodeId);
                }
            });
            // for any newly added nodes that doesn't exist in state, weight should be set to 0
            // irrespective of the weight provided in config.txt
            if (!nodeIdsLeftToUpdate.isEmpty()) {
                nodeIdsLeftToUpdate.forEach(nodeId -> {
                    configAddressBook.updateWeight(nodeId, 0);
                    logger.info(
                            "Node {} is newly added in configAddressBook during upgrade "
                                    + "with weight 0 in configAddressBook",
                            nodeId);
                });
                // update the state with new nodes and set weight to 0
                addAdditionalNodesToState(
                        stakingInfosMap, nodeIdsLeftToUpdate, context.getConfiguration(), configAddressBookSize);
            }
        }
    }

    /**
     * Add additional nodes to state with weight 0 and update all nodes maxStake to maxStakePerNode
     *
     * @param stakingInfoMap The state to update
     * @param newNodeIds The new node ids to add
     * @param config The configuration
     * @param configAddressBookSize The size of the address book
     */
    private void addAdditionalNodesToState(
            @NonNull final MerkleMapLike<EntityNum, MerkleStakingInfo> stakingInfoMap,
            @NonNull final Set<NodeId> newNodeIds,
            @NonNull final Configuration config,
            final int configAddressBookSize) {
        // Since PlatformContext configuration is not available here,
        // we are using the default values here. (FUTURE) Need to see how to use config here
        // for ledger.totalTinyBarFloat and staking.rewardHistory.numStoredPeriods
        final long maxStakePerNode = LEDGER_TOTAL_TINY_BAR_FLOAT / configAddressBookSize;

        // Add new nodes with weight 0
        for (final var nodeId : newNodeIds) {
            final var rewardSumHistory = new long[NUM_REWARD_HISTORY_STORED_PERIODS + 1];
            Arrays.fill(rewardSumHistory, 0L);

            final var id = new EntityNum((int) nodeId.id());
            final var newNodeStakingInfo = new MerkleStakingInfo();
            newNodeStakingInfo.setKey(id);
            newNodeStakingInfo.setMaxStake(maxStakePerNode);
            newNodeStakingInfo.setMinStake(0L);
            newNodeStakingInfo.setRewardSumHistory(rewardSumHistory);
            newNodeStakingInfo.setWeight(0);

            stakingInfoMap.put(id, newNodeStakingInfo);
            logger.info("Node {} is added in state with weight 0 and maxStakePerNode {} ", nodeId, maxStakePerNode);
        }

        // Update all nodes maxStake to maxStakePerNode
        stakingInfoMap.forEachNode((k, v) -> {
            final var stakingInfo = stakingInfoMap.getForModify(k);
            stakingInfo.setMaxStake(maxStakePerNode);
        });
    }

    @Override
    public void onNewRecoveredState(@NonNull final MerkleHederaState recoveredState) {
        hedera.onNewRecoveredState(recoveredState);
    }
}
