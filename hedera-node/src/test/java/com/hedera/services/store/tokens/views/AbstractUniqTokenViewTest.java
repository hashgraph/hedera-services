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
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.tokens.views.utils.GrpcUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

@ExtendWith(MockitoExtension.class)
class AbstractUniqTokenViewTest {
	@Mock
	private GrpcUtils grpcUtils;
	@Mock
	private Iterator<MerkleUniqueTokenId> firstMockRange;
	@Mock
	private FCMap<MerkleEntityId, MerkleToken> tokens;
	@Mock
	private FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts;
	@Mock
	private FCOneToManyRelation<EntityId, MerkleUniqueTokenId> nftsByType;

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
		setupFirstMockRange();
		subject.setGrpcUtils(grpcUtils);

		given(nftsByType.get(someTokenId, start, end)).willReturn(firstMockRange);
		given(tokens.get(someTokenId.asMerkle())).willReturn(someToken);
		given(nfts.get(someExplicitNftId)).willReturn(someExplicitNft);
		given(nfts.get(wildcardNftId)).willReturn(wildcardNft);
		given(grpcUtils.reprOf(
				someTokenId.toGrpcTokenId(),
				someSerial,
				someExplicitNft,
				treasuryId.toGrpcAccountId())).willReturn(mockExplicitInfo);
		given(grpcUtils.reprOf(
				someTokenId.toGrpcTokenId(),
				wildcardSerial,
				wildcardNft,
				treasuryId.toGrpcAccountId())).willReturn(mockInterpolatedInfo);

		// when:
		final var actual = subject.typedAssociations(someTokenId.toGrpcTokenId(), start, end);

		// then:
		Assertions.assertEquals(List.of(mockExplicitInfo, mockInterpolatedInfo), actual);
	}

	@Test
	void throwsCmeIfIdHasNoMatchingTokenInTokenAssociations() {
		setupFirstMockRange();
		// and:
		final var desired = "MerkleUniqueTokenId{tokenId=EntityId{shard=6, realm=6, num=6}, serialNumber=1} " +
				"was removed during query answering";

		given(nftsByType.get(someTokenId, start, end)).willReturn(firstMockRange);
		given(tokens.get(someTokenId.asMerkle())).willReturn(someToken);

		// when:
		final var e = Assertions.assertThrows(ConcurrentModificationException.class, () ->
				subject.typedAssociations(someTokenId.toGrpcTokenId(), start, end));

		// then:
		Assertions.assertEquals(desired, e.getMessage());
	}

	@Test
	void throwsCmeIfIdHasNoMatchingToken() {
		// setup:
		final var desired = "Token 6.6.6 was removed during query answering";

		// when:
		final var e = Assertions.assertThrows(ConcurrentModificationException.class, () ->
				subject.typedAssociations(someTokenId.toGrpcTokenId(), start, end));

		// then:
		Assertions.assertEquals(desired, e.getMessage());
	}

	private void setupFirstMockRange() {
		willAnswer(invocationOnMock -> {
			final Consumer<MerkleUniqueTokenId> consumer = invocationOnMock.getArgument(0);
			consumer.accept(someExplicitNftId);
			consumer.accept(wildcardNftId);
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
	private final EntityId someTokenId = new EntityId(6, 6, 6);
	private final EntityId someOwnerId = new EntityId(1, 2, 3);
	private final EntityId treasuryId = new EntityId(1, 2, 3);
	private final MerkleUniqueToken someExplicitNft = new MerkleUniqueToken(someOwnerId, someMeta, someCreationTime);
	private final MerkleUniqueToken wildcardNft = new MerkleUniqueToken(MISSING_ENTITY_ID, wildMeta, someCreationTime);
	private final MerkleUniqueTokenId someExplicitNftId = new MerkleUniqueTokenId(someTokenId, someSerial);
	private final MerkleUniqueTokenId wildcardNftId = new MerkleUniqueTokenId(someTokenId, wildcardSerial);
	private final MerkleToken someToken = new MerkleToken(
			1_234_567L, 1, 2,
			"THREE", "Four",
			true, false, treasuryId);
	final TokenNftInfo mockExplicitInfo = TokenNftInfo.newBuilder()
			.setNftID(NftID.getDefaultInstance())
			.setAccountID(AccountID.getDefaultInstance())
			.build();
	final TokenNftInfo mockInterpolatedInfo = TokenNftInfo.newBuilder()
			.setNftID(NftID.getDefaultInstance())
			.setAccountID(AccountID.getDefaultInstance())
			.build();
}
