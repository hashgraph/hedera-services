package com.hedera.services.usage.token.meta;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenGrantKycMetaTest {
	@Test
	void getterAndToStringWork() {
		final var expected = "TokenGrantKycMeta{bpt=96}";

		final var subject = new TokenGrantKycMeta(96);
		assertEquals(96, subject.getBpt());
		assertEquals(expected, subject.toString());
	}

	@Test
	void hashCodeAndEqualsWork() {
		final var meta1 = new TokenGrantKycMeta(100);
		final var meta2 = new TokenGrantKycMeta(100);

		assertEquals(meta1, meta2);
		assertEquals(meta1.hashCode(), meta2.hashCode());
	}
}
