package com.hedera.services.store.tokens;

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

public class ExplicitOwnersUniqTokenView implements UniqTokenView {
	private final Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts;
	private final Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByType;
	private final Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner;

	private GrpcUtils grpcUtils = new GrpcUtils();

	public ExplicitOwnersUniqTokenView(
			Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByType,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner
	) {
		this.nfts = nfts;
		this.nftsByType = nftsByType;
		this.nftsByOwner = nftsByOwner;
	}

	@Override
	public List<TokenNftInfo> ownedAssociations(AccountID owner, long start, long end) {
		final var accountId = EntityId.fromGrpcAccountId(owner);
		return accumulatedInfo(nftsByOwner.get(), accountId, (int) start, (int) end, null);
	}

	@Override
	public List<TokenNftInfo> typedAssociations(TokenID type, long start, long end) {
		final var tokenId = EntityId.fromGrpcTokenId(type);
		return accumulatedInfo(nftsByType.get(), tokenId, (int) start, (int) end, type);
	}

	private List<TokenNftInfo> accumulatedInfo(
			FCOneToManyRelation<EntityId, MerkleUniqueTokenId> relation,
			EntityId key,
			int start,
			int end,
			@Nullable TokenID fixedType
	) {
		final var curNfts = nfts.get();
		final List<TokenNftInfo> answer = new ArrayList<>();
		relation.get(key, start, end).forEachRemaining(nftId -> {
			final var nft = curNfts.get(nftId);
			if (nft == null) {
				throw new ConcurrentModificationException(nftId + " was removed during query answering");
			}
			final var type = (fixedType != null) ? fixedType : nftId.tokenId().toGrpcTokenId();
			final var info = grpcUtils.reprOf(type, nftId.serialNumber(), nft);
			answer.add(info);
		});
		return answer;
	}

	void setGrpcUtils(GrpcUtils grpcUtils) {
		this.grpcUtils = grpcUtils;
	}
}
