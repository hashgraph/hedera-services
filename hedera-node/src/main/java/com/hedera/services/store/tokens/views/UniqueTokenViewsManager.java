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

import com.hedera.services.state.annotations.NftsByOwner;
import com.hedera.services.state.annotations.NftsByType;
import com.hedera.services.state.annotations.TreasuryNftsByType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.tokens.annotations.AreFcotmrQueriesDisabled;
import com.hedera.services.store.tokens.annotations.AreTreasuryWildcardsEnabled;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.hedera.services.store.tokens.views.UniqueTokenViewsManager.TargetFcotmr.NFTS_BY_OWNER;
import static com.hedera.services.store.tokens.views.UniqueTokenViewsManager.TargetFcotmr.TREASURY_NFTS_BY_TYPE;
import static com.hedera.services.utils.EntityNum.fromInt;
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
@Singleton
public class UniqueTokenViewsManager {
	private static final Logger log = LogManager.getLogger(UniqueTokenViewsManager.class);

	private final boolean doNoops;
	private final Supplier<FCOneToManyRelation<EntityNum, Long>> nftsByType;
	private final Supplier<FCOneToManyRelation<EntityNum, Long>> nftsByOwner;
	private final Supplier<FCOneToManyRelation<EntityNum, Long>> treasuryNftsByType;

	enum TargetFcotmr {
		NFTS_BY_TYPE, NFTS_BY_OWNER, TREASURY_NFTS_BY_TYPE
	}

	private boolean inTxn = false;
	private List<PendingChange> changesInTxn = new ArrayList<>();

	public static final UniqueTokenViewsManager NOOP_VIEWS_MANAGER =
			new UniqueTokenViewsManager(null, null, null, true, false);

