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
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.PermHashInteger;
import com.hedera.services.utils.PermHashLong;
import com.hederahashgraph.api.proto.java.AccountID;
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
	private final Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByOwner;

	public ExplicitOwnersUniqTokenView(
			Supplier<MerkleMap<PermHashInteger, MerkleToken>> tokens,
			Supplier<MerkleMap<PermHashLong, MerkleUniqueToken>> nfts,
			Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByType,
			Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByOwner
	) {
		super(tokens, nfts, nftsByType);

		this.nftsByOwner = nftsByOwner;
	}

	@Override
	public List<TokenNftInfo> ownedAssociations(@Nonnull AccountID owner, long start, long end) {
		final var accountId = EntityId.fromGrpcAccountId(owner);
		return accumulatedInfo(nftsByOwner.get(), accountId, (int) start, (int) end, null, owner);
	}
}
