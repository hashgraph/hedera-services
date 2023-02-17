/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.ledger.accounts.staking;

import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_STARTUP_HELPER_RECOMPUTE;
import static com.hedera.node.app.service.mono.utils.MiscUtils.forEach;
import static com.hedera.node.app.service.mono.utils.MiscUtils.withLoggedDuration;

import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A helper class to recompute staking metadata at startup. Its main work is to update the staking
 * info map to reflect any changes to the address book since the last restart.
 *
 * <p>However, if a bug has corrupted a piece of staking metadata (e.g., the pending rewards), this
 * class can also be used to recompute that metadata based on the value of the {@code
 * staking.startupHelper.recompute} property.
 *
 * <p>Such re-computations are done <b>only</b> after an upgrade, since it is never safe to "undo"
 * the effects of a bug on state during reconnect. (Nodes that never fell out of sync will not get
 * this opportunity!)
 */
@Singleton
public class StakeStartupHelper {
    private static final Logger log = LogManager.getLogger(StakeStartupHelper.class);

    public enum RecomputeType {
        NODE_STAKES,
        PENDING_REWARDS
    }

    private final PropertySource properties;
    private final StakeInfoManager stakeInfoManager;
    private final RewardCalculator rewardCalculator;

    @Inject
    public StakeStartupHelper(
            final StakeInfoManager stakeInfoManager,
            final @CompositeProps PropertySource properties,
            final RewardCalculator rewardCalculator) {
        this.properties = properties;
        this.stakeInfoManager = stakeInfoManager;
        this.rewardCalculator = rewardCalculator;
    }

    /**
     * Given the genesis address book, prepares the network's staking infrastructure to work with
     * this address book.
     *
     * <p><b>FUTURE WORK:</b> Update this method to also accept the genesis staking infos map and do
     * the {@code createGenesisChildren()} work currently still done by {@link
     * com.hedera.node.app.service.mono.state.migration.StakingInfoMapBuilder}.
     *
     * @param addressBook the genesis address book
     */
    public void doGenesisHousekeeping(final AddressBook addressBook) {
        // List the node ids in the address book at genesis
        final List<Long> genesisNodeIds = idsFromAddressBook(addressBook);

        // Prepare the stake info manager for managing the new node ids
        stakeInfoManager.prepForManaging(genesisNodeIds);
    }

    /**
     * Given the current address book and a mutable staking info map, updates the map to have
     * exactly one entry for each node id in the address book. This could both remove and add
     * entries to the map.
     *
     * @param addressBook the current address book
     * @param stakingInfos the mutable staking info map
     */
    public void doRestartHousekeeping(
            final AddressBook addressBook, final MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos) {
        // List the node ids in the staking info map from BEFORE the restart
        final List<Long> preRestartNodeIds =
                stakingInfos.keySet().stream().map(EntityNum::longValue).toList();
        // List the node ids in the address book from AFTER the restart
        final List<Long> postRestartNodeIds = idsFromAddressBook(addressBook);

        // Always update the staking infos map with the new node ids
        updateInfosForAddedOrRemovedNodes(preRestartNodeIds, postRestartNodeIds, stakingInfos);
        // Always prepare the stake info manager for managing the new node ids
        stakeInfoManager.prepForManaging(postRestartNodeIds);
    }

    /**
     * Given a mutable accounts map, staking info map, and network context, re-computes any
     * requested staking metadata based on the value of the {@code staking.startupHelper.recompute}
     * property.
     *
     * @param networkContext the mutable network context
     * @param accounts the mutable accounts map
     * @param stakingInfos the mutable staking info map
     */
    public void doUpgradeHousekeeping(
            final MerkleNetworkContext networkContext,
            final AccountStorageAdapter accounts,
            final MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos) {

        // Recompute anything requested by the staking.startupHelper.recompute property
        final var recomputeTypes = properties.getRecomputeTypesProperty(STAKING_STARTUP_HELPER_RECOMPUTE);
        if (!recomputeTypes.isEmpty()) {
            withLoggedDuration(
                    () -> recomputeQuantities(
                            recomputeTypes.contains(RecomputeType.NODE_STAKES),
                            recomputeTypes.contains(RecomputeType.PENDING_REWARDS),
                            networkContext,
                            accounts,
                            stakingInfos),
                    log,
                    "Recomputing " + recomputeTypes);
        }
    }

