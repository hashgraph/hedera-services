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
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.hedera.services.store.tokens.views.internals.PermHashLong;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;

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
			Supplier<MerkleMap<PermHashInteger, MerkleToken>> tokens,
			Supplier<MerkleMap<PermHashLong, MerkleUniqueToken>> nfts,
			Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByType,
			Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByOwner,
			Supplier<FCOneToManyRelation<PermHashInteger, Long>> treasuryNftsByType
	) {
		return EMPTY_UNIQUE_TOKEN_VIEW;
	}
}
