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
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Supplier;

/**
 * A {@link UniqTokenView} that answers requests for an account's unique tokens
 * using only a {@code nftsByOwner} {@link FCOneToManyRelation}.
 */
public class ExplicitOwnersUniqTokenView extends AbstractUniqTokenView {
	private final Supplier<FCOneToManyRelation<EntityNum, Long>> nftsByOwner;

	public ExplicitOwnersUniqTokenView(
			Supplier<MerkleMap<EntityNum, MerkleToken>> tokens,
			Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> nfts,
			Supplier<FCOneToManyRelation<EntityNum, Long>> nftsByType,
			Supplier<FCOneToManyRelation<EntityNum, Long>> nftsByOwner
	) {
		super(tokens, nfts, nftsByType);

		this.nftsByOwner = nftsByOwner;
	}

	@Override
	public List<TokenNftInfo> ownedAssociations(@Nonnull EntityNum ownerId, long start, long end) {
		return accumulatedInfo(nftsByOwner.get(), ownerId, (int) start, (int) end, null, ownerId);
	}
}
