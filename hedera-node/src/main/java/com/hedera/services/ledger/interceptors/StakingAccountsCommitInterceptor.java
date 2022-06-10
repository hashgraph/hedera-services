package com.hedera.services.ledger.interceptors;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.ledger.accounts.staking.StakeChangeManager;
import com.hedera.services.ledger.accounts.staking.StakeInfoManager;
import com.hedera.services.ledger.accounts.staking.StakePeriodManager;
import com.hedera.services.ledger.accounts.staking.StakingUtils;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;

import static com.hedera.services.ledger.accounts.staking.StakingUtils.NA;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.NOT_REWARDED_SINCE_LAST_STAKING_META_CHANGE;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalBalanceGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalDeclineRewardGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalStakedToMeGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.hasStakeMetaChanges;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.roundedToHbar;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.updateBalance;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.updateStakedToMe;
import static com.hedera.services.ledger.interceptors.StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_ID;
import static com.hedera.services.state.merkle.internals.BitPackUtils.numFromCode;

public class StakingAccountsCommitInterceptor extends AccountsCommitInterceptor {
	private static final int INITIAL_CHANGE_CAPACITY = 32;
	private static final Logger log = LogManager.getLogger(StakingAccountsCommitInterceptor.class);

	private final StakeChangeManager stakeChangeManager;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private final RewardCalculator rewardCalculator;
	private final SideEffectsTracker sideEffectsTracker;
	private final GlobalDynamicProperties dynamicProperties;
	private final StakePeriodManager stakePeriodManager;
	private final StakeInfoManager stakeInfoManager;
	private final AccountNumbers accountNumbers;
	private final TransactionContext txnCtx;

	// The current and new staked ids of the account being processed
	private long curStakedId;
	private long newStakedId;
	// If staking is not activated, the new balance of 0.0.800 after the changes
	private long newFundingBalance;
	// Whether rewards are active
	private boolean rewardsActivated;
	// The rewards earned by accounts in the change set
	private long[] rewardsEarned = new long[INITIAL_CHANGE_CAPACITY];
	// The balance at the start of this reward period for accounts in the change set
	private long[] stakeAtStartOfLastRewardedPeriodUpdates = new long[INITIAL_CHANGE_CAPACITY];
	// The new stakedToMe values of accounts in the change set
	private long[] stakedToMeUpdates = new long[INITIAL_CHANGE_CAPACITY];
	// The new stakePeriodStart values of accounts in the change set
	private long[] stakePeriodStartUpdates = new long[INITIAL_CHANGE_CAPACITY];
	// The stake change scenario for each account in the change set
	private StakeChangeScenario[] stakeChangeScenarios = new StakeChangeScenario[INITIAL_CHANGE_CAPACITY];

	public StakingAccountsCommitInterceptor(
			final SideEffectsTracker sideEffectsTracker,
			final Supplier<MerkleNetworkContext> networkCtx,
			final GlobalDynamicProperties dynamicProperties,
			final RewardCalculator rewardCalculator,
			final StakeChangeManager stakeChangeManager,
			final StakePeriodManager stakePeriodManager,
			final StakeInfoManager stakeInfoManager,
			final AccountNumbers accountNumbers,
			final TransactionContext txnCtx
	) {
		super(sideEffectsTracker);
		this.txnCtx = txnCtx;
		this.networkCtx = networkCtx;
		this.accountNumbers = accountNumbers;
		this.stakeInfoManager = stakeInfoManager;
		this.rewardCalculator = rewardCalculator;
		this.dynamicProperties = dynamicProperties;
		this.stakePeriodManager = stakePeriodManager;
		this.stakeChangeManager = stakeChangeManager;
		this.sideEffectsTracker = sideEffectsTracker;
	}

	@Override
	public void preview(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		prepareAuxiliaryArraysFor(pendingChanges.size());
		// Once rewards are activated, they remain activated
		rewardsActivated = rewardsActivated || networkCtx.get().areRewardsActivated();
		// Only updated and consulted if rewards are not activated
		newFundingBalance = NA;

		updateRewardsAndElections(pendingChanges);
		finalizeStakeMetadata(pendingChanges);
		finalizeRewardBalance(pendingChanges);
		super.preview(pendingChanges);

		if (!rewardsActivated && newFundingBalance >= dynamicProperties.getStakingStartThreshold()) {
			activateStakingRewards();
		}
	}

