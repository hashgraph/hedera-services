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
import com.hedera.services.store.tokens.views.utils.GrpcUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
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
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.utils.EntityNum.fromInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

@ExtendWith(MockitoExtension.class)
class AbstractUniqTokenViewTest {
	@Mock
	private Iterator<Long> firstMockRange;
	@Mock
	private MerkleMap<EntityNum, MerkleToken> tokens;
	@Mock
	private MerkleMap<EntityNumPair, MerkleUniqueToken> nfts;
	@Mock
	private FCOneToManyRelation<EntityNum, Long> nftsByType;

	private AbstractUniqTokenView subject;

	@BeforeEach
	void setUp() {
		subject = new AbstractUniqTokenView(() -> tokens, () -> nfts, () -> nftsByType) {
			@Override
			public List<TokenNftInfo> ownedAssociations(AccountID owner, long start, long end) {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Test
	void interpolatesTreasuryIdForWildcard() {
		final var explicitInfo = GrpcUtils.reprOf(grpcTokenId, someSerial, someExplicitNft, null);
		final var interpolatedInfo = GrpcUtils.reprOf(grpcTokenId, wildcardSerial, wildcardNft,
				treasuryId.toGrpcAccountId());
		setupFirstMockRange();
		given(nftsByType.get(fromInt(tokenId.identityCode()), start, end)).willReturn(firstMockRange);
		given(tokens.get(EntityNum.fromLong(tokenId.num()))).willReturn(someToken);
		given(nfts.get(someExplicitNftId)).willReturn(someExplicitNft);
		given(nfts.get(wildcardNftId)).willReturn(wildcardNft);

		final var actual = subject.typedAssociations(grpcTokenId, start, end);

		Assertions.assertEquals(List.of(explicitInfo, interpolatedInfo), actual);
	}

	@Test
	void throwsCmeIfIdHasNoMatchingTokenInTokenAssociations() {
		setupFirstMockRange();
		// and:
		final var desired = "NFT was removed during query answering";

		given(nftsByType.get(fromInt(tokenId.identityCode()), start, end)).willReturn(firstMockRange);
		given(tokens.get(EntityNum.fromLong(tokenId.num()))).willReturn(someToken);

		// when:
		final var e = Assertions.assertThrows(ConcurrentModificationException.class, () ->
				subject.typedAssociations(grpcTokenId, start, end));

		// then:
		Assertions.assertEquals(desired, e.getMessage());
	}

	@Test
	void throwsCmeIfIdHasNoMatchingToken() {
		// setup:
		final var desired = "Token #6 was removed during query answering";

		// when:
		final var e = Assertions.assertThrows(ConcurrentModificationException.class, () ->
				subject.typedAssociations(grpcTokenId, start, end));

		// then:
		Assertions.assertEquals(desired, e.getMessage());
	}

	private void setupFirstMockRange() {
		willAnswer(invocationOnMock -> {
			final Consumer<Long> consumer = invocationOnMock.getArgument(0);
			consumer.accept(someExplicitNftId.value());
			consumer.accept(wildcardNftId.value());
			return null;
		}).given(firstMockRange).forEachRemaining(any());
	}

	private final int start = 123;
	private final int end = 456;
	private final long someSerial = 1L;
	private final long wildcardSerial = 2L;
	private final byte[] someMeta = "As you wish...".getBytes(StandardCharsets.UTF_8);
	private final byte[] wildMeta = "...caution to the wind, then!".getBytes(StandardCharsets.UTF_8);
	private final RichInstant someCreationTime = new RichInstant(1_234_567L, 890);
	private final EntityId tokenId = new EntityId(0, 0, 6);
	private final TokenID grpcTokenId = tokenId.toGrpcTokenId();
	private final EntityId someOwnerId = new EntityId(0, 0, 3);
	private final EntityId treasuryId = new EntityId(0, 0, 3);
	private final MerkleUniqueToken someExplicitNft = new MerkleUniqueToken(someOwnerId, someMeta, someCreationTime);
	private final MerkleUniqueToken wildcardNft = new MerkleUniqueToken(MISSING_ENTITY_ID, wildMeta, someCreationTime);
	private final EntityNumPair someExplicitNftId = EntityNumPair.fromLongs(tokenId.num(), someSerial);
	private final EntityNumPair wildcardNftId = EntityNumPair.fromLongs(tokenId.num(), wildcardSerial);
	private final MerkleToken someToken = new MerkleToken(
			1_234_567L, 1, 2,
			"THREE", "Four",
			true, false, treasuryId);
}
