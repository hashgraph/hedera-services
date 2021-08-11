package com.hedera.services.state.submerkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class FractionalFeeSpec {
	private final long numerator;
	private final long denominator;
	private final long minimumUnitsToCollect;
	private final long maximumUnitsToCollect;
	private final boolean netOfTransfers;

	public FractionalFeeSpec(
			long numerator,
			long denominator,
			long minimumUnitsToCollect,
			long maximumUnitsToCollect,
			boolean netOfTransfers
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
		this.netOfTransfers = netOfTransfers;
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

	public boolean isNetOfTransfers() {
		return netOfTransfers;
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
				this.maximumUnitsToCollect == that.maximumUnitsToCollect &&
				this.netOfTransfers == that.netOfTransfers;
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
				.add("netOfTransfers", netOfTransfers)
				.toString();
	}
}