	@Override
	public void finish(final int i, final MerkleAccount mutableAccount) {
		if (stakedToMeUpdates[i] != NA) {
			System.out.println("Updating 0.0." + mutableAccount.number()
					+ " stakedToMe=" + stakedToMeUpdates[i]);
			mutableAccount.setStakedToMe(stakedToMeUpdates[i]);
		}
		if (stakePeriodStartUpdates[i] != NA) {
			System.out.println("Updating 0.0." + mutableAccount.number()
					+ " stakePeriodStart=" + stakePeriodStartUpdates[i]);
			mutableAccount.setStakePeriodStart(stakePeriodStartUpdates[i]);
		}
		if (stakeAtStartOfLastRewardedPeriodUpdates[i] != NA) {
			System.out.println("Updating 0.0." + mutableAccount.number()
					+ " stakeAtStartOfLastRewardedPeriodUpdates=" + stakeAtStartOfLastRewardedPeriodUpdates[i]);
			mutableAccount.setStakeAtStartOfLastRewardedPeriod(stakeAtStartOfLastRewardedPeriodUpdates[i]);
		}
	}

	/**
	 * Iterates through the change set, maintaining two invariants:
	 * <ol>
	 *    <li>At the beginning of iteration {@code i}, any account in the {@code [0, i)} range that was reward-able
	 *    due to a change in {@code balance}, {@code stakedAccountId}, {@code stakedNodeId}, or {@code declineReward}
	 *    fields has been rewarded.
	 *    <li>Any account whose {@code stakedToMe} balance was affected by one or more changes in the {@code [0, i)}
	 *    range has been, if not already present, added to the {@code pendingChanges}; and its updated {@code stakedToMe}
	 *    is reflected in {@code stakedToMeUpdates}.</li>
	 * </ol>
	 * @param pendingChanges the changes to iterate, preserving the above invariants
	 */
	private void updateRewardsAndElections(
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var origN = pendingChanges.size();
		// We re-compute pendingChanges.size() in the for condition b/c stakeToMe side effects can increase it
		for (int i = 0; i < pendingChanges.size(); i++) {
			final var account = pendingChanges.entity(i);
			final var changes = pendingChanges.changes(i);
			stakeChangeScenarios[i] = scenarioFor(account, changes);

			if (!hasBeenRewarded(i) && isRewardSituation(account, stakedToMeUpdates[i], changes)) {
				payReward(i, account, changes, pendingChanges);
			}

			// If we are outside the original change set, this is a "stakee" account; and its stakedId cannot
			// have changed directly. Furthermore, its balance can only have changed via reward---but if so, it
			// must be staked to a node, and again staked-to-me side effects are impossible
			if (i < origN) {
				updateStakedToMeSideEffects(account, stakeChangeScenarios[i], changes, pendingChanges);
			}
			if (!rewardsActivated && pendingChanges.id(i).getAccountNum() == accountNumbers.stakingRewardAccount()) {
				newFundingBalance = finalBalanceGiven(account, changes);
			}
		}
	}

	private StakeChangeScenario scenarioFor(
			@Nullable final MerkleAccount account,
			@NotNull Map<AccountProperty, Object> changes
	) {
		setCurrentAndNewIds(account, changes);
		return StakeChangeScenario.forCase(curStakedId, newStakedId);
	}

	private void finalizeRewardBalance(
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var rewardsPaid = rewardCalculator.rewardsPaidInThisTxn();
		if (rewardsPaid > 0) {
			final var fundingI = stakeChangeManager.findOrAdd(accountNumbers.stakingRewardAccount(), pendingChanges);
			updateBalance(-rewardsPaid, fundingI, pendingChanges);
		}
	}

	private void finalizeStakeMetadata(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		for (int i = 0, n = pendingChanges.size(); i < n; i++) {
			final var scenario = stakeChangeScenarios[i];
			final var account = pendingChanges.entity(i);
			final var changes = pendingChanges.changes(i);
			setCurrentAndNewIds(account, changes);
			// Because awardStake() and withdrawStake() are very fast, we don't worry about optimizing
			// the FROM_NODE_TO_NODE case with curStakedId == newStakedId, despite how common it is
			if (scenario.withdrawsFromNode()) {
				stakeChangeManager.withdrawStake(
						-curStakedId - 1,
						roundedToHbar(account.getBalance() + account.getStakedToMe()),
						account.isDeclinedReward());
			}
			if (scenario.awardsToNode()) {
				final var stakeToAward = finalBalanceGiven(account, changes)
						+ finalStakedToMeGiven(i, account, stakedToMeUpdates);
				stakeChangeManager.awardStake(
						-newStakedId - 1,
						roundedToHbar(stakeToAward),
						finalDeclineRewardGiven(account, changes));
			}
			final var stakeMetaChanged = hasStakeMetaChanges(changes);
			if (stakeMetaChanged) {
				stakeAtStartOfLastRewardedPeriodUpdates[i] = NOT_REWARDED_SINCE_LAST_STAKING_META_CHANGE;
			} else if (shouldRememberStakeStartFor(account, curStakedId, rewardsEarned[i])) {
//			} else if (rewardsEarned[i] > 0 ||
//						(rewardsEarned[i] == 0 && account.totalStakeAtStartOfLastRewardedPeriod() == -1
//							&& stakePeriodManager.currentStakePeriod() > account.getStakePeriodStart())) {
				stakeAtStartOfLastRewardedPeriodUpdates[i] = account.getBalance() + account.getStakedToMe();
			}
			stakePeriodStartUpdates[i] = stakePeriodManager.startUpdateFor(
					curStakedId, newStakedId, rewardsEarned[i] > 0, stakeMetaChanged);
		}
	}

