package com.hedera.services.ledger.interceptors;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.ledger.accounts.staking.RewardCalculator.zoneUTC;

public class StakeAwareAccountsCommitsInterceptor extends AccountsCommitInterceptor {
	// Map of changed stakedAccounts and the effective change on its stakedToMe
	private final List<StakeAdjustment> stakeAdjustments;
	private final StakedAccountsAdjustmentsManager stakedAccountsManager;

	private final Supplier<MerkleNetworkContext> networkCtx;
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final RewardCalculator rewardCalculator;
	private boolean rewardsActivated;
	private boolean rewardBalanceChanged;
	private long newRewardBalance;

	private static final Logger log = LogManager.getLogger(AccountsCommitInterceptor.class);

	public StakeAwareAccountsCommitsInterceptor(final SideEffectsTracker sideEffectsTracker,
			final Supplier<MerkleNetworkContext> networkCtx,
			final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo,
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final RewardCalculator rewardCalculator,
			final StakedAccountsAdjustmentsManager manager) {
		super(sideEffectsTracker, networkCtx, rewardCalculator);
		stakeAdjustments = new ArrayList<>();
		stakedAccountsManager = manager;
		this.networkCtx = networkCtx;
		this.dynamicProperties = dynamicProperties;
		this.stakingInfo = stakingInfo;
		this.accounts = accounts;
		this.rewardCalculator = rewardCalculator;
	}

	@Override
	public void preview(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		final var n = pendingChanges.size();
		if (n == 0) {
			return;
		}
		// if the rewards are activated previously they will not be activated again
		rewardsActivated = rewardsActivated || networkCtx.get().areRewardsActivated();
		rewardBalanceChanged = false;

		super.preview(pendingChanges);

		if (shouldActivateStakingRewards()) {
			networkCtx.get().setStakingRewards(true);
			stakingInfo.get().forEach((entityNum, info) -> info.clearRewardSumHistory());

			long todayNumber = LocalDate.now(zoneUTC).toEpochDay();
			accounts.get().forEach(((entityNum, account) -> {
				if (account.getStakedId() < 0) {
					account.setStakePeriodStart(todayNumber);
				}
			}));
			log.info("Staking rewards is activated and rewardSumHistory is cleared");
		}

		stakeAdjustments.clear();

		for (int i = 0; i < n; i++) {
			final var entity = pendingChanges.entity(i);
			final var change = pendingChanges.changes(i);
			if (entity != null && entity.getStakedId() > 0 && change != null) {
				final var stakedId = EntityNum.fromLong(entity.getStakedId());
				if (change.containsKey(AccountProperty.BALANCE)) {
					final long newBalance = (long) change.get(AccountProperty.BALANCE);
					final long adjustment = (entity != null) ? newBalance - entity.getBalance() : newBalance;
					if (shouldCalculateReward(entity)) {
						calculateReward(entity.state().number());
					}
					stakeAdjustments.add(new StakeAdjustment(stakedId, adjustment));
				}

				if (change.containsKey(AccountProperty.STAKED_ID)) {
					final var newStakeId = (long) change.get(AccountProperty.STAKED_ID);
					stakedAccountsManager.updateStakeId(stakeAdjustments, entity, newStakeId);
				}

				if (change.containsKey(AccountProperty.DECLINE_REWARD)) {
					final var declineRewards = (boolean) change.get(AccountProperty.DECLINE_REWARD);
					// todo
				}
			}
		}
	}

	/**
	 * If the balance on 0.0.800 changed in the current transaction and the balance reached above the specified
	 * threshold activates staking rewards
	 *
	 * @return true if rewards should be activated, false otherwise
	 */
	public boolean shouldActivateStakingRewards() {
		return !rewardsActivated && rewardBalanceChanged && (newRewardBalance >= dynamicProperties.getStakingStartThreshold());
	}

	@Override
	public void finalizeSideEffects() {
		stakedAccountsManager.aggregateAndCommitStakeAdjustments(stakeAdjustments);
	}


	/* only used for unit tests */
	public boolean isRewardsActivated() {
		return rewardsActivated;
	}

	public void setRewardsActivated(final boolean rewardsActivated) {
		this.rewardsActivated = rewardsActivated;
	}

	public boolean isRewardBalanceChanged() {
		return rewardBalanceChanged;
	}

	public void setRewardBalanceChanged(final boolean rewardBalanceChanged) {
		this.rewardBalanceChanged = rewardBalanceChanged;
	}

	public long getNewRewardBalance() {
		return newRewardBalance;
	}

	public void setNewRewardBalance(final long newRewardBalance) {
		this.newRewardBalance = newRewardBalance;
	}
}
