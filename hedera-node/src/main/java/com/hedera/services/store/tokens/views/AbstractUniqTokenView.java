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

/**
 * Provides implementation support for a {@link UniqTokenView} via a method able to
 * list all the unique tokens that can be retrieved from a {@link FCOneToManyRelation}
 * in a single {@link FCOneToManyRelation#get(Object, int, int)} call.
 *
 * When {@link MerkleUniqueToken#isTreasuryOwned()} returns true for a unique token,
 * this class looks up the owner from an injected {@code Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens},
 * and builds the {@link TokenNftInfo} accordingly.
 */
public abstract class AbstractUniqTokenView implements UniqTokenView {
	protected final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;
	protected final Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts;
	protected final Supplier<FCOneToManyRelation<Integer, Long>> nftsByType;

	protected GrpcUtils grpcUtils = new GrpcUtils();

	public AbstractUniqTokenView(
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens,
			Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts,
			Supplier<FCOneToManyRelation<Integer, Long>> nftsByType
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

	/**
	 * Given a {@link FCOneToManyRelation} that associates the given key to zero or
	 * more unique tokens, returns the requested sub-list of the {@link TokenNftInfo}
	 * descriptions of those unique tokens.
	 *
	 * If any unique token has the sentinel owner id {@code 0.0.0}, looks up the actual
	 * treasury id and completes the description accordingly.
	 *
	 * @param relation
	 * 		the source of the key's associated unique tokens
	 * @param key
	 * 		the key of interest
	 * @param start
	 * 		the inclusive start of the desired sub-list
	 * @param end
	 * 		the exclusive end of the desired sub-list
	 * @param fixedType
	 * 		if not null, the type to which all the unique tokens are known to belong
	 * @param fixedTreasury
	 * 		if not null, the treasury which all sentinel owner id's should be replaced with
	 * @return the requested list
	 */
	protected List<TokenNftInfo> accumulatedInfo(
			FCOneToManyRelation<Integer, Long> relation,
			EntityId key,
			int start,
			int end,
			@Nullable TokenID fixedType,
			@Nullable AccountID fixedTreasury
	) {
		final var curNfts = nfts.get();
		final var curTokens = tokens.get();
		final List<TokenNftInfo> answer = new ArrayList<>();
		relation.get(key.identityCode(), start, end).forEachRemaining(nftIdCode -> {
			final var nftId = MerkleUniqueTokenId.fromIdentityCode(nftIdCode);
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