	@VisibleForTesting
	void updateStakedToMeSideEffects(
			final MerkleAccount account,
			final StakeChangeScenario scenario,
			final Map<AccountProperty, Object> changes,
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		if (scenario == FROM_ACCOUNT_TO_ACCOUNT && curStakedId == newStakedId) {
			final var roundedFinalBalance = roundedToHbar(finalBalanceGiven(account, changes));
			final var roundedInitialBalance = roundedToHbar(account.getBalance());
			// Common case that deserves performance optimization
			final var delta = roundedFinalBalance - roundedInitialBalance;
			alterStakedToMe(curStakedId, delta, pendingChanges);
		} else {
			if (scenario.withdrawsFromAccount()) {
				final var roundedInitialBalance = roundedToHbar(account.getBalance());
				alterStakedToMe(curStakedId, -roundedInitialBalance, pendingChanges);
			}
			if (scenario.awardsToAccount()) {
				final var roundedFinalBalance = roundedToHbar(finalBalanceGiven(account, changes));
				alterStakedToMe(newStakedId, roundedFinalBalance, pendingChanges);
			}
		}
	}

	private void alterStakedToMe(
			final long accountNum,
			final long delta,
			@NotNull final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		if (delta != 0) {
			final var stakeeI = stakeChangeManager.findOrAdd(accountNum, pendingChanges);
			updateStakedToMe(stakeeI, delta, stakedToMeUpdates, pendingChanges);
			if (!hasBeenRewarded(stakeeI)) {
				// If this stakee has already been previewed, and wasn't rewarded, we should
				// re-check if this stakedToMe change has now made it eligible for a reward
				payRewardIfRewardable(stakeeI, pendingChanges);
			}
		}
	}

	private void payRewardIfRewardable(
			final int i,
			@NotNull final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var account = pendingChanges.entity(i);
		final var changes = pendingChanges.changes(i);
		if (isRewardSituation(account, stakedToMeUpdates[i], changes)) {
			payReward(i, account, changes, pendingChanges);
		}
	}

	private void payReward(
			final int i,
			@NotNull MerkleAccount account,
			@NotNull Map<AccountProperty, Object> changes,
			@NotNull final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var reward = rewardCalculator.computePendingReward(account);
		rewardsEarned[i] = reward;

		if (reward > 0) {
			networkCtx.get().decreasePendingRewards(reward);
		} else {
			return;
		}

		var receiverNum = numFromCode(account.number());
		// We cannot reward a deleted account, so keep redirecting to the beneficiaries of deleted
		// accounts until we find a non-deleted account to reward
		if (Boolean.TRUE.equals(changes.get(IS_DELETED))) {
			var j = 1;
			var maxRedirects= txnCtx.numDeletedAccountsAndContracts();
			do {
				if (j++ > maxRedirects)	{
					log.error(
							"With {} accounts deleted, last redirect in {} led to deleted beneficiary 0.0.{}",
							maxRedirects, changes, receiverNum);
					throw new IllegalStateException("Had to redirect reward to a deleted beneficiary");
				}
				receiverNum = txnCtx.getBeneficiaryOfDeleted(receiverNum);
				final var redirectI = stakeChangeManager.findOrAdd(receiverNum, pendingChanges);
				account = pendingChanges.entity(redirectI);
				changes = pendingChanges.changes(redirectI);
			} while (Boolean.TRUE.equals(changes.get(IS_DELETED)));
		}
		// The final beneficiary might still decline the reward
		if (rewardCalculator.applyReward(reward, account, changes)) {
			System.out.println("Paid " + reward + " to account 0.0."
					+ account.getKey().longValue()
					+ " (stakePeriodStart = " + account.getStakePeriodStart() + " -> "
					+ (stakePeriodManager.currentStakePeriod() - 1) + ")");
			sideEffectsTracker.trackRewardPayment(receiverNum, reward);
		}
	}

	@VisibleForTesting
	boolean hasBeenRewarded(final int i) {
		return rewardsEarned[i] != NA;
	}

