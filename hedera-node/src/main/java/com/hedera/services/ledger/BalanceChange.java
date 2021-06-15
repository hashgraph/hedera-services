package com.hedera.services.ledger;

import com.google.common.base.MoreObjects;
import com.hedera.services.store.models.Id;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class BalanceChange {
	private final Id token;
	private final Id account;
	private final long units;

	private BalanceChange(Id token, Id account, long units) {
		this.token = token;
		this.account = account;
		this.units = units;
	}

	public static BalanceChange hbarAdjust(Id account, long units) {
		return new BalanceChange(null, account, units);
	}

	public static BalanceChange tokenAdjust(Id token, Id account, long units) {
		return new BalanceChange(token, account, units);
	}

	public boolean isForHbar() {
		throw new AssertionError("Not implemented!");
	}

	/* NOTE: The object methods below are only overridden to improve
	readability of unit tests; this model object is not used in hash-based
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
		return MoreObjects.toStringHelper(BalanceChange.class)
				.add("token", token == null ? "ℏ" : token)
				.add("account", account)
				.add("units", units)
				.toString();
	}
}
