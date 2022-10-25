/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.crypto.validators;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.protobuf.BoolValue;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.backing.BackingAccounts;
import com.hedera.services.ledger.backing.BackingNfts;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.backing.BackingTokenRels;
import com.hedera.services.ledger.backing.BackingTokens;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.ReadOnlyTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApproveAllowanceChecksTest {
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private Account owner;
    @Mock private Account treasury;
    @Mock private Account payerAccount;
    @Mock private StateView view;
    @Mock private MerkleToken merkleTokenFungible;
    @Mock private MerkleToken merkleTokenNFT;
    @Mock private OptionValidator validator;
    @Mock private MerkleAccount ownerAccount;
    @Mock private UniqueToken uniqueToken;
    @Mock private AccountStore accountStore;
    @Mock private ReadOnlyTokenStore tokenStore;

    ApproveAllowanceChecks subject;
    UniqueTokenAdapter uniqueTokenAdapter;

    private final AccountID spender1 = asAccount("0.0.123");
    private final AccountID spender2 = asAccount("0.0.1234");
    private final TokenID token1 = asToken("0.0.100");
    private final TokenID token2 = asToken("0.0.200");
    private final AccountID ownerId1 = asAccount("0.0.5000");
    private final AccountID ownerId2 = asAccount("0.0.5001");
    private final AccountID payer = asAccount("0.0.3000");
    private final Id tokenId1 = Id.fromGrpcToken(token1);
    private final Id tokenId2 = Id.fromGrpcToken(token2);

    private final Token token1Model = new Token(tokenId1);
    private final Token token2Model = new Token(tokenId2);

    private final CryptoAllowance cryptoAllowance1 =
            CryptoAllowance.newBuilder()
                    .setSpender(spender1)
                    .setAmount(10L)
                    .setOwner(ownerId1)
                    .build();
    private final CryptoAllowance cryptoAllowance2 =
            CryptoAllowance.newBuilder()
                    .setSpender(spender1)
                    .setAmount(10L)
                    .setOwner(ownerId2)
                    .build();
    private final TokenAllowance tokenAllowance1 =
            TokenAllowance.newBuilder()
                    .setSpender(spender1)
                    .setAmount(10L)
                    .setTokenId(token1)
                    .setOwner(ownerId1)
                    .build();
    private final TokenAllowance tokenAllowance2 =
            TokenAllowance.newBuilder()
                    .setSpender(spender1)
                    .setAmount(10L)
                    .setTokenId(token1)
                    .setOwner(ownerId2)
                    .build();
    private final NftAllowance nftAllowance1 =
            NftAllowance.newBuilder()
                    .setSpender(spender1)
                    .setOwner(ownerId1)
                    .setTokenId(token2)
                    .setApprovedForAll(BoolValue.of(false))
                    .addAllSerialNumbers(List.of(1L, 10L))
                    .build();
    private final NftAllowance nftAllowance2 =
            NftAllowance.newBuilder()
                    .setSpender(spender1)
                    .setOwner(ownerId2)
                    .setTokenId(token2)
                    .setApprovedForAll(BoolValue.of(false))
                    .addAllSerialNumbers(List.of(1L, 10L))
                    .build();
    final NftId token2Nft1 = new NftId(0, 0, token2.getTokenNum(), 1L);
    final NftId token2Nft2 = new NftId(0, 0, token2.getTokenNum(), 10L);

    private List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
    private List<TokenAllowance> tokenAllowances = new ArrayList<>();
    private List<NftAllowance> nftAllowances = new ArrayList<>();

    private TransactionBody cryptoApproveAllowanceTxn;
    private CryptoApproveAllowanceTransactionBody op;

    @BeforeEach
    void setUp() {
        token1Model.initSupplyConstraints(TokenSupplyType.FINITE, 5000L);
        token1Model.setType(TokenType.FUNGIBLE_COMMON);
        token2Model.initSupplyConstraints(TokenSupplyType.FINITE, 5000L);
        token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

        cryptoAllowances.add(cryptoAllowance1);
        tokenAllowances.add(tokenAllowance1);
        nftAllowances.add(nftAllowance1);

        subject = new ApproveAllowanceChecks(dynamicProperties, validator);
    }

    private void setUpForTest() {
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(merkleTokenFungible.supplyType()).willReturn(TokenSupplyType.INFINITE);
        given(merkleTokenFungible.tokenType()).willReturn(TokenType.FUNGIBLE_COMMON);
        given(merkleTokenNFT.tokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);

        final BackingStore<AccountID, HederaAccount> store = mock(BackingAccounts.class);
        final BackingStore<TokenID, MerkleToken> tokens = mock(BackingTokens.class);
        final BackingStore<NftId, UniqueTokenAdapter> nfts = mock(BackingNfts.class);
        uniqueTokenAdapter =
                UniqueTokenAdapter.wrap(
                        new MerkleUniqueToken(
                                EntityId.fromGrpcAccountId(ownerId1),
                                new byte[0],
                                RichInstant.MISSING_INSTANT));
        uniqueTokenAdapter.setSpender(EntityId.fromGrpcAccountId(spender1));

        BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> rels =
                mock(BackingTokenRels.class);
        given(view.asReadOnlyAccountStore()).willReturn(store);
        given(view.asReadOnlyTokenStore()).willReturn(tokens);
        given(view.asReadOnlyNftStore()).willReturn(nfts);
        given(view.asReadOnlyAssociationStore()).willReturn(rels);
        given(ownerAccount.getNumAssociations()).willReturn(1);
        given(ownerAccount.getNumPositiveBalances()).willReturn(0);

        given(store.getImmutableRef(ownerId1)).willReturn(ownerAccount);
        given(tokens.getImmutableRef(token1)).willReturn(merkleTokenFungible);
        given(tokens.getImmutableRef(token2)).willReturn(merkleTokenNFT);
        given(nfts.getImmutableRef(token2Nft1)).willReturn(uniqueTokenAdapter);
        given(nfts.getImmutableRef(token2Nft2)).willReturn(uniqueTokenAdapter);
        given(rels.contains(Pair.of(ownerId1, token1))).willReturn(true);
        given(rels.contains(Pair.of(ownerId1, token2))).willReturn(true);

        given(merkleTokenFungible.treasury()).willReturn(EntityId.fromGrpcAccountId(ownerId1));
        given(merkleTokenNFT.treasury()).willReturn(EntityId.fromGrpcAccountId(ownerId1));
    }

    @Test
    void isEnabledReturnsCorrectly() {
        given(dynamicProperties.areAllowancesEnabled()).willReturn(false);
        assertEquals(false, subject.isEnabled());

        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        assertEquals(true, subject.isEnabled());
    }

    @Test
    void failsIfAllowanceFeatureIsNotTurnedOn() {
        given(dynamicProperties.areAllowancesEnabled()).willReturn(false);

        cryptoAllowances.add(cryptoAllowance2);
        tokenAllowances.add(tokenAllowance2);
        nftAllowances.add(nftAllowance2);

        final var validity =
                subject.allowancesValidation(
                        cryptoAllowances, tokenAllowances, nftAllowances, payerAccount, view);

        assertEquals(NOT_SUPPORTED, validity);
    }

    @Test
    void returnsValidationOnceFailed() {
        cryptoAllowances.add(cryptoAllowance1);
        tokenAllowances.add(tokenAllowance1);
        nftAllowances.add(nftAllowance1);

        cryptoApproveAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoApproveAllowance(
                                CryptoApproveAllowanceTransactionBody.newBuilder()
                                        .addAllCryptoAllowances(cryptoAllowances)
                                        .build())
                        .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        assertEquals(
                MAX_ALLOWANCES_EXCEEDED,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        owner,
                        view));

        cryptoApproveAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoApproveAllowance(
                                CryptoApproveAllowanceTransactionBody.newBuilder()
                                        .addAllTokenAllowances(tokenAllowances)
                                        .build())
                        .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        assertEquals(
                MAX_ALLOWANCES_EXCEEDED,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        owner,
                        view));

        cryptoApproveAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoApproveAllowance(
                                CryptoApproveAllowanceTransactionBody.newBuilder()
                                        .addAllNftAllowances(nftAllowances)
                                        .build())
                        .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        assertEquals(
                MAX_ALLOWANCES_EXCEEDED,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        owner,
                        view));
    }

    @Test
    void succeedsWithEmptyLists() {
        cryptoApproveAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoApproveAllowance(
                                CryptoApproveAllowanceTransactionBody.newBuilder().build())
                        .build();
        assertEquals(
                OK,
                subject.validateCryptoAllowances(
                        cryptoApproveAllowanceTxn
                                .getCryptoApproveAllowance()
                                .getCryptoAllowancesList(),
                        owner,
                        accountStore));
        assertEquals(
                OK,
                subject.validateFungibleTokenAllowances(
                        cryptoApproveAllowanceTxn
                                .getCryptoApproveAllowance()
                                .getTokenAllowancesList(),
                        owner,
                        accountStore,
                        tokenStore));
        assertEquals(
                OK,
                subject.validateNftAllowances(
                        cryptoApproveAllowanceTxn
                                .getCryptoApproveAllowance()
                                .getNftAllowancesList(),
                        owner,
                        accountStore,
                        tokenStore));
    }

    @Test
    void failsIfOwnerSameAsSpender() {
        given(tokenStore.loadPossiblyPausedToken(tokenId1)).willReturn(token1Model);
        given(tokenStore.loadPossiblyPausedToken(tokenId2)).willReturn(token2Model);
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(tokenStore.hasAssociation(token1Model, owner)).willReturn(true);
        given(tokenStore.hasAssociation(token2Model, owner)).willReturn(true);
        given(tokenStore.loadUniqueToken(tokenId2, 1L)).willReturn(uniqueToken);
        given(tokenStore.loadUniqueToken(tokenId2, 10L)).willReturn(uniqueToken);

        final var badCryptoAllowance =
                CryptoAllowance.newBuilder()
                        .setSpender(ownerId1)
                        .setOwner(ownerId1)
                        .setAmount(10L)
                        .build();
        final var badTokenAllowance =
                TokenAllowance.newBuilder()
                        .setSpender(ownerId1)
                        .setOwner(ownerId1)
                        .setAmount(20L)
                        .setTokenId(token1)
                        .build();
        final var badNftAllowance =
                NftAllowance.newBuilder()
                        .setSpender(ownerId1)
                        .setTokenId(token2)
                        .setApprovedForAll(BoolValue.of(false))
                        .setOwner(ownerId1)
                        .addAllSerialNumbers(List.of(1L))
                        .build();

        cryptoAllowances.add(badCryptoAllowance);
        assertEquals(
                SPENDER_ACCOUNT_SAME_AS_OWNER,
                subject.validateCryptoAllowances(cryptoAllowances, owner, accountStore));

        tokenAllowances.add(badTokenAllowance);
        assertEquals(
                SPENDER_ACCOUNT_SAME_AS_OWNER,
                subject.validateFungibleTokenAllowances(
                        tokenAllowances, owner, accountStore, tokenStore));

        nftAllowances.add(badNftAllowance);
        assertEquals(
                SPENDER_ACCOUNT_SAME_AS_OWNER,
                subject.validateNftAllowances(nftAllowances, owner, accountStore, tokenStore));
    }

    @Test
    void validateNegativeAmounts() {
        given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
        given(tokenStore.hasAssociation(token1Model, owner)).willReturn(true);
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));

        final var badCryptoAllowance =
                CryptoAllowance.newBuilder()
                        .setSpender(spender2)
                        .setAmount(-10L)
                        .setOwner(ownerId1)
                        .build();
        final var badTokenAllowance =
                TokenAllowance.newBuilder()
                        .setSpender(spender2)
                        .setAmount(-20L)
                        .setTokenId(token1)
                        .setOwner(ownerId1)
                        .build();

        cryptoAllowances.add(badCryptoAllowance);
        assertEquals(
                NEGATIVE_ALLOWANCE_AMOUNT,
                subject.validateCryptoAllowances(cryptoAllowances, owner, accountStore));

        tokenAllowances.add(badTokenAllowance);
        assertEquals(
                NEGATIVE_ALLOWANCE_AMOUNT,
                subject.validateFungibleTokenAllowances(
                        tokenAllowances, owner, accountStore, tokenStore));
    }

    @Test
    void failsWhenExceedsMaxTokenSupply() {
        given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
        given(tokenStore.hasAssociation(token1Model, owner)).willReturn(true);
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        final var badTokenAllowance =
                TokenAllowance.newBuilder()
                        .setSpender(spender2)
                        .setAmount(100000L)
                        .setTokenId(token1)
                        .setOwner(ownerId1)
                        .build();

        tokenAllowances.add(badTokenAllowance);
        assertEquals(
                AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY,
                subject.validateFungibleTokenAllowances(
                        tokenAllowances, owner, accountStore, tokenStore));
    }

    @Test
    void failsForNftInFungibleTokenAllowances() {
        given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
        given(tokenStore.hasAssociation(token1Model, owner)).willReturn(true);
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
        final var badTokenAllowance =
                TokenAllowance.newBuilder()
                        .setSpender(spender2)
                        .setAmount(100000L)
                        .setTokenId(token2)
                        .setOwner(ownerId1)
                        .build();

        tokenAllowances.add(badTokenAllowance);
        assertEquals(
                NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES,
                subject.validateFungibleTokenAllowances(
                        tokenAllowances, owner, accountStore, tokenStore));
    }

    @Test
    void returnsInvalidOwnerId() {
        given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(120);

        final BackingStore<AccountID, HederaAccount> store = mock(BackingAccounts.class);
        final BackingStore<TokenID, MerkleToken> tokens = mock(BackingTokens.class);
        final BackingStore<NftId, UniqueTokenAdapter> nfts = mock(BackingNfts.class);
        given(view.asReadOnlyAccountStore()).willReturn(store);
        given(view.asReadOnlyTokenStore()).willReturn(tokens);
        given(view.asReadOnlyNftStore()).willReturn(nfts);
        given(store.getImmutableRef(ownerId1)).willThrow(InvalidTransactionException.class);

        given(tokens.getImmutableRef(token1)).willReturn(merkleTokenFungible);
        given(tokens.getImmutableRef(token2)).willReturn(merkleTokenNFT);
        given(merkleTokenFungible.treasury()).willReturn(EntityId.fromGrpcAccountId(payer));
        given(merkleTokenNFT.treasury()).willReturn(EntityId.fromGrpcAccountId(payer));
        given(store.getImmutableRef(payer)).willReturn(ownerAccount);

        cryptoApproveAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoApproveAllowance(
                                CryptoApproveAllowanceTransactionBody.newBuilder()
                                        .addAllCryptoAllowances(cryptoAllowances)
                                        .build())
                        .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
        given(payerAccount.getId()).willReturn(Id.fromGrpcAccount(payer));
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        assertEquals(
                INVALID_ALLOWANCE_OWNER_ID,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        payerAccount,
                        view));

        cryptoApproveAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoApproveAllowance(
                                CryptoApproveAllowanceTransactionBody.newBuilder()
                                        .addAllTokenAllowances(tokenAllowances)
                                        .build())
                        .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
        given(ownerAccount.getNumAssociations()).willReturn(1);
        given(ownerAccount.getNumPositiveBalances()).willReturn(0);
        given(validator.expiryStatusGiven(anyLong(), anyLong(), anyBoolean())).willReturn(OK);

        assertEquals(
                INVALID_ALLOWANCE_OWNER_ID,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        payerAccount,
                        view));

        cryptoApproveAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoApproveAllowance(
                                CryptoApproveAllowanceTransactionBody.newBuilder()
                                        .addAllNftAllowances(nftAllowances)
                                        .build())
                        .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        assertEquals(
                INVALID_ALLOWANCE_OWNER_ID,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        payerAccount,
                        view));
    }

    @Test
    void cannotGrantApproveForAllUsingDelegatingSpender() {
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
        given(tokenStore.hasAssociation(token2Model, owner)).willReturn(true);

        final var badNftAllowance =
                NftAllowance.newBuilder()
                        .setSpender(spender2)
                        .addAllSerialNumbers(List.of(1L))
                        .setTokenId(token2)
                        .setOwner(ownerId1)
                        .setDelegatingSpender(spender1)
                        .setApprovedForAll(BoolValue.of(true))
                        .build();

        nftAllowances.clear();
        nftAllowances.add(badNftAllowance);

        assertEquals(
                DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL,
                subject.validateNftAllowances(nftAllowances, owner, accountStore, tokenStore));
    }

    @Test
    void cannotGrantExplicitNftAllowanceUsingDelegatingSpenderWithNoApproveForAllAllowance() {
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
        given(tokenStore.hasAssociation(token2Model, owner)).willReturn(true);
        given(owner.getApprovedForAllNftsAllowances()).willReturn(Collections.emptySet());

        final var badNftAllowance =
                NftAllowance.newBuilder()
                        .setSpender(spender2)
                        .addAllSerialNumbers(List.of(1L))
                        .setTokenId(token2)
                        .setOwner(ownerId1)
                        .setDelegatingSpender(spender1)
                        .setApprovedForAll(BoolValue.of(false))
                        .build();

        nftAllowances.clear();
        nftAllowances.add(badNftAllowance);

        assertEquals(
                DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL,
                subject.validateNftAllowances(nftAllowances, owner, accountStore, tokenStore));
    }

    @Test
    void canGrantExplicitNftAllowanceUsingDelegatingSpenderWithApproveForAllAllowance() {
        final var allowanceKey =
                FcTokenAllowanceId.from(
                        EntityNum.fromTokenId(token2), EntityNum.fromAccountId(spender1));
        final NftId token1Nft1 = new NftId(0, 0, token2.getTokenNum(), 1L);
        final EntityNumPair numpair = EntityNumPair.fromNftId(token1Nft1);

        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
        given(tokenStore.hasAssociation(token2Model, owner)).willReturn(true);
        given(owner.getApprovedForAllNftsAllowances()).willReturn(Set.of(allowanceKey));

        final var badNftAllowance =
                NftAllowance.newBuilder()
                        .setSpender(spender2)
                        .addAllSerialNumbers(List.of(1L))
                        .setTokenId(token2)
                        .setOwner(ownerId1)
                        .setDelegatingSpender(spender1)
                        .setApprovedForAll(BoolValue.of(false))
                        .build();

        nftAllowances.clear();
        nftAllowances.add(badNftAllowance);

        assertEquals(
                OK, subject.validateNftAllowances(nftAllowances, owner, accountStore, tokenStore));
    }

    @Test
    void failsWhenTokenNotAssociatedToAccount() {
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
        given(tokenStore.hasAssociation(token1Model, owner)).willReturn(false);
        assertEquals(
                TOKEN_NOT_ASSOCIATED_TO_ACCOUNT,
                subject.validateFungibleTokenAllowances(
                        tokenAllowances, owner, accountStore, tokenStore));
    }

    @Test
    void happyPath() {
        setUpForTest();
        getValidTxnCtx();

        given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(validator.expiryStatusGiven(anyLong(), anyLong(), anyBoolean())).willReturn(OK);

        assertEquals(
                OK,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        owner,
                        view));
    }

    @Test
    void fungibleInNFTAllowances() {
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
        given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
        given(tokenStore.hasAssociation(token2Model, owner)).willReturn(true);
        given(tokenStore.loadUniqueToken(tokenId2, 1L)).willReturn(uniqueToken);
        given(tokenStore.loadUniqueToken(tokenId2, 10L)).willReturn(uniqueToken);

        final var badNftAllowance =
                NftAllowance.newBuilder()
                        .setSpender(spender2)
                        .addAllSerialNumbers(List.of(1L))
                        .setTokenId(token1)
                        .setOwner(ownerId1)
                        .setApprovedForAll(BoolValue.of(false))
                        .build();

        nftAllowances.add(badNftAllowance);
        assertEquals(
                FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES,
                subject.validateNftAllowances(nftAllowances, owner, accountStore, tokenStore));
    }

    @Test
    void validateSerialsExistence() {
        final var serials = List.of(1L, 10L);
        given(tokenStore.loadUniqueToken(tokenId2, 1L))
                .willThrow(InvalidTransactionException.class);

        var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
    }

    @Test
    void validateInvalidSerials() {
        final var serials = List.of(-1L, 10L);
        var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
    }

    @Test
    void approvesAllowanceFromTreasury() {
        final var serials = List.of(1L);
        token2Model.setTreasury(treasury);
        given(tokenStore.loadUniqueToken(tokenId2, 1L)).willReturn(uniqueToken);

        var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
        assertEquals(OK, validity);
    }

    @Test
    void validateRepeatedSerials() {
        final var serials = List.of(1L, 10L, 1L);
        var validity = subject.validateSerialNums(serials, token2Model, tokenStore);
        assertEquals(OK, validity);
    }

    @Test
    void semanticCheckForEmptyAllowancesInOp() {
        cryptoApproveAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoApproveAllowance(
                                CryptoApproveAllowanceTransactionBody.newBuilder())
                        .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        assertEquals(
                EMPTY_ALLOWANCES,
                subject.validateAllowanceCount(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList()));
    }

    @Test
    void semanticCheckForExceededLimitOfAllowancesInOp() {
        addAllowances();
        getValidTxnCtx();
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        assertEquals(
                MAX_ALLOWANCES_EXCEEDED,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        owner,
                        view));
    }

    @Test
    void loadsOwnerAccountNotDefaultingToPayer() {
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
        given(tokenStore.hasAssociation(token1Model, owner)).willReturn(true);
        given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId1))).willReturn(owner);

        getValidTxnCtx();

        assertEquals(
                OK,
                subject.validateFungibleTokenAllowances(
                        op.getTokenAllowancesList(), payerAccount, accountStore, tokenStore));
        verify(accountStore).loadAccount(Id.fromGrpcAccount(ownerId1));

        given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId1)))
                .willThrow(InvalidTransactionException.class);

        assertEquals(
                INVALID_ALLOWANCE_OWNER_ID,
                subject.validateFungibleTokenAllowances(
                        op.getTokenAllowancesList(), payerAccount, accountStore, tokenStore));
    }

    @Test
    void loadsOwnerAccountInNftNotDefaultingToPayer() {
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
        given(tokenStore.hasAssociation(token2Model, owner)).willReturn(true);

        given(tokenStore.loadUniqueToken(tokenId2, 1L)).willReturn(uniqueToken);
        given(tokenStore.loadUniqueToken(tokenId2, 10L)).willReturn(uniqueToken);
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId1))).willReturn(owner);

        getValidTxnCtx();

        assertEquals(
                OK,
                subject.validateNftAllowances(
                        op.getNftAllowancesList(), payerAccount, accountStore, tokenStore));
        verify(accountStore).loadAccount(Id.fromGrpcAccount(ownerId1));

        given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId1)))
                .willThrow(InvalidTransactionException.class);
        assertEquals(
                INVALID_ALLOWANCE_OWNER_ID,
                subject.validateNftAllowances(
                        op.getNftAllowancesList(), payerAccount, accountStore, tokenStore));
        verify(accountStore, times(2)).loadAccount(Id.fromGrpcAccount(ownerId1));
    }

    @Test
    void missingOwnerDefaultsToPayer() {
        setupNeeded();
        final CryptoAllowance cryptoAllowance1 =
                CryptoAllowance.newBuilder().setSpender(spender1).setAmount(10L).build();
        final TokenAllowance tokenAllowance1 =
                TokenAllowance.newBuilder()
                        .setSpender(spender1)
                        .setAmount(10L)
                        .setTokenId(token1)
                        .build();
        final NftAllowance nftAllowance1 =
                NftAllowance.newBuilder()
                        .setSpender(spender1)
                        .setTokenId(token2)
                        .setApprovedForAll(BoolValue.of(false))
                        .addAllSerialNumbers(List.of(1L, 10L))
                        .build();

        cryptoAllowances.clear();
        tokenAllowances.clear();
        nftAllowances.clear();
        cryptoAllowances.add(cryptoAllowance1);
        tokenAllowances.add(tokenAllowance1);
        nftAllowances.add(nftAllowance1);

        cryptoApproveAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoApproveAllowance(
                                CryptoApproveAllowanceTransactionBody.newBuilder()
                                        .addAllCryptoAllowances(cryptoAllowances))
                        .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
        assertEquals(
                OK,
                subject.validateCryptoAllowances(
                        cryptoApproveAllowanceTxn
                                .getCryptoApproveAllowance()
                                .getCryptoAllowancesList(),
                        payerAccount,
                        accountStore));
        verify(accountStore, never()).loadAccount(any());

        cryptoApproveAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoApproveAllowance(
                                CryptoApproveAllowanceTransactionBody.newBuilder()
                                        .addAllTokenAllowances(tokenAllowances))
                        .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
        assertEquals(
                OK,
                subject.validateFungibleTokenAllowances(
                        cryptoApproveAllowanceTxn
                                .getCryptoApproveAllowance()
                                .getTokenAllowancesList(),
                        payerAccount,
                        accountStore,
                        tokenStore));
        verify(accountStore, never()).loadAccount(any());

        cryptoApproveAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoApproveAllowance(
                                CryptoApproveAllowanceTransactionBody.newBuilder()
                                        .addAllNftAllowances(nftAllowances))
                        .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
        assertEquals(
                OK,
                subject.validateNftAllowances(
                        cryptoApproveAllowanceTxn
                                .getCryptoApproveAllowance()
                                .getNftAllowancesList(),
                        payerAccount,
                        accountStore,
                        tokenStore));
        verify(accountStore, never()).loadAccount(any());
    }

    private void setupNeeded() {
        given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
        given(tokenStore.loadPossiblyPausedToken(token2Model.getId())).willReturn(token2Model);
        given(tokenStore.loadUniqueToken(tokenId2, 1L)).willReturn(uniqueToken);
        given(tokenStore.loadUniqueToken(tokenId2, 10L)).willReturn(uniqueToken);
        given(payerAccount.getId()).willReturn(Id.fromGrpcAccount(payer));
        given(tokenStore.loadPossiblyPausedToken(token1Model.getId())).willReturn(token1Model);
        given(tokenStore.hasAssociation(token1Model, payerAccount)).willReturn(true);
        given(tokenStore.hasAssociation(token2Model, payerAccount)).willReturn(true);
    }

    private void addAllowances() {
        for (int i = 0; i < dynamicProperties.maxAllowanceLimitPerAccount(); i++) {
            cryptoAllowances.add(cryptoAllowance1);
            tokenAllowances.add(tokenAllowance1);
            nftAllowances.add(nftAllowance1);
        }
    }

    private void getValidTxnCtx() {
        cryptoApproveAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoApproveAllowance(
                                CryptoApproveAllowanceTransactionBody.newBuilder()
                                        .addAllCryptoAllowances(cryptoAllowances)
                                        .addAllTokenAllowances(tokenAllowances)
                                        .addAllNftAllowances(nftAllowances)
                                        .build())
                        .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(payer)
                .setTransactionValidStart(
                        Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
                .build();
    }
}
