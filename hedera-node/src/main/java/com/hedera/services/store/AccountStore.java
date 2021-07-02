package com.hedera.services.store;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.validation.OptionValidator;
import com.swirlds.fcmap.FCMap;

import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;

public class AccountStore {
	private final OptionValidator validator;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	public AccountStore(
			OptionValidator validator,
			GlobalDynamicProperties dynamicProperties,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts
	) {
		this.validator = validator;
		this.dynamicProperties = dynamicProperties;
		this.accounts = accounts;
	}

	/**
	 * Returns a model of the requested token, with operations that can be used to
	 * implement business logic in a transaction.
	 *
	 * <b>IMPORTANT:</b> Changes to the returned model are not automatically persisted
	 * to state! The altered model must be passed to {@link AccountStore#persistAccount(Account)}
	 * in order for its changes to be applied to the Swirlds state, and included in the
	 * {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} for the active transaction.
	 *
	 * @param id the account to load
	 * @return a usable model of the account
	 * @throws InvalidTransactionException if the requested account is missing, deleted, or expired and pending removal
	 */
	public Account loadAccount(Id id) {
		final var key = new MerkleEntityId(id.getShard(), id.getRealm(), id.getNum());
		final var merkleAccount = accounts.get().get(key);

		validateUsable(merkleAccount);

		final var account = new Account(id);
		account.setExpiry(merkleAccount.getExpiry());
		account.initBalance(merkleAccount.getBalance());
		account.setAssociatedTokens(merkleAccount.tokens().getIds().copy());
		account.setOwnedNfts(merkleAccount.getNftsOwned());

		return account;
	}

	/**
	 * Persists the given account to the Swirlds state, inviting the injected {@link TransactionRecordService}
	 * to update the {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} of the active transaction
	 * with these changes.
	 *
	 * @param account the account to save
	 */
	public void persistAccount(Account account) {
		final var id = account.getId();
		final var key = new MerkleEntityId(id.getShard(), id.getRealm(), id.getNum());

		final var currentAccounts = accounts.get();
		final var mutableAccount = currentAccounts.getForModify(key);
		mutableAccount.tokens().updateAssociationsFrom(account.getAssociatedTokens());
		mutableAccount.setNftsOwned(account.getOwnedNfts());
	}


	private void validateUsable(MerkleAccount merkleAccount) {
		validateTrue(merkleAccount != null, INVALID_ACCOUNT_ID);
		validateFalse(merkleAccount.isDeleted(), ACCOUNT_DELETED);
		if (dynamicProperties.autoRenewEnabled()) {
			if (merkleAccount.getBalance() == 0) {
				final boolean isExpired = validator.isAfterConsensusSecond(merkleAccount.getExpiry());
				validateFalse(isExpired, ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
			}
		}
	}
}
