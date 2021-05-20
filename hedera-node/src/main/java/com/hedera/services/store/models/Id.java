package com.hedera.services.store.models;

import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class Id {
	private final long shard;
	private final long realm;
	private final long num;

	public Id(long shard, long realm, long num) {
		this.shard = shard;
		this.realm = realm;
		this.num = num;
	}

	public long getShard() {
		return shard;
	}

	public long getRealm() {
		return realm;
	}

	public long getNum() {
		return num;
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
		return MoreObjects.toStringHelper(Id.class)
				.add("shard", shard)
				.add("realm", realm)
				.add("num", num)
				.toString();
	}
}
