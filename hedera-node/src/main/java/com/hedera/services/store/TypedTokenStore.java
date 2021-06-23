package com.hedera.services.store;

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

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.accounts.BackingNfts;
import com.hedera.services.ledger.accounts.BackingTokenRels;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

/**
 * Loads and saves token-related entities to and from the Swirlds state, hiding
 * the details of Merkle types from client code by providing an interface in
 * terms of model objects whose methods can perform validated business logic.
 * <p>
 * When loading an token, fails fast by throwing an {@link InvalidTransactionException}
 * if the token is not usable in normal business logic. There are three such
 * cases:
 * <ol>
 * <li>The token is missing.</li>
 * <li>The token is deleted.</li>
 * <li>The token is expired and pending removal.</li>
 * </ol>
 * Note that in the third case, there <i>is</i> one valid use of the token;
 * namely, in an update transaction whose only purpose is to manually renew
 * the expired token. Such update transactions must use a dedicated
 * expiry-extension service, which will be implemented before TokenUpdate.
 * <p>
 * When saving a token or token relationship, invites an injected
 * {@link TransactionRecordService} to inspect the entity for changes that
 * may need to be included in the record of the transaction.
 */
public class TypedTokenStore {
	static final Logger log = LogManager.getLogger(TypedTokenStore.class);

	private final AccountStore accountStore;
	private final TransactionRecordService transactionRecordService;
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;
	private final Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> uniqueTokens;
	private final Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> uniqueOwnership;
	private final Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> uniqueTokenAssociations;
	private final Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenRels;
	/* Only needed for interoperability with legacy HTS during refactor */
	private final BackingNfts backingNfts;
	private final BackingTokenRels backingTokenRels;

	public TypedTokenStore(
			AccountStore accountStore,
			TransactionRecordService transactionRecordService,
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens,
			Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> uniqueTokens,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> uniqueOwnership,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> uniqueTokenAssociations,
			Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenRels,
			BackingTokenRels backingTokenRels,
			BackingNfts backingNfts
	) {
		this.tokens = tokens;
		this.uniqueOwnership = uniqueOwnership;
		this.uniqueTokenAssociations = uniqueTokenAssociations;
		this.tokenRels = tokenRels;
		this.uniqueTokens = uniqueTokens;
		this.accountStore = accountStore;
		this.transactionRecordService = transactionRecordService;

		this.backingNfts = backingNfts;
		this.backingTokenRels = backingTokenRels;
	}

	/**
	 * Returns a model of the requested token relationship, with operations that
	 * can be used to implement business logic in a transaction.
	 * <p>
	 * The arguments <i>should</i> be model objects that were returned by the
	 * {@link TypedTokenStore#loadToken(Id)} and {@link AccountStore#loadAccount(Id)}
	 * methods, respectively, since it will very rarely (or never) be correct
	 * to do business logic on a relationship whose token or account have not
	 * been validated as usable.
	 *
	 * <b>IMPORTANT:</b> Changes to the returned model are not automatically persisted
	 * to state! The altered model must be passed to {@link TypedTokenStore#persistTokenRelationship(TokenRelationship)}
	 * in order for its changes to be applied to the Swirlds state, and included in the
	 * {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} for the active transaction.
	 *
	 * @param token
	 * 		the token in the relationship to load
	 * @param account
	 * 		the account in the relationship to load
	 * @return a usable model of the token-account relationship
	 * @throws InvalidTransactionException
	 * 		if the requested relationship does not exist
	 */
	public TokenRelationship loadTokenRelationship(Token token, Account account) {
		final var tokenId = token.getId();
		final var accountId = account.getId();
		final var key = new MerkleEntityAssociation(
				accountId.getShard(), accountId.getRealm(), accountId.getNum(),
				tokenId.getShard(), tokenId.getRealm(), tokenId.getNum());
		final var merkleTokenRel = tokenRels.get().get(key);

		validateUsable(merkleTokenRel);

		final var tokenRelationship = new TokenRelationship(token, account);
		tokenRelationship.initBalance(merkleTokenRel.getBalance());
		tokenRelationship.setKycGranted(merkleTokenRel.isKycGranted());
		tokenRelationship.setFrozen(merkleTokenRel.isFrozen());

		tokenRelationship.setNotYetPersisted(false);

		return tokenRelationship;
	}

