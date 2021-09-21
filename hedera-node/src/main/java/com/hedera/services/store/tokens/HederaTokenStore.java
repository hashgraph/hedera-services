package com.hedera.services.store.tokens;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.store.HederaStore;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.views.UniqTokenViewsManager;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.ledger.accounts.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hedera.services.ledger.properties.NftProperty.OWNER;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_FROZEN;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_KYC_GRANTED;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.state.submerkle.EntityId.fromGrpcAccountId;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.EntityNum.fromTokenId;
import static com.hedera.services.utils.MiscUtils.forEach;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static java.util.stream.Collectors.toList;

/**
 * Provides a managing store for arbitrary tokens.
 */
@Singleton
public class HederaTokenStore extends HederaStore implements TokenStore {

	static final TokenID NO_PENDING_ID = TokenID.getDefaultInstance();
	private final UniqTokenViewsManager uniqTokenViewsManager;
	private final GlobalDynamicProperties properties;
	private final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens;
	private final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger;
	private final TransactionalLedger<
			Pair<AccountID, TokenID>,
			TokenRelProperty,
			MerkleTokenRelStatus> tokenRelsLedger;
	Map<AccountID, Set<TokenID>> knownTreasuries = new HashMap<>();

	TokenID pendingId = NO_PENDING_ID;
	MerkleToken pendingCreation;

	@Inject
	public HederaTokenStore(
			final EntityIdSource ids,
			final UniqTokenViewsManager uniqTokenViewsManager,
			final GlobalDynamicProperties properties,
			final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens,
			final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger,
			final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger
	) {
		super(ids);
		this.tokens = tokens;
		this.properties = properties;
		this.nftsLedger = nftsLedger;
		this.tokenRelsLedger = tokenRelsLedger;
		this.uniqTokenViewsManager = uniqTokenViewsManager;
		/* Known-treasuries view is re-built on restart or reconnect */
	}

	@Override
	public void rebuildViews() {
		knownTreasuries.clear();
		rebuildViewOfKnownTreasuries();
	}

	private void rebuildViewOfKnownTreasuries() {
		forEach(tokens.get(), (key, value) -> {
			/* A deleted token's treasury is no longer bound by ACCOUNT_IS_TREASURY restrictions. */
			if (!value.isDeleted()) {
				addKnownTreasury(value.treasury().toGrpcAccountId(), key.toGrpcTokenId());
			}
		});
	}

	@Override
	public List<TokenID> listOfTokensServed(final AccountID treasury) {
		if (!isKnownTreasury(treasury)) {
			return Collections.emptyList();
		} else {
			return knownTreasuries.get(treasury).stream()
					.sorted(HederaLedger.TOKEN_ID_COMPARATOR)
					.collect(toList());
		}
	}

	@Override
	public boolean isCreationPending() {
		return pendingId != NO_PENDING_ID;
	}

	@Override
	public void setHederaLedger(final HederaLedger hederaLedger) {
		hederaLedger.setNftsLedger(nftsLedger);
		hederaLedger.setTokenRelsLedger(tokenRelsLedger);
		super.setHederaLedger(hederaLedger);
	}

