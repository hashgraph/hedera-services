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

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static com.hedera.services.store.tokens.views.UniqueTokenViewsManager.TargetFcotmr.NFTS_BY_OWNER;
import static com.hedera.services.store.tokens.views.UniqueTokenViewsManager.TargetFcotmr.NFTS_BY_TYPE;
import static com.hedera.services.store.tokens.views.UniqueTokenViewsManager.TargetFcotmr.TREASURY_NFTS_BY_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class UniqTokenViewsManagerTest {
	@Mock
	private FCOneToManyRelation<EntityNum, Long> nftsByType;
	@Mock
	private FCOneToManyRelation<EntityNum, Long> nftsByOwner;
	@Mock
	private FCOneToManyRelation<EntityNum, Long> treasuryNftsByType;

	private MerkleMap<EntityNum, MerkleToken> realTokens;
	private MerkleMap<EntityNumPair, MerkleUniqueToken> realNfts;

	private UniqueTokenViewsManager subject;

	@Test
	void everythingNoopsWithTreasuryTrackingSubject() {
		setupNoopTreasuryTrackingSubject();

		// expect:
		assertFalse(subject.isInTransaction());
		assertTrue(subject.isUsingTreasuryWildcards());

		// and when:
		subject.begin();
		subject.commit();
		subject.rollback();
		// and:
		subject.rebuildNotice(null, null);
		subject.mintNotice(null, null);
		subject.wipeNotice(null, null);
		subject.burnNotice(null, null);
		subject.exchangeNotice(null, null, null);
		subject.treasuryExitNotice(null, null, null);
		subject.treasuryReturnNotice(null, null, null);

		// then:
		verifyNoInteractions(nftsByType);
		verifyNoInteractions(nftsByOwner);
		verifyNoInteractions(treasuryNftsByType);
	}

	@Test
	void everythingNoopsWithNonTreasuryTrackingSubject() {
		setupNoopNonTreasuryTrackingSubject();

		// expect:
		assertFalse(subject.isInTransaction());
		assertFalse(subject.isUsingTreasuryWildcards());

		// and when:
		subject.begin();
		subject.commit();
		subject.rollback();
		// and:
		subject.rebuildNotice(null, null);
		subject.mintNotice(null, null);
		subject.wipeNotice(null, null);
		subject.burnNotice(null, null);
		subject.exchangeNotice(null, null, null);
		subject.treasuryExitNotice(null, null, null);
		subject.treasuryReturnNotice(null, null, null);

		// then:
		verifyNoInteractions(nftsByType);
		verifyNoInteractions(nftsByOwner);
		verifyNoInteractions(treasuryNftsByType);
	}

	@Test
	void beginWorks() {
		setupTreasuryTrackingSubject();

		// when:
		subject.begin();

		// then:
		assertTrue(subject.isInTransaction());
		// and expect:
		assertThrows(IllegalStateException.class, subject::begin);
	}

	@Test
	void rollbackWorks() {
		setupTreasuryTrackingSubject();
		// and:
		subject.getChangesInTxn().add(change(NFTS_BY_OWNER, 1, 2L, true));

		// expect:
		assertThrows(IllegalStateException.class, subject::rollback);

		// when:
		subject.begin();
		// and:
		subject.rollback();

		// then:
		assertFalse(subject.isInTransaction());
		assertTrue(subject.getChangesInTxn().isEmpty());
	}

	@Test
	void commitWorksWithTreasuryTracking() {
		setupTreasuryTrackingSubject();

		// expect:
		assertThrows(IllegalStateException.class, subject::commit);

		// when:
		subject.begin();
		// and:
		subject.getChangesInTxn().add(change(NFTS_BY_TYPE, 1, 2L, true));
		subject.getChangesInTxn().add(change(NFTS_BY_OWNER, 2, 3L, true));
		subject.getChangesInTxn().add(change(TREASURY_NFTS_BY_TYPE, 3, 4L, true));
		subject.getChangesInTxn().add(change(TREASURY_NFTS_BY_TYPE, 4, 5L, false));
		// and:
		subject.commit();

		// then:
		verify(nftsByType).associate(asPhi(1), 2L);
		verify(nftsByOwner).associate(asPhi(2), 3L);
		verify(treasuryNftsByType).associate(asPhi(3), 4L);
		verify(treasuryNftsByType).disassociate(asPhi(4), 5L);
		// and:
		assertFalse(subject.isInTransaction());
		assertTrue(subject.getChangesInTxn().isEmpty());
	}

	@Test
	void doChangeAlsoWorksForNftsByTypeForCompleteness() {
		setupTreasuryTrackingSubject();

		// when:
		subject.doChange(NFTS_BY_TYPE, 1, 2L, true);
		subject.doChange(NFTS_BY_TYPE, 2, 3L, false);

		// then:
		verify(nftsByType).associate(asPhi(1), 2L);
		verify(nftsByType).disassociate(asPhi(2), 3L);
	}

	@Test
	void treasuryExitWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.treasuryExitNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(asPhi(firstOwner.identityCode()), aOneNftId.value());
		verify(nftsByOwner).associate(asPhi(secondOwner.identityCode()), aOneNftId.value());
	}

	@Test
	void treasuryExitWorksWithExplicitOwnersInTxn() {
		setupNonTreasuryTrackingSubject();

		// given:
		subject.begin();

		// when:
		subject.treasuryExitNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner, never()).disassociate(asPhi(firstOwner.identityCode()), aOneNftId.value());
		verify(nftsByOwner, never()).associate(asPhi(secondOwner.identityCode()), aOneNftId.value());

		// and when:
		subject.commit();

		// then:
		verify(nftsByOwner).disassociate(asPhi(firstOwner.identityCode()), aOneNftId.value());
		verify(nftsByOwner).associate(asPhi(secondOwner.identityCode()), aOneNftId.value());
	}

	@Test
	void treasuryExitWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.treasuryExitNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(treasuryNftsByType).disassociate(asPhi(aTokenId.identityCode()), aOneNftId.value());
		verify(nftsByOwner).associate(asPhi(secondOwner.identityCode()), aOneNftId.value());
	}

	@Test
	void treasuryReturnWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.treasuryReturnNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(asPhi(firstOwner.identityCode()), aOneNftId.value());
		verify(nftsByOwner).associate(asPhi(secondOwner.identityCode()), aOneNftId.value());
	}

	@Test
	void treasuryReturnWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.treasuryReturnNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(asPhi(firstOwner.identityCode()), aOneNftId.value());
		verify(treasuryNftsByType).associate(asPhi(aTokenId.identityCode()), aOneNftId.value());
	}

	@Test
	void treasuryReturnWorksWithTreasuryWildcardsInTxn() {
		setupTreasuryTrackingSubject();

		// given:
		subject.begin();

		// when:
		subject.treasuryReturnNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner, never()).disassociate(asPhi(firstOwner.identityCode()), aOneNftId.value());
		verify(treasuryNftsByType, never()).associate(asPhi(aTokenId.identityCode()), aOneNftId.value());

		// and when:
		subject.commit();

		// then:
		verify(nftsByOwner).disassociate(asPhi(firstOwner.identityCode()), aOneNftId.value());
		verify(treasuryNftsByType).associate(asPhi(aTokenId.identityCode()), aOneNftId.value());
	}

	@Test
	void exchangeWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.exchangeNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(asPhi(firstOwner.identityCode()), aOneNftId.value());
		verify(nftsByOwner).associate(asPhi(secondOwner.identityCode()), aOneNftId.value());
	}

	@Test
	void exchangeWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.exchangeNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(asPhi(firstOwner.identityCode()), aOneNftId.value());
		verify(nftsByOwner).associate(asPhi(secondOwner.identityCode()), aOneNftId.value());
	}

	@Test
	void exchangeWorksWithTreasuryWildcardsViaTxn() {
		setupTreasuryTrackingSubject();

		// given:
		subject.begin();

		// when:
		subject.exchangeNotice(aOneNftId, firstOwner, secondOwner);
		// then:
		verify(nftsByOwner, never()).disassociate(asPhi(firstOwner.identityCode()), aOneNftId.value());
		verify(nftsByOwner, never()).associate(asPhi(secondOwner.identityCode()), aOneNftId.value());

		// and when:
		subject.commit();

		// then:
		verify(nftsByOwner).disassociate(EntityNum.fromLong(firstOwner.num()), aOneNftId.value());
		verify(nftsByOwner).associate(EntityNum.fromLong(secondOwner.num()), aOneNftId.value());
	}

	@Test
	void burnWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.burnNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).disassociate(EntityNum.fromLong(aTokenId.num()), aOneNftId.value());
		verify(nftsByOwner).disassociate(EntityNum.fromLong(firstOwner.num()), aOneNftId.value());
	}

	@Test
	void burnWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.burnNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).disassociate(EntityNum.fromLong(aTokenId.num()), aOneNftId.value());
		verify(treasuryNftsByType).disassociate(EntityNum.fromLong(aTokenId.num()), aOneNftId.value());
	}

	@Test
	void mintWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.mintNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).associate(EntityNum.fromLong(aTokenId.num()), aOneNftId.value());
		verify(nftsByOwner).associate(EntityNum.fromLong(firstOwner.num()), aOneNftId.value());
	}

	@Test
	void mintWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.mintNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).associate(EntityNum.fromLong(aTokenId.identityCode()), aOneNftId.value());
		verify(treasuryNftsByType).associate(EntityNum.fromLong(aTokenId.identityCode()), aOneNftId.value());
	}

	@Test
	void wipeWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.wipeNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).disassociate(EntityNum.fromLong(aTokenId.num()), aOneNftId.value());
		verify(nftsByOwner).disassociate(EntityNum.fromLong(firstOwner.num()), aOneNftId.value());
	}

	@Test
	void wipeWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.wipeNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).disassociate(EntityNum.fromLong(aTokenId.num()), aOneNftId.value());
		verify(nftsByOwner).disassociate(EntityNum.fromLong(firstOwner.num()), aOneNftId.value());
	}

	@Test
	void treasuryTrackingSafelyRebuildsAndDifferentiatesTreasuryOwned() throws ConstructableRegistryException {
		setupTreasuryTrackingSubject();

		// expect:
		assertTrue(subject.isUsingTreasuryWildcards());

		// and:
		givenRealNfts();

		// when:
		subject.rebuildNotice(realTokens, realNfts);

		// then:
		verify(nftsByType).associate(EntityNum.fromLong(aTokenId.num()), aOneNftId.value());
		verify(treasuryNftsByType).associate(EntityNum.fromLong(aTokenId.num()), aOneNftId.value());
		// and:
		verify(nftsByType).associate(EntityNum.fromLong(bTokenId.num()), bOneNftId.value());
		verify(nftsByOwner).associate(EntityNum.fromLong(firstOwner.num()), bOneNftId.value());
		// and:
		verifyNoMoreInteractions(nftsByType);
		verifyNoMoreInteractions(treasuryNftsByType);
	}

	@Test
	void nonTreasuryTrackingRebuildsEverythingExplicitly() throws ConstructableRegistryException {
		setupNonTreasuryTrackingSubject();

		// expect:
		assertFalse(subject.isUsingTreasuryWildcards());

		// and:
		givenRealNfts();

		// when:
		subject.rebuildNotice(realTokens, realNfts);

		// then:
		verify(nftsByType).associate(EntityNum.fromLong(aTokenId.num()), aOneNftId.value());
		verify(nftsByOwner).associate(EntityNum.fromLong(firstOwner.num()), aOneNftId.value());
		// and:
		verify(nftsByType).associate(EntityNum.fromLong(bTokenId.num()), bOneNftId.value());
		verify(nftsByOwner).associate(EntityNum.fromLong(firstOwner.num()), bOneNftId.value());
		// and:
		verifyNoMoreInteractions(nftsByType);
		verifyNoMoreInteractions(nftsByOwner);
	}

	@Test
	void toStringAsExpected() {
		// setup:
		final var desired = "PendingChange[targetFcotmr=NFTS_BY_TYPE, keyCode=1, valueCode=2, associate=true]";

		// given:
		final var c = change(NFTS_BY_TYPE, 1, 2L, true);

		// expect:
		assertEquals(desired, c.toString());
	}

	private void givenRealNfts() {
		realNfts = new MerkleMap<>();
		realNfts.put(aOneNftId, firstOwnedANft);
		realNfts.put(bOneNftId, firstOwnedBNft);
		realNfts.put(missingTokenNftId, tokenDeletedNft);

		realTokens = new MerkleMap<>();
		realTokens.put(EntityNum.fromLong(aTokenId.num()), aToken);
		realTokens.put(EntityNum.fromLong(bTokenId.num()), bToken);
	}

	private void setupTreasuryTrackingSubject() {
		subject = new UniqueTokenViewsManager(
				() -> nftsByType, () -> nftsByOwner, () -> treasuryNftsByType, false, true);
	}

	private void setupNonTreasuryTrackingSubject() {
		subject = new UniqueTokenViewsManager(
				() -> nftsByType, () -> nftsByOwner, () -> treasuryNftsByType, false, false);
	}

	private void setupNoopTreasuryTrackingSubject() {
		subject = new UniqueTokenViewsManager(
				() -> nftsByType, () -> nftsByOwner, () -> treasuryNftsByType, true, true);
	}

	private void setupNoopNonTreasuryTrackingSubject() {
		subject = new UniqueTokenViewsManager(
				() -> nftsByType, () -> nftsByOwner, () -> treasuryNftsByType, true, false);
	}

	private PendingChange change(
			UniqueTokenViewsManager.TargetFcotmr target,
			int keyCode,
			long valueCode,
			boolean associate
	) {
		return new PendingChange(target, keyCode, valueCode, associate);
	}

	private final EntityId firstOwner = new EntityId(0, 0, 3);
	private final EntityId secondOwner = new EntityId(0, 0, 4);
	private final EntityId aTokenId = new EntityId(0, 0, 5);
	private final EntityId bTokenId = new EntityId(0, 0, 6);
	private final EntityId cTokenId = new EntityId(0, 0, 7);
	private final EntityNumPair aOneNftId = EntityNumPair.fromLongs(aTokenId.num(), 1L);
	private final EntityNumPair bOneNftId = EntityNumPair.fromLongs(bTokenId.num(), 1L);
	private final EntityNumPair missingTokenNftId = EntityNumPair.fromLongs(cTokenId.num(), 666L);
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
	private final MerkleUniqueToken firstOwnedANft = new MerkleUniqueToken(MISSING_ENTITY_ID, someMeta,
			MISSING_INSTANT);
	private final MerkleUniqueToken firstOwnedBNft = new MerkleUniqueToken(firstOwner, otherMeta, MISSING_INSTANT);
	private final MerkleUniqueToken tokenDeletedNft = new MerkleUniqueToken(MISSING_ENTITY_ID, otherMeta,
			MISSING_INSTANT);

	private EntityNum asPhi(int i) {
		return new EntityNum(i);
	}
}
