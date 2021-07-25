package com.hedera.services.store.tokens;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.tokens.utils.GrpcUtils;
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
		final var treasuryGrpcId = grpcTreasuryOf(tokens.get(), tokenId);
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
			final var treasury = (fixedTreasury != null)
					? fixedTreasury
					: grpcTreasuryOf(curTokens, nftId.tokenId());
			final var info = grpcUtils.reprOf(type, nftId.serialNumber(), nft, treasury);
			answer.add(info);
		});
		return answer;
	}

	private AccountID grpcTreasuryOf(FCMap<MerkleEntityId, MerkleToken> curTokens, EntityId tokenId) {
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
