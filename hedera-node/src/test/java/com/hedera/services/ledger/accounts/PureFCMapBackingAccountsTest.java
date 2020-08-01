package com.hedera.services.ledger.accounts;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.test.factories.accounts.MapValueFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PureFCMapBackingAccountsTest {
	private final AccountID a = asAccount("1.2.3");
	private final AccountID b = asAccount("3.2.1");
	private final MerkleEntityId aKey = MerkleEntityId.fromAccountId(a);
	private final MerkleEntityId bKey = MerkleEntityId.fromAccountId(b);
	private final MerkleAccount aValue = MapValueFactory.newAccount().balance(123L).get();

	private FCMap<MerkleEntityId, MerkleAccount> map;
	private PureFCMapBackingAccounts subject;

	@BeforeEach
	private void setup() {
		map = mock(FCMap.class);

		subject = new PureFCMapBackingAccounts(map);
	}

	@Test
	public void mutationsNotSupported() {
		// expect:
		assertThrows(UnsupportedOperationException.class, () -> subject.remove(null));
		assertThrows(UnsupportedOperationException.class, () -> subject.put(null, null));
		assertDoesNotThrow(subject::flushMutableRefs);
	}

	@Test
	public void delegatesGet() {
		given(map.get(aKey)).willReturn(aValue);

		// then:
		assertSame(aValue, subject.getRef(a));
	}

	@Test
	public void delegatesContains() {
		given(map.containsKey(aKey)).willReturn(false);
		given(map.containsKey(bKey)).willReturn(true);

		// then:
		assertFalse(subject.contains(a));
		assertTrue(subject.contains(b));
		// and:
		verify(map, times(2)).containsKey(any());
	}

	@Test
	public void delegatesIdSet() {
		var ids = Set.of(aKey, bKey);
		var expectedIds = Set.of(a, b);

		given(map.keySet()).willReturn(ids);

		// expect:
		assertEquals(expectedIds, subject.idSet());
	}

	@Test
	public void delegatesUnsafeGet() {
		given(map.get(aKey)).willReturn(aValue);

		// expect:
		assertEquals(aValue, subject.getUnsafeRef(a));
	}
}