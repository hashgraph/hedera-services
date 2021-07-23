package com.hedera.services.store.tokens;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;

import java.util.Iterator;
import java.util.function.Supplier;

public class UniqTokenViewsManager {
	private final Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByType;
	private final Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner;
	private final Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> treasuryNftsByType;

	public UniqTokenViewsManager(
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByType,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> treasuryNftsByType
	) {
		this.nftsByType = nftsByType;
		this.nftsByOwner = nftsByOwner;
		this.treasuryNftsByType = treasuryNftsByType;
	}

	public void rebuildNotice(
			FCMap<MerkleEntityId, MerkleToken> tokens,
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts
	) {
		final var curNftsByType = nftsByType.get();
		final var curNftsByOwner = nftsByOwner.get();
		final var curTreasuryNftsByType = treasuryNftsByType.get();

		nfts.forEach((nftId, nft) -> {
			final var tokenId = nftId.tokenId();
			final var token = tokens.get(tokenId.asMerkle());
			if (token == null) {
				return;
			}
			curNftsByType.associate(tokenId, nftId);
			final var ownerId = nft.getOwner();
			final var isTreasuryOwned = token.treasury().equals(ownerId);
			if (isTreasuryOwned) {
				curTreasuryNftsByType.associate(tokenId, nftId);
			} else {
				curNftsByOwner.associate(ownerId, nftId);
			}
		});
	}

	public void mintNotice(MerkleUniqueTokenId nft, EntityId firstOwner) {
		throw new AssertionError("Not implemented!");
	}

	public void wipeNotice(MerkleUniqueTokenId nft, EntityId lastOwner) {
		throw new AssertionError("Not implemented!");
	}

	public void burnNotice(MerkleUniqueTokenId nft) {
		throw new AssertionError("Not implemented!");
	}

	public void nonTreasuryExchangeNotice(MerkleUniqueTokenId nft, EntityId prevOwner, EntityId newOwner) {
		throw new AssertionError("Not implemented!");
	}

	public void treasuryExitNotice(MerkleUniqueTokenId nft, EntityId newOwner) {
		throw new AssertionError("Not implemented!");
	}

	public void treasuryReturnNotice(MerkleUniqueTokenId nft, EntityId prevOwner) {
		throw new AssertionError("Not implemented!");
	}

	public Iterator<MerkleUniqueTokenId> ownedAssociations(EntityId owner, int start, int end) {
		throw new AssertionError("Not implemented!");
	}

	public Iterator<MerkleUniqueTokenId> typedAssociations(EntityId type, int start, int end) {
		throw new AssertionError("Not implemented!");
	}
}
