package com.hedera.services.store.tokens;

import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.fchashmap.FCOneToManyRelation;

import java.util.Iterator;
import java.util.function.Supplier;

public class UniqTokenViewsManager {
	private final Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByType;
	private final Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner;

	public UniqTokenViewsManager(
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByType,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner
	) {
		this.nftsByType = nftsByType;
		this.nftsByOwner = nftsByOwner;
	}

	public void createAssociation(MerkleUniqueTokenId nft, EntityId firstOwner) {
		nftsByType.get().associate(nft.tokenId(), nft);
		nftsByOwner.get().associate(firstOwner, nft);
	}

	public void forgetAssociation(MerkleUniqueTokenId nft, EntityId lastOwner) {
		nftsByType.get().disassociate(nft.tokenId(), nft);
		nftsByOwner.get().disassociate(lastOwner, nft);
	}

	public void changeAssociation(MerkleUniqueTokenId nft, EntityId prevOwner, EntityId newOwner) {
		final var currentNftsByOwner = nftsByOwner.get();
		currentNftsByOwner.disassociate(prevOwner, nft);
		currentNftsByOwner.associate(newOwner, nft);
	}

	public Iterator<MerkleUniqueTokenId> ownedAssociations(EntityId owner, int start, int end) {
		return nftsByOwner.get().get(owner, start, end);
	}

	public Iterator<MerkleUniqueTokenId> typedAssociations(EntityId type, int start, int end) {
		throw new AssertionError("Not implemented!");
	}
}
