package com.hedera.services.utils;

import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import org.junit.jupiter.api.Test;

import static com.hedera.services.utils.EntityNumPair.MISSING_NUM_PAIR;
import static com.hedera.services.utils.EntityNumPair.fromLongs;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityNumPairTest {
	@Test
	void overridesJavaLangImpl() {
		final var v = 1_234_567;

		final var subject = new EntityNumPair(v);

		assertNotEquals(Long.valueOf(v).hashCode(), subject.hashCode());
	}

	@Test
	void equalsWorks() {
		final var a = new EntityNumPair(1);
		final var b = new EntityNumPair(2);
		final var c = a;

		assertNotEquals(a, b);
		assertNotEquals(null, a);
		assertNotEquals(a, new Object());
		assertEquals(a, c);
	}

	@Test
	void usesExpectedBitPacking() {
		// given:
		final var expected = new EntityNumPair(BitPackUtils.packedNums(1, 2));

		// expect:
		assertEquals(expected, EntityNumPair.fromLongs(1, 2));
	}

	@Test
	void factoryFromNftIdWorks() {
		// setup:
		final var expected = EntityNumPair.fromLongs(1, 2);
		final var nftId = new NftId(0, 0, 1, 2);

		// given:
		final var actual = EntityNumPair.fromNftId(nftId);

		// expect:
		assertEquals(expected, actual);
	}

	@Test
	void returnsMissingNumPairIfInvalidLong() {
		assertDoesNotThrow(() -> EntityNumPair.fromLongs(Long.MAX_VALUE, 2));
		assertEquals(MISSING_NUM_PAIR, EntityNumPair.fromLongs(Long.MAX_VALUE, 2));
		assertEquals(MISSING_NUM_PAIR, EntityNumPair.fromLongs(Long.MAX_VALUE, Long.MAX_VALUE));
		assertEquals(MISSING_NUM_PAIR, EntityNumPair.fromLongs(-1L, 2));
		assertEquals(MISSING_NUM_PAIR, EntityNumPair.fromLongs(-1L, Long.MAX_VALUE));
	}

	@Test
	void factoryFromInvalidNftIdWorks() {
		final var invalidShard = new NftId(1, 0, 1, 2);
		final var invalidRealm = new NftId(0, 1, 1, 2);

		assertSame(MISSING_NUM_PAIR, EntityNumPair.fromNftId(invalidShard));
		assertSame(MISSING_NUM_PAIR, EntityNumPair.fromNftId(invalidRealm));
	}

	@Test
	void factoryFromModelRelWorks() {
		// setup:
		final var expected = EntityNumPair.fromLongs(1, 2);
		final var modelRel = new TokenRelationship(
				new Token(new Id(0 , 0, 2)),
				new Account(new Id(0 , 0, 1)));

		// given:
		final var actual = EntityNumPair.fromModelRel(modelRel);

		// expect:
		assertEquals(expected, actual);
	}

	@Test
	void hiPhiAccessorWorks() {
		// setup:
		final long bigNum = (long)Integer.MAX_VALUE + 123;
		final var expected = EntityNum.fromLong(bigNum);

		// given:
		final var subject = EntityNumPair.fromLongs(bigNum, 1);

		// when:
		final var hi = subject.getHiPhi();

		// then:
		assertEquals(expected, hi);
	}

	@Test
	void accountTokenReprWorks() {
		// given:
		final var subject = EntityNumPair.fromLongs(1, 2);

		// when:
		final var pairRepr = subject.asAccountTokenRel();

		// then:
		assertEquals(1, pairRepr.getLeft().getAccountNum());
		assertEquals(2, pairRepr.getRight().getTokenNum());
	}

	@Test
	void toStringWorks() {
		// given:
		final var subject = EntityNumPair.fromLongs(1, 2);

		// expect:
		assertEquals("PermHashLong(1, 2)", subject.toString());
	}

	@Test
	void validateLongNumsInRange(){
		assertDoesNotThrow(() -> EntityNumPair.fromLongs(Long.MAX_VALUE, 2));
		assertEquals(MISSING_NUM_PAIR, EntityNumPair.fromLongs(Long.MAX_VALUE, 2));
	}
}