package com.hedera.services.store.models.fees;

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

/**
 * Represents a fractional fee. Useful in validation.
 *
 * @author Yoan Sredkov <yoansredkov@gmail.com>
 */
public class FractionalFee {

	private final long maximumAmount;
	private final long minimumAmount;
	private final long fractionalNumerator;
	private final long fractionalDenominator;

	public FractionalFee(long nominalMax, long min, long num, long denom) {
		this.maximumAmount = nominalMax == 0 ? Long.MAX_VALUE : nominalMax;
		this.minimumAmount = min;
		this.fractionalNumerator = num;
		this.fractionalDenominator = denom;
	}

	public long getMinimumAmount() {
		return minimumAmount;
	}

	public long getMaximumAmount() { return maximumAmount; }

	public long getFractionalNumerator() {
		return fractionalNumerator;
	}

	public long getFractionalDenominator() {
		return fractionalDenominator;
	}
}
