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
package com.hedera.node.app.service.mono.store.contracts;

import static com.hedera.node.app.service.evm.store.models.HederaEvmAccount.ECDSA_KEY_ALIAS_PREFIX;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.node.app.service.mono.context.primitives.StateView.WILDCARD_OWNER;
import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.ledger.TransactionalLedger.activeLedgerWrapping;
import static com.hedera.node.app.service.mono.ledger.interceptors.AutoAssocTokenRelsCommitInterceptor.forKnownAutoAssociatingOp;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.ALIAS;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.APPROVE_FOR_ALL_NFTS_ALLOWANCES;
import static com.hedera.node.app.service.mono.ledger.properties.NftProperty.METADATA;
import static com.hedera.node.app.service.mono.ledger.properties.NftProperty.OWNER;
import static com.hedera.node.app.service.mono.ledger.properties.TokenProperty.ACC_FROZEN_BY_DEFAULT;
import static com.hedera.node.app.service.mono.ledger.properties.TokenProperty.ACC_KYC_GRANTED_BY_DEFAULT;
import static com.hedera.node.app.service.mono.ledger.properties.TokenProperty.DECIMALS;
import static com.hedera.node.app.service.mono.ledger.properties.TokenProperty.NAME;
import static com.hedera.node.app.service.mono.ledger.properties.TokenProperty.SYMBOL;
import static com.hedera.node.app.service.mono.ledger.properties.TokenProperty.TOKEN_TYPE;
import static com.hedera.node.app.service.mono.ledger.properties.TokenProperty.TOTAL_SUPPLY;
import static com.hedera.node.app.service.mono.ledger.properties.TokenProperty.TREASURY;
import static com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty.IS_FROZEN;
import static com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty.IS_KYC_GRANTED;
import static com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.node.app.service.mono.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.ECDSA_SECP256K1_ALIAS_SIZE;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.readableId;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.tokenIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.accounts.StackedContractAliases;
import com.hedera.node.app.service.mono.ledger.interceptors.AccountsCommitInterceptor;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.enums.TokenType;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.txns.customfees.LedgerCustomFeeSchedules;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class WorldLedgers {
    private static final Logger log = LogManager.getLogger(WorldLedgers.class);

    private final ContractAliases aliases;
    private final StaticEntityAccess staticEntityAccess;
    private final TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger;
    private final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;
    private final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;
    private final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
            tokenRelsLedger;

    public static WorldLedgers staticLedgersWith(
            final ContractAliases aliases, final StaticEntityAccess staticEntityAccess) {
        return new WorldLedgers(aliases, staticEntityAccess);
    }

    public WorldLedgers(
            final ContractAliases aliases,
            final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
                    tokenRelsLedger,
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger,
            final TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger,
            final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger) {
        this.tokenRelsLedger = tokenRelsLedger;
        this.accountsLedger = accountsLedger;
        this.tokensLedger = tokensLedger;
        this.nftsLedger = nftsLedger;
        this.aliases = aliases;

        staticEntityAccess = null;
    }

    private WorldLedgers(
            final ContractAliases aliases, final StaticEntityAccess staticEntityAccess) {
        tokenRelsLedger = null;
        accountsLedger = null;
        tokensLedger = null;
        nftsLedger = null;

        this.aliases = aliases;
        this.staticEntityAccess = staticEntityAccess;
    }

    public boolean isTokenAddress(final Address address) {
        if (staticEntityAccess != null) {
            return staticEntityAccess.isTokenAccount(address);
        } else {
            return tokensLedger.contains(tokenIdFromEvmAddress(address));
        }
    }

    public boolean defaultFreezeStatus(final TokenID tokenId) {
        return propertyOf(tokenId, ACC_FROZEN_BY_DEFAULT, StaticEntityAccess::defaultFreezeStatus);
    }

    public boolean defaultKycStatus(final TokenID tokenId) {
        return propertyOf(
                tokenId, ACC_KYC_GRANTED_BY_DEFAULT, StaticEntityAccess::defaultKycStatus);
    }

    public Optional<TokenInfo> infoForToken(final TokenID tokenId, final ByteString ledgerId) {
        if (staticEntityAccess != null) {
            return staticEntityAccess.infoForToken(tokenId);
        } else {
            final var token = tokensLedger.getImmutableRef(tokenId);
            if (token == null) {
                return Optional.empty();
            }
            return Optional.of(token.asTokenInfo(tokenId, ledgerId));
        }
    }

    public Optional<TokenNftInfo> infoForNft(final NftID target, final ByteString ledgerId) {
        if (staticEntityAccess != null) {
            return staticEntityAccess.infoForNft(target);
        } else {
            final var tokenId = EntityNum.fromTokenId(target.getTokenID());
            final var targetKey =
                    NftId.withDefaultShardRealm(tokenId.longValue(), target.getSerialNumber());
            if (!nftsLedger.contains(targetKey)) {
                return Optional.empty();
            }
            final var targetNft = nftsLedger.getImmutableRef(targetKey);
            var accountId = targetNft.getOwner().toGrpcAccountId();

            if (WILDCARD_OWNER.equals(accountId)) {
                var merkleToken = tokensLedger.getImmutableRef(target.getTokenID());
                if (merkleToken == null) {
                    return Optional.empty();
                }
                accountId = merkleToken.treasury().toGrpcAccountId();
            }

            final var spenderId = targetNft.getSpender().toGrpcAccountId();

            final var info =
                    TokenNftInfo.newBuilder()
                            .setLedgerId(ledgerId)
                            .setNftID(target)
                            .setAccountID(accountId)
                            .setCreationTime(targetNft.getCreationTime().toGrpc())
                            .setMetadata(ByteString.copyFrom(targetNft.getMetadata()))
                            .setSpenderId(spenderId)
                            .build();
            return Optional.of(info);
        }
    }

    public Optional<List<CustomFee>> infoForTokenCustomFees(final TokenID tokenId) {
        if (staticEntityAccess != null) {
            return Optional.of(staticEntityAccess.infoForTokenCustomFees(tokenId));
        } else {
            try {
                final var token = tokensLedger.getImmutableRef(tokenId);
                if (token == null) {
                    return Optional.empty();
                }
                return Optional.of(token.grpcFeeSchedule());
            } catch (Exception unexpected) {
                log.warn(
                        "Unexpected failure getting custom fees for token {}!",
                        readableId(tokenId),
                        unexpected);
                return Optional.empty();
            }
        }
    }

    public String nameOf(final TokenID tokenId) {
        return propertyOf(tokenId, NAME, StaticEntityAccess::nameOf);
    }

    public String symbolOf(final TokenID tokenId) {
        return propertyOf(tokenId, SYMBOL, StaticEntityAccess::symbolOf);
    }

    public long totalSupplyOf(final TokenID tokenId) {
        return propertyOf(tokenId, TOTAL_SUPPLY, StaticEntityAccess::supplyOf);
    }

    public int decimalsOf(final TokenID tokenId) {
        return propertyOf(tokenId, DECIMALS, StaticEntityAccess::decimalsOf);
    }

    public TokenType typeOf(final TokenID tokenId) {
        return propertyOf(tokenId, TOKEN_TYPE, StaticEntityAccess::typeOf);
    }

    public long balanceOf(final AccountID accountId, final TokenID tokenId) {
        if (staticEntityAccess != null) {
            return staticEntityAccess.balanceOf(accountId, tokenId);
        } else {
            validateTrue(tokensLedger.exists(tokenId), INVALID_TOKEN_ID);
            validateTrue(accountsLedger.exists(accountId), INVALID_ACCOUNT_ID);
            final var balanceKey = Pair.of(accountId, tokenId);
            return tokenRelsLedger.exists(balanceKey)
                    ? (long) tokenRelsLedger.get(balanceKey, TOKEN_BALANCE)
                    : 0;
        }
    }

    public boolean isKyc(final AccountID accountId, final TokenID tokenId) {
        if (staticEntityAccess != null) {
            return staticEntityAccess.isKyc(accountId, tokenId);
        } else {
            validateTrue(tokensLedger.exists(tokenId), INVALID_TOKEN_ID);
            validateTrue(accountsLedger.exists(accountId), INVALID_ACCOUNT_ID);
            final var isKycKey = Pair.of(accountId, tokenId);
            return tokenRelsLedger.exists(isKycKey)
                    && (boolean) tokenRelsLedger.get(isKycKey, IS_KYC_GRANTED);
        }
    }

    public boolean isFrozen(final AccountID accountId, final TokenID tokenId) {
        if (staticEntityAccess != null) {
            return staticEntityAccess.isFrozen(accountId, tokenId);
        } else {
            validateTrue(tokensLedger.exists(tokenId), INVALID_TOKEN_ID);
            validateTrue(accountsLedger.exists(accountId), INVALID_ACCOUNT_ID);
            final var isFrozenKey = Pair.of(accountId, tokenId);
            return tokenRelsLedger.exists(isFrozenKey)
                    && (boolean) tokenRelsLedger.get(isFrozenKey, IS_FROZEN);
        }
    }

    public long staticAllowanceOf(
            final AccountID ownerId, final AccountID spenderId, final TokenID tokenId) {
        if (staticEntityAccess == null) {
            throw new IllegalStateException(
                    "staticAllowanceOf should only be used with StaticEntityAccess");
        } else {
            return staticEntityAccess.allowanceOf(ownerId, spenderId, tokenId);
        }
    }

    public Address staticApprovedSpenderOf(final NftId nftId) {
        if (staticEntityAccess == null) {
            throw new IllegalStateException(
                    "staticApprovedOf should only be used with StaticEntityAccess");
        } else {
            return staticEntityAccess.approvedSpenderOf(nftId);
        }
    }

    public boolean staticIsOperator(
            final AccountID ownerId, final AccountID operatorId, final TokenID tokenId) {
        if (staticEntityAccess == null) {
            throw new IllegalStateException(
                    "staticApprovedOf should only be used with StaticEntityAccess");
        } else {
            return staticEntityAccess.isOperator(ownerId, operatorId, tokenId);
        }
    }

    @Nullable
    public EntityId ownerIfPresent(final NftId nftId) {
        if (!areMutable()) {
            throw new IllegalStateException(
                    "Static ledgers cannot be used to get owner if present");
        }
        return nftsLedger.contains(nftId) ? explicitOwnerOfExtant(nftId) : null;
    }

    public Address ownerOf(final NftId nftId) {
        if (!areMutable()) {
            return staticEntityAccess.ownerOf(nftId);
        }
        return explicitOwnerOfExtant(nftId).toEvmAddress();
    }

    @SuppressWarnings("unchecked")
    public boolean hasApprovedForAll(
            final AccountID ownerId, final AccountID operatorId, final TokenID tokenId) {
        if (!areMutable()) {
            throw new IllegalStateException(
                    "Static ledgers cannot be used to check approvedForAll");
        }
        final Set<FcTokenAllowanceId> approvedForAll =
                (Set<FcTokenAllowanceId>)
                        accountsLedger.get(ownerId, APPROVE_FOR_ALL_NFTS_ALLOWANCES);
        return approvedForAll.contains(FcTokenAllowanceId.from(tokenId, operatorId));
    }

    public String metadataOf(final NftId nftId) {
        if (!areMutable()) {
            return staticEntityAccess.metadataOf(nftId);
        }
        return nftsLedger.exists(nftId)
                ? new String((byte[]) nftsLedger.get(nftId, METADATA))
                : HTSPrecompiledContract.URI_QUERY_NON_EXISTING_TOKEN_ERROR;
    }

    public JKey keyOf(final TokenID tokenId, final TokenProperty keyType) {
        if (!areMutable()) {
            return staticEntityAccess.keyOf(tokenId, keyType);
        }
        return (JKey) tokensLedger.get(tokenId, keyType);
    }

    public Address canonicalAddress(final Address addressOrAlias) {
        if (aliases.isInUse(addressOrAlias)) {
            return addressOrAlias;
        }

        return getAddressOrAlias(addressOrAlias);
    }

    public Address getAddressOrAlias(final Address address) {
        final var sourceId = accountIdFromEvmAddress(address);
        final ByteString alias;
        if (accountsLedger != null) {
            if (!accountsLedger.exists(sourceId)) {
                return address;
            }
            alias = (ByteString) accountsLedger.get(sourceId, ALIAS);
        } else {
            Objects.requireNonNull(
                    staticEntityAccess, "Null ledgers must imply non-null static access");
            if (!staticEntityAccess.isExtant(address)) {
                return address;
            }
            alias = staticEntityAccess.alias(address);
        }
        if (!alias.isEmpty()) {
            if (alias.size() == EVM_ADDRESS_SIZE) {
                return Address.wrap(Bytes.wrap(alias.toByteArray()));
            } else if (alias.size() == ECDSA_SECP256K1_ALIAS_SIZE
                    && alias.startsWith(ECDSA_KEY_ALIAS_PREFIX)) {
                final byte[] value = recoverAddressFromPubKey(alias.substring(2).toByteArray());
                if (value.length > 0) {
                    return Address.wrap(Bytes.wrap(value));
                }
            }
        }
        return address;
    }

    public void commit() {
        if (areMutable()) {
            aliases.commit(null);
            commitLedgers();
        }
    }

    public void commit(final SigImpactHistorian sigImpactHistorian) {
        if (areMutable()) {
            aliases.commit(sigImpactHistorian);
            commitLedgers();
        }
    }

    private void commitLedgers() {
        tokenRelsLedger.commit();
        accountsLedger.commit();
        nftsLedger.commit();
        tokensLedger.commit();
    }

    public void revert() {
        if (areMutable()) {
            tokenRelsLedger.rollback();
            accountsLedger.rollback();
            nftsLedger.rollback();
            tokensLedger.rollback();
            aliases.revert();

            /* Since AbstractMessageProcessor.clearAccumulatedStateBesidesGasAndOutput() will make a
             * second token call to commit() after the initial revert(), we want to keep these ledgers
             * in an active transaction. */
            tokenRelsLedger.begin();
            accountsLedger.begin();
            nftsLedger.begin();
            tokensLedger.begin();
        }
    }

    public boolean areMutable() {
        return nftsLedger != null
                && tokensLedger != null
                && accountsLedger != null
                && tokenRelsLedger != null;
    }

    public WorldLedgers wrapped() {
        return wrappedInternal(null);
    }

    public WorldLedgers wrapped(final SideEffectsTracker sideEffectsTracker) {
        return wrappedInternal(sideEffectsTracker);
    }

    public void customizeForAutoAssociatingOp(final SideEffectsTracker sideEffectsTracker) {
        if (!areMutable()) {
            throw new IllegalStateException("Static ledgers cannot be customized");
        }
        tokenRelsLedger.setCommitInterceptor(forKnownAutoAssociatingOp(sideEffectsTracker));
    }

    private WorldLedgers wrappedInternal(@Nullable final SideEffectsTracker sideEffectsTracker) {
        if (!areMutable()) {
            return staticLedgersWith(StackedContractAliases.wrapping(aliases), staticEntityAccess);
        }

        final var wrappedNftsLedger = activeLedgerWrapping(nftsLedger);
        final var wrappedTokensLedger = activeLedgerWrapping(tokensLedger);
        final var wrappedAccountsLedger = activeLedgerWrapping(accountsLedger);
        if (sideEffectsTracker != null) {
            final var accountsCommitInterceptor = new AccountsCommitInterceptor(sideEffectsTracker);
            wrappedAccountsLedger.setCommitInterceptor(accountsCommitInterceptor);
        }
        final var wrappedTokenRelsLedger = activeLedgerWrapping(tokenRelsLedger);

        return new WorldLedgers(
                StackedContractAliases.wrapping(aliases),
                wrappedTokenRelsLedger,
                wrappedAccountsLedger,
                wrappedNftsLedger,
                wrappedTokensLedger);
    }

    public ContractAliases aliases() {
        return aliases;
    }

    public TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
            tokenRels() {
        return tokenRelsLedger;
    }

    public TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts() {
        return accountsLedger;
    }

    public TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nfts() {
        return nftsLedger;
    }

    public TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens() {
        return tokensLedger;
    }

    public LedgerCustomFeeSchedules customFeeSchedules() {
        return new LedgerCustomFeeSchedules(tokensLedger);
    }

    // --- Internal helpers
    private <T> T propertyOf(
            final TokenID tokenId,
            final TokenProperty property,
            final BiFunction<StaticEntityAccess, TokenID, T> staticGetter) {
        if (staticEntityAccess != null) {
            return staticGetter.apply(staticEntityAccess, tokenId);
        } else {
            return getTokenMeta(tokenId, property);
        }
    }

    private <T> T getTokenMeta(final TokenID tokenId, final TokenProperty property) {
        final var value = (T) tokensLedger.get(tokenId, property);
        validateTrue(value != null, INVALID_TOKEN_ID);
        return value;
    }

    private EntityId explicitOwnerOfExtant(final NftId nftId) {
        var owner = (EntityId) nftsLedger.get(nftId, OWNER);
        if (MISSING_ENTITY_ID.equals(owner)) {
            owner = (EntityId) tokensLedger.get(nftId.tokenId(), TREASURY);
        }
        return owner;
    }
}
