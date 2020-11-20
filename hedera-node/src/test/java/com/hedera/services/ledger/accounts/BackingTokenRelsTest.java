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

import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Collections;

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

@RunWith(JUnitPlatform.class)
class BackingTokenRelsTest {
	long aBalance = 100, bBalance = 200, cBalance = 300;
	boolean aFrozen = true, bFrozen = false, cFrozen = true;
	boolean aKyc = false, bKyc = true, cKyc = false;
	AccountID a = asAccount("1.2.3");
	AccountID b = asAccount("3.2.1");
	AccountID c = asAccount("4.3.0");
	TokenID at = asToken("9.8.7");
	TokenID bt = asToken("9.8.6");
	TokenID ct = asToken("9.8.5");

	MerkleEntityAssociation aKey = fromAccountTokenRel(a, at);
	MerkleEntityAssociation bKey = fromAccountTokenRel(b, bt);
	MerkleEntityAssociation cKey = fromAccountTokenRel(c, ct);
	MerkleTokenRelStatus aValue = new MerkleTokenRelStatus(aBalance, aFrozen, aKyc);
	MerkleTokenRelStatus bValue = new MerkleTokenRelStatus(bBalance, bFrozen, bKyc);
	MerkleTokenRelStatus cValue = new MerkleTokenRelStatus(cBalance, cFrozen, cKyc);

	private FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> rels;

	private BackingTokenRels subject;

	@BeforeEach
	private void setup() {
		rels = new FCMap<>();
		rels.put(aKey, aValue);
		rels.put(bKey, bValue);

		subject = new BackingTokenRels(() -> rels);
	}

	@Test
	public void relToStringWorks() {
		// expect:
		assertEquals("1.2.3 <-> 9.8.7", readableTokenRel(asTokenRel(a, at)));
	}

	@Test
	public void delegatesPutForNewRelIfMissing() {
		// when:
		subject.put(asTokenRel(c, ct), cValue);

		// then:
		assertEquals(cValue, rels.get(fromAccountTokenRel(c, ct)));
		// and:
		assertTrue(subject.existingRels.contains(asTokenRel(c, ct)));
	}

	@Test
	public void delegatesPutForNewRel() {
		// when:
		subject.put(asTokenRel(c, ct), cValue);

		// then:
		assertEquals(cValue, rels.get(fromAccountTokenRel(c, ct)));
	}

	@Test
	public void throwsOnReplacingUnsafeRef() {
		// when:
		assertThrows(IllegalArgumentException.class, () -> subject.put(asTokenRel(a, at), aValue));
	}

	@Test
	public void removeUpdatesBothCacheAndDelegate() {
		// when:
		subject.remove(asTokenRel(a, at));

		// then:
		assertFalse(rels.containsKey(fromAccountTokenRel(a, at)));
		// and:
		assertFalse(subject.existingRels.contains(asTokenRel(a, at)));
	}

	@Test
	public void replacesAllMutableRefs() {
		setupMocked();

		given(rels.getForModify(fromAccountTokenRel(a, at))).willReturn(aValue);
		given(rels.getForModify(fromAccountTokenRel(b, bt))).willReturn(bValue);

		// when:
		subject.getRef(asTokenRel(a, at));
		subject.getRef(asTokenRel(b, bt));
		// and:
		subject.flushMutableRefs();

		// then:
		verify(rels).replace(fromAccountTokenRel(a, at), aValue);
		verify(rels).replace(fromAccountTokenRel(b, bt), bValue);
		// and:
		assertTrue(subject.cache.isEmpty());
	}

	@Test
	public void syncsFromInjectedMap() {
		// expect:
		assertTrue(subject.existingRels.contains(asTokenRel(a, at)));
		assertTrue(subject.existingRels.contains(asTokenRel(b, bt)));
	}

	@Test
	public void rebuildsFromChangedSources() {
		// when:
		rels.clear();
		rels.put(cKey, cValue);
		// and:
		subject.rebuildFromSources();

		// then:
		assertFalse(subject.existingRels.contains(asTokenRel(a, at)));
		assertFalse(subject.existingRels.contains(asTokenRel(b, bt)));
		// and:
		assertTrue(subject.existingRels.contains(asTokenRel(c, ct)));
	}

	@Test
	public void containsWorks() {
		// expect:
		assertTrue(subject.contains(asTokenRel(a, at)));
		assertTrue(subject.contains(asTokenRel(b, bt)));
	}

	@Test
	public void getIsReadThrough() {
		setupMocked();

		given(rels.getForModify(aKey)).willReturn(aValue);

		// when:
		var firstStatus = subject.getRef(asTokenRel(a, at));
		var secondStatus = subject.getRef(asTokenRel(a, at));

		// then:
		assertSame(aValue, firstStatus);
		assertSame(aValue, secondStatus);
		// and:
		assertSame(aValue, subject.cache.get(asTokenRel(a, at)));
		// and:
		verify(rels, times(1)).getForModify(any());
	}

	@Test
	public void irrelevantMethodsNotSupported() {
		// expect:
		assertThrows(UnsupportedOperationException.class, () -> subject.getUnsafeRef(null));
		assertThrows(UnsupportedOperationException.class, subject::idSet);
	}

	private void setupMocked() {
		rels = mock(FCMap.class);
		given(rels.keySet()).willReturn(Collections.emptySet());
		subject = new BackingTokenRels(() -> rels);
	}
}
