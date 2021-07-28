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
import com.hedera.services.store.tokens.TokenStore;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;

import java.util.function.Supplier;

import static com.hedera.services.store.tokens.views.EmptyUniqueTokenView.EMPTY_UNIQUE_TOKEN_VIEW;

/**
 * A {@link UniqTokenViewFactory} that constructs an always-empty {@link UniqTokenView}.
 */
public enum EmptyUniqTokenViewFactory implements UniqTokenViewFactory {
	EMPTY_UNIQ_TOKEN_VIEW_FACTORY;

	@Override
	public UniqTokenView viewFor(
			TokenStore tokenStore,
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens,
			Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts,
			Supplier<FCOneToManyRelation<Integer, Long>> nftsByType,
			Supplier<FCOneToManyRelation<Integer, Long>> nftsByOwner,
			Supplier<FCOneToManyRelation<Integer, Long>> treasuryNftsByType
	) {
		return EMPTY_UNIQUE_TOKEN_VIEW;
	}
}
