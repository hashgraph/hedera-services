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

import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_INFO_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.Hedera;
import com.hedera.node.app.service.token.TokenService;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.platform.state.MerkleStateLifecycles;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.state.State;
import com.swirlds.state.merkle.disk.OnDiskKey;
import com.swirlds.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements the major lifecycle events for Hedera Services, primarily by delegating to a Hedera instance.
 */
public class MerkleStateLifecyclesImpl implements MerkleStateLifecycles {
    private static final Logger logger = LogManager.getLogger(MerkleStateLifecyclesImpl.class);

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

    public MerkleStateLifecyclesImpl(@NonNull final Hedera hedera) {
        this(hedera, WEIGHT_UPDATE_VISITOR);
    }

    public MerkleStateLifecyclesImpl(
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
    public void initPlatformState(@NonNull final State state) {
        hedera.initPlatformState(state);
    }

    @Override
    public void onPreHandle(@NonNull final Event event, @NonNull final State state) {
        hedera.onPreHandle(event, state);
    }

    @Override
    public void onHandleConsensusRound(@NonNull final Round round, @NonNull final State state) {
        hedera.onHandleConsensusRound(round, state);
    }

    @Override
    public void onSealConsensusRound(@NonNull final Round round, @NonNull final State state) {
        requireNonNull(state);
        requireNonNull(round);
        hedera.onSealConsensusRound(round, state);
    }

    @Override
    public void onStateInitialized(
            @NonNull final State state,
            @NonNull final Platform platform,
            @NonNull final InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {
        hedera.onStateInitialized(state, platform, trigger, previousVersion);
    }

    @Override
    public void onUpdateWeight(
            @NonNull final MerkleStateRoot stateRoot,
            @NonNull final AddressBook configAddressBook,
            @NonNull final PlatformContext context) {
        // Get all nodeIds added in the config.txt
        final var nodeIdsLeftToUpdate = configAddressBook.getNodeIdSet();
        final var stakingInfoIndex = stateRoot.findNodeIndex(TokenService.NAME, STAKING_INFO_KEY);
        if (stakingInfoIndex == -1) {
            logger.warn("Staking info not found in state, skipping weight update");
            return;
        }
        @SuppressWarnings("unchecked")
        final var stakingInfoVMap = (VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>>)
                stateRoot.getChild(stakingInfoIndex);
        // Since it is much easier to modify the in-state staking info after schemas
        // are registered with MerkleStateRoot, we do that work later in the token
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
    }

    @Override
    public void onNewRecoveredState(@NonNull final MerkleStateRoot recoveredStateRoot) {
        hedera.onNewRecoveredState(recoveredStateRoot);
    }
}