	@Override
	public ResponseCodeEnum associate(AccountID aId, List<TokenID> tokens, boolean automaticAssociation) {
		return fullySanityChecked(true, aId, tokens, (account, tokenIds) -> {
			final var accountTokens = hederaLedger.getAssociatedTokens(aId);
			for (var id : tokenIds) {
				if (accountTokens.includes(id)) {
					return TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
				}
			}
			var validity = OK;
			if ((accountTokens.numAssociations() + tokenIds.size()) > properties.maxTokensPerAccount()) {
				validity = TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
			} else {
				var maxAutomaticAssociations = hederaLedger.maxAutomaticAssociations(aId);
				var alreadyUsedAutomaticAssociations = hederaLedger.alreadyUsedAutomaticAssociations(aId);

				if (automaticAssociation && alreadyUsedAutomaticAssociations >= maxAutomaticAssociations) {
					validity = NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
				}

				if (validity == OK) {
					accountTokens.associateAll(new HashSet<>(tokenIds));
					for (var id : tokenIds) {
						final var relationship = asTokenRel(aId, id);
						tokenRelsLedger.create(relationship);
						final var token = get(id);
						tokenRelsLedger.set(
								relationship,
								TokenRelProperty.IS_FROZEN,
								token.hasFreezeKey() && token.accountsAreFrozenByDefault());
						tokenRelsLedger.set(
								relationship,
								TokenRelProperty.IS_KYC_GRANTED,
								!token.hasKycKey());
						tokenRelsLedger.set(
								relationship,
								TokenRelProperty.IS_AUTOMATIC_ASSOCIATION,
								automaticAssociation);

						hederaLedger.addNewAssociationToList(
								new FcTokenAssociation(id.getTokenNum(), aId.getAccountNum()));
						if (automaticAssociation) {
							hederaLedger.setAlreadyUsedAutomaticAssociations(aId, alreadyUsedAutomaticAssociations + 1);
						}
					}
				}
			}
			hederaLedger.setAssociatedTokens(aId, accountTokens);
			return validity;
		});
	}

	@Override
	public boolean exists(final TokenID id) {
		return (isCreationPending() && pendingId.equals(id)) || tokens.get().containsKey(fromTokenId(id));
	}

	@Override
	public MerkleToken get(final TokenID id) {
		throwIfMissing(id);

		return pendingId.equals(id) ? pendingCreation : tokens.get().get(fromTokenId(id));
	}

	@Override
	public void apply(final TokenID id, final Consumer<MerkleToken> change) {
		throwIfMissing(id);

		final var key = fromTokenId(id);
		final var token = tokens.get().getForModify(key);
		try {
			change.accept(token);
		} catch (Exception internal) {
			throw new IllegalArgumentException("Token change failed unexpectedly!", internal);
		}
	}

	@Override
	public ResponseCodeEnum adjustBalance(final AccountID aId, final TokenID tId, final long adjustment) {
		return sanityCheckedFungibleCommon(aId, tId, token -> tryAdjustment(aId, tId, adjustment));
	}

	@Override
	public ResponseCodeEnum changeOwner(final NftId nftId, final AccountID from, final AccountID to) {
		final var tId = nftId.tokenId();
		return sanityChecked(false, from, to, tId, token -> {
			if (!nftsLedger.exists(nftId)) {
				return INVALID_NFT_ID;
			}

			final var fromFreezeAndKycValidity = checkRelFrozenAndKycProps(from, tId);
			if (fromFreezeAndKycValidity != OK) {
				return fromFreezeAndKycValidity;
			}
			final var toFreezeAndKycValidity = checkRelFrozenAndKycProps(to, tId);
			if (toFreezeAndKycValidity != OK) {
				return toFreezeAndKycValidity;
			}

			var owner = (EntityId) nftsLedger.get(nftId, OWNER);
			if (owner.equals(fromGrpcAccountId(AccountID.getDefaultInstance()))) {
				final var tid = nftId.tokenId();
				final var key = EntityNum.fromTokenId(tid);
				owner = this.tokens.get().get(key).treasury();
			}
			if (!owner.matches(from)) {
				return SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
			}

			updateLedgers(nftId, from, to, tId, owner);
			return OK;
		});
	}

