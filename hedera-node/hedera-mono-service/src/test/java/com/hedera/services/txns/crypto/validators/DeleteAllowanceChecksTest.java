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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteAllowanceChecksTest {
    @Mock private AccountStore accountStore;
    @Mock private ReadOnlyTokenStore tokenStore;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private Account payer;
    @Mock private Account ownerAccount;
    @Mock private StateView view;
    @Mock private OptionValidator validator;
    @Mock private MerkleToken merkleToken;
    @Mock private MerkleAccount ownerMerkleAccount;
    @Mock private UniqueToken uniqueToken;

    UniqueTokenAdapter uniqueTokenAdapter;
    DeleteAllowanceChecks subject;

    private final AccountID spender1 = asAccount("0.0.123");

    private final TokenID nftToken = asToken("0.0.200");
    private final AccountID payerId = asAccount("0.0.5000");
    private final AccountID ownerId = asAccount("0.0.5001");

    private final Token nftModel = new Token(Id.fromGrpcToken(nftToken));

    private final NftRemoveAllowance nftAllowance1 =
            NftRemoveAllowance.newBuilder()
                    .setOwner(ownerId)
                    .setTokenId(nftToken)
                    .addAllSerialNumbers(List.of(1L, 10L))
                    .build();
    private final NftRemoveAllowance nftAllowance2 =
            NftRemoveAllowance.newBuilder()
                    .setOwner(ownerId)
                    .setTokenId(nftToken)
                    .addAllSerialNumbers(List.of(20L))
                    .build();
    private final NftRemoveAllowance nftAllowance3 =
            NftRemoveAllowance.newBuilder()
                    .setOwner(payerId)
                    .setTokenId(nftToken)
                    .addAllSerialNumbers(List.of(30L))
                    .build();

    private List<NftRemoveAllowance> nftAllowances = new ArrayList<>();

    private final Set<FcTokenAllowanceId> existingApproveForAllNftsAllowances = new TreeSet<>();

    final NftId nft1 = new NftId(0, 0, nftToken.getTokenNum(), 1L);
    final NftId nft2 = new NftId(0, 0, nftToken.getTokenNum(), 10L);

    private TransactionBody cryptoDeleteAllowanceTxn;
    private CryptoDeleteAllowanceTransactionBody op;

    @BeforeEach
    void setUp() {
        resetAllowances();
        nftModel.initSupplyConstraints(TokenSupplyType.FINITE, 5000L);
        nftModel.setType(TokenType.NON_FUNGIBLE_UNIQUE);

        nftAllowances.add(nftAllowance1);

        addExistingAllowancesAndSerials();

        subject = new DeleteAllowanceChecks(dynamicProperties, validator);
    }

    private void addExistingAllowancesAndSerials() {
        existingApproveForAllNftsAllowances.add(
                FcTokenAllowanceId.from(
                        EntityNum.fromTokenId(nftToken), EntityNum.fromAccountId(spender1)));
    }

    @Test
    void failsWhenNotSupported() {
        given(dynamicProperties.areAllowancesEnabled()).willReturn(false);
        assertEquals(NOT_SUPPORTED, subject.deleteAllowancesValidation(nftAllowances, payer, view));
    }

    @Test
    void validateIfSerialsEmpty() {
        final List<Long> serials = List.of();
        var validity = subject.validateDeleteSerialNums(serials, nftModel, tokenStore);
        assertEquals(EMPTY_ALLOWANCES, validity);
    }

    @Test
    void semanticCheckForEmptyAllowancesInOp() {
        cryptoDeleteAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.newBuilder())
                        .build();
        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
        assertEquals(EMPTY_ALLOWANCES, subject.validateAllowancesCount(op.getNftAllowancesList()));
    }

    @Test
    void rejectsMissingToken() {
        given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(nftToken)))
                .willThrow(new InvalidTransactionException(INVALID_TOKEN_ID, true));
        nftAllowances.add(nftAllowance2);
        assertEquals(
                INVALID_TOKEN_ID,
                subject.validateNftDeleteAllowances(
                        nftAllowances, payer, accountStore, tokenStore));
    }

    @Test
    void validatesIfOwnerExists() {
        given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(nftToken))).willReturn(nftModel);
        nftAllowances.add(nftAllowance2);
        given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId)))
                .willThrow(InvalidTransactionException.class);
        assertEquals(
                INVALID_ALLOWANCE_OWNER_ID,
                subject.validateNftDeleteAllowances(
                        nftAllowances, payer, accountStore, tokenStore));
    }

    @Test
    void considersPayerIfOwnerMissing() {
        final var allowance = NftRemoveAllowance.newBuilder().build();
        nftAllowances.add(allowance);
        assertEquals(
                Pair.of(payer, OK),
                subject.fetchOwnerAccount(
                        Id.fromGrpcAccount(allowance.getOwner()), payer, accountStore));
    }

    @Test
    void failsIfTokenNotAssociatedToAccount() {
        given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(nftToken))).willReturn(nftModel);
        nftAllowances.add(nftAllowance2);
        given(accountStore.loadAccount(Id.fromGrpcAccount(ownerId))).willReturn(ownerAccount);
        given(tokenStore.hasAssociation(nftModel, ownerAccount)).willReturn(false);
        assertEquals(
                TOKEN_NOT_ASSOCIATED_TO_ACCOUNT,
                subject.validateNftDeleteAllowances(
                        nftAllowances, payer, accountStore, tokenStore));
    }

    @Test
    void failsIfInvalidTypes() {
        nftAllowances.clear();

        nftModel.setType(TokenType.FUNGIBLE_COMMON);
        given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(nftToken))).willReturn(nftModel);
        nftModel.initSupplyConstraints(TokenSupplyType.FINITE, 5000L);
        nftAllowances.add(NftRemoveAllowance.newBuilder().setTokenId(nftToken).build());
        assertEquals(
                FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES,
                subject.validateNftDeleteAllowances(
                        nftAllowances, payer, accountStore, tokenStore));
    }

    @Test
    void returnsValidationOnceFailed() {
        nftAllowances.add(nftAllowance1);

        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(1);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        cryptoDeleteAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoDeleteAllowance(
                                CryptoDeleteAllowanceTransactionBody.newBuilder()
                                        .addAllNftAllowances(nftAllowances)
                                        .build())
                        .build();
        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

        assertEquals(
                MAX_ALLOWANCES_EXCEEDED,
                subject.deleteAllowancesValidation(op.getNftAllowancesList(), payer, view));
    }

    @Test
    void succeedsWithEmptyLists() {
        cryptoDeleteAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoDeleteAllowance(
                                CryptoDeleteAllowanceTransactionBody.newBuilder().build())
                        .build();
        assertEquals(
                OK,
                subject.validateNftDeleteAllowances(
                        cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance().getNftAllowancesList(),
                        payer,
                        accountStore,
                        tokenStore));
    }

    @Test
    void happyPath() {
        setUpForTest();
        getValidTxnCtx();
        given(validator.expiryStatusGiven(anyLong(), anyLong(), anyBoolean())).willReturn(OK);

        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(dynamicProperties.maxAllowanceLimitPerTransaction()).willReturn(20);
        assertEquals(
                OK, subject.deleteAllowancesValidation(op.getNftAllowancesList(), payer, view));
    }

    @Test
    void validateSerialsExistence() {
        final var serials = List.of(1L, 10L);
        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken), 1L))
                .willThrow(InvalidTransactionException.class);

        var validity = subject.validateSerialNums(serials, nftModel, tokenStore);
        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
    }

    @Test
    void returnsIfSerialsFail() {
        final var serials = List.of(1L, 10L);
        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken), 1L))
                .willThrow(InvalidTransactionException.class);
        var validity = subject.validateSerialNums(serials, nftModel, tokenStore);
        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
    }

    @Test
    void addsSerialsCorrectly() {
        nftAllowances.add(nftAllowance1);
        nftAllowances.add(nftAllowance2);
        assertEquals(5, subject.aggregateNftDeleteAllowances(nftAllowances));
    }

    @Test
    void validatesNegativeSerialsAreNotValid() {
        final var serials = List.of(-100L, 10L);

        var validity = subject.validateSerialNums(serials, nftModel, tokenStore);
        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
    }

    @Test
    void validateSerials() {
        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken), 10L)).willReturn(uniqueToken);
        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken), 1L)).willReturn(uniqueToken);

        var serials = List.of(1L, 10L, 1L);
        var validity = subject.validateSerialNums(serials, nftModel, tokenStore);
        assertEquals(OK, validity);

        serials = List.of(10L, 4L);
        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken), 10L))
                .willThrow(InvalidTransactionException.class);
        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken), 4L)).willReturn(uniqueToken);
        validity = subject.validateSerialNums(serials, nftModel, tokenStore);
        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);

        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken), 20L)).willReturn(uniqueToken);
        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(nftToken), 4L)).willReturn(uniqueToken);

        serials = List.of(20L, 4L);
        validity = subject.validateSerialNums(serials, nftModel, tokenStore);
        assertEquals(OK, validity);
    }

    private void getValidTxnCtx() {
        cryptoDeleteAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoDeleteAllowance(
                                CryptoDeleteAllowanceTransactionBody.newBuilder()
                                        .addAllNftAllowances(nftAllowances)
                                        .build())
                        .build();
        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(payerId)
                .setTransactionValidStart(
                        Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
                .build();
    }

    private void resetAllowances() {
        nftAllowances.clear();
    }

    private void setUpForTest() {
        given(payer.getId()).willReturn(Id.fromGrpcAccount(ownerId));
        final BackingStore<AccountID, HederaAccount> store = mock(BackingAccounts.class);
        final BackingStore<TokenID, MerkleToken> tokens = mock(BackingTokens.class);
        final BackingStore<NftId, UniqueTokenAdapter> nfts = mock(BackingNfts.class);

        uniqueTokenAdapter =
                UniqueTokenAdapter.wrap(
                        new MerkleUniqueToken(
                                EntityId.fromGrpcAccountId(ownerId),
                                new byte[0],
                                RichInstant.MISSING_INSTANT));

        BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> rels =
                mock(BackingTokenRels.class);
        given(view.asReadOnlyAccountStore()).willReturn(store);
        given(view.asReadOnlyTokenStore()).willReturn(tokens);
        given(view.asReadOnlyNftStore()).willReturn(nfts);
        given(view.asReadOnlyAssociationStore()).willReturn(rels);
        given(ownerMerkleAccount.getNumAssociations()).willReturn(1);
        given(ownerMerkleAccount.getNumPositiveBalances()).willReturn(0);

        given(store.getImmutableRef(ownerId)).willReturn(ownerMerkleAccount);
        given(tokens.getImmutableRef(nftToken)).willReturn(merkleToken);
        given(nfts.getImmutableRef(nft1)).willReturn(uniqueTokenAdapter);
        given(nfts.getImmutableRef(nft2)).willReturn(uniqueTokenAdapter);
        given(rels.contains(Pair.of(ownerId, nftToken))).willReturn(true);

        given(merkleToken.treasury()).willReturn(EntityId.fromGrpcAccountId(ownerId));
        given(merkleToken.tokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
    }
}
