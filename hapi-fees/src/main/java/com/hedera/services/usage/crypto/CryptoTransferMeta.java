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

	private int totalTokensInvolved;
	private int totalTokenTransfers;
	private int totalHbarTransfers;

	public CryptoTransferMeta(int tokenMultiplier, int totalTokensInvolved, int totalTokenTransfers) {
		this.tokenMultiplier = tokenMultiplier;
		this.totalTokensInvolved = totalTokensInvolved;
		this.totalTokenTransfers = totalTokenTransfers;
		this.totalHbarTransfers = 0;
	}

	public CryptoTransferMeta(int totalTokenInvolved, int totalTokenTransfers) {
		this.totalTokensInvolved = totalTokenInvolved;
		this.totalTokenTransfers = totalTokenTransfers;
		this.totalHbarTransfers = 0;
	}

	public CryptoTransferMeta(int tokenMultiplier, int totalTokenInvolved, int totalTokenTransfers, int totalHbarTransfers) {
		this.tokenMultiplier = tokenMultiplier;
		this.totalTokensInvolved = totalTokenInvolved;
		this.totalTokenTransfers = totalTokenTransfers;
		this.totalHbarTransfers = totalHbarTransfers;
	}

	public int getTokenMultiplier() {
		return tokenMultiplier;
	}

	public int getTotalTokensInvolved() {
		return totalTokensInvolved;
	}

	public int getTotalTokenTransfers() {
		return totalTokenTransfers;
	}

	public int getTotalHbarTransfers() {
		return totalHbarTransfers;
	}

	public void setTotalTokensInvolved(final int totalTokenInvolved) {
		this.totalTokensInvolved = totalTokenInvolved;
	}

	public void setTotalTokenTransfers(final int totalTokenTransfers) {
		this.totalTokenTransfers = totalTokenTransfers;
	}
	public void setTotalHbarTransfers(final int totalHbarTransfers) {
		this.totalHbarTransfers = totalHbarTransfers;
	}

	public void setTokenMultiplier(int tokenMultiplier) {
		this.tokenMultiplier = tokenMultiplier;
	}
}
