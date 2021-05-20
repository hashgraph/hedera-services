package com.hedera.services.store.models;

import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class Token {
	private final Id id;

	private boolean supplyHasChanged;

	private long totalSupply;
	private Account treasury;
	private Account autoRenewAccount;

	public Token(Id id) {
		this.id = id;
	}

	public Account getTreasury() {
		return treasury;
	}

	public void setTreasury(Account treasury) {
		this.treasury = treasury;
	}

	public Account getAutoRenewAccount() {
		return autoRenewAccount;
	}

	public void setAutoRenewAccount(Account autoRenewAccount) {
		this.autoRenewAccount = autoRenewAccount;
	}

	public long getTotalSupply() {
		return totalSupply;
	}

	public void setTotalSupply(long totalSupply) {
		supplyHasChanged = true;
		this.totalSupply = totalSupply;
	}

	public boolean hasChangedSupply() {
		return supplyHasChanged;
	}

	public Id getId() {
		return id;
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
		return MoreObjects.toStringHelper(Token.class)
				.add("id", id)
				.add("treasury", treasury)
				.add("autoRenewAccount", autoRenewAccount)
				.toString();
	}
}
