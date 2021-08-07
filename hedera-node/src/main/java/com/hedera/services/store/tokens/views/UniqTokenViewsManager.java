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
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.hedera.services.store.tokens.views.internals.PermHashInteger.asPhi;
import static com.hedera.services.utils.MiscUtils.forEach;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.runAsync;

/**
 * Keeps the {@link FCOneToManyRelation} views of the unique tokens in the world state
 * up-to-date as transactions are handled by the {@link com.hedera.services.store.TypedTokenStore}
 * and (for now) {@link com.hedera.services.store.tokens.HederaTokenStore}.
 *
 * <b>NOTE:</b> The terms "unique token" and NFT are used interchangeably in this
 * class to shorten variable names and reduce cognitive overhead.
 *
 * If constructed with a {@code treasuryNftsByType} {@link FCOneToManyRelation}, uses the
 * "dual-source" picture of NFT ownership, with an internal representation that distinguishes
 * between an account owning an NFT it received it via a {@link com.hederahashgraph.api.proto.java.NftTransfer},
 * and an account owning an NFT because it is the designated treasury for the NFT's token type.
 */
public class UniqTokenViewsManager {
	private final Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByType;
	private final Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByOwner;
	private final Supplier<FCOneToManyRelation<PermHashInteger, Long>> treasuryNftsByType;

	public UniqTokenViewsManager(
			Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByType,
			Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByOwner,
			Supplier<FCOneToManyRelation<PermHashInteger, Long>> treasuryNftsByType
	) {
		this.nftsByType = nftsByType;
		this.nftsByOwner = nftsByOwner;
		this.treasuryNftsByType = treasuryNftsByType;
	}

	public UniqTokenViewsManager(
			Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByType,
			Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByOwner
	) {
		this.nftsByType = nftsByType;
		this.nftsByOwner = nftsByOwner;
		this.treasuryNftsByType = null;
	}

	/**
	 * Rebuilds the internal views of the unique tokens in the world state, given
	 * the token types and unique tokens in the world state.
	 *
	 * <b>NOTE: </b> Does not clear the internal {@link FCOneToManyRelation}s,
	 * because it would never be correct to call this method except on restart
	 * or reconnect; and in those cases, the {@link FCOneToManyRelation}s are
	 * newly created.
	 *
	 * @param tokens
	 * 		token types in the world state
	 * @param nfts
	 * 		unique tokens in the world state
	 */
	public void rebuildNotice(
			FCMap<MerkleEntityId, MerkleToken> tokens,
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts
	) {
		final CompletableFuture<Void> futureRebuild = allOf(rebuildFutures(tokens, nfts));

		futureRebuild.join();
	}

	/**
	 * Updates the internal view of the unique tokens in the world state to reflect
	 * the minting of an NFT with the given id in the given treasury.
	 *
	 * @param nftId
	 * 		the minted id
	 * @param treasury
	 * 		the treasury that received the new NFT
	 */
	public void mintNotice(MerkleUniqueTokenId nftId, EntityId treasury) {
		final var tokenId = nftId.tokenId();
		nftsByType.get().associate(asPhi(tokenId.identityCode()), nftId.identityCode());
		if (isUsingTreasuryWildcards()) {
			curTreasuryNftsByType().associate(asPhi(tokenId.identityCode()), nftId.identityCode());
		} else {
			nftsByOwner.get().associate(asPhi(treasury.identityCode()), nftId.identityCode());
		}
	}

	/**
	 * Updates the internal view of the unique tokens in the world state to reflect
	 * the wiping of an NFT with the given id from the given account.
	 *
	 * @param nftId
	 * 		the wiped id
	 * @param fromAccount
	 * 		the account that was wiped
	 */
	public void wipeNotice(MerkleUniqueTokenId nftId, EntityId fromAccount) {
		/* The treasury account cannot be wiped, so both cases are the same */
		nftsByType.get().disassociate(asPhi(nftId.tokenId().identityCode()), nftId.identityCode());
		nftsByOwner.get().disassociate(asPhi(fromAccount.identityCode()), nftId.identityCode());
	}

	/**
	 * Updates the internal view of the unique tokens in the world state to reflect
	 * the burning of an NFT with the given id from the given treasury.
	 *
	 * @param nftId
	 * 		the burned id
	 * @param treasury
	 * 		the treasury of the burned NFT's token type
	 */
	public void burnNotice(MerkleUniqueTokenId nftId, EntityId treasury) {
		final var tokenId = nftId.tokenId();
		nftsByType.get().disassociate(asPhi(tokenId.identityCode()), nftId.identityCode());
		if (isUsingTreasuryWildcards()) {
			curTreasuryNftsByType().disassociate(asPhi(tokenId.identityCode()), nftId.identityCode());
		} else {
			nftsByOwner.get().disassociate(asPhi(treasury.identityCode()), nftId.identityCode());
		}
	}

	/**
	 * Updates the internal view of the unique tokens in the world state to reflect
	 * the exchange of an NFT with the given id between the given non-treasury accounts.
	 *
	 * @param nftId
	 * 		the exchanged id
	 * @param prevOwner
	 * 		the previous owner
	 * @param newOwner
	 * 		the new owner
	 */
	public void exchangeNotice(MerkleUniqueTokenId nftId, EntityId prevOwner, EntityId newOwner) {
		final var curNftsByOwner = nftsByOwner.get();
		curNftsByOwner.disassociate(asPhi(prevOwner.identityCode()), nftId.identityCode());
		curNftsByOwner.associate(asPhi(newOwner.identityCode()), nftId.identityCode());
	}

