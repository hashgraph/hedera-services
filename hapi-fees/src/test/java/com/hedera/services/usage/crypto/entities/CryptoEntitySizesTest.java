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

import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;


import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.services.usage.crypto.entities.CryptoEntitySizes.*;

@RunWith(JUnitPlatform.class)
class CryptoEntitySizesTest {
	CryptoEntitySizes subject = CRYPTO_ENTITY_SIZES;

	@Test
	public void knowsExpectedFixedBytes() {
		// expect:
		assertEquals(3 * BOOL_SIZE + 5 * LONG_SIZE, subject.fixedBytesInAccountRepr());
	}

	@Test
	public void knowsTotalBytes() {
		// when:
		var actual = subject.bytesInTokenAssocRepr();

		// then:
		assertEquals(LONG_SIZE + 2 * BOOL_SIZE, actual);
	}
}