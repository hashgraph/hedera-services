package com.hedera.services.state.submerkle;

import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Objects;

public class RoyaltyFeeSpec {
	private final long numerator;
	private final long denominator;
	private final FixedFeeSpec fallbackFee;

	public RoyaltyFeeSpec(long numerator, long denominator, FixedFeeSpec fallbackFee) {
		if (denominator <= 0 || numerator < 0 || numerator > denominator) {
			throw new IllegalArgumentException(
					"Cannot build royalty fee with args numerator='" + numerator
							+ "', denominator='" + denominator + "'");
		}
		this.numerator = numerator;
		this.denominator = denominator;
		this.fallbackFee = fallbackFee;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || !obj.getClass().equals(RoyaltyFeeSpec.class)) {
			return false;
		}

		final var that = (RoyaltyFeeSpec) obj;
		return this.numerator == that.numerator &&
				this.denominator == that.denominator &&
				Objects.equals(this.fallbackFee, that.fallbackFee);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(RoyaltyFeeSpec.class)
				.add("numerator", numerator)
				.add("denominator", denominator)
				.add("fallbackFee", fallbackFee)
				.toString();
	}

	public long getNumerator() {
		return numerator;
	}

	public long getDenominator() {
		return denominator;
	}

	public FixedFeeSpec getFallbackFee() {
		return fallbackFee;
	}
}
