package com.hedera.services.usage.token.entities;

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

import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.services.usage.token.entities.TokenEntitySizes.*;

@RunWith(JUnitPlatform.class)
public class TokenEntitySizesTest {
	TokenEntitySizes subject = TokenEntitySizes.TOKEN_ENTITY_SIZES;

	@Test
	public void sizesFixedAsExpected() {
		// setup:
		long expected = NUM_FLAGS_IN_BASE_TOKEN_REPRESENTATION * FeeBuilder.BOOL_SIZE
				+ NUM_INT_FIELDS_IN_BASE_TOKEN_REPRESENTATION * 4
				+ NUM_LONG_FIELDS_IN_BASE_TOKEN_REPRESENTATION * 8
				+ NUM_ENTITY_ID_FIELDS_IN_BASE_TOKEN_REPRESENTATION * BASIC_ENTITY_ID_SIZE;

		// given:
		long actual = subject.fixedBytesInTokenRepr();

		// expect:
		assertEquals(expected, actual);
	}

	@Test
	public void sizesAsExpected() {
		// setup:
		var symbol = "ABCDEFGH";
		var name = "WhyWouldINameItThis";
		long expected = NUM_FLAGS_IN_BASE_TOKEN_REPRESENTATION * 1
				+ NUM_INT_FIELDS_IN_BASE_TOKEN_REPRESENTATION * 4
				+ NUM_LONG_FIELDS_IN_BASE_TOKEN_REPRESENTATION * 8
				+ NUM_ENTITY_ID_FIELDS_IN_BASE_TOKEN_REPRESENTATION * BASIC_ENTITY_ID_SIZE
				+ symbol.getBytes().length + name.getBytes().length;

		// given:
		long actual = subject.totalBytesInfTokenReprGiven(symbol, name);

		// expect:
		assertEquals(expected, actual);
	}

	@Test
	public void understandsRecordTransfersSize() {
		// setup:
		int numTokens = 3, numTransfers = 8;

		// given:
		var expected = 3 * BASIC_ENTITY_ID_SIZE + 8 * (8 + BASIC_ENTITY_ID_SIZE);

		// then:
		assertEquals(expected, subject.bytesUsedToRecordTokenTransfers(numTokens, numTransfers));
	}

	@Test
	public void returnsRequiredBytesForRel() {
		// expect:
		assertEquals(
				3 * BASIC_ENTITY_ID_SIZE + LONG_SIZE + 2 * FeeBuilder.BOOL_SIZE,
				subject.bytesUsedPerAccountRelationship());
	}
}
