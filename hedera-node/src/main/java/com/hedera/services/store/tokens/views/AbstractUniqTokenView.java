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
import com.hedera.services.store.tokens.views.utils.GrpcUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.function.Supplier;

public abstract class AbstractUniqTokenView implements UniqTokenView {
	protected final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;
	protected final Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts;
	protected final Supplier<FCOneToManyRelation<EntityId,MerkleUniqueTokenId>> nftsByType;

	protected GrpcUtils grpcUtils = new GrpcUtils();

	public AbstractUniqTokenView(
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens,
			Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByType
	) {
		this.tokens = tokens;
		this.nfts = nfts;
		this.nftsByType = nftsByType;
	}

	@Override
	public List<TokenNftInfo> typedAssociations(TokenID type, long start, long end) {
		final var tokenId = EntityId.fromGrpcTokenId(type);
		final var treasuryGrpcId = treasuryOf(tokens.get(), tokenId);
		return accumulatedInfo(nftsByType.get(), tokenId, (int) start, (int) end, type, treasuryGrpcId);
	}

	protected List<TokenNftInfo> accumulatedInfo(
			FCOneToManyRelation<EntityId, MerkleUniqueTokenId> relation,
			EntityId key,
			int start,
			int end,
			@Nullable TokenID fixedType,
			@Nullable AccountID fixedTreasury
	) {
		final var curNfts = nfts.get();
		final var curTokens = tokens.get();
		final List<TokenNftInfo> answer = new ArrayList<>();
		relation.get(key, start, end).forEachRemaining(nftId -> {
			final var nft = curNfts.get(nftId);
			if (nft == null) {
				throw new ConcurrentModificationException(nftId + " was removed during query answering");
			}
			final var type = (fixedType != null) ? fixedType : nftId.tokenId().toGrpcTokenId();
			final var treasury = nft.isTreasuryOwned()
					? ((fixedTreasury != null) ? fixedTreasury : treasuryOf(curTokens, nftId.tokenId()))
					: null;
			final var info = grpcUtils.reprOf(type, nftId.serialNumber(), nft, treasury);
			answer.add(info);
		});
		return answer;
	}

	private AccountID treasuryOf(FCMap<MerkleEntityId, MerkleToken> curTokens, EntityId tokenId) {
		final var token = curTokens.get(tokenId.asMerkle());
		if (token == null) {
			throw new ConcurrentModificationException(
					"Token " + tokenId.toAbbrevString() + " was removed during query answering");
		}
		return token.treasury().toGrpcAccountId();
	}

	void setGrpcUtils(GrpcUtils grpcUtils) {
		this.grpcUtils = grpcUtils;
	}
}