	@Inject
	public UniqueTokenViewsManager(
			@NftsByType Supplier<FCOneToManyRelation<EntityNum, Long>> nftsByType,
			@NftsByOwner Supplier<FCOneToManyRelation<EntityNum, Long>> nftsByOwner,
			@TreasuryNftsByType Supplier<FCOneToManyRelation<EntityNum, Long>> treasuryNftsByType,
			@AreFcotmrQueriesDisabled boolean doNoops,
			@AreTreasuryWildcardsEnabled boolean useWildcards
	) {
		this.nftsByType = nftsByType;
		this.nftsByOwner = nftsByOwner;
		if (useWildcards) {
			this.treasuryNftsByType = treasuryNftsByType;
		} else {
			this.treasuryNftsByType = null;
		}
		this.doNoops = doNoops;
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
			MerkleMap<EntityNum, MerkleToken> tokens,
			MerkleMap<EntityNumPair, MerkleUniqueToken> nfts
	) {
		if (doNoops) {
			return;
		}

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
	public void mintNotice(EntityNumPair nftId, EntityId treasury) {
		if (doNoops) {
			return;
		}

		final var tokenId = nftId.getHiPhi();
		nftsByType.get().associate(tokenId, nftId.value());
		if (isUsingTreasuryWildcards()) {
			curTreasuryNftsByType().associate(tokenId, nftId.value());
		} else {
			nftsByOwner.get().associate(EntityNum.fromInt(treasury.identityCode()), nftId.value());
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
	public void wipeNotice(EntityNumPair nftId, EntityId fromAccount) {
		if (doNoops) {
			return;
		}

		/* The treasury account cannot be wiped, so both cases are the same */
		nftsByType.get().disassociate(nftId.getHiPhi(), nftId.value());
		nftsByOwner.get().disassociate(fromInt(fromAccount.identityCode()), nftId.value());
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
	public void burnNotice(EntityNumPair nftId, EntityId treasury) {
		if (doNoops) {
			return;
		}

		final var tokenId = nftId.getHiPhi();
		nftsByType.get().disassociate(tokenId, nftId.value());
		if (isUsingTreasuryWildcards()) {
			curTreasuryNftsByType().disassociate(tokenId, nftId.value());
		} else {
			nftsByOwner.get().disassociate(EntityNum.fromInt(treasury.identityCode()), nftId.value());
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
	public void exchangeNotice(EntityNumPair nftId, EntityId prevOwner, EntityId newOwner) {
		if (doNoops) {
			return;
		}

		changeOrStage(NFTS_BY_OWNER, prevOwner.identityCode(), nftId.value(), false);
		changeOrStage(NFTS_BY_OWNER, newOwner.identityCode(), nftId.value(), true);
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
	public void treasuryExitNotice(EntityNumPair nftId, EntityId treasury, EntityId newOwner) {
		if (doNoops) {
			return;
		}

		if (isUsingTreasuryWildcards()) {
			changeOrStage(TREASURY_NFTS_BY_TYPE, nftId.getHiPhi().intValue(), nftId.value(), false);
		} else {
			changeOrStage(NFTS_BY_OWNER, treasury.identityCode(), nftId.value(), false);
		}
		changeOrStage(NFTS_BY_OWNER, newOwner.identityCode(), nftId.value(), true);
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
	public void treasuryReturnNotice(EntityNumPair nftId, EntityId prevOwner, EntityId treasury) {
		if (doNoops) {
			return;
		}

		changeOrStage(NFTS_BY_OWNER, prevOwner.identityCode(), nftId.value(), false);
		if (isUsingTreasuryWildcards()) {
			changeOrStage(TREASURY_NFTS_BY_TYPE, nftId.getHiPhi().intValue(), nftId.value(), true);
		} else {
			changeOrStage(NFTS_BY_OWNER, treasury.identityCode(), nftId.value(), true);
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
		if (doNoops) {
			return false;
		}

		return inTxn;
	}

	public void begin() {
		if (doNoops) {
			return;
		}

		if (inTxn) {
			throw new IllegalStateException("Manager already in transaction");
		}
		inTxn = true;
	}

	public void rollback() {
		if (doNoops) {
			return;
		}

		if (!inTxn) {
			throw new IllegalStateException("Manager not in transaction");
		}
		inTxn = false;
		changesInTxn.clear();
	}

	public void commit() {
		if (doNoops) {
			return;
		}

		if (!inTxn) {
			throw new IllegalStateException("Manager not in transaction");
		}
		if (!changesInTxn.isEmpty()) {
			for (var change : changesInTxn) {
				doChange(change.targetFcotmr(), change.keyCode(), change.valueCode(), change.associate());
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

	void doChange(TargetFcotmr targetFcotmr, int keyCode, long valueCode, boolean associate) {
		switch (targetFcotmr) {
			case NFTS_BY_TYPE:
				if (associate) {
					nftsByType.get().associate(fromInt(keyCode), valueCode);
				} else {
					nftsByType.get().disassociate(fromInt(keyCode), valueCode);
				}
				break;
			case NFTS_BY_OWNER:
				if (associate) {
					nftsByOwner.get().associate(fromInt(keyCode), valueCode);
				} else {
					nftsByOwner.get().disassociate(fromInt(keyCode), valueCode);
				}
				break;
			case TREASURY_NFTS_BY_TYPE:
				if (associate) {
					curTreasuryNftsByType().associate(fromInt(keyCode), valueCode);
				} else {
					curTreasuryNftsByType().disassociate(fromInt(keyCode), valueCode);
				}
				break;
		}
	}

	private FCOneToManyRelation<EntityNum, Long> curTreasuryNftsByType() {
		Objects.requireNonNull(treasuryNftsByType);
		return treasuryNftsByType.get();
	}

	private CompletableFuture<?>[] rebuildFutures(
			MerkleMap<EntityNum, MerkleToken> tokens,
			MerkleMap<EntityNumPair, MerkleUniqueToken> nfts
	) {
		if (isUsingTreasuryWildcards()) {
			return new CompletableFuture<?>[] {
					runAsync(() -> {
						log.info(" - Started rebuilding NFTs by type in {}",
								Thread.currentThread().getName());
						rebuildNftsByType(tokens, nfts);
						log.info(" - Finished rebuilding NFTs by type in {}",
								Thread.currentThread().getName());
					}),
					runAsync(() -> {
						log.info(" - Started rebuilding treasury NFTs by type in {}",
								Thread.currentThread().getName());
						rebuildTreasuryNftsByType(tokens, nfts);
						log.info(" - Finished rebuilding treasury NFTs in {}",
								Thread.currentThread().getName());
					}),
					runAsync(() -> {
						log.info(" - Started rebuilding non-treasury NFTs by owner in {}",
								Thread.currentThread().getName());
						rebuildNonTreasuryNftsByOwner(tokens, nfts);
						log.info(" - Finished rebuilding non-treasury NFTs by owner in {}",
								Thread.currentThread().getName());
					})
			};
		} else {
			return new CompletableFuture<?>[] {
					runAsync(() -> rebuildNftsByType(tokens, nfts)),
					runAsync(() -> rebuildAllNftsByOwner(tokens, nfts))
			};
		}
	}

	private void rebuildTreasuryNftsByType(
			MerkleMap<EntityNum, MerkleToken> tokens,
			MerkleMap<EntityNumPair, MerkleUniqueToken> nfts
	) {
		final var curTreasuryNftsByType = curTreasuryNftsByType();
		forEach(nfts, (nftId, nft) -> {
			if (nft.isTreasuryOwned()) {
				final var tokenId = nftId.getHiPhi();
				if (!tokens.containsKey(tokenId)) {
					return;
				}
				curTreasuryNftsByType.associate(tokenId, nftId.value());
			}
		});
	}

	private void rebuildNonTreasuryNftsByOwner(
			MerkleMap<EntityNum, MerkleToken> tokens,
			MerkleMap<EntityNumPair, MerkleUniqueToken> nfts
	) {
		final var curNftsByOwner = nftsByOwner.get();
		forEach(nfts, (nftId, nft) -> {
			if (!tokens.containsKey(nftId.getHiPhi())) {
				return;
			}
			if (!nft.isTreasuryOwned()) {
				curNftsByOwner.associate(fromInt(nft.getOwner().identityCode()), nftId.value());
			}
		});
	}

	private void rebuildAllNftsByOwner(
			MerkleMap<EntityNum, MerkleToken> tokens,
			MerkleMap<EntityNumPair, MerkleUniqueToken> nfts
	) {
		final var curNftsByOwner = nftsByOwner.get();
		forEach(nfts, (nftId, nft) -> {
			final var tokenId = nftId.getHiPhi();
			if (!tokens.containsKey(tokenId)) {
				return;
			}
			if (nft.isTreasuryOwned()) {
				final var token = tokens.get(tokenId);
				curNftsByOwner.associate(fromInt(token.treasury().identityCode()), nftId.value());
			} else {
				curNftsByOwner.associate(fromInt(nft.getOwner().identityCode()), nftId.value());
			}
		});
	}

	private void rebuildNftsByType(
			MerkleMap<EntityNum, MerkleToken> tokens,
			MerkleMap<EntityNumPair, MerkleUniqueToken> nfts
	) {
		final var curNftsByType = nftsByType.get();
		forEach(nfts, (nftId, nft) -> {
			final var tokenId = nftId.getHiPhi();
			if (!tokens.containsKey(tokenId)) {
				return;
			}
			curNftsByType.associate(tokenId, nftId.value());
		});
	}

	/* --- Only used by unit tests --- */
	List<PendingChange> getChangesInTxn() {
		return changesInTxn;
	}
}