	private void updateLedgers(
			final NftId nftId,
			final AccountID from,
			final AccountID to,
			final TokenID tId,
			final EntityId owner
	) {
		final var nftType = nftId.tokenId();
		final var fromRel = asTokenRel(from, nftType);
		final var toRel = asTokenRel(to, nftType);
		final var fromNftsOwned = (long) accountsLedger.get(from, NUM_NFTS_OWNED);
		final var fromThisNftsOwned = (long) tokenRelsLedger.get(fromRel, TOKEN_BALANCE);
		final var toNftsOwned = (long) accountsLedger.get(to, NUM_NFTS_OWNED);
		final var toThisNftsOwned = (long) tokenRelsLedger.get(asTokenRel(to, nftType), TOKEN_BALANCE);
		final var isTreasuryReturn = isTreasuryForToken(to, tId);
		if (isTreasuryReturn) {
			nftsLedger.set(nftId, OWNER, EntityId.MISSING_ENTITY_ID);
		} else {
			nftsLedger.set(nftId, OWNER, EntityId.fromGrpcAccountId(to));
		}

		accountsLedger.set(from, NUM_NFTS_OWNED, fromNftsOwned - 1);
		accountsLedger.set(to, NUM_NFTS_OWNED, toNftsOwned + 1);
		tokenRelsLedger.set(fromRel, TOKEN_BALANCE, fromThisNftsOwned - 1);
		tokenRelsLedger.set(toRel, TOKEN_BALANCE, toThisNftsOwned + 1);

		final var merkleNftId = EntityNumPair.fromLongs(nftId.tokenId().getTokenNum(), nftId.serialNo());
		final var receiver = fromGrpcAccountId(to);
		if (isTreasuryReturn) {
			uniqTokenViewsManager.treasuryReturnNotice(merkleNftId, owner, receiver);
		} else {
			final var isTreasuryExit = isTreasuryForToken(from, tId);
			if (isTreasuryExit) {
				uniqTokenViewsManager.treasuryExitNotice(merkleNftId, owner, receiver);
			} else {
				uniqTokenViewsManager.exchangeNotice(merkleNftId, owner, receiver);
			}
		}
		hederaLedger.updateOwnershipChanges(nftId, from, to);
	}

	@Override
	public void addKnownTreasury(final AccountID aId, final TokenID tId) {
		knownTreasuries.computeIfAbsent(aId, ignore -> new HashSet<>()).add(tId);
	}

	public void removeKnownTreasuryForToken(final AccountID aId, final TokenID tId) {
		throwIfKnownTreasuryIsMissing(aId);
		knownTreasuries.get(aId).remove(tId);
		if (knownTreasuries.get(aId).isEmpty()) {
			knownTreasuries.remove(aId);
		}
	}

	private void throwIfKnownTreasuryIsMissing(final AccountID aId) {
		if (!knownTreasuries.containsKey(aId)) {
			throw new IllegalArgumentException(String.format(
					"Argument 'aId=%s' does not refer to a known treasury!",
					readableId(aId)));
		}
	}

	private ResponseCodeEnum tryAdjustment(final AccountID aId, final TokenID tId, final long adjustment) {
		final var freezeAndKycValidity = checkRelFrozenAndKycProps(aId, tId);
		if (!freezeAndKycValidity.equals(OK)) {
			return freezeAndKycValidity;
		}

		final var relationship = asTokenRel(aId, tId);
		final var balance = (long) tokenRelsLedger.get(relationship, TOKEN_BALANCE);
		final var newBalance = balance + adjustment;
		if (newBalance < 0) {
			return INSUFFICIENT_TOKEN_BALANCE;
		}
		tokenRelsLedger.set(relationship, TOKEN_BALANCE, newBalance);
		hederaLedger.updateTokenXfers(tId, aId, adjustment);
		return OK;
	}

	private ResponseCodeEnum checkRelFrozenAndKycProps(final AccountID aId, final TokenID tId) {
		final var relationship = asTokenRel(aId, tId);
		if ((boolean) tokenRelsLedger.get(relationship, IS_FROZEN)) {
			return ACCOUNT_FROZEN_FOR_TOKEN;
		}
		if (!(boolean) tokenRelsLedger.get(relationship, IS_KYC_GRANTED)) {
			return ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
		}
		return OK;
	}

	@Override
	public void commitCreation() {
		throwIfNoCreationPending();

		tokens.get().put(fromTokenId(pendingId), pendingCreation);
		addKnownTreasury(pendingCreation.treasury().toGrpcAccountId(), pendingId);

		resetPendingCreation();
	}

	@Override
	public void rollbackCreation() {
		throwIfNoCreationPending();

		ids.reclaimLastId();
		resetPendingCreation();
	}
	