	/**
	 * Persists the given token relationship to the Swirlds state, inviting the injected
	 * {@link TransactionRecordService} to update the {@link com.hedera.services.state.submerkle.ExpirableTxnRecord}
	 * of the active transaction with these changes.
	 *
	 * @param tokenRelationship
	 * 		the token relationship to save
	 */
	public void persistTokenRelationship(TokenRelationship tokenRelationship) {
		final var tokenId = tokenRelationship.getToken().getId();
		final var accountId = tokenRelationship.getAccount().getId();
		final var key = new MerkleEntityAssociation(
				accountId.getShard(), accountId.getRealm(), accountId.getNum(),
				tokenId.getShard(), tokenId.getRealm(), tokenId.getNum());
		final var currentTokenRels = tokenRels.get();

		final var isNewRel = tokenRelationship.isNotYetPersisted();
		final var mutableTokenRel = isNewRel ? new MerkleTokenRelStatus() : currentTokenRels.getForModify(key);
		mutableTokenRel.setBalance(tokenRelationship.getBalance());
		mutableTokenRel.setFrozen(tokenRelationship.isFrozen());
		mutableTokenRel.setKycGranted(tokenRelationship.isKycGranted());

		if (isNewRel) {
			currentTokenRels.put(key, mutableTokenRel);
			/* Only done for interoperability with legacy HTS code during refactor */
			alertTokenBackingStoreOfNew(tokenRelationship);
		}

		transactionRecordService.includeChangesToTokenRel(tokenRelationship);
	}

	/**
	 * Invites the injected {@link TransactionRecordService} to include the changes to the exported transaction record
	 * Currently, the only implemented tracker is the {@link OwnershipTracker} which records the changes to the
	 * ownership
	 * of {@link UniqueToken}
	 *
	 * @param ownershipTracker
	 * 		holds changes to {@link UniqueToken} ownership
	 */
	public void persistTrackers(OwnershipTracker ownershipTracker) {
		transactionRecordService.includeOwnershipChanges(
				ownershipTracker,
				this.uniqueOwnership.get()
		);
	}

	/**
	 * Returns a model of the requested token, with operations that can be used to
	 * implement business logic in a transaction.
	 *
	 * <b>IMPORTANT:</b> Changes to the returned model are not automatically persisted
	 * to state! The altered model must be passed to {@link TypedTokenStore#persistToken(Token)}
	 * in order for its changes to be applied to the Swirlds state, and included in the
	 * {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} for the active transaction.
	 *
	 * @param id
	 * 		the token to load
	 * @return a usable model of the token
	 * @throws InvalidTransactionException
	 * 		if the requested token is missing, deleted, or expired and pending removal
	 */
	public Token loadToken(Id id) {
		final var key = new MerkleEntityId(id.getShard(), id.getRealm(), id.getNum());
		final var merkleToken = tokens.get().get(key);

		validateUsable(merkleToken);

		final var token = new Token(id);
		initModelAccounts(token, merkleToken.treasury(), merkleToken.autoRenewAccount());
		initModelFields(token, merkleToken);

		return token;
	}

	/**
	 * Returns a {@link UniqueToken} model of the requested unique token, with operations that can be used to
	 * implement business logic in a transaction.
	 *
	 * @param tokenId
	 * 		the token class of the unique token
	 * @param serialNumber
	 * 		the serial number to load
	 * @return a usable model of the unique token
	 * @throws InvalidTransactionException
	 * 		if the requested token class is missing, deleted, or expired and pending removal
	 */
	public UniqueToken loadUniqueToken(Id tokenId, long serialNumber) {
		final var tokenKey = new MerkleEntityId(tokenId.getShard(), tokenId.getRealm(), tokenId.getNum());
		final var merkleToken = tokens.get().get(tokenKey);
		validateUsable(merkleToken);

		final var uniqueTokenKey = new MerkleUniqueTokenId(
				new EntityId(tokenId.getShard(), tokenId.getRealm(), tokenId.getNum()), serialNumber);
		final var merkleUniqueToken = uniqueTokens.get().get(uniqueTokenKey);
		validateUsable(merkleUniqueToken);

		final var uniqueToken = new UniqueToken(tokenId, serialNumber);
		initModelFields(uniqueToken, merkleUniqueToken);

		return uniqueToken;
	}

