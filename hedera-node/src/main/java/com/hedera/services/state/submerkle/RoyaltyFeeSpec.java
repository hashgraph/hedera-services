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
