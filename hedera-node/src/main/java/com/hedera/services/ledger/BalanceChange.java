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

/**
 * Process object that encapsulates a balance change, either ℏ or token unit.
 *
 * Includes an optional override for the {@link ResponseCodeEnum} to be used
 * in the case that the change is determined to result to an insufficient balance;
 * and a field to contain the new balance that will result from the change.
 * (This field is helpful to simplify work done in {@link HederaLedger}.)
 *
 * The {@code explicitTokenId} and {@code explicitAccountId} fields are
 * temporary, needed to interact with the {@link com.hedera.services.ledger.accounts.BackingAccounts}
 * and {@link com.hedera.services.ledger.accounts.BackingTokenRels} components
 * whose APIs still use gRPC types.
 */
public class BalanceChange {
	private final Id token;
	private final Id account;
	private final long units;
	private ResponseCodeEnum codeForInsufficientBalance;

	private long newBalance;
	private TokenID explicitTokenId = null;
	private AccountID explicitAccountId = null;

	private BalanceChange(final Id token, final Id account, final long units, final ResponseCodeEnum code) {
		this.token = token;
		this.account = account;
		this.units = units;
		this.codeForInsufficientBalance = code;
	}

	public static BalanceChange hbarAdjust(final Id account, final long units) {
		return new BalanceChange(null, account, units, INSUFFICIENT_ACCOUNT_BALANCE);
	}

	public static BalanceChange tokenAdjust(final Id token, final Id account, final long units) {
		return new BalanceChange(token, account, units, INSUFFICIENT_TOKEN_BALANCE);
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

	public void setNewBalance(final long newBalance) {
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

	/* NOTE: The object methods below are only overridden to improve readability of unit tests;
	this model object is not used in hash-based collections, so the performance of these
	methods doesn't matter. */

	@Override
	public boolean equals(final Object obj) {
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
