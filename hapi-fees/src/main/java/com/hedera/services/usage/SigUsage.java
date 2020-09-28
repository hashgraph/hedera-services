package com.hedera.services.usage;

/*-
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import java.util.Objects;

public class SigUsage {
	private final int numSigs;
	private final int sigsSize;
	private final int numPayerKeys;

	public SigUsage(int numSigs, int sigsSize, int numPayerKeys) {
		this.numSigs = numSigs;
		this.sigsSize = sigsSize;
		this.numPayerKeys = numPayerKeys;
	}

	public int numSigs() {
		return numSigs;
	}

	public int sigsSize() {
		return sigsSize;
	}

	public int numPayerKeys() {
		return numPayerKeys;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || o.getClass() != SigUsage.class) {
			return false;
		}
		SigUsage that = (SigUsage)o;
		return this.numSigs == that.numSigs && this.sigsSize == that.sigsSize && this.numPayerKeys == that.numPayerKeys;
	}

	@Override
	public int hashCode() {
		return Objects.hash(numSigs, sigsSize, numPayerKeys);
	}

	@Override
	public String toString() {
		return String.format("SigUsage{numSigs=%d, sigsSize=%d, numPayerKeys=%d}", numSigs, sigsSize, numPayerKeys);
	}
}
