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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.fcmap.FCMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.annotation.Nullable;

import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;

@Singleton
public class AccountStore {
	private final OptionValidator validator;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	@Inject
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
	 * Returns a model of the requested account, with operations that can be used to
	 * implement business logic in a transaction.
	 *
	 * <b>IMPORTANT:</b> Changes to the returned model are not automatically persisted
	 * to state! The altered model must be passed to {@link AccountStore#persistAccount(Account)}
	 * in order for its changes to be applied to the Swirlds state, and included in the
	 * {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} for the active transaction.
	 *
	 * The method uses the {@link AccountStore#loadAccountOrFailWith(Id, ResponseCodeEnum)} by passing a `null` explicit response code
	 *
	 * @param id the account to load
	 * @return a usable model of the account
	 * @throws InvalidTransactionException if the requested account is missing, deleted, or expired and pending removal
	 */
	public Account loadAccount(Id id) {
		return this.loadAccountOrFailWith(id, null);
	}

	/**
	 * Attempts to load an account from state
	 * and throws the given code if an exception occurs due to an invalid account.
	 *
	 * <b>IMPORTANT:</b> Changes to the returned model are not automatically persisted
	 * to state! The altered model must be passed to {@link AccountStore#persistAccount(Account)}
	 * in order for its changes to be applied to the Swirlds state, and included in the
	 * {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} for the active transaction.
	 *
	 * @param id   the account to load
	 * @param code the {@link ResponseCodeEnum} to fail with if the account is deleted/missing
	 * @return a usable model of the account if available
	 */
	public Account loadAccountOrFailWith(Id id, @Nullable ResponseCodeEnum code) {
		final var key = new MerkleEntityId(id.getShard(), id.getRealm(), id.getNum());
		final var merkleAccount = accounts.get().get(key);

		validateUsable(merkleAccount, code);

		final var account = new Account(id);
		account.setExpiry(merkleAccount.getExpiry());
		account.setBalance(merkleAccount.getBalance());
		account.setAssociatedTokens(merkleAccount.tokens().getIds().copy());
		account.setOwnedNfts(merkleAccount.getNftsOwned());
		account.setMaxAutomaticAssociations(merkleAccount.getMaxAutomaticAssociations());
		account.setAlreadyUsedAutomaticAssociations(merkleAccount.getAlreadyUsedAutoAssociations());
		account.setProxy(merkleAccount.getProxy().asId());
		account.setReceiverSigRequired(merkleAccount.isReceiverSigRequired());
		account.setKey(merkleAccount.getKey());
		account.setMemo(merkleAccount.getMemo());
		account.setAutoRenewSecs(merkleAccount.getAutoRenewSecs());
		account.setDeleted(merkleAccount.isDeleted());
		account.setSmartContract(merkleAccount.isSmartContract());

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

		final var mutableAccount = accounts.get().getForModify(key);
		mapModelToMutable(account, mutableAccount);
		mutableAccount.tokens().updateAssociationsFrom(account.getAssociatedTokens());
	}

	/**
	 * Creates the given {@link Account} to the Swirlds state
	 * @param account the account to create
	 */
	public void persistNew(Account account) {
		final var newMerkleId = account.getId().asMerkle();
		final var mutableAccount = new MerkleAccount();

		mapModelToMutable(account, mutableAccount);
		mutableAccount.setMemo(account.getMemo());
		mutableAccount.setSmartContract(account.isSmartContract());

		accounts.get().put(newMerkleId, mutableAccount);
	}

	private void mapModelToMutable(Account model, MerkleAccount mutableAccount) {
		mutableAccount.setExpiry(model.getExpiry());
		mutableAccount.setBalanceUnchecked(model.getBalance());
		mutableAccount.setNftsOwned(model.getOwnedNfts());
		mutableAccount.setMaxAutomaticAssociations(model.getMaxAutomaticAssociations());
		mutableAccount.setAlreadyUsedAutomaticAssociations(model.getAlreadyUsedAutomaticAssociations());
		mutableAccount.setKey(model.getKey());
		mutableAccount.setProxy(model.getProxy().asEntityId());
		mutableAccount.setReceiverSigRequired(model.isReceiverSigRequired());
		mutableAccount.setDeleted(model.isDeleted());
		mutableAccount.setAutoRenewSecs(model.getAutoRenewSecs());
	}

	private void validateUsable(MerkleAccount merkleAccount, @Nullable ResponseCodeEnum explicitResponse) {
		validateTrue(merkleAccount != null, explicitResponse != null ? explicitResponse : INVALID_ACCOUNT_ID);
		validateFalse(merkleAccount.isDeleted(), explicitResponse != null ? explicitResponse : ACCOUNT_DELETED);
		if (dynamicProperties.autoRenewEnabled()) {
			if (merkleAccount.getBalance() == 0) {
				final boolean isExpired = !validator.isAfterConsensusSecond(merkleAccount.getExpiry());
				validateFalse(isExpired, ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
			}
		}
	}

	public OptionValidator getValidator() {
		return validator;
	}

}
