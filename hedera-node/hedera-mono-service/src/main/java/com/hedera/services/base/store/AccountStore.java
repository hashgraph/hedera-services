/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.base.store;

import com.hedera.services.base.entity.Account;
import com.hedera.services.base.entity.AccountImpl;
import com.hedera.services.base.state.State;
import com.hedera.services.base.state.States;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

import static com.hedera.services.base.state.StateKeys.ACCOUNT_STORE;

/**
 * Provides methods for interacting with the underlying data storage mechanisms for working with Accounts.
 * <p>
 * This class is not exported from the module. It is an internal implementation detail.
 */
public class AccountStore {
	/**
	 * The underlying data storage class that holds the account data.
	 */
	private final State<EntityNum, MerkleAccount> accountState;

	/**
	 * Create a new {@link AccountStore} instance.
	 *
	 * @param states The state to use.
	 */
	public AccountStore(@Nonnull States states) {
		this.accountState = states.get(ACCOUNT_STORE);
		Objects.requireNonNull(accountState);
	}

	public Optional<Account> getAccount(EntityNum id) {
		final var opt = getAccountLeaf(id);
		if (opt.isPresent()) {
			final var account = opt.get();
			return Optional.of(new AccountImpl(
					account.number(),
					Optional.ofNullable(account.getAlias()),
					Optional.ofNullable(account.getAccountKey()),
					account.getExpiry(),
					account.getBalance(),
					Optional.ofNullable(account.getMemo()),
					account.isDeleted(),
					account.isSmartContract(),
					account.isReceiverSigRequired(),
					account.getProxy().num(),
					account.getNftsOwned(),
					account.getMaxAutomaticAssociations(),
					account.getUsedAutoAssociations(),
					account.getNumAssociations(),
					account.getNumPositiveBalances(),
					account.getEthereumNonce(),
					account.getStakedToMe(),
					account.getStakePeriodStart(),
					account.getStakedId(),
					account.isDeclinedReward(),
					account.totalStakeAtStartOfLastRewardedPeriod(),
					account.getAutoRenewAccount().num()));
		}
		return Optional.empty();
	}

	/**
	 * Returns the account leaf for the given account number. If the account doesn't
	 * exist throws {@code NoSuchElementException}
	 * @param accountNumber given account number
	 * @return merkle leaf for the given account number
	 */
	private Optional<MerkleAccount> getAccountLeaf(final EntityNum accountNumber) {
		return accountState.get(accountNumber);
	}
}
