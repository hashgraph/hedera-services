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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.ledger.accounts.staking.RewardCalculator.stakingFundAccount;
import static com.hedera.services.ledger.accounts.staking.RewardCalculator.zoneUTC;

public class StakeAwareAccountsCommitsInterceptor extends AccountsCommitInterceptor {
	private final StakeChangeManager stakeChangeManager;

	private final SideEffectsTracker sideEffectsTracker;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final RewardCalculator rewardCalculator;

	private boolean rewardsActivated;
	private boolean rewardBalanceChanged;
	private long newRewardBalance;
	private Set<Long> rewardsPaidAccounts;

	private static final Logger log = LogManager.getLogger(AccountsCommitInterceptor.class);
	public static final long STAKING_FUNDING_ACCOUNT_NUMBER = 800L;

	public StakeAwareAccountsCommitsInterceptor(
			final SideEffectsTracker sideEffectsTracker,
			final Supplier<MerkleNetworkContext> networkCtx,
			final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo,
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final RewardCalculator rewardCalculator,
			final StakeChangeManager manager) {
		super(sideEffectsTracker);
		stakeChangeManager = manager;
		this.networkCtx = networkCtx;
		this.dynamicProperties = dynamicProperties;
		this.stakingInfo = stakingInfo;
		this.accounts = accounts;
		this.rewardCalculator = rewardCalculator;
		this.sideEffectsTracker = sideEffectsTracker;
		this.rewardsPaidAccounts = new HashSet<>();
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
		reset();

		for (int i = 0; i < n; i++) {
			final var entity = pendingChanges.entity(i);
			final var change = pendingChanges.changes(i);
			final var accountNum = pendingChanges.id(i).getAccountNum();

			if (entity != null && change != null) {
				balanceChanges(change, entity);
				stakedIdChanges(change, entity);

				if (isRewardPaid(accountNum) && stakingFieldsChanged(change) && shouldCalculateReward(entity)) {
					calculateReward(accountNum);
				}
			}
		}

		checkRewardActivation();
	}

	private boolean isRewardPaid(final long accountNum) {
		return rewardsPaidAccounts.contains(accountNum);
	}

	private boolean stakingFieldsChanged(final Map<AccountProperty, Object> change) {
		return change.containsKey(AccountProperty.STAKED_ID) ||
				change.containsKey(AccountProperty.DECLINE_REWARD) ||
				change.containsKey(AccountProperty.BALANCE);
	}

	private void stakedIdChanges(final Map<AccountProperty, Object> change, final MerkleAccount entity) {
		if (change.containsKey(AccountProperty.STAKED_ID)) {
			final var newStakeId = (long) change.get(AccountProperty.STAKED_ID);
			stakeChangeManager.recordStakeChanges(entity, newStakeId);
		}
	}

	private void balanceChanges(final Map<AccountProperty, Object> change,
			final MerkleAccount entity) {
		if (change.containsKey(AccountProperty.BALANCE)) {
			final long newBalance = (long) change.get(AccountProperty.BALANCE);
			final long accountNum = entity.state().number();

			trackBalanceChangeIfAny(accountNum, entity, change);
			if (entity != null && (accountNum == STAKING_FUNDING_ACCOUNT_NUMBER)) {
				rewardBalanceChanged = true;
				newRewardBalance = newBalance;
			}
		}
	}

	private void checkRewardActivation() {
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

	void calculateReward(final long accountNum) {
		final long reward = rewardCalculator.computeAndApplyRewards(EntityNum.fromLong(accountNum));

		if (reward > 0) {
			sideEffectsTracker.trackHbarChange(accountNum, reward);
			sideEffectsTracker.trackHbarChange(stakingFundAccount.longValue(), -reward);
			rewardsPaidAccounts.add(accountNum);
		}

		sideEffectsTracker.trackRewardPayment(accountNum, reward);
	}

	boolean shouldCalculateReward(final MerkleAccount account) {
		return account != null && account.getStakedId() < 0 && networkCtx.get().areRewardsActivated();
	}

	@Override
	public void finalizeSideEffects() {
		stakeChangeManager.aggregateAndCommitStakeAdjustments(sideEffectsTracker.getNetTrackedHbarChanges());
	}

	public void reset() {
		rewardsPaidAccounts.clear();
	}
}
