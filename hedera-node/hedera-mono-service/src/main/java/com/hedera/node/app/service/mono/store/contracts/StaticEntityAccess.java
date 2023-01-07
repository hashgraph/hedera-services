/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts;

import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.node.app.service.mono.state.merkle.internals.BitPackUtils.codeFromNum;
import static com.hedera.node.app.service.mono.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.numFromEvmAddress;
import static com.hedera.node.app.service.mono.utils.EntityNum.fromEvmAddress;
import static com.hedera.node.app.service.mono.utils.EntityNumPair.fromAccountTokenRel;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

public class StaticEntityAccess implements EntityAccess {
    private final StateView view;
    private final ContractAliases aliases;
    private final OptionValidator validator;
    private final MerkleMap<EntityNum, MerkleToken> tokens;
    private final AccountStorageAdapter accounts;
    private final UniqueTokenMapAdapter nfts;
    private final TokenRelStorageAdapter tokenAssociations;
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
    public long getBalance(final Address address) {
        return accounts.get(fromEvmAddress(address)).getBalance();
    }

    @Override
    public ByteString alias(final Address address) {
        return accounts.get(fromEvmAddress(address)).getAlias();
    }

    @Override
    public WorldLedgers worldLedgers() {
        return WorldLedgers.staticLedgersWith(aliases, this);
    }

    @Override
    public boolean isUsable(final Address address) {
        final var account = accounts.get(fromEvmAddress(address));
        if (account == null || account.isDeleted()) {
            return false;
        }
        return validator.expiryStatusGiven(
                        account.getBalance(),
                        account.isExpiredAndPendingRemoval(),
                        account.isSmartContract())
                == OK;
    }

    @Override
    public boolean isExtant(final Address address) {
        return accounts.get(fromEvmAddress(address)) != null;
    }

    @Override
    public boolean isTokenAccount(final Address address) {
        return view.tokenExists(EntityIdUtils.tokenIdFromEvmAddress(address));
    }

    @Override
    public void putStorage(final AccountID id, final Bytes key, final Bytes value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UInt256 getStorage(final Address address, final Bytes key) {
        final var num = numFromEvmAddress(address.toArrayUnsafe());
        final var contractKey = new ContractKey(num, key.toArray());
        final IterableContractValue value = storage.get(contractKey);
        return value == null ? UInt256.ZERO : UInt256.fromBytes(Bytes32.wrap(value.getValue()));
    }

    @Override
    public void flushStorage(
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void storeCode(final AccountID id, final Bytes code) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bytes fetchCodeIfPresent(final Address address) {
        return explicitCodeFetch(bytecode, accountIdFromEvmAddress(address));
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
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger) {
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

    public JKey keyOf(final TokenID tokenId, TokenProperty keyType) {
        final var token = lookupToken(tokenId);
        return switch (keyType) {
            case ADMIN_KEY -> token.getAdminKey();
            case KYC_KEY -> token.getKycKey();
            case FREEZE_KEY -> token.getFreezeKey();
            case WIPE_KEY -> token.getWipeKey();
            case SUPPLY_KEY -> token.getSupplyKey();
            case FEE_SCHEDULE_KEY -> token.getFeeScheduleKey();
            case PAUSE_KEY -> token.getPauseKey();
            default -> throw new InvalidTransactionException(ResponseCodeEnum.INVALID_KEY_ENCODING);
        };
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

    public Optional<EvmTokenInfo> evmInfoForToken(final TokenID tokenId) {
        return view.evmInfoForToken(tokenId);
    }

    public Optional<TokenInfo> infoForToken(final TokenID tokenId) {
        return view.infoForToken(tokenId);
    }

    public Optional<TokenNftInfo> infoForNft(final NftID target) {
        final var nft =
                nfts.get(
                        NftId.withDefaultShardRealm(
                                EntityNum.fromTokenId(target.getTokenID()).longValue(),
                                target.getSerialNumber()));
        validateTrueOrRevert(nft != null, INVALID_TOKEN_NFT_SERIAL_NUMBER);
        return view.infoForNft(target);
    }

    public List<CustomFee> infoForTokenCustomFees(final TokenID tokenId) {
        return view.infoForTokenCustomFees(tokenId);
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
        final var nft = nfts.get(nftId);
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
                        final var token = tokens.get(nftId.asEntityNumPair().getHiOrderAsNum());
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

    private <T> T nftPropertyOf(final NftId nftId, final Function<UniqueTokenAdapter, T> getter) {
        final var nft = nfts.get(nftId);
        validateTrue(nft != null, INVALID_TOKEN_NFT_SERIAL_NUMBER);
        return getter.apply(nft);
    }

    private MerkleToken lookupToken(final TokenID tokenId) {
        final var token = tokens.get(EntityNum.fromTokenId(tokenId));
        validateTrue(token != null, INVALID_TOKEN_ID);
        return token;
    }
}
