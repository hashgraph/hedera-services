package com.hedera.services.ledger.accounts.staking;

import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.services.context.properties.PropertyNames.STAKING_STARTUP_HELPER_RECOMPUTE;
import static com.hedera.services.ledger.accounts.staking.StakeStartupHelper.RecomputeType.NODE_STAKES;
import static com.hedera.services.ledger.accounts.staking.StakeStartupHelper.RecomputeType.PENDING_REWARDS;
import static com.hedera.services.utils.MiscUtils.forEach;
import static com.hedera.services.utils.MiscUtils.withLoggedDuration;

/**
 * A helper class to recompute staking metadata at startup. Its main work is to
 * update the staking info map to reflect any changes to the address book since the
 * last restart.
 *
 * <p>However, if a bug has corrupted a piece of staking metadata (e.g., the pending
 * rewards), this class can also be used to recompute that metadata based on the value
 * of the {@code staking.startupHelper.recompute} property.
 *
 * <p>Such re-computations are done <b>only</b> after an upgrade, since it is never
 * safe to "undo" the effects of a bug on state during reconnect. (Nodes that never
 * fell out of sync will not get this opportunity!)
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
    private final StakePeriodManager stakePeriodManager;
    private final RewardCalculator rewardCalculator;

    @Inject
    public StakeStartupHelper(
            final StakeInfoManager stakeInfoManager,
            final @CompositeProps PropertySource properties,
            final StakePeriodManager stakePeriodManager,
            final RewardCalculator rewardCalculator) {
        this.properties = properties;
        this.stakeInfoManager = stakeInfoManager;
        this.stakePeriodManager = stakePeriodManager;
        this.rewardCalculator = rewardCalculator;
    }

    /**
     * Given the current address book and a mutable staking info map, updates the map
     * to have exactly one entry for each node id in the address book. This could both
     * remove and add entries to the map.
     *
     * @param addressBook the current address book
     * @param stakingInfos the mutable staking info map
     */
    public void doRestartHousekeeping(
            final AddressBook addressBook,
            final MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos) {
        // List the node ids in the staking info map from BEFORE the restart
        final List<Long> preUpgradeNodeIds = stakingInfos.keySet().stream().map(EntityNum::longValue).toList();
        // List the node ids in the address book from AFTER the restart
        final List<Long> postUpgradeNodeIds = idsFromAddressBook(addressBook);

        // Always update the staking infos map with the new node ids
        updateInfosForAddedOrRemovedNodes(preUpgradeNodeIds, postUpgradeNodeIds, stakingInfos);
        // Always prepare the stake info manager for managing the new node ids
        stakeInfoManager.prepForManaging(postUpgradeNodeIds);
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
            final MerkleMap<EntityNum, MerkleAccount> accounts,
            final MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos) {

        // Recompute anything requested by the staking.startupHelper.recompute property
        final var recomputeTypes =
                properties.getRecomputeTypesProperty(STAKING_STARTUP_HELPER_RECOMPUTE);
        if (!recomputeTypes.isEmpty()) {
            withLoggedDuration(
                    () -> recomputeQuantities(
                            recomputeTypes.contains(NODE_STAKES),
                            recomputeTypes.contains(PENDING_REWARDS),
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
            addedNodeIds.forEach(id -> stakingInfos.put(
                    EntityNum.fromLong(id),
                    new MerkleStakingInfo(numRewardablePeriods)));
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
            final MerkleMap<EntityNum, MerkleAccount> accounts,
            final MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos) {

        final AtomicLong newPendingRewards = new AtomicLong();
        final Map<EntityNum, Long> newStakeStarts = new HashMap<>();
        final Map<EntityNum, Long> newStakesToReward = new HashMap<>();
        final Map<EntityNum, Long> newStakesToNotReward = new HashMap<>();
        final Map<EntityNum, Long> newStakeRewardStarts = new HashMap<>();

        accounts.forEach((num, account) -> {
            if (account.getStakedId() < 0) {
                if (doPendingRewards) {
                    final var actualPending = rewardCalculator.computePendingReward(account);
                    newPendingRewards.addAndGet(actualPending);
                }
                if (doNodeStakes) {
                    updateForNodeStaked(
                            account, newStakesToReward, newStakesToNotReward, newStakeRewardStarts, newStakeStarts);
                }
            }
        });

        if (doPendingRewards) {
            networkContext.setPendingRewards(newPendingRewards.get());
        }

        if (doNodeStakes) {
            final var networkStakeStart = new AtomicLong();
            final var networkStakeRewardStart = new AtomicLong();
            forEach(stakingInfos, (num, info) -> {
                final var mutableInfo = stakingInfos.getForModify(num);
                final var nodeStakeStart = newStakeStarts.getOrDefault(num, 0L);
                final var nodeStakeRewardStart = newStakeRewardStarts.getOrDefault(num, 0L);
                mutableInfo.syncRecomputedStakeValues(
                        newStakesToReward.getOrDefault(num, 0L),
                        newStakesToNotReward.getOrDefault(num, 0L),
                        nodeStakeRewardStart);
                networkStakeStart.addAndGet(nodeStakeStart);
                networkStakeRewardStart.addAndGet(nodeStakeRewardStart);
            });
            networkContext.setTotalStakedStart(networkStakeStart.get());
            networkContext.setTotalStakedRewardStart(networkStakeRewardStart.get());
        }
    }

    private void updateForNodeStaked(
            final MerkleAccount account,
            final Map<EntityNum, Long> newStakesToReward,
            final Map<EntityNum, Long> newStakesToNotReward,
            final Map<EntityNum, Long> newStakeRewardStarts,
            final Map<EntityNum, Long> newStakeStarts) {
        final var nodeNum = EntityNum.fromLong(account.getStakedNodeAddressBookId());
        final var stake = StakingUtils.roundedToHbar(account.totalStake());
        final var wasStakedAtStartOfPeriod = account.getStakePeriodStart() < stakePeriodManager.currentStakePeriod();
        if (account.isDeclinedReward()) {
            newStakesToNotReward.merge(nodeNum, stake, Long::sum);
        } else {
            newStakesToReward.merge(nodeNum, stake, Long::sum);
            if (wasStakedAtStartOfPeriod) {
                newStakeRewardStarts.merge(nodeNum, stake, Long::sum);
            }
        }
        if (wasStakedAtStartOfPeriod) {
            newStakeStarts.merge(nodeNum, stake, Long::sum);
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
