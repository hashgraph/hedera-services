package com.hedera.services.state.merkle.internals;

import java.util.Objects;

public class BlobKey {
	public enum BlobType {
		FILE_DATA, FILE_METADATA, BYTECODE, SYSTEM_DELETION_TIME
	}
	private final BlobType type;
	private final long entityNum;

	public BlobKey(BlobType type, long entityNum) {
		this.type = type;
		this.entityNum = entityNum;
	}

	public BlobType getType() {
		return type;
	}

	public long getEntityNum() {
		return entityNum;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (o == null || BlobKey.class != o.getClass()) {
			return false;
		}
		final var that = (BlobKey) o;
		return this.type == that.type && this.entityNum == that.entityNum;
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, entityNum);
	}

	@Override
	public String toString() {
		return "BlobKey{" +
				"type=" + type +
				", entityNum=" + entityNum +
				'}';
	}
}