	private ResponseCodeEnum fullySanityChecked(
			final boolean strictTokenCheck,
			final AccountID aId,
			final List<TokenID> tokens,
			final BiFunction<AccountID, List<TokenID>, ResponseCodeEnum> action
	) {
		final var validity = checkAccountUsability(aId);
		if (validity != OK) {
			return validity;
		}
		if (strictTokenCheck) {
			for (var tID : tokens) {
				final var id = resolve(tID);
				if (id == MISSING_TOKEN) {
					return INVALID_TOKEN_ID;
				}
				final var token = get(id);
				if (token.isDeleted()) {
					return TOKEN_WAS_DELETED;
				}
			}
		}
		return action.apply(aId, tokens);
	}

	private void resetPendingCreation() {
		pendingId = NO_PENDING_ID;
		pendingCreation = null;
	}

	private void throwIfNoCreationPending() {
		if (pendingId == NO_PENDING_ID) {
			throw new IllegalStateException("No pending token creation!");
		}
	}

	private void throwIfMissing(final TokenID id) {
		if (!exists(id)) {
			throw new IllegalArgumentException(String.format(
					"Argument 'id=%s' does not refer to a known token!",
					readableId(id)));
		}
	}

	public boolean isKnownTreasury(final AccountID aid) {
		return knownTreasuries.containsKey(aid);
	}

	@Override
	public boolean isTreasuryForToken(final AccountID aId, final TokenID tId) {
		if (!knownTreasuries.containsKey(aId)) {
			return false;
		}
		return knownTreasuries.get(aId).contains(tId);
	}

	private ResponseCodeEnum sanityCheckedFungibleCommon(
			final AccountID aId,
			final TokenID tId,
			final Function<MerkleToken, ResponseCodeEnum> action
	) {
		return sanityChecked(true, aId, null, tId, action);
	}

	private ResponseCodeEnum sanityChecked(
			final boolean onlyFungibleCommon,
			final AccountID aId,
			final AccountID aCounterPartyId,
			final TokenID tId,
			final Function<MerkleToken, ResponseCodeEnum> action
	) {
		var validity = checkAccountUsability(aId);
		if (validity != OK) {
			return validity;
		}
		if (aCounterPartyId != null) {
			validity = checkAccountUsability(aCounterPartyId);
			if (validity != OK) {
				return validity;
			}
		}

		validity = checkTokenExistence(tId);
		if (validity != OK) {
			return validity;
		}

		final var token = get(tId);
		if (token.isDeleted()) {
			return TOKEN_WAS_DELETED;
		}
		if (onlyFungibleCommon && token.tokenType() == NON_FUNGIBLE_UNIQUE) {
			return ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
		}

		var key = asTokenRel(aId, tId);
		/*
		 * Instead of returning  TOKEN_NOT_ASSOCIATED_TO_ACCOUNT when a token is not associated,
		 * we check if the account has any maxAutoAssociations set up, if they do check if we reached the limit and
		 * auto associate. If not return EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT
		 */
		if (!tokenRelsLedger.exists(key)) {
			validity = validateAndAutoAssociate(aId, tId);
			if (validity != OK) {
				return validity;
			}
		}
		if (aCounterPartyId != null) {
			key = asTokenRel(aCounterPartyId, tId);
			if (!tokenRelsLedger.exists(key)) {
				validity = validateAndAutoAssociate(aCounterPartyId, tId);
				if (validity != OK) {
					return validity;
				}
			}
		}

		return action.apply(token);
	}

	private ResponseCodeEnum validateAndAutoAssociate(AccountID aId, TokenID tId) {
		if (hederaLedger.maxAutomaticAssociations(aId) > 0) {
			return associate(aId, List.of(tId), true);
		}
		return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
	}

	private ResponseCodeEnum checkTokenExistence(final TokenID tId) {
		return exists(tId) ? OK : INVALID_TOKEN_ID;
	}

	Map<AccountID, Set<TokenID>> getKnownTreasuries() {
		return knownTreasuries;
	}
}
