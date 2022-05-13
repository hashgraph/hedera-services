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
import com.hedera.services.state.logic.NetworkCtxManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

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
	private final NetworkCtxManager networkCtxManager;
	private final GlobalDynamicProperties dynamicProperties;
	private boolean rewardsActivated;
	private boolean rewardBalanceChanged;
	private long newRewardBalance;

	private static final long STAKING_FUNDING_ACCOUNT_NUMBER = 800L;

	public AccountsCommitInterceptor(final SideEffectsTracker sideEffectsTracker,
			final NetworkCtxManager networkCtxManager,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.sideEffectsTracker = sideEffectsTracker;
		this.networkCtxManager = networkCtxManager;
		this.dynamicProperties = dynamicProperties;
	}

	/**
	 * Accepts a pending change set, including creations. Removals are not supported.
	 *
	 * @throws IllegalStateException
	 * 		if these changes are invalid
	 */
	@Override
	public void preview(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		rewardsActivated = rewardsActivated || networkCtxManager.areRewardsActivated();
		rewardBalanceChanged = false;

		for (int i = 0, n = pendingChanges.size(); i < n; i++) {
			trackBalanceChangeIfAny(
					pendingChanges.id(i).getAccountNum(),
					pendingChanges.entity(i),
					pendingChanges.changes(i));
		}
		assertZeroSum();
		if (!rewardsActivated && rewardBalanceChanged) {
			if (newRewardBalance >= dynamicProperties.getStakingStartThreshold()) {
				networkCtxManager.permanentlyActivateStakingRewards();
			}
		}
	}

	private void trackBalanceChangeIfAny(
			final long accountNum,
			@Nullable final MerkleAccount merkleAccount,
			@NotNull final Map<AccountProperty, Object> accountChanges
	) {
		if (accountChanges.containsKey(AccountProperty.BALANCE)) {
			final long newBalance = (long) accountChanges.get(AccountProperty.BALANCE);
			if (merkleAccount.state().number() == STAKING_FUNDING_ACCOUNT_NUMBER) {
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
}
