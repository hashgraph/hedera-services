package com.hedera.services.store.tokens;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.function.BiConsumer;

import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UniqTokenViewsManagerTest {
	@Mock
	private Iterator<MerkleUniqueTokenId> expectedIterator;
	@Mock
	private FCMap<MerkleEntityId, MerkleToken> tokens;
	@Mock
	private FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts;
	@Mock
	private FCOneToManyRelation<EntityId, MerkleUniqueTokenId> nftsByType;
	@Mock
	private FCOneToManyRelation<EntityId, MerkleUniqueTokenId> nftsByOwner;
	@Mock
	private FCOneToManyRelation<EntityId, MerkleUniqueTokenId> treasuryNftsByType;

	private UniqTokenViewsManager subject;

	@BeforeEach
	void setUp() {
		subject = new UniqTokenViewsManager(() -> nftsByType, () -> nftsByOwner, () -> treasuryNftsByType);
	}

	@Test
	void safelyRebuildDifferentiatesTreasuryOwned() {
		// given:
		willAnswer(invocationOnMock -> {
			final BiConsumer<MerkleUniqueTokenId, MerkleUniqueToken> consumer = invocationOnMock.getArgument(0);
			consumer.accept(aOneNftId, firstOwnedANft);
			consumer.accept(bOneNftId, firstOwnedBNft);
			consumer.accept(missingTokenNftId, tokenDeletedNft);
			return null;
		}).given(nfts).forEach(any());
		// and:
		given(tokens.get(aTokenId.asMerkle())).willReturn(aToken);
		given(tokens.get(bTokenId.asMerkle())).willReturn(bToken);

		// when:
		subject.rebuildNotice(tokens, nfts);

		// then:
		verify(nftsByType).associate(aTokenId, aOneNftId);
		verify(treasuryNftsByType).associate(aTokenId, aOneNftId);
		// and:
		verify(nftsByType).associate(bTokenId, bOneNftId);
		verify(nftsByOwner).associate(firstOwner, bOneNftId);
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
	private final MerkleUniqueToken firstOwnedANft = new MerkleUniqueToken(firstOwner, someMeta, MISSING_INSTANT);
	private final MerkleUniqueToken firstOwnedBNft = new MerkleUniqueToken(firstOwner, otherMeta, MISSING_INSTANT);
	private final MerkleUniqueToken tokenDeletedNft = new MerkleUniqueToken(secondOwner, otherMeta, MISSING_INSTANT);
}