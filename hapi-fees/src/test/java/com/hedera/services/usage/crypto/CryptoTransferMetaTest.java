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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CryptoTransferMetaTest {
	@Test
	void setterWith2ParamsWorks() {
		final var subject = new CryptoTransferMeta(1, 2);

		// when:
		subject.setTokenMultiplier(3);

		// then:
		assertEquals(3, subject.getTokenMultiplier());
		assertEquals(1, subject.getNumTokensInvolved());
		assertEquals(2, subject.getNumTokenTransfers());
	}

	@Test
	void setterWith3ParamsWorks() {
		final var subject = new CryptoTransferMeta(1, 2, 3);

		// when:
		subject.setCustomFeeHbarTransfers(10);
		subject.setCustomFeeTokenTransfers(5);
		subject.setCustomFeeTokensInvolved(2);

		// then:
		assertEquals(1, subject.getTokenMultiplier());
		assertEquals(3, subject.getNumTokenTransfers());
		assertEquals(2, subject.getNumTokensInvolved());
		assertEquals(2, subject.getCustomFeeTokensInvolved());
		assertEquals(5, subject.getCustomFeeTokenTransfers());
		assertEquals(10, subject.getCustomFeeHbarTransfers());
	}
}
