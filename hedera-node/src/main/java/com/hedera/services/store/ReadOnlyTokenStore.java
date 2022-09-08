/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.store;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.HashMap;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Loads token-related entities from the Swirlds state, hiding the details of Merkle types from
 * client code by providing an interface in terms of model objects whose methods can perform
 * validated business logic. It does not permit mutations of Merkle types.
 *
 * <p>When loading an token, fails fast by throwing an {@link InvalidTransactionException} if the
 * token is not usable in normal business logic. There are three such cases:
 *
 * <ol>
 *   <li>The token is missing.
 *   <li>The token is deleted.
 *   <li>The token is expired and pending removal.
 * </ol>
 *
 * Note that in the third case, there <i>is</i> one valid use of the token; namely, in an update
 * transaction whose only purpose is to manually renew the expired token. Such update transactions
 * must use a dedicated expiry-extension service, which will be implemented before TokenUpdate.
 *
 * <p>
 */
public class ReadOnlyTokenStore {
    protected final AccountStore accountStore;
    protected final BackingStore<TokenID, MerkleToken> tokens;
    protected final BackingStore<NftId, UniqueTokenAdapter> uniqueTokens;
    protected final BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> tokenRels;

    public ReadOnlyTokenStore(
            final AccountStore accountStore,
            final BackingStore<TokenID, MerkleToken> tokens,
            final BackingStore<NftId, UniqueTokenAdapter> uniqueTokens,
            final BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> tokenRels) {
        this.tokens = tokens;
        this.tokenRels = tokenRels;
        this.uniqueTokens = uniqueTokens;
        this.accountStore = accountStore;
    }

    /**
     * Returns the number of NFTs currently in the ledger state. (That is, the number of entries in
     * the {@code uniqueTokens} map---NFTs are not marked deleted but actually removed from state
     * when they are burned.)
     *
     * @return the current number of NFTs in the system
     */
    public long currentMintedNfts() {
        return uniqueTokens.size();
    }

    /**
     * Returns a model of the requested token relationship, with operations that can be used to
     * implement business logic in a transaction.
     *
     * <p>The arguments <i>should</i> be model objects that were returned by the {@link
     * TypedTokenStore#loadToken(Id)} and {@link AccountStore#loadAccount(Id)} methods,
     * respectively, since it will very rarely (or never) be correct to do business logic on a
     * relationship whose token or account have not been validated as usable.
     *
     * <p><b>IMPORTANT:</b> Changes to the returned model are not automatically persisted to state!
     * The altered model must be passed to {@link TypedTokenStore#commitTokenRelationships(List)} in
     * order for its changes to be applied to the Swirlds state, and included in the {@link
     * com.hedera.services.state.submerkle.ExpirableTxnRecord} for the active transaction.
     *
     * @param token the token in the relationship to load
     * @param account the account in the relationship to load
     * @return a usable model of the token-account relationship
     * @throws InvalidTransactionException if the requested relationship does not exist
     */
    public TokenRelationship loadTokenRelationship(Token token, Account account) {
        final var merkleTokenRel = getMerkleTokenRelationship(token, account);

        validateUsable(merkleTokenRel);

        return buildTokenRelationship(token, account, merkleTokenRel);
    }

    /**
     * Returns a model of the requested token relationship, with operations that can be used to
     * implement business logic in a transaction. If the requested token relationship does not
     * exist, returns null.
     *
     * <p>The arguments <i>should</i> be model objects that were returned by the {@link
     * TypedTokenStore#loadToken(Id)} and {@link AccountStore#loadAccount(Id)} methods,
     * respectively, since it will very rarely (or never) be correct to do business logic on a
     * relationship whose token or account have not been validated as usable.
     *
     * <p><b>IMPORTANT:</b> Changes to the returned model are not automatically persisted to state!
     * The altered model must be passed to {@link TypedTokenStore#commitTokenRelationships(List)} in
     * order for its changes to be applied to the Swirlds state, and included in the {@link
     * com.hedera.services.state.submerkle.ExpirableTxnRecord} for the active transaction.
     *
     * @param token the token in the relationship to load
     * @param account the account in the relationship to load
     * @return a usable model of the token-account relationship or null if the requested
     *     relationship doesnt exist
     */
    public TokenRelationship loadPossiblyMissingTokenRelationship(Token token, Account account) {
        final var merkleTokenRel = getMerkleTokenRelationship(token, account);

        if (merkleTokenRel == null) {
            return null;
        } else {
            return buildTokenRelationship(token, account, merkleTokenRel);
        }
    }

    public boolean hasAssociation(Token token, Account account) {
        return tokenRels.contains(
                Pair.of(account.getId().asGrpcAccount(), token.getId().asGrpcToken()));
    }

