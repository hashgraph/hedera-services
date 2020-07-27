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

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.asAccount;

@RunWith(JUnitPlatform.class)
class FCMapBackingAccountsTest {
	private final AccountID a = asAccount("1.2.3");
	private final AccountID b = asAccount("3.2.1");
	private final MerkleEntityId aKey = MerkleEntityId.fromPojoAccountId(a);
	private final MerkleEntityId bKey = MerkleEntityId.fromPojoAccountId(b);
	private final MerkleAccount aValue = MapValueFactory.newAccount().balance(123L).get();
	private final MerkleAccount bValue = MapValueFactory.newAccount().balance(122L).get();

	private Set<AccountID> knownAccounts;
	private FCMap<MerkleEntityId, MerkleAccount> map;
	private FCMapBackingAccounts subject;

	@BeforeEach
	private void setup() {
		knownAccounts = (Set<AccountID>)mock(Set.class);
		map = mock(FCMap.class);

		subject = new FCMapBackingAccounts(knownAccounts, map);
	}

	@Test
	public void syncsCorrectly() {
		// setup:
		var delegate = new FCMap<MerkleEntityId, MerkleAccount>();
		var knownAccounts = new HashSet<AccountID>();
		// and:
		subject = new FCMapBackingAccounts(knownAccounts, delegate);

		// given:
		delegate.put(new MerkleEntityId(1,2, 3), new MerkleAccount());

		// expect:
		knownAccounts.isEmpty();

		// when:
		subject.syncKnownAccounts();

		// then:
		assertEquals(1, knownAccounts.size());
	}

	@Test
	public void initializationWorks() {
		// setup:
		Set<MerkleEntityId> actualAccounts = Set.of(aKey, bKey);

		given(map.keySet()).willReturn(actualAccounts);

		// when:
		FCMapBackingAccounts.sync(map, knownAccounts);

		// then:
		verify(knownAccounts).add(a);
		verify(knownAccounts).add(b);
	}

	@Test
	public void usesDelegateRemove() {
		// when:
		subject.remove(a);

		// then:
		verify(map).remove(aKey);
	}

	@Test
	public void usesDelegateIfKnownAccountsUnavail() {
		// setup:
		subject.knownAccounts = FCMapBackingAccounts.NO_KNOWN_ACCOUNTS;

		given(map.containsKey(aKey)).willReturn(true);
		given(map.containsKey(bKey)).willReturn(false);

		// when:
		boolean hasA = subject.contains(a);
		boolean hasB = subject.contains(b);

		// then:
		verify(map, times(2)).containsKey(any());
		verify(knownAccounts, never()).contains(any());
		// and:
		assertTrue(hasA);
		assertFalse(hasB);
	}

	@Test
	public void usesKnownAccountsIfAvail() {
		given(knownAccounts.contains(a)).willReturn(true);
		given(knownAccounts.contains(b)).willReturn(false);

		// when:
		boolean hasA = subject.contains(a);
		boolean hasB = subject.contains(b);

		// then:
		verify(knownAccounts, times(2)).contains(any());
		verify(map, never()).containsKey(any());
		// and:
		assertTrue(hasA);
		assertFalse(hasB);
	}

	@Test
	public void returnsMutableRef() {
		given(map.getForModify(aKey)).willReturn(aValue);

		// when:
		MerkleAccount v = subject.getMutableRef(a);

		// then:
		assertSame(aValue, v);
	}

	@Test
	public void returnsRef() {
		given(map.get(aKey)).willReturn(aValue);

		// when:
		MerkleAccount v = subject.getUnsafeRef(a);

		// then:
		assertSame(aValue, v);
	}

	@Test
	public void usesPutToReplaceMissing() {
		// when:
		subject.replace(a, bValue);

		// then:
		verify(map).put(aKey, bValue);
	}

	@Test
	public void usesReplaceOnDelegate() {
		given(knownAccounts.contains(a)).willReturn(true);

		// when:
		subject.replace(a, bValue);

		// then:
		verify(map).replace(aKey, bValue);
	}
}
