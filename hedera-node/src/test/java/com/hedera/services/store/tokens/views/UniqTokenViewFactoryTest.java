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
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.ExplicitOwnersUniqTokenView;
import com.hedera.services.store.tokens.views.TreasuryWildcardsUniqTokenView;
import com.hedera.services.store.tokens.views.UniqTokenViewFactory;
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
