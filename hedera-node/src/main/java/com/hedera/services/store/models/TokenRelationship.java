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

import com.google.common.base.MoreObjects;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

/**
 * Encapsulates the state and operations of a Hedera account-token relationship.
 *
 * Operations are validated, and throw a {@link com.hedera.services.exceptions.InvalidTransactionException}
 * with response code capturing the failure when one occurs.
 *
 * <b>NOTE:</b> Some operations will likely be moved to specializations
 * of this class as NFTs are fully supported. For example, a
 * {@link TokenRelationship#getBalanceChange()} signature only makes
 * sense for a token of type {@code FUNGIBLE_COMMON}; the analogous signature
 * for a {@code NON_FUNGIBLE_UNIQUE} is {@code getOwnershipChanges())},
 * returning a type that is structurally equivalent to a
 * {@code Pair<long[], long[]>} of acquired and relinquished serial numbers.
 */
public class TokenRelationship {
	private final Token token;
	private final Account account;

	private long balance;
	private boolean frozen;
	private boolean kycGranted;
	private boolean notYetPersisted = true;

	private long balanceChange = 0L;

	public TokenRelationship(Token token, Account account) {
		this.token = token;
		this.account = account;
	}

	public long getBalance() {
		return balance;
	}

	/**
	 * Set the balance of this relationship's token that
	 * the account holds at the beginning of a user transaction. (In particular, does
	 * <b>not</b> change the return value of {@link TokenRelationship#getBalanceChange()}.)
	 *
	 * @param balance
	 * 		the initial balance in the relationship
	 */
	public void initBalance(long balance) {
		this.balance = balance;
	}

	/**
	 * Update the balance of this relationship token held by the account.
	 *
	 * This <b>does</b> change the return value of {@link TokenRelationship#getBalanceChange()}.
	 *
	 * @param balance
	 * 		the updated balance of the relationship
	 */
	public void setBalance(long balance) {
		validateTrue(!token.hasFreezeKey() || !frozen, ACCOUNT_FROZEN_FOR_TOKEN);
		validateTrue(!token.hasKycKey() || kycGranted, ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN);

		balanceChange += (balance - this.balance);
		this.balance = balance;
	}

	private void adjustBalance(long adjustment) {
		balanceChange += adjustment;
		this.balance += adjustment;
	}

	public void validateAndDissociate(final OptionValidator validator,
			TokenRelationship treasuryRelationShip) {
		final var treasury = token.getTreasury();
		validateFalse(!token.isDeleted() && treasury.getId().equals(account.getId()), ACCOUNT_IS_TREASURY);
		validateFalse(!token.isDeleted() && isFrozen(), ACCOUNT_FROZEN_FOR_TOKEN);

		if(balance > 0) {
			final var expiry = Timestamp.newBuilder().setSeconds(token.getExpiry()).build();
			final var isTokenExpired = !validator.isValidExpiry(expiry);
			validateFalse(!token.isDeleted() && !isTokenExpired, TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
			if(!token.isDeleted()) {
				/* Must be expired; return balance to treasury account. */
				treasuryRelationShip.adjustBalance(balance);
				this.adjustBalance(-balance);
			}
		}
	}

	public boolean isFrozen() {
		return frozen;
	}

	public void setFrozen(boolean frozen) {
		this.frozen = frozen;
	}

	public boolean isKycGranted() {
		return kycGranted;
	}

	public void setKycGranted(boolean kycGranted) {
		this.kycGranted = kycGranted;
	}

	public long getBalanceChange() {
		return balanceChange;
	}

	public Token getToken() {
		return token;
	}

	public Account getAccount() {
		return account;
	}

	boolean hasInvolvedIds(Id tokenId, Id accountId) {
		return account.getId().equals(accountId) && token.getId().equals(tokenId);
	}

	public boolean isNotYetPersisted() {
		return notYetPersisted;
	}

	public void setNotYetPersisted(boolean notYetPersisted) {
		this.notYetPersisted = notYetPersisted;
	}

	public boolean hasCommonRepresentation() {
		return token.getType() == TokenType.FUNGIBLE_COMMON;
	}

	public boolean hasUniqueRepresentation() {
		return token.getType() == TokenType.NON_FUNGIBLE_UNIQUE;
	}

	/* The object methods below are only overridden to improve
	readability of unit tests; model objects are not used in hash-based
	collections, so the performance of these methods doesn't matter. */
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
		return MoreObjects.toStringHelper(TokenRelationship.class)
				.add("notYetPersisted", notYetPersisted)
				.add("account", account)
				.add("token", token)
				.add("balance", balance)
				.add("balanceChange", balanceChange)
				.add("frozen", frozen)
				.add("kycGranted", kycGranted)
				.toString();
	}
}
