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

import com.google.protobuf.ByteString;
import com.hedera.services.base.metadata.TransactionMetadata;
import com.hedera.services.base.state.State;
import com.hedera.services.base.state.States;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Transaction;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

import static com.hedera.services.base.state.StateKeys.ACCOUNT_STORE;
import static com.hedera.services.base.state.StateKeys.ALIASES;
import static com.hedera.services.utils.EntityIdUtils.isAlias;

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
	private final State<ByteString, EntityNum> aliases;

	/**
	 * Create a new {@link AccountStore} instance.
	 *
	 * @param states The state to use.
	 */
	public AccountStore(@Nonnull States states) {
		this.accountState = states.get(ACCOUNT_STORE);
		this.aliases = states.get(ALIASES);
		Objects.requireNonNull(accountState);
		Objects.requireNonNull(aliases);
	}

	public TransactionMetadata createAccountSigningMetadata(final Transaction tx, final AccountID payer){
		final EntityNum accountNum = isAlias(payer) ? aliases.get(payer.getAlias()).get() :
				EntityNum.fromLong(payer.getAccountNum());
		final var merkleAccount = getAccountLeaf(accountNum);
		if (merkleAccount.isPresent()) {
			final var key = merkleAccount.get().getAccountKey();
			return new TransactionMetadata(tx, false, key);
		}
		throw new IllegalArgumentException("Provided account number doesn't exist");
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
