package com.hedera.services.usage.token;

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

import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

public enum TokenEntitySizes {
	TOKEN_ENTITY_SIZES;

	/* { deleted, accountsFrozenByDefault } */
	static int NUM_FLAGS_IN_BASE_TOKEN_REPRESENTATION = 2;
	/* { decimals } */
	static int NUM_INT_FIELDS_IN_BASE_TOKEN_REPRESENTATION = 1;
	/* { expiry, totalSupply, autoRenewPeriod } */
	static int NUM_LONG_FIELDS_IN_BASE_TOKEN_REPRESENTATION = 3;
	/* { treasury } */
	static int NUM_ENTITY_ID_FIELDS_IN_BASE_TOKEN_REPRESENTATION = 1;

	public int fixedBytesInTokenRepr() {
		return NUM_FLAGS_IN_BASE_TOKEN_REPRESENTATION * 1
				+ NUM_INT_FIELDS_IN_BASE_TOKEN_REPRESENTATION * 4
				+ NUM_LONG_FIELDS_IN_BASE_TOKEN_REPRESENTATION * 8
				+ NUM_ENTITY_ID_FIELDS_IN_BASE_TOKEN_REPRESENTATION * BASIC_ENTITY_ID_SIZE;
	}

	public int totalBytesInfTokenReprGiven(String symbol, String name) {
		return fixedBytesInTokenRepr() + symbol.length() + name.length();
	}

	public int bytesUsedToRecordTokenTransfers(int numTokens, int numTransfers) {
		return numTokens * BASIC_ENTITY_ID_SIZE + numTransfers * USAGE_PROPERTIES.accountAmountBytes();
	}

	public int bytesUsedPerAccountRelationship() {
		return 3 * BASIC_ENTITY_ID_SIZE + LONG_SIZE + 2 * BOOL_SIZE;
	}
}