	/**
	 * Checks if this is a <i>reward situation</i>, in the terminology of HIP-406; please see
	 * <a href="URL#value">https://hips.hedera.com/hip/hip-406</a> for details.
	 *
	 * @param account
	 * 		the account being checked
	 * @param stakedToMeUpdate
	 * 		its new stakedToMe field, or NA if unchanged
	 * @param changes
	 * 		all pending user-controlled property changes
	 * @return true if this is a reward situation, false otherwise
	 */
	@VisibleForTesting
	boolean isRewardSituation(
			@Nullable final MerkleAccount account,
			final long stakedToMeUpdate,
			@NotNull final Map<AccountProperty, Object> changes
	) {
		return account != null
				&& rewardsActivated
				&& account.getStakedId() < 0
				&& (stakedToMeUpdate != NA || changes.containsKey(BALANCE) || hasStakeMetaChanges(changes));
	}

	/**
	 * Checks and activates staking rewards if the staking funding account balance reaches threshold
	 */
	private void activateStakingRewards() {
		long todayNumber = stakePeriodManager.currentStakePeriod();

		networkCtx.get().setStakingRewardsActivated(true);
		stakeInfoManager.clearAllRewardHistory();
		stakeChangeManager.initializeAllStakingStartsTo(todayNumber);
		log.info("Staking rewards are activated and rewardSumHistory arrays for all nodes cleared");
	}

	private void prepareAuxiliaryArraysFor(final int n) {
		// Each pending change could potentially affect stakedToMe of two accounts not yet included in the
		// change set; and if rewards were paid without 0.0.800 in the change set, it will be included too
		final var maxImpliedChanges = 3 * n + 1;
		if (rewardsEarned.length < maxImpliedChanges) {
			rewardsEarned = new long[maxImpliedChanges];
			stakeAtStartOfLastRewardedPeriodUpdates = new long[maxImpliedChanges];
			stakedToMeUpdates = new long[maxImpliedChanges];
			stakeChangeScenarios = new StakeChangeScenario[maxImpliedChanges];
			stakePeriodStartUpdates = new long[maxImpliedChanges];
		}
		Arrays.fill(rewardsEarned, NA);
		Arrays.fill(stakeAtStartOfLastRewardedPeriodUpdates, NA);
		Arrays.fill(stakedToMeUpdates, NA);
		// The stakeChangeScenarios and stakePeriodStartUpdates arrays are filled and used left-to-right only
	}

	private void setCurrentAndNewIds(
			@Nullable final MerkleAccount account,
			@NotNull Map<AccountProperty, Object> changes
	) {
		curStakedId = account == null ? 0L : account.getStakedId();
		newStakedId = (long) changes.getOrDefault(STAKED_ID, curStakedId);
	}
	private boolean shouldRememberStakeStartFor(
			@NotNull final MerkleAccount account,
			final long curStakedId,
			final long reward
	) {
		if (curStakedId >= 0 || account.isDeclinedReward()) {
			// Alice cannot receive a reward for today, so nothing to remember here
			return false;
		}
		if (reward == NA) {
			// Alice's stake could not have changed this transaction, so nothing to remember
			return false;
		} else if (reward > 0) {
			// Alice earned a reward without changing her stake metadata, so she is still eligible
			// for a reward today; since her total stake will change this txn, we remember its
			// current value to reward her correctly for today no matter what happens later on
			return true;
		} else {
			if (account.totalStakeAtStartOfLastRewardedPeriod() != NOT_REWARDED_SINCE_LAST_STAKING_META_CHANGE) {
				// Alice was in a reward situation, but did not earn anything because she already
				// received a reward earlier today; we preserve our memory of her stake from then
				return false;
			}
			// Alice was in a reward situation for the first time today, but received nothing---
			// either because she is staking < 1 hbar, or because she started staking only
			// today or yesterday; we don't care about the exact reason, we just remember
			// her total stake as long as she didn't begin staking today exactly
			return account.getStakePeriodStart() < stakePeriodManager.currentStakePeriod();
		}
	}

	// Only used for unit tests
	@VisibleForTesting
	public void setRewardsActivated(final boolean rewardsActivated) {
		this.rewardsActivated = rewardsActivated;
	}

	@VisibleForTesting
	long[] getStakedToMeUpdates() {
		return stakedToMeUpdates;
	}

	@VisibleForTesting
	StakeChangeScenario[] getStakeChangeScenarios() {
		return stakeChangeScenarios;
	}

	@VisibleForTesting
	void setCurStakedId(long curStakedId) {
		this.curStakedId = curStakedId;
	}

	@VisibleForTesting
	void setNewStakedId(long newStakedId) {
		this.newStakedId = newStakedId;
	}

	@VisibleForTesting
	long[] getStakePeriodStartUpdates() {
		return stakePeriodStartUpdates;
	}

	@VisibleForTesting
	long[] getStakeAtStartOfLastRewardedPeriodUpdates() {
		return stakeAtStartOfLastRewardedPeriodUpdates;
	}

	@VisibleForTesting
	long[] getRewardsEarned() {
		return rewardsEarned;
	}
}
