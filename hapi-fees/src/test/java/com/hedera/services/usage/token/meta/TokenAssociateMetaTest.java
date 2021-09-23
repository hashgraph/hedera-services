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

import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenAssociateMetaTest {
	@Test
	void allGettersAndToStringWork() {
		final var expected = "TokenAssociateMeta{bpt=132, numOfTokens=2, relativeLifeTime=777600}";

		final var subject = new TokenAssociateMeta(132, 2);
		subject.setRelativeLifeTime(777_600L);


		assertEquals(132, subject.getBpt());
		assertEquals(2, subject.getNumOfTokens());
		assertEquals(777_600L, subject.getRelativeLifeTime());

		assertEquals(expected, subject.toString());
	}

	@Test
	void hashCodeAndEqualsWork() {
		final var meta1 = new TokenAssociateMeta(132, 2);;
		final var meta2 = new TokenAssociateMeta(132, 2);;

		assertEquals(meta1, meta2);
		assertEquals(meta1.hashCode(), meta1.hashCode());
	}
}
