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
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.store.tokens.views.utils.GrpcUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.utils.EntityNum.fromInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

@ExtendWith(MockitoExtension.class)
class TreasuryWildcardsUniqTokenViewTest {
	@Mock
	private Iterator<Long> firstMockRange;
	@Mock
	private Iterator<Long> secondMockRange;
	@Mock
	private TokenStore tokenStore;
	@Mock
	private MerkleMap<EntityNum, MerkleToken> tokens;
	@Mock
	private MerkleMap<EntityNumPair, MerkleUniqueToken> nfts;
	@Mock
	private FCOneToManyRelation<EntityNum, Long> nftsByType;
	@Mock
	private FCOneToManyRelation<EntityNum, Long> nftsByOwner;
	@Mock
	private FCOneToManyRelation<EntityNum, Long> treasuryNftsByType;

	private TreasuryWildcardsUniqTokenView subject;

	@BeforeEach
	void setUp() {
		subject = new TreasuryWildcardsUniqTokenView(
				tokenStore, () -> tokens, () -> nfts, () -> nftsByType, () -> nftsByOwner, () -> treasuryNftsByType);
	}

	@Test
	void withNoTreasuriesWorksSameAsExplicitOwners() {
		setupFirstMockRange();

		given(nftsByOwner.getCount(fromInt(ownerId.identityCode()))).willReturn(end + 1);
		given(nftsByOwner.get(fromInt(ownerId.identityCode()), start, end)).willReturn(firstMockRange);
		given(nfts.get(someExplicitNftId)).willReturn(someExplicitNft);
		given(nfts.get(wildcardNftId)).willReturn(wildcardNft);

		final var actual = subject.ownedAssociations(owner, start, end);

		Assertions.assertEquals(List.of(explicitInfo, interpolatedInfo), actual);
	}

	@Test
	void withTreasuriesMergesMultiSource() {
		setupFirstMockRange();
		setupSecondMockRange();

		given(nftsByOwner.getCount(fromInt(ownerId.identityCode()))).willReturn(end - 1);
		given(treasuryNftsByType.getCount(fromInt(treasuryTokenId.identityCode()))).willReturn(1);
		given(nftsByOwner.get(fromInt(ownerId.identityCode()), start, end - 1)).willReturn(firstMockRange);
		given(treasuryNftsByType.get(fromInt(treasuryTokenId.identityCode()), 0, 1)).willReturn(secondMockRange);
		given(nfts.get(someExplicitNftId)).willReturn(someExplicitNft);
		given(nfts.get(wildcardNftId)).willReturn(wildcardNft);
		given(nfts.get(otherWildcardNftId)).willReturn(otherWildNft);
		final var owner = EntityNum.fromEntityId(ownerId);
		given(tokenStore.listOfTokensServed(owner))
				.willReturn(List.of(treasuryTokenId.toGrpcTokenId(), yTreasuryTokenId.toGrpcTokenId()));

		final var actual = subject.ownedAssociations(owner, start, end);

		Assertions.assertEquals(List.of(explicitInfo, interpolatedInfo, treasuryInfo), actual);
	}

	@Test
	void getsAllAssociationsWithRangeToSpare() {
		setupFirstMockRange();
		setupSecondMockRange();
		given(nftsByOwner.getCount(fromInt(ownerId.identityCode()))).willReturn(end - 1);
		given(treasuryNftsByType.getCount(fromInt(treasuryTokenId.identityCode()))).willReturn(1);
		given(nftsByOwner.get(fromInt(ownerId.identityCode()), start, end - 1)).willReturn(firstMockRange);
		given(treasuryNftsByType.get(fromInt(treasuryTokenId.identityCode()), 0, 1)).willReturn(secondMockRange);
		given(nfts.get(someExplicitNftId)).willReturn(someExplicitNft);
		given(nfts.get(wildcardNftId)).willReturn(wildcardNft);
		given(nfts.get(otherWildcardNftId)).willReturn(otherWildNft);
		// and:
		given(tokenStore.listOfTokensServed(owner)).willReturn(List.of(treasuryTokenId.toGrpcTokenId()));

		// when:
		final var actual = subject.ownedAssociations(owner, start, end + 1);

		// then:
		Assertions.assertEquals(List.of(explicitInfo, interpolatedInfo, treasuryInfo), actual);
	}

	private void setupFirstMockRange() {
		willAnswer(invocationOnMock -> {
			final Consumer<Long> consumer = invocationOnMock.getArgument(0);
			consumer.accept(someExplicitNftId.getValue());
			consumer.accept(wildcardNftId.getValue());
			return null;
		}).given(firstMockRange).forEachRemaining(any());
	}

	private void setupSecondMockRange() {
		willAnswer(invocationOnMock -> {
			final Consumer<Long> consumer = invocationOnMock.getArgument(0);
			consumer.accept(otherWildcardNftId.getValue());
			return null;
		}).given(secondMockRange).forEachRemaining(any());
	}

	private final int start = 123;
	private final int end = 126;
	private final long someSerial = 1L;
	private final long wildcardSerial = 2L;
	private final long treasurySerial = 3L;
	private final byte[] someMeta = "As you wish...".getBytes(StandardCharsets.UTF_8);
	private final byte[] wildMeta = "...caution to the wind, then!".getBytes(StandardCharsets.UTF_8);
	private final byte[] om = "Post-haste!".getBytes(StandardCharsets.UTF_8);
	private final RichInstant someCreationTime = new RichInstant(1_234_567L, 890);
	private final EntityId tokenId = new EntityId(0, 0, 6);
	private final EntityId otherTokenId = new EntityId(0, 0, 7);
	private final EntityId treasuryTokenId = new EntityId(0, 0, 8);
	private final EntityId yTreasuryTokenId = new EntityId(0, 0, 9);
	private final EntityId ownerId = new EntityId(0, 0, 3);
	private final AccountID grpcOwnerId = ownerId.toGrpcAccountId();
	private final EntityNum owner = EntityNum.fromEntityId(ownerId);
	private final MerkleUniqueToken someExplicitNft = new MerkleUniqueToken(ownerId, someMeta, someCreationTime);
	private final MerkleUniqueToken wildcardNft = new MerkleUniqueToken(MISSING_ENTITY_ID, wildMeta, someCreationTime);
	private final MerkleUniqueToken otherWildNft = new MerkleUniqueToken(MISSING_ENTITY_ID, om, someCreationTime);
	private final EntityNumPair someExplicitNftId = EntityNumPair.fromLongs(tokenId.num(), someSerial);
	private final EntityNumPair wildcardNftId = EntityNumPair.fromLongs(otherTokenId.num(), wildcardSerial);
	private final EntityNumPair otherWildcardNftId = EntityNumPair.fromLongs(treasuryTokenId.num(), treasurySerial);
	private final TokenNftInfo explicitInfo =
			GrpcUtils.reprOf(tokenId.toGrpcTokenId(), someSerial, someExplicitNft, null);
	private final TokenNftInfo interpolatedInfo =
			GrpcUtils.reprOf(otherTokenId.toGrpcTokenId(), wildcardSerial, wildcardNft, owner);
	private final TokenNftInfo treasuryInfo =
			GrpcUtils.reprOf(treasuryTokenId.toGrpcTokenId(), treasurySerial, otherWildNft, owner);
}
