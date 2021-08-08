package com.hedera.services.state.submerkle;

import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class FractionalFeeSpec {
	private final long numerator;
	private final long denominator;
	private final long minimumUnitsToCollect;
	private final long maximumUnitsToCollect;

	public FractionalFeeSpec(
			long numerator,
			long denominator,
			long minimumUnitsToCollect,
			long maximumUnitsToCollect
	) {
		if (denominator == 0) {
			throw new IllegalArgumentException("Division by zero is not allowed");
		}
		if (numerator < 0 || denominator < 0 || minimumUnitsToCollect < 0) {
			throw new IllegalArgumentException("Negative values are not allowed");
		}
		if (maximumUnitsToCollect < minimumUnitsToCollect) {
			throw new IllegalArgumentException("maximumUnitsToCollect cannot be less than minimumUnitsToCollect");
		}
		this.numerator = numerator;
		this.denominator = denominator;
		this.minimumUnitsToCollect = minimumUnitsToCollect;
		this.maximumUnitsToCollect = maximumUnitsToCollect;
	}

	public long getNumerator() {
		return numerator;
	}

	public long getDenominator() {
		return denominator;
	}

	public long getMinimumAmount() {
		return minimumUnitsToCollect;
	}

	public long getMaximumUnitsToCollect() {
		return maximumUnitsToCollect;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || !obj.getClass().equals(FractionalFeeSpec.class)) {
			return false;
		}

		final var that = (FractionalFeeSpec) obj;
		return this.numerator == that.numerator &&
				this.denominator == that.denominator &&
				this.minimumUnitsToCollect == that.minimumUnitsToCollect &&
				this.maximumUnitsToCollect == that.maximumUnitsToCollect;
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(FractionalFeeSpec.class)
				.add("numerator", numerator)
				.add("denominator", denominator)
				.add("minimumUnitsToCollect", minimumUnitsToCollect)
				.add("maximumUnitsToCollect", maximumUnitsToCollect)
				.toString();
	}
}
