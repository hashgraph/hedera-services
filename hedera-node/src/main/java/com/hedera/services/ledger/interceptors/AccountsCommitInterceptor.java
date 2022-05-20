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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.CommitInterceptor;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Supplier;

import static com.hedera.services.ledger.accounts.staking.RewardCalculator.zoneUTC;

/**
 * A {@link CommitInterceptor} implementation that tracks the hbar adjustments being committed,
 * and throws {@link IllegalStateException} if the adjustments do not sum to zero.
 *
 * In future work, this interceptor will be extended to capture <i>all</i> externalized side-effects
 * of a transaction on the {@code accounts} ledger, including (for example) approved allowances and
 * new aliases.
 */
public class AccountsCommitInterceptor implements CommitInterceptor<AccountID, MerkleAccount, AccountProperty> {
	private final SideEffectsTracker sideEffectsTracker;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;

	protected boolean rewardsActivated;
	protected boolean rewardBalanceChanged;
	protected long newRewardBalance;

	private static final long STAKING_FUNDING_ACCOUNT_NUMBER = 800L;

	private static final Logger log = LogManager.getLogger(AccountsCommitInterceptor.class);

	public AccountsCommitInterceptor(final SideEffectsTracker sideEffectsTracker,
			final Supplier<MerkleNetworkContext> networkCtx,
			final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo,
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts
	) {
		this.sideEffectsTracker = sideEffectsTracker;
		this.networkCtx = networkCtx;
		this.dynamicProperties = dynamicProperties;
		this.stakingInfo = stakingInfo;
		this.accounts = accounts;
	}

	/**
	 * Accepts a pending change set, including creations. Removals are not supported.
	 *
	 * @throws IllegalStateException
	 * 		if these changes are invalid
	 */
	@Override
	public void preview(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		// if the rewards are activated previously they will not be activated again
		rewardsActivated = rewardsActivated || networkCtx.get().areRewardsActivated();
		rewardBalanceChanged = false;

		for (int i = 0, n = pendingChanges.size(); i < n; i++) {
			trackBalanceChangeIfAny(
					pendingChanges.id(i).getAccountNum(),
					pendingChanges.entity(i),
					pendingChanges.changes(i));
		}
		assertZeroSum();
		activateRewardsIfValid();
	}

	private void activateRewardsIfValid() {
		if (!shouldActivateStakingRewards()) {
			return;
		}
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

	/**
	 * If the balance on 0.0.800 changed in the current transaction and the balance reached above the specified
	 * threshold activates staking rewards
	 *
	 * @return true if rewards should be activated, false otherwise
	 */
	protected boolean shouldActivateStakingRewards() {
		return !rewardsActivated && rewardBalanceChanged && (newRewardBalance >= dynamicProperties.getStakingStartThreshold());
	}

	private void trackBalanceChangeIfAny(
			final long accountNum,
			@Nullable final MerkleAccount merkleAccount,
			@NotNull final Map<AccountProperty, Object> accountChanges
	) {
		if (accountChanges.containsKey(AccountProperty.BALANCE)) {
			final long newBalance = (long) accountChanges.get(AccountProperty.BALANCE);
			if (merkleAccount != null && (accountNum == STAKING_FUNDING_ACCOUNT_NUMBER)) {
				rewardBalanceChanged = true;
				newRewardBalance = newBalance;
			}
			final long adjustment = (merkleAccount != null) ? newBalance - merkleAccount.getBalance() : newBalance;
			sideEffectsTracker.trackHbarChange(accountNum, adjustment);
		}
	}

	private void assertZeroSum() {
		if (sideEffectsTracker.getNetHbarChange() != 0) {
			throw new IllegalStateException("Invalid balance changes");
		}
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
