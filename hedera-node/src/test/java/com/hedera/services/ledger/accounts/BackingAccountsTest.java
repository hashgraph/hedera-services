package com.hedera.services.ledger.accounts;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.MutabilityException;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.merkle.utility.MerkleLong;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcmap.internal.FCMLeaf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;

class BackingAccountsTest {
	private final AccountID a = asAccount("1.2.3");
	private final AccountID b = asAccount("3.2.1");
	private final AccountID c = asAccount("4.3.0");
	private final AccountID d = asAccount("1.3.4");
	private final MerkleEntityId aKey = MerkleEntityId.fromAccountId(a);
	private final MerkleEntityId bKey = MerkleEntityId.fromAccountId(b);
	private final MerkleEntityId cKey = MerkleEntityId.fromAccountId(c);
	private final MerkleEntityId dKey = MerkleEntityId.fromAccountId(d);
	private final MerkleAccount aValue = MerkleAccountFactory.newAccount().balance(123L).get();
	private final MerkleAccount bValue = MerkleAccountFactory.newAccount().balance(122L).get();
	private final MerkleAccount cValue = MerkleAccountFactory.newAccount().balance(121L).get();
	private final MerkleAccount dValue = MerkleAccountFactory.newAccount().balance(120L).get();

	private FCMap<MerkleEntityId, MerkleAccount> map;
	private BackingAccounts subject;

	@BeforeEach
	private void setup() {
		map = mock(FCMap.class);
		given(map.keySet()).willReturn(Collections.emptySet());

		subject = new BackingAccounts(() -> map);
	}

	@Test
	void syncsFromInjectedMap() {
		// setup:
		map = new FCMap<>();
		map.put(aKey, aValue);
		map.put(bKey, bValue);
		// and:
		subject = new BackingAccounts(() -> map);

		// then:
		assertTrue(subject.existingAccounts.contains(a));
		assertTrue(subject.existingAccounts.contains(b));
	}

	@Test
	void rebuildsFromChangedSources() {
		// setup:
		map = new FCMap<>();
		map.put(aKey, aValue);
		map.put(bKey, bValue);
		// and:
		subject = new BackingAccounts(() -> map);

		// when:
		map.clear();
		map.put(cKey, cValue);
		map.put(dKey, dValue);
		// and:
		subject.rebuildFromSources();

		// then:
		assertFalse(subject.existingAccounts.contains(a));
		assertFalse(subject.existingAccounts.contains(b));
		// and:
		assertTrue(subject.existingAccounts.contains(c));
		assertTrue(subject.existingAccounts.contains(d));
	}

	@Test
	void containsDelegatesToKnownActive() {
		// setup:
		subject.existingAccounts = Set.of(a, b);

		// expect:
		assertTrue(subject.contains(a));
		assertTrue(subject.contains(b));
		// and:
		verify(map, never()).containsKey(any());
	}

	@Test
	void putUpdatesKnownAccounts() {
		// when:
		subject.put(a, aValue);

		// then:
		assertTrue(subject.existingAccounts.contains(a));
		// and:
		verify(map, never()).containsKey(any());
	}

	@Test
	void getRefIsReadThrough() {
		given(map.getForModify(aKey)).willReturn(aValue);

		// expect:
		assertEquals(aValue, subject.getRef(a));
		assertEquals(aValue, subject.getRef(a));
		// and:
		verify(map, times(2)).getForModify(aKey);
	}

	@Test
	void removeUpdatesBothCacheAndDelegate() {
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
	void returnsMutableRef() {
		given(map.getForModify(aKey)).willReturn(aValue);

		// when:
		MerkleAccount v = subject.getRef(a);

		// then:
		assertSame(aValue, v);
	}

	@Test
	void usesPutForMissing() {
		// given:
		subject.put(a, bValue);

		// expect:
		verify(map).put(aKey, bValue);
	}

	@Test
	void putDoesNothingIfPresent() {
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
	void returnsExpectedIds() {
		// setup:
		var s = Set.of(a, b, c, d);
		// given:
		subject.existingAccounts = s;

		// expect:
		assertSame(s, subject.idSet());
	}

	@Test
	void delegatesUnsafeRef() {
		given(map.get(aKey)).willReturn(aValue);

		// expect:
		assertEquals(aValue, subject.getUnsafeRef(a));
	}

	@Test
	void twoPutsChangesG4M() throws ConstructableRegistryException {
		// setup:
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMLeaf.class, FCMLeaf::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleLong.class, MerkleLong::new));

		/* Case 1: g4m a leaf; then put ONE new leaf; then change the mutable leaf and re-get to verify new value */
		final var firstFcm = new FCMap<MerkleLong, MerkleLong>();
		final var oneGrandKey = new MerkleLong(1000L);
		firstFcm.put(oneGrandKey, new MerkleLong(1L));
		final var mutableOne = firstFcm.getForModify(oneGrandKey);
		/* Putting just one new leaf */
		firstFcm.put(new MerkleLong(666L), new MerkleLong(666L));
		/* And then changing the mutable value */
		mutableOne.increment();
		assertEquals(2L, firstFcm.get(oneGrandKey).getValue());

		/* Case 2: g4m a leaf; then put TWO new leaves; then change the mutable leaf and re-get to verify new value */
		final var secondFcm = new FCMap<MerkleLong, MerkleLong>();
		final var twoGrandKey = new MerkleLong(2000L);
		secondFcm.put(twoGrandKey, new MerkleLong(2L));
		final var mutableTwo = secondFcm.getForModify(twoGrandKey);
		/* Putting two new leaves now */
		secondFcm.put(new MerkleLong(666L), new MerkleLong(666L));
		secondFcm.put(new MerkleLong(667L), new MerkleLong(667L));
		/* And now changing the once-mutable value throws MutabilityException */
		assertThrows(MutabilityException.class, mutableTwo::increment);
	}
}