    public MerkleTokenRelStatus getMerkleTokenRelationship(Token token, Account account) {
        return tokenRels.getImmutableRef(
                Pair.of(account.getId().asGrpcAccount(), token.getId().asGrpcToken()));
    }

    /**
     * Returns a model of the requested token, with operations that can be used to implement
     * business logic in a transaction.
     *
     * <p><b>IMPORTANT:</b> Changes to the returned model are not automatically persisted to state!
     * The altered model must be passed to {@link TypedTokenStore#commitToken(Token)} in order for
     * its changes to be applied to the Swirlds state, and included in the {@link
     * com.hedera.services.state.submerkle.ExpirableTxnRecord} for the active transaction.
     *
     * @param id the token to load
     * @return a usable model of the token
     * @throws InvalidTransactionException if the requested token is missing, deleted, or expired
     *     and pending removal
     */
    public Token loadToken(Id id) {
        final var merkleToken = tokens.getImmutableRef(id.asGrpcToken());

        validateUsable(merkleToken);

        final var token = new Token(id);
        initModelAccounts(token, merkleToken.treasury(), merkleToken.autoRenewAccount());
        initModelFields(token, merkleToken);

        return token;
    }

    /**
     * This is only to be used when pausing/unpausing token as this method ignores the pause status
     * of the token.
     *
     * @param id the token to load
     * @return a usable model of the token which is possibly paused
     */
    public Token loadPossiblyPausedToken(Id id) {
        final var merkleToken = tokens.getImmutableRef(id.asGrpcToken());

        validateTrue(merkleToken != null, INVALID_TOKEN_ID);
        validateFalse(merkleToken.isDeleted(), TOKEN_WAS_DELETED);

        final var token = new Token(id);
        initModelAccounts(token, merkleToken.treasury(), merkleToken.autoRenewAccount());
        initModelFields(token, merkleToken);

        return token;
    }

    /**
     * Returns a {@link UniqueToken} model of the requested unique token, with operations that can
     * be used to implement business logic in a transaction.
     *
     * @param token the token model, on which to load the of the unique token
     * @param serialNumbers the serial numbers to load
     * @throws InvalidTransactionException if the requested token class is missing, deleted, or
     *     expired and pending removal
     */
    public void loadUniqueTokens(Token token, List<Long> serialNumbers) {
        final var loadedUniqueTokens = new HashMap<Long, UniqueToken>();
        for (long serialNumber : serialNumbers) {
            final var uniqueToken = loadUniqueToken(token.getId(), serialNumber);
            loadedUniqueTokens.put(serialNumber, uniqueToken);
        }
        token.setLoadedUniqueTokens(loadedUniqueTokens);
    }

    /**
     * Returns a {@link UniqueToken} model of the requested unique token, with operations that can
     * be used to implement business logic in a transaction.
     *
     * @param tokenId TokenId of the NFT
     * @param serialNum Serial number of the NFT
     * @return The {@link UniqueToken} model of the requested unique token
     */
    public UniqueToken loadUniqueToken(Id tokenId, Long serialNum) {
        final var nftId = NftId.withDefaultShardRealm(tokenId.num(), serialNum);
        final var merkleUniqueToken = uniqueTokens.getImmutableRef(nftId);
        validateUsable(merkleUniqueToken);
        final var uniqueToken = new UniqueToken(tokenId, serialNum);
        initModelFields(uniqueToken, merkleUniqueToken);
        return uniqueToken;
    }

    /**
     * Use carefully. Returns a model of the requested token which may be marked as deleted or even
     * marked as auto-removed if the Merkle state has no token with that id.
     *
     * @param id the token to load
     * @return a usable model of the token
     */
    public Token loadPossiblyDeletedOrAutoRemovedToken(Id id) {
        final var merkleToken = tokens.getImmutableRef(id.asGrpcToken());

        final var token = new Token(id);
        if (merkleToken != null) {
            validateFalse(merkleToken.isPaused(), TOKEN_IS_PAUSED);
            initModelAccounts(token, merkleToken.treasury(), merkleToken.autoRenewAccount());
            initModelFields(token, merkleToken);
        } else {
            token.markAutoRemoved();
        }

        return token;
    }

    /**
     * Loads a token from the swirlds state. Throws the given response code upon unusable token.
     *
     * @param id - id of the token to be loaded
     * @param code - the {@link ResponseCodeEnum} code to fail with if the token is not present or
     *     is erroneous
     * @return - the loaded token
     */
    public Token loadTokenOrFailWith(Id id, ResponseCodeEnum code) {
        final var merkleToken = tokens.getImmutableRef(id.asGrpcToken());

        validateUsable(merkleToken, code);

        final var token = new Token(id);
        initModelAccounts(token, merkleToken.treasury(), merkleToken.autoRenewAccount());
        initModelFields(token, merkleToken);
        return token;
    }

