package com.hedera.services.ledger;

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

import com.google.common.base.MoreObjects;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;

public class BalanceChange {
	private final Id token;
	private final Id account;
	private final long units;
	private ResponseCodeEnum codeForInsufficientBalance = INSUFFICIENT_ACCOUNT_BALANCE;

	private long newBalance;
	private TokenID explicitTokenId = null;
	private AccountID explicitAccountId = null;

	private BalanceChange(Id token, Id account, long units) {
		this.token = token;
		this.account = account;
		this.units = units;
	}

	public static BalanceChange hbarAdjust(Id account, long units) {
		return new BalanceChange(null, account, units);
	}

	public static BalanceChange tokenAdjust(Id token, Id account, long units) {
		final var tokenChange = new BalanceChange(token, account, units);
		tokenChange.setCodeForInsufficientBalance(INSUFFICIENT_TOKEN_BALANCE);
		return tokenChange;
	}

	public boolean isForHbar() {
		return token == null;
	}

	public Id token() {
		return token;
	}

	public Id account() {
		return account;
	}

	public long units() {
		return units;
	}

	public long getNewBalance() {
		return newBalance;
	}

	public void setNewBalance(long newBalance) {
		this.newBalance = newBalance;
	}

	public TokenID tokenId() {
		return (explicitTokenId != null) ? explicitTokenId : token.asGrpcToken();
	}

	public AccountID accountId() {
		return (explicitAccountId != null) ? explicitAccountId : account.asGrpcAccount();
	}

	public void setExplicitTokenId(TokenID explicitTokenId) {
		this.explicitTokenId = explicitTokenId;
	}

	public void setExplicitAccountId(AccountID explicitAccountId) {
		this.explicitAccountId = explicitAccountId;
	}

	public ResponseCodeEnum codeForInsufficientBalance() {
		return codeForInsufficientBalance;
	}

	public void setCodeForInsufficientBalance(ResponseCodeEnum codeForInsufficientBalance) {
		this.codeForInsufficientBalance = codeForInsufficientBalance;
	}

	/* NOTE: The object methods below are only overridden to improve readability of unit tests;
	this model object is not used in hash-based collections, so the performance of these
	methods doesn't matter. */

	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(BalanceChange.class)
				.add("token", token == null ? "ℏ" : token)
				.add("account", account)
				.add("units", units)
				.toString();
	}
}
