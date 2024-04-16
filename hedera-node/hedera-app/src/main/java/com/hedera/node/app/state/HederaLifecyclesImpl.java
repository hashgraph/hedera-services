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
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.state.merkle.HederaLifecycles;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements the major lifecycle events for Hedera Services.
 */
public class HederaLifecyclesImpl implements HederaLifecycles {
    private static final Logger logger = LogManager.getLogger(HederaLifecyclesImpl.class);

    private final Hedera hedera;

    public HederaLifecyclesImpl(@NonNull final Hedera hedera) {
        this.hedera = Objects.requireNonNull(hedera);
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
        final var tokenServiceState = state.getWritableStates(TokenService.NAME);
        final var stakingInfoState =
                state.getWritableStates(TokenService.NAME).<EntityNumber, StakingNodeInfo>get(STAKING_INFO_KEY);
        if (!tokenServiceState.isEmpty()) {
            final var readableStoreFactory = new ReadableStoreFactory(state);
            // Get all nodeIds added in the config.txt
            Set<NodeId> configNodeIds = configAddressBook.getNodeIdSet();
            final var configAddressBookSize = configNodeIds.size();
            final var stakingInfoStore = readableStoreFactory.getStore(ReadableStakingInfoStore.class);
            final var allNodeIds = stakingInfoStore.getAll();
            for (final var nodeId : allNodeIds) {
                final var stakingInfo = requireNonNull(stakingInfoStore.get(nodeId));
                NodeId id = new NodeId(nodeId);
                // set weight for the nodes that exist in state and remove from
                // nodes given in config.txt. This is needed to recognize newly added nodes
                // It is possible that some nodes are deleted in configAddressBook compared to state
                // We will set those node sas deleted in EndOfStakingPeriodCalculator
                if (configNodeIds.contains(id)) {
                    configAddressBook.updateWeight(id, stakingInfo.weight());
                    configNodeIds.remove(id);
                } else {
                    // We need to validate and mark any node that are removed during upgrade as deleted.
                    stakingInfoState.put(
                            EntityNumber.newBuilder().number(id.id()).build(),
                            stakingInfo.copyBuilder().deleted(true).weight(0).build());
                    logger.info(
                            "Node {} is deleted from configAddressBook during upgrade "
                                    + "and is marked deleted in state",
                            id);
                }
            }
            // for any newly added nodes that doesn't exist in state, weight should be set to 0
            // irrespective of the weight provided in config.txt
            if (!configNodeIds.isEmpty()) {
                addAdditionalNodesToState(
                        stakingInfoState, configNodeIds, context.getConfiguration(), configAddressBookSize);
                configNodeIds.forEach(nodeId -> {
                    configAddressBook.updateWeight(nodeId, 0);
                    logger.info(
                            "Node {} is newly added in configAddressBook during upgrade "
                                    + "and is added to state with weight 0 in state",
                            nodeId);
                });
            }

            if (stakingInfoState.isModified()) {
                ((WritableKVStateBase) stakingInfoState).commit();
            }
        } else {
            logger.warn("Token service state is empty to update weights from StakingInfo Map");
        }
    }

    private void addAdditionalNodesToState(
            @NonNull final WritableKVState<EntityNumber, StakingNodeInfo> stakingToState,
            @NonNull final Set<NodeId> newNodeIds,
            @NonNull final Configuration config,
            final int configAddressBookSize) {
        final long maxStakePerNode =
                config.getConfigData(LedgerConfig.class).totalTinyBarFloat() / configAddressBookSize;
        final var numRewardHistoryStoredPeriods =
                config.getConfigData(StakingConfig.class).rewardHistoryNumStoredPeriods();

        final var rewardSumHistory = new Long[numRewardHistoryStoredPeriods + 1];
        Arrays.fill(rewardSumHistory, 0L);

        // Add new nodes with weight 0
        for (final var nodeId : newNodeIds) {
            final var newNodeStakingInfo = StakingNodeInfo.newBuilder()
                    .nodeNumber(nodeId.id())
                    .maxStake(maxStakePerNode)
                    .minStake(0L)
                    .rewardSumHistory(Arrays.asList(rewardSumHistory))
                    .weight(0)
                    .build();
            stakingToState.put(new EntityNumber(nodeId.id()), newNodeStakingInfo);
        }

        // Update all nodes maxStake to maxStakePerNode
        stakingToState.keys().forEachRemaining(key -> {
            final var stakingInfo = stakingToState.get(key);
            final var copy = stakingInfo.copyBuilder().maxStake(maxStakePerNode).build();
            stakingToState.put(key, copy);
        });
    }

    @Override
    public void onNewRecoveredState(@NonNull final MerkleHederaState recoveredState) {
        hedera.onNewRecoveredState(recoveredState);
    }
}
