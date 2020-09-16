package com.hedera.services.ledger.accounts;

/*-
 * ‌
 * Hedera Services Node
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

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.test.factories.accounts.MapValueFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.asAccount;

@RunWith(JUnitPlatform.class)
class FCMapBackingAccountsTest {
	private final AccountID a = asAccount("1.2.3");
	private final AccountID b = asAccount("3.2.1");
	private final AccountID c = asAccount("4.3.0");
	private final AccountID d = asAccount("1.3.4");
	private final MerkleEntityId aKey = MerkleEntityId.fromAccountId(a);
	private final MerkleEntityId bKey = MerkleEntityId.fromAccountId(b);
	private final MerkleEntityId cKey = MerkleEntityId.fromAccountId(c);
	private final MerkleEntityId dKey = MerkleEntityId.fromAccountId(d);
	private final MerkleAccount aValue = MapValueFactory.newAccount().balance(123L).get();
	private final MerkleAccount bValue = MapValueFactory.newAccount().balance(122L).get();
	private final MerkleAccount cValue = MapValueFactory.newAccount().balance(121L).get();
	private final MerkleAccount dValue = MapValueFactory.newAccount().balance(120L).get();

	private FCMap<MerkleEntityId, MerkleAccount> map;
	private FCMapBackingAccounts subject;

	@BeforeEach
	private void setup() {
		map = mock(FCMap.class);
		given(map.keySet()).willReturn(Collections.emptySet());

		subject = new FCMapBackingAccounts(() -> map);
	}

	@Test
	public void tokenCopyAsExpected() {
		// setup:
		var account = mock(MerkleAccount.class);
		var expected = mock(MerkleAccount.class);

		given(account.tokenCopy()).willReturn(expected);
		given(map.getForModify(aKey)).willReturn(account);

		// when:
		var tokenCopy = subject.getTokenCopy(a);

		// then:
		assertSame(expected, tokenCopy);
	}

	@Test
	public void syncsFromInjectedMap() {
		// setup:
		map = new FCMap<>();
		map.put(aKey, aValue);
		map.put(bKey, bValue);
		// and:
		subject = new FCMapBackingAccounts(() -> map);

		// then:
		assertTrue(subject.existingAccounts.contains(a));
		assertTrue(subject.existingAccounts.contains(b));
	}

	@Test
	public void containsDelegatesToKnownActive() {
		// setup:
		subject.existingAccounts = Set.of(a, b);

		// expect:
		assertTrue(subject.contains(a));
		assertTrue(subject.contains(b));
		// and:
		verify(map, never()).containsKey(any());
	}

	@Test
	public void putUpdatesKnownAccounts() {
		// when:
		subject.put(a, aValue);

		// then:
		assertTrue(subject.existingAccounts.contains(a));
		// and:
		verify(map, never()).containsKey(any());
	}

	@Test
	public void getRefIsReadThrough() {
		given(map.getForModify(aKey)).willReturn(aValue);

		// expect:
		assertEquals(aValue, subject.getRef(a));
		assertEquals(aValue, subject.getRef(a));
		// and:
		verify(map, times(1)).getForModify(aKey);
	}

	@Test
	public void getRefUpdatesCache() {
		given(map.getForModify(aKey)).willReturn(aValue);

		// when:
		subject.getRef(a);

		// then:
		assertEquals(aValue, subject.cache.get(a));
	}

	@Test
	public void removeUpdatesBothCacheAndDelegate() {
		// given:
		subject.existingAccounts.add(a);

		// when:
		subject.remove(a);

		// then:
		verify(map).remove(aKey);
		// and:
		assertFalse(subject.existingAccounts.contains(a));
	}

	@Test
	public void returnsMutableRef() {
		given(map.getForModify(aKey)).willReturn(aValue);

		// when:
		MerkleAccount v = subject.getRef(a);

		// then:
		assertSame(aValue, v);
	}

	@Test
	public void usesPutForMissing() {
		// given:
		subject.put(a, bValue);

		// expect:
		verify(map).put(aKey, bValue);
	}

	@Test
	public void putDoesNothingIfPresent() {
		// setup:
		subject.existingAccounts.add(a);

		given(map.getForModify(aKey)).willReturn(aValue);

		// when:
		subject.getRef(a);
		subject.put(a, aValue);

		// then:
		verify(map, never()).replace(aKey, aValue);
	}

	@Test
	public void putThrowsIfAttemptToReplaceExistingWithUnrecognizedRef() {
		// setup:
		subject.existingAccounts.add(a);

		// given:
		subject.getRef(a);

		// when:
		assertThrows(IllegalArgumentException.class, () -> subject.put(a, cValue));
	}

	@Test
	public void putThrowsIfAttemptToReplaceExistingWithNonmutableRef() {
		// given:
		subject.existingAccounts.add(a);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.put(a, cValue));
	}

	@Test
	public void ensuresAllRefsAreReplaced() {
		// setup:
		subject.existingAccounts = Set.of(a, b, c, d);
		// and:
		InOrder inOrder = inOrder(map);

		// given:
		given(map.getForModify(aKey)).willReturn(aValue);
		given(map.getForModify(dKey)).willReturn(dValue);
		given(map.getForModify(bKey)).willReturn(bValue);
		given(map.getForModify(cKey)).willReturn(cValue);

		// when:
		subject.getRef(c);
		subject.getRef(a);
		subject.getRef(d);
		subject.getRef(b);
		// and:
		subject.flushMutableRefs();

		// then:
		inOrder.verify(map).replace(cKey, cValue);
		inOrder.verify(map).replace(bKey, bValue);
		inOrder.verify(map).replace(aKey, aValue);
		inOrder.verify(map).replace(dKey, dValue);
	}

	@Test
	public void returnsExpectedIds() {
		// setup:
		var s = Set.of(a, b, c, d);
		// given:
		subject.existingAccounts = s;

		// expect:
		assertSame(s, subject.idSet());
	}

	@Test
	public void delegatesUnsafeRef() {
		given(map.get(aKey)).willReturn(aValue);

		// expect:
		assertEquals(aValue, subject.getUnsafeRef(a));
	}
}