    private void updateInfosForAddedOrRemovedNodes(
            final List<Long> preUpgradeNodeIds,
            final List<Long> postUpgradeNodeIds,
            final MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos) {
        // Add staking info for nodes that are new in the address book
        final List<Long> addedNodeIds = orderedSetMinus(postUpgradeNodeIds, preUpgradeNodeIds);
        if (!addedNodeIds.isEmpty()) {
            final var numRewardablePeriods = properties.getIntProperty(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS);
            log.info("Adding staking info for new node ids: {}", addedNodeIds);
            addedNodeIds.forEach(
                    id -> stakingInfos.put(EntityNum.fromLong(id), new MerkleStakingInfo(numRewardablePeriods)));
        }

        // Remove any staking info for nodes that are no longer in the address book
        final List<Long> removedNodeIds = orderedSetMinus(preUpgradeNodeIds, postUpgradeNodeIds);
        if (!removedNodeIds.isEmpty()) {
            log.info("Removing staking info for missing node ids: {}", removedNodeIds);
            removedNodeIds.forEach(id -> stakingInfos.remove(EntityNum.fromLong(id)));
        }
    }

    private void recomputeQuantities(
            final boolean doNodeStakes,
            final boolean doPendingRewards,
            final MerkleNetworkContext networkContext,
            final AccountStorageAdapter accounts,
            final MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos) {

        final AtomicLong newPendingRewards = new AtomicLong();
        final Map<EntityNum, Long> newStakesToReward = new HashMap<>();
        final Map<EntityNum, Long> newStakesToNotReward = new HashMap<>();

        accounts.forEach((num, account) -> {
            if (!account.isDeleted() && account.getStakedId() < 0) {
                if (doPendingRewards) {
                    final var truePending = rewardCalculator.estimatePendingRewards(
                            account, stakingInfos.get(EntityNum.fromLong(account.getStakedNodeAddressBookId())));
                    newPendingRewards.addAndGet(truePending);
                }
                if (doNodeStakes) {
                    updateForNodeStaked(account, newStakesToReward, newStakesToNotReward);
                }
            }
        });

        if (doPendingRewards) {
            final var recomputedPending = newPendingRewards.get();
            if (recomputedPending != networkContext.pendingRewards()) {
                log.warn(
                        "Pending rewards were recomputed from {} to {}",
                        networkContext.pendingRewards(),
                        recomputedPending);
            }
            networkContext.setPendingRewards(newPendingRewards.get());
        }

        if (doNodeStakes) {
            forEach(stakingInfos, (num, info) -> {
                final var mutableInfo = stakingInfos.getForModify(num);
                mutableInfo.syncRecomputedStakeValues(
                        newStakesToReward.getOrDefault(num, 0L), newStakesToNotReward.getOrDefault(num, 0L));
            });
        }
    }

    private void updateForNodeStaked(
            final HederaAccount account,
            final Map<EntityNum, Long> newStakesToReward,
            final Map<EntityNum, Long> newStakesToNotReward) {
        final var nodeNum = EntityNum.fromLong(account.getStakedNodeAddressBookId());
        final var stake = StakingUtils.roundedToHbar(account.totalStake());
        if (account.isDeclinedReward()) {
            newStakesToNotReward.merge(nodeNum, stake, Long::sum);
        } else {
            newStakesToReward.merge(nodeNum, stake, Long::sum);
        }
    }

    private List<Long> orderedSetMinus(final List<Long> a, final List<Long> b) {
        final Set<Long> result = new HashSet<>(a);
        b.forEach(result::remove);
        return new ArrayList<>(result).stream().sorted().toList();
    }

    private List<Long> idsFromAddressBook(final AddressBook addressBook) {
        final List<Long> nodeIds = new ArrayList<>();
        for (int i = 0, n = addressBook.getSize(); i < n; i++) {
            final long id = addressBook.getId(i);
            nodeIds.add(id);
        }
        return nodeIds;
    }
}
