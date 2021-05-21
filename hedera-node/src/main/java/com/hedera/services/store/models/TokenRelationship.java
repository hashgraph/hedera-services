package com.hedera.services.store.models;

import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class TokenRelationship {
	private final Token token;
	private final Account account;

	private long balance;
	private boolean frozen;
	private boolean kycGranted;

	private long balanceChange = 0L;

	public TokenRelationship(Token token, Account account) {
		this.token = token;
		this.account = account;
	}

	public boolean hasInvolvedIds(Id tokenId, Id accountId) {
		return account.getId().equals(accountId) && token.getId().equals(tokenId);
	}

	public long getBalance() {
		return balance;
	}

	public void initBalance(long balance) {
		this.balance = balance;
	}

	public void setBalance(long balance) {
		balanceChange += (balance - this.balance);
		this.balance = balance;
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

	/* NOTE: The object methods below are only overridden to improve
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
				.add("account", account)
				.add("token", token)
				.add("balance", balance)
				.add("balanceChange", balanceChange)
				.add("frozen", frozen)
				.add("kycGranted", kycGranted)
				.toString();
	}
}
