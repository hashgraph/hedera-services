package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.state.expiry.TokenRelsListRemoval;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;

import static com.hedera.services.utils.MapValueListUtils.inPlaceRemoveFromMapValueList;
import static com.hedera.services.utils.MapValueListUtils.overwritingRemoveFromMapValueList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapValueListUtilsTest {
	private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels = new MerkleMap<>();

	@Test
	void sequentialRemovalWorksAsExpectedOverwriting() {
		initializeRels();

		final var relsListRemoval = new TokenRelsListRemoval(accountNum.longValue(), tokenRels);

		final var k1 = overwritingRemoveFromMapValueList(aRelKey, aRelKey, relsListRemoval);
		assertEquals(bRelKey, k1);
		assertFalse(tokenRels.containsKey(aRelKey));

		final var k2 = overwritingRemoveFromMapValueList(k1, k1, relsListRemoval);
		assertEquals(cRelKey, k2);
		assertFalse(tokenRels.containsKey(bRelKey));

		final var k3 = overwritingRemoveFromMapValueList(k2, k2, relsListRemoval);
		assertNull(k3);
		assertTrue(tokenRels.isEmpty());
	}

	@Test
	void interiorRemovalWorksAsExpectedOverwriting() {
		initializeRels();

		final var relsListRemoval = new TokenRelsListRemoval(accountNum.longValue(), tokenRels);

		final var k1 = overwritingRemoveFromMapValueList(bRelKey, aRelKey, relsListRemoval);
		assertEquals(aRelKey, k1);
		assertFalse(tokenRels.containsKey(bRelKey));
	}

	@Test
	void tailRemovalWorksAsExpectedOverwriting() {
		initializeRels();

		final var relsListRemoval = new TokenRelsListRemoval(accountNum.longValue(), tokenRels);

		final var k1 = overwritingRemoveFromMapValueList(cRelKey, aRelKey, relsListRemoval);
		assertEquals(aRelKey, k1);
		assertFalse(tokenRels.containsKey(cRelKey));
	}

	@Test
	void sequentialRemovalWorksAsExpectedInPlace() {
		initializeRels();

		final var relsListRemoval = new TokenRelsListRemoval(accountNum.longValue(), tokenRels);

		final var k1 = inPlaceRemoveFromMapValueList(aRelKey, aRelKey, relsListRemoval);
		assertEquals(bRelKey, k1);
		assertFalse(tokenRels.containsKey(aRelKey));

		final var k2 = inPlaceRemoveFromMapValueList(k1, k1, relsListRemoval);
		assertEquals(cRelKey, k2);
		assertFalse(tokenRels.containsKey(bRelKey));

		final var k3 = inPlaceRemoveFromMapValueList(k2, k2, relsListRemoval);
		assertNull(k3);
		assertTrue(tokenRels.isEmpty());
	}

	@Test
	void interiorRemovalWorksAsExpectedInPlace() {
		initializeRels();

		final var relsListRemoval = new TokenRelsListRemoval(accountNum.longValue(), tokenRels);

		final var k1 = inPlaceRemoveFromMapValueList(bRelKey, aRelKey, relsListRemoval);
		assertEquals(aRelKey, k1);
		assertFalse(tokenRels.containsKey(bRelKey));
	}

	@Test
	void tailRemovalWorksAsExpectedInPlace() {
		initializeRels();

		final var relsListRemoval = new TokenRelsListRemoval(accountNum.longValue(), tokenRels);

		final var k1 = inPlaceRemoveFromMapValueList(cRelKey, aRelKey, relsListRemoval);
		assertEquals(aRelKey, k1);
		assertFalse(tokenRels.containsKey(cRelKey));
	}

	private void initializeRels() {
		aRel.setNext(b.longValue());
		tokenRels.put(aRelKey, aRel);
		bRel.setPrev(a.longValue());
		bRel.setNext(c.longValue());
		tokenRels.put(bRelKey, bRel);
		cRel.setPrev(b.longValue());
		tokenRels.put(cRelKey, cRel);
	}

	private static final EntityNum accountNum = EntityNum.fromLong(2);
	private static final EntityNum a = EntityNum.fromLong(4);
	private static final EntityNum b = EntityNum.fromLong(8);
	private static final EntityNum c = EntityNum.fromLong(16);
	private static final EntityNumPair aRelKey = EntityNumPair.fromNums(accountNum, a);
	private static final EntityNumPair bRelKey = EntityNumPair.fromNums(accountNum, b);
	private static final EntityNumPair cRelKey = EntityNumPair.fromNums(accountNum, c);
	private MerkleTokenRelStatus aRel = new MerkleTokenRelStatus(
			1L, true, false, true);
	private MerkleTokenRelStatus bRel = new MerkleTokenRelStatus(
			2L, true, false, true);
	private MerkleTokenRelStatus cRel = new MerkleTokenRelStatus(
			3L, true, false, true);
}
