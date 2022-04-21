package com.hedera.services.utils;

import com.hedera.services.state.expiry.TokenRelsListMutation;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;

import static com.hedera.services.utils.MapValueListUtils.inPlaceInsertAtMapValueListHead;
import static org.junit.jupiter.api.Assertions.*;

class InPlaceHeadInsertionTest {
	private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels = new MerkleMap<>();

	@Test
	void canInsertToEmptyList() {
		final var listInsertion = new TokenRelsListMutation(accountNum.longValue(), tokenRels);

		final var newRoot = inPlaceInsertAtMapValueListHead(aRelKey, aRel, null, null, listInsertion);

		assertSame(aRelKey, newRoot);
		assertSame(aRel, tokenRels.get(aRelKey));
	}

	@Test
	void canInsertToNonEmptyListWithNullValue() {
		tokenRels.put(bRelKey, bRel);
		final var listInsertion = new TokenRelsListMutation(accountNum.longValue(), tokenRels);

		final var newRoot = inPlaceInsertAtMapValueListHead(aRelKey, aRel, bRelKey, null, listInsertion);

		assertSame(aRelKey, newRoot);
		final var newRootValue = tokenRels.get(aRelKey);
		assertEquals(bRelKey.getLowOrderAsLong(), newRootValue.nextKey());
		final var newNextValue = tokenRels.get(bRelKey);
		assertEquals(aRelKey.getLowOrderAsLong(), newNextValue.prevKey());
	}

	@Test
	void canInsertToNonEmptyListWithNonNullValue() {
		tokenRels.put(bRelKey, bRel);
		final var listInsertion = new TokenRelsListMutation(accountNum.longValue(), tokenRels);

		final var newRoot = inPlaceInsertAtMapValueListHead(aRelKey, aRel, bRelKey, bRel, listInsertion);

		assertSame(aRelKey, newRoot);
		final var newRootValue = tokenRels.get(aRelKey);
		assertEquals(bRelKey.getLowOrderAsLong(), newRootValue.nextKey());
		final var newNextValue = tokenRels.get(bRelKey);
		assertEquals(aRelKey.getLowOrderAsLong(), newNextValue.prevKey());
	}

	private static final EntityNum accountNum = EntityNum.fromLong(2);
	private static final EntityNum a = EntityNum.fromLong(4);
	private static final EntityNum b = EntityNum.fromLong(8);
	private static final EntityNum c = EntityNum.fromLong(16);
	private static final EntityNumPair aRelKey = EntityNumPair.fromNums(accountNum, a);
	private static final EntityNumPair bRelKey = EntityNumPair.fromNums(accountNum, b);
	private MerkleTokenRelStatus aRel = new MerkleTokenRelStatus(
			1L, true, false, true);
	private MerkleTokenRelStatus bRel = new MerkleTokenRelStatus(
			2L, true, false, true);
}