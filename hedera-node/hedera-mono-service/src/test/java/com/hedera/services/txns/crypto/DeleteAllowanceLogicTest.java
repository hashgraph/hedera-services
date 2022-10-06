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
package com.hedera.services.txns.crypto;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteAllowanceLogicTest {
    @Mock private AccountStore accountStore;
    @Mock private TypedTokenStore tokenStore;

    private TransactionBody cryptoDeleteAllowanceTxn;
    private CryptoDeleteAllowanceTransactionBody op;

    DeleteAllowanceLogic subject;

    private static final TokenID token1 = asToken("0.0.100");
    private static final TokenID token2 = asToken("0.0.200");
    private static final AccountID ownerId = asAccount("0.0.5000");
    private static final AccountID payerId = asAccount("0.0.5001");
    private static final Instant consensusTime = Instant.now();
    private final Token token1Model = new Token(Id.fromGrpcToken(token1));
    private final Token token2Model = new Token(Id.fromGrpcToken(token2));

    private final NftRemoveAllowance nftAllowance1 =
            NftRemoveAllowance.newBuilder()
                    .setOwner(ownerId)
                    .setTokenId(token2)
                    .addAllSerialNumbers(List.of(12L, 10L))
                    .build();
    private List<NftRemoveAllowance> nftAllowances = new ArrayList<>();
    private final Account ownerAccount = new Account(Id.fromGrpcAccount(ownerId));
    private final Account payerAccount = new Account(Id.fromGrpcAccount(payerId));

    private final Set<FcTokenAllowanceId> existingNftAllowances = new TreeSet<>();

    private static final AccountID spender1 = asAccount("0.0.123");
    private static final AccountID spender2 = asAccount("0.0.1234");

    private static final UniqueToken uniqueToken1 = new UniqueToken(Id.fromGrpcToken(token2), 12L);
    private static final UniqueToken uniqueToken2 = new UniqueToken(Id.fromGrpcToken(token2), 10L);

    @BeforeEach
    void setup() {
        subject = new DeleteAllowanceLogic(accountStore, tokenStore);
    }

    @Test
    void happyPathDeletesAllowances() {
        givenValidTxnCtx();
        addExistingAllowances(ownerAccount);
        given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);
        given(
                        accountStore.loadAccountOrFailWith(
                                Id.fromGrpcAccount(ownerId), INVALID_ALLOWANCE_OWNER_ID))
                .willReturn(ownerAccount);

        token2Model.setTreasury(ownerAccount);
        given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token2))).willReturn(token2Model);
        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 12L)).willReturn(uniqueToken2);
        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 10L)).willReturn(uniqueToken1);
        uniqueToken1.setOwner(Id.MISSING_ID);
        uniqueToken2.setOwner(Id.MISSING_ID);

        assertEquals(1, ownerAccount.getApprovedForAllNftsAllowances().size());

        subject.deleteAllowance(op.getNftAllowancesList(), payerId);

        assertEquals(1, ownerAccount.getApprovedForAllNftsAllowances().size());

        verify(tokenStore, times(2)).persistNft(any());
    }

    @Test
    void canDeleteAllowancesOnTreasury() {
        givenValidTxnCtx();
        addExistingAllowances(ownerAccount);
        given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);
        given(
                        accountStore.loadAccountOrFailWith(
                                Id.fromGrpcAccount(ownerId), INVALID_ALLOWANCE_OWNER_ID))
                .willReturn(ownerAccount);
        token2Model.setTreasury(ownerAccount);
        given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token2))).willReturn(token2Model);
        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 12L)).willReturn(uniqueToken2);
        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 10L)).willReturn(uniqueToken1);
        uniqueToken1.setOwner(Id.MISSING_ID);
        uniqueToken2.setOwner(Id.MISSING_ID);

        assertEquals(1, ownerAccount.getApprovedForAllNftsAllowances().size());

        subject.deleteAllowance(op.getNftAllowancesList(), payerId);

        assertEquals(1, ownerAccount.getApprovedForAllNftsAllowances().size());
        verify(tokenStore, times(2)).persistNft(any());
    }

    @Test
    void failsDeleteAllowancesOnInvalidTreasury() {
        givenValidTxnCtx();

        given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);
        given(
                        accountStore.loadAccountOrFailWith(
                                Id.fromGrpcAccount(ownerId), INVALID_ALLOWANCE_OWNER_ID))
                .willReturn(ownerAccount);
        token2Model.setTreasury(payerAccount);
        given(tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(token2))).willReturn(token2Model);
        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 12L)).willReturn(uniqueToken2);
        uniqueToken1.setOwner(Id.MISSING_ID);
        uniqueToken2.setOwner(Id.MISSING_ID);

        Executable deleteAllowance =
                () -> subject.deleteAllowance(op.getNftAllowancesList(), payerId);

        assertThrows(InvalidTransactionException.class, deleteAllowance);
    }

    @Test
    void doesntThrowIfAllowancesDoesNotExist() {
        final NftRemoveAllowance nftRemoveAllowance =
                NftRemoveAllowance.newBuilder().setOwner(ownerId).build();

        cryptoDeleteAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoDeleteAllowance(
                                CryptoDeleteAllowanceTransactionBody.newBuilder()
                                        .addNftAllowances(nftRemoveAllowance))
                        .build();

        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

        given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);
        given(
                        accountStore.loadAccountOrFailWith(
                                Id.fromGrpcAccount(ownerId), INVALID_ALLOWANCE_OWNER_ID))
                .willReturn(ownerAccount);

        subject.deleteAllowance(op.getNftAllowancesList(), payerId);

        verify(tokenStore, never()).persistNft(any());
    }

    @Test
    void clearsPayerIfOwnerNotSpecified() {
        givenValidTxnCtxWithNoOwner();
        addExistingAllowances(payerAccount);

        given(accountStore.loadAccount(payerAccount.getId())).willReturn(payerAccount);
        given(tokenStore.loadUniqueToken(token2Model.getId(), 12L)).willReturn(uniqueToken1);
        given(tokenStore.loadUniqueToken(token2Model.getId(), 10L)).willReturn(uniqueToken2);
        uniqueToken1.setOwner(payerAccount.getId());
        uniqueToken2.setOwner(payerAccount.getId());

        assertEquals(1, payerAccount.getApprovedForAllNftsAllowances().size());

        subject.deleteAllowance(op.getNftAllowancesList(), payerId);

        assertEquals(1, payerAccount.getApprovedForAllNftsAllowances().size());

        verify(tokenStore, times(2)).persistNft(any());
    }

    @Test
    void emptyAllowancesInStateTransitionWorks() {
        cryptoDeleteAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.newBuilder())
                        .build();

        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

        given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);

        subject.deleteAllowance(op.getNftAllowancesList(), payerId);

        assertEquals(0, ownerAccount.getCryptoAllowances().size());
        assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
        assertEquals(0, ownerAccount.getApprovedForAllNftsAllowances().size());
        verify(accountStore, never()).commitAccount(ownerAccount);
    }

    private void givenValidTxnCtx() {
        token1Model.setMaxSupply(5000L);
        token1Model.setType(TokenType.FUNGIBLE_COMMON);
        token2Model.setMaxSupply(5000L);
        token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

        nftAllowances.add(nftAllowance1);

        cryptoDeleteAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoDeleteAllowance(
                                CryptoDeleteAllowanceTransactionBody.newBuilder()
                                        .addAllNftAllowances(nftAllowances))
                        .build();
        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

        ownerAccount.setApproveForAllNfts(new TreeSet<>());
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(payerId)
                .setTransactionValidStart(
                        Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
                .build();
    }

    private void addExistingAllowances(Account ownerAccount) {
        List<Long> serials = new ArrayList<>();
        serials.add(10L);
        serials.add(12L);

        existingNftAllowances.add(
                FcTokenAllowanceId.from(
                        EntityNum.fromTokenId(token2), EntityNum.fromAccountId(spender1)));

        ownerAccount.setApproveForAllNfts(existingNftAllowances);

        uniqueToken1.setSpender(Id.fromGrpcAccount(spender1));
        uniqueToken2.setSpender(Id.fromGrpcAccount(spender2));
        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 10L)).willReturn(uniqueToken1);
        given(tokenStore.loadUniqueToken(Id.fromGrpcToken(token2), 12L)).willReturn(uniqueToken2);
    }

    private void givenValidTxnCtxWithNoOwner() {
        token1Model.setMaxSupply(5000L);
        token1Model.setType(TokenType.FUNGIBLE_COMMON);
        token2Model.setMaxSupply(5000L);
        token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

        final NftRemoveAllowance nftAllowance =
                NftRemoveAllowance.newBuilder()
                        .setTokenId(token2)
                        .addAllSerialNumbers(List.of(12L, 10L))
                        .build();

        nftAllowances.add(nftAllowance);

        cryptoDeleteAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoDeleteAllowance(
                                CryptoDeleteAllowanceTransactionBody.newBuilder()
                                        .addAllNftAllowances(nftAllowances))
                        .build();
        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

        ownerAccount.setApproveForAllNfts(new TreeSet<>());

        given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);
    }
}