	/**
	 * Persists the given token to the Swirlds state, inviting the injected {@link TransactionRecordService}
	 * to update the {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} of the active transaction
	 * with these changes.
	 *
	 * @param token
	 * 		the token to save
	 */
	public void persistToken(Token token) {
		final var id = token.getId();
		final var key = new MerkleEntityId(id.getShard(), id.getRealm(), id.getNum());
		final var currentTokens = tokens.get();

		final var mutableToken = currentTokens.getForModify(key);
		mapModelChangesToMutable(token, mutableToken);

		if (token.hasMintedUniqueTokens()) {
			final var currentNfts = uniqueTokens.get();
			for (var uniqueToken : token.mintedUniqueTokens()) {
				final var mintKey = new MerkleUniqueTokenId(
						new EntityId(uniqueToken.getTokenId()), uniqueToken.getSerialNumber());
				final var merkleUniqueToken = new MerkleUniqueToken(
						new EntityId(uniqueToken.getOwner()), uniqueToken.getMetadata(), uniqueToken.getCreationTime());
				currentNfts.put(mintKey, merkleUniqueToken);
				backingNfts.addToExistingNfts(mintKey.asNftId());
			}
		}
		if (token.hasBurnedUniqueTokens()) {
			final var currentNfts = uniqueTokens.get();
			for (var uniqueToken : token.burnedUniqueTokens()) {
				final var burnKey = new MerkleUniqueTokenId(
						new EntityId(uniqueToken.getTokenId()), uniqueToken.getSerialNumber());
				currentNfts.remove(burnKey);
				backingNfts.removeFromExistingNfts(burnKey.asNftId());
			}
		}

		transactionRecordService.includeChangesToToken(token);
	}

	private void validateUsable(MerkleTokenRelStatus merkleTokenRelStatus) {
		validateTrue(merkleTokenRelStatus != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
	}

	private void validateUsable(MerkleToken merkleToken) {
		validateTrue(merkleToken != null, INVALID_TOKEN_ID);
		validateFalse(merkleToken.isDeleted(), TOKEN_WAS_DELETED);
	}

	private void validateUsable(MerkleUniqueToken merkleUniqueTokenToken) {
		validateTrue(merkleUniqueTokenToken != null, INVALID_TOKEN_ID);
	}

	private void mapModelChangesToMutable(Token token, MerkleToken mutableToken) {
		final var newAutoRenewAccount = token.getAutoRenewAccount();
		if (newAutoRenewAccount != null) {
			mutableToken.setAutoRenewAccount(new EntityId(newAutoRenewAccount.getId()));
		}
		mutableToken.setTreasury(new EntityId(token.getTreasury().getId()));
		mutableToken.setTotalSupply(token.getTotalSupply());
		mutableToken.setAccountsFrozenByDefault(token.isFrozenByDefault());
		mutableToken.setLastUsedSerialNumber(token.getLastUsedSerialNumber());
	}

	private void initModelAccounts(Token token, EntityId _treasuryId, @Nullable EntityId _autoRenewId) {
		if (_autoRenewId != null) {
			final var autoRenewId = new Id(_autoRenewId.shard(), _autoRenewId.realm(), _autoRenewId.num());
			final var autoRenew = accountStore.loadAccount(autoRenewId);
			token.setAutoRenewAccount(autoRenew);
		}
		final var treasuryId = new Id(_treasuryId.shard(), _treasuryId.realm(), _treasuryId.num());
		final var treasury = accountStore.loadAccount(treasuryId);
		token.setTreasury(treasury);
	}

	private void initModelFields(Token token, MerkleToken immutableToken) {
		token.initTotalSupply(immutableToken.totalSupply());
		token.initSupplyConstraints(immutableToken.supplyType(), immutableToken.maxSupply());
		token.setKycKey(immutableToken.getKycKey());
		token.setFreezeKey(immutableToken.getFreezeKey());
		token.setSupplyKey(immutableToken.getSupplyKey());
		token.setFrozenByDefault(immutableToken.accountsAreFrozenByDefault());
		token.setType(immutableToken.tokenType());
		token.setLastUsedSerialNumber(immutableToken.getLastUsedSerialNumber());
	}

	private void initModelFields(UniqueToken uniqueToken, MerkleUniqueToken immutableUniqueToken) {
		uniqueToken.setCreationTime(immutableUniqueToken.getCreationTime());
		uniqueToken.setMetadata(immutableUniqueToken.getMetadata());
		uniqueToken.setOwner(
				new Id(
						immutableUniqueToken.getOwner().shard(),
						immutableUniqueToken.getOwner().realm(),
						immutableUniqueToken.getOwner().num()
				)
		);

	}

	private void alertTokenBackingStoreOfNew(TokenRelationship newRel) {
		final var tokenId = newRel.getToken().getId();
		final var accountId = newRel.getAccount().getId();
		backingTokenRels.addToExistingRels(Pair.of(
				AccountID.newBuilder()
						.setShardNum(accountId.getShard())
						.setRealmNum(accountId.getRealm())
						.setAccountNum(accountId.getNum())
						.build(),
				TokenID.newBuilder()
						.setShardNum(tokenId.getShard())
						.setRealmNum(tokenId.getRealm())
						.setTokenNum(tokenId.getNum())
						.build()));
	}
}
