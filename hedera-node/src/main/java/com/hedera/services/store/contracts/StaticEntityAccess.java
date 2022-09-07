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
package com.hedera.services.store.contracts;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.state.merkle.internals.BitPackUtils.codeFromNum;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hedera.services.utils.EntityNumPair.fromAccountTokenRel;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.store.models.NftId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

public class StaticEntityAccess implements EntityAccess {
    private final StateView view;
    private final ContractAliases aliases;
    private final OptionValidator validator;
    private final MerkleMap<EntityNum, MerkleToken> tokens;
    private final MerkleMap<EntityNum, MerkleAccount> accounts;
    private final MerkleMap<EntityNumPair, MerkleUniqueToken> nfts;
    private final MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations;
    private final VirtualMap<ContractKey, IterableContractValue> storage;
    private final VirtualMap<VirtualBlobKey, VirtualBlobValue> bytecode;

    public StaticEntityAccess(
            final StateView view, final ContractAliases aliases, final OptionValidator validator) {
        this.view = view;
        this.aliases = aliases;
        this.validator = validator;
        this.bytecode = view.storage();
        this.storage = view.contractStorage();
        this.accounts = view.accounts();
        this.tokens = view.tokens();
        this.nfts = view.uniqueTokens();
        this.tokenAssociations = view.tokenAssociations();
    }

    @Override
    public void startAccess() {
        // No-op
    }

