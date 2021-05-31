package com.hedera.services.store.tokens.unique;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */


import com.hedera.services.state.submerkle.EntityId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerIdentifierTest {


	OwnerIdentifier oid1 = new OwnerIdentifier(new EntityId(1, 2, 3));
	OwnerIdentifier oid2 = new OwnerIdentifier(new EntityId(1, 2, 3));

	@Test
	void testRawEquality() {
		assertEquals(oid1, oid2);
	}

	@Test
	void testEqualityWithEquals() {
		assertTrue(oid1.equals(oid2));
	}

	@Test
	void testEqualityWithHashCode() {
		assertEquals(oid1.hashCode(), oid2.hashCode());
	}
}