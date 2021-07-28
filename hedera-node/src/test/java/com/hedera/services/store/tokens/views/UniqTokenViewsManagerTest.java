package com.hedera.services.store.tokens.views;

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

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class UniqTokenViewsManagerTest {
	@Mock
	private FCMap<MerkleEntityId, MerkleToken> tokens;
	@Mock
	private FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts;
	@Mock
	private FCOneToManyRelation<Integer, Long> nftsByType;
	@Mock
	private FCOneToManyRelation<Integer, Long> nftsByOwner;
	@Mock
	private FCOneToManyRelation<Integer, Long> treasuryNftsByType;

	private UniqTokenViewsManager subject;

	@Test
	void treasuryExitWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.treasuryExitNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(secondOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void treasuryExitWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.treasuryExitNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(treasuryNftsByType).disassociate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(secondOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void treasuryReturnWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.treasuryReturnNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(secondOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void treasuryReturnWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.treasuryReturnNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(treasuryNftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void exchangeWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.exchangeNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(secondOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void exchangeWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.exchangeNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(secondOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void burnWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.burnNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).disassociate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void burnWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.burnNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).disassociate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(treasuryNftsByType).disassociate(aTokenId.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void mintWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.mintNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(firstOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void mintWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.mintNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(treasuryNftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void wipeWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.wipeNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).disassociate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void wipeWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.wipeNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).disassociate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void treasuryTrackingSafelyRebuildsAndDifferentiatesTreasuryOwned() {
		setupTreasuryTrackingSubject();

		// expect:
		Assertions.assertTrue(subject.isUsingTreasuryWildcards());

		// and:
		givenWellKnownNfts();
		// and:
		given(tokens.get(aTokenId.asMerkle())).willReturn(aToken);

		// when:
		subject.rebuildNotice(tokens, nfts);

		// then:
		verify(nftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(treasuryNftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
		// and:
		verify(nftsByType).associate(bTokenId.identityCode(), bOneNftId.identityCode());
		verify(nftsByOwner).associate(firstOwner.identityCode(), bOneNftId.identityCode());
		// and:
		verifyNoMoreInteractions(nftsByType);
		verifyNoMoreInteractions(treasuryNftsByType);
	}

	@Test
	void nonTreasuryTrackingRebuildsEverythingExplicitly() {
		setupNonTreasuryTrackingSubject();

		// expect:
		Assertions.assertFalse(subject.isUsingTreasuryWildcards());

		// and:
		givenWellKnownNfts();
		// and:
		given(tokens.get(aTokenId.asMerkle())).willReturn(aToken);

		// when:
		subject.rebuildNotice(tokens, nfts);

		// then:
		verify(nftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(firstOwner.identityCode(), aOneNftId.identityCode());
		// and:
		verify(nftsByType).associate(bTokenId.identityCode(), bOneNftId.identityCode());
		verify(nftsByOwner).associate(firstOwner.identityCode(), bOneNftId.identityCode());
		// and:
		verifyNoMoreInteractions(nftsByType);
		verifyNoMoreInteractions(nftsByOwner);
	}

	private void givenWellKnownNfts() {
		willAnswer(invocationOnMock -> {
			final BiConsumer<MerkleUniqueTokenId, MerkleUniqueToken> consumer = invocationOnMock.getArgument(0);
			consumer.accept(aOneNftId, firstOwnedANft);
			consumer.accept(bOneNftId, firstOwnedBNft);
			consumer.accept(missingTokenNftId, tokenDeletedNft);
			return null;
		}).given(nfts).forEach(any());
	}

	private void setupTreasuryTrackingSubject() {
		subject = new UniqTokenViewsManager(() -> nftsByType, () -> nftsByOwner, () -> treasuryNftsByType);
	}

	private void setupNonTreasuryTrackingSubject() {
		subject = new UniqTokenViewsManager(() -> nftsByType, () -> nftsByOwner);
	}

	private final EntityId firstOwner = new EntityId(1, 2, 3);
	private final EntityId secondOwner = new EntityId(2, 3, 4);
	private final EntityId aTokenId = new EntityId(3, 4, 5);
	private final EntityId bTokenId = new EntityId(4, 5, 6);
	private final EntityId cTokenId = new EntityId(5, 6, 7);
	private final MerkleUniqueTokenId aOneNftId = new MerkleUniqueTokenId(aTokenId, 1L);
	private final MerkleUniqueTokenId bOneNftId = new MerkleUniqueTokenId(bTokenId, 1L);
	private final MerkleUniqueTokenId missingTokenNftId = new MerkleUniqueTokenId(cTokenId, 666L);
	private final MerkleToken aToken = new MerkleToken(
			1_234_567L, 1_234L, 1,
			"Hi", "EVERYBODY",
			false, true, firstOwner);
	private final MerkleToken bToken = new MerkleToken(
			1_234_567L, 1_234L, 1,
			"Bye", "YOU",
			false, true, secondOwner);
	private byte[] someMeta = "SOMETHING".getBytes(StandardCharsets.UTF_8);
	private byte[] otherMeta = "ELSE".getBytes(StandardCharsets.UTF_8);
	private final MerkleUniqueToken firstOwnedANft = new MerkleUniqueToken(MISSING_ENTITY_ID, someMeta, MISSING_INSTANT);
	private final MerkleUniqueToken firstOwnedBNft = new MerkleUniqueToken(firstOwner, otherMeta, MISSING_INSTANT);
	private final MerkleUniqueToken tokenDeletedNft = new MerkleUniqueToken(MISSING_ENTITY_ID, otherMeta, MISSING_INSTANT);
}
