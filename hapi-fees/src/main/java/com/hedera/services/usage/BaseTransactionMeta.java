package com.hedera.services.usage;

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

public class BaseTransactionMeta {
	private final int memoUtf8Bytes;
	// Note: This field only records original grpc transaction's transfers.
	// Keep it this way till we have a comprehensive solution for custom fee enhancement.
	private final int numExplicitTransfers;

	public BaseTransactionMeta(int memoUtf8Bytes, int numExplicitTransfers) {
		this.memoUtf8Bytes = memoUtf8Bytes;
		this.numExplicitTransfers = numExplicitTransfers;
	}

	public int getMemoUtf8Bytes() {
		return memoUtf8Bytes;
	}

	public int getNumExplicitTransfers() {
		return numExplicitTransfers;
	}
}
