package com.hedera.services.store.tokens.views.internals;

import com.hedera.services.utils.MiscUtils;

/**
 * An integer whose {@code hashCode()} implementation vastly reduces
 * the risk of hash collisions in structured data using this type,
 * when compared to the {@code java.lang.Integer} boxed type.
 *
 * May no longer be necessary after {@link com.swirlds.fchashmap.FCOneToManyRelation}
 * improves internal hashing.
 */
public class PermHashInteger {
	private final int value;

	public PermHashInteger(int value) {
		this.value = value;
	}

	public static PermHashInteger asPhi(int i) {
		return new PermHashInteger(i);
	}

	@Override
	public int hashCode() {
		return (int) MiscUtils.perm64(value);
	}

	public int getValue() {
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || PermHashInteger.class != o.getClass()) {
			return false;
		}

		var that = (PermHashInteger) o;

		return this.value == that.value;
	}
}
