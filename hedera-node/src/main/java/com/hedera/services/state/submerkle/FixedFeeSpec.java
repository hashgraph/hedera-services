package com.hedera.services.state.submerkle;

import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Objects;

public class FixedFeeSpec {
	private final long unitsToCollect;
	/* If null, fee is collected in ℏ */
	private final EntityId tokenDenomination;

	public FixedFeeSpec(long unitsToCollect, EntityId tokenDenomination) {
		if (unitsToCollect <= 0) {
			throw new IllegalArgumentException("Only positive values are allowed");
		}
		this.unitsToCollect = unitsToCollect;
		this.tokenDenomination = tokenDenomination;
	}

	public long getUnitsToCollect() {
		return unitsToCollect;
	}

	public EntityId getTokenDenomination() {
		return tokenDenomination;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || !obj.getClass().equals(FixedFeeSpec.class)) {
			return false;
		}

		final var that = (FixedFeeSpec) obj;
		return this.unitsToCollect == that.unitsToCollect &&
				Objects.equals(this.tokenDenomination, that.tokenDenomination);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(FixedFeeSpec.class)
				.add("unitsToCollect", unitsToCollect)
				.add("tokenDenomination", tokenDenomination == null ? "ℏ" : tokenDenomination.toAbbrevString())
				.toString();
	}
}
