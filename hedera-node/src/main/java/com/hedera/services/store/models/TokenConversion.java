package com.hedera.services.store.models;

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

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.AccountStore;

import javax.annotation.Nullable;

/**
 * Performs MerkleToken {@link MerkleToken}  - Token {@link Token} - MerkleToken {@link MerkleToken} and
 * MerkleUniqueToken {@link MerkleUniqueToken} - UniqueToken {@link UniqueToken} - MerkleUniqueToken {@link MerkleUniqueToken}
 * conversions by preserving all the fields as expected.
 */
public final class TokenConversion {
	private TokenConversion() {
		throw new UnsupportedOperationException("Utility class");
	}

	/**
	 * Returns the token {@link Token} model type based on the merkleToken.
	 *
	 * @param merkleToken
	 * 			immutable MerkleToken object to base the Token model from.
	 * @param id
	 * 			Id of the token.
	 * @param accountStore
	 * 			AccountStore to retrieve Treasury and Autorenew accounts
	 * @return
	 * 			Token Model based on the passed MerkleToken.
	 */
	public static Token fromMerkle(final MerkleToken merkleToken, final Id id, final AccountStore accountStore) {
		final var token = new Token(id);
		initModelAccounts(token, merkleToken.treasury(), merkleToken.autoRenewAccount(), accountStore);
		initModelFields(token, merkleToken);
		return token;
	}

	/**
	 * Returns a MerkleToken {@link MerkleToken} based on the token model passed.
	 *
	 * @param token
	 * 			Token model base to build a MerkleToken from
	 * @return
	 * 			A MerkleToken
	 */
	public static MerkleToken fromToken(final Token token) {
		final var mutableToken = new MerkleToken();
		fromToken(token, mutableToken);
		return mutableToken;
	}

	/**
	 * Populates the mutableToken {@link MerkleToken} using the data from the Token model {@link Token}
	 *
	 * @param token
	 * 			The token model to use
	 * @param mutableToken
	 * 			The mutable MerkleToken to populate the data.
	 */
	public static void fromToken(final Token token, final MerkleToken mutableToken) {
		mutableToken.setExpiry(token.getExpiry());
		mutableToken.setTotalSupply(token.getTotalSupply());
		mutableToken.setDecimals(token.getDecimals());
		mutableToken.setSymbol(token.getSymbol());
		mutableToken.setName(token.getName());
		mutableToken.setAccountsFrozenByDefault(token.isFrozenByDefault());
		mutableToken.setAccountsKycGrantedByDefault(token.isKycGrantedByDefault());
		mutableToken.setTreasury(token.getTreasury().getId().asEntityId());

		final var newAutoRenewAccount = token.getAutoRenewAccount();
		if (newAutoRenewAccount != null) {
			mutableToken.setAutoRenewAccount(new EntityId(newAutoRenewAccount.getId()));
			mutableToken.setAutoRenewPeriod(token.getAutoRenewPeriod());
		}

		mutableToken.setTreasury(new EntityId(token.getTreasury().getId()));
		mutableToken.setTotalSupply(token.getTotalSupply());
		mutableToken.setLastUsedSerialNumber(token.getLastUsedSerialNumber());

		mutableToken.setTokenType(token.getType());
		mutableToken.setSupplyType(token.getSupplyType());

		mutableToken.setMemo(token.getMemo());
		mutableToken.setName(token.getName());
		mutableToken.setSymbol(token.getSymbol());

		mutableToken.setAdminKey(token.getAdminKey());
		mutableToken.setSupplyKey(token.getSupplyKey());
		mutableToken.setWipeKey(token.getWipeKey());
		mutableToken.setFreezeKey(token.getFreezeKey());
		mutableToken.setKycKey(token.getKycKey());
		mutableToken.setFeeScheduleKey(token.getFeeScheduleKey());

		mutableToken.setMaxSupply(token.getMaxSupply());
		mutableToken.setDeleted(token.isDeleted());

		if (token.getCustomFees() != null) {
			mutableToken.setFeeSchedule(token.getCustomFees());
		}

		mutableToken.setExpiry(token.getExpiry());
	}

	/**
	 * Returns a UniqueToken {@link UniqueToken} model using the data from
	 * a immutable MerkleUniqueToken {@link MerkleUniqueToken}
	 *
	 * @param immutableUniqueToken
	 * 			Immutable MerkleUniqueToken object.
	 * @param id
	 * 			The tokenId
	 * @param serialNumber
	 * 			The the serial number of the unique token.
	 * @return
	 * 			The UniqueToken model object with all the data.
	 */
	public static UniqueToken fromMerkleUnique(
			final MerkleUniqueToken immutableUniqueToken,
			final Id id,
			final long serialNumber) {
		final var uniqueToken = new UniqueToken(id, serialNumber);
		uniqueToken.setCreationTime(immutableUniqueToken.getCreationTime());
		uniqueToken.setMetadata(immutableUniqueToken.getMetadata());
		uniqueToken.setOwner(immutableUniqueToken.getOwner().asId());
		return uniqueToken;
	}

	/**
	 * Returns a MerkleUniqueToken {@link MerkleUniqueToken} with data from
	 * the UniqueToken {@link UniqueToken} model
	 *
	 * @param uniqueToken
	 * 			The UniqueToken to base the MerkleUniqueToken on.
	 * @return
	 * 			The created MerkleUniqueToken.
	 */
	public static MerkleUniqueToken fromUniqueToken(final UniqueToken uniqueToken) {
		return new MerkleUniqueToken(
				uniqueToken.getOwner().asEntityId(),
				uniqueToken.getMetadata(),
				uniqueToken.getCreationTime());
	}

	private static void initModelAccounts(
			final Token token,
			final EntityId treasuryId,
			@Nullable final EntityId autoRenewId,
			final AccountStore accountStore) {
		if (autoRenewId != null) {
			final var autoRenew = new Id(autoRenewId.shard(), autoRenewId.realm(), autoRenewId.num());
			final var autoRenewAccount = accountStore.loadAccount(autoRenew);
			token.setAutoRenewAccount(autoRenewAccount);
		}
		final var treasury = new Id(treasuryId.shard(), treasuryId.realm(), treasuryId.num());
		final var treasuryAccount = accountStore.loadAccount(treasury);
		token.setTreasury(treasuryAccount);
	}

	private static void initModelFields(final Token token, final MerkleToken immutableToken) {
		token.setName(immutableToken.name());
		token.setSymbol(immutableToken.symbol());
		token.initTotalSupply(immutableToken.totalSupply());
		token.initSupplyConstraints(immutableToken.supplyType(), immutableToken.maxSupply());
		token.setKycKey(immutableToken.getKycKey());
		token.setFreezeKey(immutableToken.getFreezeKey());
		token.setSupplyKey(immutableToken.getSupplyKey());
		token.setWipeKey(immutableToken.getWipeKey());
		token.setFrozenByDefault(immutableToken.accountsAreFrozenByDefault());
		token.setKycGrantedByDefault(immutableToken.accountsKycGrantedByDefault());
		token.setAdminKey(immutableToken.getAdminKey());
		token.setFeeScheduleKey(immutableToken.getFeeScheduleKey());
		token.setType(immutableToken.tokenType());
		token.setLastUsedSerialNumber(immutableToken.getLastUsedSerialNumber());
		token.setIsDeleted(immutableToken.isDeleted());
		token.setExpiry(immutableToken.expiry());
		token.setMemo(immutableToken.memo());
		token.setAutoRenewPeriod(immutableToken.autoRenewPeriod());
	}
}
