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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static com.hedera.services.store.tokens.views.UniqTokenViewsManager.TargetFcotmr.NFTS_BY_OWNER;
import static com.hedera.services.store.tokens.views.UniqTokenViewsManager.TargetFcotmr.TREASURY_NFTS_BY_TYPE;

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
	enum TargetFcotmr {
		NFTS_BY_TYPE, NFTS_BY_OWNER, TREASURY_NFTS_BY_TYPE
	}

	private final Supplier<FCOneToManyRelation<Integer, Long>> nftsByType;
	private final Supplier<FCOneToManyRelation<Integer, Long>> nftsByOwner;
	private final Supplier<FCOneToManyRelation<Integer, Long>> treasuryNftsByType;

	private boolean inTxn = false;
	private List<PendingChange> changesInTxn = new ArrayList<>();

	public UniqTokenViewsManager(
			Supplier<FCOneToManyRelation<Integer, Long>> nftsByType,
			Supplier<FCOneToManyRelation<Integer, Long>> nftsByOwner,
			Supplier<FCOneToManyRelation<Integer, Long>> treasuryNftsByType
	) {
		this.nftsByType = nftsByType;
		this.nftsByOwner = nftsByOwner;
		this.treasuryNftsByType = treasuryNftsByType;
	}

	public UniqTokenViewsManager(
			Supplier<FCOneToManyRelation<Integer, Long>> nftsByType,
			Supplier<FCOneToManyRelation<Integer, Long>> nftsByOwner
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
	 * 		the token types in the world state
	 * @param uniqueTokens
	 * 		unique tokens in the world state
	 */
	public void rebuildNotice(
			FCMap<MerkleEntityId, MerkleToken> tokens,
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> uniqueTokens
	) {
		if (isUsingTreasuryWildcards()) {
			rebuildUsingTreasuryWildcards(tokens, uniqueTokens);
		} else {
			rebuildUsingExplicitOwners(tokens, uniqueTokens);
		}
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
		nftsByType.get().associate(tokenId.identityCode(), nftId.identityCode());
		if (isUsingTreasuryWildcards()) {
			curTreasuryNftsByType().associate(tokenId.identityCode(), nftId.identityCode());
		} else {
			nftsByOwner.get().associate(treasury.identityCode(), nftId.identityCode());
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
		nftsByType.get().disassociate(nftId.tokenId().identityCode(), nftId.identityCode());
		nftsByOwner.get().disassociate(fromAccount.identityCode(), nftId.identityCode());
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
		nftsByType.get().disassociate(tokenId.identityCode(), nftId.identityCode());
		if (isUsingTreasuryWildcards()) {
			curTreasuryNftsByType().disassociate(tokenId.identityCode(), nftId.identityCode());
		} else {
			nftsByOwner.get().disassociate(treasury.identityCode(), nftId.identityCode());
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
		changeOrStage(NFTS_BY_OWNER, prevOwner.identityCode(), nftId.identityCode(), false);
		changeOrStage(NFTS_BY_OWNER, newOwner.identityCode(), nftId.identityCode(), true);
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
		if (isUsingTreasuryWildcards()) {
			changeOrStage(TREASURY_NFTS_BY_TYPE, nftId.tokenId().identityCode(), nftId.identityCode(), false);
		} else {
			changeOrStage(NFTS_BY_OWNER, treasury.identityCode(), nftId.identityCode(), false);
		}
		changeOrStage(NFTS_BY_OWNER, newOwner.identityCode(), nftId.identityCode(), true);
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
		changeOrStage(NFTS_BY_OWNER, prevOwner.identityCode(), nftId.identityCode(), false);
		if (isUsingTreasuryWildcards()) {
			changeOrStage(TREASURY_NFTS_BY_TYPE, nftId.tokenId().identityCode(), nftId.identityCode(), true);
		} else {
			changeOrStage(NFTS_BY_OWNER, treasury.identityCode(), nftId.identityCode(), true);
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

	/* --- Transactional semantics --- */
	public boolean isInTransaction() {
		return inTxn;
	}

	public void begin() {
		if (inTxn) {
			throw new IllegalStateException("Manager already in transaction");
		}
		inTxn = true;
	}

	public void rollback() {
		if (!inTxn) {
			throw new IllegalStateException("Manager not in transaction");
		}
		inTxn = false;
		changesInTxn.clear();
	}

	public void commit() {
		if (!inTxn) {
			throw new IllegalStateException("Manager not in transaction");
		}
		if (!changesInTxn.isEmpty()) {
			for (var change : changesInTxn) {
				doChange(change.targetFcotmr(), change.keyCode(), change.valueCode(), change.isAssociate());
			}
			changesInTxn.clear();
		}
		inTxn = false;
	}

	private void changeOrStage(TargetFcotmr targetFcotmr, int keyCode, long valueCode, boolean associate) {
		if (inTxn) {
			final var change = new PendingChange(targetFcotmr, keyCode, valueCode, associate);
			changesInTxn.add(change);
		} else {
			doChange(targetFcotmr, keyCode, valueCode, associate);
		}
	}

	private void doChange(TargetFcotmr targetFcotmr, int keyCode, long valueCode, boolean associate) {
		switch (targetFcotmr) {
			case NFTS_BY_TYPE:
				if (associate) {
					nftsByType.get().associate(keyCode, valueCode);
				} else {
					nftsByType.get().disassociate(keyCode, valueCode);
				}
				break;
			case NFTS_BY_OWNER:
				if (associate) {
					nftsByOwner.get().associate(keyCode, valueCode);
				} else {
					nftsByOwner.get().disassociate(keyCode, valueCode);
				}
				break;
			case TREASURY_NFTS_BY_TYPE:
				if (associate) {
					curTreasuryNftsByType().associate(keyCode, valueCode);
				} else {
					curTreasuryNftsByType().disassociate(keyCode, valueCode);
				}
				break;
		}
	}

	private FCOneToManyRelation<Integer, Long> curTreasuryNftsByType() {
		Objects.requireNonNull(treasuryNftsByType);
		return treasuryNftsByType.get();
	}

	private void rebuildUsingTreasuryWildcards(
			FCMap<MerkleEntityId, MerkleToken> tokens,
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts
	) {
		final var curTreasuryNftsByType = curTreasuryNftsByType();
		rebuildDelegate(tokens, nfts, (treasuryId, nftId) ->
				curTreasuryNftsByType.associate(nftId.tokenId().identityCode(), nftId.identityCode()));
	}

	private void rebuildUsingExplicitOwners(
			FCMap<MerkleEntityId, MerkleToken> tokens,
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts
	) {
		final var curNftsByOwner = nftsByOwner.get();
		rebuildDelegate(tokens, nfts, (treasuryId, nftId) ->
				curNftsByOwner.associate(treasuryId.identityCode(), nftId.identityCode()));
	}

	private void rebuildDelegate(
			FCMap<MerkleEntityId, MerkleToken> tokens,
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts,
			BiConsumer<EntityId, MerkleUniqueTokenId> treasuryOwnedAction
	) {
		final var curNftsByType = nftsByType.get();
		final var curNftsByOwner = nftsByOwner.get();
		nfts.forEach((nftId, nft) -> {
			final var tokenId = nftId.tokenId();
			if (nft.isTreasuryOwned()) {
				final var token = tokens.get(tokenId.asMerkle());
				if (token == null) {
					return;
				}
				treasuryOwnedAction.accept(token.treasury(), nftId);
			} else {
				curNftsByOwner.associate(nft.getOwner().identityCode(), nftId.identityCode());
			}
			curNftsByType.associate(nftId.tokenId().identityCode(), nftId.identityCode());
		});
	}

	static class PendingChange {
		private final TargetFcotmr targetFcotmr;
		private final int keyCode;
		private final long valueCode;
		private final boolean associate;

		public PendingChange(TargetFcotmr targetFcotmr, int keyCode, long valueCode, boolean associate) {
			this.targetFcotmr = targetFcotmr;
			this.keyCode = keyCode;
			this.valueCode = valueCode;
			this.associate = associate;
		}

		public TargetFcotmr targetFcotmr() {
			return targetFcotmr;
		}

		public int keyCode() {
			return keyCode;
		}

		public long valueCode() {
			return valueCode;
		}

		public boolean isAssociate() {
			return associate;
		}

		@Override
		public String toString() {
			return "PendingChange{" +
					"targetFcotmr=" + targetFcotmr +
					", keyCode=" + keyCode +
					", valueCode=" + valueCode +
					", associate=" + associate +
					'}';
		}
	}

	/* --- Only used by unit tests --- */
	List<PendingChange> getChangesInTxn() {
		return changesInTxn;
	}
}
