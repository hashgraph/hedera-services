package com.hedera.services.ledger.backing;

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

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.store.models.NftId;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static com.google.common.truth.Truth.assertThat;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackingNftsTest {
	private final NftId aNftId = new NftId(0, 0, 3, 4);
	private final NftId bNftId = new NftId(0, 0, 4, 5);
	private final NftId cNftId = new NftId(0, 0, 5, 6);
	private final UniqueTokenKey aKey = new UniqueTokenKey(3, 4);
	private final UniqueTokenKey bKey = new UniqueTokenKey(4, 5);
	private final UniqueTokenValue updatedTokenA = new UniqueTokenValue(
			3,
			MISSING_ENTITY_ID.num(),
			new RichInstant(1_234_567L, 1),
			"abcdefgh".getBytes());
	private final UniqueTokenValue updatedTokenB = new UniqueTokenValue(
			4,
			MISSING_ENTITY_ID.num(),
			new RichInstant(1_234_567L, 1),
			"eeeeee".getBytes());
	private final UniqueTokenValue tokenA = new UniqueTokenValue(
			MISSING_ENTITY_ID.num(),
			MISSING_ENTITY_ID.num(),
			MISSING_INSTANT,
			"HI".getBytes(StandardCharsets.UTF_8));
	private final UniqueTokenValue tokenB = new UniqueTokenValue(
			MISSING_ENTITY_ID.num(),
			MISSING_ENTITY_ID.num(),
			MISSING_INSTANT,
			"IH".getBytes(StandardCharsets.UTF_8));

	private VirtualMap<UniqueTokenKey, UniqueTokenValue> delegate;

	private BackingNfts subject;

	@BeforeEach
	void setUp() {
		delegate = new VirtualMapFactory(JasperDbBuilder::new).newVirtualizedUniqueTokenStorage();

		delegate.put(aKey, tokenA);
		delegate.put(bKey, tokenB);

		subject = new BackingNfts(() -> delegate);
	}

	@Test
	void checkInitialSizeIsExpected() {
		// when:
		subject = new BackingNfts(() -> delegate);

		// expect:
		assertEquals(2, subject.size());
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
		// when:
		final var mutable = subject.getRef(aNftId);

		// then:
		assertEquals(tokenA, mutable);
		assertFalse(mutable.isImmutable());
	}

	@Test
	void mutationsAreStored() {
		var mutable = subject.getRef(aNftId);
		mutable.setOwner(EntityId.fromNum(1234L));
		mutable.setSpender(EntityId.fromNum(5678L));
		mutable.setMetadata("changed!".getBytes());
		mutable.setPackedCreationTime(12345L);

		var immutable = subject.getImmutableRef(aNftId);
		assertThat(immutable.getOwnerAccountNum()).isEqualTo(1234L);
		assertThat(immutable.getSpender().num()).isEqualTo(5678L);
		assertThat(immutable.getMetadata()).isEqualTo("changed!".getBytes());
		assertThat(immutable.getPackedCreationTime()).isEqualTo(12345L);
	}

	@Test
	void getImmutableRefDelegatesToGet() {
		// when:
		final var immutableA = subject.getImmutableRef(aNftId);
		final var immutableB = subject.getImmutableRef(bNftId);

		// then:
		assertEquals(tokenA, immutableA);
		assertEquals(tokenB, immutableB);
	}

	@Test
	void putWorks() {
		// when:
		subject.put(aNftId, updatedTokenA);
		subject.put(cNftId, updatedTokenA);

		// then:
		assertEquals(updatedTokenA, subject.getImmutableRef(cNftId));

		// Overwrites should not replace the existing token value.
		assertEquals(tokenA, subject.getImmutableRef(aNftId));
	}

	@Test
	void removeWorks() {
		// when:
		subject.remove(aNftId);

		// then:
		assertFalse(subject.contains(aNftId));
	}

	@Test
	void sizePropagatesCallToDelegate() {
		assertEquals(delegate.size(), subject.size());
	}

	@Test
	void idSetShouldThrowUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, subject::idSet);
	}
}
