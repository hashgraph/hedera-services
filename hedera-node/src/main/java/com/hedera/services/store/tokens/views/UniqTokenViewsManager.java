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
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;

import java.util.Objects;
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

	public UniqTokenViewsManager(
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByType,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner
	) {
		this.nftsByType = nftsByType;
		this.nftsByOwner = nftsByOwner;
		this.treasuryNftsByType = null;
	}

	public void rebuildNotice(
			FCMap<MerkleEntityId, MerkleToken> tokens,
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts
	) {
		if (isUsingTreasuryWildcards()) {
			rebuildUsingTreasuryWildcards(tokens, nfts);
		} else {
			rebuildUsingExplicitOwners(nfts);
		}
	}

	public void mintNotice(MerkleUniqueTokenId nftId, EntityId treasury) {
		final var tokenId = nftId.tokenId();
		nftsByType.get().associate(tokenId, nftId);
		if (isUsingTreasuryWildcards()) {
			curTreasuryNftsByType().associate(tokenId, nftId);
		} else {
			nftsByOwner.get().associate(treasury, nftId);
		}
	}

	public void wipeNotice(MerkleUniqueTokenId nftId, EntityId fromAccount) {
		/* The treasury account cannot wiped, so both cases are the same */
		nftsByType.get().disassociate(nftId.tokenId(), nftId);
		nftsByOwner.get().disassociate(fromAccount, nftId);
	}

	public void burnNotice(MerkleUniqueTokenId nftId, EntityId treasury) {
		final var tokenId = nftId.tokenId();
		nftsByType.get().disassociate(tokenId, nftId);
		if (isUsingTreasuryWildcards()) {
			curTreasuryNftsByType().disassociate(tokenId, nftId);
		} else {
			nftsByOwner.get().disassociate(treasury, nftId);
		}
	}

	public void exchangeNotice(MerkleUniqueTokenId nftId, EntityId prevOwner, EntityId newOwner) {
		final var curNftsByOwner = nftsByOwner.get();
		curNftsByOwner.disassociate(prevOwner, nftId);
		curNftsByOwner.associate(newOwner, nftId);
	}

	public void treasuryExitNotice(MerkleUniqueTokenId nftId, EntityId treasury, EntityId newOwner) {
		final var curNftsByOwner = nftsByOwner.get();
		if (isUsingTreasuryWildcards()) {
			curTreasuryNftsByType().disassociate(nftId.tokenId(), nftId);
		} else {
			curNftsByOwner.disassociate(treasury, nftId);
		}
		curNftsByOwner.associate(newOwner, nftId);
	}

	public void treasuryReturnNotice(MerkleUniqueTokenId nftId, EntityId prevOwner, EntityId treasury) {
		final var curNftsByOwner = nftsByOwner.get();
		curNftsByOwner.disassociate(prevOwner, nftId);
		if (isUsingTreasuryWildcards()) {
			curTreasuryNftsByType().associate(nftId.tokenId(), nftId);
		} else {
			curNftsByOwner.associate(treasury, nftId);
		}
	}

	public boolean isUsingTreasuryWildcards() {
		return treasuryNftsByType != null;
	}

	private FCOneToManyRelation<EntityId, MerkleUniqueTokenId> curTreasuryNftsByType() {
		Objects.requireNonNull(treasuryNftsByType);
		return treasuryNftsByType.get();
	}

	private void rebuildUsingTreasuryWildcards(
			FCMap<MerkleEntityId, MerkleToken> tokens,
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts
	) {
		final var curNftsByType = nftsByType.get();
		final var curNftsByOwner = nftsByOwner.get();
		final var curTreasuryNftsByType = curTreasuryNftsByType();

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

	private void rebuildUsingExplicitOwners(FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts) {
		final var curNftsByType = nftsByType.get();
		final var curNftsByOwner = nftsByOwner.get();
		nfts.forEach((nftId, nft) -> {
			curNftsByType.associate(nftId.tokenId(), nftId);
			curNftsByOwner.associate(nft.getOwner(), nftId);
		});
	}
}
