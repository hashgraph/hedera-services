package com.hedera.services.store.tokens;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.tokens.utils.GrpcUtils;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

@ExtendWith(MockitoExtension.class)
class ExplicitOwnersUniqTokenViewTest {
	@Mock
	private Iterator<MerkleUniqueTokenId> mockRange;
	@Mock
	private GrpcUtils grpcUtils;
	@Mock
	private FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts;
	@Mock
	private FCOneToManyRelation<EntityId,MerkleUniqueTokenId> nftsByType;
	@Mock
	private FCOneToManyRelation<EntityId, MerkleUniqueTokenId> nftsByOwner;

	private ExplicitOwnersUniqTokenView subject;

	@BeforeEach
	void setUp() {
		subject = new ExplicitOwnersUniqTokenView(() -> nfts, () -> nftsByType, () -> nftsByOwner);
	}

	@Test
	void returnsMockInfoFromOwnerAssociations() {
		setupMockRange();
		subject.setGrpcUtils(grpcUtils);

		given(nftsByOwner.get(someOwner, start, end)).willReturn(mockRange);
		given(nfts.get(someNftId)).willReturn(someNft);
		given(grpcUtils.reprOf(token.toGrpcTokenId(), someSerial, someNft)).willReturn(mockInfo);

		// when:
		final var actual = subject.ownedAssociations(someOwner.toGrpcAccountId(), start, end);

		// then:
		Assertions.assertEquals(List.of(mockInfo), actual);
	}

	@Test
	void returnsMockInfoFromTokenAssociations() {
		setupMockRange();
		subject.setGrpcUtils(grpcUtils);

		given(nftsByType.get(token, start, end)).willReturn(mockRange);
		given(nfts.get(someNftId)).willReturn(someNft);
		given(grpcUtils.reprOf(token.toGrpcTokenId(), someSerial, someNft)).willReturn(mockInfo);

		// when:
		final var actual = subject.typedAssociations(token.toGrpcTokenId(), start, end);

		// then:
		Assertions.assertEquals(List.of(mockInfo), actual);
	}

	@Test
	void throwsCmeIfIdHasNoMatchingTokenInTokenAssociations() {
		setupMockRange();
		// and:
		final var desired = "MerkleUniqueTokenId{tokenId=EntityId{shard=6, realm=6, num=6}, serialNumber=1} " +
				"was removed during query answering";

		given(nftsByType.get(token, start, end)).willReturn(mockRange);

		// when:
		final var e = Assertions.assertThrows(ConcurrentModificationException.class, () ->
				subject.typedAssociations(token.toGrpcTokenId(), start, end));

		// then:
		Assertions.assertEquals(desired, e.getMessage());
	}

	private void setupMockRange() {
		willAnswer(invocationOnMock -> {
			final Consumer<MerkleUniqueTokenId> consumer = invocationOnMock.getArgument(0);
			consumer.accept(someNftId);
			return null;
		}).given(mockRange).forEachRemaining(any());
	}

	private final int start = 123;
	private final int end = 456;
	private final long someSerial = 1L;
	private final byte[] someMeta = "As you wish...".getBytes(StandardCharsets.UTF_8);
	private final RichInstant someCreationTime = new RichInstant(1_234_567L, 890);
	private final EntityId token = new EntityId(6, 6, 6);
	private final EntityId someOwner = new EntityId(1, 2, 3);
	private final MerkleUniqueToken someNft = new MerkleUniqueToken(someOwner, someMeta, someCreationTime);
	private final MerkleUniqueTokenId someNftId = new MerkleUniqueTokenId(token, someSerial);
	final TokenNftInfo mockInfo = TokenNftInfo.newBuilder()
			.setNftID(NftID.getDefaultInstance())
			.setAccountID(AccountID.getDefaultInstance())
			.build();
}