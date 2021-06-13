package com.hedera.services.usage.crypto;

/*-
 * ‌
 * Hedera Services API Fees
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

public class CryptoTransferMeta {
	private int tokenMultiplier = 1;

	private final int numTokensInvolved;
	private final int numTokenTransfers;

	public CryptoTransferMeta(int tokenMultiplier, int numTokensInvolved, int numTokenTransfers) {
		this.tokenMultiplier = tokenMultiplier;
		this.numTokensInvolved = numTokensInvolved;
		this.numTokenTransfers = numTokenTransfers;
	}

	public CryptoTransferMeta(int numTokensInvolved, int numTokenTransfers) {
		this.numTokensInvolved = numTokensInvolved;
		this.numTokenTransfers = numTokenTransfers;
	}

	public int getTokenMultiplier() {
		return tokenMultiplier;
	}

	public int getNumTokensInvolved() {
		return numTokensInvolved;
	}

	public int getNumTokenTransfers() {
		return numTokenTransfers;
	}

	public void setTokenMultiplier(int tokenMultiplier) {
		this.tokenMultiplier = tokenMultiplier;
	}
}
