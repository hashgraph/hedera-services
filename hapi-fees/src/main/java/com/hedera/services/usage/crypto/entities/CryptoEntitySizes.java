package com.hedera.services.usage.crypto.entities;

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

import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

public enum CryptoEntitySizes {
	CRYPTO_ENTITY_SIZES;

	/* { deleted, smartContract, receiverSigRequired } */
	static int NUM_FLAGS_IN_BASE_ACCOUNT_REPRESENTATION = 3;
	/* { expiry, hbarBalance, autoRenewSecs } */
	static int NUM_LONG_FIELDS_IN_BASE_ACCOUNT_REPRESENTATION = 3;

	public int bytesInTokenAssocRepr() {
		return LONG_SIZE + 2 * BOOL_SIZE;
	}

	public int fixedBytesInAccountRepr() {
		return NUM_FLAGS_IN_BASE_ACCOUNT_REPRESENTATION * BOOL_SIZE
				+ NUM_LONG_FIELDS_IN_BASE_ACCOUNT_REPRESENTATION * LONG_SIZE;
	}
}
