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
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.PermHashInteger;
import com.hedera.services.utils.PermHashLong;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;

import java.util.function.Supplier;

/**
 * Defines a type able to build a {@link UniqTokenView} from multiple sources of token-related information.
 */
@FunctionalInterface
public interface UniqTokenViewFactory {
	/**
	 * Returns a queryable view of the unique tokens contained in the given sources of token-related information.
	 *
	 * @param tokenStore
	 * 		the store used to manipulate token types in the world state
	 * @param tokens
	 * 		the token types in the world state
	 * @param nfts
	 * 		the unique tokens in the world state
	 * @param nftsByType
	 * 		the unique tokens associated to each non-fungible unique token type
	 * @param nftsByOwner
	 * 		the unique tokens associated to owning account
	 * @param treasuryNftsByType
	 * 		the unique tokens associated to their token type's treasury
	 * @return a queryable view of the unique tokens in the given sources
	 */
	UniqTokenView viewFor(
			TokenStore tokenStore,
			Supplier<MerkleMap<PermHashInteger, MerkleToken>> tokens,
			Supplier<MerkleMap<PermHashLong, MerkleUniqueToken>> nfts,
			Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByType,
			Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByOwner,
			Supplier<FCOneToManyRelation<PermHashInteger, Long>> treasuryNftsByType);
}
