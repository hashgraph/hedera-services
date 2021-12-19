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

import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.ledger.accounts.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.accounts.BackingTokenRels.readableTokenRel;
import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;

class BackingTokenRelsTest {
	private long aBalance = 100, bBalance = 200, cBalance = 300;
	private boolean aFrozen = true, bFrozen = false, cFrozen = true;
	private boolean aKyc = false, bKyc = true, cKyc = false;
	private boolean automaticAssociation = false;
	private AccountID a = asAccount("0.0.3");
	private AccountID b = asAccount("0.0.1");
	private AccountID c = asAccount("0.0.0");
	private TokenID at = asToken("0.0.7");
	private TokenID bt = asToken("0.0.6");
	private TokenID ct = asToken("0.0.5");

	private EntityNumPair aKey = fromAccountTokenRel(a, at);
	private EntityNumPair bKey = fromAccountTokenRel(b, bt);
	private EntityNumPair cKey = fromAccountTokenRel(c, ct);
	private MerkleTokenRelStatus aValue = new MerkleTokenRelStatus(aBalance, aFrozen, aKyc, automaticAssociation);
	private MerkleTokenRelStatus bValue = new MerkleTokenRelStatus(bBalance, bFrozen, bKyc, automaticAssociation);
	private MerkleTokenRelStatus cValue = new MerkleTokenRelStatus(cBalance, cFrozen, cKyc, automaticAssociation);

	private MerkleMap<EntityNumPair, MerkleTokenRelStatus> rels;

	private BackingTokenRels subject;

	@BeforeEach
	private void setup() {
		rels = new MerkleMap<>();
		rels.put(aKey, aValue);
		rels.put(bKey, bValue);

		subject = new BackingTokenRels(() -> rels);
	}

	@Test
	void relToStringWorks() {
		// expect:
		assertEquals("0.0.3 <-> 0.0.7", readableTokenRel(asTokenRel(a, at)));
	}

	@Test
	void delegatesPutForNewRelIfMissing() throws ConstructableRegistryException {
		// when:
		subject.put(asTokenRel(c, ct), cValue);

		// then:
		assertEquals(cValue, rels.get(fromAccountTokenRel(c, ct)));
		// and:
		assertTrue(subject.contains(asTokenRel(c, ct)));
	}

	@Test
	void delegatesPutForNewRel() {
		// when:
		subject.put(asTokenRel(c, ct), cValue);

		// then:
		assertEquals(cValue, rels.get(fromAccountTokenRel(c, ct)));
	}

	@Test
	void removeUpdatesBothCacheAndDelegate() {
		// when:
		subject.remove(asTokenRel(a, at));

		// then:
		assertFalse(rels.containsKey(fromAccountTokenRel(a, at)));
		// and:
		assertFalse(subject.contains(asTokenRel(a, at)));
	}

	@Test
	void rebuildsFromChangedSources() {
		// when:
		rels.clear();
		rels.put(cKey, cValue);
		// and:
		subject.rebuildFromSources();

		// then:
		assertFalse(subject.contains(asTokenRel(a, at)));
		assertFalse(subject.contains(asTokenRel(b, bt)));
		// and:
		assertTrue(subject.contains(asTokenRel(c, ct)));
	}

	@Test
	void containsWorks() {
		// given:
		subject.rebuildFromSources();

		// expect:
		assertTrue(subject.contains(asTokenRel(a, at)));
		assertTrue(subject.contains(asTokenRel(b, bt)));
	}

	@Test
	void getIsReadThrough() {
		setupMocked();

		given(rels.getForModify(aKey)).willReturn(aValue);
		given(rels.get(bKey)).willReturn(bValue);

		// when:
		var firstStatus = subject.getRef(asTokenRel(a, at));
		var secondStatus = subject.getRef(asTokenRel(a, at));

		// then:
		assertSame(aValue, firstStatus);
		assertSame(aValue, secondStatus);
		assertSame(bValue, subject.getImmutableRef(asTokenRel(b, bt)));
		// and:
		verify(rels, times(2)).getForModify(any());
		verify(rels, times(1)).get(any());
	}

	@Test
	void irrelevantMethodsNotSupported() {
		// expect:
		assertThrows(UnsupportedOperationException.class, subject::idSet);
	}

	private void setupMocked() {
		rels = mock(MerkleMap.class);
		given(rels.keySet()).willReturn(Collections.emptySet());
		given(rels.entrySet()).willReturn(Collections.emptySet());
		subject = new BackingTokenRels(() -> rels);
	}
}
