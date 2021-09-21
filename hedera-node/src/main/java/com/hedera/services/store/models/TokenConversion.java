/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.store.models;

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.AccountStore;

import javax.annotation.Nullable;

/**
 * Responsible for mapping:
 * - {@link MerkleToken} - {@link Token}
 * - {@link UniqueToken} - {@link MerkleUniqueToken}
 */
public final class TokenConversion {

	private TokenConversion() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	public static void mapMerkleToModel(MerkleToken merkle, Token model) {
		model.initTotalSupply(merkle.totalSupply());
		model.initSupplyConstraints(merkle.supplyType(), merkle.maxSupply());
		model.setKycKey(merkle.getKycKey());
		model.setFreezeKey(merkle.getFreezeKey());
		model.setSupplyKey(merkle.getSupplyKey());
		model.setWipeKey(merkle.getWipeKey());
		model.setFrozenByDefault(merkle.accountsAreFrozenByDefault());
		model.setAdminKey(merkle.getAdminKey());
		model.setFeeScheduleKey(merkle.getFeeScheduleKey());
		model.setType(merkle.tokenType());
		model.setLastUsedSerialNumber(merkle.getLastUsedSerialNumber());
		model.setIsDeleted(merkle.isDeleted());
		model.setExpiry(merkle.expiry());
		model.setMemo(merkle.memo());
		model.setAutoRenewPeriod(merkle.autoRenewPeriod());
		model.setSymbol(merkle.symbol());
		model.setName(merkle.name());
		model.setDecimals(merkle.decimals());
	}

	public static void mapModelToMerkle(Token model, MerkleToken merkle) {
		final var newAutoRenewAccount = model.getAutoRenewAccount();
		if (newAutoRenewAccount != null) {
			merkle.setAutoRenewAccount(new EntityId(newAutoRenewAccount.getId()));
			merkle.setAutoRenewPeriod(model.getAutoRenewPeriod());
		}
		merkle.setTreasury(new EntityId(model.getTreasury().getId()));
		merkle.setTotalSupply(model.getTotalSupply());
		merkle.setAccountsFrozenByDefault(model.isFrozenByDefault());
		merkle.setLastUsedSerialNumber(model.getLastUsedSerialNumber());

		merkle.setTokenType(model.getType());
		merkle.setSupplyType(model.getSupplyType());

		merkle.setMemo(model.getMemo());

		merkle.setAdminKey(model.getAdminKey());
		merkle.setSupplyKey(model.getSupplyKey());
		merkle.setWipeKey(model.getWipeKey());
		merkle.setFreezeKey(model.getFreezeKey());
		merkle.setKycKey(model.getKycKey());
		merkle.setFeeScheduleKey(model.getFeeScheduleKey());
		merkle.setSymbol(model.getSymbol());
		merkle.setName(model.getName());
		merkle.setExpiry(model.getExpiry());
		merkle.setDecimals(model.getDecimals());

		merkle.setMaxSupply(model.getMaxSupply());
		merkle.setDeleted(model.isDeleted());

		if (model.getCustomFees() != null) {
			merkle.setFeeSchedule(model.getCustomFees());
		}

		merkle.setExpiry(model.getExpiry());
	}

	public static void mapMerkleAccountsToModel(Token token, EntityId treasuryID, @Nullable EntityId autoRenewID, AccountStore accountStore) {
		if (autoRenewID != null) {
			final var autoRenewId = new Id(autoRenewID.shard(), autoRenewID.realm(), autoRenewID.num());
			final var autoRenew = accountStore.loadAccount(autoRenewId);
			token.setAutoRenewAccount(autoRenew);
		}
		final var treasuryId = new Id(treasuryID.shard(), treasuryID.realm(), treasuryID.num());
		final var treasury = accountStore.loadAccount(treasuryId);
		token.setTreasury(treasury);
	}

	public static void mapUniqueTokenModelFields(UniqueToken model, MerkleUniqueToken merkle) {
		model.setCreationTime(merkle.getCreationTime());
		model.setMetadata(merkle.getMetadata());
		model.setOwner(merkle.getOwner().asId());
	}
}