    @Override
    public String currentManagedChangeSet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void customize(final AccountID id, final HederaAccountCustomizer customizer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getBalance(AccountID id) {
        return accounts.get(fromAccountId(id)).getBalance();
    }

    @Override
    public ByteString alias(final AccountID id) {
        return accounts.get(fromAccountId(id)).getAlias();
    }

    @Override
    public WorldLedgers worldLedgers() {
        return WorldLedgers.staticLedgersWith(aliases, this);
    }

    @Override
    public boolean isUsable(AccountID id) {
        final var account = accounts.get(fromAccountId(id));
        if (account == null || account.isDeleted()) {
            return false;
        }
        return validator.expiryStatusGiven(
                        account.getBalance(), account.getExpiry(), account.isSmartContract())
                == OK;
    }

    @Override
    public boolean isExtant(AccountID id) {
        return accounts.get(fromAccountId(id)) != null;
    }

    @Override
    public boolean isTokenAccount(Address address) {
        return view.tokenExists(EntityIdUtils.tokenIdFromEvmAddress(address));
    }

    @Override
    public void putStorage(AccountID id, UInt256 key, UInt256 value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UInt256 getStorage(AccountID id, UInt256 key) {
        final var contractKey = new ContractKey(id.getAccountNum(), key.toArray());
        IterableContractValue value = storage.get(contractKey);
        return value == null ? UInt256.ZERO : UInt256.fromBytes(Bytes32.wrap(value.getValue()));
    }

    @Override
    public void flushStorage(
            TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void storeCode(AccountID id, Bytes code) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bytes fetchCodeIfPresent(final AccountID id) {
        return explicitCodeFetch(bytecode, id);
    }

    @Nullable
    static Bytes explicitCodeFetch(
            final VirtualMap<VirtualBlobKey, VirtualBlobValue> bytecode, final AccountID id) {
        return explicitCodeFetch(bytecode, id.getAccountNum());
    }

    @Nullable
    public static Bytes explicitCodeFetch(
            final VirtualMap<VirtualBlobKey, VirtualBlobValue> bytecode, final long contractNum) {
        final var key =
                new VirtualBlobKey(VirtualBlobKey.Type.CONTRACT_BYTECODE, codeFromNum(contractNum));
        final var value = bytecode.get(key);
        return (value != null) ? Bytes.of(value.getData()) : null;
    }

    @Override
    public void recordNewKvUsageTo(
            TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
        throw new UnsupportedOperationException();
    }

    // We don't put these methods on the EntityAccess interface, because they would never be used
    // when processing
    // a non-static EVM call; then the WorldLedgers should get all such information from its ledgers

    public boolean defaultFreezeStatus(final TokenID tokenId) {
        final var token = lookupToken(tokenId);
        return token.accountsAreFrozenByDefault();
    }

    public boolean defaultKycStatus(final TokenID tokenID) {
        final var token = lookupToken(tokenID);
        return token.accountsKycGrantedByDefault();
    }

    /**
     * Returns the name of the given token.
     *
     * @param tokenId the token of interest
     * @return the token's name
     */
    public String nameOf(final TokenID tokenId) {
        final var token = lookupToken(tokenId);
        return token.name();
    }

    /**
     * Returns the symbol of the given token.
     *
     * @param tokenId the token of interest
     * @return the token's symbol
     */
    public String symbolOf(final TokenID tokenId) {
        final var token = lookupToken(tokenId);
        return token.symbol();
    }

    /**
     * Returns the supply of the given token.
     *
     * @param tokenId the token of interest
     * @return the token's supply
     */
    public long supplyOf(final TokenID tokenId) {
        final var token = lookupToken(tokenId);
        return token.totalSupply();
    }

    /**
     * Returns the decimals of the given token.
     *
     * @param tokenId the token of interest
     * @return the token's decimals
     */
    public int decimalsOf(final TokenID tokenId) {
        final var token = lookupToken(tokenId);
        return token.decimals();
    }

    /**
     * Returns the type of the given token.
     *
     * @param tokenId the token of interest
     * @return the token's type
     */
    public TokenType typeOf(final TokenID tokenId) {
        final var token = lookupToken(tokenId);
        return token.tokenType();
    }

    /**
     * Returns the balance of the given account for the given token.
     *
     * @param accountId the account of interest
     * @param tokenId the token of interest
     * @return the token's supply
     */
    public long balanceOf(final AccountID accountId, final TokenID tokenId) {
        lookupToken(tokenId);
        final var accountNum = EntityNum.fromAccountId(accountId);
        validateTrue(accounts.containsKey(accountNum), INVALID_ACCOUNT_ID);
        final var balanceKey = fromAccountTokenRel(accountId, tokenId);
        final var relStatus = tokenAssociations.get(balanceKey);
        return (relStatus != null) ? relStatus.getBalance() : 0;
    }

    /**
     * Returns the frozen status of the given token for the given account.
     *
     * @param accountId the account of interest
     * @param tokenId the token of interest
     * @return the token's freeze status
     */
    public boolean isFrozen(final AccountID accountId, final TokenID tokenId) {
        lookupToken(tokenId);
        final var accountNum = EntityNum.fromAccountId(accountId);
        validateTrue(accounts.containsKey(accountNum), INVALID_ACCOUNT_ID);
        final var isFrozenKey = fromAccountTokenRel(accountId, tokenId);
        final var relStatus = tokenAssociations.get(isFrozenKey);
        return relStatus != null && relStatus.isFrozen();
    }

    /**
     * Returns the allowance of the given spender for the given owner for the given token.
     *
     * @param ownerId the owner account
     * @param spenderId the spender account
     * @param tokenId the token of interest
     * @return the token's supply
     */
    public long allowanceOf(
            final AccountID ownerId, final AccountID spenderId, final TokenID tokenId) {
        final var ownerNum = EntityNum.fromAccountId(ownerId);
        final var owner = accounts.get(ownerNum);
        validateTrueOrRevert(owner != null, INVALID_ALLOWANCE_OWNER_ID);
        final var allowances = owner.getFungibleTokenAllowances();
        final var fcTokenAllowanceId = FcTokenAllowanceId.from(tokenId, spenderId);
        return allowances.getOrDefault(fcTokenAllowanceId, 0L);
    }

    /**
     * Returns the mirror EVM address of the approved spender for the given NFT.
     *
     * @param nftId the NFT of interest
     * @return the token's supply
     */
    public Address approvedSpenderOf(final NftId nftId) {
        final var nft = nfts.get(EntityNumPair.fromNftId(nftId));
        validateTrueOrRevert(nft != null, INVALID_TOKEN_NFT_SERIAL_NUMBER);
        return nft.getSpender().toEvmAddress();
    }

    /**
     * Indicates if the operator is approved-for-all by the given owner for the given (non-fungible)
     * token type.
     *
     * @param ownerId the owner account
     * @param operatorId the putative operator account
     * @param tokenId the token of interest
     * @return the token's supply
     */
    public boolean isOperator(
            final AccountID ownerId, final AccountID operatorId, final TokenID tokenId) {
        final var owner = accounts.get(EntityNum.fromAccountId(ownerId));
        if (owner == null) {
            return false;
        }
        final var operatorNum = EntityNum.fromAccountId(operatorId);
        final var operator = accounts.get(operatorNum);
        if (operator == null) {
            return false;
        }
        final var approvedForAll = owner.getApproveForAllNfts();
        return approvedForAll.contains(
                new FcTokenAllowanceId(EntityNum.fromTokenId(tokenId), operatorNum));
    }

    /**
     * Returns the mirror EVM address of the owner of the given NFT.
     *
     * @param nftId the NFT of interest
     * @return the owner address
     */
    public Address ownerOf(final NftId nftId) {
        return nftPropertyOf(
                nftId,
                nft -> {
                    var owner = nft.getOwner();
                    if (MISSING_ENTITY_ID.equals(owner)) {
                        final var token = tokens.get(nft.getKey().getHiOrderAsNum());
                        validateTrue(token != null, INVALID_TOKEN_ID);
                        owner = token.treasury();
                    }
                    return EntityIdUtils.asTypedEvmAddress(owner);
                });
    }

    /**
     * Returns the metadata of a given NFT as a {@code String}.
     *
     * @param nftId the NFT of interest
     * @return the metadata
     */
    public String metadataOf(final NftId nftId) {
        return nftPropertyOf(nftId, nft -> new String(nft.getMetadata()));
    }

    public boolean isKyc(final AccountID accountId, final TokenID tokenId) {
        lookupToken(tokenId);
        final var accountNum = EntityNum.fromAccountId(accountId);
        validateTrue(accounts.containsKey(accountNum), INVALID_ACCOUNT_ID);
        final var isKycKey = fromAccountTokenRel(accountId, tokenId);
        final var relStatus = tokenAssociations.get(isKycKey);
        return relStatus != null && relStatus.isKycGranted();
    }

    private <T> T nftPropertyOf(final NftId nftId, final Function<MerkleUniqueToken, T> getter) {
        final var key = EntityNumPair.fromNftId(nftId);
        var nft = nfts.get(key);
        validateTrue(nft != null, INVALID_TOKEN_NFT_SERIAL_NUMBER);
        return getter.apply(nft);
    }

    private MerkleToken lookupToken(final TokenID tokenId) {
        final var token = tokens.get(EntityNum.fromTokenId(tokenId));
        validateTrue(token != null, INVALID_TOKEN_ID);
        return token;
    }
}