    private void validateUsable(MerkleTokenRelStatus merkleTokenRelStatus) {
        validateTrue(merkleTokenRelStatus != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
    }

    private void validateUsable(MerkleToken merkleToken) {
        validateTrue(merkleToken != null, INVALID_TOKEN_ID);
        validateFalse(merkleToken.isDeleted(), TOKEN_WAS_DELETED);
        validateFalse(merkleToken.isPaused(), TOKEN_IS_PAUSED);
    }

    private void validateUsable(MerkleToken merkleToken, ResponseCodeEnum code) {
        validateTrue(merkleToken != null, code);
        validateFalse(merkleToken.isDeleted(), code);
        validateFalse(merkleToken.isPaused(), code);
    }

    private void validateUsable(UniqueTokenAdapter uniqueTokenAdapter) {
        validateTrue(uniqueTokenAdapter != null, INVALID_NFT_ID);
    }

    private void initModelAccounts(
            Token token, EntityId treasuryId, @Nullable EntityId autoRenewId) {
        if (autoRenewId != null) {
            final var autoRenewIdentifier =
                    new Id(autoRenewId.shard(), autoRenewId.realm(), autoRenewId.num());
            final var autoRenew = accountStore.loadAccount(autoRenewIdentifier);
            token.setAutoRenewAccount(autoRenew);
        }
        final var treasuryIdentifier =
                new Id(treasuryId.shard(), treasuryId.realm(), treasuryId.num());
        final var treasury = accountStore.loadAccount(treasuryIdentifier);
        token.setTreasury(treasury);
    }

    private void initModelFields(Token token, MerkleToken immutableToken) {
        token.initTotalSupply(immutableToken.totalSupply());
        token.initSupplyConstraints(immutableToken.supplyType(), immutableToken.maxSupply());
        token.setKycKey(immutableToken.getKycKey());
        token.setFreezeKey(immutableToken.getFreezeKey());
        token.setSupplyKey(immutableToken.getSupplyKey());
        token.setWipeKey(immutableToken.getWipeKey());
        token.setFrozenByDefault(immutableToken.accountsAreFrozenByDefault());
        token.setAdminKey(immutableToken.getAdminKey());
        token.setFeeScheduleKey(immutableToken.getFeeScheduleKey());
        token.setPauseKey(immutableToken.getPauseKey());
        token.setType(immutableToken.tokenType());
        token.setLastUsedSerialNumber(immutableToken.getLastUsedSerialNumber());
        token.setIsDeleted(immutableToken.isDeleted());
        token.setPaused(immutableToken.isPaused());
        token.setExpiry(immutableToken.expiry());
        token.setMemo(immutableToken.memo());
        token.setAutoRenewPeriod(immutableToken.autoRenewPeriod());
    }

    private void initModelFields(UniqueToken uniqueToken, UniqueTokenAdapter immutableUniqueToken) {
        if (immutableUniqueToken.isVirtual()) {
            uniqueToken.setCreationTime(immutableUniqueToken.uniqueTokenValue().getCreationTime());
            uniqueToken.setMetadata(immutableUniqueToken.uniqueTokenValue().getMetadata());
            uniqueToken.setOwner(immutableUniqueToken.uniqueTokenValue().getOwner().asId());
            uniqueToken.setSpender(immutableUniqueToken.uniqueTokenValue().getSpender().asId());
        } else {
            uniqueToken.setCreationTime(immutableUniqueToken.merkleUniqueToken().getCreationTime());
            uniqueToken.setMetadata(immutableUniqueToken.merkleUniqueToken().getMetadata());
            uniqueToken.setOwner(immutableUniqueToken.merkleUniqueToken().getOwner().asId());
            uniqueToken.setSpender(immutableUniqueToken.merkleUniqueToken().getSpender().asId());
        }
    }

    private TokenRelationship buildTokenRelationship(
            final Token token, final Account account, final MerkleTokenRelStatus merkleTokenRel) {
        final var tokenRelationship = new TokenRelationship(token, account);
        tokenRelationship.initBalance(merkleTokenRel.getBalance());
        tokenRelationship.setKycGranted(merkleTokenRel.isKycGranted());
        tokenRelationship.setFrozen(merkleTokenRel.isFrozen());
        tokenRelationship.setAutomaticAssociation(merkleTokenRel.isAutomaticAssociation());
        tokenRelationship.markAsPersisted();

        return tokenRelationship;
    }
}
