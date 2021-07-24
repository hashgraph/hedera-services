package com.hedera.services.store.tokens;

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

import java.util.function.Supplier;

@ExtendWith(MockitoExtension.class)
class UniqTokenViewFactoryTest {
	@Mock
	private TokenStore tokenStore;
	@Mock
	private Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts;
	@Mock
	private Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;
	@Mock
	private Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByType;
	@Mock
	private Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner;
	@Mock
	private Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> treasuryNftsByType;

	@Test
	void constructsExplicitIfNotUsingWildcards() {
		// given:
		final var subject = new UniqTokenViewFactory(false);

		// when:
		final var view = subject.viewFor(
				tokenStore, tokens, nfts, nftsByType, nftsByOwner, treasuryNftsByType);

		// then:
		Assertions.assertTrue(view instanceof ExplicitOwnersUniqTokenView);
	}

	@Test
	void constructsTreasuryWildcardIfUsing() {
		// given:
		final var subject = new UniqTokenViewFactory(true);

		// when:
		final var view = subject.viewFor(
				tokenStore, tokens, nfts, nftsByType, nftsByOwner, treasuryNftsByType);

		// then:
		Assertions.assertTrue(view instanceof TreasuryWildcardsUniqTokenView);
	}
}