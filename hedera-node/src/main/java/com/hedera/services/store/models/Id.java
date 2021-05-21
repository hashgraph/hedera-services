package com.hedera.services.store.models;

import com.google.common.base.MoreObjects;

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

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || !Id.class.equals(obj.getClass())) {
			return false;
		}
		final Id that = (Id) obj;

		return this.shard == that.shard && this.realm == that.realm && this.num == that.num;
	}

	@Override
	public int hashCode() {
		int result = Long.hashCode(shard);
		result = 31 * result + Long.hashCode(realm);
		return 31 * result + Long.hashCode(num);
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
