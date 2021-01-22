package com.hedera.services.usage.contract.entities;

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

import org.junit.jupiter.api.Test;


import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractEntitySizesTest {
	ContractEntitySizes subject = ContractEntitySizes.CONTRACT_ENTITY_SIZES;

	@Test
	public void knowsExpectedFixedBytes() {
		// expect:
		assertEquals(
				1 * BOOL_SIZE + 4 * LONG_SIZE + 2 * BASIC_ENTITY_ID_SIZE + 40,
				subject.fixedBytesInContractRepr());
	}
}
