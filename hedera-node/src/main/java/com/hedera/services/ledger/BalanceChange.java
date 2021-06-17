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
import com.hederahashgraph.api.proto.java.AccountAmount;
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
	static final TokenID NO_TOKEN_FOR_HBAR_ADJUST = TokenID.getDefaultInstance();

	private final Id token;
	private final Id account;
	private final long units;
	private ResponseCodeEnum codeForInsufficientBalance;

	private long newBalance;
	private TokenID tokenId = null;
	private AccountID accountId;

	private BalanceChange(final Id token, final AccountAmount aa, final ResponseCodeEnum code) {
		this.token = token;
		final var account = aa.getAccountID();
		this.accountId = account;
		final var id = Id.fromGrpcAccount(account);
		this.account = id;
		this.units = aa.getAmount();
		this.codeForInsufficientBalance = code;
	}

	public static BalanceChange hbarAdjust(final AccountAmount aa) {
		return new BalanceChange(null, aa, INSUFFICIENT_ACCOUNT_BALANCE);
	}

	public static BalanceChange tokenAdjust(final Id token, final TokenID tokenId, final AccountAmount aa) {
		final var tokenChange = new BalanceChange(token, aa, INSUFFICIENT_TOKEN_BALANCE);
		tokenChange.tokenId = tokenId;
		return tokenChange;
	}

	public boolean isForHbar() {
		return token == null;
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
		return (tokenId != null) ? tokenId : NO_TOKEN_FOR_HBAR_ADJUST;
	}

	public AccountID accountId() {
		return accountId;
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
