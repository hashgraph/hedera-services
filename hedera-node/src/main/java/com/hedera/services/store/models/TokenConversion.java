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
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.AccountStore;

import javax.annotation.Nullable;

/**
 * Performs MerkleToken {@link MerkleToken}  <-> Token {@link Token} <-> MerkleToken {@link MerkleToken} conversions,
 * preserving all the fields as expected.
 */
public final class TokenConversion {
	private TokenConversion() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static Token FromMerkle(final MerkleToken merkleToken, final Id id, final AccountStore accountStore) {
		var token = new Token(id);
		initModelAccounts(token, merkleToken.treasury(), merkleToken.autoRenewAccount(), accountStore);
		initModelFields(token, merkleToken);
		return token;
	}

	public static void fromToken(Token token, MerkleToken merkleToken) {
		mapModelChangesToMutable(token, merkleToken);
	}


	private static void initModelAccounts(Token token,
			final EntityId _treasuryId,
			@Nullable final EntityId _autoRenewId,
			final AccountStore accountStore) {
		if (_autoRenewId != null) {
			final var autoRenewId = new Id(_autoRenewId.shard(), _autoRenewId.realm(), _autoRenewId.num());
			final var autoRenew = accountStore.loadAccount(autoRenewId);
			token.setAutoRenewAccount(autoRenew);
		}
		final var treasuryId = new Id(_treasuryId.shard(), _treasuryId.realm(), _treasuryId.num());
		final var treasury = accountStore.loadAccount(treasuryId);
		token.setTreasury(treasury);
	}

	private static void initModelFields(Token token, MerkleToken immutableToken) {
		token.initTotalSupply(immutableToken.totalSupply());
		token.initSupplyConstraints(immutableToken.supplyType(), immutableToken.maxSupply());
		token.setKycKey(immutableToken.getKycKey());
		token.setFreezeKey(immutableToken.getFreezeKey());
		token.setSupplyKey(immutableToken.getSupplyKey());
		token.setWipeKey(immutableToken.getWipeKey());
		token.setFrozenByDefault(immutableToken.accountsAreFrozenByDefault());
		token.setAdminKey(immutableToken.getAdminKey());
		token.setFeeScheduleKey(immutableToken.getFeeScheduleKey());
		token.setType(immutableToken.tokenType());
		token.setLastUsedSerialNumber(immutableToken.getLastUsedSerialNumber());
		token.setIsDeleted(immutableToken.isDeleted());
		token.setExpiry(immutableToken.expiry());
		token.setMemo(immutableToken.memo());
		token.setAutoRenewPeriod(immutableToken.autoRenewPeriod());
	}

	private static void mapModelChangesToMutable(Token token, MerkleToken mutableToken) {
		final var newAutoRenewAccount = token.getAutoRenewAccount();
		if (newAutoRenewAccount != null) {
			mutableToken.setAutoRenewAccount(new EntityId(newAutoRenewAccount.getId()));
			mutableToken.setAutoRenewPeriod(token.getAutoRenewPeriod());
		}
		mutableToken.setTreasury(new EntityId(token.getTreasury().getId()));
		mutableToken.setTotalSupply(token.getTotalSupply());
		mutableToken.setAccountsFrozenByDefault(token.isFrozenByDefault());
		mutableToken.setLastUsedSerialNumber(token.getLastUsedSerialNumber());

		mutableToken.setTokenType(token.getType());
		mutableToken.setSupplyType(token.getSupplyType());

		mutableToken.setMemo(token.getMemo());

		mutableToken.setAdminKey(token.getAdminKey());
		mutableToken.setSupplyKey(token.getSupplyKey());
		mutableToken.setWipeKey(token.getWipeKey());
		mutableToken.setFreezeKey(token.getFreezeKey());
		mutableToken.setKycKey(token.getKycKey());
		mutableToken.setFeeScheduleKey(token.getFeeScheduleKey());

		mutableToken.setMaxSupply(token.getMaxSupply());
		mutableToken.setDeleted(token.isDeleted());

		if (token.getCustomFees() != null) {
			mutableToken.setFeeSchedule(token.getCustomFeesAsMerkle());
		}

		mutableToken.setExpiry(token.getExpiry());
	}
}
