package com.hedera.services.ledger.accounts.staking;

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

import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Map;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.DECLINE_REWARD;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_ID;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_TO_ME;

public class StakingUtils {

	private StakingUtils() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static long getAccountStakeeNum(@NotNull final Map<AccountProperty, Object> changes) {
		final var entityId = (long) changes.getOrDefault(STAKED_ID, 0L);
		// Node ids are negative and account ids are positive
		return (entityId < 0) ? 0 : entityId;
	}

	public static long getNodeStakeeNum(@NotNull final Map<AccountProperty, Object> changes) {
		final var entityId = (long) changes.getOrDefault(STAKED_ID, 0L);
		// Node ids are negative
		return (entityId < 0) ? entityId : 0;
	}

	public static long finalBalanceGiven(
			@Nullable final MerkleAccount account,
			@NotNull final Map<AccountProperty, Object> changes
	) {
		if (changes.containsKey(BALANCE)) {
			return (long) changes.get(BALANCE);
		} else {
			return (account == null) ? 0 : account.getBalance();
		}
	}

	public static boolean finalDeclineRewardGiven(
			@Nullable final MerkleAccount account,
			@NotNull final Map<AccountProperty, Object> changes
	) {
		if (changes.containsKey(DECLINE_REWARD)) {
			return (Boolean) changes.get(DECLINE_REWARD);
		} else {
			return account != null && account.isDeclinedReward();
		}
	}

	public static long finalStakedToMeGiven(
			@Nullable final MerkleAccount account,
			@NotNull final Map<AccountProperty, Object> changes
	) {
		if (changes.containsKey(STAKED_TO_ME)) {
			return (long) changes.get(STAKED_TO_ME);
		} else {
			return (account == null) ? 0 : account.getStakedToMe();
		}
	}

	public static void updateStakedToMe(
			final int stakeeI,
			final long delta,
			@NotNull final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var mutableChanges = pendingChanges.changes(stakeeI);

		if (mutableChanges.containsKey(STAKED_TO_ME)) {
			mutableChanges.put(STAKED_TO_ME, (long) mutableChanges.get(STAKED_TO_ME) + delta);
		} else {
			final var newStakedToMe = pendingChanges.entity(stakeeI).getStakedToMe() + delta;
			mutableChanges.put(STAKED_TO_ME, newStakedToMe);
		}
	}

	public static void updateBalance(
			final long delta,
			final int rewardAccountI,
			@NotNull final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var mutableChanges = pendingChanges.changes(rewardAccountI);
		if (mutableChanges.containsKey(BALANCE)) {
			mutableChanges.put(BALANCE, (long) mutableChanges.get(BALANCE) + delta);
		} else {
			final var newBalance = pendingChanges.entity(rewardAccountI).getBalance() + delta;
			mutableChanges.put(BALANCE, newBalance);
		}
	}

	public static boolean hasStakeFieldChanges(@NotNull final Map<AccountProperty, Object> changes) {
		return changes.containsKey(BALANCE) ||
				changes.containsKey(DECLINE_REWARD) ||
				changes.containsKey(STAKED_ID) ||
				changes.containsKey(STAKED_TO_ME);
	}

	public static boolean isIncreased(final Map<AccountProperty, Object> changes, final MerkleAccount account) {
		// checks if balance of 0.0.800 increased
		if (changes.containsKey(AccountProperty.BALANCE)) {
			final long newBalance = (long) changes.get(AccountProperty.BALANCE);
			final long currentBalance = account != null ? account.getBalance() : 0;
			return (newBalance - currentBalance) > 0;
		}
		return false;
	}
}
