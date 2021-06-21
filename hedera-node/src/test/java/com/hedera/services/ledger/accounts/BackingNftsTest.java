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

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.NftId;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BackingNftsTest {
	private NftId aNftId = new NftId(1, 2, 3, 4);
	private NftId bNftId = new NftId(2, 3, 4, 5);
	private NftId cNftId = new NftId(3, 4, 5, 6);
	private MerkleUniqueTokenId aKey =
			new MerkleUniqueTokenId(new EntityId(1, 2, 3), 4);
	private MerkleUniqueTokenId cKey =
			new MerkleUniqueTokenId(new EntityId(3, 4, 5), 6);
	private MerkleUniqueToken aValue = new MerkleUniqueToken(
			new EntityId(1, 2, 3),
			"abcdefgh".getBytes(),
			new RichInstant(1_234_567L, 1));

	@Mock
	private FCMap<MerkleUniqueTokenId, MerkleUniqueToken> delegate;

	private BackingNfts subject;

	@BeforeEach
	void setUp() {
		given(delegate.keySet()).willReturn(Set.of(
				aKey,
				new MerkleUniqueTokenId(new EntityId(2, 3, 4), 5)
		));

		subject = new BackingNfts(() -> delegate);
	}

	@Test
	void rebuildWorks() {
		// when:
		subject = new BackingNfts(() -> delegate);

		// expect:
		assertEquals(Set.of(aNftId, bNftId), subject.idSet());
	}

	@Test
	void containsWorks() {
		// expect:
		assertTrue(subject.contains(aNftId));
		assertTrue(subject.contains(bNftId));
		assertFalse(subject.contains(cNftId));
	}

	@Test
	void getRefDelegatesToGetForModify() {
		given(delegate.getForModify(aKey)).willReturn(aValue);

		// when:
		final var mutable = subject.getRef(aNftId);

		// then:
		assertSame(aValue, mutable);
	}

	@Test
	void getImmutableRefDelegatesToGet() {
		given(delegate.get(aKey)).willReturn(aValue);

		// when:
		final var immutable = subject.getImmutableRef(aNftId);

		// then:
		assertSame(aValue, immutable);
	}

	@Test
	void putWorks() {
		// when:
		subject.put(cNftId, aValue);
		subject.put(cNftId, aValue);

		// then:
		verify(delegate, times(1)).put(cKey, aValue);
	}

	@Test
	void removeWorks() {
		// when:
		subject.remove(aNftId);

		// then:
		assertEquals(Set.of(bNftId), subject.idSet());
		verify(delegate).remove(aKey);
	}
}
