package com.hedera.services.usage.token;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hedera.services.usage.QueryUsage;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.fee.FeeBuilder;

import java.util.Optional;

import static com.hedera.services.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

public class TokenGetInfoUsage extends QueryUsage {
	private TokenGetInfoUsage(Query query) {
		super(query.getTokenGetInfo().getHeader().getResponseType());
		updateTb(BASIC_ENTITY_ID_SIZE);
		updateRb(TOKEN_ENTITY_SIZES.fixedBytesInTokenRepr());
	}

	public static TokenGetInfoUsage newEstimate(Query query) {
		return new TokenGetInfoUsage(query);
	}

	public TokenGetInfoUsage givenCurrentAdminKey(Optional<Key> adminKey) {
		adminKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateRb);
		return this;
	}

	public TokenGetInfoUsage givenCurrentWipeKey(Optional<Key> wipeKey) {
		wipeKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateRb);
		return this;
	}

	public TokenGetInfoUsage givenCurrentSupplyKey(Optional<Key> supplyKey) {
		supplyKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateRb);
		return this;
	}

	public TokenGetInfoUsage givenCurrentFreezeKey(Optional<Key> freezeKey) {
		freezeKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateRb);
		return this;
	}

	public TokenGetInfoUsage givenCurrentKycKey(Optional<Key> kycKey) {
		kycKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateRb);
		return this;
	}

	public TokenGetInfoUsage givenCurrentMemo(String memo) {
		updateRb(memo.length());
		return this;
	}

	public TokenGetInfoUsage givenCurrentName(String name) {
		updateRb(name.length());
		return this;
	}

	public TokenGetInfoUsage givenCurrentSymbol(String symbol) {
		updateRb(symbol.length());
		return this;
	}

	public TokenGetInfoUsage givenCurrentlyUsingAutoRenewAccount() {
		updateRb(BASIC_ENTITY_ID_SIZE);
		return this;
	}
}