	/**
	 * Updates the internal view of the unique tokens in the world state to reflect
	 * the departure of an NFT with the given id from the given treasury to the
	 * given receiving account.
	 *
	 * @param nftId
	 * 		the id exiting the treasury
	 * @param treasury
	 * 		the relevant treasury
	 * @param newOwner
	 * 		the new owner
	 */
	public void treasuryExitNotice(MerkleUniqueTokenId nftId, EntityId treasury, EntityId newOwner) {
		final var curNftsByOwner = nftsByOwner.get();
		if (isUsingTreasuryWildcards()) {
			curTreasuryNftsByType().disassociate(asPhi(nftId.tokenId().identityCode()),
					nftId.identityCode());
		} else {
			curNftsByOwner.disassociate(asPhi(treasury.identityCode()), nftId.identityCode());
		}
		curNftsByOwner.associate(asPhi(newOwner.identityCode()), nftId.identityCode());
	}

	/**
	 * Updates the internal view of the unique tokens in the world state to reflect
	 * the return of an NFT with the given id to the given treasury from the given
	 * sending account.
	 *
	 * @param nftId
	 * 		the id returning to the treasury
	 * @param prevOwner
	 * 		the previous owner
	 * @param treasury
	 * 		the relevant treasury
	 */
	public void treasuryReturnNotice(MerkleUniqueTokenId nftId, EntityId prevOwner, EntityId treasury) {
		final var curNftsByOwner = nftsByOwner.get();
		curNftsByOwner.disassociate(asPhi(prevOwner.identityCode()), nftId.identityCode());
		if (isUsingTreasuryWildcards()) {
			curTreasuryNftsByType().associate(asPhi(nftId.tokenId().identityCode()),
					nftId.identityCode());
		} else {
			curNftsByOwner.associate(asPhi(treasury.identityCode()), nftId.identityCode());
		}
	}

	/**
	 * Indicates whether this manager uses a "dual-source" picture of NFT ownership, with an internal
	 * representation that distinguishes between an account owning an NFT it received it via a
	 * {@link com.hederahashgraph.api.proto.java.NftTransfer}, and an account owning an NFT because
	 * it is the designated treasury for the NFT's token type.
	 *
	 * @return whether this manager internally differentiates the above ownership sources
	 */
	public boolean isUsingTreasuryWildcards() {
		return treasuryNftsByType != null;
	}

	private FCOneToManyRelation<PermHashInteger, Long> curTreasuryNftsByType() {
		Objects.requireNonNull(treasuryNftsByType);
		return treasuryNftsByType.get();
	}

	private CompletableFuture<?>[] rebuildFutures(
			FCMap<MerkleEntityId, MerkleToken> tokens,
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts
	) {
		if (isUsingTreasuryWildcards()) {
			return new CompletableFuture<?>[] {
					runAsync(() -> rebuildNftsByType(tokens, nfts)),
					runAsync(() -> rebuildTreasuryNftsByType(tokens, nfts)),
					runAsync(() -> rebuildNonTreasuryNftsByOwner(tokens, nfts))
			};
		} else {
			return new CompletableFuture<?>[] {
					runAsync(() -> rebuildNftsByType(tokens, nfts)),
					runAsync(() -> rebuildAllNftsByOwner(tokens, nfts))
			};
		}
	}

	private void rebuildTreasuryNftsByType(
			FCMap<MerkleEntityId, MerkleToken> tokens,
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts
	) {
		final var curTreasuryNftsByType = curTreasuryNftsByType();
		forEach(nfts, (nftId, nft) -> {
			if (nft.isTreasuryOwned()) {
				final var tokenId = nftId.tokenId();
				if (!tokens.containsKey(tokenId.asMerkle())) {
					return;
				}
				curTreasuryNftsByType.associate(asPhi(tokenId.identityCode()), nftId.identityCode());
			}
		});
	}

	private void rebuildNonTreasuryNftsByOwner(
			FCMap<MerkleEntityId, MerkleToken> tokens,
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts
	) {
		final var curNftsByOwner = nftsByOwner.get();
		forEach(nfts, (nftId, nft) -> {
			if (!tokens.containsKey(nftId.tokenId().asMerkle())) {
				return;
			}
			if (!nft.isTreasuryOwned()) {
				curNftsByOwner.associate(asPhi(nft.getOwner().identityCode()), nftId.identityCode());
			}
		});
	}

	private void rebuildAllNftsByOwner(
			FCMap<MerkleEntityId, MerkleToken> tokens,
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts
	) {
		final var curNftsByOwner = nftsByOwner.get();
		forEach(nfts, (nftId, nft) -> {
			final var merkleTokenId = nftId.tokenId().asMerkle();
			if (!tokens.containsKey(merkleTokenId)) {
				return;
			}
			if (nft.isTreasuryOwned()) {
				final var token = tokens.get(merkleTokenId);
				curNftsByOwner.associate(asPhi(token.treasury().identityCode()), nftId.identityCode());
			} else {
				curNftsByOwner.associate(asPhi(nft.getOwner().identityCode()), nftId.identityCode());
			}
		});
	}

	private void rebuildNftsByType(
			FCMap<MerkleEntityId, MerkleToken> tokens,
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts
	) {
		final var curNftsByType = nftsByType.get();
		forEach(nfts, (nftId, nft) -> {
			final var tokenId = nftId.tokenId();
			if (!tokens.containsKey(tokenId.asMerkle())) {
				return;
			}
			curNftsByType.associate(asPhi(tokenId.identityCode()), nftId.identityCode());
		});
	}
}
