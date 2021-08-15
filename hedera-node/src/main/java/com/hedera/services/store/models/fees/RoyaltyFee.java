/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.store.models.fees;

public class RoyaltyFee {
	private final long numerator;
	private final long denominator;
	private final FixedFee fallbackFee;

	public RoyaltyFee(FixedFee fallback, long numerator, long denominator) {
		this.fallbackFee = fallback;
		this.numerator = numerator;
		this.denominator = denominator;
	}

	public boolean hasFallbackFee() {
		return fallbackFee != null;
	}

	public FixedFee getFallbackFee() {
		return fallbackFee;
	}

	public long getNumerator() {
		return numerator;
	}

	public long getDenominator() {
		return denominator;
	}
}
