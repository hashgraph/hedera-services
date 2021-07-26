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
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;

import java.util.function.Supplier;

/**
 * A {@link UniqTokenViewFactory} able to construct an appropriate implementation
 * of {@link UniqTokenView} depending on the injected value of the global/static
 * {@code tokens.nfts.useTreasuryWildcards} property.
 */
public class ConfigDrivenUniqTokenViewFactory implements UniqTokenViewFactory {
	private final boolean shouldUseTreasuryWildcards;

	public ConfigDrivenUniqTokenViewFactory(boolean shouldUseTreasuryWildcards) {
		this.shouldUseTreasuryWildcards = shouldUseTreasuryWildcards;
	}

	@Override
	public UniqTokenView viewFor(
			TokenStore tokenStore,
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens,
			Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByType,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> treasuryNftsByType
	) {
		if (shouldUseTreasuryWildcards) {
			return new TreasuryWildcardsUniqTokenView(
					tokenStore, tokens, nfts, nftsByType, nftsByOwner, treasuryNftsByType);
		} else {
			return new ExplicitOwnersUniqTokenView(tokens, nfts, nftsByType, nftsByOwner);
		}
	}
}
