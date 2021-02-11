package com.hedera.services.records;

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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class EarliestEntryExpiryTest {
	@Test
	public void equalityWorks() {
		// given:
		EarliestRecordExpiry ere = new EarliestRecordExpiry(5L, asAccount("0.0.5"));

		// expect:
		assertTrue(ere.equals(ere));
		assertFalse(ere.equals(new Object()));
		assertTrue(ere.equals(new EarliestRecordExpiry(5L, asAccount("0.0.5"))));
		assertFalse(ere.equals(new EarliestRecordExpiry(6L, asAccount("0.0.5"))));
	}

	@Test
	public void toStringWorks() {
		// given:
		EarliestRecordExpiry ere = new EarliestRecordExpiry(5L, asAccount("0.0.5"));

		// expect:
		assertEquals("EarliestRecordExpiry{id=0.0.5, earliestExpiry=5}", ere.toString());
	}
}
