package com.hedera.services.utils;

import com.hedera.services.state.expiry.TokenRelsListRemoval;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapValueListUtilsTest {
	private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels = new MerkleMap<>();

	@Test
	void sequentialRemovalWorksAsExpected() {
		initializeRels();

		final var relsListRemoval = new TokenRelsListRemoval(accountNum.longValue(), tokenRels);

		final var k1 = MapValueListUtils.removeFromMapValueList(aRelKey, aRelKey, relsListRemoval);
		assertEquals(bRelKey, k1);
		assertFalse(tokenRels.containsKey(aRelKey));

		final var k2 = MapValueListUtils.removeFromMapValueList(k1, k1, relsListRemoval);
		assertEquals(cRelKey, k2);
		assertFalse(tokenRels.containsKey(bRelKey));

		final var k3 = MapValueListUtils.removeFromMapValueList(k2, k2, relsListRemoval);
		assertNull(k3);
		assertTrue(tokenRels.isEmpty());
	}

	@Test
	void interiorRemovalWorksAsExpected() {
		initializeRels();

		final var relsListRemoval = new TokenRelsListRemoval(accountNum.longValue(), tokenRels);

		final var k1 = MapValueListUtils.removeFromMapValueList(bRelKey, aRelKey, relsListRemoval);
		assertEquals(aRelKey, k1);
		assertFalse(tokenRels.containsKey(bRelKey));
	}

	@Test
	void tailRemovalWorksAsExpected() {
		initializeRels();

		final var relsListRemoval = new TokenRelsListRemoval(accountNum.longValue(), tokenRels);

		final var k1 = MapValueListUtils.removeFromMapValueList(cRelKey, aRelKey, relsListRemoval);
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